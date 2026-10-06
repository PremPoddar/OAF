package com.prempoddar.oaf.pdf

import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.sqrt

/** Something on a page that can be picked: a line of text, a picture, a drawn shape, or an embedded drawing. */
class PdfElement internal constructor(
    val kind: Kind,
    /** Indexes of the content-stream operators that make it. */
    internal val ops: List<Int>,
    val box: Box,
    val text: String = "",
    internal val info: TextInfo? = null,
    /** The pieces a tap can land on: each text operator's box for a line, otherwise just [box]. */
    val parts: List<Box> = listOf(box),
) {
    enum class Kind { TEXT, IMAGE, SHAPE, OBJECT }

    /** Points, for showing and keeping the text's size when it's edited. */
    val fontSize get() = info?.let { it.size * it.scale } ?: 0f
    /** RGB of the text. */
    val color get() = info?.color ?: 0
    val fontName get() = info?.font?.name.orEmpty()
}

internal class TextInfo(
    val tm: Matrix, val ctm: Matrix, val endTm: Matrix, val size: Float, val hScale: Float,
    val font: PDFont?, val fontResource: COSName?, val color: Int,
) {
    /** Size as it appears on the page, through the text and graphics matrices. */
    val scale: Float get() = tm.multiply(ctm).let { sqrt(it.scaleY * it.scaleY + it.shearY * it.shearY) }
}

/** Fonts used when text is edited or added and the document's own font can't be reused. */
enum class FontFamilyChoice { SANS, SERIF, MONO }

fun interface FontSource {
    fun open(family: FontFamilyChoice): InputStream
}

object PdfContent {
    private val TEXT_OPS = setOf("Tj", "TJ", "'", "\"")
    private val PATH_BUILD = setOf("m", "l", "c", "v", "y", "h", "re")
    private val PATH_PAINT = setOf("S", "s", "f", "F", "f*", "B", "B*", "b", "b*")

    private class OpRecord(val index: Int, val kind: PdfElement.Kind, var box: Box?, val text: StringBuilder = StringBuilder(), var info: TextInfo? = null)

    private class Scanner(page: PDPage, val geom: PageGeom) : PDFGraphicsStreamEngine(page) {
        var index = -1
        var depth = 0
        val records = LinkedHashMap<Int, OpRecord>()
        var fontResource: COSName? = null
        private var pathStart = -1
        private var pathBox: Box? = null
        private var pathClip = false
        private var current = PointF()
        private var pendingText: Triple<Matrix, Matrix, Int>? = null

        private fun displayBox(points: List<PointF>): Box = points.map { geom.toDisplay(it.x, it.y) }.let { p ->
            Box(p.minOf { it.first }, p.minOf { it.second }, p.maxOf { it.first }, p.maxOf { it.second })
        }

        private fun addToCurrent(kind: PdfElement.Kind, b: Box) {
            val rec = records.getOrPut(index) { OpRecord(index, if (depth > 0) PdfElement.Kind.OBJECT else kind, null) }
            rec.box = rec.box?.union(b) ?: b
        }

        /** Above zero while an operator runs other operators itself (' runs T* and Tj, TD runs TL and Td). */
        private var nesting = 0

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            val name = operator.name
            val top = nesting == 0 && depth == 0
            if (top) {
                index++
                if (name == "Tf") fontResource = operands.firstOrNull() as? COSName
                if (name in PATH_BUILD && pathStart < 0) pathStart = index
                if (name in TEXT_OPS) {
                    val gs = graphicsState
                    val color = runCatching { gs.nonStrokingColor.toRGB() }.getOrDefault(0)
                    pendingText = Triple(textMatrix.clone(), gs.currentTransformationMatrix.clone(), color)
                }
            }
            nesting++
            try { super.processOperator(operator, operands) } finally { nesting-- }
            if (top && name in TEXT_OPS) {
                val (tm, ctm, color) = pendingText ?: return
                val ts = graphicsState.textState
                // For ' and " the line moves first, so the start is the new line's matrix.
                val start = if (name == "Tj" || name == "TJ") tm else textLineMatrix.clone()
                val rec = records.getOrPut(index) { OpRecord(index, PdfElement.Kind.TEXT, null) }
                rec.info = TextInfo(start, ctm, textMatrix.clone(), ts.fontSize, ts.horizontalScaling / 100f, ts.font, fontResource, color)
            }
        }

        override fun showForm(form: PDFormXObject) { depth++; try { super.showForm(form) } finally { depth-- } }

        override fun showGlyph(textRenderingMatrix: Matrix, font: PDFont, code: Int, displacement: Vector) {
            val w = displacement.x
            val corners = listOf(0f to -0.22f, w to -0.22f, 0f to 0.8f, w to 0.8f).map { (x, y) -> textRenderingMatrix.transformPoint(x, y) }
            addToCurrent(PdfElement.Kind.TEXT, displayBox(corners))
            if (depth == 0) records[index]?.text?.append(runCatching { font.toUnicode(code) }.getOrNull() ?: "")
        }

        override fun drawImage(pdImage: PDImage) {
            val m = graphicsState.currentTransformationMatrix
            addToCurrent(PdfElement.Kind.IMAGE, displayBox(listOf(m.transformPoint(0f, 0f), m.transformPoint(1f, 0f), m.transformPoint(0f, 1f), m.transformPoint(1f, 1f))))
        }

        private fun grow(x: Float, y: Float) {
            val (dx, dy) = geom.toDisplay(x, y)
            val b = Box(dx, dy, dx, dy)
            if (depth > 0) addToCurrent(PdfElement.Kind.OBJECT, b) else pathBox = pathBox?.union(b) ?: b
        }

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) { listOf(p0, p1, p2, p3).forEach { grow(it.x, it.y) }; current = p0 }
        override fun moveTo(x: Float, y: Float) { grow(x, y); current = PointF(x, y) }
        override fun lineTo(x: Float, y: Float) { grow(x, y); current = PointF(x, y) }
        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { grow(x1, y1); grow(x2, y2); grow(x3, y3); current = PointF(x3, y3) }
        override fun getCurrentPoint(): PointF = current
        override fun closePath() = Unit
        override fun clip(windingRule: Path.FillType) { if (depth == 0) pathClip = true }
        override fun endPath() = finishPath(paint = false)
        override fun strokePath() = finishPath(paint = true)
        override fun fillPath(windingRule: Path.FillType) = finishPath(paint = true)
        override fun fillAndStrokePath(windingRule: Path.FillType) = finishPath(paint = true)
        override fun shadingFill(shadingName: COSName) = Unit

        private fun finishPath(paint: Boolean) {
            if (depth > 0) return
            val box = pathBox
            if (paint && !pathClip && pathStart >= 0 && box != null) {
                // Hairlines get some thickness so they can be tapped.
                val pad = 0.004f
                val rec = OpRecord(pathStart, PdfElement.Kind.SHAPE, Box(box.l - pad, box.t - pad, box.r + pad, box.b + pad))
                rec.text.append(index.toString()) // last operator of the path
                records[pathStart] = rec
            }
            pathStart = -1; pathBox = null; pathClip = false
        }
    }

    /** Everything that can be picked on page [pageIndex], text gathered into lines. */
    fun elements(doc: PDDocument, pageIndex: Int): List<PdfElement> {
        val page = doc.getPage(pageIndex)
        val geom = PageGeom(page)
        val scanner = Scanner(page, geom)
        runCatching { scanner.processPage(page) }
        val out = ArrayList<PdfElement>()
        var line: MutableList<OpRecord>? = null
        fun flushLine() {
            val l = line ?: return
            line = null
            val text = StringBuilder()
            l.forEachIndexed { i, r ->
                if (i > 0) {
                    val prev = l[i - 1].box!!; val b = r.box!!
                    if (b.l - prev.r > prev.height * 0.2f && !text.endsWith(" ") && !r.text.startsWith(" ")) text.append(' ')
                }
                text.append(r.text)
            }
            out += PdfElement(PdfElement.Kind.TEXT, l.map { it.index }, l.map { it.box!! }.reduce(Box::union), text.toString(), l.first().info, l.map { it.box!! })
        }
        for (rec in scanner.records.values.sortedBy { it.index }) {
            val box = rec.box ?: continue
            when (rec.kind) {
                PdfElement.Kind.TEXT -> {
                    if (rec.text.isBlank()) continue
                    val l = line
                    val last = l?.lastOrNull()?.box
                    // Same baseline and close by: part of the same line.
                    if (l != null && last != null && abs(last.b - box.b) < last.height * 0.35f && box.l >= last.l && box.l - last.r < last.height * 2.5f) l += rec
                    else { flushLine(); line = mutableListOf(rec) }
                }
                PdfElement.Kind.SHAPE -> {
                    flushLine()
                    val last = rec.text.toString().toIntOrNull() ?: rec.index
                    out += PdfElement(PdfElement.Kind.SHAPE, (rec.index..last).toList(), box)
                }
                else -> { flushLine(); out += PdfElement(rec.kind, listOf(rec.index), box) }
            }
        }
        flushLine()
        return out
    }

    /**
     * Everything under a point, best first: text whose words were touched comes before anything
     * else, then the smallest of the rest (so a cell wins over the page-sized box behind it).
     * Tapping the same spot again steps through this list.
     */
    fun candidates(elements: List<PdfElement>, x: Float, y: Float): List<PdfElement> {
        val slop = 0.004f
        fun touched(e: PdfElement) = e.parts.filter { it.contains(x, y, slop) }
        return elements.filter { touched(it).isNotEmpty() }
            .sortedWith(compareBy<PdfElement>({ if (it.kind == PdfElement.Kind.TEXT) 0 else 1 }, { e -> touched(e).minOf { it.width * it.height } }))
    }

    fun at(elements: List<PdfElement>, x: Float, y: Float): PdfElement? = candidates(elements, x, y).firstOrNull()

    // ---------------------------------------------------------------- rewriting

    private fun rewrite(doc: PDDocument, page: PDPage, replace: (index: Int, op: Operator, operands: List<COSBase>) -> List<Any>?) {
        val parser = PDFStreamParser(page)
        val tokens = ArrayList<Any>()
        var operands = ArrayList<COSBase>()
        var index = -1
        while (true) {
            val token = parser.parseNextToken() ?: break
            if (token is Operator) {
                index++
                val r = replace(index, token, operands)
                if (r == null) { tokens.addAll(operands); tokens += token } else tokens.addAll(r)
                operands = ArrayList()
            } else if (token is COSBase) operands += token
        }
        tokens.addAll(operands)
        val stream = PDStream(doc)
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(tokens) }
        page.setContents(stream)
    }

    /** Removes elements. Text is replaced by an equal gap so the rest of its line stays where it was. */
    fun delete(doc: PDDocument, pageIndex: Int, elements: List<PdfElement>) {
        val page = doc.getPage(pageIndex)
        // Text operators need their own advance, which only a fresh scan knows.
        val scan = Scanner(page, PageGeom(page)).also { runCatching { it.processPage(page) } }
        val remove = elements.flatMap { it.ops }.toSet()
        rewrite(doc, page) { i, op, operands ->
            if (i !in remove) return@rewrite null
            when (op.name) {
                "Tj", "TJ", "'", "\"" -> {
                    val info = scan.records[i]?.info
                    val gap = info?.let { advanceGap(it) }
                    val move = buildList<Any> {
                        if (op.name == "'") add(Operator.getOperator("T*"))
                        if (op.name == "\"") {
                            add(operands[0]); add(Operator.getOperator("Tw")); add(operands[1]); add(Operator.getOperator("Tc")); add(Operator.getOperator("T*"))
                        }
                        if (gap != null && gap != 0f) { add(COSArray().apply { add(COSFloat(gap)) }); add(Operator.getOperator("TJ")) }
                    }
                    move
                }
                else -> emptyList()
            }
        }
    }

    /** The TJ adjustment that moves the text position as far as the removed text did. */
    private fun advanceGap(info: TextInfo): Float? {
        val a = info.tm.scaleX; val b = info.tm.shearY
        val dx = info.endTm.translateX - info.tm.translateX
        val dy = info.endTm.translateY - info.tm.translateY
        val norm = a * a + b * b
        if (norm == 0f || info.size == 0f || info.hScale == 0f) return null
        val tx = (dx * a + dy * b) / norm
        return -tx / (info.size * info.hScale) * 1000f
    }

    private fun isSubset(font: PDFont) = font.name?.let { it.length > 7 && it[6] == '+' && it.substring(0, 6).all(Char::isUpperCase) } == true

    private fun canShow(font: PDFont, text: String) = font !is PDType3Font && !isSubset(font) && runCatching { font.encode(text) }.isSuccess

    fun familyFor(fontName: String): FontFamilyChoice {
        val n = fontName.lowercase()
        return when {
            listOf("courier", "mono", "consol", "code").any { it in n } -> FontFamilyChoice.MONO
            listOf("times", "serif", "georgia", "garamond", "book", "roman", "cambria", "minion", "palatino").any { it in n } && "sans" !in n -> FontFamilyChoice.SERIF
            else -> FontFamilyChoice.SANS
        }
    }

    /**
     * Replaces a line of text. The document's own font is kept when it has every character needed;
     * otherwise (usually because only the characters already used were embedded) a matching
     * family is substituted.
     */
    fun replaceText(doc: PDDocument, pageIndex: Int, element: PdfElement, text: String, fonts: FontSource) {
        val info = element.info ?: error("Not text")
        delete(doc, pageIndex, listOf(element))
        if (text.isEmpty()) return
        val page = doc.getPage(pageIndex)
        val own = info.font?.takeIf { f -> text.lines().all { canShow(f, it) } }
        val font = own ?: PDType0Font.load(doc, fonts.open(familyFor(info.font?.name.orEmpty())), true)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.beginText()
            cs.setFont(font, info.size)
            cs.setNonStrokingColor((info.color shr 16 and 0xFF) / 255f, (info.color shr 8 and 0xFF) / 255f, (info.color and 0xFF) / 255f)
            cs.setTextMatrix(info.tm.multiply(info.ctm))
            text.lines().forEachIndexed { i, l ->
                if (i > 0) cs.newLineAtOffset(0f, -info.size * 1.2f)
                cs.showText(showable(font, l))
            }
            cs.endText()
        }
    }

    private fun showable(font: PDFont, s: String): String = if (runCatching { font.encode(s) }.isSuccess) s else s.map { c -> if (runCatching { font.encode(c.toString()) }.isSuccess) c else '?' }.joinToString("")

    /** Adds text with its top-left at ([x], [y]) on the page as shown, upright however the page is rotated. */
    fun addText(doc: PDDocument, pageIndex: Int, x: Float, y: Float, text: String, sizePt: Float, rgb: Int, family: FontFamilyChoice, fonts: FontSource) {
        val page = doc.getPage(pageIndex)
        val geom = PageGeom(page)
        val font = PDType0Font.load(doc, fonts.open(family), true)
        val (ux, uy) = geom.toUser(x, y + sizePt * 0.85f / geom.displayHeight)
        val theta = Math.toRadians(geom.rotation.toDouble())
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.beginText()
            cs.setFont(font, sizePt)
            cs.setNonStrokingColor((rgb shr 16 and 0xFF) / 255f, (rgb shr 8 and 0xFF) / 255f, (rgb and 0xFF) / 255f)
            cs.setTextMatrix(Matrix.getRotateInstance(theta, ux, uy))
            text.lines().forEachIndexed { i, l ->
                if (i > 0) cs.newLineAtOffset(0f, -sizePt * 1.25f)
                cs.showText(showable(font, l))
            }
            cs.endText()
        }
    }

    /** Draws a picture filling [box] (display coordinates), upright however the page is rotated. */
    fun addImage(doc: PDDocument, pageIndex: Int, box: Box, image: PDImageXObject) {
        val page = doc.getPage(pageIndex)
        val geom = PageGeom(page)
        val (ux, uy) = geom.toUser(box.l, box.b)
        val w = box.width * geom.displayWidth; val h = box.height * geom.displayHeight
        val m = Matrix(w, 0f, 0f, h, 0f, 0f).multiply(Matrix.getRotateInstance(Math.toRadians(geom.rotation.toDouble()), ux, uy))
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { it.drawImage(image, m) }
    }
}

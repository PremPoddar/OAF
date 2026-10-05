package com.prempoddar.oaf.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/** An annotation as the viewer needs it: where it is, what it says, and where a link goes. */
class PdfAnnot(val index: Int, val subtype: String, val box: Box, val contents: String?, val color: Int?, val uri: String?, val page: Int?) {
    val isLink get() = subtype == "Link"
    /** Notes, markup, drawings and shapes: what the annotate tools made or can remove. */
    val isMarkup get() = subtype in setOf("Text", "Highlight", "Underline", "StrikeOut", "Squiggly", "Ink", "Square", "Circle", "Line", "FreeText", "Polygon", "PolyLine", "Stamp", "Caret")
}

object PdfAnnots {
    enum class Markup(val subtype: String) {
        HIGHLIGHT(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT), UNDERLINE(PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE),
        STRIKEOUT(PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT), SQUIGGLY(PDAnnotationTextMarkup.SUB_TYPE_SQUIGGLY),
    }

    enum class Shape { RECTANGLE, ELLIPSE, LINE, ARROW }

    fun list(doc: PDDocument, pageIndex: Int): List<PdfAnnot> {
        val page = doc.getPage(pageIndex)
        val geom = PageGeom(page)
        return page.annotations.mapIndexedNotNull { i, a ->
            val sub = a.subtype ?: return@mapIndexedNotNull null
            if (sub == "Popup" || sub == "Widget") return@mapIndexedNotNull null
            val r = a.rectangle ?: return@mapIndexedNotNull null
            var uri: String? = null
            var target: Int? = null
            if (a is PDAnnotationLink) {
                when (val action = runCatching { a.action }.getOrNull()) {
                    is PDActionURI -> uri = action.uri
                    is PDActionGoTo -> target = pageOf(doc, action.destination as? PDPageDestination)
                }
                if (uri == null && target == null) target = pageOf(doc, runCatching { a.destination as? PDPageDestination }.getOrNull())
            }
            PdfAnnot(i, sub, geom.boxOf(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY), a.contents, a.color?.let { runCatching { it.toRGB() }.getOrNull() }, uri, target)
        }
    }

    private fun pageOf(doc: PDDocument, dest: PDPageDestination?): Int? {
        dest ?: return null
        val n = dest.retrievePageNumber()
        if (n >= 0) return n
        return dest.page?.let { p -> doc.pages.indexOf(p).takeIf { it >= 0 } }
    }

    private fun colour(rgb: Int) = PDColor(floatArrayOf((rgb shr 16 and 0xFF) / 255f, (rgb shr 8 and 0xFF) / 255f, (rgb and 0xFF) / 255f), PDDeviceRGB.INSTANCE)

    private fun add(doc: PDDocument, pageIndex: Int, a: PDAnnotation) {
        val page = doc.getPage(pageIndex)
        a.setModifiedDate(Calendar.getInstance())
        if (a is PDAnnotationMarkup) a.setCreationDate(Calendar.getInstance())
        a.setPrinted(true)
        a.page = page
        val list = page.annotations
        list += a
        page.annotations = list
        a.constructAppearances(doc)
    }

    /** Highlight, underline, strike out or squiggle the text under [lines] (one box per line of text). */
    fun markup(doc: PDDocument, pageIndex: Int, kind: Markup, lines: List<Box>, rgb: Int, note: String? = null) {
        if (lines.isEmpty()) return
        val geom = PageGeom(doc.getPage(pageIndex))
        val a = PDAnnotationTextMarkup(kind.subtype)
        val quads = FloatArray(lines.size * 8)
        lines.forEachIndexed { i, b ->
            // Corners in the order readers expect: top-left, top-right, bottom-left, bottom-right.
            listOf(b.l to b.t, b.r to b.t, b.l to b.b, b.r to b.b).forEachIndexed { k, (x, y) ->
                val (ux, uy) = geom.toUser(x, y)
                quads[i * 8 + k * 2] = ux; quads[i * 8 + k * 2 + 1] = uy
            }
        }
        a.quadPoints = quads
        a.rectangle = boundsOf(quads)
        a.color = colour(rgb)
        if (kind == Markup.HIGHLIGHT) a.constantOpacity = 1f
        note?.let { a.contents = it }
        add(doc, pageIndex, a)
    }

    /** Freehand drawing; each stroke is a list of display points. */
    fun ink(doc: PDDocument, pageIndex: Int, strokes: List<List<Pair<Float, Float>>>, rgb: Int, widthPt: Float, opacity: Float = 1f) {
        val geom = PageGeom(doc.getPage(pageIndex))
        val a = PDAnnotationMarkup()
        a.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
        val paths = strokes.filter { it.isNotEmpty() }.map { s -> s.flatMap { (x, y) -> geom.toUser(x, y).toList() }.toFloatArray() }
        if (paths.isEmpty()) return
        a.inkList = paths.toTypedArray()
        val all = paths.flatMap { it.toList() }.toFloatArray()
        a.rectangle = boundsOf(all, widthPt)
        a.color = colour(rgb)
        a.borderStyle = PDBorderStyleDictionary().apply { width = widthPt }
        a.constantOpacity = opacity
        add(doc, pageIndex, a)
    }

    /** A sticky note at a display point. */
    fun note(doc: PDDocument, pageIndex: Int, x: Float, y: Float, text: String, rgb: Int = 0xF5C518) {
        val geom = PageGeom(doc.getPage(pageIndex))
        val (ux, uy) = geom.toUser(x, y)
        val a = PDAnnotationText()
        a.rectangle = PDRectangle(ux, uy - 22f, 22f, 22f)
        a.contents = text
        a.color = colour(rgb)
        a.name = PDAnnotationText.NAME_COMMENT
        add(doc, pageIndex, a)
    }

    /** A rectangle, ellipse, line or arrow from [a] to [b] (display points). */
    fun shape(doc: PDDocument, pageIndex: Int, kind: Shape, a: Pair<Float, Float>, b: Pair<Float, Float>, rgb: Int, widthPt: Float) {
        val geom = PageGeom(doc.getPage(pageIndex))
        val p = geom.toUser(a.first, a.second); val q = geom.toUser(b.first, b.second)
        val border = PDBorderStyleDictionary().apply { width = widthPt }
        val annot: PDAnnotation = when (kind) {
            Shape.RECTANGLE, Shape.ELLIPSE -> PDAnnotationSquareCircle(if (kind == Shape.RECTANGLE) PDAnnotationSquareCircle.SUB_TYPE_SQUARE else PDAnnotationSquareCircle.SUB_TYPE_CIRCLE).apply {
                rectangle = boundsOf(floatArrayOf(p.first, p.second, q.first, q.second), widthPt / 2)
                borderStyle = border
            }
            Shape.LINE, Shape.ARROW -> PDAnnotationLine().apply {
                line = floatArrayOf(p.first, p.second, q.first, q.second)
                rectangle = boundsOf(floatArrayOf(p.first, p.second, q.first, q.second), widthPt * 4)
                borderStyle = border
                if (kind == Shape.ARROW) endPointEndingStyle = PDAnnotationLine.LE_OPEN_ARROW
            }
        }
        annot.color = colour(rgb)
        add(doc, pageIndex, annot)
    }

    fun setContents(doc: PDDocument, pageIndex: Int, index: Int, text: String) {
        val a = doc.getPage(pageIndex).annotations.getOrNull(index) ?: return
        a.contents = text
        a.setModifiedDate(Calendar.getInstance())
    }

    fun delete(doc: PDDocument, pageIndex: Int, index: Int) {
        val page = doc.getPage(pageIndex)
        val list = page.annotations
        if (index !in list.indices) return
        val a = list.removeAt(index)
        // Its pop-up window goes with it.
        (a as? PDAnnotationMarkup)?.popup?.let { popup -> list.removeAll { it.cosObject === popup.cosObject } }
        page.annotations = list
    }

    private fun boundsOf(points: FloatArray, pad: Float = 0f): PDRectangle {
        var x1 = Float.MAX_VALUE; var y1 = Float.MAX_VALUE; var x2 = -Float.MAX_VALUE; var y2 = -Float.MAX_VALUE
        for (i in points.indices step 2) { x1 = min(x1, points[i]); x2 = max(x2, points[i]); y1 = min(y1, points[i + 1]); y2 = max(y2, points[i + 1]) }
        return PDRectangle(x1 - pad, y1 - pad, x2 - x1 + 2 * pad, y2 - y1 + 2 * pad)
    }
}

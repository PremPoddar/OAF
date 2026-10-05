package com.prempoddar.oaf.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.Writer
import kotlin.math.abs
import kotlin.math.max

/** A page's text in reading order, with where each character sits (null for added spaces and line breaks). */
class PageText(val text: String, val boxes: List<Box?>) {
    /** The character nearest a point, for starting a selection. */
    fun charAt(x: Float, y: Float): Int? {
        var best: Int? = null
        var bestD = Float.MAX_VALUE
        boxes.forEachIndexed { i, b ->
            if (b == null) return@forEachIndexed
            if (b.contains(x, y)) return i
            val dx = max(0f, max(b.l - x, x - b.r)); val dy = max(0f, max(b.t - y, y - b.b)) * 3
            val d = dx + dy
            if (d < bestD) { bestD = d; best = i }
        }
        return best?.takeIf { bestD < 0.06f }
    }

    /** The word around [i]. */
    fun wordAt(i: Int): IntRange {
        var s = i; var e = i
        while (s > 0 && text[s - 1].isLetterOrDigit()) s--
        while (e < text.length - 1 && text[e + 1].isLetterOrDigit()) e++
        return s..e
    }

    /** Boxes covering [range], one per line: what a highlight is drawn from. */
    fun lineBoxes(range: IntRange): List<Box> {
        val out = ArrayList<Box>()
        var cur: Box? = null
        for (i in range) {
            val b = boxes.getOrNull(i) ?: continue
            val c = cur
            cur = if (c != null && abs(c.cy - b.cy) < c.height * 0.5f && b.l >= c.l - c.height) c.union(b) else { if (c != null) out += c; b }
        }
        cur?.let { out += it }
        return out
    }

    fun find(query: String): List<IntRange> {
        if (query.isBlank()) return emptyList()
        val hay = text.replace('\n', ' ')
        val out = ArrayList<IntRange>()
        var at = hay.indexOf(query, 0, ignoreCase = true)
        while (at >= 0) { out += at until at + query.length; at = hay.indexOf(query, at + 1, ignoreCase = true) }
        return out
    }
}

object PdfText {
    fun page(doc: PDDocument, index: Int): PageText {
        val geom = PageGeom(doc.getPage(index))
        val sb = StringBuilder()
        val boxes = ArrayList<Box?>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, positions: MutableList<TextPosition>) {
                for (tp in positions) {
                    val u = tp.unicode ?: continue
                    val size = max(tp.heightDir, tp.fontSizeInPt * 0.72f).coerceAtLeast(1f)
                    val l = tp.xDirAdj; val r = tp.xDirAdj + tp.widthDirAdj
                    val base = tp.yDirAdj
                    val box = Box(l / geom.displayWidth, (base - size * 1.05f) / geom.displayHeight, r / geom.displayWidth, (base + size * 0.28f) / geom.displayHeight)
                    for (c in u) { sb.append(c); boxes += box }
                }
            }
            override fun writeWordSeparator() { sb.append(' '); boxes += null }
            override fun writeLineSeparator() { sb.append('\n'); boxes += null }
        }
        stripper.sortByPosition = true
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        stripper.writeText(doc, object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
            override fun flush() = Unit
            override fun close() = Unit
        })
        return PageText(sb.toString(), boxes)
    }
}

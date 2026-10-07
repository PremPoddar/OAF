package com.prempoddar.oaf.doc

import com.prempoddar.oaf.xml.kid
import org.w3c.dom.Element

private fun Element.wAttr(name: String) = getAttributeNS(W, name).takeIf { hasAttributeNS(W, name) }
private fun twips(v: String?) = v?.toFloatOrNull()?.div(20f)

/** Paper size and margins in points. */
data class PageSetup(val width: Float, val height: Float, val left: Float, val right: Float, val top: Float, val bottom: Float) {
    val textWidth get() = width - left - right

    companion object {
        /** US Letter with one-inch margins, Word's own fallback. */
        val LETTER = PageSetup(612f, 792f, 72f, 72f, 72f, 72f)

        fun of(sectPr: Element?): PageSetup {
            sectPr ?: return LETTER
            val size = sectPr.kid("pgSz")
            val mar = sectPr.kid("pgMar")
            var w = twips(size?.wAttr("w")) ?: LETTER.width
            var h = twips(size?.wAttr("h")) ?: LETTER.height
            if (size?.wAttr("orient") == "landscape" && h > w) { val t = w; w = h; h = t }
            return PageSetup(
                w, h,
                twips(mar?.wAttr("left") ?: mar?.wAttr("start")) ?: LETTER.left,
                twips(mar?.wAttr("right") ?: mar?.wAttr("end")) ?: LETTER.right,
                twips(mar?.wAttr("top"))?.let { kotlin.math.abs(it) } ?: LETTER.top,
                twips(mar?.wAttr("bottom"))?.let { kotlin.math.abs(it) } ?: LETTER.bottom,
            )
        }
    }
}

/**
 * How a paragraph sits on the page, in points: space around it, line spacing (a multiple of single,
 * an exact height, or a minimum), indents (a negative [firstLine] is a hanging indent), and shading.
 */
data class ParaLayout(
    val spaceBefore: Float = 0f,
    val spaceAfter: Float = 0f,
    val lineMultiple: Float? = 1f,
    val lineExact: Float? = null,
    val lineAtLeast: Float? = null,
    val left: Float = 0f,
    val right: Float = 0f,
    val firstLine: Float = 0f,
    /** RGB background, if any. */
    val shading: Int? = null,
    val pageBreakBefore: Boolean = false,
    /** No space between paragraphs of the same style. */
    val contextual: Boolean = false,
) {
    companion object {
        /** Applies each source's properties over the ones before it. */
        fun resolve(sources: List<Element>): ParaLayout {
            var l = ParaLayout()
            for (pPr in sources) {
                pPr.kid("spacing")?.let { sp ->
                    twips(sp.wAttr("before"))?.let { l = l.copy(spaceBefore = it) }
                    twips(sp.wAttr("after"))?.let { l = l.copy(spaceAfter = it) }
                    // "Auto" spacing before and after is about 14 pt in Word, like a web page.
                    if (sp.wAttr("beforeAutospacing").isOn()) l = l.copy(spaceBefore = 14f)
                    if (sp.wAttr("afterAutospacing").isOn()) l = l.copy(spaceAfter = 14f)
                    sp.wAttr("line")?.toFloatOrNull()?.let { line ->
                        l = when (sp.wAttr("lineRule")) {
                            "exact" -> l.copy(lineExact = line / 20f, lineMultiple = null, lineAtLeast = null)
                            "atLeast" -> l.copy(lineAtLeast = line / 20f, lineMultiple = 1f, lineExact = null)
                            else -> l.copy(lineMultiple = line / 240f, lineExact = null, lineAtLeast = null)
                        }
                    }
                }
                pPr.kid("ind")?.let { ind ->
                    twips(ind.wAttr("left") ?: ind.wAttr("start"))?.let { l = l.copy(left = it) }
                    twips(ind.wAttr("right") ?: ind.wAttr("end"))?.let { l = l.copy(right = it) }
                    twips(ind.wAttr("firstLine"))?.let { l = l.copy(firstLine = it) }
                    twips(ind.wAttr("hanging"))?.let { l = l.copy(firstLine = -it) }
                }
                pPr.kid("shd")?.wAttr("fill")?.let { f -> l = l.copy(shading = f.takeIf { it != "auto" }?.toIntOrNull(16)) }
                pPr.kid("pageBreakBefore")?.let { l = l.copy(pageBreakBefore = it.wAttr("val").isOn(default = true)) }
                pPr.kid("contextualSpacing")?.let { l = l.copy(contextual = it.wAttr("val").isOn(default = true)) }
            }
            return l
        }

        private fun String?.isOn(default: Boolean = false) = if (this == null) default else this != "0" && this != "false" && this != "off"
    }
}

/** Table looks that the page view needs. */
object DocLayout {
    fun bordered(t: Block.T, styles: DocStyles): Boolean {
        val tblPr = t.el?.kid("tblPr")
        if (tblPr?.kid("tblBorders") != null) return true
        if (t.rows.flatten().any { it.el?.kid("tcPr")?.kid("tcBorders") != null }) return true
        return styles.tableHasBorders(tblPr?.kid("tblStyle")?.wAttr("val"))
    }

    fun cellFill(c: Cell): Int? = c.el?.kid("tcPr")?.kid("shd")?.wAttr("fill")?.takeIf { it != "auto" }?.toIntOrNull(16)

    /** How many grid columns a cell spans. */
    fun span(c: Cell): Int = c.el?.kid("tcPr")?.kid("gridSpan")?.wAttr("val")?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    /** Whether [r] is a page break Word draws. */
    fun isPageBreak(r: Run): Boolean = r.obj?.let { o ->
        val br = if (o.localName == "br") o else o.kid("br")
        br?.wAttr("type") == "page"
    } == true

    /** Whether the paragraph ends a section that starts a new page. */
    fun endsSection(p: Para): Boolean = p.pPr?.kid("sectPr")?.let { s -> s.kid("type")?.wAttr("val").let { it == null || it == "nextPage" || it == "oddPage" || it == "evenPage" } } == true
}

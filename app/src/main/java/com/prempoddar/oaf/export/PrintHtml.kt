package com.prempoddar.oaf.export

import com.prempoddar.oaf.doc.Block
import com.prempoddar.oaf.doc.DocBody
import com.prempoddar.oaf.doc.DocStyles
import com.prempoddar.oaf.doc.Numbering
import com.prempoddar.oaf.doc.Para
import com.prempoddar.oaf.sheet.CellStyle
import com.prempoddar.oaf.sheet.NumFmt
import com.prempoddar.oaf.sheet.Sheet
import com.prempoddar.oaf.sheet.Value
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.util.Base64

/**
 * Print-ready HTML for each kind of document: black on white, A4-friendly margins. Android's print
 * dialog turns it into paper or a PDF.
 */
object PrintHtml {
    private const val BASE_CSS = """
@page { margin: 18mm 16mm; }
body { font-family: 'Georgia', serif; font-size: 11pt; line-height: 1.45; color: #111; margin: 0; }
h1, h2, h3, h4 { font-family: 'Helvetica Neue', Arial, sans-serif; line-height: 1.2; page-break-after: avoid; }
table { border-collapse: collapse; page-break-inside: auto; }
tr { page-break-inside: avoid; }
td, th { border: 0.5pt solid #999; padding: 3pt 5pt; vertical-align: top; }
img { max-width: 100%; }
pre { white-space: pre-wrap; font-family: 'Courier New', monospace; font-size: 9.5pt; }
"""

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(title: String, css: String, body: String) =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>${escape(title)}</title><style>$BASE_CSS$css</style></head><body>$body</body></html>"

    private fun hex(rgb: Int) = "#%06X".format(rgb and 0xFFFFFF)

    // ---------------------------------------------------------------- documents

    fun document(title: String, body: DocBody, styles: DocStyles, numbering: Numbering, image: (String) -> ByteArray?): String {
        val labels = numbering.labels(body.paragraphs, styles)
        val sb = StringBuilder()
        fun para(p: Para, inCell: Boolean = false) {
            val level = styles.headingLevel(p.props.styleId)
            val tag = when { inCell -> "div"; level == 0 -> "h1"; level == 1 -> "h1"; level == 2 -> "h2"; level != null -> "h3"; else -> "p" }
            val align = when (styles.align(p)) { "center" -> "center"; "right", "end" -> "right"; "both" -> "justify"; else -> null }
            val indent = labels[p.id]?.let { (styles.numbering(p)?.second ?: 0) * 18 + 18 }
            // The paragraph's own look is set once; runs only say how they differ from it.
            val base = styles.paraRun(p.props.styleId)
            val attrs = listOfNotNull(
                align?.let { "text-align:$it" }, indent?.let { "margin-left:${it}pt;text-indent:-14pt" },
                base.size?.takeIf { it != styles.baseSize }?.let { "font-size:${it}pt" }, base.color?.let { "color:${hex(it)}" },
                if (level == null && base.bold == true) "font-weight:bold" else null,
            )
            sb.append("<$tag").append(if (attrs.isEmpty()) "" else " style=\"${attrs.joinToString(";")}\"").append('>')
            labels[p.id]?.let { sb.append("<span style=\"display:inline-block;min-width:14pt\">${escape(it)}</span>") }
            for (r in p.runs) {
                if (r.image != null) {
                    image(r.image)?.let { bytes ->
                        val w = r.imageSize?.first?.let { "width:${it}pt;" } ?: ""
                        sb.append("<img style=\"$w\" src=\"data:${mime(r.image)};base64,${Base64.getEncoder().encodeToString(bytes)}\">")
                    }
                    continue
                }
                if (r.isObject || r.text.isEmpty()) continue
                val s = styles.effective(p, r)
                val css = listOfNotNull(
                    (if (s.bold == true) "font-weight:bold" else "font-weight:normal").takeIf { (s.bold == true) != (base.bold == true) },
                    "font-style:italic".takeIf { s.italic == true && base.italic != true },
                    listOfNotNull("underline".takeIf { s.underline == true }, "line-through".takeIf { s.strike == true }).takeIf { it.isNotEmpty() }?.let { "text-decoration:${it.joinToString(" ")}" },
                    s.color?.takeIf { it != base.color }?.let { "color:${hex(it)}" }, s.highlight?.let { "background:${if (it == "yellow") "#FFF176" else "#E0E0E0"}" },
                    s.size?.takeIf { it != base.size }?.let { "font-size:${it}pt" },
                    when (s.vert) { "superscript" -> "vertical-align:super;font-size:smaller"; "subscript" -> "vertical-align:sub;font-size:smaller"; else -> null },
                )
                val text = escape(r.text).replace("\t", "&emsp;").replace(" ", "<br>")
                val content = if (css.isEmpty()) text else "<span style=\"${css.joinToString(";")}\">$text</span>"
                sb.append(if (r.linkTarget != null && !r.linkTarget.startsWith("#")) "<a href=\"${escape(r.linkTarget)}\">$content</a>" else content)
            }
            if (p.runs.all { it.text.isEmpty() || it.isObject } && p.images.isEmpty()) sb.append("&nbsp;")
            sb.append("</$tag>")
        }
        for (b in body.blocks) when (b) {
            is Block.P -> para(b.para)
            is Block.T -> {
                sb.append("<table>")
                b.rows.forEach { row -> sb.append("<tr>"); row.forEach { c -> sb.append("<td>"); c.paras.forEach { para(it, inCell = true) }; sb.append("</td>") }; sb.append("</tr>") }
                sb.append("</table>")
            }
        }
        val base = styles.baseSize
        return page(title, "body { font-size: ${base}pt; } p { margin: 0 0 6pt; } td div { margin: 0; }", sb.toString())
    }

    private fun mime(path: String) = when (path.substringAfterLast('.').lowercase()) { "png" -> "image/png"; "gif" -> "image/gif"; "svg" -> "image/svg+xml"; "bmp" -> "image/bmp"; else -> "image/jpeg" }

    // ---------------------------------------------------------------- spreadsheets

    fun sheet(title: String, sheet: Sheet): String {
        val sb = StringBuilder("<h3>${escape(sheet.name)}</h3><table style=\"font-family:Arial,sans-serif;font-size:9pt\">")
        val covered = HashSet<Long>()
        for (r in 0..sheet.maxRow) {
            sb.append("<tr>")
            for (c in 0..sheet.maxCol) {
                if (sheet.key(r, c) in covered) continue
                val merge = sheet.merges.firstOrNull { it.r1 == r && it.c1 == c }
                merge?.let { m -> for (rr in m.r1..m.r2) for (cc in m.c1..m.c2) if (rr != r || cc != c) covered += sheet.key(rr, cc) }
                val cell = sheet[r, c]
                val st = cell?.style ?: CellStyle.DEFAULT
                val v = cell?.value ?: Value.Empty
                val align = when (st.align) { CellStyle.Align.LEFT -> "left"; CellStyle.Align.CENTER -> "center"; CellStyle.Align.RIGHT -> "right"; else -> if (v is Value.Num) "right" else "left" }
                val css = listOfNotNull(
                    "text-align:$align", "font-weight:bold".takeIf { st.bold }, "font-style:italic".takeIf { st.italic },
                    st.fill?.let { "background:${hex(it)}" }, st.color?.let { "color:${hex(it)}" }, if (st.wrap) null else "white-space:nowrap",
                )
                val span = merge?.let { m -> (if (m.r2 > m.r1) " rowspan=\"${m.r2 - m.r1 + 1}\"" else "") + (if (m.c2 > m.c1) " colspan=\"${m.c2 - m.c1 + 1}\"" else "") }.orEmpty()
                sb.append("<td$span style=\"${css.joinToString(";")}\">${escape(NumFmt.format(v, st.numFmt))}</td>")
            }
            sb.append("</tr>")
        }
        sb.append("</table>")
        return page(title, "@page { size: landscape; }", sb.toString())
    }

    // ---------------------------------------------------------------- text and pictures

    fun text(title: String, text: String, markdown: Boolean): String {
        val body = if (markdown) {
            val ext = listOf(TablesExtension.create())
            HtmlRenderer.builder().extensions(ext).escapeHtml(true).build().render(Parser.builder().extensions(ext).build().parse(text))
        } else "<pre>${escape(text)}</pre>"
        return page(title, "", body)
    }

    fun image(title: String, bytes: ByteArray, mime: String) =
        page(title, "@page { margin: 10mm; } body { text-align: center; } img { max-height: 95vh; }", "<img src=\"data:$mime;base64,${Base64.getEncoder().encodeToString(bytes)}\">")
}

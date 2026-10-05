package com.prempoddar.oaf.doc

import com.prempoddar.oaf.xml.Xml
import com.prempoddar.oaf.xml.kids
import org.apache.poi.hwpf.HWPFDocument
import org.w3c.dom.Element
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile

/**
 * Other word-processor formats, read into the same paragraphs as .docx using Word's standard style
 * names, so they display and edit the same way and save as .docx.
 */
object Converters {
    private fun para(runs: List<Run>, props: ParaProps = ParaProps()) = Para(newId(), Para.normalize(runs.ifEmpty { listOf(Run("")) }), props)

    private fun headingStyle(name: String?): String? {
        val n = name?.lowercase()?.replace("_20_", " ")?.trim() ?: return null
        if (n == "title") return "Title"
        if (n == "subtitle") return "Subtitle"
        return Regex("^heading ?(\\d)$").find(n)?.groupValues?.get(1)?.toInt()?.coerceIn(1, 3)?.let { "Heading$it" }
    }

    // ---------------------------------------------------------------- Word 97–2003

    fun doc(file: File): DocBody = file.inputStream().use { input ->
        val d = HWPFDocument(input)
        val range = d.range
        val sheet = d.styleSheet
        val blocks = ArrayList<Block>()
        var rows = ArrayList<List<Cell>>()
        var cells = ArrayList<Cell>()
        var cellParas = ArrayList<Para>()
        for (i in 0 until range.numParagraphs()) {
            val p = range.getParagraph(i)
            val runs = ArrayList<Run>()
            var inCode = false
            for (j in 0 until p.numCharacterRuns()) {
                val cr = p.getCharacterRun(j)
                val sb = StringBuilder()
                for (ch in cr.text()) when (ch.code) {
                    0x13 -> inCode = true            // field start: the code follows
                    0x14 -> inCode = false           // field separator: the result follows
                    0x15 -> inCode = false
                    0x0B -> if (!inCode) sb.append(' ')
                    0x09 -> if (!inCode) sb.append('\t')
                    0x1E -> if (!inCode) sb.append('‑')
                    0x0D, 0x07, 0x01, 0x08, 0x0C, 0x1F -> Unit
                    else -> if (!inCode && ch.code >= 0x20) sb.append(ch)
                }
                if (sb.isEmpty()) continue
                val ico = cr.ico24
                runs += Run(sb.toString(), RunStyle(
                    bold = cr.isBold.takeIf { it }, italic = cr.isItalic.takeIf { it }, underline = (cr.underlineCode != 0).takeIf { it },
                    strike = cr.isStrikeThrough.takeIf { it }, size = cr.fontSize / 2f,
                    color = if (ico == -1 || ico == 0) null else ((ico and 0xFF) shl 16) or (ico and 0xFF00) or ((ico shr 16) and 0xFF),
                    font = cr.fontName,
                ))
            }
            val styleName = runCatching { sheet.getStyleDescription(p.styleIndex.toInt())?.name }.getOrNull()
            val props = ParaProps(
                styleId = headingStyle(styleName),
                align = when (p.justification.toInt()) { 1 -> "center"; 2 -> "right"; 3 -> "both"; else -> null },
                numId = if (p.isInList) Numbering.BULLET else null,
                level = if (p.isInList) runCatching { p.ilvl.toInt() }.getOrDefault(0).coerceIn(0, 8) else 0,
            )
            if (p.isInTable) {
                if (p.isTableRowEnd) {
                    if (cells.isNotEmpty()) rows += cells
                    cells = ArrayList()
                } else {
                    cellParas += para(runs, props)
                    if (p.text().endsWith('\u0007')) { cells += Cell(null, cellParas); cellParas = ArrayList() }
                }
                continue
            }
            if (rows.isNotEmpty()) { blocks += Block.T(null, rows, emptyList()); rows = ArrayList() }
            blocks += Block.P(para(runs, props))
        }
        if (rows.isNotEmpty()) blocks += Block.T(null, rows, emptyList())
        DocBody(blocks.ifEmpty { listOf(Block.P(para(emptyList()))) })
    }

    // ---------------------------------------------------------------- OpenDocument text

    private const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    private const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private const val XLINK = "http://www.w3.org/1999/xlink"

    private class OdtStyle(val parent: String?, val run: RunStyle, val align: String?)

    fun odt(file: File): DocBody = ZipFile(file).use { zip ->
        fun part(name: String) = zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        val content = Xml.parse(part("content.xml") ?: error("Not an OpenDocument text"))
        val styles = HashMap<String, OdtStyle>()
        listOfNotNull(part("styles.xml")?.let { Xml.parse(it) }, content).forEach { d ->
            val nl = d.getElementsByTagNameNS(STYLE, "style")
            for (i in 0 until nl.length) {
                val s = nl.item(i) as Element
                val tp = s.kids("text-properties").firstOrNull()
                val pp = s.kids("paragraph-properties").firstOrNull()
                styles[s.getAttributeNS(STYLE, "name")] = OdtStyle(
                    s.getAttributeNS(STYLE, "parent-style-name").ifEmpty { null },
                    tp?.let { t ->
                        RunStyle(
                            bold = t.getAttributeNS(FO, "font-weight").takeIf { it.isNotEmpty() }?.let { it == "bold" || (it.toIntOrNull() ?: 400) >= 600 },
                            italic = t.getAttributeNS(FO, "font-style").takeIf { it.isNotEmpty() }?.let { it == "italic" || it == "oblique" },
                            underline = t.getAttributeNS(STYLE, "text-underline-style").takeIf { it.isNotEmpty() }?.let { it != "none" },
                            strike = t.getAttributeNS(STYLE, "text-line-through-style").takeIf { it.isNotEmpty() }?.let { it != "none" },
                            color = t.getAttributeNS(FO, "color").takeIf { it.startsWith("#") }?.substring(1)?.toIntOrNull(16),
                            size = t.getAttributeNS(FO, "font-size").takeIf { it.endsWith("pt") }?.removeSuffix("pt")?.toFloatOrNull(),
                            font = t.getAttributeNS(STYLE, "font-name").ifEmpty { null },
                        )
                    } ?: RunStyle(),
                    pp?.getAttributeNS(FO, "text-align")?.let { when (it) { "center" -> "center"; "end", "right" -> "right"; "justify" -> "both"; else -> null } },
                )
            }
        }
        fun runStyle(name: String?): RunStyle {
            var s = name?.let { styles[it] } ?: return RunStyle()
            val chain = mutableListOf(s)
            while (chain.size < 10) { s = s.parent?.let { styles[it] } ?: break; chain += s }
            return chain.reversed().fold(RunStyle()) { acc, st -> acc under st.run }
        }
        fun heading(name: String?): String? {
            var n = name
            repeat(10) { headingStyle(n)?.let { return it }; n = n?.let { styles[it]?.parent } ?: return null }
            return null
        }

        fun inline(el: Element, style: RunStyle, link: String?, out: MutableList<Run>) {
            var n = el.firstChild
            while (n != null) {
                when {
                    n.nodeType == org.w3c.dom.Node.TEXT_NODE -> out += Run(n.nodeValue.replace(Regex("\\s+"), " "), style, linkTarget = link)
                    n is Element && n.namespaceURI == TEXT -> when (n.localName) {
                        "span" -> inline(n, style under runStyle(n.getAttributeNS(TEXT, "style-name")), link, out)
                        "a" -> inline(n, style, n.getAttributeNS(XLINK, "href"), out)
                        "s" -> out += Run(" ".repeat(n.getAttributeNS(TEXT, "c").toIntOrNull() ?: 1), style)
                        "tab" -> out += Run("\t", style)
                        "line-break" -> out += Run(" ", style)
                        "note", "bookmark", "bookmark-start", "bookmark-end", "soft-page-break", "sequence-decls" -> Unit
                        else -> inline(n, style, link, out)
                    }
                }
                n = n.nextSibling
            }
        }

        fun paragraph(el: Element, list: Pair<Boolean, Int>?): Para {
            val styleName = el.getAttributeNS(TEXT, "style-name")
            val runs = ArrayList<Run>()
            inline(el, runStyle(styleName), null, runs)
            if (runs.isNotEmpty()) runs[0] = runs[0].copy(text = runs[0].text.trimStart())
            var align: String? = null
            var s: String? = styleName
            repeat(10) { styles[s]?.align?.let { align = it; return@repeat }; s = styles[s]?.parent }
            val style = if (el.localName == "h") heading(styleName) ?: "Heading${(el.getAttributeNS(TEXT, "outline-level").toIntOrNull() ?: 1).coerceIn(1, 3)}" else heading(styleName)
            return para(runs, ParaProps(styleId = style, align = align, numId = list?.let { if (it.first) Numbering.BULLET else Numbering.DECIMAL }, level = list?.second ?: 0))
        }

        // Lists are bulleted unless their style numbers them.
        fun numbered(listEl: Element): Boolean {
            val name = listEl.getAttributeNS(TEXT, "style-name")
            val nl = content.getElementsByTagNameNS(TEXT, "list-style")
            for (i in 0 until nl.length) {
                val ls = nl.item(i) as Element
                if (ls.getAttributeNS(STYLE, "name") == name) return ls.kids("list-level-style-number").isNotEmpty()
            }
            return false
        }

        fun blocks(parent: Element, out: MutableList<Block>, list: Pair<Boolean, Int>?) {
            for (k in parent.kids()) when (k.localName) {
                "p", "h" -> out += Block.P(paragraph(k, list))
                "list" -> { val bullet = list?.first ?: !numbered(k); val level = list?.let { it.second + 1 } ?: 0; k.kids("list-item").forEach { blocks(it, out, bullet to level) } }
                "list-header" -> blocks(k, out, list)
                "section", "index-body", "table-of-content" -> blocks(k, out, list)
                "table" -> if (k.namespaceURI == TABLE) {
                    val rows = ArrayList<List<Cell>>()
                    fun rowsOf(e: Element) {
                        for (r in e.kids()) when (r.localName) {
                            "table-row" -> rows += r.kids().filter { it.localName == "table-cell" || it.localName == "covered-table-cell" }.map { c ->
                                val inner = ArrayList<Block>()
                                blocks(c, inner, null)
                                Cell(null, inner.filterIsInstance<Block.P>().map { it.para })
                            }
                            "table-rows", "table-header-rows" -> rowsOf(r)
                        }
                    }
                    rowsOf(k)
                    out += Block.T(null, rows, emptyList())
                }
            }
        }

        val text = content.documentElement.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:office:1.0", "text").item(0) as? Element ?: error("Empty document")
        val out = ArrayList<Block>()
        blocks(text, out, null)
        DocBody(out.ifEmpty { listOf(Block.P(para(emptyList()))) })
    }

    // ---------------------------------------------------------------- RTF

    /** A forgiving RTF reader: text, paragraphs, character formatting, colours, alignment and simple tables. */
    fun rtf(file: File): DocBody = rtf(file.readBytes().toString(Charsets.ISO_8859_1))

    fun rtf(src: String): DocBody {
        data class State(var style: RunStyle = RunStyle(), var skip: Boolean = false, var uc: Int = 1, var colorIndex: Int = 0)
        val stack = ArrayDeque<State>()
        var st = State()
        val colors = ArrayList<Int?>()
        var inColorTable = false
        var colorDepth = 0
        var r = 0; var g = 0; var b = 0
        var codepage: Charset = Charset.forName("windows-1252")
        val blocks = ArrayList<Block>()
        val runs = ArrayList<Run>()
        var align: String? = null
        var skipChars = 0
        val text = StringBuilder()
        val pendingBytes = java.io.ByteArrayOutputStream()

        fun flushBytes() { if (pendingBytes.size() > 0) { text.append(String(pendingBytes.toByteArray(), codepage)); pendingBytes.reset() } }
        fun flushRun() {
            flushBytes()
            if (text.isNotEmpty()) { runs += Run(text.toString(), st.style.copy(color = colors.getOrNull(st.colorIndex))); text.setLength(0) }
        }
        fun endParagraph() { flushRun(); blocks += Block.P(para(runs.toList(), ParaProps(align = align))); runs.clear() }
        fun emit(s: String) {
            if (st.skip) return
            if (skipChars > 0) { skipChars--; return }
            flushBytes(); text.append(s)
        }

        val skipDestinations = setOf("fonttbl", "stylesheet", "info", "pict", "object", "header", "footer", "headerl", "headerr", "footerl", "footerr", "footnote", "field\u0000", "fldinst", "themedata", "colorschememapping", "latentstyles", "datastore", "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "generator", "mmathPr", "pgdsctbl")
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            when (ch) {
                '{' -> { flushRun(); stack.addLast(st.copy()); i++ }
                '}' -> { flushRun(); if (inColorTable && stack.size == colorDepth) inColorTable = false; st = stack.removeLastOrNull() ?: State(); i++ }
                '\\' -> {
                    i++
                    if (i >= src.length) break
                    val c = src[i]
                    when {
                        c == '\'' -> {
                            val hex = src.substring(i + 1, (i + 3).coerceAtMost(src.length)).toIntOrNull(16)
                            i += 3
                            if (hex != null && !st.skip) { if (skipChars > 0) skipChars-- else pendingBytes.write(hex) }
                        }
                        c == '*' -> { st.skip = true; i++ }
                        c == '~' -> { emit(" "); i++ }
                        c == '-' -> i++
                        c == '_' -> { emit("‑"); i++ }
                        c == '\\' || c == '{' || c == '}' -> { emit(c.toString()); i++ }
                        c == '\n' || c == '\r' -> { if (!st.skip) endParagraph(); i++ }
                        c.isLetter() -> {
                            val start = i
                            while (i < src.length && src[i].isLetter()) i++
                            val word = src.substring(start, i)
                            val numStart = i
                            if (i < src.length && (src[i] == '-' || src[i].isDigit())) { i++; while (i < src.length && src[i].isDigit()) i++ }
                            val num = src.substring(numStart, i).toIntOrNull()
                            if (i < src.length && src[i] == ' ') i++
                            if (word in skipDestinations) { st.skip = true; continue }
                            if (word == "colortbl") { inColorTable = true; colorDepth = stack.size; st.skip = true; continue }
                            if (inColorTable) { when (word) { "red" -> r = num ?: 0; "green" -> g = num ?: 0; "blue" -> b = num ?: 0 }; continue }
                            if (st.skip) continue
                            val on = num != 0
                            when (word) {
                                "par", "sect" -> endParagraph()
                                "line" -> emit(" ")
                                "tab" -> emit("\t")
                                "cell" -> emit("\t")
                                "row" -> endParagraph()
                                "page" -> Unit
                                "pard" -> align = null
                                "ql" -> align = null
                                "qc" -> align = "center"
                                "qr" -> align = "right"
                                "qj" -> align = "both"
                                "plain" -> { flushRun(); st.style = RunStyle(); st.colorIndex = 0 }
                                "b" -> { flushRun(); st.style = st.style.copy(bold = on.takeIf { it }) }
                                "i" -> { flushRun(); st.style = st.style.copy(italic = on.takeIf { it }) }
                                "ul" -> { flushRun(); st.style = st.style.copy(underline = on.takeIf { it }) }
                                "ulnone" -> { flushRun(); st.style = st.style.copy(underline = null) }
                                "strike" -> { flushRun(); st.style = st.style.copy(strike = on.takeIf { it }) }
                                "super" -> { flushRun(); st.style = st.style.copy(vert = "superscript") }
                                "sub" -> { flushRun(); st.style = st.style.copy(vert = "subscript") }
                                "nosupersub" -> { flushRun(); st.style = st.style.copy(vert = null) }
                                "fs" -> { flushRun(); st.style = st.style.copy(size = num?.div(2f)) }
                                "cf" -> { flushRun(); st.colorIndex = num ?: 0 }
                                "uc" -> st.uc = num ?: 1
                                "u" -> { num?.let { emit(((if (it < 0) it + 65536 else it)).toChar().toString()) }; skipChars = st.uc }
                                "ansicpg" -> num?.let { runCatching { codepage = Charset.forName("windows-$it") } }
                                "emdash" -> emit("—"); "endash" -> emit("–"); "bullet" -> emit("•")
                                "lquote" -> emit("‘"); "rquote" -> emit("’"); "ldblquote" -> emit("“"); "rdblquote" -> emit("”")
                            }
                        }
                        else -> i++
                    }
                }
                ';' -> {
                    if (inColorTable) { colors += if (colors.isEmpty() && r == 0 && g == 0 && b == 0) null else (r shl 16) or (g shl 8) or b; r = 0; g = 0; b = 0 }
                    else emit(";")
                    i++
                }
                '\r', '\n' -> i++
                else -> { if (!st.skip && !inColorTable) { if (skipChars > 0) skipChars-- else { flushBytes(); text.append(ch) } }; i++ }
            }
        }
        flushRun()
        if (runs.isNotEmpty()) endParagraph()
        // A trailing \par leaves an empty last paragraph in most files.
        while (blocks.size > 1 && (blocks.last() as? Block.P)?.para?.text?.isEmpty() == true) blocks.removeAt(blocks.lastIndex)
        return DocBody(blocks.ifEmpty { listOf(Block.P(para(emptyList()))) })
    }
}

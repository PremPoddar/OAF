package com.prempoddar.oaf.doc

import com.prempoddar.oaf.xml.Xml
import com.prempoddar.oaf.xml.descendants
import com.prempoddar.oaf.xml.kid
import com.prempoddar.oaf.xml.kids
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.OutputStream
import java.util.IdentityHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"
private const val XML_NS = "http://www.w3.org/XML/1998/namespace"

private fun Element.w(name: String) = getAttributeNS(W, name).takeIf { hasAttributeNS(W, name) }
private fun Element.flag(): Boolean = w("val")?.let { it != "0" && it != "false" && it != "off" && it != "none" } ?: true

/** A paragraph or character style from styles.xml. */
class StyleDef(
    val id: String, val name: String, val type: String, val basedOn: String?, val run: RunStyle, val align: String?, val numId: String?, val level: Int?, val outline: Int?,
    /** Paragraph properties (spacing, indents…) and, for table styles, table properties. */
    val pPr: Element? = null,
    val tblPr: Element? = null,
)

class DocStyles(private val defs: Map<String, StyleDef>, val defaults: RunStyle, private val defaultPara: String?, private val defaultPPr: Element? = null) {
    /** Spacing, indents and shading for [p], from the document defaults, its style chain and itself. */
    fun layout(p: Para, numbering: Numbering? = null): ParaLayout {
        val sources = ArrayList<Element>()
        defaultPPr?.let { sources += it }
        chain(p.props.styleId ?: defaultPara).forEach { s -> s.pPr?.let { sources += it } }
        // A list level's indents sit between the style and the paragraph's own.
        val list = numbering(p)
        if (list != null && numbering != null) numbering.level(list.first, list.second)?.pPr?.let { sources += it }
        p.pPr?.let { sources += it }
        return ParaLayout.resolve(sources)
    }

    /** Whether table style [id] (or one it's based on) draws borders. */
    fun tableHasBorders(id: String?): Boolean = id != null && chain(id).any { it.tblPr?.kid("tblBorders") != null } || id.equals("TableGrid", true)

    /** The body text size, which reading sizes are scaled from. */
    val baseSize get() = defaults.size ?: 11f

    fun def(id: String?): StyleDef? = id?.let { defs[it] ?: BUILTIN[it] }

    private fun chain(id: String?): List<StyleDef> {
        val out = ArrayList<StyleDef>()
        var cur = def(id)
        while (cur != null && out.size < 12) { out += cur; cur = def(cur.basedOn) }
        return out.reversed()
    }

    fun paraRun(styleId: String?): RunStyle = chain(styleId ?: defaultPara).fold(defaults) { acc, s -> acc under s.run }

    fun effective(p: Para, r: Run): RunStyle = (paraRun(p.props.styleId) under (chain(r.charStyle).fold(RunStyle.NONE) { a, s -> a under s.run })) under r.style

    fun align(p: Para): String? = p.props.align ?: chain(p.props.styleId ?: defaultPara).lastOrNull { it.align != null }?.align

    /** List numbering, from the paragraph or its style. */
    fun numbering(p: Para): Pair<String, Int>? {
        p.props.numId?.let { return if (it == "0") null else it to p.props.level }
        val s = chain(p.props.styleId ?: defaultPara).lastOrNull { it.numId != null } ?: return null
        return if (s.numId == "0") null else s.numId!! to (s.level ?: 0)
    }

    /** 0 for a title, 1–9 for headings, null for body text. */
    fun headingLevel(id: String?): Int? {
        val d = def(id) ?: return null
        val name = d.name.lowercase()
        if (name == "title") return 0
        Regex("^heading (\\d)$").find(name)?.let { return it.groupValues[1].toInt() }
        return chain(id).lastOrNull { it.outline != null }?.outline?.plus(1)
    }

    /** The paragraph styles to offer: the document's own heading styles, or Word's standard ones. */
    fun choices(): List<Pair<String?, String>> {
        val found = mutableListOf<Pair<String?, String>>(null to "Normal text")
        listOf("Title", "Subtitle", "Heading1", "Heading2", "Heading3").forEach { id ->
            val d = defs.values.firstOrNull { it.type == "paragraph" && it.name.equals(BUILTIN.getValue(id).name, true) } ?: BUILTIN.getValue(id)
            found += d.id to d.name.replaceFirstChar { it.uppercase() }
        }
        return found
    }

    companion object {
        /** Standard styles, used when a document lacks them; written into the file when used. */
        val BUILTIN = mapOf(
            "Title" to StyleDef("Title", "Title", "paragraph", null, RunStyle(size = 26f), null, null, null, null),
            "Subtitle" to StyleDef("Subtitle", "Subtitle", "paragraph", null, RunStyle(size = 15f, color = 0x595959), null, null, null, null),
            "Heading1" to StyleDef("Heading1", "heading 1", "paragraph", null, RunStyle(bold = true, size = 16f), null, null, null, 0),
            "Heading2" to StyleDef("Heading2", "heading 2", "paragraph", null, RunStyle(bold = true, size = 13f), null, null, null, 1),
            "Heading3" to StyleDef("Heading3", "heading 3", "paragraph", null, RunStyle(bold = true, size = 12f), null, null, null, 2),
        )

        fun parse(doc: Document?): DocStyles {
            if (doc == null) return DocStyles(emptyMap(), RunStyle(size = 11f), null)
            val root = doc.documentElement
            val defaults = root.kid("docDefaults")?.kid("rPrDefault")?.kid("rPr")?.let { parseRun(it) } ?: RunStyle()
            val defs = HashMap<String, StyleDef>()
            var defaultPara: String? = null
            root.kids("style").forEach { s ->
                val id = s.w("styleId") ?: return@forEach
                val type = s.w("type") ?: "paragraph"
                if (type == "paragraph" && s.w("default").let { it == "1" || it == "true" }) defaultPara = id
                val pPr = s.kid("pPr")
                val numPr = pPr?.kid("numPr")
                defs[id] = StyleDef(
                    id, s.kid("name")?.w("val") ?: id, type, s.kid("basedOn")?.w("val"),
                    s.kid("rPr")?.let { parseRun(it) } ?: RunStyle(),
                    pPr?.kid("jc")?.w("val"), numPr?.kid("numId")?.w("val"), numPr?.kid("ilvl")?.w("val")?.toIntOrNull(),
                    pPr?.kid("outlineLvl")?.w("val")?.toIntOrNull(),
                    pPr, s.kid("tblPr"),
                )
            }
            return DocStyles(defs, RunStyle(size = 10f) under defaults, defaultPara, root.kid("docDefaults")?.kid("pPrDefault")?.kid("pPr"))
        }

        fun parseRun(rPr: Element): RunStyle {
            fun f(name: String) = rPr.kid(name)?.flag()
            return RunStyle(
                bold = f("b"), italic = f("i"),
                underline = rPr.kid("u")?.let { it.w("val") != "none" },
                strike = f("strike") ?: f("dstrike"),
                color = rPr.kid("color")?.w("val")?.takeIf { it != "auto" }?.toIntOrNull(16),
                highlight = rPr.kid("highlight")?.w("val")?.takeIf { it != "none" },
                size = rPr.kid("sz")?.w("val")?.toFloatOrNull()?.div(2),
                font = rPr.kid("rFonts")?.let { it.w("ascii") ?: it.w("hAnsi") },
                vert = rPr.kid("vertAlign")?.w("val")?.takeIf { it != "baseline" },
            )
        }
    }
}

/** How lists are numbered, from numbering.xml. */
class Numbering(private val nums: Map<String, Map<Int, Level>>) {
    data class Level(val format: String, val text: String, val start: Int, val pPr: Element? = null)

    fun level(numId: String, lvl: Int): Level? = when (numId) {
        BULLET -> Level("bullet", listOf("•", "◦", "▪")[lvl % 3], 1)
        DECIMAL -> Level("decimal", "%${lvl + 1}.", 1)
        else -> nums[numId]?.let { it[lvl] ?: it[0] }
    }

    fun isBullet(numId: String?) = numId != null && (numId == BULLET || level(numId, 0)?.format == "bullet")

    /** The label in front of each list paragraph: "•", "3.", "b)", "1.2." and so on. */
    fun labels(paras: List<Para>, styles: DocStyles): Map<Long, String> {
        val counters = HashMap<String, IntArray>()
        val out = LinkedHashMap<Long, String>()
        for (p in paras) {
            val (numId, lvl) = styles.numbering(p) ?: continue
            val l = level(numId, lvl) ?: continue
            if (l.format == "none") continue
            // Lists sharing a definition count together, as Word does.
            val c = counters.getOrPut(numId) { IntArray(9) { -1 } }
            val lv = lvl.coerceIn(0, 8)
            c[lv] = if (c[lv] < 0) l.start else c[lv] + 1
            for (d in lv + 1 until 9) c[d] = -1
            out[p.id] = if (l.format == "bullet") bullet(l.text, lv) else {
                Regex("%(\\d)").replace(l.text) { m ->
                    val k = m.groupValues[1].toInt() - 1
                    val lk = level(numId, k)
                    format(if (c.getOrElse(k) { -1 } < 0) (lk?.start ?: 1) else c[k], lk?.format ?: "decimal")
                }
            }
        }
        return out
    }

    private fun bullet(text: String, lvl: Int): String {
        val t = text.trim()
        // Symbol and Wingdings bullets live in the private use area; show a plain one instead.
        return if (t.isEmpty() || t[0] in ''..'' || t[0] == 'o' || t[0] == '§') listOf("•", "◦", "▪")[lvl % 3] else t
    }

    private fun format(n: Int, fmt: String): String = when (fmt) {
        "lowerLetter" -> letters(n).lowercase()
        "upperLetter" -> letters(n)
        "lowerRoman" -> roman(n).lowercase()
        "upperRoman" -> roman(n)
        "decimalZero" -> n.toString().padStart(2, '0')
        else -> n.toString()
    }

    private fun letters(n: Int): String { var x = n; val sb = StringBuilder(); while (x > 0) { sb.append('A' + (x - 1) % 26); x = (x - 1) / 26 }; return sb.reverse().toString() }

    private fun roman(n: Int): String {
        if (n <= 0) return n.toString()
        val v = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val s = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var x = n; val sb = StringBuilder()
        v.forEachIndexed { i, k -> while (x >= k) { sb.append(s[i]); x -= k } }
        return sb.toString()
    }

    companion object {
        /** List kinds made in OAF, given real numbering when saved. */
        const val BULLET = "+bullet"
        const val DECIMAL = "+decimal"

        fun parse(doc: Document?): Numbering {
            if (doc == null) return Numbering(emptyMap())
            val abstract = doc.documentElement.kids("abstractNum").associate { a ->
                a.w("abstractNumId").orEmpty() to a.kids("lvl").associate { l ->
                    (l.w("ilvl")?.toIntOrNull() ?: 0) to Level(l.kid("numFmt")?.w("val") ?: "decimal", l.kid("lvlText")?.w("val") ?: "", l.kid("start")?.w("val")?.toIntOrNull() ?: 1, l.kid("pPr"))
                }
            }
            val nums = doc.documentElement.kids("num").associate { n ->
                val levels = abstract[n.kid("abstractNumId")?.w("val")].orEmpty().toMutableMap()
                n.kids("lvlOverride").forEach { o ->
                    val i = o.w("ilvl")?.toIntOrNull() ?: return@forEach
                    o.kid("startOverride")?.w("val")?.toIntOrNull()?.let { s -> levels[i]?.let { levels[i] = it.copy(start = s) } }
                }
                n.w("numId").orEmpty() to levels.toMap()
            }
            return Numbering(nums)
        }
    }
}

/** An open .docx: the editable body plus everything needed to save it back without losing the rest. */
class DocxDoc internal constructor(
    val body: DocBody,
    val styles: DocStyles,
    val numbering: Numbering,
    internal val parts: LinkedHashMap<String, ByteArray>,
    internal val docPath: String,
    internal val dom: Document,
    internal val bodyEl: Element,
    /** Paragraph elements OAF knows about: those read and those it made. */
    internal val known: MutableSet<Element>,
) {
    /** Paper size and margins, from the body's last section. */
    val page: PageSetup by lazy { PageSetup.of(bodyEl.kids("sectPr").lastOrNull()) }

    internal val created = HashMap<Long, Element>()
    internal val written = IdentityHashMap<Element, Para>()
    internal val markerIds = HashMap<String, String>()

    fun bytes(path: String): ByteArray? = parts[path]
}

object Docx {
    fun read(file: File): DocxDoc = ZipFile(file).use { zip ->
        val parts = LinkedHashMap<String, ByteArray>()
        zip.entries().asSequence().filter { !it.isDirectory }.forEach { e -> parts[e.name] = zip.getInputStream(e).use { it.readBytes() } }
        read(parts)
    }

    /** An empty document, for saving text from other formats as .docx. */
    fun blank(): DocxDoc = read(LinkedHashMap(TEMPLATE.mapValues { it.value.toByteArray() }))

    fun read(parts: LinkedHashMap<String, ByteArray>): DocxDoc {
        val docPath = parts["_rels/.rels"]?.let { Xml.parse(it) }?.documentElement?.kids("Relationship")
            ?.firstOrNull { it.getAttribute("Type").endsWith("/officeDocument") }?.getAttribute("Target")?.removePrefix("/") ?: "word/document.xml"
        val dom = Xml.parse(parts[docPath] ?: error("Not a Word document"))
        val rels = rels(parts, docPath)
        val styles = DocStyles.parse(rels.values.firstOrNull { it.first.endsWith("/styles") }?.second?.let { parts[it] }?.let { Xml.parse(it) })
        val numbering = Numbering.parse(rels.values.firstOrNull { it.first.endsWith("/numbering") }?.second?.let { parts[it] }?.let { Xml.parse(it) })
        val bodyEl = dom.documentElement.kid("body") ?: error("Empty document")
        val known = HashSet<Element>()
        val reader = Reader(rels, known)
        val blocks = ArrayList<Block>()
        reader.blocks(bodyEl, blocks)
        return DocxDoc(DocBody(blocks), styles, numbering, parts, docPath, dom, bodyEl, known)
    }

    /** Relationship id → (type, target); targets of internal parts are resolved to part paths. */
    private fun rels(parts: Map<String, ByteArray>, owner: String): Map<String, Pair<String, String>> {
        val dir = owner.substringBeforeLast('/', "")
        val path = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + owner.substringAfterLast('/') + ".rels"
        val doc = parts[path]?.let { Xml.parse(it) } ?: return emptyMap()
        return doc.documentElement.kids("Relationship").associate {
            val target = it.getAttribute("Target")
            it.getAttribute("Id") to (it.getAttribute("Type") to if (it.getAttribute("TargetMode") == "External") target else resolve(dir, target))
        }
    }

    private fun resolve(dir: String, target: String): String {
        if (target.startsWith("/")) return target.substring(1)
        val stack = ArrayList(if (dir.isEmpty()) emptyList() else dir.split('/'))
        target.split('/').forEach { seg -> when (seg) { ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); ".", "" -> Unit; else -> stack += seg } }
        return stack.joinToString("/")
    }

    private class Reader(val rels: Map<String, Pair<String, String>>, val known: MutableSet<Element>) {
        fun blocks(parent: Element, out: MutableList<Block>) {
            for (el in parent.kids()) when (el.localName) {
                "p" -> out += Block.P(para(el))
                "tbl" -> out += table(el)
                "sdt" -> el.kid("sdtContent")?.let { blocks(it, out) }
                "customXml" -> blocks(el, out)
            }
        }

        fun table(tbl: Element): Block.T {
            val widths = tbl.kid("tblGrid")?.kids("gridCol")?.map { it.w("w")?.toIntOrNull() ?: 0 }.orEmpty()
            val rows = tbl.kids("tr").map { tr ->
                tr.kids("tc").map { tc ->
                    val paras = ArrayList<Para>()
                    fun collect(e: Element) {
                        for (k in e.kids()) when (k.localName) {
                            "p" -> paras += para(k)
                            "tbl", "tr", "tc", "sdt", "sdtContent", "customXml" -> collect(k)
                        }
                    }
                    collect(tc)
                    Cell(tc, paras)
                }
            }
            return Block.T(tbl, rows, widths)
        }

        fun para(p: Element): Para {
            known += p
            val pPr = p.kid("pPr")
            val numPr = pPr?.kid("numPr")
            val props = ParaProps(
                styleId = pPr?.kid("pStyle")?.w("val"),
                align = pPr?.kid("jc")?.w("val")?.let { if (it == "start") "left" else if (it == "end") "right" else it },
                numId = numPr?.kid("numId")?.w("val"),
                level = numPr?.kid("ilvl")?.w("val")?.toIntOrNull() ?: 0,
            )
            val runs = ArrayList<Run>()
            collect(p, null, runs)
            if (runs.none { !it.isObject }) {
                val mark = pPr?.kid("rPr")
                runs += Run("", mark?.let { DocStyles.parseRun(it) } ?: RunStyle.NONE, mark)
            }
            return Para(newId(), Para.normalize(runs), props, p, pPr)
        }

        private fun collect(el: Element, link: Element?, out: MutableList<Run>) {
            for (k in el.kids()) when (k.localName) {
                "pPr", "del", "moveFrom", "bookmarkStart", "bookmarkEnd", "proofErr", "commentRangeStart", "commentRangeEnd", "permStart", "permEnd" -> Unit
                "r" -> run(k, link, out)
                "hyperlink" -> collect(k, k, out)
                "ins", "smartTag", "customXml", "fldSimple", "dir", "bdo", "moveTo" -> collect(k, link, out)
                "sdt" -> k.kid("sdtContent")?.let { collect(it, link, out) }
                else -> out += Run(Para.OBJ.toString(), obj = k.cloneNode(true) as Element) // equations and the like
            }
        }

        private fun linkTarget(link: Element?): String? = link?.let { l ->
            l.getAttributeNS(R, "id").takeIf { it.isNotEmpty() }?.let { rels[it]?.second } ?: l.w("anchor")?.let { "#$it" }
        }

        private fun run(r: Element, link: Element?, out: MutableList<Run>) {
            val rPr = r.kid("rPr")
            val style = rPr?.let { DocStyles.parseRun(it) } ?: RunStyle.NONE
            val charStyle = rPr?.kid("rStyle")?.w("val")
            val target = linkTarget(link)
            val sb = StringBuilder()
            fun flush() { if (sb.isNotEmpty()) { out += Run(sb.toString(), style, rPr, link = link, linkTarget = target, charStyle = charStyle); sb.setLength(0) } }
            for (k in r.kids()) when (k.localName) {
                "rPr", "lastRenderedPageBreak" -> Unit
                "t" -> sb.append(k.textContent)
                "tab" -> sb.append('\t')
                "cr" -> sb.append(' ')
                "br" -> if (k.w("type").let { it == "page" || it == "column" }) { flush(); out += objectRun(r, rPr, k, link) } else sb.append(' ')
                "noBreakHyphen" -> sb.append('‑')
                "softHyphen" -> sb.append('­')
                "sym" -> k.w("char")?.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                else -> { flush(); out += objectRun(r, rPr, k, link) }
            }
            flush()
        }

        /** Wraps one non-text piece of a run (a picture, a field code…) as a run of its own. */
        private fun objectRun(r: Element, rPr: Element?, child: Element, link: Element?): Run {
            val doc = r.ownerDocument
            val wr = doc.createElementNS(W, "w:r")
            rPr?.let { wr.appendChild(it.cloneNode(true)) }
            wr.appendChild(child.cloneNode(true))
            val embed = child.descendants("blip").firstOrNull()?.getAttributeNS(R, "embed")?.takeIf { it.isNotEmpty() }
                ?: child.descendants("imagedata").firstOrNull()?.getAttributeNS(R, "id")?.takeIf { it.isNotEmpty() }
            val image = embed?.let { rels[it]?.second }
            val extent = child.descendants("extent").firstOrNull()?.let { e ->
                val cx = e.getAttribute("cx").toFloatOrNull(); val cy = e.getAttribute("cy").toFloatOrNull()
                if (cx != null && cy != null) cx / 12700f to cy / 12700f else null
            }
            return Run(Para.OBJ.toString(), obj = wr, image = image, imageSize = extent, link = link, linkTarget = linkTarget(link))
        }
    }

    // ---------------------------------------------------------------- writing

    /**
     * Writes [body] into [doc]'s package. Only paragraphs that changed are rebuilt; everything else
     * in the file is copied as it was. Returns the body as saved, for editing to carry on from.
     */
    fun write(doc: DocxDoc, body: DocBody, out: OutputStream): DocBody {
        val dom = doc.dom
        val rels = rels(doc.parts, doc.docPath)
        val relsPath = doc.docPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } + "_rels/" + doc.docPath.substringAfterLast('/') + ".rels"
        val relsDom = Xml.parse(doc.parts[relsPath] ?: """<Relationships xmlns="$PKG_REL"/>""".toByteArray())
        val ctDom = Xml.parse(doc.parts.getValue("[Content_Types].xml"))

        fun elementFor(p: Para): Element = p.el ?: doc.created.getOrPut(p.id) { dom.createElementNS(W, "w:p").also { doc.known += it } }

        // Tables made from other formats get their XML here.
        val tables = IdentityHashMap<Block.T, Block.T>()
        val blocks = body.blocks.map { b -> if (b is Block.T && b.el == null) newTable(dom, b).also { tables[b] = it } else b }

        // 1. Paragraphs no longer in the document leave it.
        val inModel = HashSet<Element>()
        val containers = ArrayList<Pair<List<Element>, Element>>()
        val top = blocks.map { b -> when (b) { is Block.P -> elementFor(b.para); is Block.T -> b.el!! } }
        inModel += top
        containers += top to doc.bodyEl
        blocks.filterIsInstance<Block.T>().forEach { t -> t.rows.flatten().forEach { c -> val els = c.paras.map(::elementFor); inModel += els; containers += els to c.el!! } }
        doc.known.filter { it !in inModel && it.parentNode != null }.forEach { it.parentNode.removeChild(it) }

        // 2. New and restored paragraphs go in after their predecessor.
        for ((els, hint) in containers) {
            els.forEachIndexed { i, e ->
                if (e.parentNode != null) return@forEachIndexed
                val prev = els.getOrNull(i - 1)
                val next = els.drop(i + 1).firstOrNull { it.parentNode != null }
                when {
                    prev?.parentNode != null -> prev.parentNode.insertBefore(e, prev.nextSibling)
                    next != null -> next.parentNode.insertBefore(e, next)
                    else -> hint.insertBefore(e, if (hint === doc.bodyEl) hint.kid("sectPr") else null)
                }
            }
        }

        // 3. Changed paragraphs are rebuilt.
        val context = WriteContext(doc, rels, relsDom, ctDom)
        fun save(p: Para): Para {
            val e = elementFor(p)
            val clean = if (p.el === e && !p.dirty) p else p.copy(el = e, dirty = false)
            val last = doc.written[e]
            if (p.dirty || (last != null && last !== p) || (p.el == null && last == null)) writePara(context, p, e)
            doc.written[e] = clean
            return clean
        }
        val saved = DocBody(blocks.map { b ->
            when (b) {
                is Block.P -> Block.P(save(b.para))
                is Block.T -> b.copy(rows = b.rows.map { row -> row.map { c -> Cell(c.el, c.paras.map(::save)) } })
            }
        })

        context.finish()
        doc.parts[doc.docPath] = Xml.bytes(dom)
        doc.parts[relsPath] = Xml.bytes(relsDom)
        doc.parts["[Content_Types].xml"] = Xml.bytes(ctDom)
        ZipOutputStream(out).use { zip ->
            doc.parts.keys.sortedBy { if (it == "[Content_Types].xml") 0 else 1 }.forEach { name ->
                zip.putNextEntry(ZipEntry(name)); zip.write(doc.parts.getValue(name)); zip.closeEntry()
            }
        }
        return saved
    }

    private class WriteContext(val doc: DocxDoc, val rels: Map<String, Pair<String, String>>, val relsDom: Document, val ctDom: Document) {
        private var stylesDom: Pair<String, Document>? = null
        private var numberingDom: Pair<String, Document>? = null

        private fun partFor(type: String, default: String, contentType: String, empty: String): Pair<String, Document> {
            val path = rels.values.firstOrNull { it.first.endsWith("/$type") }?.second ?: default.also { p ->
                val ids = relsDom.documentElement.kids("Relationship").map { it.getAttribute("Id") }.toSet()
                var n = ids.size + 1
                while ("rId$n" in ids) n++
                relsDom.documentElement.appendChild(relsDom.createElementNS(PKG_REL, "Relationship").apply {
                    setAttribute("Id", "rId$n"); setAttribute("Type", "$R/$type"); setAttribute("Target", p.substringAfterLast('/'))
                })
                ctDom.documentElement.appendChild(ctDom.createElementNS(CT, "Override").apply { setAttribute("PartName", "/$p"); setAttribute("ContentType", contentType) })
            }
            return path to Xml.parse(doc.parts[path] ?: empty.toByteArray())
        }

        fun styles() = stylesDom ?: partFor("styles", "word/styles.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml", """<w:styles xmlns:w="$W"/>""").also { stylesDom = it }
        fun numbering() = numberingDom ?: partFor("numbering", "word/numbering.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml", """<w:numbering xmlns:w="$W"/>""").also { numberingDom = it }

        /** Adds a standard style the document doesn't define yet. */
        fun ensureStyle(id: String) {
            val builtin = DocStyles.BUILTIN[id] ?: return
            val (_, sd) = styles()
            if (sd.documentElement.kids("style").any { it.w("styleId") == id }) return
            val size = ((builtin.run.size ?: 11f) * 2).toInt()
            val outline = builtin.outline?.let { """<w:outlineLvl w:val="$it"/>""" }.orEmpty()
            val keep = if (builtin.outline != null) "<w:keepNext/>" else ""
            val color = builtin.run.color?.let { """<w:color w:val="${"%06X".format(it)}"/>""" }.orEmpty()
            val xml = """<w:style xmlns:w="$W" w:type="paragraph" w:styleId="$id"><w:name w:val="${builtin.name}"/><w:next w:val="Normal"/><w:qFormat/>""" +
                """<w:pPr>$keep<w:spacing w:before="240" w:after="80"/>$outline</w:pPr><w:rPr>${if (builtin.run.bold == true) "<w:b/>" else ""}$color<w:sz w:val="$size"/></w:rPr></w:style>"""
            sd.documentElement.appendChild(sd.importNode(Xml.parse(xml).documentElement, true))
        }

        /** Real numbering for lists made in OAF: one definition per kind, reused across saves. */
        fun numId(marker: String): String = doc.markerIds.getOrPut(marker) {
            val (_, nd) = numbering()
            val root = nd.documentElement
            val abstractId = (root.kids("abstractNum").maxOfOrNull { it.w("abstractNumId")?.toIntOrNull() ?: 0 } ?: -1) + 1
            val numId = (root.kids("num").maxOfOrNull { it.w("numId")?.toIntOrNull() ?: 0 } ?: 0) + 1
            val bullet = marker == Numbering.BULLET
            val levels = (0 until 9).joinToString("") { l ->
                val fmt = if (bullet) "bullet" else listOf("decimal", "lowerLetter", "lowerRoman")[l % 3]
                val text = if (bullet) listOf("•", "◦", "▪")[l % 3] else "%${l + 1}."
                """<w:lvl w:ilvl="$l"><w:start w:val="1"/><w:numFmt w:val="$fmt"/><w:lvlText w:val="$text"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="${720 * (l + 1)}" w:hanging="360"/></w:pPr></w:lvl>"""
            }
            val abstract = nd.importNode(Xml.parse("""<w:abstractNum xmlns:w="$W" w:abstractNumId="$abstractId"><w:multiLevelType w:val="hybridMultilevel"/>$levels</w:abstractNum>""").documentElement, true)
            root.insertBefore(abstract, root.kids("num").firstOrNull())
            root.appendChild(nd.importNode(Xml.parse("""<w:num xmlns:w="$W" w:numId="$numId"><w:abstractNumId w:val="$abstractId"/></w:num>""").documentElement, true))
            numId.toString()
        }

        fun finish() {
            stylesDom?.let { (p, d) -> doc.parts[p] = Xml.bytes(d) }
            numberingDom?.let { (p, d) -> doc.parts[p] = Xml.bytes(d) }
        }
    }

    private val RPR_ORDER = listOf(
        "rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike", "dstrike", "outline", "shadow", "emboss", "imprint",
        "noProof", "snapToGrid", "vanish", "webHidden", "color", "spacing", "w", "kern", "position", "sz", "szCs", "highlight", "u",
        "effect", "bdr", "shd", "fitText", "vertAlign", "rtl", "cs", "em", "lang", "eastAsianLayout", "specVanish", "oMath",
    )
    private val PPR_ORDER = listOf(
        "pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr", "widowControl", "numPr", "suppressLineNumbers", "pBdr", "shd",
        "tabs", "suppressAutoHyphens", "kinsoku", "wordWrap", "overflowPunct", "topLinePunct", "autoSpaceDE", "autoSpaceDN", "bidi",
        "adjustRightInd", "snapToGrid", "spacing", "ind", "contextualSpacing", "mirrorIndents", "suppressOverlap", "jc", "textDirection",
        "textAlignment", "textboxTightWrap", "outlineLvl", "divId", "cnfStyle", "rPr", "sectPr", "pPrChange",
    )

    /** Sets (or with null removes) child [name] of [parent], keeping the schema's order. */
    private fun setChild(parent: Element, order: List<String>, name: String, attrs: Map<String, String>?): Element? {
        val existing = parent.kid(name)
        if (attrs == null) { existing?.let { parent.removeChild(it) }; return null }
        val el = existing ?: parent.ownerDocument.createElementNS(W, "w:$name").also { e ->
            val pos = order.indexOf(name)
            parent.insertBefore(e, parent.kids().firstOrNull { order.indexOf(it.localName) > pos })
        }
        attrs.forEach { (k, v) -> el.setAttributeNS(W, "w:$k", v) }
        if (attrs.isEmpty()) { while (el.attributes.length > 0) el.removeAttributeNode(el.attributes.item(0) as org.w3c.dom.Attr) }
        return el
    }

    private fun onOff(v: Boolean?): Map<String, String>? = when (v) { null -> null; true -> emptyMap(); false -> mapOf("val" to "0") }

    private fun buildRPr(dom: Document, base: Element?, s: RunStyle): Element? {
        val rPr = (base?.let { dom.importNode(it, true) as Element }) ?: dom.createElementNS(W, "w:rPr")
        // A paragraph mark's properties may record revisions that don't belong on a run.
        rPr.kids().filter { it.localName in setOf("ins", "del", "moveFrom", "moveTo", "rPrChange") }.forEach { rPr.removeChild(it) }
        if (s.font != null && rPr.kid("rFonts")?.let { it.w("ascii") ?: it.w("hAnsi") } != s.font) {
            val f = setChild(rPr, RPR_ORDER, "rFonts", mapOf("ascii" to s.font, "hAnsi" to s.font, "cs" to s.font))!!
            f.removeAttributeNS(W, "asciiTheme"); f.removeAttributeNS(W, "hAnsiTheme")
        }
        setChild(rPr, RPR_ORDER, "b", onOff(s.bold)); if (s.bold == null) setChild(rPr, RPR_ORDER, "bCs", null)
        setChild(rPr, RPR_ORDER, "i", onOff(s.italic)); if (s.italic == null) setChild(rPr, RPR_ORDER, "iCs", null)
        setChild(rPr, RPR_ORDER, "strike", onOff(s.strike)); if (s.strike != null) setChild(rPr, RPR_ORDER, "dstrike", null)
        setChild(rPr, RPR_ORDER, "color", s.color?.let { mapOf("val" to "%06X".format(it and 0xFFFFFF)) })
        setChild(rPr, RPR_ORDER, "sz", s.size?.let { mapOf("val" to (it * 2).toInt().toString()) })
        setChild(rPr, RPR_ORDER, "szCs", s.size?.let { mapOf("val" to (it * 2).toInt().toString()) })
        setChild(rPr, RPR_ORDER, "highlight", s.highlight?.let { mapOf("val" to it) })
        val u = rPr.kid("u")?.w("val")
        setChild(rPr, RPR_ORDER, "u", when (s.underline) { null -> null; true -> mapOf("val" to (u?.takeIf { it != "none" } ?: "single")); false -> mapOf("val" to "none") })
        setChild(rPr, RPR_ORDER, "vertAlign", s.vert?.let { mapOf("val" to it) })
        return rPr.takeIf { it.hasChildNodes() }
    }

    private fun writePara(ctx: WriteContext, p: Para, e: Element) {
        val dom = e.ownerDocument
        val kids = e.kids()
        val starts = kids.filter { it.localName in setOf("bookmarkStart", "commentRangeStart", "permStart") }
        val ends = kids.filter { it.localName in setOf("bookmarkEnd", "commentRangeEnd", "permEnd") }
        while (e.firstChild != null) e.removeChild(e.firstChild)

        val pPr = (p.pPr?.let { dom.importNode(it, true) as Element }) ?: dom.createElementNS(W, "w:pPr")
        pPr.kid("pPrChange")?.let { pPr.removeChild(it) }
        p.props.styleId?.let { ctx.ensureStyle(it) }
        setChild(pPr, PPR_ORDER, "pStyle", p.props.styleId?.let { mapOf("val" to it) })
        val numId = p.props.numId?.let { if (it.startsWith("+")) ctx.numId(it) else it }
        if (numId == null) setChild(pPr, PPR_ORDER, "numPr", null) else {
            val numPr = setChild(pPr, PPR_ORDER, "numPr", emptyMap())!!
            while (numPr.firstChild != null) numPr.removeChild(numPr.firstChild)
            numPr.appendChild(dom.createElementNS(W, "w:ilvl").apply { setAttributeNS(W, "w:val", p.props.level.toString()) })
            numPr.appendChild(dom.createElementNS(W, "w:numId").apply { setAttributeNS(W, "w:val", numId) })
        }
        setChild(pPr, PPR_ORDER, "jc", p.props.align?.let { mapOf("val" to it) })
        if (pPr.hasChildNodes()) e.appendChild(pPr)

        starts.forEach { e.appendChild(it) }
        var i = 0
        val runs = p.runs
        while (i < runs.size) {
            val link = runs[i].link
            if (link == null) { runNodes(dom, runs[i]).forEach { e.appendChild(it) }; i++; continue }
            val h = dom.importNode(link, false) as Element
            while (i < runs.size && runs[i].link === link) { runNodes(dom, runs[i]).forEach { h.appendChild(it) }; i++ }
            e.appendChild(h)
        }
        ends.forEach { e.appendChild(it) }
    }

    private fun runNodes(dom: Document, r: Run): List<Element> {
        if (r.obj != null) return listOf(dom.importNode(r.obj, true) as Element)
        if (r.text.isEmpty()) return emptyList()
        val wr = dom.createElementNS(W, "w:r")
        buildRPr(dom, r.rPr, r.style)?.let { wr.appendChild(it) }
        val sb = StringBuilder()
        fun flush() {
            if (sb.isEmpty()) return
            val t = dom.createElementNS(W, "w:t")
            if (sb.first().isWhitespace() || sb.last().isWhitespace()) t.setAttributeNS(XML_NS, "xml:space", "preserve")
            t.textContent = sb.toString().replace(BAD_XML, "")
            wr.appendChild(t)
            sb.setLength(0)
        }
        fun add(name: String) { flush(); wr.appendChild(dom.createElementNS(W, "w:$name")) }
        for (ch in r.text) when (ch) {
            '\t' -> add("tab")
            ' ', '\n' -> add("br")
            '‑' -> add("noBreakHyphen")
            '­' -> add("softHyphen")
            Para.OBJ -> Unit
            else -> sb.append(ch)
        }
        flush()
        return listOf(wr)
    }

    private val BAD_XML = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]")

    /** XML for a table that came from another format: simple borders, columns from [Block.T.widths]. */
    private fun newTable(dom: Document, t: Block.T): Block.T {
        val cols = t.rows.maxOfOrNull { it.size } ?: 1
        val widths = t.widths.takeIf { it.size == cols } ?: List(cols) { 9000 / cols }
        val border = (listOf("top", "left", "bottom", "right", "insideH", "insideV")).joinToString("") { """<w:$it w:val="single" w:sz="4" w:space="0" w:color="BFBFBF"/>""" }
        val tbl = dom.importNode(Xml.parse("""<w:tbl xmlns:w="$W"><w:tblPr><w:tblW w:w="0" w:type="auto"/><w:tblBorders>$border</w:tblBorders></w:tblPr><w:tblGrid>${widths.joinToString("") { """<w:gridCol w:w="$it"/>""" }}</w:tblGrid></w:tbl>""").documentElement, true) as Element
        val rows = t.rows.map { row ->
            val tr = dom.createElementNS(W, "w:tr")
            tbl.appendChild(tr)
            row.mapIndexed { i, c ->
                val tc = dom.createElementNS(W, "w:tc")
                tc.appendChild(dom.importNode(Xml.parse("""<w:tcPr xmlns:w="$W"><w:tcW w:w="${widths.getOrElse(i) { 2000 }}" w:type="dxa"/></w:tcPr>""").documentElement, true))
                tr.appendChild(tc)
                Cell(tc, c.paras.ifEmpty { listOf(Para(newId(), listOf(Run("")))) })
            }
        }
        return Block.T(tbl, rows, widths)
    }

    private val TEMPLATE = mapOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="$CT"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Default Extension="jpeg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>""",
        "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$PKG_REL"><Relationship Id="rId1" Type="$R/officeDocument" Target="word/document.xml"/></Relationships>""",
        "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$PKG_REL"><Relationship Id="rId1" Type="$R/styles" Target="styles.xml"/></Relationships>""",
        "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="$W" xmlns:r="$R"><w:body><w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>""",
        "word/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="$W"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style></w:styles>""",
    )
}

package com.prempoddar.oaf.sheet

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node as XNode
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** A workbook read from an .xlsx, with what's needed to save it back without losing the rest of the file. */
class XlsxDoc(val workbook: Workbook, val xfStyles: List<CellStyle>)

/**
 * Reads and writes Office Open XML spreadsheets directly. Saving patches the original package: cell
 * data, column widths, merges and frozen panes are rewritten; charts, drawings, defined names,
 * conditional formats and everything else are carried over untouched.
 */
object Xlsx {
    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val T_SHEET = "$REL/worksheet"
    private const val T_STRINGS = "$REL/sharedStrings"
    private const val T_STYLES = "$REL/styles"
    private const val CT_SHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
    private const val CT_STRINGS = "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"

    private val BUILTIN_FMT = mapOf(
        0 to "General", 1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00", 9 to "0%", 10 to "0.00%", 11 to "0.00E+00",
        14 to "yyyy-mm-dd", 15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy", 18 to "h:mm", 19 to "h:mm:ss", 20 to "h:mm",
        21 to "h:mm:ss", 22 to "yyyy-mm-dd h:mm", 37 to "#,##0", 38 to "#,##0", 39 to "#,##0.00", 40 to "#,##0.00",
        45 to "mm:ss", 46 to "h:mm:ss", 47 to "mm:ss", 48 to "0.0E+0", 49 to "@",
    )

    /** Functions Excel stores with a "_xlfn." prefix because they're newer than the file format. */
    private val NEW_FUNCTIONS = listOf(
        "XLOOKUP", "IFS", "XOR", "CONCAT", "TEXTJOIN", "STDEV.S", "STDEV.P", "VAR.S", "VAR.P", "MODE.SNGL", "RANK.EQ",
        "CEILING.MATH", "FLOOR.MATH", "DAYS", "IFNA",
    )
    private val NEW_FN_RE = Regex("(?<![A-Za-z0-9_.])(${NEW_FUNCTIONS.joinToString("|") { Regex.escape(it) }})\\(", RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------- reading

    fun read(file: File): XlsxDoc = ZipFile(file).use { zip ->
        val bytes = { name: String -> zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes() } } }
        read(bytes)
    }

    internal fun read(part: (String) -> ByteArray?): XlsxDoc {
        val wbPath = workbookPath(part)
        val rels = rels(part, wbPath)
        val wbDoc = dom(part(wbPath) ?: error("Not a spreadsheet"))
        val strings = rels.values.firstOrNull { it.first == T_STRINGS }?.let { part(it.second) }?.let { readStrings(it) } ?: emptyList()
        val styles = rels.values.firstOrNull { it.first == T_STYLES }?.let { part(it.second) }?.let { readStyles(it) } ?: emptyList()
        val wb = Workbook(mutableListOf())
        wbDoc.byTag("sheet").forEach { el ->
            val target = rels[el.getAttributeNS(REL, "id")]?.second ?: return@forEach
            val data = part(target) ?: return@forEach
            val sheet = Sheet(el.getAttribute("name")).apply { sourcePart = target }
            readSheet(data, sheet, strings, styles)
            wb.sheets += sheet
        }
        if (wb.sheets.isEmpty()) wb.sheets += Sheet("Sheet1")
        wb.recalculate()
        return XlsxDoc(wb, styles)
    }

    private fun readStrings(data: ByteArray): List<String> {
        val out = ArrayList<String>()
        sax(data, object : DefaultHandler() {
            val sb = StringBuilder()
            var inT = false
            var phonetic = 0
            override fun startElement(uri: String?, local: String, q: String?, a: Attributes) {
                when (local) { "si" -> sb.setLength(0); "t" -> inT = phonetic == 0; "rPh" -> phonetic++ }
            }
            override fun endElement(uri: String?, local: String, q: String?) {
                when (local) { "si" -> out += sb.toString(); "t" -> inT = false; "rPh" -> phonetic-- }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inT) sb.appendRange(ch, start, start + length) }
        })
        return out
    }

    private fun readStyles(data: ByteArray): List<CellStyle> {
        val doc = dom(data)
        val fmts = HashMap(BUILTIN_FMT)
        doc.byTag("numFmt").forEach { fmts[it.getAttribute("numFmtId").toIntOrNull() ?: return@forEach] = it.getAttribute("formatCode") }
        val fonts = doc.byTag("fonts").firstOrNull()?.children("font").orEmpty()
        val baseSize = fonts.firstOrNull()?.children("sz")?.firstOrNull()?.getAttribute("val")?.toDoubleOrNull()
        val fills = doc.byTag("fills").firstOrNull()?.children("fill").orEmpty()
        val xfs = doc.byTag("cellXfs").firstOrNull()?.children("xf").orEmpty()
        return xfs.map { xf ->
            val font = fonts.getOrNull(xf.getAttribute("fontId").toIntOrNull() ?: 0)
            fun has(tag: String) = font?.children(tag)?.firstOrNull()?.let { it.getAttribute("val").let { v -> v != "0" && v != "false" && v != "none" } } == true
            val size = font?.children("sz")?.firstOrNull()?.getAttribute("val")?.toDoubleOrNull()
            val fill = fills.getOrNull(xf.getAttribute("fillId").toIntOrNull() ?: 0)?.children("patternFill")?.firstOrNull()
                ?.takeIf { it.getAttribute("patternType") == "solid" }?.children("fgColor")?.firstOrNull()?.let { argb(it.getAttribute("rgb")) }
            val align = xf.children("alignment").firstOrNull()
            CellStyle(
                bold = has("b"), italic = has("i"), underline = has("u"), strike = has("strike"),
                color = font?.children("color")?.firstOrNull()?.let { argb(it.getAttribute("rgb")) }?.takeIf { it != 0xFF000000.toInt() },
                fill = fill,
                align = when (align?.getAttribute("horizontal")) {
                    "left" -> CellStyle.Align.LEFT; "center", "centerContinuous" -> CellStyle.Align.CENTER; "right" -> CellStyle.Align.RIGHT
                    else -> CellStyle.Align.GENERAL
                },
                wrap = align?.getAttribute("wrapText") == "1" || align?.getAttribute("wrapText") == "true",
                fontSize = size?.takeIf { it != baseSize },
                numFmt = fmts[xf.getAttribute("numFmtId").toIntOrNull() ?: 0]?.takeIf { it != "General" },
            )
        }
    }

    private fun argb(s: String?): Int? = s?.takeIf { it.length == 8 || it.length == 6 }?.let { (if (it.length == 6) "FF$it" else it).toLongOrNull(16)?.toInt() }

    private fun readSheet(data: ByteArray, sheet: Sheet, strings: List<String>, styles: List<CellStyle>) {
        val shared = HashMap<String, Triple<String, Int, Int>>()
        sax(data, object : DefaultHandler() {
            var row = -1; var col = -1
            var type = ""; var style = 0
            var text: StringBuilder? = null
            var v: String? = null; var f: String? = null; var fShared: String? = null; var fRef = false
            var inline = StringBuilder(); var inInline = false

            override fun startElement(uri: String?, local: String, q: String?, a: Attributes) {
                when (local) {
                    "row" -> { row = a.getValue("r")?.toIntOrNull()?.minus(1) ?: (row + 1); col = -1 }
                    "c" -> {
                        val ref = a.getValue("r")?.let { A1.parse(it) }
                        if (ref != null) { row = ref.first; col = ref.second } else col++
                        type = a.getValue("t") ?: ""; style = a.getValue("s")?.toIntOrNull() ?: 0
                        v = null; f = null; fShared = null; fRef = false; inline.setLength(0)
                    }
                    "v" -> text = StringBuilder()
                    "f" -> { text = StringBuilder(); fShared = if (a.getValue("t") == "shared") a.getValue("si") else null; fRef = a.getValue("ref") != null }
                    "is" -> inInline = true
                    "t" -> if (inInline) text = StringBuilder()
                    "col" -> {
                        val min = a.getValue("min")?.toIntOrNull() ?: return; val max = a.getValue("max")?.toIntOrNull() ?: return
                        val w = a.getValue("width")?.toFloatOrNull() ?: return
                        if (max - min < 256) for (c in min - 1 until max) sheet.colWidths[c] = w
                    }
                    "pane" -> if (a.getValue("state")?.startsWith("frozen") == true) {
                        sheet.frozenCols = a.getValue("xSplit")?.toDoubleOrNull()?.toInt() ?: 0
                        sheet.frozenRows = a.getValue("ySplit")?.toDoubleOrNull()?.toInt() ?: 0
                    }
                    "mergeCell" -> a.getValue("ref")?.let { A1.parseRange(it) }?.let { sheet.merges += it }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) { text?.appendRange(ch, start, start + length) }

            override fun endElement(uri: String?, local: String, q: String?) {
                when (local) {
                    "v" -> { v = text.toString(); text = null }
                    "f" -> { f = text.toString(); text = null }
                    "t" -> if (inInline) { inline.append(text ?: ""); text = null }
                    "is" -> inInline = false
                    "c" -> finishCell()
                }
            }

            private fun finishCell() {
                val cached: Value = when (type) {
                    "s" -> Value.Text(strings.getOrNull(v?.toIntOrNull() ?: -1) ?: "")
                    "str" -> Value.Text(v ?: "")
                    "inlineStr" -> Value.Text(inline.toString())
                    "b" -> Value.Bool(v == "1")
                    "e" -> Value.Err(v ?: "#N/A")
                    else -> v?.toDoubleOrNull()?.let { Value.Num(it) } ?: Value.Empty
                }
                var formula = f?.takeIf { it.isNotEmpty() }
                fShared?.let { si ->
                    if (formula != null) shared[si] = Triple(formula!!, row, col)
                    else shared[si]?.let { (src, r0, c0) -> formula = Refs.shift(src, row - r0, col - c0) }
                }
                val st = styles.getOrNull(style) ?: CellStyle.DEFAULT
                val input = when {
                    formula != null -> "=" + formula!!.replace(Regex("_xl(fn|ws)\\."), "")
                    cached is Value.Text -> cached.v.let { if (Workbook.literal(it) !is Value.Text || it.startsWith("=") || it.startsWith("'")) "'$it" else it }
                    cached is Value.Num -> v!!.trim()
                    else -> cached.display()
                }
                if (input.isEmpty() && st == CellStyle.DEFAULT) return
                sheet.set(row, col, input, st)
                if (formula != null) sheet[row, col]?.let { it.cached = cached; it.value = cached }
            }
        })
    }

    // ---------------------------------------------------------------- writing

    /** Writes [doc]; when [original] is given, everything [doc] doesn't model is kept from it. */
    fun write(doc: XlsxDoc, original: File?, out: OutputStream) {
        val parts = LinkedHashMap<String, ByteArray>()
        if (original != null) {
            ZipFile(original).use { zip -> zip.entries().asSequence().filter { !it.isDirectory }.forEach { e -> parts[e.name] = zip.getInputStream(e).use { it.readBytes() } } }
        } else {
            TEMPLATE.forEach { (k, v) -> parts[k] = v.toByteArray() }
        }
        patch(doc, parts)
        ZipOutputStream(out).use { zip ->
            val ordered = parts.keys.sortedBy { if (it == "[Content_Types].xml") 0 else 1 }
            ordered.forEach { name -> zip.putNextEntry(ZipEntry(name)); zip.write(parts.getValue(name)); zip.closeEntry() }
        }
    }

    private fun patch(doc: XlsxDoc, parts: MutableMap<String, ByteArray>) {
        val wb = doc.workbook
        val wbPath = workbookPath { parts[it] }
        val dir = wbPath.substringBeforeLast('/', "")
        val relsPath = relsPath(wbPath)
        val relsDoc = dom(parts[relsPath] ?: EMPTY_RELS.toByteArray())
        val ctDoc = dom(parts.getValue("[Content_Types].xml"))
        val wbDoc = dom(parts.getValue(wbPath))
        val rels = rels({ parts[it] }, wbPath)

        // Styles.
        val stylesPath = rels.values.firstOrNull { it.first == T_STYLES }?.second ?: "$dir/styles.xml".removePrefix("/").also {
            parts[it] = TEMPLATE.getValue("xl/styles.xml").toByteArray()
            addRel(relsDoc, T_STYLES, relTarget(dir, it))
            addOverride(ctDoc, it, "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml")
        }
        val styles = StyleWriter(dom(parts.getValue(stylesPath)), doc.xfStyles)

        // Shared strings: keep the original table (rich text included) and add what's new.
        val stringsPath = rels.values.firstOrNull { it.first == T_STRINGS }?.second ?: "$dir/sharedStrings.xml".removePrefix("/").also {
            addRel(relsDoc, T_STRINGS, relTarget(dir, it))
            addOverride(ctDoc, it, CT_STRINGS)
        }
        val sst = parts[stringsPath]?.let { dom(it) } ?: dom("""<sst xmlns="$MAIN"/>""".toByteArray())
        val sstIndex = HashMap<String, Int>()
        readStrings(serialize(sst)).forEachIndexed { i, s -> sstIndex.putIfAbsent(s, i) }
        var sstCount = sst.documentElement.children("si").size
        var sstRefs = 0
        fun stringIndex(s: String): Int {
            sstRefs++
            return sstIndex.getOrPut(s) {
                val si = sst.createElementNS(MAIN, "si")
                val t = sst.createElementNS(MAIN, "t")
                if (s != s.trim() || s.contains('\n')) t.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve")
                t.textContent = clean(s)
                si.appendChild(t); sst.documentElement.appendChild(si)
                sstCount++
            }
        }

        // Sheets.
        val sheetsEl = wbDoc.byTag("sheets").first()
        val oldSheets = sheetsEl.children("sheet")
        val byTarget = oldSheets.associateBy { rels[it.getAttributeNS(REL, "id")]?.second }
        var nextSheetId = (oldSheets.maxOfOrNull { it.getAttribute("sheetId").toIntOrNull() ?: 0 } ?: 0) + 1
        val kept = HashSet<String>()
        oldSheets.forEach { sheetsEl.removeChild(it) }
        val reordered = wb.sheets.mapNotNull { it.sourcePart } != oldSheets.mapNotNull { rels[it.getAttributeNS(REL, "id")]?.second }
        wb.sheets.forEachIndexed { index, sheet ->
            var path = sheet.sourcePart?.takeIf { parts.containsKey(it) && byTarget.containsKey(it) }
            val el: Element
            if (path != null) {
                el = byTarget.getValue(path)
            } else {
                var n = index + 1
                while (parts.containsKey("$dir/worksheets/sheet$n.xml".removePrefix("/"))) n++
                path = "$dir/worksheets/sheet$n.xml".removePrefix("/")
                val rid = addRel(relsDoc, T_SHEET, relTarget(dir, path))
                addOverride(ctDoc, path, CT_SHEET)
                el = wbDoc.createElementNS(MAIN, "sheet")
                el.setAttribute("sheetId", (nextSheetId++).toString())
                el.setAttributeNS(REL, "r:id", rid)
                sheet.sourcePart = path
            }
            el.setAttribute("name", sheet.name)
            sheetsEl.appendChild(el)
            kept += path
            val sheetDoc = parts[path]?.let { dom(it) } ?: dom("""<worksheet xmlns="$MAIN" xmlns:r="$REL"><sheetData/></worksheet>""".toByteArray())
            writeSheet(sheetDoc, sheet, styles, ::stringIndex)
            parts[path] = serialize(sheetDoc)
        }
        // Sheets that were deleted, and the calculation chain (Excel rebuilds it).
        val dropped = byTarget.keys.filterNotNull().filter { it !in kept }.toMutableList()
        rels.values.filter { it.first.endsWith("/calcChain") }.forEach { dropped += it.second }
        dropped.forEach { p ->
            parts.remove(p); parts.remove(relsPath(p))
            relsDoc.documentElement.children("Relationship").filter { resolve(dir, it.getAttribute("Target")) == p }.forEach { it.parentNode.removeChild(it) }
            ctDoc.documentElement.children("Override").filter { it.getAttribute("PartName") == "/$p" }.forEach { it.parentNode.removeChild(it) }
        }
        if (reordered || dropped.isNotEmpty()) {
            wbDoc.byTag("definedName").filter { it.hasAttribute("localSheetId") }.forEach { it.parentNode.removeChild(it) }
            wbDoc.byTag("workbookView").forEach { it.removeAttribute("activeTab"); it.removeAttribute("firstSheet") }
        }
        // Ask Excel to recalculate on open so values we couldn't compute are refreshed.
        val calc = wbDoc.byTag("calcPr").firstOrNull() ?: wbDoc.createElementNS(MAIN, "calcPr").also { c ->
            val after = wbDoc.documentElement.children().lastOrNull { it.localName in setOf("sheets", "functionGroups", "externalReferences", "definedNames") }
            wbDoc.documentElement.insertBefore(c, after?.nextSibling)
        }
        calc.setAttribute("fullCalcOnLoad", "1")

        sst.documentElement.setAttribute("count", sstRefs.toString())
        sst.documentElement.setAttribute("uniqueCount", sstCount.toString())
        parts[stringsPath] = serialize(sst)
        parts[stylesPath] = serialize(styles.finish())
        parts[wbPath] = serialize(wbDoc)
        parts[relsPath] = serialize(relsDoc)
        parts["[Content_Types].xml"] = serialize(ctDoc)
    }

    private fun writeSheet(doc: Document, sheet: Sheet, styles: StyleWriter, stringIndex: (String) -> Int) {
        val root = doc.documentElement
        fun el(name: String) = doc.createElementNS(MAIN, name)
        fun insertInOrder(e: Element) {
            val pos = ORDER.indexOf(e.localName)
            val before = root.children().firstOrNull { ORDER.indexOf(it.localName) > pos }
            root.insertBefore(e, before)
        }

        val maxR = sheet.maxRow; val maxC = sheet.maxCol
        val dim = root.children("dimension").firstOrNull() ?: el("dimension").also { insertInOrder(it) }
        dim.setAttribute("ref", if (maxR < 0) "A1" else "A1:" + A1.name(maxR, maxC))

        // Frozen panes.
        val views = root.children("sheetViews").firstOrNull() ?: el("sheetViews").also { insertInOrder(it) }
        val view = views.children("sheetView").firstOrNull() ?: el("sheetView").also { it.setAttribute("workbookViewId", "0"); views.appendChild(it) }
        view.children("pane").forEach { view.removeChild(it) }
        if (sheet.frozenRows > 0 || sheet.frozenCols > 0) {
            view.children("selection").forEach { view.removeChild(it) }
            val pane = el("pane")
            if (sheet.frozenCols > 0) pane.setAttribute("xSplit", sheet.frozenCols.toString())
            if (sheet.frozenRows > 0) pane.setAttribute("ySplit", sheet.frozenRows.toString())
            pane.setAttribute("topLeftCell", A1.name(sheet.frozenRows, sheet.frozenCols))
            pane.setAttribute("activePane", when { sheet.frozenRows > 0 && sheet.frozenCols > 0 -> "bottomRight"; sheet.frozenRows > 0 -> "bottomLeft"; else -> "topRight" })
            pane.setAttribute("state", "frozen")
            view.insertBefore(pane, view.firstChild)
        } else if (view.children("selection").any { it.hasAttribute("pane") }) {
            view.children("selection").forEach { view.removeChild(it) }
        }

        // Column widths.
        val cols = root.children("cols").firstOrNull()
        if (cols == null || sheet.colsChanged) {
            cols?.let { root.removeChild(it) }
            if (sheet.colWidths.isNotEmpty()) {
                val newCols = el("cols")
                sheet.colWidths.keys.sorted().forEach { c ->
                    newCols.appendChild(el("col").apply {
                        setAttribute("min", (c + 1).toString()); setAttribute("max", (c + 1).toString())
                        setAttribute("width", sheet.colWidths.getValue(c).toString()); setAttribute("customWidth", "1")
                    })
                }
                insertInOrder(newCols)
            }
        } else {
            // Widths changed by hand are written over the original entries.
            val existing = cols.children("col")
            sheet.colWidths.forEach { (c, w) ->
                val hit = existing.firstOrNull { (it.getAttribute("min").toIntOrNull() ?: 0) == c + 1 && it.getAttribute("max") == (c + 1).toString() }
                if (hit != null) { hit.setAttribute("width", w.toString()); hit.setAttribute("customWidth", "1") }
                else if (existing.none { c + 1 in (it.getAttribute("min").toIntOrNull() ?: 0)..(it.getAttribute("max").toIntOrNull() ?: 0) }) {
                    val e = el("col").apply { setAttribute("min", (c + 1).toString()); setAttribute("max", (c + 1).toString()); setAttribute("width", w.toString()); setAttribute("customWidth", "1") }
                    cols.insertBefore(e, existing.firstOrNull { (it.getAttribute("min").toIntOrNull() ?: 0) > c + 1 })
                }
            }
        }

        // Cell data. Row attributes (heights, hidden) survive unless rows were moved.
        val data = root.children("sheetData").firstOrNull() ?: el("sheetData").also { insertInOrder(it) }
        val oldRows = if (sheet.rowsChanged) emptyMap() else data.children("row").associateBy { it.getAttribute("r").toIntOrNull() ?: -1 }
        while (data.firstChild != null) data.removeChild(data.firstChild)
        val byRow = sheet.cells.keys.groupBy { sheet.row(it) }
        (byRow.keys + oldRows.keys.map { it - 1 }.filter { it >= 0 }).toSortedSet().forEach { r ->
            val rowEl = el("row")
            oldRows[r + 1]?.let { old ->
                val attrs = old.attributes
                for (i in 0 until attrs.length) { val a = attrs.item(i); if (a.nodeName != "spans") rowEl.setAttribute(a.nodeName, a.nodeValue) }
            }
            rowEl.setAttribute("r", (r + 1).toString())
            byRow[r].orEmpty().sortedBy { sheet.col(it) }.forEach { k ->
                val cell = sheet.cells.getValue(k)
                val c = el("c")
                c.setAttribute("r", A1.name(r, sheet.col(k)))
                styles.index(cell.style).takeIf { it != 0 }?.let { c.setAttribute("s", it.toString()) }
                if (cell.isFormula) {
                    val f = el("f")
                    f.textContent = clean(withPrefixes(cell.input.substring(1)))
                    c.appendChild(f)
                    when (val v = cell.value) {
                        is Value.Num -> c.appendChild(el("v").apply { textContent = num(v.v) })
                        is Value.Text -> { c.setAttribute("t", "str"); c.appendChild(el("v").apply { textContent = clean(v.v) }) }
                        is Value.Bool -> { c.setAttribute("t", "b"); c.appendChild(el("v").apply { textContent = if (v.v) "1" else "0" }) }
                        is Value.Err -> if (v.code in EXCEL_ERRORS) { c.setAttribute("t", "e"); c.appendChild(el("v").apply { textContent = v.code }) }
                        Value.Empty -> Unit
                    }
                } else {
                    when (val v = Workbook.literal(cell.input)) {
                        is Value.Num -> c.appendChild(el("v").apply { textContent = num(v.v) })
                        is Value.Text -> { c.setAttribute("t", "s"); c.appendChild(el("v").apply { textContent = stringIndex(v.v).toString() }) }
                        is Value.Bool -> { c.setAttribute("t", "b"); c.appendChild(el("v").apply { textContent = if (v.v) "1" else "0" }) }
                        else -> Unit
                    }
                }
                rowEl.appendChild(c)
            }
            data.appendChild(rowEl)
        }

        // Merged cells.
        root.children("mergeCells").forEach { root.removeChild(it) }
        if (sheet.merges.isNotEmpty()) {
            val m = el("mergeCells")
            m.setAttribute("count", sheet.merges.size.toString())
            sheet.merges.forEach { r -> m.appendChild(el("mergeCell").apply { setAttribute("ref", A1.name(r.r1, r.c1) + ":" + A1.name(r.r2, r.c2)) }) }
            insertInOrder(m)
        }
    }

    private val EXCEL_ERRORS = setOf("#NULL!", "#DIV/0!", "#VALUE!", "#REF!", "#NAME?", "#NUM!", "#N/A")

    /** Child order the worksheet schema requires. */
    private val ORDER = listOf(
        "sheetPr", "dimension", "sheetViews", "sheetFormatPr", "cols", "sheetData", "sheetCalcPr", "sheetProtection", "protectedRanges",
        "scenarios", "autoFilter", "sortState", "dataConsolidate", "customSheetViews", "mergeCells", "phoneticPr", "conditionalFormatting",
        "dataValidations", "hyperlinks", "printOptions", "pageMargins", "pageSetup", "headerFooter", "rowBreaks", "colBreaks",
        "customProperties", "cellWatches", "ignoredErrors", "smartTags", "drawing", "legacyDrawing", "legacyDrawingHF", "drawingHF",
        "picture", "oleObjects", "controls", "webPublishItems", "tableParts", "extLst",
    )

    private fun withPrefixes(f: String): String {
        // Leave string literals alone.
        val parts = f.split('"')
        return parts.mapIndexed { i, p -> if (i % 2 == 0) NEW_FN_RE.replace(p) { "_xlfn." + it.groupValues[1].uppercase() + "(" } else p }.joinToString("\"")
    }

    private fun num(d: Double): String = if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private val BAD_XML = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]")
    private fun clean(s: String) = BAD_XML.replace(s, "")

    /** Maps [CellStyle]s to cellXfs indexes, adding fonts, fills, formats and xfs as needed. */
    private class StyleWriter(private val doc: Document, loaded: List<CellStyle>) {
        private val index = HashMap<CellStyle, Int>()
        private val fonts = doc.byTag("fonts").firstOrNull() ?: doc.createElementNS(MAIN, "fonts").also { doc.documentElement.insertBefore(it, doc.documentElement.firstChild) }
        private val fills = doc.byTag("fills").first()
        private val xfs = doc.byTag("cellXfs").first()
        private val fontCache = HashMap<CellStyle, Int>()
        private val fillCache = HashMap<Int, Int>()
        private val fmtCache = HashMap<String, Int>()

        init {
            loaded.forEachIndexed { i, s -> index.putIfAbsent(s, i) }
            BUILTIN_FMT.forEach { (id, code) -> fmtCache.putIfAbsent(code, id) }
            doc.byTag("numFmt").forEach { fmtCache[it.getAttribute("formatCode")] = it.getAttribute("numFmtId").toIntOrNull() ?: return@forEach }
            index.putIfAbsent(CellStyle.DEFAULT, 0)
        }

        fun index(style: CellStyle): Int = index.getOrPut(style) {
            val xf = (xfs.children("xf").firstOrNull()?.cloneNode(false) as? Element) ?: doc.createElementNS(MAIN, "xf")
            xf.setAttribute("fontId", font(style).toString()); xf.setAttribute("applyFont", "1")
            xf.setAttribute("fillId", (style.fill?.let { fill(it) } ?: 0).toString()); xf.setAttribute("applyFill", "1")
            xf.setAttribute("numFmtId", (style.numFmt?.let { fmt(it) } ?: 0).toString()); xf.setAttribute("applyNumberFormat", "1")
            if (!xf.hasAttribute("borderId")) xf.setAttribute("borderId", "0")
            if (!xf.hasAttribute("xfId")) xf.setAttribute("xfId", "0")
            if (style.align != CellStyle.Align.GENERAL || style.wrap) {
                val al = doc.createElementNS(MAIN, "alignment")
                if (style.align != CellStyle.Align.GENERAL) al.setAttribute("horizontal", style.align.name.lowercase())
                if (style.wrap) al.setAttribute("wrapText", "1")
                xf.appendChild(al); xf.setAttribute("applyAlignment", "1")
            }
            xfs.appendChild(xf)
            xfs.setAttribute("count", xfs.children("xf").size.toString())
            xfs.children("xf").size - 1
        }

        private fun font(s: CellStyle): Int {
            val key = CellStyle(bold = s.bold, italic = s.italic, underline = s.underline, strike = s.strike, color = s.color, fontSize = s.fontSize)
            if (key == CellStyle.DEFAULT) return 0
            return fontCache.getOrPut(key) {
                val base = fonts.children("font").firstOrNull()
                val f = (base?.cloneNode(true) as? Element) ?: doc.createElementNS(MAIN, "font")
                f.children().filter { it.localName in setOf("b", "i", "u", "strike", "color") }.forEach { f.removeChild(it) }
                fun add(tag: String, attr: String? = null, value: String? = null) =
                    f.insertBefore(doc.createElementNS(MAIN, tag).apply { if (attr != null) setAttribute(attr, value) }, f.firstChild)
                if (s.color != null) add("color", "rgb", "%08X".format(s.color))
                if (s.strike) add("strike")
                if (s.underline) add("u")
                if (s.italic) add("i")
                if (s.bold) add("b")
                s.fontSize?.let { size -> (f.children("sz").firstOrNull() ?: doc.createElementNS(MAIN, "sz").also { f.appendChild(it) }).setAttribute("val", Value.formatNumber(size)) }
                fonts.appendChild(f)
                fonts.setAttribute("count", fonts.children("font").size.toString())
                fonts.children("font").size - 1
            }
        }

        private fun fill(argb: Int): Int = fillCache.getOrPut(argb) {
            val f = doc.createElementNS(MAIN, "fill")
            val p = doc.createElementNS(MAIN, "patternFill").apply { setAttribute("patternType", "solid") }
            p.appendChild(doc.createElementNS(MAIN, "fgColor").apply { setAttribute("rgb", "%08X".format(argb)) })
            p.appendChild(doc.createElementNS(MAIN, "bgColor").apply { setAttribute("indexed", "64") })
            f.appendChild(p); fills.appendChild(f)
            fills.setAttribute("count", fills.children("fill").size.toString())
            fills.children("fill").size - 1
        }

        private fun fmt(code: String): Int = fmtCache.getOrPut(code) {
            val numFmts = doc.byTag("numFmts").firstOrNull() ?: doc.createElementNS(MAIN, "numFmts").also { doc.documentElement.insertBefore(it, doc.documentElement.firstChild) }
            val id = maxOf(164, (numFmts.children("numFmt").maxOfOrNull { it.getAttribute("numFmtId").toIntOrNull() ?: 0 } ?: 0) + 1)
            numFmts.appendChild(doc.createElementNS(MAIN, "numFmt").apply { setAttribute("numFmtId", id.toString()); setAttribute("formatCode", code) })
            numFmts.setAttribute("count", numFmts.children("numFmt").size.toString())
            id
        }

        fun finish() = doc
    }

    // ---------------------------------------------------------------- package plumbing

    private fun workbookPath(part: (String) -> ByteArray?): String =
        part("_rels/.rels")?.let { dom(it) }?.documentElement?.children("Relationship")
            ?.firstOrNull { it.getAttribute("Type").endsWith("/officeDocument") }?.getAttribute("Target")?.removePrefix("/")
            ?: "xl/workbook.xml"

    private fun relsPath(part: String): String {
        val dir = part.substringBeforeLast('/', "")
        val name = part.substringAfterLast('/')
        return (if (dir.isEmpty()) "" else "$dir/") + "_rels/$name.rels"
    }

    /** Relationship id → (type, resolved part path). */
    private fun rels(part: (String) -> ByteArray?, owner: String): Map<String, Pair<String, String>> {
        val dir = owner.substringBeforeLast('/', "")
        val doc = part(relsPath(owner))?.let { dom(it) } ?: return emptyMap()
        return doc.documentElement.children("Relationship").filter { it.getAttribute("TargetMode") != "External" }
            .associate { it.getAttribute("Id") to (it.getAttribute("Type") to resolve(dir, it.getAttribute("Target"))) }
    }

    private fun resolve(dir: String, target: String): String {
        if (target.startsWith("/")) return target.substring(1)
        val stack = ArrayList(if (dir.isEmpty()) emptyList() else dir.split('/'))
        target.split('/').forEach { seg -> when (seg) { ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1); ".", "" -> Unit; else -> stack += seg } }
        return stack.joinToString("/")
    }

    private fun relTarget(dir: String, path: String) = if (dir.isNotEmpty() && path.startsWith("$dir/")) path.substring(dir.length + 1) else "/$path"

    private fun addRel(doc: Document, type: String, target: String): String {
        val existing = doc.documentElement.children("Relationship").map { it.getAttribute("Id") }.toSet()
        var n = existing.size + 1
        while ("rId$n" in existing) n++
        doc.documentElement.appendChild(doc.createElementNS(PKG_REL, "Relationship").apply {
            setAttribute("Id", "rId$n"); setAttribute("Type", type); setAttribute("Target", target)
        })
        return "rId$n"
    }

    private fun addOverride(doc: Document, path: String, type: String) {
        if (doc.documentElement.children("Override").any { it.getAttribute("PartName") == "/$path" }) return
        doc.documentElement.appendChild(doc.createElementNS(CT, "Override").apply { setAttribute("PartName", "/$path"); setAttribute("ContentType", type) })
    }

    private const val EMPTY_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$PKG_REL"/>"""

    /** The smallest package Excel accepts; sheets, strings and styles are filled in by [patch]. */
    private val TEMPLATE = mapOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="$CT"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
        "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$PKG_REL"><Relationship Id="rId1" Type="$REL/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="$MAIN" xmlns:r="$REL"><bookViews><workbookView/></bookViews><sheets/></workbook>""",
        "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="$PKG_REL"><Relationship Id="rId1" Type="$T_STYLES" Target="styles.xml"/></Relationships>""",
        "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="$MAIN"><fonts count="1"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""",
    )

    // ---------------------------------------------------------------- XML helpers

    private fun dom(data: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return f.newDocumentBuilder().parse(InputSource(ByteArrayInputStream(data)))
    }

    private fun sax(data: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        f.newSAXParser().parse(ByteArrayInputStream(data) as InputStream, handler)
    }

    private fun serialize(doc: Document): ByteArray {
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        t.setOutputProperty(OutputKeys.STANDALONE, "yes")
        doc.xmlStandalone = true
        return ByteArrayOutputStream().also { t.transform(DOMSource(doc), StreamResult(it)) }.toByteArray()
    }

    private fun Document.byTag(local: String): List<Element> {
        val nl = getElementsByTagNameNS("*", local)
        return (0 until nl.length).map { nl.item(it) as Element }
    }

    private fun Element.children(local: String? = null): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) { if (n.nodeType == XNode.ELEMENT_NODE && (local == null || n.localName == local)) out += n as Element; n = n.nextSibling }
        return out
    }
}

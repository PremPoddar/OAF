package com.prempoddar.oaf.sheet

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/** OpenDocument spreadsheets (LibreOffice, Google Sheets exports). Read only; saving makes an .xlsx. */
object Ods {
    private const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"

    fun read(file: File): Workbook = ZipFile(file).use { zip ->
        val entry = zip.getEntry("content.xml") ?: error("Not an OpenDocument spreadsheet")
        val wb = Workbook(mutableListOf())
        val f = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        zip.getInputStream(entry).use { input -> f.newSAXParser().parse(input, Handler(wb)) }
        if (wb.sheets.isEmpty()) wb.sheets += Sheet("Sheet1")
        wb.recalculate()
        wb
    }

    private class Handler(val wb: Workbook) : DefaultHandler() {
        var sheet: Sheet? = null
        var row = 0; var col = 0
        var rowRepeat = 1; var colRepeat = 1
        var formula: String? = null; var value: String? = null; var type: String? = null
        var text: StringBuilder? = null; var paragraphs = 0
        var cellDepth = 0

        override fun startElement(uri: String?, local: String, q: String?, a: Attributes) {
            when {
                uri == TABLE && local == "table" -> { sheet = Sheet(a.getValue(TABLE, "name") ?: wb.uniqueSheetName()).also { wb.sheets += it }; row = 0 }
                uri == TABLE && local == "table-column" -> Unit
                uri == TABLE && local == "table-row" -> { rowRepeat = a.getValue(TABLE, "number-rows-repeated")?.toIntOrNull() ?: 1; col = 0 }
                uri == TABLE && (local == "table-cell" || local == "covered-table-cell") -> {
                    colRepeat = a.getValue(TABLE, "number-columns-repeated")?.toIntOrNull() ?: 1
                    formula = a.getValue(TABLE, "formula")
                    type = a.getValue(OFFICE, "value-type")
                    value = when (type) {
                        "float", "percentage", "currency" -> a.getValue(OFFICE, "value")
                        "date" -> a.getValue(OFFICE, "date-value")
                        "boolean" -> a.getValue(OFFICE, "boolean-value")
                        "time" -> a.getValue(OFFICE, "time-value")
                        else -> null
                    }
                    text = StringBuilder(); paragraphs = 0; cellDepth++
                    val span = a.getValue(TABLE, "number-columns-spanned")?.toIntOrNull() ?: 1
                    val rspan = a.getValue(TABLE, "number-rows-spanned")?.toIntOrNull() ?: 1
                    if (span > 1 || rspan > 1) sheet?.merges?.add(CellRange(row, col, row + rspan - 1, col + span - 1))
                }
                uri == TEXT && local == "p" && text != null -> { if (paragraphs++ > 0) text!!.append('\n') }
                uri == TEXT && local == "s" -> text?.append(" ".repeat(a.getValue(TEXT, "c")?.toIntOrNull() ?: 1))
                uri == TEXT && local == "tab" -> text?.append('\t')
                uri == TEXT && local == "line-break" -> text?.append('\n')
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) { text?.appendRange(ch, start, start + length) }

        override fun endElement(uri: String?, local: String, q: String?) {
            when {
                uri == TABLE && (local == "table-cell" || local == "covered-table-cell") -> {
                    cellDepth--
                    val input = input()
                    // Repeats of empty cells pad rows out to 16384 columns; only real content is stored.
                    if (input.isNotEmpty()) {
                        val reps = if (rowRepeat > 1000) 1 else rowRepeat
                        for (dr in 0 until reps) for (dc in 0 until colRepeat.coerceAtMost(1000)) sheet?.set(row + dr, col + dc, input)
                    }
                    col += colRepeat
                    text = null
                }
                uri == TABLE && local == "table-row" -> row += rowRepeat
            }
        }

        private fun input(): String {
            formula?.let { return "=" + convert(it) }
            val t = text?.toString().orEmpty()
            return when (type) {
                "float", "percentage", "currency" -> value ?: t
                "boolean" -> if (value == "true") "TRUE" else "FALSE"
                "date" -> value?.let { d -> runCatching {
                    val date = java.time.LocalDate.parse(d.take(10))
                    val frac = if (d.length > 10) java.time.LocalTime.parse(d.substring(11).take(8)).toSecondOfDay() / 86_400.0 else 0.0
                    Value.formatNumber(Evaluator.serial(date) + frac)
                }.getOrNull() } ?: t
                else -> Csv.input(t)
            }
        }
    }

    /** "of:=SUM([.A1:.B2];[Sheet2.C3])" → "SUM(A1:B2,Sheet2!C3)". */
    fun convert(f: String): String {
        val body = f.substringAfter(":=", f.removePrefix("="))
        val out = StringBuilder()
        var inString = false
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            when {
                ch == '"' -> { inString = !inString; out.append(ch) }
                inString -> out.append(ch)
                ch == '[' -> {
                    val end = body.indexOf(']', i)
                    out.append(ref(body.substring(i + 1, if (end < 0) body.length else end)))
                    i = if (end < 0) body.length else end
                }
                ch == ';' -> out.append(',')
                else -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }

    private fun ref(r: String): String = r.split(':').joinToString(":") { part ->
        val sheet = part.substringBeforeLast('.', "").removePrefix("$").removeSurrounding("'")
        val cell = part.substringAfterLast('.')
        if (sheet.isEmpty()) cell else (if (sheet.any { !it.isLetterOrDigit() && it != '_' }) "'${sheet.replace("'", "''")}'" else sheet) + "!" + cell
    }.let { joined ->
        // A range written with the sheet on both ends keeps it only on the first.
        val parts = joined.split(':')
        if (parts.size == 2 && parts[1].contains('!')) parts[0] + ":" + parts[1].substringAfter('!') else joined
    }
}

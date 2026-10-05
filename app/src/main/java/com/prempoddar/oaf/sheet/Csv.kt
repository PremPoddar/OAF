package com.prempoddar.oaf.sheet

import com.prempoddar.oaf.text.TextFormat
import com.prempoddar.oaf.text.TextLoad
import java.io.File

/** Delimited text: the separator and encoding found when reading, reused when saving. */
data class CsvDialect(val delimiter: Char, val format: TextFormat)

object Csv {
    private val CANDIDATES = charArrayOf('\t', ';', ',', '|')

    fun read(file: File, tsv: Boolean = false): Pair<Workbook, CsvDialect> {
        val format = TextLoad.format(file.inputStream().use { s -> ByteArray(8192).let { b -> b.copyOf(s.read(b).coerceAtLeast(0)) } })
        val text = TextLoad.read(file, format)
        val delimiter = if (tsv) '\t' else detect(text)
        val wb = Workbook(mutableListOf(Sheet(file.nameWithoutExtension.take(31).ifBlank { "Sheet1" })))
        load(wb.sheets[0], parse(text, delimiter))
        wb.recalculate()
        return wb to CsvDialect(delimiter, format)
    }

    fun load(sheet: Sheet, rows: List<List<String>>) {
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, field -> if (field.isNotEmpty()) sheet.set(r, c, input(field)) } }
    }

    /**
     * How a field becomes cell input. Text that would otherwise turn into a formula or lose its
     * shape (leading zeros, as in phone numbers and codes) is kept as text.
     */
    fun input(field: String): String = when {
        field.startsWith("=") || field.startsWith("'") -> "'$field"
        field.length > 1 && field[0] == '0' && field[1].isDigit() && field.all { it.isDigit() } -> "'$field"
        else -> field
    }

    /** Picks the separator that splits the first lines into the most consistent number of fields. */
    fun detect(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(20).toList()
        if (lines.isEmpty()) return ','
        val scores = CANDIDATES.associateWith { d ->
            val counts = lines.map { line -> countOutsideQuotes(line, d) }
            val first = counts.first()
            if (first == 0) -1.0 else counts.count { it == first }.toDouble() / counts.size * 1000 + first
        }
        return scores.entries.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var n = 0; var q = false
        line.forEach { ch -> if (ch == '"') q = !q else if (ch == d && !q) n++ }
        return n
    }

    /** RFC 4180, forgiving: quotes may appear mid-field and line endings may be any kind. */
    fun parse(text: String, delimiter: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                delimiter -> { row += field.toString(); field.setLength(0) }
                '\r' -> Unit
                '\n' -> { row += field.toString(); field.setLength(0); rows += row; row = ArrayList() }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }

    /** Formulas are saved as their results; everything else exactly as typed. */
    fun write(sheet: Sheet, delimiter: Char): String {
        val sb = StringBuilder()
        val maxC = sheet.maxCol
        for (r in 0..sheet.maxRow) {
            for (c in 0..maxC) {
                if (c > 0) sb.append(delimiter)
                val cell = sheet[r, c] ?: continue
                val text = if (cell.isFormula) cell.value.display() else cell.input.removePrefix("'")
                sb.append(quote(text, delimiter))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun quote(s: String, d: Char) =
        if (s.any { it == d || it == '"' || it == '\n' || it == '\r' } || s != s.trim()) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

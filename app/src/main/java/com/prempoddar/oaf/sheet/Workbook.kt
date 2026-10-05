package com.prempoddar.oaf.sheet

/** A computed value in a cell. */
sealed interface Value {
    data class Num(val v: Double) : Value
    data class Text(val v: String) : Value
    data class Bool(val v: Boolean) : Value
    data class Err(val code: String) : Value
    data object Empty : Value

    fun display(): String = when (this) {
        is Num -> formatNumber(v)
        is Text -> v
        is Bool -> if (v) "TRUE" else "FALSE"
        is Err -> code
        Empty -> ""
    }

    companion object {
        val DIV0 = Err("#DIV/0!")
        val VALUE = Err("#VALUE!")
        val REF = Err("#REF!")
        val NAME = Err("#NAME?")
        val NA = Err("#N/A")
        val NUM = Err("#NUM!")
        val CIRC = Err("#CIRC!")

        fun formatNumber(d: Double): String {
            if (d.isNaN() || d.isInfinite()) return "#NUM!"
            if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) return d.toLong().toString()
            val s = "%.10g".format(java.util.Locale.ROOT, d)
            return if (s.contains('e')) s else s.trimEnd('0').trimEnd('.')
        }
    }
}

/** How a cell looks. Kept small: the formatting people actually change on a phone. */
data class CellStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    /** ARGB, or null for the theme's text colour. */
    val color: Int? = null,
    val fill: Int? = null,
    val align: Align = Align.GENERAL,
    val wrap: Boolean = false,
    val fontSize: Double? = null,
    /** Excel number format code, e.g. "0.00", "#,##0", "0%", "yyyy-mm-dd". */
    val numFmt: String? = null,
) {
    enum class Align { GENERAL, LEFT, CENTER, RIGHT }

    companion object {
        val DEFAULT = CellStyle()
    }
}

/** What the user typed ([input]: a literal or "=FORMULA") and what it computes to. */
class Cell(var input: String, var value: Value = Value.Empty, var style: CellStyle = CellStyle.DEFAULT) {
    val isFormula get() = input.startsWith("=") && input.length > 1
}

data class CellRange(val r1: Int, val c1: Int, val r2: Int, val c2: Int) {
    fun contains(r: Int, c: Int) = r in r1..r2 && c in c1..c2
}

class Sheet(var name: String) {
    val cells = HashMap<Long, Cell>()
    val colWidths = HashMap<Int, Float>()
    val merges = ArrayList<CellRange>()
    var frozenRows = 0
    var frozenCols = 0
    /** Index of this sheet's XML part in the original file, if it came from one. */
    var sourcePart: String? = null

    fun key(r: Int, c: Int) = (r.toLong() shl 20) or c.toLong()
    fun row(key: Long) = (key shr 20).toInt()
    fun col(key: Long) = (key and 0xFFFFF).toInt()

    operator fun get(r: Int, c: Int): Cell? = cells[key(r, c)]

    fun set(r: Int, c: Int, input: String, style: CellStyle? = null) {
        val existing = cells[key(r, c)]
        if (input.isEmpty() && (style ?: existing?.style ?: CellStyle.DEFAULT) == CellStyle.DEFAULT) {
            cells.remove(key(r, c)); return
        }
        if (existing != null) {
            existing.input = input
            if (style != null) existing.style = style
        } else {
            cells[key(r, c)] = Cell(input, style = style ?: CellStyle.DEFAULT)
        }
    }

    val maxRow: Int get() = cells.keys.maxOfOrNull { row(it) } ?: -1
    val maxCol: Int get() = cells.keys.maxOfOrNull { col(it) } ?: -1

    /** Shifts cells at or after [at] by [count] rows (negative deletes). Formulas are left as typed. */
    fun shiftRows(at: Int, count: Int) {
        val moved = cells.entries.map { it.key to it.value }
        cells.clear()
        moved.forEach { (k, cell) ->
            val r = row(k); val c = col(k)
            when {
                r < at -> cells[k] = cell
                count < 0 && r < at - count -> Unit // deleted
                else -> cells[key(r + count, c)] = cell
            }
        }
    }

    fun shiftCols(at: Int, count: Int) {
        val moved = cells.entries.map { it.key to it.value }
        cells.clear()
        moved.forEach { (k, cell) ->
            val r = row(k); val c = col(k)
            when {
                c < at -> cells[k] = cell
                count < 0 && c < at - count -> Unit
                else -> cells[key(r, c + count)] = cell
            }
        }
    }

    /** Sorts rows [fromRow]..maxRow by column [col]: numbers before text, empties last. */
    fun sortBy(col: Int, fromRow: Int, ascending: Boolean) {
        val last = maxRow
        if (last <= fromRow) return
        val rows = (fromRow..last).map { r -> (0..maxCol).map { c -> this[r, c] } }
        val sorted = rows.sortedWith { a, b ->
            val va = a.getOrNull(col)?.value ?: Value.Empty
            val vb = b.getOrNull(col)?.value ?: Value.Empty
            val cmp = compareValues(va, vb)
            if (ascending) cmp else if (va == Value.Empty || vb == Value.Empty) cmp else -cmp
        }
        sorted.forEachIndexed { i, row -> row.forEachIndexed { c, cell -> val k = key(fromRow + i, c); if (cell == null) cells.remove(k) else cells[k] = cell } }
    }

    private fun compareValues(a: Value, b: Value): Int {
        fun rank(v: Value) = when (v) { is Value.Num -> 0; is Value.Text -> 1; is Value.Bool -> 2; is Value.Err -> 3; Value.Empty -> 4 }
        val ra = rank(a); val rb = rank(b)
        if (ra != rb) return ra - rb
        return when (a) {
            is Value.Num -> a.v.compareTo((b as Value.Num).v)
            is Value.Text -> a.v.compareTo((b as Value.Text).v, ignoreCase = true)
            is Value.Bool -> a.v.compareTo((b as Value.Bool).v)
            else -> 0
        }
    }
}

class Workbook(val sheets: MutableList<Sheet> = mutableListOf(Sheet("Sheet1"))) {
    fun sheet(name: String) = sheets.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Recomputes every formula. Cheap enough for phone-sized sheets; called after each edit. */
    fun recalculate() {
        val evaluator = Evaluator(this)
        sheets.forEach { s -> s.cells.values.forEach { if (it.isFormula) it.value = Value.Empty } }
        sheets.forEach { s ->
            s.cells.entries.forEach { (k, cell) ->
                cell.value = if (cell.isFormula) evaluator.cellValue(s, s.row(k), s.col(k)) else literal(cell.input)
            }
        }
    }

    fun uniqueSheetName(base: String = "Sheet"): String {
        var n = sheets.size + 1
        while (sheet("$base$n") != null) n++
        return "$base$n"
    }

    companion object {
        /** How typed text is understood when it isn't a formula. */
        fun literal(input: String): Value {
            if (input.isEmpty()) return Value.Empty
            if (input.startsWith("'")) return Value.Text(input.substring(1))
            input.trim().let { t ->
                t.replace(",", "").toDoubleOrNull()?.let { if (!t.startsWith(",") && !t.endsWith(",")) return Value.Num(it) }
                if (t.endsWith("%")) t.dropLast(1).trim().toDoubleOrNull()?.let { return Value.Num(it / 100) }
                if (t.equals("TRUE", true)) return Value.Bool(true)
                if (t.equals("FALSE", true)) return Value.Bool(false)
            }
            return Value.Text(input)
        }
    }
}

/** A1-style addresses. */
object A1 {
    fun colName(c: Int): String {
        var n = c + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.append('A' + m); n = (n - 1) / 26 }
        return sb.reverse().toString()
    }

    fun colIndex(name: String): Int = name.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

    fun name(r: Int, c: Int) = colName(c) + (r + 1)

    /** "B12" → (11, 1); also accepts "$B$12". */
    fun parse(ref: String): Pair<Int, Int>? {
        val m = Regex("^\\$?([A-Za-z]{1,3})\\$?(\\d{1,7})$").matchEntire(ref.trim()) ?: return null
        val r = m.groupValues[2].toInt() - 1
        if (r < 0) return null
        return r to colIndex(m.groupValues[1])
    }

    fun parseRange(ref: String): CellRange? {
        val parts = ref.split(':')
        val a = parse(parts[0]) ?: return null
        val b = if (parts.size > 1) parse(parts[1]) ?: return null else a
        return CellRange(minOf(a.first, b.first), minOf(a.second, b.second), maxOf(a.first, b.first), maxOf(a.second, b.second))
    }
}

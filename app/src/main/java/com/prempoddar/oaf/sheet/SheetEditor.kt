package com.prempoddar.oaf.sheet

import java.time.LocalDate
import java.time.LocalTime

/** Copied or cut cells, positioned relative to [origin]. */
class Clip(val sheet: Sheet, val origin: Pair<Int, Int>, val rows: Int, val cols: Int, val cells: List<Entry>, val cut: Boolean, val text: String) {
    class Entry(val dr: Int, val dc: Int, val input: String, val style: CellStyle)
}

/**
 * Everything the grid does to a workbook — edits, formatting, structure, clipboard — with undo.
 * Plain Kotlin, so it's tested without a device. [version] bumps on every change for the UI to redraw.
 */
class SheetEditor(val wb: Workbook) {
    var active = 0
        set(v) { field = v.coerceIn(0, wb.sheets.lastIndex); cursor = 0 to 0; selection = CellRange(0, 0, 0, 0) }
    val sheet get() = wb.sheets[active]

    var cursor = 0 to 0
        private set
    var selection = CellRange(0, 0, 0, 0)
        private set
    var version = 0
        private set
    /** Changes since the last save. */
    var unsaved = 0
        private set

    private sealed interface Step
    private class Snap(val input: String, val style: CellStyle, val cached: Value?)
    private class CellsStep(val sheet: Sheet, val before: Map<Long, Snap?>) : Step
    private class SheetState(
        val sheet: Sheet, val name: String, val cells: Map<Long, Snap>, val widths: Map<Int, Float>, val merges: List<CellRange>,
        val frozenRows: Int, val frozenCols: Int, val rowsChanged: Boolean, val colsChanged: Boolean,
    )
    private class BookStep(val sheets: List<SheetState>, val active: Int) : Step

    private val undoStack = ArrayDeque<Step>()
    private val redoStack = ArrayDeque<Step>()
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun markSaved() { unsaved = 0 }

    // ------------------------------------------------------------ selection

    fun select(r: Int, c: Int, extend: Boolean = false) {
        val rr = r.coerceIn(0, MAX_ROW); val cc = c.coerceIn(0, MAX_COL)
        if (extend) {
            selection = CellRange(minOf(cursor.first, rr), minOf(cursor.second, cc), maxOf(cursor.first, rr), maxOf(cursor.second, cc))
        } else {
            val merge = sheet.merges.firstOrNull { it.contains(rr, cc) }
            cursor = (merge?.r1 ?: rr) to (merge?.c1 ?: cc)
            selection = merge ?: CellRange(rr, cc, rr, cc)
        }
        version++
    }

    fun selectRow(r: Int) { cursor = r to 0; selection = CellRange(r, 0, r, MAX_COL); version++ }
    fun selectCol(c: Int) { cursor = 0 to c; selection = CellRange(0, c, MAX_ROW, c); version++ }
    fun selectAll() { cursor = 0 to 0; selection = CellRange(0, 0, MAX_ROW, MAX_COL); version++ }

    val wholeRows get() = selection.c1 == 0 && selection.c2 == MAX_COL
    val wholeCols get() = selection.r1 == 0 && selection.r2 == MAX_ROW

    /** The selection trimmed to where there's data, for operations that visit cells. */
    private fun usedSelection(): CellRange = selection.let {
        CellRange(it.r1, it.c1, minOf(it.r2, maxOf(sheet.maxRow, it.r1)), minOf(it.c2, maxOf(sheet.maxCol, it.c1)))
    }

    // ------------------------------------------------------------ edits

    fun inputAt(r: Int, c: Int) = sheet[r, c]?.input.orEmpty()

    /** Puts what the user typed into the cursor cell. Dates and times typed plainly become real dates. */
    fun commit(text: String) {
        val (r, c) = cursor
        val style = sheet[r, c]?.style ?: CellStyle.DEFAULT
        val (input, fmt) = understand(text)
        recordCells(listOf(sheet.key(r, c))) {
            sheet.set(r, c, input, if (fmt != null && style.numFmt == null) style.copy(numFmt = fmt) else null)
        }
    }

    private fun understand(text: String): Pair<String, String?> {
        val t = text.trim()
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(t)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
                ?.let { return Value.formatNumber(Evaluator.serial(it)) to "yyyy-mm-dd" }
        }
        Regex("^(\\d{1,2}):(\\d{2})(:(\\d{2}))?$").matchEntire(t)?.let { m ->
            runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[4].ifEmpty { "0" }.toInt()) }.getOrNull()
                ?.let { return Value.formatNumber(it.toSecondOfDay() / 86_400.0) to if (m.groupValues[3].isEmpty()) "h:mm" else "h:mm:ss" }
        }
        return text to null
    }

    fun clear() {
        val area = usedSelection()
        val keys = keysIn(area)
        recordCells(keys) { keys.forEach { k -> sheet.cells[k]?.let { sheet.set(sheet.row(k), sheet.col(k), "", CellStyle.DEFAULT) } } }
    }

    fun style(change: (CellStyle) -> CellStyle) {
        val area = usedSelection()
        val big = (area.r2 - area.r1 + 1).toLong() * (area.c2 - area.c1 + 1) > 20_000
        // Huge selections (whole columns) only restyle cells that exist.
        val keys = if (big) keysIn(area) else (area.r1..area.r2).flatMap { r -> (area.c1..area.c2).map { c -> sheet.key(r, c) } }
        recordCells(keys) {
            keys.forEach { k ->
                val r = sheet.row(k); val c = sheet.col(k)
                val cell = sheet[r, c]
                sheet.set(r, c, cell?.input.orEmpty(), change(cell?.style ?: CellStyle.DEFAULT))
            }
        }
    }

    /** The style at the cursor, for showing which buttons are on. */
    val cursorStyle get() = sheet[cursor.first, cursor.second]?.style ?: CellStyle.DEFAULT

    private fun keysIn(area: CellRange) = sheet.cells.keys.filter { area.contains(sheet.row(it), sheet.col(it)) }

    // ------------------------------------------------------------ structure

    fun insertRows(below: Boolean) = book {
        val at = if (below) selection.r2 + 1 else selection.r1
        wb.shiftRows(sheet, at, rowCount())
        if (!below) selection = selection.copy(r1 = selection.r1 + rowCount(), r2 = selection.r2 + rowCount()).also { cursor = cursor.first + rowCount() to cursor.second }
    }

    fun deleteRows() = book {
        wb.shiftRows(sheet, selection.r1, -rowCount())
        select(selection.r1, cursor.second)
    }

    fun insertCols(right: Boolean) = book {
        val at = if (right) selection.c2 + 1 else selection.c1
        wb.shiftCols(sheet, at, colCount())
        if (!right) selection = selection.copy(c1 = selection.c1 + colCount(), c2 = selection.c2 + colCount()).also { cursor = cursor.first to cursor.second + colCount() }
    }

    fun deleteCols() = book {
        wb.shiftCols(sheet, selection.c1, -colCount())
        select(cursor.first, selection.c1)
    }

    private fun rowCount() = if (wholeCols) 1 else selection.r2 - selection.r1 + 1
    private fun colCount() = if (wholeRows) 1 else selection.c2 - selection.c1 + 1

    /**
     * Sorts by the cursor's column: the selected rows if several are selected, otherwise the whole
     * sheet, keeping a header row in place when the first row looks like one.
     */
    fun sort(ascending: Boolean) = book {
        val col = cursor.second
        if (selection.r2 > selection.r1 && !wholeCols) {
            sheet.sortBy(col, selection.r1, ascending, selection.r2)
        } else {
            sheet.sortBy(col, if (hasHeader()) 1 else 0, ascending)
        }
    }

    fun hasHeader(): Boolean {
        val first = (0..sheet.maxCol).mapNotNull { sheet[0, it]?.value }
        val second = (0..sheet.maxCol).mapNotNull { sheet[1, it]?.value }
        return first.isNotEmpty() && first.all { it is Value.Text } && second.any { it !is Value.Text }
    }

    /** Freezes rows above and columns left of the cursor; again to unfreeze. */
    fun toggleFreeze() = book {
        if (sheet.frozenRows > 0 || sheet.frozenCols > 0) { sheet.frozenRows = 0; sheet.frozenCols = 0 }
        else if (cursor == (0 to 0)) sheet.frozenRows = 1
        else { sheet.frozenRows = cursor.first; sheet.frozenCols = cursor.second }
    }

    fun toggleMerge() = book {
        val hit = sheet.merges.filter { m -> m.r1 <= selection.r2 && m.r2 >= selection.r1 && m.c1 <= selection.c2 && m.c2 >= selection.c1 }
        if (hit.isNotEmpty()) sheet.merges.removeAll(hit.toSet())
        else if (selection != CellRange(cursor.first, cursor.second, cursor.first, cursor.second) && !wholeCols && !wholeRows) sheet.merges += selection
    }

    fun setColumnWidth(c: Int, width: Float) = book {
        sheet.colWidths[c] = width.coerceIn(1f, 120f)
    }

    // ------------------------------------------------------------ sheets

    fun addSheet() = book { wb.sheets += Sheet(wb.uniqueSheetName()); active = wb.sheets.lastIndex }

    fun renameSheet(index: Int, name: String): Boolean {
        val n = name.trim().take(31)
        if (n.isEmpty() || n.any { it in "[]:*?/\\" } || wb.sheets.withIndex().any { (i, s) -> i != index && s.name.equals(n, true) }) return false
        book {
            val old = wb.sheets[index].name
            wb.sheets[index].name = n
            // Formulas naming the sheet follow it.
            val quotedOld = Regex("(?<![A-Za-z0-9_.])(?:'${Regex.escape(old.replace("'", "''"))}'|${Regex.escape(old)})!", RegexOption.IGNORE_CASE)
            val ref = if (n.any { !it.isLetterOrDigit() && it != '_' }) "'${n.replace("'", "''")}'!" else "$n!"
            wb.sheets.forEach { s -> s.cells.values.forEach { if (it.isFormula) it.input = quotedOld.replace(it.input) { ref } } }
        }
        return true
    }

    fun deleteSheet(index: Int) {
        if (wb.sheets.size <= 1) return
        book { wb.sheets.removeAt(index); active = minOf(active, wb.sheets.lastIndex) }
    }

    fun moveSheet(from: Int, to: Int) {
        if (to !in wb.sheets.indices) return
        book { val s = wb.sheets.removeAt(from); wb.sheets.add(to, s); active = to }
    }

    // ------------------------------------------------------------ clipboard

    fun copy(cut: Boolean = false): Clip {
        val area = usedSelection()
        val entries = keysIn(area).map { k -> val cell = sheet.cells.getValue(k); Clip.Entry(sheet.row(k) - area.r1, sheet.col(k) - area.c1, cell.input, cell.style) }
        val text = (area.r1..area.r2).joinToString("\n") { r ->
            (area.c1..area.c2).joinToString("\t") { c -> sheet[r, c]?.let { NumFmt.format(it.value, it.style.numFmt) }.orEmpty() }
        }
        return Clip(sheet, area.r1 to area.c1, area.r2 - area.r1 + 1, area.c2 - area.c1 + 1, entries, cut, text)
    }

    /** Pastes at the cursor; formulas are shifted like Excel does. A cut is moved only once. */
    fun paste(clip: Clip): Boolean {
        val (r0, c0) = cursor
        val dr = r0 - clip.origin.first; val dc = c0 - clip.origin.second
        val target = (0 until clip.rows).flatMap { r -> (0 until clip.cols).map { c -> sheet.key(r0 + r, c0 + c) } }
        val source = if (clip.cut && clip.sheet === sheet) (0 until clip.rows).flatMap { r -> (0 until clip.cols).map { c -> sheet.key(clip.origin.first + r, clip.origin.second + c) } } else emptyList()
        if (clip.cut && clip.sheet !== sheet) return moveAcrossSheets(clip)
        recordCells(target + source) {
            source.forEach { k -> sheet.cells.remove(k) }
            target.forEach { sheet.cells.remove(it) }
            clip.cells.forEach { e -> sheet.set(r0 + e.dr, c0 + e.dc, if (e.input.startsWith("=") && !clip.cut) Refs.shift(e.input, dr, dc) else e.input, e.style) }
        }
        selection = CellRange(r0, c0, r0 + clip.rows - 1, c0 + clip.cols - 1)
        return clip.cut
    }

    /** A cut from another sheet touches two sheets, so it's undone as a whole-book step. */
    private fun moveAcrossSheets(clip: Clip): Boolean {
        val (r0, c0) = cursor
        book {
            (0 until clip.rows).forEach { r -> (0 until clip.cols).forEach { c -> clip.sheet.cells.remove(clip.sheet.key(clip.origin.first + r, clip.origin.second + c)) } }
            clip.cells.forEach { e -> sheet.set(r0 + e.dr, c0 + e.dc, e.input, e.style) }
        }
        selection = CellRange(r0, c0, r0 + clip.rows - 1, c0 + clip.cols - 1)
        return true
    }

    /** Text from another app: tab- or comma-separated values land in a block of cells. */
    fun pasteText(text: String) {
        val delimiter = if (text.contains('\t')) '\t' else if (text.contains('\n')) Csv.detect(text) else '\u0000'
        val rows = if (delimiter == '\u0000') listOf(listOf(text)) else Csv.parse(text.trimEnd('\n'), delimiter)
        val (r0, c0) = cursor
        val keys = rows.flatMapIndexed { r, row -> row.indices.map { c -> sheet.key(r0 + r, c0 + c) } }
        recordCells(keys) { rows.forEachIndexed { r, row -> row.forEachIndexed { c, f -> sheet.set(r0 + r, c0 + c, Csv.input(f).let { if (f.startsWith("=")) f else it }) } } }
        selection = CellRange(r0, c0, r0 + rows.size - 1, c0 + (rows.maxOfOrNull { it.size } ?: 1) - 1)
    }

    // ------------------------------------------------------------ undo

    fun undo() = swap(undoStack, redoStack)
    fun redo() = swap(redoStack, undoStack)

    private fun swap(from: ArrayDeque<Step>, to: ArrayDeque<Step>) {
        val step = from.removeLastOrNull() ?: return
        to.addLast(when (step) {
            is CellsStep -> CellsStep(step.sheet, step.before.keys.associateWith { k -> step.sheet.cells[k]?.snap() }).also { restore(step) }
            is BookStep -> captureBook().also { restore(step) }
        })
        changed()
    }

    private fun Cell.snap() = Snap(input, style, cached)

    private fun recordCells(keys: Collection<Long>, change: () -> Unit) {
        val s = sheet
        push(CellsStep(s, keys.associateWith { s.cells[it]?.snap() }))
        change()
        changed()
    }

    private fun book(change: () -> Unit) {
        push(captureBook())
        change()
        changed()
    }

    private fun push(step: Step) {
        undoStack.addLast(step)
        if (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun captureBook() = BookStep(wb.sheets.map { s ->
        SheetState(s, s.name, s.cells.mapValues { it.value.snap() }, HashMap(s.colWidths), ArrayList(s.merges), s.frozenRows, s.frozenCols, s.rowsChanged, s.colsChanged)
    }, active)

    private fun restore(step: CellsStep) {
        step.before.forEach { (k, snap) ->
            if (snap == null) step.sheet.cells.remove(k)
            else step.sheet.cells[k] = Cell(snap.input, style = snap.style).also { it.cached = snap.cached }
        }
    }

    private fun restore(step: BookStep) {
        wb.sheets.clear()
        step.sheets.forEach { st ->
            val s = st.sheet
            s.name = st.name
            s.cells.clear(); st.cells.forEach { (k, snap) -> s.cells[k] = Cell(snap.input, style = snap.style).also { it.cached = snap.cached } }
            s.colWidths.clear(); s.colWidths.putAll(st.widths)
            s.merges.clear(); s.merges.addAll(st.merges)
            s.frozenRows = st.frozenRows; s.frozenCols = st.frozenCols
            // Once moved, the original layout can't be trusted even after undoing.
            s.rowsChanged = s.rowsChanged || st.rowsChanged; s.colsChanged = s.colsChanged || st.colsChanged
            wb.sheets += s
        }
        val keep = cursor to selection
        active = step.active
        cursor = keep.first; selection = keep.second
    }

    private fun changed() {
        wb.recalculate()
        unsaved++
        version++
    }

    companion object {
        const val MAX_ROW = 1_048_575
        const val MAX_COL = 16_383
        private const val UNDO_LIMIT = 60
    }
}

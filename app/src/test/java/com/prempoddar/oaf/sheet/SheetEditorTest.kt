package com.prempoddar.oaf.sheet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SheetEditorTest {
    private fun editor(vararg cells: Pair<String, String>): SheetEditor {
        val wb = Workbook()
        cells.forEach { (ref, input) -> val (r, c) = A1.parse(ref)!!; wb.sheets[0].set(r, c, input) }
        wb.recalculate()
        return SheetEditor(wb)
    }

    private fun SheetEditor.go(ref: String, to: String? = null) {
        val (r, c) = A1.parse(ref)!!
        select(r, c)
        if (to != null) A1.parse(to)!!.let { (r2, c2) -> select(r2, c2, extend = true) }
    }

    private fun SheetEditor.v(ref: String) = A1.parse(ref)!!.let { (r, c) -> sheet[r, c]?.value?.display() }
    private fun SheetEditor.i(ref: String) = A1.parse(ref)!!.let { (r, c) -> sheet[r, c]?.input }

    @Test fun editingRecalculatesAndUndoes() {
        val e = editor("A1" to "2", "A2" to "=A1*10")
        e.go("A1"); e.commit("5")
        assertThat(e.v("A2")).isEqualTo("50")
        e.undo()
        assertThat(e.v("A2")).isEqualTo("20")
        e.redo()
        assertThat(e.v("A2")).isEqualTo("50")
        assertThat(e.unsaved).isEqualTo(3)
        e.markSaved()
        assertThat(e.unsaved).isEqualTo(0)
    }

    @Test fun typedDatesAndTimesBecomeNumbers() {
        val e = editor()
        e.go("A1"); e.commit("2026-10-05")
        assertThat(e.i("A1")).isEqualTo("46300")
        assertThat(e.sheet[0, 0]!!.style.numFmt).isEqualTo("yyyy-mm-dd")
        assertThat(NumFmt.format(e.sheet[0, 0]!!.value, "yyyy-mm-dd")).isEqualTo("2026-10-05")
        e.go("A2"); e.commit("13:30")
        assertThat(e.v("A2")).isEqualTo("0.5625")
        e.go("A3"); e.commit("=A1+1")
        assertThat(e.v("A3")).isEqualTo("46301")
    }

    @Test fun stylingASelectionAndUndoingIt() {
        val e = editor("A1" to "x", "B2" to "y")
        e.go("A1", "B2")
        e.style { it.copy(bold = true) }
        assertThat(e.sheet[0, 0]!!.style.bold).isTrue()
        assertThat(e.sheet[1, 0]!!.style.bold).isTrue() // empty cells in the selection get the style too
        e.undo()
        assertThat(e.sheet[0, 0]!!.style.bold).isFalse()
        assertThat(e.sheet[1, 0]).isNull()
        // A whole column is styled only as far down as the data goes.
        e.selectCol(0)
        e.style { it.copy(italic = true) }
        assertThat(e.sheet.maxRow).isEqualTo(1)
        assertThat(e.sheet[1, 0]!!.style.italic).isTrue()
    }

    @Test fun rowsAndColumnsInsertAndDelete() {
        val e = editor("A1" to "1", "A2" to "2", "B1" to "=SUM(A1:A2)")
        e.go("A2"); e.insertRows(below = false)
        assertThat(e.i("A3")).isEqualTo("2")
        assertThat(e.i("B1")).isEqualTo("=SUM(A1:A3)")
        assertThat(e.cursor).isEqualTo(2 to 0)
        e.go("A1"); e.insertCols(right = false)
        assertThat(e.i("C1")).isEqualTo("=SUM(B1:B3)")
        e.go("A1"); e.deleteCols()
        e.go("A2"); e.deleteRows()
        assertThat(e.i("B1")).isEqualTo("=SUM(A1:A2)")
        assertThat(e.v("B1")).isEqualTo("3")
        e.undo(); e.undo(); e.undo(); e.undo()
        assertThat(e.i("B1")).isEqualTo("=SUM(A1:A2)")
        assertThat(e.i("A2")).isEqualTo("2")
    }

    @Test fun sortKeepsTheHeader() {
        val e = editor("A1" to "Name", "B1" to "Age", "A2" to "Zoe", "B2" to "30", "A3" to "Amy", "B3" to "25", "C2" to "=B2*2", "C3" to "=B3*2")
        e.go("A2"); e.sort(ascending = true)
        assertThat(e.i("A1")).isEqualTo("Name")
        assertThat(e.i("A2")).isEqualTo("Amy")
        assertThat(e.i("C2")).isEqualTo("=B2*2")
        assertThat(e.v("C2")).isEqualTo("50")
        e.go("B1"); e.sort(ascending = false)
        assertThat(e.i("A2")).isEqualTo("Zoe")
    }

    @Test fun copyPasteShiftsFormulasAndCutMoves() {
        val e = editor("A1" to "1", "A2" to "2", "B1" to "=A1*2")
        e.go("B1")
        val clip = e.copy()
        assertThat(clip.text).isEqualTo("2")
        e.go("B2"); e.paste(clip)
        assertThat(e.i("B2")).isEqualTo("=A2*2")
        assertThat(e.v("B2")).isEqualTo("4")
        e.go("A1", "A2")
        val cut = e.copy(cut = true)
        e.go("D5"); assertThat(e.paste(cut)).isTrue()
        assertThat(e.i("A1")).isNull()
        assertThat(e.i("D6")).isEqualTo("2")
        e.undo()
        assertThat(e.i("A1")).isEqualTo("1")
        assertThat(e.i("D5")).isNull()
    }

    @Test fun pastingTextFromElsewhere() {
        val e = editor()
        e.go("B2"); e.pasteText("a\tb\n1\t007\n")
        assertThat(e.i("C3")).isEqualTo("'007")
        assertThat(e.selection).isEqualTo(CellRange(1, 1, 2, 2))
        e.go("A1"); e.pasteText("just text, with comma")
        assertThat(e.i("A1")).isEqualTo("just text, with comma")
    }

    @Test fun sheetsAddRenameDelete() {
        val e = editor("A1" to "4")
        e.addSheet()
        assertThat(e.wb.sheets.map { it.name }).containsExactly("Sheet1", "Sheet2").inOrder()
        e.go("A1"); e.commit("=Sheet1!A1*2")
        assertThat(e.renameSheet(0, "My Data")).isTrue()
        assertThat(e.i("A1")).isEqualTo("='My Data'!A1*2")
        assertThat(e.v("A1")).isEqualTo("8")
        assertThat(e.renameSheet(1, "my data")).isFalse()
        e.deleteSheet(0)
        assertThat(e.wb.sheets.map { it.name }).containsExactly("Sheet2")
        e.undo()
        assertThat(e.wb.sheets.map { it.name }).containsExactly("My Data", "Sheet2").inOrder()
    }

    @Test fun mergeAndFreeze() {
        val e = editor("A1" to "Title")
        e.go("A1", "C1"); e.toggleMerge()
        assertThat(e.sheet.merges).containsExactly(CellRange(0, 0, 0, 2))
        e.go("B1")
        assertThat(e.selection).isEqualTo(CellRange(0, 0, 0, 2))
        e.toggleMerge()
        assertThat(e.sheet.merges).isEmpty()
        e.go("B3"); e.toggleFreeze()
        assertThat(e.sheet.frozenRows to e.sheet.frozenCols).isEqualTo(2 to 1)
        e.toggleFreeze()
        assertThat(e.sheet.frozenRows).isEqualTo(0)
    }

    @Test fun fillDownShiftsFormulas() {
        val e = editor("A1" to "1", "A2" to "2", "A3" to "3", "B1" to "=A1*2")
        e.go("B1", "B3"); e.fill(down = true)
        assertThat(e.i("B3")).isEqualTo("=A3*2")
        assertThat(e.v("B2")).isEqualTo("4")
        e.undo()
        assertThat(e.i("B2")).isNull()
        e.go("A1", "C1"); e.fill(down = false)
        assertThat(e.i("C1")).isEqualTo("1")
    }

    @Test fun autoSumAddsTheNumbersAboveOrUnderASelection() {
        val e = editor("A1" to "Price", "A2" to "3", "A3" to "4", "B2" to "10", "B3" to "20")
        e.go("A4"); e.autoSum()
        assertThat(e.i("A4")).isEqualTo("=SUM(A2:A3)")
        assertThat(e.v("A4")).isEqualTo("7")
        e.go("A2", "B3"); e.autoSum()
        assertThat(e.v("B4")).isEqualTo("30")
        e.go("C2"); e.autoSum()
        assertThat(e.v("C2")).isEqualTo("13")
    }

    @Test fun replaceAllChangesCellsAndUndoes() {
        val e = editor("A1" to "Apple pie", "A2" to "apple", "A3" to "pear")
        assertThat(e.replaceAll("apple", "Plum")).isEqualTo(2)
        assertThat(e.i("A1")).isEqualTo("Plum pie")
        e.undo()
        assertThat(e.replaceAll("apple", "x", matchCase = true)).isEqualTo(1)
        assertThat(e.i("A1")).isEqualTo("Apple pie")
    }
}

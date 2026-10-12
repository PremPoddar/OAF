package com.prempoddar.oaf.ui.viewers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaginateTest {
    @Test fun startsANewSheetWhenThePageIsFull() {
        // Sheets hold 100 points; pieces of 40 fit two to a sheet.
        assertThat(paginate(5, emptySet(), 100f) { 40f }).containsExactly(2, 4)
    }

    @Test fun keepsTheDocumentsOwnBreaksAndCountsFromThem() {
        assertThat(paginate(4, setOf(1), 100f) { 60f }).containsExactly(1, 2, 3)
        assertThat(paginate(4, setOf(2), 100f) { 30f }).containsExactly(2)
    }

    @Test fun aPieceTallerThanASheetGetsOneToItself() {
        assertThat(paginate(3, emptySet(), 100f) { if (it == 1) 250f else 10f }).containsExactly(1, 2)
    }

    @Test fun stopsAtPiecesNotLaidOutYet() {
        assertThat(paginate(6, emptySet(), 100f) { if (it < 3) 60f else null }).containsExactly(1, 2)
    }
}

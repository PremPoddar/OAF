package com.prempoddar.oaf.ui.browser

import com.google.common.truth.Truth.assertThat
import com.prempoddar.oaf.data.SortBy
import com.prempoddar.oaf.format.FileKind
import org.junit.Test
import java.io.File
import java.util.Calendar

class SectionsTest {
    private fun file(name: String, size: Long = 0, modified: Long = 0) = Entry(File("/x/$name"), FileKind.IMAGE, size, modified, 0)
    private val folder = Entry(File("/x/Album"), null, 0, 0, 3)

    @Test fun namesByFirstLetter() {
        assertThat(Sections.letter("beach.jpg")).isEqualTo("B")
        assertThat(Sections.letter("Élan.png")).isEqualTo("E")
        assertThat(Sections.letter("2024 trip.jpg")).isEqualTo("0–9")
        assertThat(Sections.letter("_draft.png")).isEqualTo("#")
    }

    @Test fun datesTodayYesterdayThisWeekThenMonths() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 3, 15, 0) }.timeInMillis
        val hour = 3_600_000L
        assertThat(Sections.date(now - hour, now)).isEqualTo("Today")
        assertThat(Sections.date(now - 20 * hour, now)).isEqualTo("Yesterday")
        assertThat(Sections.date(now - 4 * 24 * hour, now)).isEqualTo("This week")
        val october = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 2, 12, 0) }.timeInMillis
        assertThat(Sections.date(october, now)).contains("2026")
    }

    @Test fun sizeBands() {
        assertThat(Sections.size(50_000)).isEqualTo("Under 100 KB")
        assertThat(Sections.size(3L shl 20)).isEqualTo("1 – 10 MB")
        assertThat(Sections.size(2L shl 30)).isEqualTo("1 GB and over")
    }

    @Test fun typesAndFolders() {
        assertThat(Sections.of(file("a.jpeg"), SortBy.TYPE, false)).isEqualTo("JPEG")
        assertThat(Sections.of(file("README"), SortBy.TYPE, false)).isEqualTo("No extension")
        assertThat(Sections.of(folder, SortBy.NAME, true)).isEqualTo("Folders")
        assertThat(Sections.of(folder, SortBy.NAME, false)).isEqualTo("A")
    }
}

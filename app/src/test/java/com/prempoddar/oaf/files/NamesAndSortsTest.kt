package com.prempoddar.oaf.files

import com.google.common.truth.Truth.assertThat
import com.prempoddar.oaf.data.OafSettings
import com.prempoddar.oaf.data.SortBy
import com.prempoddar.oaf.data.SortMap
import com.prempoddar.oaf.data.sortFor
import com.prempoddar.oaf.data.sortedAt
import com.prempoddar.oaf.ui.design.MiddleEllipsis
import org.junit.Test

class NamesAndSortsTest {
    @Test fun eachPlaceKeepsItsOwnSort() {
        val s = OafSettings().sortedAt("category:IMAGES", SortBy.DATE, true).sortedAt("folders", SortBy.SIZE, true)
        assertThat(s.sortFor("category:IMAGES")).isEqualTo(SortBy.DATE to true)
        assertThat(s.sortFor("folders")).isEqualTo(SortBy.SIZE to true)
        // Somewhere never sorted follows the app-wide default.
        assertThat(s.sortFor("category:AUDIO")).isEqualTo(SortBy.NAME to false)
        assertThat(SortMap.decode(SortMap.encode(s.sorts))).isEqualTo(s.sorts)
        assertThat(SortMap.decode("junk;folders=NOPE:1")).isEmpty()
    }

    @Test fun longNamesKeepTheirEnding() {
        val name = "WhatsApp Image 2024-10-21 at 18.33.07_4f2a9c1e.jpeg"
        val cut = MiddleEllipsis.cut(name) { it.length <= 24 }
        assertThat(cut.length).isAtMost(24)
        assertThat(cut).startsWith("WhatsApp")
        assertThat(cut).endsWith(".jpeg")
        assertThat(cut).contains("…")
        assertThat(MiddleEllipsis.cut("short.txt") { true }).isEqualTo("short.txt")
    }
}

package com.prempoddar.oaf.ui.browser

import com.google.common.truth.Truth.assertThat
import com.prempoddar.oaf.data.SortBy
import org.junit.Test
import java.io.File

class SortTest {
    private fun e(name: String, size: Long = 0, modified: Long = 0, dir: Boolean = false) =
        Entry(File("/x/$name"), if (dir) null else kindOf(File(name)), size, modified, 0)

    private val list = listOf(e("file 10.txt", 5, 3), e("File 2.pdf", 50, 1), e("a.jpg", 20, 2), e("Docs", dir = true))

    private fun names(sort: SortBy, desc: Boolean, folders: Boolean = false) = sortEntries(list, sort, folders, desc).map { it.file.name }

    @Test fun namesReadNaturally() {
        assertThat(names(SortBy.NAME, false)).containsExactly("a.jpg", "Docs", "File 2.pdf", "file 10.txt").inOrder()
        assertThat(names(SortBy.NAME, true)).containsExactly("file 10.txt", "File 2.pdf", "Docs", "a.jpg").inOrder()
    }

    @Test fun sizesAndDatesGoEitherWay() {
        assertThat(names(SortBy.SIZE, true, folders = true)).containsExactly("Docs", "File 2.pdf", "a.jpg", "file 10.txt").inOrder()
        assertThat(names(SortBy.DATE, false, folders = true)).containsExactly("Docs", "File 2.pdf", "a.jpg", "file 10.txt").inOrder()
    }

    @Test fun typeGroupsByExtension() {
        assertThat(names(SortBy.TYPE, false, folders = true)).containsExactly("Docs", "a.jpg", "File 2.pdf", "file 10.txt").inOrder()
    }
}

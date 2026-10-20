package com.prempoddar.oaf.files

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CleanUpTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day = 24 * 3600 * 1000L
    private val now = 1_000 * day

    private fun file(path: String, bytes: ByteArray, age: Long = 0) = File(tmp.root, path).apply {
        parentFile!!.mkdirs(); writeBytes(bytes); setLastModified(now - age)
    }

    @Test fun findsBigFilesBiggestFirst() {
        file("a/small.bin", ByteArray(10))
        file("a/big.bin", ByteArray(300))
        file("b/bigger.bin", ByteArray(500))
        val r = CleanUp.scan(tmp.root, now, largeBytes = 200)
        assertThat(r.large.map { it.name }).containsExactly("bigger.bin", "big.bin").inOrder()
    }

    @Test fun duplicatesNeedTheSameContentNotJustTheSameSize() {
        val same = ByteArray(2000) { 7 }
        file("one/photo.jpg", same, age = 5 * day)
        file("two/photo (1).jpg", same, age = 1 * day)
        file("three/other.jpg", ByteArray(2000) { 8 })
        val r = CleanUp.scan(tmp.root, now, minDupeBytes = 1000)
        assertThat(r.duplicates).hasSize(1)
        val d = r.duplicates[0]
        // Oldest first, so the original is the one kept.
        assertThat(d.files.map { it.parentFile!!.name }).containsExactly("one", "two").inOrder()
        assertThat(d.spare).isEqualTo(2000)
    }

    @Test fun onlyOldInstallersAreSuggested() {
        file("Download/old.apk", ByteArray(50), age = 40 * day)
        file("Download/new.apk", ByteArray(50), age = 2 * day)
        file("Download/notes.txt", ByteArray(50), age = 90 * day)
        assertThat(CleanUp.scan(tmp.root, now).installers.map { it.name }).containsExactly("old.apk")
    }

    @Test fun emptyFoldersButNotTheRootOrAppFolders() {
        File(tmp.root, "empty/inner").mkdirs()
        File(tmp.root, "Android/data/x").mkdirs()
        file("full/a.txt", ByteArray(1))
        File(tmp.root, "hidden").mkdirs(); file("hidden/.nomedia", ByteArray(0))
        val names = CleanUp.scan(tmp.root, now).emptyFolders.map { it.relativeTo(tmp.root).path }
        assertThat(names).containsExactly("empty/inner")
    }

    @Test fun nothingFoundIsEmpty() {
        file("a.txt", ByteArray(5))
        assertThat(CleanUp.scan(tmp.root, now).isEmpty).isTrue()
    }
}

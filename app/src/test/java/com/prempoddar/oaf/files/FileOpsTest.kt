package com.prempoddar.oaf.files

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileOpsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, text: String = name) = File(dir, name).apply { parentFile.mkdirs(); writeText(text) }

    @Test fun freeNamesCountUp() {
        val d = tmp.newFolder()
        file(d, "a.txt"); file(d, "a (2).txt")
        assertThat(FileOps.freeName(d, "a.txt").name).isEqualTo("a (3).txt")
        assertThat(FileOps.freeName(d, "b.txt").name).isEqualTo("b.txt")
    }

    @Test fun copyFolderRecursivelyAndRefuseIntoItself() {
        val src = tmp.newFolder("src"); file(src, "x/y.txt", "hello")
        val dst = tmp.newFolder("dst")
        val out = FileOps.copy(src, dst)
        assertThat(File(out, "x/y.txt").readText()).isEqualTo("hello")
        assertThrows(FileOps.Conflict::class.java) { FileOps.copy(src, File(src, "x")) }
        // Copying next to itself makes a numbered copy.
        assertThat(FileOps.copy(File(src, "x/y.txt"), File(src, "x")).name).isEqualTo("y (2).txt")
    }

    @Test fun moveAndRename() {
        val a = tmp.newFolder("a"); val b = tmp.newFolder("b")
        val f = file(a, "doc.txt")
        val moved = FileOps.move(f, b)
        assertThat(moved.exists()).isTrue(); assertThat(f.exists()).isFalse()
        val renamed = FileOps.rename(moved, "notes.txt")
        assertThat(renamed.name).isEqualTo("notes.txt")
        file(b, "taken.txt")
        assertThrows(FileOps.Conflict::class.java) { FileOps.rename(renamed, "taken.txt") }
        assertThrows(IllegalArgumentException::class.java) { FileOps.rename(renamed, "../escape") }
    }

    @Test fun zipAndUnzipRoundTrip() {
        val src = tmp.newFolder("project"); file(src, "a.txt", "A"); file(src, "sub/b.txt", "B")
        val z = File(tmp.root, "p.zip")
        FileOps.zip(listOf(src), z)
        val out = tmp.newFolder("out")
        FileOps.unzip(z, out)
        assertThat(File(out, "project/a.txt").readText()).isEqualTo("A")
        assertThat(File(out, "project/sub/b.txt").readText()).isEqualTo("B")
    }

    @Test fun zipSlipIsRefused() {
        val evil = File(tmp.root, "evil.zip")
        ZipOutputStream(evil.outputStream()).use { it.putNextEntry(ZipEntry("../../escaped.txt")); it.write(1); it.closeEntry() }
        assertThrows(IOException::class.java) { FileOps.unzip(evil, tmp.newFolder("safe")) }
        assertThat(File(tmp.root.parentFile, "escaped.txt").exists()).isFalse()
    }

    @Test fun binRestoresToTheOriginalPlace() {
        val home = tmp.newFolder("home"); val bin = Bin(tmp.newFolder("bin"))
        val f = file(home, "letter.txt", "dear")
        val item = bin.delete(f, now = 100)
        assertThat(f.exists()).isFalse()
        assertThat(bin.items().map { it.name }).containsExactly("letter.txt")
        val back = bin.restore(item)
        assertThat(back.readText()).isEqualTo("dear")
        assertThat(bin.items()).isEmpty()
    }

    @Test fun restoreDoesntOverwriteANewerFile() {
        val home = tmp.newFolder("home"); val bin = Bin(tmp.newFolder("bin"))
        val item = bin.delete(file(home, "x.txt", "old"))
        file(home, "x.txt", "new")
        assertThat(bin.restore(item).name).isEqualTo("x (2).txt")
        assertThat(File(home, "x.txt").readText()).isEqualTo("new")
    }

    @Test fun binExpiresOldItems() {
        val home = tmp.newFolder("home"); val bin = Bin(tmp.newFolder("bin"))
        bin.delete(file(home, "old.txt"), now = 0)
        bin.delete(file(home, "new.txt"), now = 1_000)
        bin.expire(now = 1_500, maxAgeMs = 1_000)
        assertThat(bin.items().map { it.name }).containsExactly("new.txt")
    }
}

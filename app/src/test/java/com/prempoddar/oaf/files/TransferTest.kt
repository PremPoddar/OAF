package com.prempoddar.oaf.files

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TransferTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, text: String = name) = File(dir, name).apply { parentFile?.mkdirs(); writeText(text) }

    private fun transfer(answer: Transfer.Resolution, all: Boolean = false, discarded: MutableList<File> = mutableListOf()) =
        Transfer(ask = { answer to all }, discard = { discarded += it; it.deleteRecursively() })

    @Test fun copiesFilesAndFoldersWithProgress() = runBlocking<Unit> {
        val src = tmp.newFolder("src"); val out = tmp.newFolder("out")
        val a = file(src, "a.txt", "hello")
        val dir = File(src, "d").apply { mkdirs() }; file(dir, "b.txt", "world!")
        var last: Transfer.Progress? = null
        val made = transfer(Transfer.Resolution.SKIP).run(Clip(ClipMode.COPY, listOf(a, dir)), out) { last = it }
        assertThat(made.map { it.name }).containsExactly("a.txt", "d")
        assertThat(File(out, "d/b.txt").readText()).isEqualTo("world!")
        assertThat(a.exists()).isTrue()
        assertThat(last!!.done).isEqualTo(11)
        assertThat(last!!.total).isEqualTo(11)
    }

    @Test fun cutMovesAndRemovesTheOriginal() = runBlocking<Unit> {
        val src = tmp.newFolder("src"); val out = tmp.newFolder("out")
        val a = file(src, "a.txt")
        transfer(Transfer.Resolution.SKIP).run(Clip(ClipMode.CUT, listOf(a)), out) {}
        assertThat(a.exists()).isFalse()
        assertThat(File(out, "a.txt").exists()).isTrue()
    }

    @Test fun conflictSkipLeavesTheTargetAlone() = runBlocking<Unit> {
        val src = tmp.newFolder("src"); val out = tmp.newFolder("out")
        val a = file(src, "a.txt", "new"); file(out, "a.txt", "old")
        val made = transfer(Transfer.Resolution.SKIP).run(Clip(ClipMode.COPY, listOf(a)), out) {}
        assertThat(made).isEmpty()
        assertThat(File(out, "a.txt").readText()).isEqualTo("old")
    }

    @Test fun conflictReplaceDiscardsTheOldOne() = runBlocking<Unit> {
        val src = tmp.newFolder("src"); val out = tmp.newFolder("out")
        val a = file(src, "a.txt", "new"); val old = file(out, "a.txt", "old")
        val discarded = mutableListOf<File>()
        transfer(Transfer.Resolution.REPLACE, discarded = discarded).run(Clip(ClipMode.COPY, listOf(a)), out) {}
        assertThat(File(out, "a.txt").readText()).isEqualTo("new")
        assertThat(discarded.map { it.canonicalFile }).containsExactly(old.canonicalFile)
    }

    @Test fun conflictKeepBothAddsANumber() = runBlocking<Unit> {
        val src = tmp.newFolder("src"); val out = tmp.newFolder("out")
        val a = file(src, "a.txt", "new"); file(out, "a.txt", "old")
        transfer(Transfer.Resolution.KEEP_BOTH).run(Clip(ClipMode.COPY, listOf(a)), out) {}
        assertThat(File(out, "a (2).txt").readText()).isEqualTo("new")
    }

    @Test fun applyToAllAsksOnce() = runBlocking<Unit> {
        val src = tmp.newFolder("src"); val out = tmp.newFolder("out")
        val files = listOf(file(src, "a"), file(src, "b")); file(out, "a"); file(out, "b")
        var asked = 0
        Transfer(ask = { asked++; Transfer.Resolution.SKIP to true }, discard = {}).run(Clip(ClipMode.COPY, files), out) {}
        assertThat(asked).isEqualTo(1)
    }

    @Test fun pastingACopyIntoTheSameFolderKeepsBoth() = runBlocking<Unit> {
        val src = tmp.newFolder("src")
        val a = file(src, "a.txt")
        transfer(Transfer.Resolution.SKIP).run(Clip(ClipMode.COPY, listOf(a)), src) {}
        assertThat(File(src, "a (2).txt").exists()).isTrue()
    }

    @Test(expected = FileOps.Conflict::class)
    fun aFolderCantGoInsideItself() = runBlocking<Unit> {
        val src = tmp.newFolder("src"); val inner = File(src, "inner").apply { mkdirs() }
        transfer(Transfer.Resolution.SKIP).run(Clip(ClipMode.CUT, listOf(src)), inner) {}
    }

    @Test fun batchRenameNumbersAndKeepsExtensions() {
        val dir = tmp.newFolder("d")
        val files = listOf(file(dir, "IMG_1.jpg"), file(dir, "IMG_2.png"), file(dir, "notes"))
        assertThat(FileOps.batchNames(files, "Trip ###", 1)).containsExactly("Trip 001.jpg", "Trip 002.png", "Trip 003").inOrder()
        assertThat(FileOps.batchNames(files, "* - edited", 1)).containsExactly("IMG_1 - edited.jpg", "IMG_2 - edited.png", "notes - edited").inOrder()
    }

    @Test fun batchRenameApplies() {
        val dir = tmp.newFolder("d")
        val files = listOf(file(dir, "x.txt"), file(dir, "y.txt"))
        FileOps.batchRename(files, "Note #", 5)
        assertThat(dir.list()!!.sorted()).containsExactly("Note 5.txt", "Note 6.txt").inOrder()
    }

    @Test fun createsFilesAndFolders() {
        val dir = tmp.newFolder("d")
        assertThat(FileOps.newFolder(dir, "Photos").isDirectory).isTrue()
        assertThat(FileOps.newFile(dir, "todo.txt").isFile).isTrue()
    }

    @Test(expected = FileOps.Conflict::class)
    fun newItemRefusesExistingName() {
        val dir = tmp.newFolder("d")
        FileOps.newFolder(dir, "a"); FileOps.newFile(dir, "a")
    }

    @Test fun hashesMatchKnownValues() {
        val f = file(tmp.newFolder("d"), "abc", "abc")
        assertThat(FileOps.hash(f, "MD5")).isEqualTo("900150983cd24fb0d6963f7d28e17f72")
        assertThat(FileOps.hash(f, "SHA-256")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}

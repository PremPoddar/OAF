package com.prempoddar.oaf.archive

import com.google.common.truth.Truth.assertThat
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchivesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun head(f: File) = f.inputStream().use { s -> ByteArray(512).let { b -> b.copyOf(s.read(b).coerceAtLeast(0)) } }

    private val files = linkedMapOf("docs/readme.txt" to "hello", "docs/deep/notes.md" to "# notes", "top.csv" to "a,b")

    private fun zip(): File = tmp.newFile("a.zip").also { f ->
        ZipOutputStream(f.outputStream()).use { z ->
            files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            z.putNextEntry(ZipEntry("../escape.txt")); z.write("bad".toByteArray()); z.closeEntry()
        }
    }

    private fun tarGz(): File = tmp.newFile("a.tar.gz").also { f ->
        TarArchiveOutputStream(GzipCompressorOutputStream(f.outputStream())).use { t ->
            files.forEach { (n, s) -> val b = s.toByteArray(); t.putArchiveEntry(TarArchiveEntry(n).apply { size = b.size.toLong() }); t.write(b); t.closeArchiveEntry() }
        }
    }

    private fun sevenZ(): File = tmp.newFile("a.7z").also { f ->
        SevenZOutputFile(f).use { z ->
            files.forEach { (n, s) -> val e = z.createArchiveEntry(tmp.newFile(), n); z.putArchiveEntry(e); z.write(s.toByteArray()); z.closeArchiveEntry() }
        }
    }

    @Test fun detectsTypes() {
        assertThat(Archives.typeOf("a.zip", head(zip()))).isEqualTo(ArchiveType.ZIP)
        assertThat(Archives.typeOf("a.tar.gz", head(tarGz()))).isEqualTo(ArchiveType.TAR_GZ)
        assertThat(Archives.typeOf("a.7z", head(sevenZ()))).isEqualTo(ArchiveType.SEVEN_Z)
        assertThat(Archives.typeOf("x.txt.gz", byteArrayOf(0x1F, 0x8B.toByte()))).isEqualTo(ArchiveType.GZ)
        assertThat(Archives.typeOf("x.bin", byteArrayOf(1, 2))).isNull()
    }

    @Test fun pathsThatEscapeAreRefused() {
        assertThat(Archives.clean("../x")).isNull()
        assertThat(Archives.clean("a/../../x")).isNull()
        assertThat(Archives.clean("C:\\x")).isNull()
        assertThat(Archives.clean("/abs/x")).isEqualTo("abs/x")
        assertThat(Archives.clean("a\\b.txt")).isEqualTo("a/b.txt")
        assertThat(Archives.clean("dir/")).isEqualTo("dir/")
    }

    @Test fun everyFormatListsAndExtracts() {
        for ((f, type) in listOf(zip() to ArchiveType.ZIP, tarGz() to ArchiveType.TAR_GZ, sevenZ() to ArchiveType.SEVEN_Z)) {
            val entries = Archives.list(f, type)
            assertThat(entries.filter { !it.isDirectory }.map { it.path }).containsExactlyElementsIn(files.keys)
            // Folders are listed even when the archive only names the files in them.
            assertThat(entries.filter { it.isDirectory }.map { it.path }).containsAtLeast("docs/", "docs/deep/")
            val one = tmp.newFile()
            Archives.extract(f, type, "docs/deep/notes.md", one)
            assertThat(one.readText()).isEqualTo("# notes")
            val dir = tmp.newFolder()
            assertThat(Archives.extractAll(f, type, dir)).isEqualTo(3)
            assertThat(File(dir, "docs/readme.txt").readText()).isEqualTo("hello")
            assertThat(File(dir.parentFile, "escape.txt").exists()).isFalse()
            val sub = tmp.newFolder()
            assertThat(Archives.extractAll(f, type, sub, prefix = "docs/")).isEqualTo(2)
            assertThat(File(sub, "deep/notes.md").exists()).isTrue()
        }
    }

    @Test fun singleCompressedFiles() {
        val f = tmp.newFile("report.txt.gz")
        GZIPOutputStream(f.outputStream()).use { it.write("compressed".toByteArray()) }
        val entries = Archives.list(f, ArchiveType.GZ)
        assertThat(entries.single().path).isEqualTo("report.txt")
        val out = tmp.newFile()
        Archives.extract(f, ArchiveType.GZ, "report.txt", out)
        assertThat(out.readText()).isEqualTo("compressed")
    }
}

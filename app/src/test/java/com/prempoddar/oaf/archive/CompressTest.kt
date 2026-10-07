package com.prempoddar.oaf.archive

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompressTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sources(): List<File> {
        val dir = tmp.newFolder()
        val a = File(dir, "notes.txt").apply { writeText("hello ".repeat(500)) }
        val sub = File(dir, "photos").apply { mkdirs() }
        File(sub, "one.txt").writeText("first")
        return listOf(a, sub)
    }

    private fun roundTrip(format: Compressor.Format, password: String? = null): File {
        val out = Compressor.compress(sources(), tmp.newFolder("out"), Compressor.Options("Bundle", format, Compressor.Level.NORMAL, password))
        assertThat(out.name).isEqualTo("Bundle." + format.ext)
        val type = Archives.typeOf(out.name, out.inputStream().use { s -> ByteArray(512).also { s.read(it) } })!!
        val names = Archives.list(out, type, password).filter { !it.isDirectory }.map { it.path }
        assertThat(names).containsExactly("notes.txt", "photos/one.txt")
        val dest = tmp.newFolder("x-" + format.name)
        Archives.extractAll(out, type, dest, password = password)
        assertThat(File(dest, "photos/one.txt").readText()).isEqualTo("first")
        return out
    }

    @Test fun zip() { roundTrip(Compressor.Format.ZIP) }
    @Test fun sevenZip() { roundTrip(Compressor.Format.SEVEN_Z) }
    @Test fun tarGz() { roundTrip(Compressor.Format.TAR_GZ) }
    @Test fun tarXz() { roundTrip(Compressor.Format.TAR_XZ) }
    @Test fun tar() { roundTrip(Compressor.Format.TAR) }

    @Test fun passwordProtectedZip() {
        val out = roundTrip(Compressor.Format.ZIP, "s3cret")
        val missing = assertThrows(Archives.PasswordNeeded::class.java) { Archives.list(out, ArchiveType.ZIP) }
        assertThat(missing.wrong).isFalse()
        val wrong = assertThrows(Archives.PasswordNeeded::class.java) { Archives.extractAll(out, ArchiveType.ZIP, tmp.newFolder("bad"), password = "nope") }
        assertThat(wrong.wrong).isTrue()
    }

    @Test fun storeIsBiggerThanMaximum() {
        val stored = Compressor.compress(sources(), tmp.newFolder("s"), Compressor.Options("a", Compressor.Format.ZIP, Compressor.Level.STORE, null))
        val max = Compressor.compress(sources(), tmp.newFolder("m"), Compressor.Options("a", Compressor.Format.ZIP, Compressor.Level.MAXIMUM, null))
        assertThat(stored.length()).isGreaterThan(max.length())
    }

    @Test fun onlyZipTakesAPassword() {
        assertThat(Compressor.Format.ZIP.passwords).isTrue()
        assertThat(Compressor.Format.SEVEN_Z.passwords).isFalse()
    }
}

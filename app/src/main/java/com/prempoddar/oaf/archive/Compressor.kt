package com.prempoddar.oaf.archive

import com.prempoddar.oaf.files.FileOps
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.tukaani.xz.LZMA2Options
import java.io.File
import java.io.OutputStream

/** Makes archives: ZIP (optionally locked with AES-256), 7z, and TAR plain, gzipped or xz'd. */
object Compressor {
    enum class Format(val label: String, val ext: String, val passwords: Boolean) {
        ZIP("ZIP", "zip", true), SEVEN_Z("7z", "7z", false), TAR_GZ("TAR.GZ", "tar.gz", false), TAR_XZ("TAR.XZ", "tar.xz", false), TAR("TAR", "tar", false),
    }

    enum class Level(val label: String) { STORE("None"), FAST("Fast"), NORMAL("Normal"), MAXIMUM("Smallest") }

    data class Options(val name: String, val format: Format, val level: Level, val password: String?)

    /** Writes [items] (files and folders) into a new archive in [dir] and returns it. */
    fun compress(items: List<File>, dir: File, o: Options, onFile: (String) -> Unit = {}): File {
        val out = FileOps.freeName(dir, o.name.trim().ifEmpty { "Archive" } + "." + o.format.ext)
        try {
            when (o.format) {
                Format.ZIP -> zip(items, out, o, onFile)
                Format.SEVEN_Z -> sevenZ(items, out, o.level, onFile)
                Format.TAR_GZ -> out.outputStream().buffered().use { raw ->
                    val params = GzipParameters().apply { compressionLevel = gzipLevel(o.level) }
                    GzipCompressorOutputStream(raw, params).use { tar(items, it, onFile) }
                }
                Format.TAR_XZ -> out.outputStream().buffered().use { raw -> XZCompressorOutputStream(raw, xzPreset(o.level)).use { tar(items, it, onFile) } }
                Format.TAR -> out.outputStream().buffered().use { tar(items, it, onFile) }
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
        return out
    }

    private fun zip(items: List<File>, out: File, o: Options, onFile: (String) -> Unit) {
        val params = ZipParameters().apply {
            if (o.level == Level.STORE) compressionMethod = CompressionMethod.STORE
            else compressionLevel = when (o.level) { Level.FAST -> CompressionLevel.FASTEST; Level.MAXIMUM -> CompressionLevel.ULTRA; else -> CompressionLevel.NORMAL }
            if (!o.password.isNullOrEmpty()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }
        net.lingala.zip4j.ZipFile(out, o.password?.takeIf { it.isNotEmpty() }?.toCharArray()).use { z ->
            items.forEach { f -> onFile(f.name); if (f.isDirectory) z.addFolder(f, params) else z.addFile(f, params) }
        }
    }

    private fun sevenZ(items: List<File>, out: File, level: Level, onFile: (String) -> Unit) {
        SevenZOutputFile(out).use { z ->
            z.setContentMethods(listOf(
                if (level == Level.STORE) SevenZMethodConfiguration(SevenZMethod.COPY)
                else SevenZMethodConfiguration(SevenZMethod.LZMA2, LZMA2Options(xzPreset(level))),
            ))
            items.forEach { root ->
                root.walkTopDown().forEach { f ->
                    val name = (if (f == root) root.name else root.name + "/" + f.relativeTo(root).path).replace(File.separatorChar, '/')
                    onFile(name)
                    val entry = z.createArchiveEntry(f, name)
                    z.putArchiveEntry(entry)
                    if (f.isFile) f.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; z.write(buf, 0, n) } }
                    z.closeArchiveEntry()
                }
            }
        }
    }

    private fun tar(items: List<File>, raw: OutputStream, onFile: (String) -> Unit) {
        TarArchiveOutputStream(raw).use { t ->
            t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            t.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            items.forEach { root ->
                root.walkTopDown().forEach { f ->
                    val name = (if (f == root) root.name else root.name + "/" + f.relativeTo(root).path).replace(File.separatorChar, '/') + if (f.isDirectory) "/" else ""
                    onFile(name)
                    t.putArchiveEntry(TarArchiveEntry(f, name))
                    if (f.isFile) f.inputStream().use { it.copyTo(t) }
                    t.closeArchiveEntry()
                }
            }
            t.finish()
        }
    }

    private fun gzipLevel(l: Level) = when (l) { Level.STORE -> 0; Level.FAST -> 1; Level.NORMAL -> 6; Level.MAXIMUM -> 9 }
    private fun xzPreset(l: Level) = when (l) { Level.STORE -> 0; Level.FAST -> 1; Level.NORMAL -> 6; Level.MAXIMUM -> 9 }
}

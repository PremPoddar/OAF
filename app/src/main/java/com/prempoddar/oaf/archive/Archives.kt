package com.prempoddar.oaf.archive

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile

/** One file or folder inside an archive; [path] uses "/" and never starts with one. */
data class ArchiveEntry(val path: String, val size: Long, val isDirectory: Boolean, val modified: Long?) {
    val name get() = path.trimEnd('/').substringAfterLast('/')
    val parent get() = path.trimEnd('/').substringBeforeLast('/', "")
}

enum class ArchiveType { ZIP, SEVEN_Z, TAR, TAR_GZ, TAR_BZ2, TAR_XZ, GZ, BZ2, XZ, RAR }

class ArchiveException(message: String) : IOException(message)

/**
 * Reads ZIP, 7z, TAR (plain or compressed), single compressed files and RAR. Names that would
 * land outside the destination ("../", absolute paths) are refused.
 */
object Archives {
    fun typeOf(name: String, head: ByteArray): ArchiveType? {
        val n = name.lowercase()
        fun starts(vararg b: Int) = head.size >= b.size && b.indices.all { head[it] == b[it].toByte() }
        return when {
            starts(0x50, 0x4B) -> ArchiveType.ZIP
            starts(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> ArchiveType.SEVEN_Z
            starts(0x52, 0x61, 0x72, 0x21) -> ArchiveType.RAR
            starts(0x1F, 0x8B) -> if (n.endsWith(".tar.gz") || n.endsWith(".tgz")) ArchiveType.TAR_GZ else ArchiveType.GZ
            starts(0x42, 0x5A, 0x68) -> if (n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz")) ArchiveType.TAR_BZ2 else ArchiveType.BZ2
            starts(0xFD, 0x37, 0x7A, 0x58, 0x5A) -> if (n.endsWith(".tar.xz") || n.endsWith(".txz")) ArchiveType.TAR_XZ else ArchiveType.XZ
            head.size > 262 && String(head, 257, 5, Charsets.US_ASCII) == "ustar" -> ArchiveType.TAR
            n.endsWith(".tar") -> ArchiveType.TAR
            else -> null
        }
    }

    /** A safe relative path, or null for names that try to escape. */
    fun clean(raw: String): String? {
        val p = raw.replace('\\', '/').trimStart('/')
        val parts = p.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." } || p.contains(':')) return null
        return parts.joinToString("/") + if (raw.endsWith("/") && parts.isNotEmpty()) "/" else ""
    }

    private fun singleName(file: File, ext: String) = file.name.let { if (it.lowercase().endsWith(ext)) it.dropLast(ext.length) else "$it.out" }

    private fun compressed(type: ArchiveType, input: InputStream): InputStream = when (type) {
        ArchiveType.TAR_GZ, ArchiveType.GZ -> GzipCompressorInputStream(input, true)
        ArchiveType.TAR_BZ2, ArchiveType.BZ2 -> BZip2CompressorInputStream(input, true)
        ArchiveType.TAR_XZ, ArchiveType.XZ -> XZCompressorInputStream(input, true)
        else -> input
    }

    /** Every entry. Folders implied by file paths are added so the archive can be browsed. */
    fun list(file: File, type: ArchiveType): List<ArchiveEntry> {
        val raw = ArrayList<ArchiveEntry>()
        walk(file, type) { e, _ -> raw += e; false }
        val dirs = raw.filter { it.isDirectory }.map { it.path.trimEnd('/') }.toMutableSet()
        val implied = ArrayList<ArchiveEntry>()
        raw.forEach { e ->
            var p = e.parent
            while (p.isNotEmpty() && dirs.add(p)) { implied += ArchiveEntry("$p/", 0, true, null); p = p.substringBeforeLast('/', "") }
        }
        return raw + implied
    }

    /**
     * Visits entries in order. [visit] gets each entry and a stream of its data (null for folders);
     * returning true stops the walk.
     */
    private fun walk(file: File, type: ArchiveType, visit: (ArchiveEntry, InputStream?) -> Boolean) {
        when (type) {
            ArchiveType.ZIP -> openZip(file).use { zip ->
                for (z in zip.entries()) {
                    val path = clean(z.name) ?: continue
                    val e = ArchiveEntry(path, z.size, z.isDirectory, z.time.takeIf { it > 0 })
                    val stop = if (z.isDirectory) visit(e, null) else zip.getInputStream(z).use { visit(e, it) }
                    if (stop) return
                }
            }
            ArchiveType.SEVEN_Z -> SevenZFile.builder().setFile(file).get().use { sz ->
                while (true) {
                    val z = sz.nextEntry ?: break
                    val path = clean(z.name) ?: continue
                    val e = ArchiveEntry(if (z.isDirectory && !path.endsWith("/")) "$path/" else path, z.size, z.isDirectory, runCatching { z.lastModifiedDate.time }.getOrNull())
                    val stop = if (z.isDirectory || !z.hasStream()) visit(e, null) else visit(e, sz.getInputStream(z))
                    if (stop) return
                }
            }
            ArchiveType.TAR, ArchiveType.TAR_GZ, ArchiveType.TAR_BZ2, ArchiveType.TAR_XZ ->
                TarArchiveInputStream(compressed(type, BufferedInputStream(file.inputStream()))).use { walkStream(it, visit) }
            ArchiveType.GZ, ArchiveType.BZ2, ArchiveType.XZ -> {
                val ext = when (type) { ArchiveType.GZ -> ".gz"; ArchiveType.BZ2 -> ".bz2"; else -> ".xz" }
                compressed(type, BufferedInputStream(file.inputStream())).use { visit(ArchiveEntry(singleName(file, ext), -1, false, file.lastModified()), it) }
            }
            ArchiveType.RAR -> {
                val archive = try { Archive(file) } catch (e: Exception) {
                    throw ArchiveException(if (e.javaClass.simpleName.contains("Unsupported")) "This RAR uses the RAR5 format, which OAF can't open yet" else "Couldn't read this RAR")
                }
                archive.use { a ->
                    if (a.isEncrypted) throw ArchiveException("This RAR is password protected")
                    for (h in a.fileHeaders) {
                        val path = clean(h.fileName) ?: continue
                        val e = ArchiveEntry(if (h.isDirectory && !path.endsWith("/")) "$path/" else path, h.fullUnpackSize, h.isDirectory, h.mTime?.time)
                        val stop = if (h.isDirectory) visit(e, null) else a.getInputStream(h).use { visit(e, it) }
                        if (stop) return
                    }
                }
            }
        }
    }

    private fun walkStream(s: ArchiveInputStream<*>, visit: (ArchiveEntry, InputStream?) -> Boolean) {
        while (true) {
            val z = s.nextEntry ?: break
            if (!s.canReadEntryData(z)) continue
            val path = clean(z.name) ?: continue
            val e = ArchiveEntry(path, z.size, z.isDirectory, runCatching { z.lastModifiedDate.time }.getOrNull())
            if (visit(e, if (z.isDirectory) null else s)) return
        }
    }

    /** Zip names are UTF-8 in newer files and the old DOS code page in many older ones. */
    private fun openZip(file: File): ZipFile = try {
        ZipFile(file, Charsets.UTF_8).also { z -> z.entries().asSequence().forEach { it.name } }
    } catch (e: IllegalArgumentException) {
        ZipFile(file, Charset.forName("IBM437"))
    } catch (e: java.util.zip.ZipException) {
        throw ArchiveException(if (e.message?.contains("encrypt", true) == true) "This ZIP is password protected" else "Couldn't read this ZIP")
    }

    /** Extracts one entry to [dest]. */
    fun extract(file: File, type: ArchiveType, path: String, dest: File) {
        var found = false
        walk(file, type) { e, data ->
            if (e.path != path || data == null) return@walk false
            dest.parentFile?.mkdirs()
            dest.outputStream().use { data.copyTo(it) }
            found = true
            true
        }
        if (!found) throw ArchiveException("$path isn't in the archive")
    }

    /** Extracts everything (or the entries under [prefix]) into [dir]. Returns the number of files written. */
    fun extractAll(file: File, type: ArchiveType, dir: File, prefix: String = "", progress: (Int) -> Unit = {}): Int {
        var n = 0
        val root = dir.canonicalFile
        walk(file, type) { e, data ->
            if (!e.path.startsWith(prefix)) return@walk false
            val rel = e.path.removePrefix(prefix).trimEnd('/')
            if (rel.isEmpty()) return@walk false
            val target = File(root, rel)
            if (!target.canonicalPath.startsWith(root.path + File.separator)) return@walk false
            if (e.isDirectory) target.mkdirs() else {
                target.parentFile?.mkdirs()
                target.outputStream().use { out -> data?.copyTo(out) }
                e.modified?.let { target.setLastModified(it) }
                progress(++n)
            }
            false
        }
        return n
    }
}

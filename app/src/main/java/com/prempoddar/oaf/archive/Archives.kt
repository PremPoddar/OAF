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

open class ArchiveException(message: String) : IOException(message)

/**
 * Reads ZIP, 7z, TAR (plain or compressed), single compressed files and RAR. Names that would
 * land outside the destination ("../", absolute paths) are refused.
 */
object Archives {
    /** The archive is locked: no password was given, or [wrong] the one given didn't open it. */
    class PasswordNeeded(val wrong: Boolean) : ArchiveException(if (wrong) "That password didn't work" else "This archive is password protected")
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
    fun list(file: File, type: ArchiveType, password: String? = null): List<ArchiveEntry> {
        val raw = ArrayList<ArchiveEntry>()
        walk(file, type, password, listing = true) { e, _ -> raw += e; false }
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
    private fun walk(file: File, type: ArchiveType, password: String?, listing: Boolean = false, visit: (ArchiveEntry, InputStream?) -> Boolean) {
        when (type) {
            ArchiveType.ZIP -> walkZip(file, password, listing, visit)
            ArchiveType.SEVEN_Z -> try { walkSevenZ(file, password, visit) } catch (e: org.apache.commons.compress.PasswordRequiredException) {
                throw PasswordNeeded(false)
            } catch (e: ArchiveException) {
                throw e
            } catch (e: IOException) {
                // A wrong key makes the (encrypted) header or data unreadable.
                if (password != null) throw PasswordNeeded(true) else throw e
            }
            ArchiveType.TAR, ArchiveType.TAR_GZ, ArchiveType.TAR_BZ2, ArchiveType.TAR_XZ ->
                TarArchiveInputStream(compressed(type, BufferedInputStream(file.inputStream()))).use { walkStream(it, visit) }
            ArchiveType.GZ, ArchiveType.BZ2, ArchiveType.XZ -> {
                val ext = when (type) { ArchiveType.GZ -> ".gz"; ArchiveType.BZ2 -> ".bz2"; else -> ".xz" }
                compressed(type, BufferedInputStream(file.inputStream())).use { visit(ArchiveEntry(singleName(file, ext), -1, false, file.lastModified()), it) }
            }
            ArchiveType.RAR -> walkRar(file, password, visit)
        }
    }

    private fun walkZip(file: File, password: String?, listing: Boolean, visit: (ArchiveEntry, InputStream?) -> Boolean) {
        val zip = net.lingala.zip4j.ZipFile(file, password?.toCharArray())
        zip.charset = Charsets.UTF_8
        zip.use {
            val headers = try { zip.fileHeaders } catch (e: net.lingala.zip4j.exception.ZipException) { throw ArchiveException("Couldn't read this ZIP") }
            if (headers.any { it.isEncrypted } && password == null) throw PasswordNeeded(false)
            var checked = false
            for (h in headers) {
                val path = clean(h.fileName) ?: continue
                val e = ArchiveEntry(path, h.uncompressedSize, h.isDirectory, h.lastModifiedTimeEpoch.takeIf { it > 0 })
                // Listing reads no data, except one encrypted entry to check the password.
                val needStream = !h.isDirectory && (!listing || (h.isEncrypted && !checked))
                val stop = if (!needStream) visit(e, null) else {
                    try {
                        zip.getInputStream(h).use { s ->
                            if (listing) { checked = true; s.read(ByteArray(1)); visit(e, null) } else visit(e, s)
                        }
                    } catch (x: net.lingala.zip4j.exception.ZipException) {
                        if (x.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD) throw PasswordNeeded(true) else throw ArchiveException(x.message ?: "Couldn't read this ZIP")
                    } catch (x: IOException) {
                        if (h.isEncrypted) throw PasswordNeeded(true) else throw x
                    }
                }
                if (stop) return
            }
        }
    }

    private fun walkSevenZ(file: File, password: String?, visit: (ArchiveEntry, InputStream?) -> Boolean) {
        SevenZFile.builder().setFile(file).apply { if (password != null) setPassword(password.toCharArray()) }.get().use { sz ->
                while (true) {
                    val z = sz.nextEntry ?: break
                    val path = clean(z.name) ?: continue
                    val e = ArchiveEntry(if (z.isDirectory && !path.endsWith("/")) "$path/" else path, z.size, z.isDirectory, runCatching { z.lastModifiedDate.time }.getOrNull())
                    val stop = if (z.isDirectory || !z.hasStream()) visit(e, null) else visit(e, sz.getInputStream(z))
                    if (stop) return
                }
        }
    }

    private fun walkRar(file: File, password: String?, visit: (ArchiveEntry, InputStream?) -> Boolean) {
        val archive = try { if (password != null) Archive(file, password) else Archive(file) } catch (e: Exception) {
            throw when {
                e.javaClass.simpleName.contains("UnsupportedRarV5") -> ArchiveException("This RAR uses the RAR5 format, which OAF can't open yet")
                e.javaClass.simpleName.contains("Encrypted") || e.javaClass.simpleName.contains("Decipher") -> if (password == null) PasswordNeeded(false) else PasswordNeeded(true)
                else -> ArchiveException("Couldn't read this RAR")
            }
        }
        archive.use { a ->
            if ((a.isPasswordProtected || a.isEncrypted) && password == null) throw PasswordNeeded(false)
            for (h in a.fileHeaders) {
                val path = clean(h.fileName) ?: continue
                val e = ArchiveEntry(if (h.isDirectory && !path.endsWith("/")) "$path/" else path, h.fullUnpackSize, h.isDirectory, h.mTime?.time)
                val stop = try {
                    if (h.isDirectory) visit(e, null) else a.getInputStream(h).use { visit(e, it) }
                } catch (x: Exception) {
                    if (password != null && (x.javaClass.simpleName.contains("Crc") || x.cause?.javaClass?.simpleName?.contains("Crc") == true)) throw PasswordNeeded(true) else throw x
                }
                if (stop) return
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

    /** Extracts one entry to [dest]. */
    fun extract(file: File, type: ArchiveType, path: String, dest: File, password: String? = null) {
        var found = false
        walk(file, type, password) { e, data ->
            if (e.path != path || data == null) return@walk false
            dest.parentFile?.mkdirs()
            dest.outputStream().use { data.copyTo(it) }
            found = true
            true
        }
        if (!found) throw ArchiveException("$path isn't in the archive")
    }

    /** Extracts everything (or the entries under [prefix]) into [dir]. Returns the number of files written. */
    fun extractAll(file: File, type: ArchiveType, dir: File, prefix: String = "", progress: (Int) -> Unit = {}, password: String? = null): Int {
        var n = 0
        val root = dir.canonicalFile
        walk(file, type, password) { e, data ->
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

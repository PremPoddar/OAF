package com.prempoddar.oaf.io

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.prempoddar.oaf.format.Detector
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.format.Lang
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * A file OAF has been asked to open, whether from its own browser (a real path) or from another
 * app (a content URI). Viewers read it through here; editors save through here.
 */
class DocSource(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String?,
    /** Set when OAF can reach the real file (its own browser, or file:// URIs). */
    val file: File?,
) {
    var kind: FileKind = FileKind.UNKNOWN
        private set
    var lang: Lang = Lang.PLAIN
        private set

    fun open(context: Context): InputStream = file?.inputStream() ?: context.contentResolver.openInputStream(uri) ?: error("Can't open $name")

    /** True when saving back to the same file is possible. */
    fun canWrite(context: Context): Boolean = when {
        file != null -> file.canWrite()
        uri.scheme == ContentResolver.SCHEME_CONTENT -> runCatching { context.contentResolver.openFileDescriptor(uri, "rw")?.close(); true }.getOrDefault(false)
        else -> false
    }

    /** Overwrites the original file. */
    fun openOutput(context: Context): OutputStream = file?.outputStream() ?: context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't save $name")

    /** A real file to read from: the file itself, or a copy in the cache for content URIs. */
    fun local(context: Context): File {
        file?.let { return it }
        val dir = File(context.cacheDir, "open").apply { mkdirs() }
        val copy = File(dir, "${uri.toString().hashCode().toUInt()}-${name.filter { it.isLetterOrDigit() || it == '.' }.takeLast(60)}")
        if (!copy.exists() || copy.length() != size) {
            open(context).use { i -> copy.outputStream().use { i.copyTo(it) } }
        }
        return copy
    }

    fun detect(context: Context): DocSource {
        val head = runCatching { open(context).use { s -> s.readNBytesCompat(8192) } }.getOrDefault(ByteArray(0))
        val r = Detector.detect(name, mime, head, zipEntries = { zipEntries(context) }, zipText = { zipText(context, it) })
        kind = r.kind
        lang = r.lang
        return this
    }

    private fun zipEntries(context: Context): List<String> = if (file != null) {
        ZipFile(file).use { z -> z.entries().asSequence().map { it.name }.toList() }
    } else {
        ZipInputStream(open(context)).use { zis -> generateSequence { zis.nextEntry }.map { it.name }.take(5_000).toList() }
    }

    private fun zipText(context: Context, entry: String): String? = runCatching {
        ZipInputStream(open(context)).use { zis ->
            generateSequence { zis.nextEntry }.firstOrNull { it.name == entry }?.let { zis.readNBytesCompat(512).decodeToString() }
        }
    }.getOrNull()

    companion object {
        fun fromFile(f: File) = DocSource(Uri.fromFile(f), f.name, f.length(), null, f)

        /**
         * A file handed over by another app. Its permission to read usually ends when that app
         * says so, so unless it can be kept for good (and saved back to), OAF keeps its own copy
         * in a private folder: Recents can then open it again later. Run off the main thread.
         */
        fun received(context: Context, uri: Uri, mimeHint: String?): DocSource {
            val original = fromUri(context, uri, mimeHint)
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return original
            val kept = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrDefault(false)
            if (kept) return original
            return runCatching {
                val dir = File(context.filesDir, "received/${uri.toString().hashCode().toUInt()}").apply { mkdirs() }
                val safe = original.name.replace(Regex("[\\/:*?\"<>|]"), "_").ifBlank { "file" }
                val copy = File(dir, safe)
                original.open(context).use { i -> copy.outputStream().use { i.copyTo(it) } }
                DocSource(Uri.fromFile(copy), original.name, copy.length(), original.mime, copy)
            }.getOrDefault(original)
        }

        fun fromUri(context: Context, uri: Uri, mimeHint: String? = null): DocSource {
            if (uri.scheme == ContentResolver.SCHEME_FILE) uri.path?.let { return fromFile(File(it)) }
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            var size = -1L
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            }
            return DocSource(uri, name, size, mimeHint ?: context.contentResolver.getType(uri), null)
        }
    }
}

/** Reads up to [n] bytes (InputStream.readNBytes needs Android 13). */
fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val out = ByteArray(n)
    var total = 0
    while (total < n) {
        val r = read(out, total, n - total)
        if (r < 0) break
        total += r
    }
    return out.copyOf(total)
}

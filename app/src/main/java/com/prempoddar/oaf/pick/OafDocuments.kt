package com.prempoddar.oaf.pick

import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.prempoddar.oaf.R
import com.prempoddar.oaf.ui.browser.Storage
import java.io.File
import java.io.FileNotFoundException

/**
 * OAF in Android's own file picker ("Open from"), for apps that open documents through it rather
 * than asking an app: the phone's storage and SD cards, browsable, searchable and editable by the
 * app that asked, as Acode and other file apps offer. Needs "All files access".
 */
class OafDocuments : DocumentsProvider() {
    private val rootColumns = arrayOf(
        Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY,
        Root.COLUMN_DOCUMENT_ID, Root.COLUMN_AVAILABLE_BYTES, Root.COLUMN_MIME_TYPES,
    )
    private val docColumns = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
    )

    override fun onCreate() = true

    private fun roots() = runCatching { Storage.roots(context!!) }.getOrDefault(emptyList())

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: rootColumns)
        if (!Storage.hasAllFilesAccess()) return c
        roots().forEachIndexed { i, r ->
            c.newRow().apply {
                add(Root.COLUMN_ROOT_ID, "oaf$i")
                add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_LOCAL_ONLY)
                add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(Root.COLUMN_TITLE, "OAF")
                add(Root.COLUMN_SUMMARY, "${r.label} · ${com.prempoddar.oaf.ui.browser.humanSize(r.dir.freeSpace)} free")
                add(Root.COLUMN_DOCUMENT_ID, r.dir.absolutePath)
                add(Root.COLUMN_AVAILABLE_BYTES, r.dir.freeSpace)
                add(Root.COLUMN_MIME_TYPES, "*/*")
            }
        }
        return c
    }

    /** Document ids are absolute paths, kept inside the storage roots. */
    private fun fileFor(id: String): File {
        val f = File(id).canonicalFile
        if (roots().none { r -> f.path == r.dir.canonicalPath || f.path.startsWith(r.dir.canonicalPath + "/") }) throw FileNotFoundException(id)
        return f
    }

    private fun mimeOf(f: File) = if (f.isDirectory) Document.MIME_TYPE_DIR
        else MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"

    private fun addRow(c: MatrixCursor, f: File) {
        var flags = 0
        if (f.canWrite()) {
            flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
            flags = flags or if (f.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
        }
        val mime = mimeOf(f)
        if (mime.startsWith("image/") || mime.startsWith("video/")) flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        c.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, f.absolutePath)
            add(Document.COLUMN_MIME_TYPE, mime)
            add(Document.COLUMN_DISPLAY_NAME, f.name)
            add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_SIZE, if (f.isFile) f.length() else null)
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: docColumns).also { addRow(it, fileFor(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val c = MatrixCursor(projection ?: docColumns)
        fileFor(parentDocumentId).listFiles()?.filter { !it.name.startsWith(".") }?.forEach { addRow(c, it) }
        return c
    }

    override fun querySearchDocuments(rootId: String, query: String, projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: docColumns)
        val root = roots().getOrNull(rootId.removePrefix("oaf").toIntOrNull() ?: -1)?.dir ?: return c
        var found = 0
        root.walkTopDown().onEnter { !it.name.startsWith(".") && it.name != "Android" }
            .filter { it != root && it.name.contains(query, ignoreCase = true) }
            .forEach { if (found < 200) { addRow(c, it); found++ } }
        return c
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point, signal: CancellationSignal?): android.content.res.AssetFileDescriptor {
        val f = fileFor(documentId)
        val size = android.util.Size(sizeHint.x.coerceAtLeast(64), sizeHint.y.coerceAtLeast(64))
        val bmp = if (mimeOf(f).startsWith("video/")) android.media.ThumbnailUtils.createVideoThumbnail(f, size, signal)
        else android.media.ThumbnailUtils.createImageThumbnail(f, size, signal)
        val out = File(context!!.cacheDir, "doc-thumbs").apply { mkdirs() }.let { File(it, "${f.path.hashCode().toUInt()}.jpg") }
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        return android.content.res.AssetFileDescriptor(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY), 0, android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        runCatching { fileFor(documentId).path.startsWith(fileFor(parentDocumentId).path + "/") }.getOrDefault(false)

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val dir = fileFor(parentDocumentId)
        val clean = displayName.replace('/', '_')
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        val wanted = if (mimeType != Document.MIME_TYPE_DIR && ext != null && !clean.endsWith(".$ext", true)) "$clean.$ext" else clean
        val f = com.prempoddar.oaf.files.FileOps.freeName(dir, wanted)
        val ok = if (mimeType == Document.MIME_TYPE_DIR) f.mkdir() else f.createNewFile()
        if (!ok) throw FileNotFoundException("Couldn't create $wanted")
        return f.absolutePath
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val f = fileFor(documentId)
        val target = File(f.parentFile, displayName.replace('/', '_'))
        if (target.exists() || !f.renameTo(target)) throw FileNotFoundException("Couldn't rename ${f.name}")
        return target.absolutePath
    }

    override fun deleteDocument(documentId: String) {
        val f = fileFor(documentId)
        if (!f.deleteRecursively()) throw FileNotFoundException("Couldn't delete ${f.name}")
    }

    override fun getDocumentType(documentId: String): String = mimeOf(fileFor(documentId))
}

package com.prempoddar.oaf.ui.browser

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import java.io.File

data class Root(val label: String, val dir: File)

object Storage {
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun roots(context: Context): List<Root> {
        val primary = Environment.getExternalStorageDirectory()
        val out = mutableListOf(Root("Phone", primary))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(StorageManager::class.java).storageVolumes
                .filter { !it.isPrimary && it.directory != null }
                .forEach { out += Root(it.getDescription(context) ?: "SD card", it.directory!!) }
        }
        return out
    }

    fun quickFolders(): List<Root> = listOf(
        "Downloads" to Environment.DIRECTORY_DOWNLOADS, "Documents" to Environment.DIRECTORY_DOCUMENTS,
        "Pictures" to Environment.DIRECTORY_PICTURES, "Camera" to Environment.DIRECTORY_DCIM,
        "Music" to Environment.DIRECTORY_MUSIC, "Movies" to Environment.DIRECTORY_MOVIES,
    ).map { (label, d) -> Root(label, Environment.getExternalStoragePublicDirectory(d)) }.filter { it.dir.isDirectory }

    data class Usage(val total: Long, val free: Long, val images: Long, val video: Long, val audio: Long, val documents: Long)

    /** How the phone's storage is used, from MediaStore (fast) rather than walking every folder. */
    fun usage(context: Context): Usage {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        fun sum(uri: android.net.Uri, selection: String? = null): Long = runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), selection, null, null)?.use { c ->
                var s = 0L
                while (c.moveToNext()) s += c.getLong(0)
                s
            } ?: 0L
        }.getOrDefault(0L)
        val docMimes = "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/%' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%')"
        return Usage(
            total = stat.totalBytes, free = stat.availableBytes,
            images = sum(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            video = sum(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
            audio = sum(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
            documents = sum(MediaStore.Files.getContentUri("external"), docMimes),
        )
    }
}

fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v < 10) "%.1f %s".format(v, units[i]) else "%.0f %s".format(v, units[i])
}

package com.prempoddar.oaf.files

import java.io.File
import java.security.MessageDigest

/**
 * Finds things worth clearing out of a storage root: the biggest files, copies of the same file,
 * app installers that have been sitting around, and folders with nothing in them. Nothing is
 * deleted here; the storage screen offers each find to the bin.
 */
object CleanUp {
    data class Dupes(val size: Long, val files: List<File>) {
        /** What deleting all but one copy would free. */
        val spare get() = size * (files.size - 1)
    }

    data class Report(val large: List<File>, val duplicates: List<Dupes>, val installers: List<File>, val emptyFolders: List<File>) {
        val isEmpty get() = large.isEmpty() && duplicates.isEmpty() && installers.isEmpty() && emptyFolders.isEmpty()
    }

    const val LARGE_BYTES = 100L * 1024 * 1024
    const val OLD_INSTALLER_MS = 30L * 24 * 3600 * 1000

    /** Folders a phone needs or that belong to apps; their contents aren't ours to suggest. */
    private val skip = setOf("Android", ".thumbnails", ".Trash")

    fun scan(
        root: File, now: Long = System.currentTimeMillis(), largeBytes: Long = LARGE_BYTES,
        minDupeBytes: Long = 1024 * 1024, maxFiles: Int = 50_000, cancelled: () -> Boolean = { false },
    ): Report {
        val files = mutableListOf<File>()
        val empty = mutableListOf<File>()
        // Depth-first; a folder is empty if it has no entries at all (hidden ones count).
        fun walk(dir: File, depth: Int) {
            if (cancelled() || files.size >= maxFiles || depth > 24) return
            val kids = dir.listFiles() ?: return
            if (kids.isEmpty() && dir != root) { empty += dir; return }
            for (k in kids) {
                if (k.name in skip) continue
                if (k.isDirectory) walk(k, depth + 1) else if (k.isFile) files += k
            }
        }
        walk(root, 0)
        val large = files.filter { it.length() >= largeBytes }.sortedByDescending { it.length() }.take(20)
        val installers = files.filter { it.extension.lowercase() in setOf("apk", "apks", "xapk", "apkm") && now - it.lastModified() > OLD_INSTALLER_MS }
            .sortedByDescending { it.length() }
        return Report(large, duplicates(files, minDupeBytes, cancelled), installers, empty.sortedBy { it.path })
    }

    /** Same size first (cheap), then the same contents; biggest savings first. */
    fun duplicates(files: List<File>, minBytes: Long = 1024 * 1024, cancelled: () -> Boolean = { false }): List<Dupes> =
        files.filter { it.length() >= minBytes }.groupBy { it.length() }.values.filter { it.size > 1 }
            .flatMap { same -> if (cancelled()) emptyList() else same.groupBy { hash(it) }.filterKeys { it != null }.values.filter { it.size > 1 } }
            .map { g -> Dupes(g[0].length(), g.sortedBy { it.lastModified() }) }
            .sortedByDescending { it.spare }

    private fun hash(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}

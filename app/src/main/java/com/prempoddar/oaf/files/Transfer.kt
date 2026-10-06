package com.prempoddar.oaf.files

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

enum class ClipMode { COPY, CUT }

/** Files waiting to be pasted. */
data class Clip(val mode: ClipMode, val files: List<File>)

/**
 * Pastes a [Clip] into a folder: copies or moves every item, reports progress in bytes, and asks
 * what to do when a name is already taken. Cancelling the coroutine stops between blocks.
 */
class Transfer(
    /** Called for a name that's taken; returns the choice and whether it applies to the rest. */
    private val ask: suspend (existing: File) -> Pair<Resolution, Boolean>,
    /** Gets rid of an item being replaced; the app sends it to the bin. */
    private val discard: (File) -> Unit,
) {
    enum class Resolution { REPLACE, SKIP, KEEP_BOTH }
    data class Progress(val done: Long, val total: Long, val current: String)

    suspend fun run(clip: Clip, into: File, onProgress: (Progress) -> Unit): List<File> {
        val dest = into.canonicalFile
        clip.files.forEach { src ->
            val c = src.canonicalFile
            if (c.isDirectory && (dest == c || dest.path.startsWith(c.path + File.separator))) {
                throw FileOps.Conflict("“${src.name}” can't go inside itself")
            }
        }
        val total = clip.files.sumOf { FileOps.size(it) }
        var done = 0L
        var always: Resolution? = null
        val made = ArrayList<File>()
        for (src in clip.files) {
            currentCoroutineContext().ensureActive()
            val samePlace = src.parentFile?.canonicalFile == dest
            if (samePlace && clip.mode == ClipMode.CUT) continue
            var target = File(dest, src.name)
            if (samePlace) {
                target = FileOps.freeName(dest, src.name)
            } else if (target.exists()) {
                val choice = always ?: ask(target).let { (r, all) -> if (all) always = r; r }
                when (choice) {
                    Resolution.SKIP -> { done += FileOps.size(src); onProgress(Progress(done, total, src.name)); continue }
                    Resolution.KEEP_BOTH -> target = FileOps.freeName(dest, src.name)
                    Resolution.REPLACE -> discard(target)
                }
            }
            if (clip.mode == ClipMode.CUT && src.renameTo(target)) {
                done += FileOps.size(target)
                onProgress(Progress(done, total, src.name))
            } else {
                copyTree(src, target) { n -> done += n; onProgress(Progress(done, total, src.name)) }
                if (clip.mode == ClipMode.CUT) src.deleteRecursively()
            }
            made += target
        }
        onProgress(Progress(total, total, ""))
        return made
    }

    private suspend fun copyTree(src: File, target: File, onBytes: (Long) -> Unit) {
        for (f in src.walkTopDown()) {
            val rel = f.relativeTo(src).path
            val out = if (rel.isEmpty()) target else File(target, rel)
            if (f.isDirectory) { out.mkdirs(); continue }
            out.parentFile?.mkdirs()
            f.inputStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
            out.setLastModified(f.lastModified())
        }
    }

    private companion object {
        const val BUFFER = 256 * 1024
    }
}

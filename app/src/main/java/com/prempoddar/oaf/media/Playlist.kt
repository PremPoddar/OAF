package com.prempoddar.oaf.media

import java.io.File

/** What plays after the file that was opened: its neighbours of the same kind, in the order a person would sort them. */
class Playlist(val items: List<File>, val start: Int) {
    companion object {
        val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "amr", "mid", "midi", "mka")
        val VIDEO = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "ts", "flv", "wmv")
        private val SUBS = setOf("srt", "vtt", "ass", "ssa", "ttml")

        fun isVideo(f: File) = f.extension.lowercase() in VIDEO

        fun around(file: File): Playlist {
            val kinds = if (isVideo(file)) VIDEO else AUDIO
            val items = file.parentFile?.listFiles()
                ?.filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in kinds }
                ?.sortedWith(NaturalOrder)
                ?: listOf(file)
            val start = items.indexOfFirst { it.path == file.path }
            return if (start < 0) Playlist(listOf(file), 0) else Playlist(items, start)
        }

        /** Subtitle files beside a video that share its name, like Film.srt or Film.en.vtt. */
        fun subtitlesFor(video: File): List<File> {
            val stem = video.nameWithoutExtension
            return video.parentFile?.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in SUBS && (it.nameWithoutExtension == stem || it.name.startsWith("$stem.")) }
                ?.sortedBy { it.name }
                .orEmpty()
        }

        /** "en" from Film.en.vtt; null when the name carries no language. */
        fun language(sub: File, stem: String): String? =
            sub.nameWithoutExtension.removePrefix(stem).removePrefix(".").takeIf { it.isNotEmpty() && it.length <= 8 }

        fun mimeOfSubtitle(f: File): String = when (f.extension.lowercase()) {
            "vtt" -> "text/vtt"
            "ass", "ssa" -> "text/x-ssa"
            "ttml" -> "application/ttml+xml"
            else -> "application/x-subrip"
        }
    }
}

/** Sorts "2 Two" before "10 Ten" by comparing runs of digits as numbers. */
object NaturalOrder : Comparator<File> {
    private val chunk = Regex("\\d+|\\D+")
    override fun compare(a: File, b: File): Int {
        val x = chunk.findAll(a.name.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.name.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) (p.toBigInteger().compareTo(q.toBigInteger())).takeIf { it != 0 } ?: p.length.compareTo(q.length) else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}

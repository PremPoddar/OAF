package com.prempoddar.oaf.files

import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.ui.browser.kindOf
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/**
 * Finds files by what's written inside them: text and code, Word, Excel and PowerPoint (and their
 * OpenDocument cousins), and PDFs. Each match comes with a snippet of the words around it.
 */
object ContentSearch {
    data class Hit(val file: File, val snippet: String)

    private const val MAX_BYTES = 25L * 1024 * 1024
    private const val MAX_TEXT = 2_000_000
    private const val PDF_PAGES = 40

    /** Up to [limit] matches for [query] under [dir], nearest folders first. */
    suspend fun search(dir: File, query: String, showHidden: Boolean, limit: Int = 200, maxFiles: Int = 4_000): List<Hit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val hits = ArrayList<Hit>()
        var looked = 0
        for (f in dir.walkTopDown().onEnter { showHidden || !it.name.startsWith(".") || it == dir }.maxDepth(12)) {
            coroutineContext.ensureActive()
            if (!f.isFile || f.length() > MAX_BYTES || (!showHidden && f.name.startsWith("."))) continue
            val text = runCatching { textOf(f) }.getOrNull() ?: continue
            if (++looked > maxFiles) break
            snippet(text, q)?.let { hits += Hit(f, it) }
            if (hits.size >= limit) break
        }
        return hits
    }

    /** The searchable words in [f], or null for files with no text to search. */
    fun textOf(f: File): String? = when (kindOf(f)) {
        FileKind.TEXT, FileKind.CODE, FileKind.MARKDOWN, FileKind.CSV, FileKind.TSV, FileKind.JSON, FileKind.XML, FileKind.YAML -> readText(f)
        FileKind.HTML, FileKind.SVG -> readText(f)?.let(::stripTags)
        FileKind.DOCX -> zipText(f) { it == "word/document.xml" || it.startsWith("word/header") || it.startsWith("word/footer") }
        FileKind.XLSX -> zipText(f) { it == "xl/sharedStrings.xml" || it.startsWith("xl/worksheets/") }
        FileKind.PPTX -> zipText(f) { it.startsWith("ppt/slides/slide") || it.startsWith("ppt/notesSlides/") }
        FileKind.ODT, FileKind.ODS, FileKind.ODP -> zipText(f) { it == "content.xml" }
        FileKind.EPUB -> zipText(f) { it.endsWith(".xhtml") || it.endsWith(".html") }
        FileKind.PDF -> pdfText(f)
        else -> null
    }

    private fun readText(f: File): String? = f.inputStream().use { input ->
        val buf = ByteArray(minOf(f.length(), MAX_TEXT.toLong()).toInt())
        var n = 0
        while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
        // Binary files that slipped through (a .txt that isn't) have NULs; skip them.
        if (buf.take(n.coerceAtMost(1024)).any { it == 0.toByte() }) null else String(buf, 0, n, Charsets.UTF_8)
    }

    private fun zipText(f: File, wanted: (String) -> Boolean): String = ZipFile(f).use { zip ->
        buildString {
            zip.entries().asSequence().filter { !it.isDirectory && wanted(it.name) }.forEach { e ->
                if (length < MAX_TEXT) append(stripTags(zip.getInputStream(e).bufferedReader().readText())).append('\n')
            }
        }
    }

    private fun pdfText(f: File): String = com.tom_roush.pdfbox.pdmodel.PDDocument.load(f).use { doc ->
        com.tom_roush.pdfbox.text.PDFTextStripper().apply { endPage = minOf(doc.numberOfPages, PDF_PAGES) }.getText(doc)
    }

    /** Markup out, entities decoded, and a space where tags were so words don't run together. */
    fun stripTags(xml: String): String = xml
        .replace(Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
        .replace(Regex("</(w:p|a:p|text:p|p|div|br|tr|li|h[1-6])>|<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("[ \t]+"), " ")

    /** The words around the first match, or null if [query] isn't in [text]. */
    fun snippet(text: String, query: String): String? {
        val at = text.indexOf(query, ignoreCase = true)
        if (at < 0) return null
        val start = maxOf(0, at - 40); val end = minOf(text.length, at + query.length + 60)
        val piece = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
        return (if (start > 0) "…" else "") + piece + (if (end < text.length) "…" else "")
    }
}

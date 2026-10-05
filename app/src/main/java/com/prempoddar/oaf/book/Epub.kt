package com.prempoddar.oaf.book

import com.prempoddar.oaf.archive.Archives
import com.prempoddar.oaf.xml.Xml
import com.prempoddar.oaf.xml.descendants
import com.prempoddar.oaf.xml.kids
import org.w3c.dom.Element
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/** A book unpacked to [root]: chapters in reading order and its table of contents. */
class EpubBook(val title: String, val author: String?, val root: File, val chapters: List<Chapter>, val toc: List<TocItem>) {
    /** [file] is the chapter on disk; [title] comes from the table of contents when it names it. */
    class Chapter(val file: File, val title: String?)
    class TocItem(val title: String, val chapter: Int, val fragment: String?, val level: Int)

    fun chapterOf(file: File): Int = chapters.indexOfFirst { it.file.canonicalPath == file.canonicalPath }
}

object Epub {
    fun open(epub: File, dir: File): EpubBook {
        unpack(epub, dir)
        val container = Xml.parse(File(dir, "META-INF/container.xml").takeIf { it.exists() }?.readBytes() ?: error("Not an EPUB (no container)"))
        val opfPath = container.documentElement.descendants("rootfile").firstOrNull()?.getAttribute("full-path") ?: error("Not an EPUB (no package)")
        val opfFile = File(dir, opfPath)
        val opf = Xml.parse(opfFile.readBytes()).documentElement
        val base = opfFile.parentFile!!
        val meta = opf.descendants("metadata").firstOrNull()
        val title = meta?.descendants("title")?.firstOrNull()?.textContent?.trim()?.ifEmpty { null } ?: epub.nameWithoutExtension
        val author = meta?.descendants("creator")?.firstOrNull()?.textContent?.trim()?.ifEmpty { null }

        class Item(val id: String, val href: String, val type: String, val props: String)
        val manifest = opf.descendants("item").map { Item(it.getAttribute("id"), it.getAttribute("href"), it.getAttribute("media-type"), it.getAttribute("properties")) }
        val byId = manifest.associateBy { it.id }
        fun fileOf(href: String, from: File = base) = File(from, URLDecoder.decode(href.substringBefore('#'), "UTF-8")).canonicalFile
        val spine = opf.descendants("itemref").filter { it.getAttribute("linear") != "no" }.mapNotNull { byId[it.getAttribute("idref")] }
            .filter { it.type.contains("html") || it.type.contains("xml") }.map { fileOf(it.href) }.filter { it.exists() }

        // Table of contents: EPUB 3 navigation document, else the EPUB 2 NCX.
        val tocEntries = ArrayList<Triple<String, String, Int>>() // title, href relative to base dir of the toc file, level
        var tocDir = base
        val nav = manifest.firstOrNull { it.props.split(' ').contains("nav") }
        if (nav != null) runCatching {
            val navFile = fileOf(nav.href)
            tocDir = navFile.parentFile!!
            val doc = Xml.parse(navFile.readBytes()).documentElement
            val tocNav = doc.descendants("nav").firstOrNull { n -> n.attributes.let { a -> (0 until a.length).any { a.item(it).nodeName.endsWith("type") && a.item(it).nodeValue == "toc" } } }
                ?: doc.descendants("nav").firstOrNull()
            fun walk(ol: Element, level: Int) {
                ol.kids("li").forEach { li ->
                    val a = li.kids().firstOrNull { it.localName == "a" || it.localName == "span" }
                    val href = a?.getAttribute("href").orEmpty()
                    if (a != null && href.isNotEmpty()) tocEntries += Triple(a.textContent.trim().replace(Regex("\\s+"), " "), href, level)
                    li.kids("ol").forEach { walk(it, level + 1) }
                }
            }
            tocNav?.kids("ol")?.forEach { walk(it, 0) }
        }
        if (tocEntries.isEmpty()) {
            val ncxItem = opf.descendants("spine").firstOrNull()?.getAttribute("toc")?.let { byId[it] } ?: manifest.firstOrNull { it.type == "application/x-dtbncx+xml" }
            if (ncxItem != null) runCatching {
                val ncxFile = fileOf(ncxItem.href)
                tocDir = ncxFile.parentFile!!
                val doc = Xml.parse(ncxFile.readBytes()).documentElement
                fun walk(parent: Element, level: Int) {
                    parent.kids("navPoint").forEach { np ->
                        val label = np.descendants("text").firstOrNull()?.textContent?.trim().orEmpty()
                        val src = np.kids("content").firstOrNull()?.getAttribute("src").orEmpty()
                        if (src.isNotEmpty()) tocEntries += Triple(label, src, level)
                        walk(np, level + 1)
                    }
                }
                doc.descendants("navMap").firstOrNull()?.let { walk(it, 0) }
            }
        }
        val toc = tocEntries.mapNotNull { (t, href, level) ->
            val f = fileOf(href, tocDir)
            val i = spine.indexOfFirst { it.path == f.path }
            if (i < 0) null else EpubBook.TocItem(t.ifEmpty { "Chapter ${i + 1}" }, i, href.substringAfter('#', "").ifEmpty { null }, level)
        }
        val chapters = spine.mapIndexed { i, f -> EpubBook.Chapter(f, toc.firstOrNull { it.chapter == i && it.fragment == null }?.title ?: toc.firstOrNull { it.chapter == i }?.title) }
        if (chapters.isEmpty()) error("This EPUB has no readable chapters")
        return EpubBook(title, author, dir, chapters, toc)
    }

    /** Unpacks once per book: a marker remembers which file the folder holds. */
    private fun unpack(epub: File, dir: File) {
        val marker = File(dir, ".oaf-${epub.length()}-${epub.lastModified()}")
        if (marker.exists()) return
        dir.deleteRecursively()
        dir.mkdirs()
        val root = dir.canonicalFile
        ZipFile(epub).use { zip ->
            for (e in zip.entries()) {
                if (e.isDirectory) continue
                val path = Archives.clean(e.name) ?: continue
                val target = File(root, path)
                if (!target.canonicalPath.startsWith(root.path + File.separator)) continue
                target.parentFile?.mkdirs()
                zip.getInputStream(e).use { i -> target.outputStream().use { i.copyTo(it) } }
            }
        }
        marker.createNewFile()
    }
}

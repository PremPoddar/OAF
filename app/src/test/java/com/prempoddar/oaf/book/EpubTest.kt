package com.prempoddar.oaf.book

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun epub(files: Map<String, String>): File = tmp.newFile("book.epub").also { f ->
        ZipOutputStream(f.outputStream()).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }

    private val container = """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
    private fun chapter(t: String) = """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1 id="top">$t</h1></body></html>"""

    @Test fun epub3WithNavigation() {
        val book = Epub.open(epub(mapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>The Book</dc:title><dc:creator>A. Writer</dc:creator></metadata>
<manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="text/one.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="text/two%20b.xhtml" media-type="application/xhtml+xml"/><item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/></manifest>
<spine><itemref idref="cover" linear="no"/><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            "OEBPS/nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="text/one.xhtml">One</a><ol><li><a href="text/one.xhtml#part">Part</a></li></ol></li><li><a href="text/two%20b.xhtml">Two</a></li></ol></nav></body></html>""",
            "OEBPS/text/one.xhtml" to chapter("One"),
            "OEBPS/text/two b.xhtml" to chapter("Two"),
            "OEBPS/cover.xhtml" to chapter("Cover"),
            "../outside.txt" to "nope",
        )), tmp.newFolder("b"))
        assertThat(book.title).isEqualTo("The Book")
        assertThat(book.author).isEqualTo("A. Writer")
        assertThat(book.chapters.map { it.title }).containsExactly("One", "Two").inOrder()
        assertThat(book.toc.map { Triple(it.title, it.chapter, it.level) }).containsExactly(Triple("One", 0, 0), Triple("Part", 0, 1), Triple("Two", 1, 0)).inOrder()
        assertThat(book.toc[1].fragment).isEqualTo("part")
        assertThat(book.chapterOf(book.chapters[1].file)).isEqualTo(1)
        assertThat(File(tmp.root, "outside.txt").exists()).isFalse()
    }

    @Test fun epub2WithNcx() {
        val book = Epub.open(epub(mapOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata/><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="a" href="a.html" media-type="application/xhtml+xml"/></manifest><spine toc="ncx"><itemref idref="a"/></spine></package>""",
            "OEBPS/toc.ncx" to """<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint id="p1"><navLabel><text>Start here</text></navLabel><content src="a.html"/></navPoint></navMap></ncx>""",
            "OEBPS/a.html" to chapter("A"),
        )), tmp.newFolder("c"))
        assertThat(book.title).isEqualTo("book")
        assertThat(book.chapters.single().title).isEqualTo("Start here")
    }
}

package com.prempoddar.oaf.files

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContentSearchTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(name: String, vararg entries: Pair<String, String>) = File(tmp.root, name).also { f ->
        ZipOutputStream(f.outputStream()).use { z -> entries.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }

    @Test fun findsWordsInsideDocumentsSheetsSlidesPdfsAndText() = runBlocking {
        PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext<Application>())
        zip("letter.docx", "word/document.xml" to "<w:document><w:body><w:p><w:r><w:t>Dear Asha, the invoice number is INV-2047.</w:t></w:r></w:p></w:body></w:document>")
        zip("budget.xlsx", "xl/sharedStrings.xml" to "<sst><si><t>Groceries</t></si><si><t>Rent &amp; bills</t></si></sst>")
        zip("talk.pptx", "ppt/slides/slide1.xml" to "<p:sld><a:t>Quarterly roadmap</a:t></p:sld>")
        File(tmp.newFolder("notes"), "todo.md").writeText("- call the plumber about the leak")
        PDDocument().use { doc ->
            val page = PDPage(); doc.addPage(page)
            PDPageContentStream(doc, page).use { cs -> cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(50f, 700f); cs.showText("Boarding pass gate 14"); cs.endText() }
            doc.save(File(tmp.root, "ticket.pdf"))
        }
        File(tmp.root, "photo.jpg").writeBytes(ByteArray(100) { 0 })

        fun names(q: String) = runBlocking { ContentSearch.search(tmp.root, q, showHidden = false).map { it.file.name } }
        assertThat(names("inv-2047")).containsExactly("letter.docx")
        assertThat(names("rent & bills")).containsExactly("budget.xlsx")
        assertThat(names("roadmap")).containsExactly("talk.pptx")
        assertThat(names("plumber")).containsExactly("todo.md")
        assertThat(names("gate 14")).containsExactly("ticket.pdf")
        assertThat(names("zzzz")).isEmpty()
    }

    @Test fun snippetsShowTheWordsAround() {
        val s = ContentSearch.snippet("x".repeat(100) + " the quick brown fox jumps over the lazy dog " + "y".repeat(100), "brown fox")!!
        assertThat(s).contains("quick brown fox jumps")
        assertThat(s).startsWith("…")
    }
}

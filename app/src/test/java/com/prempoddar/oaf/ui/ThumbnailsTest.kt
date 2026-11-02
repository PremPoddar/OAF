package com.prempoddar.oaf.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.sheet.CellStyle
import com.prempoddar.oaf.sheet.Sheet
import com.prempoddar.oaf.sheet.Workbook
import com.prempoddar.oaf.sheet.Xlsx
import com.prempoddar.oaf.sheet.XlsxDoc
import com.prempoddar.oaf.ui.browser.DocThumbs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Draws a preview of each kind of document, and keeps them in build/thumbs to look at. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34])
class ThumbnailsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun check(name: String, file: File, kind: FileKind): Bitmap {
        val b = DocThumbs.load(context, file, kind, 384)
        assertThat(b).isNotNull()
        File("build/thumbs").apply { mkdirs() }.resolve("$name.png").outputStream().use { b!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return b!!
    }

    @Test fun wordDocument() {
        val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val body = buildString {
            append("""<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Picnic plan</w:t></w:r></w:p>""")
            append("""<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>What to bring</w:t></w:r></w:p>""")
            repeat(8) { append("""<w:p><w:r><w:t xml:space="preserve">Sandwiches, lemonade and a blanket big enough for everyone. </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>Don't forget the frisbee.</w:t></w:r></w:p>""") }
        }
        val parts = linkedMapOf(
            "[Content_Types].xml" to """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>""",
            "_rels/.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$r/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/document.xml" to """<w:document xmlns:w="$w" xmlns:r="$r"><w:body>$body</w:body></w:document>""",
        )
        val f = tmp.newFile("plan.docx")
        ZipOutputStream(f.outputStream()).use { z -> parts.forEach { (k, v) -> z.putNextEntry(ZipEntry(k)); z.write(v.toByteArray()); z.closeEntry() } }
        val b = check("docx", f, FileKind.DOCX)
        assertThat(b.height).isGreaterThan(b.width)
    }

    @Test fun spreadsheet() {
        val wb = Workbook(mutableListOf(Sheet("Budget")))
        val s = wb.sheets[0]
        s.set(0, 0, "Item", CellStyle(bold = true, fill = 0xFFFFE08A.toInt())); s.set(0, 1, "Cost", CellStyle(bold = true, fill = 0xFFFFE08A.toInt()))
        listOf("Rent" to "1200", "Food" to "300", "Bus" to "45", "Phone" to "20").forEachIndexed { i, (a, c) -> s.set(i + 1, 0, a); s.set(i + 1, 1, c) }
        s.set(5, 0, "Total", CellStyle(bold = true)); s.set(5, 1, "=SUM(B2:B5)", CellStyle(bold = true))
        val f = tmp.newFile("budget.xlsx")
        f.outputStream().use { Xlsx.write(XlsxDoc(wb, emptyList()), null, it) }
        check("xlsx", f, FileKind.XLSX)
    }

    @Test fun presentation() {
        val f = tmp.newFile("picnic.pptx")
        javaClass.getResourceAsStream("/decks/picnic.pptx")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        val b = check("pptx", f, FileKind.PPTX)
        assertThat(b.width).isGreaterThan(b.height)
    }

    @Test fun text() {
        val f = tmp.newFile("notes.md")
        f.writeText("# Notes\n\n- buy bread\n- call Sam\n\nfun main() {\n    println(\"hi\")\n}\n")
        check("md", f, FileKind.MARKDOWN)
    }
}

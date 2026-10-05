package com.prempoddar.oaf.pdf

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.prempoddar.oaf.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PdfEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val fonts = FontSource { family ->
        context.resources.openRawResource(when (family) { FontFamilyChoice.SERIF -> R.font.literata; FontFamilyChoice.MONO -> R.font.jetbrains_mono; else -> R.font.inter })
    }

    @Before fun setUp() = PDFBoxResourceLoader.init(context)

    private fun text(cs: PDPageContentStream, x: Float, y: Float, s: String, size: Float = 24f) {
        cs.beginText(); cs.setFont(PDType1Font.HELVETICA, size); cs.newLineAtOffset(x, y); cs.showText(s); cs.endText()
    }

    /** A4 page 1: two lines and a filled square; page 2 is rotated and has one line. */
    private fun sample(): PDDocument {
        val doc = PDDocument()
        val p1 = PDPage(PDRectangle.A4)
        doc.addPage(p1)
        PDPageContentStream(doc, p1).use { cs ->
            text(cs, 72f, 700f, "Hello World")
            text(cs, 72f, 650f, "Second line")
            cs.setNonStrokingColor(1f, 0f, 0f)
            cs.addRect(300f, 300f, 100f, 50f); cs.fill()
            // Two pieces of one line: deleting the first must not move the second.
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(72f, 500f); cs.showText("AAAA "); cs.setFont(PDType1Font.HELVETICA_BOLD, 12f); cs.showText("BBBB"); cs.endText()
        }
        val p2 = PDPage(PDRectangle.A4).apply { rotation = 90 }
        doc.addPage(p2)
        PDPageContentStream(doc, p2).use { cs -> text(cs, 100f, 400f, "Turned", 18f) }
        return doc
    }

    private fun saved(doc: PDDocument): File = tmp.newFile().also { doc.save(it); doc.close() }

    @Test fun geometryRoundTripsForEveryRotation() {
        for (rot in listOf(0, 90, 180, 270)) {
            val page = PDPage(PDRectangle(0f, 0f, 600f, 800f)).apply { rotation = rot }
            val g = PageGeom(page)
            val (nx, ny) = g.toDisplay(150f, 600f)
            val (ux, uy) = g.toUser(nx, ny)
            assertThat(abs(ux - 150f)).isLessThan(0.01f)
            assertThat(abs(uy - 600f)).isLessThan(0.01f)
        }
        // Top-left of an unrotated page.
        val g = PageGeom(PDPage(PDRectangle(0f, 0f, 600f, 800f)))
        assertThat(g.toDisplay(0f, 800f)).isEqualTo(0f to 0f)
    }

    @Test fun textComesWithPositions() {
        val doc = sample()
        val t = PdfText.page(doc, 0)
        assertThat(t.text).contains("Hello World")
        val h = t.boxes[t.text.indexOf("Hello")]!!
        assertThat(h.l).isWithin(0.01f).of(72f / 595f)
        assertThat(h.b).isWithin(0.02f).of((842f - 700f) / 842f)
        assertThat(t.find("world")).hasSize(1)
        val i = t.charAt(h.cx, h.cy)!!
        assertThat(t.text.substring(t.wordAt(i).first, t.wordAt(i).last + 1)).isEqualTo("Hello")
        assertThat(t.lineBoxes(t.find("Hello World").single())).hasSize(1)
        // Rotated pages read upright.
        assertThat(PdfText.page(doc, 1).text).contains("Turned")
    }

    @Test fun elementsAreFoundAndDeleted() {
        val doc = sample()
        val els = PdfContent.elements(doc, 0)
        assertThat(els.filter { it.kind == PdfElement.Kind.TEXT }.map { it.text }).containsExactly("Hello World", "Second line", "AAAA BBBB").inOrder()
        val square = els.single { it.kind == PdfElement.Kind.SHAPE }
        assertThat(square.box.l).isWithin(0.01f).of(300f / 595f)
        val hello = els.first { it.text == "Hello World" }
        assertThat(hello.fontSize).isWithin(0.01f).of(24f)
        assertThat(PdfContent.at(els, hello.box.cx, hello.box.cy)).isSameInstanceAs(hello)

        PdfContent.delete(doc, 0, listOf(hello, square))
        val after = PdfContent.elements(doc, 0)
        assertThat(after.map { it.text }).doesNotContain("Hello World")
        assertThat(after.none { it.kind == PdfElement.Kind.SHAPE }).isTrue()
        assertThat(PdfText.page(doc, 0).text).contains("Second line")
    }

    @Test fun deletingPartOfALineLeavesTheRestInPlace() {
        val doc = sample()
        val before = PdfText.page(doc, 0).let { t -> t.boxes[t.text.indexOf("BBBB")]!! }
        val line = PdfContent.elements(doc, 0).first { it.text.startsWith("AAAA") }
        val first = PdfElement(PdfElement.Kind.TEXT, listOf(line.ops.first()), line.box, "AAAA ", line.info)
        PdfContent.delete(doc, 0, listOf(first))
        val t = PdfText.page(doc, 0)
        assertThat(t.text).doesNotContain("AAAA")
        val after = t.boxes[t.text.indexOf("BBBB")]!!
        assertThat(after.l).isWithin(0.002f).of(before.l)
    }

    @Test fun replacingTextKeepsOrSubstitutesTheFont() {
        val doc = sample()
        val hello = PdfContent.elements(doc, 0).first { it.text == "Hello World" }
        PdfContent.replaceText(doc, 0, hello, "Hello Mars", fonts)
        val reread = PDDocument.load(saved(doc))
        val t = PdfText.page(reread, 0)
        assertThat(t.text).contains("Hello Mars")
        assertThat(t.text).doesNotContain("World")
        val box = t.boxes[t.text.indexOf("Mars")]!!
        assertThat(box.b).isWithin(0.02f).of((842f - 700f) / 842f)
        // Greek isn't in Helvetica's encoding: a substitute font is embedded.
        val second = PdfContent.elements(reread, 0).first { it.text == "Second line" }
        PdfContent.replaceText(reread, 0, second, "Ωmega line", fonts)
        assertThat(PdfText.page(PDDocument.load(saved(reread)), 0).text).contains("Ωmega line")
    }

    @Test fun addingTextAndPicturesOnRotatedPages() {
        val doc = sample()
        PdfContent.addText(doc, 1, 0.1f, 0.1f, "Added note", 14f, 0x2F5D8C, FontFamilyChoice.SANS, fonts)
        val t = PdfText.page(PDDocument.load(saved(doc)), 1)
        assertThat(t.text).contains("Added note")
        val a = t.boxes[t.text.indexOf("Added")]!!
        assertThat(a.l).isWithin(0.02f).of(0.1f)
        assertThat(a.t).isWithin(0.03f).of(0.1f)
    }

    @Test fun annotationsAreAddedListedAndRemoved() {
        val doc = sample()
        val t = PdfText.page(doc, 0)
        val lines = t.lineBoxes(t.find("Hello").single())
        PdfAnnots.markup(doc, 0, PdfAnnots.Markup.HIGHLIGHT, lines, 0xFFEB3B)
        PdfAnnots.ink(doc, 0, listOf(listOf(0.1f to 0.5f, 0.2f to 0.55f, 0.3f to 0.5f)), 0x2F5D8C, 2f)
        PdfAnnots.note(doc, 0, 0.8f, 0.1f, "Check this")
        PdfAnnots.shape(doc, 0, PdfAnnots.Shape.ARROW, 0.2f to 0.8f, 0.6f to 0.9f, 0xC00000, 2f)
        val reread = PDDocument.load(saved(doc))
        val list = PdfAnnots.list(reread, 0)
        assertThat(list.map { it.subtype }).containsExactly("Highlight", "Ink", "Text", "Line").inOrder()
        assertThat(list[0].box.intersects(lines.single())).isTrue()
        assertThat(list[2].contents).isEqualTo("Check this")
        assertThat(reread.getPage(0).annotations.all { it.normalAppearanceStream != null }).isTrue()
        PdfAnnots.delete(reread, 0, list[1].index)
        assertThat(PdfAnnots.list(reread, 0).map { it.subtype }).containsExactly("Highlight", "Text", "Line").inOrder()
    }

    @Test fun formsAreReadAndFilled() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val form = PDAcroForm(doc)
        doc.documentCatalog.acroForm = form
        form.defaultResources = PDResources().apply { put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA) }
        form.defaultAppearance = "/Helv 0 Tf 0 g"
        val field = PDTextField(form).apply { partialName = "name" }
        val widget: PDAnnotationWidget = field.widgets.first()
        widget.rectangle = PDRectangle(100f, 700f, 200f, 20f)
        widget.page = page
        page.annotations.add(widget)
        form.fields.add(field)

        val fields = PdfForms.fields(doc)
        assertThat(fields.map { it.name to it.kind }).containsExactly("name" to PdfField.Kind.TEXT)
        assertThat(fields.single().box.l).isWithin(0.01f).of(100f / 595f)
        PdfForms.setText(doc, "name", "Prem")
        assertThat(PdfForms.fields(PDDocument.load(saved(doc))).single().value).isEqualTo("Prem")
    }

    @Test fun pageToolsAndOutline() {
        val doc = sample()
        val outline = PDDocumentOutline()
        doc.documentCatalog.documentOutline = outline
        outline.addLast(PDOutlineItem().apply { title = "Part two"; setDestination(doc.getPage(1)) })
        assertThat(PdfPages.outline(doc).map { it.title to it.page }).containsExactly("Part two" to 1)

        PdfPages.insertBlank(doc, 0)
        assertThat(doc.numberOfPages).isEqualTo(3)
        PdfPages.move(doc, 2, 0)
        assertThat(PdfText.page(doc, 0).text).contains("Turned")
        PdfPages.rotate(doc, listOf(0), 90)
        assertThat(doc.getPage(0).rotation).isEqualTo(180)
        PdfPages.duplicate(doc, 1)
        assertThat(doc.numberOfPages).isEqualTo(4)
        PdfPages.delete(doc, listOf(2, 3))
        assertThat(doc.numberOfPages).isEqualTo(2)
        assertThrows(IllegalArgumentException::class.java) { PdfPages.delete(doc, listOf(0, 1)) }
        val out = tmp.newFile("part.pdf")
        PdfPages.extract(doc, listOf(1), out)
        assertThat(PDDocument.load(out).use { it.numberOfPages }).isEqualTo(1)
        PdfPages.append(doc, out).close()
        assertThat(doc.numberOfPages).isEqualTo(3)
    }

    @Test fun sessionsUndoAndKeepPasswords() {
        val src = saved(sample())
        val s = PdfSession.open(src, tmp.newFolder())
        assertThat(s.encrypted).isFalse()
        s.mutate { PdfPages.delete(it, listOf(1)) }
        assertThat(s.pageCount).isEqualTo(1)
        s.undo()
        assertThat(s.pageCount).isEqualTo(2)
        s.redo()
        assertThat(s.pageCount).isEqualTo(1)
        s.close()

        // A protected file needs its password, and is still protected after saving.
        val locked = tmp.newFile("locked.pdf")
        sample().use { d ->
            d.protect(StandardProtectionPolicy("owner", "secret", AccessPermission()).apply { encryptionKeyLength = 128 })
            d.save(locked)
        }
        assertThrows(PdfSession.PasswordNeeded::class.java) { PdfSession.open(locked, tmp.newFolder()) }
        val ls = PdfSession.open(locked, tmp.newFolder(), "secret")
        assertThat(ls.encrypted).isTrue()
        ls.mutate { PdfAnnots.note(it, 0, 0.5f, 0.5f, "hi") }
        val out = tmp.newFile("out.pdf")
        out.outputStream().use { ls.saveTo(it) }
        ls.close()
        assertThrows(Exception::class.java) { PDDocument.load(out).close() }
        PDDocument.load(out, "secret").use { assertThat(PdfAnnots.list(it, 0).map { a -> a.contents }).contains("hi") }
    }
}

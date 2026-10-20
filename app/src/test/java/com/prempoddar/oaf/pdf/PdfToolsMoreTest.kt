package com.prempoddar.oaf.pdf

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfToolsMoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun fonts() = PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext<Application>())

    private fun pages(n: Int) = PDDocument().apply { repeat(n) { addPage(PDPage(PDRectangle.A4)) } }

    @Test fun splitMakesOneFilePerPage() {
        pages(3).use { doc ->
            val files = PdfPages.split(doc, tmp.root, "Notes")
            assertThat(files.map { it.name }).containsExactly("Notes - page 1.pdf", "Notes - page 2.pdf", "Notes - page 3.pdf").inOrder()
            files.forEach { f -> PDDocument.load(f).use { assertThat(it.numberOfPages).isEqualTo(1) } }
        }
    }

    @Test fun watermarkIsWrittenOnEveryPage() {
        val out = tmp.newFile("w.pdf")
        pages(2).use { doc -> PdfPages.watermark(doc, "CONFIDENTIAL"); doc.save(out) }
        // PDFBox's text reader skips diagonal text, so look at what each page draws instead.
        PDDocument.load(out).use { doc ->
            doc.pages.forEach { page ->
                val drawn = com.tom_roush.pdfbox.pdfparser.PDFStreamParser(page).apply { parse() }.tokens
                    .filterIsInstance<com.tom_roush.pdfbox.cos.COSString>().map { it.string }
                assertThat(drawn).contains("CONFIDENTIAL")
            }
        }
    }

    @Test fun compressShrinksABigPicture() {
        val before = tmp.newFile("big.pdf"); val after = tmp.newFile("small.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4); doc.addPage(page)
            // A noisy 2400×2400 picture stored losslessly: the kind a phone camera puts in a PDF.
            val bmp = Bitmap.createBitmap(2400, 2400, Bitmap.Config.ARGB_8888)
            val rnd = java.util.Random(1)
            val px = IntArray(2400 * 2400) { 0xFF000000.toInt() or (rnd.nextInt() and 0x3F3F3F) or 0x808080 }
            bmp.setPixels(px, 0, 2400, 0, 0, 2400, 2400)
            bmp.setHasAlpha(false)
            val image = LosslessFactory.createFromImage(doc, bmp)
            PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, 595f, 595f) }
            doc.save(before)
            assertThat(PdfPages.compressImages(doc)).isEqualTo(1)
            doc.save(after)
        }
        assertThat(after.length()).isLessThan(before.length() / 2)
    }
}

package com.prempoddar.oaf.pdf

import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File

/** A form field's box on one page. Radio groups have one per button, each with its own [onValue]. */
class PdfField(
    val name: String, val kind: Kind, val page: Int, val box: Box, val value: String, val readOnly: Boolean,
    val options: List<String> = emptyList(), val multiline: Boolean = false, val maxLength: Int = -1, val onValue: String? = null,
) {
    enum class Kind { TEXT, CHECK, RADIO, CHOICE, SIGNATURE, BUTTON }
    val checked get() = onValue != null && value == onValue
}

object PdfForms {
    fun fields(doc: PDDocument): List<PdfField> {
        val form = doc.documentCatalog.acroForm ?: return emptyList()
        val out = ArrayList<PdfField>()
        for (f in form.fieldTree) {
            val kind = when (f) {
                is PDTextField -> PdfField.Kind.TEXT
                is PDCheckBox -> PdfField.Kind.CHECK
                is PDRadioButton -> PdfField.Kind.RADIO
                is PDChoice -> PdfField.Kind.CHOICE
                is PDSignatureField -> PdfField.Kind.SIGNATURE
                is PDPushButton -> PdfField.Kind.BUTTON
                else -> continue
            }
            val value = runCatching { f.valueAsString }.getOrDefault("")
            f.widgets.forEach { w ->
                val page = w.page?.let { doc.pages.indexOf(it) }?.takeIf { it >= 0 } ?: pageOfWidget(doc, w.cosObject) ?: return@forEach
                val r = w.rectangle ?: return@forEach
                val box = PageGeom(doc.getPage(page)).boxOf(r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.upperRightY)
                // A check box or radio button's "on" name is the appearance state other than Off.
                val on = runCatching { w.appearance?.normalAppearance?.subDictionary?.keys?.map { it.name }?.firstOrNull { it != "Off" } }.getOrNull()
                out += PdfField(
                    f.fullyQualifiedName, kind, page, box, value, f.isReadOnly,
                    options = (f as? PDChoice)?.optionsDisplayValues.orEmpty(),
                    multiline = (f as? PDTextField)?.isMultiline == true,
                    maxLength = (f as? PDTextField)?.maxLen ?: -1,
                    onValue = if (kind == PdfField.Kind.CHECK || kind == PdfField.Kind.RADIO) on else null,
                )
            }
        }
        return out
    }

    private fun pageOfWidget(doc: PDDocument, widget: Any): Int? = doc.pages.indexOfFirst { p -> p.annotations.any { it.cosObject === widget } }.takeIf { it >= 0 }

    private fun field(doc: PDDocument, name: String): PDField = doc.documentCatalog.acroForm?.getField(name) ?: error("No field $name")

    /** Sets a text or choice field. Appearances are regenerated; if the form's fonts don't allow that, readers are asked to draw them. */
    fun setText(doc: PDDocument, name: String, value: String) {
        val f = field(doc, name)
        try {
            f.setValue(value)
        } catch (e: Exception) {
            doc.documentCatalog.acroForm.needAppearances = true
            f.cosObject.setString(com.tom_roush.pdfbox.cos.COSName.V, value)
        }
    }

    fun setChecked(doc: PDDocument, name: String, onValue: String?, checked: Boolean) {
        when (val f = field(doc, name)) {
            is PDCheckBox -> if (checked) f.check() else f.unCheck()
            is PDRadioButton -> if (checked && onValue != null) f.setValue(onValue) else f.setValue("Off")
            else -> Unit
        }
    }

    /** Turns the form into ordinary page content, so it can't be changed any more. */
    fun flatten(doc: PDDocument) {
        val form = doc.documentCatalog.acroForm ?: return
        runCatching { form.refreshAppearances() }
        form.flatten()
    }

    fun hasXfa(doc: PDDocument) = doc.documentCatalog.acroForm?.hasXFA() == true
}

class OutlineItem(val title: String, val level: Int, val page: Int?)

object PdfPages {
    fun outline(doc: PDDocument): List<OutlineItem> {
        val root = doc.documentCatalog.documentOutline ?: return emptyList()
        val out = ArrayList<OutlineItem>()
        fun walk(node: PDOutlineNode, level: Int) {
            var item = node.firstChild
            var guard = 0
            while (item != null && guard++ < 10_000) {
                val page = runCatching { item.findDestinationPage(doc) }.getOrNull()?.let { p -> doc.pages.indexOf(p).takeIf { it >= 0 } }
                out += OutlineItem(item.title.orEmpty().trim(), level, page)
                if (level < 8) walk(item, level + 1)
                item = item.nextSibling
            }
        }
        walk(root, 0)
        return out
    }

    fun rotate(doc: PDDocument, pages: Collection<Int>, degrees: Int) = pages.forEach { i ->
        val p = doc.getPage(i)
        p.rotation = ((p.rotation + degrees) % 360 + 360) % 360
    }

    fun delete(doc: PDDocument, pages: Collection<Int>) {
        require(pages.size < doc.numberOfPages) { "A PDF needs at least one page" }
        pages.sortedDescending().forEach { doc.removePage(it) }
    }

    /** Moves page [from] so it ends up at position [to]. */
    fun move(doc: PDDocument, from: Int, to: Int) {
        if (from == to) return
        val tree = doc.pages
        val page = tree.get(from)
        tree.remove(from)
        if (to >= tree.count) tree.add(page) else tree.insertBefore(page, tree.get(to))
    }

    /** A blank page after [after] (-1 for the start), the size of its neighbour. */
    fun insertBlank(doc: PDDocument, after: Int) {
        val ref = doc.getPage(after.coerceIn(0, doc.numberOfPages - 1))
        val page = PDPage(PDRectangle(ref.mediaBox.width, ref.mediaBox.height))
        if (after < 0) doc.pages.insertBefore(page, doc.getPage(0)) else if (after >= doc.numberOfPages - 1) doc.addPage(page) else doc.pages.insertAfter(page, doc.getPage(after))
    }

    fun duplicate(doc: PDDocument, index: Int) {
        val src = doc.getPage(index)
        val copy = PDPage(com.tom_roush.pdfbox.cos.COSDictionary(src.cosObject).apply {
            removeItem(com.tom_roush.pdfbox.cos.COSName.PARENT)
            // Annotations belong to one page each; the copy starts without them.
            removeItem(com.tom_roush.pdfbox.cos.COSName.ANNOTS)
        })
        doc.pages.insertAfter(copy, src)
    }

    /** Writes [pages] (in that order) to a new PDF. */
    fun extract(doc: PDDocument, pages: List<Int>, out: File) {
        PDDocument().use { target ->
            pages.forEach { i -> target.importPage(doc.getPage(i)).rotation = doc.getPage(i).rotation }
            target.save(out)
        }
    }

    /** One PDF per page in [dir], named "[base] - page N.pdf"; returns them in order. */
    fun split(doc: PDDocument, dir: File, base: String): List<File> = (0 until doc.numberOfPages).map { i ->
        File(dir, "$base - page ${i + 1}.pdf").also { extract(doc, listOf(i), it) }
    }

    /**
     * Makes the file smaller by shrinking pictures larger than [maxSide] pixels and storing them as
     * JPEG at [quality]; text and drawings are untouched. Returns how many pictures changed.
     */
    fun compressImages(doc: PDDocument, maxSide: Int = 1600, quality: Float = 0.7f): Int {
        var changed = 0
        doc.pages.forEach { page ->
            val res = page.resources ?: return@forEach
            res.xObjectNames.toList().forEach { name ->
                val image = res.getXObject(name) as? com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject ?: return@forEach
                // Small pictures and ones with see-through parts (which JPEG would lose) stay as they are.
                if (image.width * image.height < 300 * 300 || image.cosObject.containsKey(com.tom_roush.pdfbox.cos.COSName.SMASK)) return@forEach
                val bitmap = runCatching { image.image }.getOrNull() ?: return@forEach
                val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
                val small = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
                val jpeg = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, small, quality)
                // Only keep it if it really is smaller.
                if (encodedSize(jpeg.cosObject) < encodedSize(image.cosObject)) { res.put(name, jpeg); changed++ }
            }
        }
        return changed
    }

    private fun encodedSize(s: com.tom_roush.pdfbox.cos.COSStream): Long =
        runCatching { s.createRawInputStream().use { input -> var n = 0L; val b = ByteArray(65536); while (true) { val r = input.read(b); if (r < 0) break; n += r }; n } }.getOrDefault(Long.MAX_VALUE)

    /** [text] across every page, faint and diagonal, under whatever is drawn on top later. */
    fun watermark(doc: PDDocument, text: String, opacity: Float = 0.15f) {
        val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
        val state = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = opacity }
        doc.pages.forEach { page ->
            val box = page.cropBox
            val diagonal = kotlin.math.hypot(box.width, box.height)
            val size = (diagonal / (font.getStringWidth(text) / 1000f) * 0.7f).coerceIn(12f, 140f)
            val width = font.getStringWidth(text) / 1000f * size
            val angle = kotlin.math.atan2(box.height, box.width)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.setGraphicsStateParameters(state)
                cs.setNonStrokingColor(0.6f, 0.6f, 0.6f)
                cs.beginText()
                cs.setFont(font, size)
                // Centred on the page, along the diagonal.
                val cx = box.lowerLeftX + box.width / 2 - kotlin.math.cos(angle) * width / 2
                val cy = box.lowerLeftY + box.height / 2 - kotlin.math.sin(angle) * width / 2
                cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(angle.toDouble(), cx, cy))
                cs.showText(text)
                cs.endText()
            }
        }
    }

    /**
     * Adds every page of [other] at the end. The returned source must stay open until [doc] has
     * been saved, because the copied pages still read from it.
     */
    fun append(doc: PDDocument, other: File, password: String? = null): PDDocument {
        val src = PDDocument.load(other, password.orEmpty())
        src.isAllSecurityToBeRemoved = true
        PDFMergerUtility().appendDocument(doc, src)
        return src
    }
}

/** Pictures into a PDF: one per page, each page shaped to its picture and A4 wide. */
object ImagesToPdf {
    fun make(context: android.content.Context, uris: List<android.net.Uri>, out: File) {
        PDDocument().use { doc ->
            uris.forEach { uri ->
                val bitmap = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                    d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = maxOf(info.size.width, info.size.height) / 2400f
                    if (scale > 1) d.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                }
                val image = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, bitmap, 0.85f)
                val w = 595f; val h = w * bitmap.height / bitmap.width
                val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle(w, h))
                doc.addPage(page)
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, w, h) }
            }
            doc.save(out)
        }
    }
}

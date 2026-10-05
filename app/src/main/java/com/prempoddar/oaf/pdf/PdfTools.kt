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

package com.prempoddar.oaf.doc

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class DocxTest {
    private val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private val documentXml = """<?xml version="1.0" encoding="UTF-8"?>
<w:document xmlns:w="$w" xmlns:r="$r" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><w:body>
<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Report</w:t></w:r></w:p>
<w:p><w:bookmarkStart w:id="0" w:name="intro"/><w:r><w:t xml:space="preserve">Plain </w:t></w:r><w:r><w:rPr><w:rFonts w:ascii="Georgia" w:hAnsi="Georgia"/><w:b/></w:rPr><w:t>bold</w:t></w:r><w:r><w:t xml:space="preserve"> and </w:t></w:r><w:hyperlink r:id="rId9"><w:r><w:t>a link</w:t></w:r></w:hyperlink><w:r><w:tab/><w:t>end.</w:t></w:r><w:bookmarkEnd w:id="0"/></w:p>
<w:p><w:r><w:drawing><wp:inline><wp:extent cx="1270000" cy="635000"/><a:graphic><a:graphicData><a:blip r:embed="rId5"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>First</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Second</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Nested</w:t></w:r></w:p>
<w:tbl><w:tblGrid><w:gridCol w:w="3000"/><w:gridCol w:w="3000"/></w:tblGrid><w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:r><w:t>Last</w:t></w:r><w:del w:id="3"><w:r><w:delText>gone</w:delText></w:r></w:del></w:p>
<w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr>
</w:body></w:document>"""

    private val stylesXml = """<w:styles xmlns:w="$w"><w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="24"/></w:rPr></w:rPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style></w:styles>"""

    private val numberingXml = """<w:numbering xmlns:w="$w"><w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%1.%2)"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>"""

    private fun parts() = linkedMapOf(
        "[Content_Types].xml" to """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>""",
        "_rels/.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$r/officeDocument" Target="word/document.xml"/></Relationships>""",
        "word/_rels/document.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$r/styles" Target="styles.xml"/><Relationship Id="rId2" Type="$r/numbering" Target="numbering.xml"/><Relationship Id="rId5" Type="$r/image" Target="media/image1.png"/><Relationship Id="rId9" Type="$r/hyperlink" Target="https://example.org" TargetMode="External"/></Relationships>""",
        "word/document.xml" to documentXml,
        "word/styles.xml" to stylesXml,
        "word/numbering.xml" to numberingXml,
        "word/media/image1.png" to "PNG",
        "word/footer1.xml" to "<ftr/>",
    ).mapValuesTo(LinkedHashMap()) { it.value.toByteArray() }

    private fun save(doc: DocxDoc, body: DocBody): Pair<DocBody, Map<String, String>> {
        val out = ByteArrayOutputStream()
        val saved = Docx.write(doc, body, out)
        val files = LinkedHashMap<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; files[e.name] = z.readBytes().decodeToString() } }
        return saved to files
    }

    private fun reread(files: Map<String, String>) = Docx.read(files.mapValuesTo(LinkedHashMap()) { it.value.toByteArray() })

    @Test fun readsStructureStylesAndLists() {
        val doc = Docx.read(parts())
        val paras = doc.body.paragraphs
        assertThat(paras.map { it.text.replace(Para.OBJ.toString(), "[obj]") }).containsExactly(
            "Report", "Plain bold and a link\tend.", "[obj]", "First", "Second", "Nested", "A", "B", "Last",
        ).inOrder()
        assertThat(doc.styles.headingLevel(paras[0].props.styleId)).isEqualTo(1)
        assertThat(doc.styles.effective(paras[0], paras[0].runs[0])).isEqualTo(RunStyle(bold = true, size = 16f))
        val body = paras[1]
        assertThat(body.runs.first { it.text == "bold" }.style).isEqualTo(RunStyle(bold = true, font = "Georgia"))
        assertThat(body.runs.first { it.text == "a link" }.linkTarget).isEqualTo("https://example.org")
        assertThat(doc.styles.baseSize).isEqualTo(12f)
        val image = paras[2].runs.single()
        assertThat(image.image).isEqualTo("word/media/image1.png")
        assertThat(image.imageSize).isEqualTo(100f to 50f)
        assertThat(doc.numbering.labels(paras, doc.styles).values.toList()).containsExactly("1.", "2.", "2.a)").inOrder()
        val table = doc.body.blocks.filterIsInstance<Block.T>().single()
        assertThat(table.widths).containsExactly(3000, 3000)
    }

    @Test fun editingRunsKeepsFormattingWhereItWas() {
        val doc = Docx.read(parts())
        val p = doc.body.paragraphs[1]
        // Typing after "bold" continues in bold; typing at the start takes the first run's look.
        val typed = p.edit("Plain boldest and a link\tend.")
        assertThat(typed.runs.first { it.text.startsWith("bold") }.text).isEqualTo("boldest")
        val start = p.edit("Very Plain bold and a link\tend.")
        assertThat(start.runs.first().text).isEqualTo("Very Plain ")
        // Typing inside the link extends it.
        val linked = p.edit("Plain bold and a lovely link\tend.")
        assertThat(linked.runs.first { it.link != null }.text).isEqualTo("a lovely link")
        // Deleting across runs.
        val cut = p.edit("Plain link\tend.")
        assertThat(cut.text).isEqualTo("Plain link\tend.")
        assertThat(cut.runs.first { it.link != null }.text).isEqualTo("link")
        // Formatting a range splits runs.
        val italic = p.format(0, 5) { it.copy(italic = true) }
        assertThat(italic.runs.first().text).isEqualTo("Plain")
        assertThat(italic.runs.first().style.italic).isTrue()
        val (a, b) = p.split(6)
        assertThat(a.text).isEqualTo("Plain ")
        assertThat(b.text).isEqualTo("bold and a link\tend.")
        assertThat(a.merge(b).text).isEqualTo(p.text)
    }

    @Test fun savingRebuildsOnlyWhatChangedAndKeepsTheRest() {
        val doc = Docx.read(parts())
        val paras = doc.body.paragraphs
        var body = doc.body.update(paras[1].id) { it.edit("Plain bold and a link\tthe end.").format(0, 5) { s -> s.copy(italic = true) } }
        val (a, b) = paras[8].split(2)
        body = body.replace(paras[8].id, listOf(a, b.copy(props = ParaProps(styleId = "Heading2"))))
        body = body.update(paras[3].id) { it.copy(props = it.props.copy(numId = Numbering.BULLET), dirty = true) }
        val (_, files) = save(doc, body)
        val xml = files.getValue("word/document.xml")
        assertThat(xml).contains("<w:drawing>")
        assertThat(xml).contains("w:name=\"intro\"")
        assertThat(xml).contains("<w:hyperlink")
        assertThat(xml).contains("Georgia")
        assertThat(xml).doesNotContain("gone") // the edited paragraph's deletion is accepted
        assertThat(xml.indexOf("<w:sectPr")).isGreaterThan(xml.lastIndexOf("<w:p>").coerceAtLeast(xml.lastIndexOf("<w:p ")))
        assertThat(files.getValue("word/footer1.xml")).isEqualTo("<ftr/>")
        assertThat(files.getValue("word/styles.xml")).contains("w:styleId=\"Heading2\"")
        assertThat(files.getValue("word/numbering.xml")).contains("w:numId=\"2\"")

        val again = reread(files)
        val texts = again.body.paragraphs.map { it.text }
        assertThat(texts).contains("Plain bold and a link\tthe end.")
        assertThat(texts.takeLast(2)).containsExactly("La", "st").inOrder()
        val edited = again.body.paragraphs[1]
        assertThat(edited.runs.first().text).isEqualTo("Plain")
        assertThat(edited.runs.first().style.italic).isTrue()
        assertThat(again.styles.headingLevel(again.body.paragraphs.last().props.styleId)).isEqualTo(2)
        assertThat(again.numbering.isBullet(again.body.paragraphs[3].props.numId)).isTrue()
    }

    @Test fun undoingAfterASaveSavesTheOlderVersion() {
        val doc = Docx.read(parts())
        val original = doc.body
        val p = original.paragraphs.last()
        val (a, b) = p.split(2)
        val edited = original.replace(p.id, listOf(a, b))
        val (saved, files1) = save(doc, edited)
        assertThat(reread(files1).body.paragraphs.takeLast(2).map { it.text }).containsExactly("La", "st").inOrder()
        // Save again unchanged: nothing duplicates.
        val (_, files2) = save(doc, saved)
        assertThat(reread(files2).body.paragraphs.map { it.text }.count { it == "st" }).isEqualTo(1)
        // Back to the state before the split.
        val (_, files3) = save(doc, original)
        val texts = reread(files3).body.paragraphs.map { it.text }
        assertThat(texts.last()).isEqualTo("Last")
        assertThat(texts).doesNotContain("st")
        // And forward again.
        val (_, files4) = save(doc, edited)
        assertThat(reread(files4).body.paragraphs.takeLast(2).map { it.text }).containsExactly("La", "st").inOrder()
    }

    @Test fun blankDocumentsTakeParagraphsAndTables() {
        val doc = Docx.blank()
        val x = Cell(null, listOf(Para(newId(), listOf(Run("x")))))
        val y = Cell(null, listOf(Para(newId(), listOf(Run("y", RunStyle(bold = true))))))
        val body = DocBody(listOf(
            Block.P(Para(newId(), listOf(Run("Title")), ParaProps(styleId = "Title"))),
            Block.T(null, listOf(listOf(x, y)), emptyList()),
            Block.P(Para(newId(), listOf(Run("item")), ParaProps(numId = Numbering.DECIMAL))),
        ))
        val (_, files) = save(doc, body)
        val again = reread(files)
        assertThat(again.body.paragraphs.map { it.text }).containsExactly("Title", "x", "y", "item").inOrder()
        assertThat(again.body.paragraphs[2].runs.single().style.bold).isTrue()
        assertThat(again.numbering.labels(again.body.paragraphs, again.styles)).containsExactly(again.body.paragraphs[3].id, "1.")
        assertThat(files.getValue("[Content_Types].xml")).contains("numbering")
    }

    @Test fun rowsAddedAndDeletedInATableFromTheFile() {
        val doc = Docx.read(parts())
        val e = DocEditor(doc.body)
        val a = doc.body.paragraphs.first { it.text == "A" }
        val newRow = e.addRow(a.id, below = true)!!
        e.change(newRow, "C")
        val (_, files) = save(doc, e.body)
        val xml = files.getValue("word/document.xml")
        assertThat(Regex("<w:tr>").findAll(xml).count()).isEqualTo(2)
        val doc2 = reread(files)
        val table = doc2.body.blocks.filterIsInstance<Block.T>().single()
        assertThat(table.rows.map { row -> row.map { c -> c.paras.joinToString { it.text } } }).containsExactly(listOf("A", "B"), listOf("C", "")).inOrder()

        val e2 = DocEditor(doc2.body)
        e2.deleteRow(doc2.body.paragraphs.first { it.text == "A" }.id)
        val (_, files2) = save(doc2, e2.body)
        assertThat(Regex("<w:tr>").findAll(files2.getValue("word/document.xml")).count()).isEqualTo(1)
        val rows = reread(files2).body.blocks.filterIsInstance<Block.T>().single().rows
        assertThat(rows.map { row -> row.map { c -> c.paras.joinToString { it.text } } }).containsExactly(listOf("C", ""))
    }

    @Test fun aNewTableIsWritten() {
        val doc = Docx.read(parts())
        val e = DocEditor(doc.body)
        val cell = e.insertTable(doc.body.paragraphs.first { it.text == "Report" }.id, 1, 2)!!
        e.change(cell, "New cell")
        val (_, files) = save(doc, e.body)
        val tables = reread(files).body.blocks.filterIsInstance<Block.T>()
        assertThat(tables).hasSize(2)
        assertThat(tables[0].rows[0][0].paras[0].text).isEqualTo("New cell")
    }
}

package com.prempoddar.oaf.sheet

import com.google.common.truth.Truth.assertThat
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SheetIoTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun Sheet.input(ref: String) = A1.parse(ref)!!.let { (r, c) -> this[r, c]?.input }
    private fun Sheet.value(ref: String) = A1.parse(ref)!!.let { (r, c) -> this[r, c]?.value?.display() }
    private fun Sheet.put(ref: String, input: String, style: CellStyle? = null) = A1.parse(ref)!!.let { (r, c) -> set(r, c, input, style) }

    @Test fun refsShiftLikeExcel() {
        assertThat(Refs.shift("=A1+\$B\$2+C\$3+\$D4", 2, 1)).isEqualTo("=B3+\$B\$2+D\$3+\$D6")
        assertThat(Refs.shift("=SUM(A1:B2)&\"A1\"", 1, 0)).isEqualTo("=SUM(A2:B3)&\"A1\"")
        assertThat(Refs.shift("=A1", -1, 0)).isEqualTo("=#REF!")
        assertThat(Refs.shift("=Data!A1+'My Sheet'!B2", 1, 1)).isEqualTo("=Data!B2+'My Sheet'!C3")
        assertThat(Refs.shift("=LOG10(A1)", 1, 0)).isEqualTo("=LOG10(A2)")
    }

    @Test fun insertingAndDeletingRowsFixesFormulas() {
        val wb = Workbook(mutableListOf(Sheet("S"), Sheet("T")))
        val s = wb.sheets[0]
        s.put("A1", "1"); s.put("A2", "2"); s.put("A3", "3"); s.put("B1", "=SUM(A1:A3)"); s.put("B2", "=A3*2")
        wb.sheets[1].put("A1", "=S!A3")
        wb.shiftRows(s, 1, 1) // insert above row 2
        assertThat(s.input("B1")).isEqualTo("=SUM(A1:A4)")
        assertThat(s.input("B3")).isEqualTo("=A4*2")
        assertThat(wb.sheets[1].input("A1")).isEqualTo("=S!A4")
        wb.shiftRows(s, 3, -1) // delete row 4 (old A3)
        assertThat(s.input("B1")).isEqualTo("=SUM(A1:A3)")
        assertThat(s.input("B3")).isEqualTo("=#REF!*2")
        wb.recalculate()
        assertThat(s.value("B1")).isEqualTo("3")
    }

    @Test fun xlsxRoundTripsFromScratch() {
        val wb = Workbook(mutableListOf(Sheet("Budget"), Sheet("Notes")))
        val s = wb.sheets[0]
        s.put("A1", "Item", CellStyle(bold = true, fill = 0xFFFFEE00.toInt()))
        s.put("B1", "Cost", CellStyle(bold = true, align = CellStyle.Align.RIGHT))
        s.put("A2", "Rent"); s.put("B2", "1200.5", CellStyle(numFmt = "#,##0.00"))
        s.put("A3", "Food"); s.put("B3", "300")
        s.put("A4", "'007"); s.put("B4", "=SUM(B2:B3)"); s.put("C4", "=XLOOKUP(\"Food\",A2:A3,B2:B3)")
        s.put("A5", " padded & <tagged> ")
        s.put("A6", "TRUE")
        s.colWidths[0] = 20f; s.frozenRows = 1; s.merges += CellRange(6, 0, 6, 1)
        wb.sheets[1].put("A1", "=Budget!B4/2")
        wb.recalculate()
        val f = tmp.newFile("a.xlsx")
        f.outputStream().use { Xlsx.write(XlsxDoc(wb, emptyList()), null, it) }

        val back = Xlsx.read(f).workbook
        val b = back.sheets[0]
        assertThat(back.sheets.map { it.name }).containsExactly("Budget", "Notes").inOrder()
        assertThat(b.input("A1")).isEqualTo("Item")
        assertThat(b["A1".let { 0 }, 0]!!.style).isEqualTo(CellStyle(bold = true, fill = 0xFFFFEE00.toInt()))
        assertThat(b[0, 1]!!.style.align).isEqualTo(CellStyle.Align.RIGHT)
        assertThat(b[1, 1]!!.style.numFmt).isEqualTo("#,##0.00")
        assertThat(b.input("A4")).isEqualTo("'007")
        assertThat(b.input("B4")).isEqualTo("=SUM(B2:B3)")
        assertThat(b.value("B4")).isEqualTo("1500.5")
        assertThat(b.input("C4")).isEqualTo("=XLOOKUP(\"Food\",A2:A3,B2:B3)")
        assertThat(b.input("A5")).isEqualTo(" padded & <tagged> ")
        assertThat(b.value("A6")).isEqualTo("TRUE")
        assertThat(b.colWidths[0]).isEqualTo(20f)
        assertThat(b.frozenRows).isEqualTo(1)
        assertThat(b.merges).containsExactly(CellRange(6, 0, 6, 1))
        assertThat(back.sheets[1].value("A1")).isEqualTo("750.25")
        ZipFile(f).use { z ->
            val sheetXml = z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertThat(sheetXml).contains("_xlfn.XLOOKUP(")
        }
    }

    @Test fun xlsxSaveKeepsWhatItDoesNotModel() {
        // A first save, then a drawing and a calc chain are added as if by Excel.
        val wb = Workbook()
        wb.sheets[0].put("A1", "5")
        val base = tmp.newFile("base.xlsx")
        base.outputStream().use { Xlsx.write(XlsxDoc(wb, emptyList()), null, it) }
        val rich = tmp.newFile("rich.xlsx")
        ZipFile(base).use { z ->
            ZipOutputStream(rich.outputStream()).use { out ->
                z.entries().asSequence().forEach { e ->
                    var bytes = z.getInputStream(e).readBytes()
                    if (e.name == "xl/_rels/workbook.xml.rels") bytes = String(bytes).replace("</Relationships>", "<Relationship Id=\"rId99\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/calcChain\" Target=\"calcChain.xml\"/></Relationships>").toByteArray()
                    if (e.name == "xl/worksheets/sheet1.xml") bytes = String(bytes).replace("</worksheet>", "<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/><drawing r:id=\"rId1\"/></worksheet>").toByteArray()
                    out.putNextEntry(ZipEntry(e.name)); out.write(bytes); out.closeEntry()
                }
                out.putNextEntry(ZipEntry("xl/drawings/drawing1.xml")); out.write("<chart/>".toByteArray()); out.closeEntry()
                out.putNextEntry(ZipEntry("xl/calcChain.xml")); out.write("<calcChain/>".toByteArray()); out.closeEntry()
            }
        }
        val doc = Xlsx.read(rich)
        val s = doc.workbook.sheets[0]
        s.put("A2", "=A1*2"); s.put("B1", "hello", CellStyle(italic = true)); s.frozenRows = 1
        doc.workbook.sheets += Sheet("Added").also { it.put("A1", "x") }
        doc.workbook.recalculate()
        val out = tmp.newFile("out.xlsx")
        out.outputStream().use { Xlsx.write(doc, rich, it) }

        ZipFile(out).use { z ->
            assertThat(z.getEntry("xl/drawings/drawing1.xml")).isNotNull()
            assertThat(z.getEntry("xl/calcChain.xml")).isNull()
            val sheetXml = z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertThat(sheetXml).contains("<drawing")
            // The schema order puts the pane before data and data before the margins.
            assertThat(sheetXml.indexOf("<pane")).isLessThan(sheetXml.indexOf("<sheetData"))
            assertThat(sheetXml.indexOf("<sheetData")).isLessThan(sheetXml.indexOf("<pageMargins"))
            val rels = z.getInputStream(z.getEntry("xl/_rels/workbook.xml.rels")).bufferedReader().readText()
            assertThat(rels).doesNotContain("calcChain")
            assertThat(z.getInputStream(z.getEntry("xl/workbook.xml")).bufferedReader().readText()).contains("fullCalcOnLoad=\"1\"")
        }
        val again = Xlsx.read(out).workbook
        assertThat(again.sheets.map { it.name }).containsExactly("Sheet1", "Added").inOrder()
        assertThat(again.sheets[0].value("A2")).isEqualTo("10")
        assertThat(again.sheets[0][0, 1]!!.style.italic).isTrue()
        assertThat(again.sheets[1].value("A1")).isEqualTo("x")
    }

    @Test fun xlsxSharedFormulasExpand() {
        val sheetXml = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1"><v>1</v></c><c r="B1"><f t="shared" ref="B1:B3" si="0">A1*10</f><v>10</v></c></row>
            <row r="2"><c r="A2"><v>2</v></c><c r="B2"><f t="shared" si="0"/><v>20</v></c></row>
            <row r="3"><c r="A3" t="s"><v>0</v></c><c r="B3"><f t="shared" si="0"/></c><c r="C3"><f>MYSTERY(A1)</f><v>42</v></c></row>
            </sheetData></worksheet>"""
        val parts = mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="S" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="/xl/sharedStrings.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheetXml,
            "xl/sharedStrings.xml" to """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><r><t>rich </t></r><r><t>text</t></r><rPh><t>ignored</t></rPh></si></sst>""",
        )
        val s = Xlsx.read { parts[it]?.toByteArray() }.workbook.sheets[0]
        assertThat(s.input("B2")).isEqualTo("=A2*10")
        assertThat(s.input("B3")).isEqualTo("=A3*10")
        assertThat(s.value("B3")).isEqualTo("#VALUE!")
        assertThat(s.value("A3")).isEqualTo("rich text")
        // Unknown functions fall back to the value Excel saved.
        assertThat(s.value("C3")).isEqualTo("42")
    }

    @Test fun csvParsesAndWritesBack() {
        val text = "name;note;n\n\"Smith; J\";\"said \"\"hi\"\"\nthen left\";007\nLee;;=1+1\n"
        assertThat(Csv.detect(text)).isEqualTo(';')
        val rows = Csv.parse(text, ';')
        assertThat(rows[1]).containsExactly("Smith; J", "said \"hi\"\nthen left", "007").inOrder()
        val f = tmp.newFile("people.csv").apply { writeText(text) }
        val (wb, dialect) = Csv.read(f)
        val s = wb.sheets[0]
        assertThat(dialect.delimiter).isEqualTo(';')
        assertThat(s.value("C2")).isEqualTo("007")
        assertThat(s.value("C3")).isEqualTo("=1+1")
        assertThat(Csv.write(s, ';')).isEqualTo(text)
    }

    @Test fun csvDetectsTabsAndCommas() {
        assertThat(Csv.detect("a,b,c\n1,2,3\n")).isEqualTo(',')
        assertThat(Csv.detect("a\tb, c\n1\t2, 3\n")).isEqualTo('\t')
        assertThat(Csv.detect("single column\nvalues\n")).isEqualTo(',')
    }

    @Test fun odsFormulasConvert() {
        assertThat(Ods.convert("of:=SUM([.A1:.B2];[Sheet2.C3])")).isEqualTo("SUM(A1:B2,Sheet2!C3)")
        assertThat(Ods.convert("of:=[\$'My Sheet'.A1:.A3]&\";\"")).isEqualTo("'My Sheet'!A1:A3&\";\"")
    }

    @Test fun odsReads() {
        val content = """<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"><office:body><office:spreadsheet>
            <table:table table:name="Data">
              <table:table-row><table:table-cell office:value-type="float" office:value="2"><text:p>2</text:p></table:table-cell><table:table-cell table:number-columns-repeated="2" office:value-type="string"><text:p>x</text:p></table:table-cell><table:table-cell table:number-columns-repeated="16380"/></table:table-row>
              <table:table-row table:number-rows-repeated="2"><table:table-cell table:formula="of:=[.A1]*3" office:value-type="float" office:value="6"/></table:table-row>
              <table:table-row table:number-rows-repeated="1048570"><table:table-cell table:number-columns-repeated="16384"/></table:table-row>
            </table:table></office:spreadsheet></office:body></office:document-content>"""
        val f = tmp.newFile("a.ods")
        ZipOutputStream(f.outputStream()).use { z -> z.putNextEntry(ZipEntry("content.xml")); z.write(content.toByteArray()) }
        val s = Ods.read(f).sheets[0]
        assertThat(s.name).isEqualTo("Data")
        assertThat(s.value("C1")).isEqualTo("x")
        assertThat(s.value("A3")).isEqualTo("6")
        assertThat(s.cells.size).isEqualTo(5)
    }

    @Test fun xlsReads() {
        val f = tmp.newFile("old.xls")
        HSSFWorkbook().use { book ->
            val sh = book.createSheet("Old")
            val bold = book.createCellStyle().apply { setFont(book.createFont().apply { bold = true }) }
            sh.createRow(0).apply {
                createCell(0).apply { setCellValue("Total"); cellStyle = bold }
                createCell(1).setCellValue(4.0)
                createCell(2).cellFormula = "B1*2"
            }
            sh.createFreezePane(0, 1)
            f.outputStream().use { book.write(it) }
        }
        val s = Xls.read(f).sheets[0]
        assertThat(s.name).isEqualTo("Old")
        assertThat(s[0, 0]!!.style.bold).isTrue()
        assertThat(s.input("C1")).isEqualTo("=B1*2")
        assertThat(s.value("C1")).isEqualTo("8")
        assertThat(s.frozenRows).isEqualTo(1)
    }

    @Suppress("unused") private fun File.text() = readText()
}

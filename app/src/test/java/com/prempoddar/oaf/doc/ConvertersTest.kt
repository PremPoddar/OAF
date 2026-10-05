package com.prempoddar.oaf.doc

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ConvertersTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun rtfTextFormattingAndColours() {
        val rtf = """{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0 Times New Roman;}}{\colortbl;\red255\green0\blue0;}
{\*\generator Test;}\pard\qc\b Title\b0\par
\pard Plain {\i italic} and {\cf1 red} caf\'e9 ${'\\'}u8364?5\par
Line one\line line two\tab end\par
}"""
        val body = Converters.rtf(rtf)
        val paras = body.paragraphs
        assertThat(paras.map { it.text }).containsExactly("Title", "Plain italic and red café €5", "Line one line two\tend").inOrder()
        assertThat(paras[0].props.align).isEqualTo("center")
        assertThat(paras[0].runs.single().style.bold).isTrue()
        assertThat(paras[1].runs.first { it.text == "italic" }.style.italic).isTrue()
        assertThat(paras[1].runs.first { it.text == "red" }.style.color).isEqualTo(0xFF0000)
        assertThat(paras[1].props.align).isNull()
    }

    @Test fun odtHeadingsListsSpansAndTables() {
        val content = """<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:xlink="http://www.w3.org/1999/xlink">
<office:automatic-styles><style:style style:name="T1" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>
<style:style style:name="P1" style:family="paragraph" style:parent-style-name="Standard"><style:paragraph-properties fo:text-align="center"/></style:style>
<text:list-style style:name="L1"><text:list-level-style-number text:level="1"/></text:list-style></office:automatic-styles>
<office:body><office:text>
<text:h text:outline-level="2">Chapter</text:h>
<text:p text:style-name="P1">Hello <text:span text:style-name="T1">bold</text:span><text:s text:c="2"/>world <text:a xlink:href="https://example.org">link</text:a></text:p>
<text:list text:style-name="L1"><text:list-item><text:p>one</text:p><text:list><text:list-item><text:p>inner</text:p></text:list-item></text:list></text:list-item></text:list>
<table:table><table:table-row><table:table-cell><text:p>a</text:p></table:table-cell><table:table-cell><text:p>b</text:p></table:table-cell></table:table-row></table:table>
</office:text></office:body></office:document-content>"""
        val f = tmp.newFile("a.odt")
        ZipOutputStream(f.outputStream()).use { z -> z.putNextEntry(ZipEntry("content.xml")); z.write(content.toByteArray()) }
        val body = Converters.odt(f)
        val paras = body.paragraphs
        assertThat(paras.map { it.text }).containsExactly("Chapter", "Hello bold  world link", "one", "inner", "a", "b").inOrder()
        assertThat(paras[0].props.styleId).isEqualTo("Heading2")
        assertThat(paras[1].props.align).isEqualTo("center")
        assertThat(paras[1].runs.first { it.text == "bold" }.style.bold).isTrue()
        assertThat(paras[1].runs.first { it.text == "link" }.linkTarget).isEqualTo("https://example.org")
        assertThat(paras[2].props.numId).isEqualTo(Numbering.DECIMAL)
        assertThat(paras[3].props.level).isEqualTo(1)
        assertThat(body.blocks.last()).isInstanceOf(Block.T::class.java)
    }

    @Test fun convertedDocumentsSaveAsDocx() {
        val body = Converters.rtf("""{\rtf1 {\b Bold} text\par Second\par}""")
        val out = ByteArrayOutputStream()
        Docx.write(Docx.blank(), body, out)
        val parts = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(out.toByteArray().inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; parts[e.name] = z.readBytes() } }
        val back = Docx.read(parts)
        assertThat(back.body.paragraphs.map { it.text }).containsExactly("Bold text", "Second").inOrder()
        assertThat(back.body.paragraphs[0].runs.first().style.bold).isTrue()
    }
}

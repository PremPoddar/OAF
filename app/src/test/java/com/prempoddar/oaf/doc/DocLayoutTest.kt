package com.prempoddar.oaf.doc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocLayoutTest {
    private val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun doc(body: String, styles: String = defaultStyles, numbering: String? = null): DocxDoc = Docx.read(linkedMapOf(
        "_rels/.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$r/officeDocument" Target="word/document.xml"/></Relationships>""",
        "word/_rels/document.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$r/styles" Target="styles.xml"/>${if (numbering != null) """<Relationship Id="rId2" Type="$r/numbering" Target="numbering.xml"/>""" else ""}</Relationships>""",
        "word/document.xml" to """<w:document xmlns:w="$w"><w:body>$body</w:body></w:document>""",
        "word/styles.xml" to styles,
    ).apply { if (numbering != null) put("word/numbering.xml", numbering) }.mapValuesTo(LinkedHashMap()) { it.value.toByteArray() })

    // Like a modern Word default: 8 pt after, 1.08 lines; headings add space before.
    private val defaultStyles = """<w:styles xmlns:w="$w"><w:docDefaults>
<w:rPrDefault><w:rPr><w:sz w:val="22"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240" w:after="0"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:pPr><w:ind w:left="864" w:right="864"/></w:pPr></w:style>
</w:styles>"""

    @Test fun pageSizeAndMarginsComeFromTheSection() {
        val d = doc("""<w:p/><w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1080" w:bottom="1440" w:left="1080"/></w:sectPr>""")
        assertThat(d.page.width).isWithin(0.1f).of(595.3f)
        assertThat(d.page.height).isWithin(0.1f).of(841.9f)
        assertThat(d.page.left).isEqualTo(54f)
        assertThat(d.page.top).isEqualTo(72f)
    }

    @Test fun pageDefaultsToLetterWithInchMargins() {
        val d = doc("<w:p/>")
        assertThat(d.page.width).isEqualTo(612f)
        assertThat(d.page.left).isEqualTo(72f)
    }

    @Test fun spacingComesFromDefaultsStylesAndTheParagraph() {
        val d = doc("""<w:p><w:r><w:t>Body</w:t></w:r></w:p>
<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Head</w:t></w:r></w:p>
<w:p><w:pPr><w:spacing w:after="0" w:line="360" w:lineRule="auto"/></w:pPr><w:r><w:t>Tight</w:t></w:r></w:p>
<w:p><w:pPr><w:spacing w:line="300" w:lineRule="exact"/></w:pPr><w:r><w:t>Exact</w:t></w:r></w:p>""")
        val (body, head, tight, exact) = d.body.paragraphs.map { d.styles.layout(it) }
        assertThat(body.spaceAfter).isEqualTo(8f)
        assertThat(body.lineMultiple).isWithin(0.001f).of(259f / 240f)
        assertThat(head.spaceBefore).isEqualTo(12f)
        assertThat(head.spaceAfter).isEqualTo(0f)
        assertThat(tight.spaceAfter).isEqualTo(0f)
        assertThat(tight.lineMultiple).isWithin(0.001f).of(1.5f)
        assertThat(exact.lineExact).isEqualTo(15f)
        assertThat(exact.lineMultiple).isNull()
    }

    @Test fun indentsAndShading() {
        val d = doc("""<w:p><w:pPr><w:pStyle w:val="Quote"/></w:pPr><w:r><w:t>Q</w:t></w:r></w:p>
<w:p><w:pPr><w:ind w:left="720" w:hanging="360"/><w:shd w:val="clear" w:fill="FFF2CC"/></w:pPr><w:r><w:t>H</w:t></w:r></w:p>
<w:p><w:pPr><w:ind w:firstLine="567"/><w:pageBreakBefore/></w:pPr><w:r><w:t>F</w:t></w:r></w:p>""")
        val (quote, hang, first) = d.body.paragraphs.map { d.styles.layout(it) }
        assertThat(quote.left).isEqualTo(43.2f)
        assertThat(quote.right).isEqualTo(43.2f)
        assertThat(hang.left).isEqualTo(36f)
        assertThat(hang.firstLine).isEqualTo(-18f)
        assertThat(hang.shading).isEqualTo(0xFFF2CC)
        assertThat(first.firstLine).isWithin(0.01f).of(28.35f)
        assertThat(first.pageBreakBefore).isTrue()
    }

    @Test fun listIndentsComeFromNumbering() {
        val numbering = """<w:numbering xmlns:w="$w"><w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>"""
        val d = doc("""<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>One</w:t></w:r></w:p>""", numbering = numbering)
        val l = d.styles.layout(d.body.paragraphs.single(), d.numbering)
        assertThat(l.left).isEqualTo(36f)
        assertThat(l.firstLine).isEqualTo(-18f)
    }

    @Test fun tableCellShadingAndBorders() {
        val d = doc("""<w:tbl><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4"/></w:tblBorders></w:tblPr><w:tblGrid><w:gridCol w:w="2000"/></w:tblGrid>
<w:tr><w:tc><w:tcPr><w:shd w:val="clear" w:fill="D9E2F3"/></w:tcPr><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc></w:tr></w:tbl>""")
        val t = d.body.blocks.single() as Block.T
        assertThat(DocLayout.bordered(t, d.styles)).isTrue()
        assertThat(DocLayout.cellFill(t.rows[0][0])).isEqualTo(0xD9E2F3)
    }

    @Test fun findsPageBreaks() {
        val d = doc("""<w:p><w:r><w:t>One</w:t></w:r><w:r><w:br w:type="page"/></w:r></w:p><w:p><w:r><w:br/><w:t>Two</w:t></w:r></w:p>""")
        val (one, two) = d.body.paragraphs
        assertThat(one.runs.any(DocLayout::isPageBreak)).isTrue()
        assertThat(two.runs.any(DocLayout::isPageBreak)).isFalse()
    }
}

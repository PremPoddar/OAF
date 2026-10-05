package com.prempoddar.oaf.export

import com.google.common.truth.Truth.assertThat
import com.prempoddar.oaf.doc.Block
import com.prempoddar.oaf.doc.DocBody
import com.prempoddar.oaf.doc.DocStyles
import com.prempoddar.oaf.doc.Numbering
import com.prempoddar.oaf.doc.Para
import com.prempoddar.oaf.doc.ParaProps
import com.prempoddar.oaf.doc.Run
import com.prempoddar.oaf.doc.RunStyle
import com.prempoddar.oaf.doc.newId
import com.prempoddar.oaf.sheet.CellRange
import com.prempoddar.oaf.sheet.CellStyle
import com.prempoddar.oaf.sheet.Workbook
import org.junit.Test

class PrintHtmlTest {
    @Test fun documentsKeepHeadingsFormattingAndLists() {
        val body = DocBody(listOf(
            Block.P(Para(newId(), listOf(Run("Title & <more>")), ParaProps(styleId = "Heading1"))),
            Block.P(Para(newId(), listOf(Run("plain "), Run("bold", RunStyle(bold = true)), Run("link", linkTarget = "https://example.org")))),
            Block.P(Para(newId(), listOf(Run("item")), ParaProps(numId = Numbering.DECIMAL))),
        ))
        val html = PrintHtml.document("Doc", body, DocStyles.parse(null), Numbering(emptyMap())) { null }
        assertThat(html).contains("<h1 style=\"font-size:16.0pt\">Title &amp; &lt;more&gt;</h1>")
        assertThat(html).contains("font-weight:bold\">bold</span>")
        assertThat(html).contains("<a href=\"https://example.org\">link</a>")
        assertThat(html).contains(">1.</span>item")
    }

    @Test fun sheetsShowFormattedValuesAndMerges() {
        val wb = Workbook()
        val s = wb.sheets[0]
        s.set(0, 0, "Total", CellStyle(bold = true))
        s.set(0, 1, "1234.5", CellStyle(numFmt = "#,##0.00"))
        s.set(1, 0, "=B1*2")
        s.merges += CellRange(2, 0, 2, 1)
        s.set(2, 0, "Wide")
        wb.recalculate()
        val html = PrintHtml.sheet("Book", s)
        assertThat(html).contains(">1,234.50</td>")
        assertThat(html).contains(">2469</td>")
        assertThat(html).contains("colspan=\"2\"")
        assertThat(html).contains("font-weight:bold")
    }

    @Test fun textIsEscapedAndMarkdownRendered() {
        assertThat(PrintHtml.text("t", "<b>x</b>", markdown = false)).contains("<pre>&lt;b&gt;x&lt;/b&gt;</pre>")
        assertThat(PrintHtml.text("t", "# Head\n\n| a | b |\n|---|---|\n| 1 | 2 |", markdown = true)).contains("<table>")
    }
}

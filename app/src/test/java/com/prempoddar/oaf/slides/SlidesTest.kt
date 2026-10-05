package com.prempoddar.oaf.slides

import com.google.common.truth.Truth.assertThat
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SlidesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val p = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private val a = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val rel = "http://schemas.openxmlformats.org/package/2006/relationships"

    private fun zip(name: String, files: Map<String, String>): File = tmp.newFile(name).also { f ->
        ZipOutputStream(f.outputStream()).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }

    private fun rels(vararg pairs: Pair<String, String>) = """<Relationships xmlns="$rel">${pairs.joinToString("") { (id, t) -> """<Relationship Id="$id" Type="x" Target="$t"/>""" }}</Relationships>"""

    @Test fun pptxPlaceholdersThemeGroupsAndNotes() {
        val deck = Slides.pptx(zip("a.pptx", mapOf(
            "ppt/presentation.xml" to """<p:presentation xmlns:p="$p" xmlns:r="$r"><p:sldIdLst><p:sldId id="256" r:id="rId2"/></p:sldIdLst><p:sldSz cx="12192000" cy="6858000"/></p:presentation>""",
            "ppt/_rels/presentation.xml.rels" to rels("rId2" to "slides/slide1.xml"),
            "ppt/slides/_rels/slide1.xml.rels" to rels("rId1" to "../slideLayouts/slideLayout1.xml", "rId2" to "../media/image1.png", "rId3" to "../notesSlides/notesSlide1.xml"),
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to rels("rId1" to "../slideMasters/slideMaster1.xml"),
            "ppt/slideMasters/_rels/slideMaster1.xml.rels" to rels("rId1" to "../theme/theme1.xml"),
            "ppt/theme/theme1.xml" to """<a:theme xmlns:a="$a"><a:themeElements><a:clrScheme name="x"><a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:accent1><a:srgbClr val="4472C4"/></a:accent1></a:clrScheme></a:themeElements></a:theme>""",
            "ppt/slideMasters/slideMaster1.xml" to """<p:sldMaster xmlns:p="$p" xmlns:a="$a"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="FAFAFA"/></a:solidFill></p:bgPr></p:bg><p:spTree>
<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="838200" y="365125"/><a:ext cx="10515600" cy="1325563"/></a:xfrm></p:spPr></p:sp>
<p:sp><p:nvSpPr><p:cNvPr id="9" name="Logo"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="127000" cy="127000"/></a:xfrm><a:solidFill><a:schemeClr val="accent1"/></a:solidFill></p:spPr></p:sp>
</p:spTree></p:cSld><p:txStyles><p:titleStyle><a:lvl1pPr><a:defRPr sz="4400"/></a:lvl1pPr></p:titleStyle><p:bodyStyle><a:lvl1pPr><a:defRPr sz="2800"/></a:lvl1pPr><a:lvl2pPr><a:defRPr sz="2400"/></a:lvl2pPr></p:bodyStyle></p:txStyles></p:sldMaster>""",
            "ppt/slideLayouts/slideLayout1.xml" to """<p:sldLayout xmlns:p="$p" xmlns:a="$a"><p:cSld><p:spTree>
<p:sp><p:nvSpPr><p:cNvPr id="3" name="Body"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x="838200" y="1825625"/><a:ext cx="10515600" cy="4351338"/></a:xfrm></p:spPr></p:sp>
</p:spTree></p:cSld></p:sldLayout>""",
            "ppt/slides/slide1.xml" to """<p:sld xmlns:p="$p" xmlns:a="$a" xmlns:r="$r"><p:cSld><p:spTree>
<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title 1"/><p:cNvSpPr/><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:p><a:r><a:rPr b="1"/><a:t>Quarterly review</a:t></a:r></a:p></p:txBody></p:sp>
<p:sp><p:nvSpPr><p:cNvPr id="3" name="Body"/><p:cNvSpPr/><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/>
<a:p><a:pPr><a:buChar char="•"/></a:pPr><a:r><a:t>Revenue up</a:t></a:r></a:p><a:p><a:pPr lvl="1"><a:buAutoNum type="arabicPeriod"/></a:pPr><a:r><a:rPr><a:solidFill><a:schemeClr val="accent1"><a:lumMod val="50000"/></a:schemeClr></a:solidFill></a:rPr><a:t>Detail</a:t></a:r></a:p></p:txBody></p:sp>
<p:grpSp><p:grpSpPr><a:xfrm><a:off x="1270000" y="1270000"/><a:ext cx="2540000" cy="2540000"/><a:chOff x="0" y="0"/><a:chExt cx="1270000" cy="1270000"/></a:xfrm></p:grpSpPr>
<p:pic><p:nvPicPr><p:cNvPr id="5" name="Pic"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="rId2"/></p:blipFill><p:spPr><a:xfrm><a:off x="127000" y="127000"/><a:ext cx="635000" cy="635000"/></a:xfrm></p:spPr></p:pic></p:grpSp>
</p:spTree></p:cSld></p:sld>""",
            "ppt/notesSlides/notesSlide1.xml" to """<p:notes xmlns:p="$p" xmlns:a="$a"><p:cSld><p:spTree><p:sp><p:nvSpPr><p:cNvPr id="2" name="N"/><p:cNvSpPr/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:p><a:r><a:t>Say thanks</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:notes>""",
            "ppt/media/image1.png" to "PNGDATA",
        )))
        assertThat(deck.width).isWithin(0.1f).of(960f)
        assertThat(deck.height).isWithin(0.1f).of(540f)
        val s = deck.slides.single()
        assertThat(s.title).isEqualTo("Quarterly review")
        assertThat(s.background).isEqualTo(0xFAFAFA)
        assertThat(s.notes).isEqualTo("Say thanks")
        val texts = s.shapes.filterIsInstance<SlideShape.Text>()
        // The master's logo is drawn first, under the slide's own shapes.
        assertThat(texts.first().fill).isEqualTo(0x4472C4)
        val title = texts.single { it.isTitle }
        assertThat(title.x).isWithin(0.1f).of(66f)
        assertThat(title.paragraphs.single().runs.single().size).isEqualTo(44f)
        val body = texts.single { it.plainText.startsWith("Revenue") }
        assertThat(body.y).isWithin(0.1f).of(143.75f)
        assertThat(body.paragraphs.map { it.bullet }).containsExactly("•", "1.").inOrder()
        assertThat(body.paragraphs[1].runs.single().size).isEqualTo(24f)
        assertThat(body.paragraphs[1].runs.single().color).isEqualTo(0x203864)
        val pic = s.shapes.filterIsInstance<SlideShape.Picture>().single()
        assertThat(pic.path).isEqualTo("ppt/media/image1.png")
        assertThat(pic.x).isWithin(0.1f).of(120f)
        assertThat(pic.w).isWithin(0.1f).of(100f)
        assertThat(String(deck.image(pic.path)!!)).isEqualTo("PNGDATA")
        deck.close()
    }

    @Test fun odpFramesTextAndPictures() {
        val content = """<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" xmlns:presentation="urn:oasis:names:tc:opendocument:xmlns:presentation:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:xlink="http://www.w3.org/1999/xlink"><office:body><office:presentation>
<draw:page draw:name="p1"><draw:frame presentation:class="title" svg:x="2cm" svg:y="1cm" svg:width="20cm" svg:height="3cm"><draw:text-box><text:p>Hello deck</text:p></draw:text-box></draw:frame>
<draw:frame presentation:class="outline" svg:x="2cm" svg:y="5cm" svg:width="20cm" svg:height="10cm"><draw:text-box><text:list><text:list-item><text:p>Point one</text:p></text:list-item></text:list></draw:text-box></draw:frame>
<draw:frame svg:x="1in" svg:y="1in" svg:width="2in" svg:height="1in"><draw:image xlink:href="Pictures/a.png"/></draw:frame>
<presentation:notes><draw:frame><draw:text-box><text:p>odp notes</text:p></draw:text-box></draw:frame></presentation:notes></draw:page>
</office:presentation></office:body></office:document-content>"""
        val styles = """<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"><office:automatic-styles><style:page-layout style:name="PM1"><style:page-layout-properties fo:page-width="28cm" fo:page-height="15.75cm"/></style:page-layout></office:automatic-styles></office:document-styles>"""
        val deck = Slides.odp(zip("a.odp", mapOf("content.xml" to content, "styles.xml" to styles, "Pictures/a.png" to "IMG")))
        assertThat(deck.width).isWithin(0.5f).of(793.7f)
        val s = deck.slides.single()
        assertThat(s.title).isEqualTo("Hello deck")
        assertThat(s.notes).isEqualTo("odp notes")
        val outline = s.shapes.filterIsInstance<SlideShape.Text>().single { !it.isTitle }
        assertThat(outline.paragraphs.single().bullet).isEqualTo("•")
        val pic = s.shapes.filterIsInstance<SlideShape.Picture>().single()
        assertThat(pic.x).isEqualTo(72f)
        assertThat(String(deck.image(pic.path)!!)).isEqualTo("IMG")
        assertThat(Slides.length("10mm")).isWithin(0.01f).of(28.35f)
    }

    @Test fun pptTextIsRead() {
        val f = tmp.newFile("a.ppt")
        HSLFSlideShow().use { show ->
            val slide = show.createSlide()
            slide.addTitle().text = "Old deck"
            f.outputStream().use { show.write(it) }
        }
        val deck = Slides.ppt(f)
        assertThat(deck.slides.single().title).isEqualTo("Old deck")
    }
}

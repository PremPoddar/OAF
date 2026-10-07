package com.prempoddar.oaf.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.ui.browser.AppsScreen
import com.prempoddar.oaf.ui.browser.FilesHome
import com.prempoddar.oaf.ui.browser.FolderScreen
import com.prempoddar.oaf.ui.browser.SettingsScreen
import com.prempoddar.oaf.ui.design.Appearance
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws real screens to build/screens to check light, dark and landscape without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun setUp() = AppGraph.init(ApplicationProvider.getApplicationContext())

    private fun shoot(name: String, appearance: Appearance, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { OafTheme(appearance) { content() } }
        repeat(8) { Thread.sleep(100); rule.mainClock.advanceTimeBy(250) }
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        File("build/screens").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val light = Appearance(mode = Appearance.Mode.LIGHT)
    private val dark = Appearance(mode = Appearance.Mode.DARK)

    private fun sampleFolder(): File {
        val root = Environment.getExternalStorageDirectory()
        return File(root, "Documents/Trip").apply {
            mkdirs()
            File(this, "Bookings").mkdirs()
            File(this, "Photos").mkdirs()
            File(this, "itinerary.pdf").writeText("x".repeat(240_000))
            File(this, "budget.xlsx").writeText("x".repeat(18_000))
            File(this, "notes.md").writeText("# Notes")
            File(this, "song.mp3").writeText("x")
            File(this, ".hidden").writeText("x")
        }
    }

    @Test fun homeLight() = shoot("home-light", light) { FilesHome({}, {}) }
    @Test fun homeDark() = shoot("home-dark", dark) { FilesHome({}, {}) }
    @Test fun folderDark() = sampleFolder().let { f -> shoot("folder-dark", dark) { FolderScreen(f, onOpen = {}, onBack = {}) } }
    @Test fun folderLight() = sampleFolder().let { f -> shoot("folder-light", light) { FolderScreen(f, onOpen = {}, onBack = {}) } }
    @Test fun appsDark() = shoot("apps-dark", dark) { AppsScreen {} }
    @Test fun settingsDark() = shoot("settings-dark", dark) { SettingsScreen({}, {}, {}, {}, {}) }

    private fun samplePicture(): File {
        val b = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(b).apply {
            drawColor(android.graphics.Color.rgb(70, 120, 190))
            drawCircle(800f, 300f, 160f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(250, 210, 90) })
            drawRect(0f, 560f, 1200f, 800f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(60, 140, 80) })
        }
        return File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "sample.png").apply { outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @Test fun imageEditorDark() = samplePicture().let { f -> shoot("image-editor-dark", dark) { com.prempoddar.oaf.ui.edit.ImageEditor(com.prempoddar.oaf.io.DocSource.fromFile(f).let { s -> s }, {}) } }

    private fun fakePlayer() = androidx.media3.exoplayer.ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build().apply {
        setMediaItem(androidx.media3.common.MediaItem.Builder().setUri("file:///nothing.mp3")
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle("Midnight City").setArtist("M83").build()).build())
    }

    @Test fun audioPlayerDark() = shoot("audio-dark", dark) {
        val p = androidx.compose.runtime.remember { fakePlayer() }
        com.prempoddar.oaf.ui.viewers.AudioPlayer(com.prempoddar.oaf.media.rememberPlayerState(p), com.prempoddar.oaf.io.DocSource.fromFile(File("song.mp3")), {}, {})
    }

    @Test fun audioPlayerLight() = shoot("audio-light", light) {
        val p = androidx.compose.runtime.remember { fakePlayer() }
        com.prempoddar.oaf.ui.viewers.AudioPlayer(com.prempoddar.oaf.media.rememberPlayerState(p), com.prempoddar.oaf.io.DocSource.fromFile(File("song.mp3")), {}, {})
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test fun videoPlayer() = shoot("video-land", dark) {
        val p = androidx.compose.runtime.remember { fakePlayer() }
        com.prempoddar.oaf.ui.viewers.VideoPlayer(com.prempoddar.oaf.media.rememberPlayerState(p), com.prempoddar.oaf.io.DocSource.fromFile(File("clip.mp4")), {}, {})
    }

    private fun sampleDocx(): File {
        val w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val r = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val parts = linkedMapOf(
            "[Content_Types].xml" to """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>""",
            "_rels/.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$r/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/_rels/document.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$r/styles" Target="styles.xml"/></Relationships>""",
            "word/styles.xml" to """<w:styles xmlns:w="$w"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="0"/></w:pPr><w:rPr><w:rFonts w:ascii="Calibri Light"/><w:sz w:val="56"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="240" w:after="0"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:color w:val="2F5496"/><w:sz w:val="32"/></w:rPr></w:style>
<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4"/></w:tblBorders></w:tblPr></w:style></w:styles>""",
            "word/document.xml" to """<w:document xmlns:w="$w"><w:body>
<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Quarterly report</w:t></w:r></w:p>
<w:p><w:r><w:t xml:space="preserve">Sales grew steadily through the quarter, helped by the new store and a strong holiday season. </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>Costs stayed flat.</w:t></w:r></w:p>
<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Highlights</w:t></w:r></w:p>
<w:p><w:pPr><w:ind w:left="720" w:hanging="360"/><w:shd w:val="clear" w:fill="FFF2CC"/></w:pPr><w:r><w:t>A shaded paragraph with a hanging indent that wraps onto a second line to show how the indent works.</w:t></w:r></w:p>
<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/></w:tblPr><w:tblGrid><w:gridCol w:w="3000"/><w:gridCol w:w="3000"/><w:gridCol w:w="3000"/></w:tblGrid>
<w:tr><w:tc><w:tcPr><w:shd w:val="clear" w:fill="D9E2F3"/></w:tcPr><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Region</w:t></w:r></w:p></w:tc><w:tc><w:tcPr><w:shd w:val="clear" w:fill="D9E2F3"/></w:tcPr><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Q3</w:t></w:r></w:p></w:tc><w:tc><w:tcPr><w:shd w:val="clear" w:fill="D9E2F3"/></w:tcPr><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Q4</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:p><w:r><w:t>North</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>120</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>140</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
<w:p><w:r><w:t>Second page.</w:t></w:r></w:p>
<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>""",
        )
        val f = File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "report.docx")
        java.util.zip.ZipOutputStream(f.outputStream()).use { z -> parts.forEach { (k, v) -> z.putNextEntry(java.util.zip.ZipEntry(k)); z.write(v.toByteArray()); z.closeEntry() } }
        return f
    }

    @Test fun docPageView() = sampleDocx().let { f -> shoot("doc-page", light) { com.prempoddar.oaf.ui.viewers.DocScreen(com.prempoddar.oaf.io.DocSource.fromFile(f).detect(ApplicationProvider.getApplicationContext())) {} } }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test fun homeLandscape() = shoot("home-land", light) { FilesHome({}, {}) }
}

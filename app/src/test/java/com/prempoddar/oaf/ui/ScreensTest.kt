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

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test fun homeLandscape() = shoot("home-land", light) { FilesHome({}, {}) }
}

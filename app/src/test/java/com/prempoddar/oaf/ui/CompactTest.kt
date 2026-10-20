package com.prempoddar.oaf.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.prempoddar.oaf.ui.design.Appearance
import com.prempoddar.oaf.ui.design.ExpressiveScaffold
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Turning Compact on after scrolling (which folds the big title away) used to crash every app. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CompactTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactAfterScrollingDoesNotCrash() {
        val look = mutableStateOf(Appearance(mode = Appearance.Mode.LIGHT))
        rule.setContent {
            OafTheme(look.value) {
                ExpressiveScaffold("Appearance", onBack = {}) { pad ->
                    LazyColumn(Modifier.testTag("list"), contentPadding = PaddingValues(top = pad.calculateTopPadding())) {
                        items(60) { Text("Row $it") }
                    }
                }
            }
        }
        rule.onNodeWithTag("list").performTouchInput { swipeUp() }
        rule.waitForIdle()
        look.value = look.value.copy(compact = true)
        rule.waitForIdle()
        look.value = look.value.copy(compact = false)
        rule.waitForIdle()
    }
}

package com.prempoddar.oaf.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SwapHoriz
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Whether the bars are showing. Viewers flip it on tap and hide it while reading. */
class ChromeState(val visible: MutableState<Boolean>) {
    fun toggle() { visible.value = !visible.value }
    fun show() { visible.value = true }
    fun hide() { visible.value = false }
}

@Composable
fun rememberChrome(startVisible: Boolean = true) = remember { ChromeState(mutableStateOf(startVisible)) }

/** Offered when another engine can open the file: what it's called, and how to switch. */
class EngineSwitch(val label: String, val toggle: () -> Unit)

val LocalEngineSwitch = androidx.compose.runtime.staticCompositionLocalOf<EngineSwitch?> { null }

/**
 * The quiet chrome: the document fills the screen; a title bar and a floating pill toolbar slide
 * in on tap and away again on their own after a few seconds of reading.
 */
@Composable
fun ViewerScaffold(
    title: String,
    subtitle: String? = null,
    chrome: ChromeState = rememberChrome(),
    autoHide: Boolean = true,
    /** The sun/moon button that turns the pages light or dark for every viewer. */
    themeToggle: Boolean = true,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    toolbar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val paper = LocalPaper.current
    LaunchedEffect(chrome.visible.value, autoHide) {
        if (autoHide && chrome.visible.value) {
            delay(AUTO_HIDE_MS)
            chrome.hide()
        }
    }
    Box(Modifier.fillMaxSize().background(paper.page)) {
        content()
        AnimatedVisibility(chrome.visible.value, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Surface(color = paper.chrome, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = paper.muted, maxLines = 1)
                    }
                    LocalEngineSwitch.current?.let { sw ->
                        IconButton(onClick = sw.toggle) { Icon(Icons.Rounded.SwapHoriz, "Open with ${sw.label}") }
                    }
                    if (themeToggle) {
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        IconButton(onClick = {
                            val next = if (paper.dark) com.prempoddar.oaf.ui.design.Appearance.Mode.LIGHT else com.prempoddar.oaf.ui.design.Appearance.Mode.DARK
                            scope.launch { com.prempoddar.oaf.AppGraph.prefs.update { it.copy(viewerMode = next) } }
                        }) {
                            Icon(
                                if (paper.dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                if (paper.dark) "Light pages" else "Dark pages",
                            )
                        }
                    }
                    actions()
                }
            }
        }
        if (toolbar != null) {
            AnimatedVisibility(
                chrome.visible.value, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(
                    color = paper.chrome, shadowElevation = 6.dp, shape = CircleShape,
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp).clip(CircleShape),
                ) {
                    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, content = toolbar)
                }
            }
        }
    }
}

private const val AUTO_HIDE_MS = 3_500L

package com.prempoddar.oaf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prempoddar.oaf.data.OafSettings
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.OafTheme
import com.prempoddar.oaf.ui.browser.BinScreen
import com.prempoddar.oaf.ui.browser.BrowserScreen
import com.prempoddar.oaf.ui.browser.StorageScreen
import com.prempoddar.oaf.ui.isDark
import com.prempoddar.oaf.ui.viewers.ViewerHost
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    /** A file another app asked us to open. */
    private val incoming = mutableStateOf<DocSource?>(null)
    private var openedFromOutside = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings: OafSettings? by AppGraph.prefs.settings.collectAsStateWithLifecycle(null)
            val s = settings ?: return@setContent
            val dark = isDark(s.theme)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            OafTheme(s.theme) {
                val nav = rememberNavController()
                fun open(source: DocSource) = nav.navigate("view/" + URLEncoder.encode(AppGraph.put(source), "UTF-8"))
                LaunchedEffect(incoming.value) { incoming.value?.let { open(it); incoming.value = null } }
                NavHost(nav, "browser", enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
                    composable("browser") { BrowserScreen(onOpen = ::open, onBin = { nav.navigate("bin") }, onStorage = { nav.navigate("storage") }) }
                    composable("bin") { BinScreen(onBack = { nav.popBackStack() }) }
                    composable("storage") { StorageScreen(onBack = { nav.popBackStack() }) }
                    composable("view/{key}") { e ->
                        val key = java.net.URLDecoder.decode(e.arguments!!.getString("key")!!, "UTF-8")
                        val source = AppGraph.get(key) ?: DocSource.fromUri(this@MainActivity, Uri.parse(key))
                        ViewerHost(
                            source,
                            onBack = {
                                // Opened from another app: back returns there.
                                if (openedFromOutside && nav.previousBackStackEntry?.destination?.route == "browser") finish() else nav.popBackStack()
                            },
                            onOpen = ::open,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val uri: Uri = when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> null
        } ?: return
        openedFromOutside = true
        incoming.value = DocSource.fromUri(this, uri, intent.type)
    }
}

package com.prempoddar.oaf

import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.prempoddar.oaf.ui.OAF_BRAND
import com.prempoddar.oaf.ui.browser.AppsScreen
import com.prempoddar.oaf.ui.browser.Category
import com.prempoddar.oaf.ui.browser.CategoryScreen
import com.prempoddar.oaf.ui.browser.FilesHome
import com.prempoddar.oaf.ui.browser.FolderScreen
import com.prempoddar.oaf.ui.browser.HomeTarget
import com.prempoddar.oaf.ui.browser.SettingsScreen
import com.prempoddar.oaf.ui.design.AboutScreen
import com.prempoddar.oaf.ui.design.Appearance
import com.prempoddar.oaf.ui.design.AppearanceScreen
import com.prempoddar.oaf.ui.design.Credit
import com.prempoddar.oaf.ui.design.LayeredIcon
import kotlinx.coroutines.launch
import com.prempoddar.oaf.ui.browser.StorageScreen
import com.prempoddar.oaf.ui.viewers.ViewerHost
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    /** A file another app asked us to open. */
    private val incoming = mutableStateOf<DocSource?>(null)
    private var openedFromOutside = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.prempoddar.oaf.ui.design.Haptics.attach(window.decorView)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings: OafSettings? by AppGraph.prefs.settings.collectAsStateWithLifecycle(null)
            val s = settings ?: return@setContent
            OafTheme(s.appearance) {
                val nav = rememberNavController()
                fun open(source: DocSource) = nav.navigate("view/" + URLEncoder.encode(AppGraph.put(source), "UTF-8"))
                LaunchedEffect(incoming.value) { incoming.value?.let { open(it); incoming.value = null } }
                fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
                fun go(t: HomeTarget) = when (t) {
                    is HomeTarget.Folder -> nav.navigate("folder/${enc(t.dir.path)}?search=${t.search}")
                    is HomeTarget.Of -> nav.navigate("category/${t.category.name}")
                    HomeTarget.Apps -> nav.navigate("apps")
                    HomeTarget.Bin -> nav.navigate("bin")
                    HomeTarget.Storage -> nav.navigate("storage")
                    HomeTarget.Settings -> nav.navigate("settings")
                    HomeTarget.Network -> nav.navigate("network")
                    HomeTarget.Batch -> nav.navigate("batch")
                }
                val back: () -> Unit = { nav.popBackStack() }
                NavHost(
                    nav, "browser",
                    enterTransition = { slideInHorizontally { it / 6 } + fadeIn() }, exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() }, popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() },
                ) {
                    composable("browser") { FilesHome(onOpen = ::open, onGo = ::go) }
                    composable("folder/{path}?search={search}", arguments = listOf(navArgument("search") { type = NavType.BoolType; defaultValue = false })) { e ->
                        val path = java.net.URLDecoder.decode(e.arguments!!.getString("path")!!, "UTF-8")
                        FolderScreen(java.io.File(path), e.arguments!!.getBoolean("search"), onOpen = ::open, onBack = back)
                    }
                    composable("category/{c}") { e -> CategoryScreen(Category.valueOf(e.arguments!!.getString("c")!!), onOpen = ::open, onBack = back) }
                    composable("apps") { AppsScreen(onBack = back) }
                    composable("bin") { BinScreen(onBack = back) }
                    composable("batch") { com.prempoddar.oaf.ui.edit.BatchScreen(onBack = back) }
                    composable("network") { com.prempoddar.oaf.ui.browser.NetworkScreen(onBack = back, onOpenServer = { sv -> nav.navigate("remote/${sv.id}") }) }
                    composable("remote/{id}") { e ->
                        val server = AppGraph.servers.get(e.arguments!!.getString("id")!!)
                        if (server == null) LaunchedEffect(Unit) { back() } else com.prempoddar.oaf.ui.browser.RemoteScreen(server, onBack = back, onOpen = ::open)
                    }
                    composable("storage") { StorageScreen(onBack = back) }
                    composable("settings") {
                        SettingsScreen(
                            onBack = back, onAppearance = { nav.navigate("appearance") }, onAbout = { nav.navigate("about") },
                            onBin = { nav.navigate("bin") }, onStorage = { nav.navigate("storage") },
                        )
                    }
                    composable("appearance") {
                        val scope = rememberCoroutineScope()
                        AppearanceScreen(s.appearance, OAF_BRAND, onChange = { a -> scope.launch { AppGraph.prefs.update { it.copy(appearance = a) } } }, onBack = back)
                    }
                    composable("about") {
                        AboutScreen(
                            "OAF", "Open all files: documents, sheets, slides, books, media and archives, all on your phone.",
                            icon = { LayeredIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground) },
                            credits = listOf(
                                Credit("pdf.js", "Apache 2.0"),
                                Credit("docx-preview", "Apache 2.0"), Credit("JSZip", "MIT"), Credit("SheetJS", "Apache 2.0"),
                                Credit("PdfBox-Android", "Apache 2.0"), Credit("Apache POI", "Apache 2.0"),
                                Credit("Apache Commons Compress", "Apache 2.0"), Credit("XZ for Java", "Public domain"),
                                Credit("junrar", "UnRAR licence"), Credit("commonmark-java", "BSD 2-Clause"),
                                Credit("AndroidSVG", "Apache 2.0"), Credit("AndroidX Media3", "Apache 2.0"),
                                Credit("Apache Commons Net", "Apache 2.0"), Credit("JSch", "BSD"), Credit("SMBJ", "Apache 2.0"), Credit("OkHttp", "Apache 2.0"),
                                Credit("Literata, Inter, JetBrains Mono", "SIL Open Font License"),
                            ),
                            onBack = back,
                        )
                    }
                    composable("view/{key}") { e ->
                        val key = java.net.URLDecoder.decode(e.arguments!!.getString("key")!!, "UTF-8")
                        // Kept for the life of this screen: a new source on every redraw would start loading again.
                        val source = remember(key) { AppGraph.get(key) ?: DocSource.fromUri(this@MainActivity, Uri.parse(key)).also { AppGraph.put(it) } }
                        // Opened from another app: back (button or swipe) returns there instead of to OAF's home.
                        val leave = openedFromOutside && nav.previousBackStackEntry?.destination?.route == "browser"
                        val onBack: () -> Unit = { if (leave) finish() else nav.popBackStack() }
                        BackHandler(leave) { onBack() }
                        // Pages can be light or dark on their own; the app around them keeps its look.
                        OafTheme(s.appearance.copy(mode = if (s.viewerMode == Appearance.Mode.SYSTEM) s.appearance.mode else s.viewerMode)) {
                            ViewerHost(source, onBack = onBack, onOpen = ::open)
                        }
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.prempoddar.oaf.media.Pip.active.value = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        com.prempoddar.oaf.media.Pip.onLeave?.invoke()
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
        // Read now, while the other app's permission is fresh; see DocSource.received.
        val type = intent.type
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val source = DocSource.received(this@MainActivity, uri, type)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { incoming.value = source }
        }
    }
}

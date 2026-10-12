package com.prempoddar.oaf.engine

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import org.json.JSONObject

/**
 * A second opinion for files OAF's own viewers may not draw faithfully: pdf.js for PDFs,
 * docx-preview for Word documents and SheetJS for spreadsheets, each running in the web view from
 * files inside the app. Nothing is fetched from the internet; the document is handed over directly.
 */
object WebEngine : DocumentEngine {
    override val name = "Web"

    /** Which bundled page shows [kind], and what to call that engine. */
    fun pageFor(kind: FileKind): Pair<String, String>? = when (kind) {
        FileKind.PDF -> "pdf" to "pdf.js"
        FileKind.DOCX -> "docx" to "docx-preview"
        FileKind.XLSX, FileKind.XLS, FileKind.ODS, FileKind.CSV, FileKind.TSV -> "sheet" to "SheetJS"
        else -> null
    }

    override fun viewer(kind: FileKind): Viewer? = pageFor(kind)?.let { (page, label) -> { s, back, _ -> WebDocView(s, page, label, back) } }

    /** Bigger files would take too long to hand over and too much memory to draw this way. */
    const val MAX_BYTES = 40L * 1024 * 1024
}

/** What the page asks OAF for: the file, its name, and the colours to draw in. */
private class Bridge(private val read: () -> ByteArray, private val name: String, private val theme: String, private val onDone: (String?) -> Unit) {
    @JavascriptInterface fun name() = name
    @JavascriptInterface fun theme() = theme
    @JavascriptInterface fun data(): String = Base64.encodeToString(read(), Base64.NO_WRAP)
    /** The page has drawn the document, or [error] says why it couldn't. */
    @JavascriptInterface fun done(error: String?) = onDone(error?.takeIf { it.isNotBlank() })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebDocView(source: DocSource, page: String, label: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val chrome = rememberChrome()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val theme = remember(paper) {
        JSONObject().put("dark", paper.dark)
            .put("page", "#%06X".format(paper.page.toArgb() and 0xFFFFFF))
            .put("ink", "#%06X".format(paper.ink.toArgb() and 0xFFFFFF))
            .put("accent", "#%06X".format(paper.accent.toArgb() and 0xFFFFFF)).toString()
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val web = remember(theme) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.blockNetworkLoads = true
            settings.allowContentAccess = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setBackgroundColor(paper.page.toArgb())
            addJavascriptInterface(
                Bridge(
                    read = {
                        if (source.size > WebEngine.MAX_BYTES) error("This file is too big for $label; OAF's own viewer can open it.")
                        source.open(context).use { it.readBytes() }
                    },
                    name = source.name, theme = theme,
                    onDone = { e -> post { loading = false; error = e } },
                ),
                "OAF",
            )
            loadUrl("file:///android_asset/web/$page.html")
        }
    }
    DisposableEffect(web) { onDispose { web.destroy() } }
    ViewerScaffold(title = source.name, subtitle = "Opened with $label", chrome = chrome, autoHide = false, onBack = onBack) {
        AndroidView({ web }, Modifier.fillMaxSize().padding(top = top + 56.dp))
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            when {
                error != null -> Text("$label couldn't open this file: $error", color = paper.muted)
                loading -> CircularProgressIndicator()
            }
        }
    }
}

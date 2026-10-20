package com.prempoddar.oaf.ui.viewers

import com.prempoddar.oaf.ui.design.tap
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.book.Epub
import com.prempoddar.oaf.book.EpubBook
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.Paper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun hex(c: Color) = "#%06X".format(0xFFFFFF and c.toArgb())

/** The book's own styles stay, but colours and type follow OAF so every book reads comfortably. */
private fun readerCss(p: Paper, scale: Float) = """
<style>
@font-face { font-family: 'OafSerif'; src: url('file:///android_res/font/literata.ttf'); }
html { -webkit-text-size-adjust: none; }
body { background: ${hex(p.page)} !important; color: ${hex(p.ink)} !important; font-family: 'OafSerif', serif; font-size: ${(17 * scale).toInt()}px; line-height: 1.65;
  margin: 0 auto; padding: 96px 22px 140px; max-width: 680px; overflow-wrap: break-word; }
img, svg, video { max-width: 100% !important; height: auto !important; }
a { color: ${hex(p.accent)}; }
pre, code { white-space: pre-wrap; }
table { max-width: 100%; }
${if (p.dark) "body * { color: inherit !important; background-color: transparent !important; border-color: ${hex(p.rule)} !important; } img { opacity: 0.88; }" else ""}
</style>"""

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun EpubScreen(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val chrome = rememberChrome()
    val book by produceState<Result<EpubBook>?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching { Epub.open(source.local(context), File(context.cacheDir, "epub/${source.uri.toString().hashCode().toUInt()}")) }
        }
    }
    var chapter by remember { mutableIntStateOf(-1) }
    var restoreFraction by remember { mutableFloatStateOf(0f) }
    var fragment by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var contents by remember { mutableStateOf(false) }
    val key = source.uri.toString()

    // Position is saved as chapter × 1000 + how far through it, in thousandths.
    LaunchedEffect(book) {
        if (book?.isSuccess == true && chapter < 0) {
            val saved = runCatching { AppGraph.prefs.position(key) }.getOrDefault(0)
            chapter = (saved / 1000).coerceIn(0, book!!.getOrThrow().chapters.lastIndex)
            restoreFraction = (saved % 1000) / 1000f
        }
    }
    fun savePosition(ch: Int, fraction: Float) { scope.launch { runCatching { AppGraph.prefs.savePosition(key, ch * 1000 + (fraction * 999).toInt().coerceIn(0, 999)) } } }

    val b = book?.getOrNull()
    ViewerScaffold(
        title = b?.title ?: source.name,
        subtitle = b?.let { bk -> listOfNotNull(bk.author, bk.chapters.getOrNull(chapter)?.title ?: "Chapter ${chapter + 1} of ${bk.chapters.size}").joinToString(" · ") } ?: "EPUB",
        chrome = chrome, onBack = onBack,
        actions = {
            if (b != null) {
                IconButton(onClick = tap { contents = true }) { Icon(Icons.AutoMirrored.Rounded.Toc, "Contents") }
                IconButton(onClick = tap { scale = (scale - 0.1f).coerceAtLeast(0.7f) }) { Icon(Icons.Rounded.TextDecrease, "Smaller text") }
                IconButton(onClick = tap { scale = (scale + 0.1f).coerceAtMost(2f) }) { Icon(Icons.Rounded.TextIncrease, "Larger text") }
            }
        },
    ) {
        when {
            book == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            b == null -> Text(book?.exceptionOrNull()?.message ?: "Couldn't open this book", color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = 100.dp))
            chapter >= 0 -> {
                val file = b.chapters[chapter].file
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = true
                            settings.allowContentAccess = false
                            settings.blockNetworkLoads = true
                            settings.builtInZoomControls = false
                            setBackgroundColor(paper.page.toArgb())
                            val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean { chrome.toggle(); return false }
                            })
                            setOnTouchListener { _, e -> taps.onTouchEvent(e); false }
                            setOnScrollChangeListener { v, _, y, _, _ ->
                                val web = v as WebView
                                val total = (web.contentHeight * web.scale - web.height).coerceAtLeast(1f)
                                tag = (y / total).coerceIn(0f, 1f)
                            }
                        }
                    },
                    update = { web ->
                        val loadKey = "${file.path}|$scale|${paper.dark}"
                        if (web.getTag(com.prempoddar.oaf.R.id.oaf_loaded) == loadKey) return@AndroidView
                        web.setTag(com.prempoddar.oaf.R.id.oaf_loaded, loadKey)
                        val fraction = if (web.getTag(com.prempoddar.oaf.R.id.oaf_loaded_file) == file.path) (web.tag as? Float) ?: 0f else restoreFraction
                        web.setTag(com.prempoddar.oaf.R.id.oaf_loaded_file, file.path)
                        val target = fragment
                        web.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url
                                if (url.scheme == "file") {
                                    val f = File(url.path ?: return true).canonicalFile
                                    val i = b.chapterOf(f)
                                    if (i >= 0) { savePosition(chapter, (view.tag as? Float) ?: 0f); fragment = url.fragment; restoreFraction = 0f; chapter = i }
                                    return true
                                }
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                                return true
                            }
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                // Only the book's own files and OAF's fonts load.
                                val u = request.url
                                val allowed = u.scheme == "file" && (u.path?.let { File(it).canonicalPath.startsWith(b.root.canonicalPath) || it.startsWith("/android_res/") } == true)
                                return if (allowed) null else WebResourceResponse("text/plain", "utf-8", null)
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                // Links to a spot inside a chapter open at the chapter's start: jumping
                                // further would need scripts, which stay off for safety.
                                val f = if (target != null) 0f else fraction
                                view.postDelayed({ view.scrollTo(0, ((view.contentHeight * view.scale - view.height) * f).toInt().coerceAtLeast(0)) }, 120)
                            }
                        }
                        fragment = null
                        val html = runCatching { file.readText() }.getOrDefault("<p>Couldn't read this chapter.</p>")
                        val styled = Regex("</head>", RegexOption.IGNORE_CASE).find(html)?.let { html.replaceRange(it.range, readerCss(paper, scale) + "</head>") } ?: (readerCss(paper, scale) + html)
                        web.loadDataWithBaseURL(Uri.fromFile(file).toString(), styled, "text/html", "utf-8", null)
                    },
                    onRelease = { web -> savePosition(chapter, (web.tag as? Float) ?: 0f); web.destroy() },
                )
                if (chrome.visible.value) {
                    Surface(color = paper.chrome, shadowElevation = 6.dp, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        Row(Modifier.navigationBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(enabled = chapter > 0, onClick = tap { savePosition(chapter - 1, 0f); restoreFraction = 0f; chapter-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous chapter") }
                            Text(
                                b.chapters[chapter].title ?: "Chapter ${chapter + 1}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { contents = true },
                            )
                            IconButton(enabled = chapter < b.chapters.lastIndex, onClick = tap { savePosition(chapter + 1, 0f); restoreFraction = 0f; chapter++ }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next chapter") }
                        }
                    }
                }
            }
        }
    }

    if (contents && b != null) {
        AlertDialog(
            onDismissRequest = { contents = false },
            title = { Text("Contents") },
            text = {
                val items = b.toc.ifEmpty { b.chapters.mapIndexed { i, c -> EpubBook.TocItem(c.title ?: "Chapter ${i + 1}", i, null, 0) } }
                LazyColumn {
                    itemsIndexed(items) { _, t ->
                        Text(
                            t.title, maxLines = 2,
                            style = if (t.level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                            color = if (t.chapter == chapter) paper.accent else paper.ink,
                            modifier = Modifier.fillMaxWidth().clickable {
                                contents = false; restoreFraction = 0f; fragment = t.fragment; savePosition(t.chapter, 0f); chapter = t.chapter
                            }.padding(start = (t.level * 14).dp, top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = tap { contents = false }) { Text("Close") } },
        )
    }
}

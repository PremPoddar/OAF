package com.prempoddar.oaf.ui.viewers

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.prempoddar.oaf.export.PrintHtml
import com.prempoddar.oaf.export.Printing
import android.annotation.SuppressLint
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.io.readNBytesCompat
import com.prempoddar.oaf.text.Highlighter
import com.prempoddar.oaf.text.LineIndex
import com.prempoddar.oaf.text.Pretty
import com.prempoddar.oaf.text.TextFormat
import com.prempoddar.oaf.text.TextLoad
import com.prempoddar.oaf.text.Token
import com.prempoddar.oaf.ui.CodeStyle
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.Paper
import com.prempoddar.oaf.ui.ReadingBody
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private const val EDIT_LIMIT = 2L * 1024 * 1024
private const val HIGHLIGHT_EDIT_LIMIT = 300_000

private sealed interface TextLoaded {
    data class Small(val text: String, val format: TextFormat) : TextLoaded
    data class Big(val index: LineIndex) : TextLoaded
    data class Failed(val reason: String) : TextLoaded
}

private fun tokenColor(t: Token, p: Paper): SpanStyle = when (t) {
    Token.KEYWORD -> SpanStyle(color = if (p.dark) Color(0xFFC792EA) else Color(0xFF8E3FA8))
    Token.TYPE -> SpanStyle(color = if (p.dark) Color(0xFF82AAFF) else Color(0xFF2F5D8C))
    Token.STRING -> SpanStyle(color = if (p.dark) Color(0xFFC3E88D) else Color(0xFF3E7B2C))
    Token.COMMENT -> SpanStyle(color = p.faint, fontStyle = FontStyle.Italic)
    Token.NUMBER, Token.LITERAL -> SpanStyle(color = if (p.dark) Color(0xFFF78C6C) else Color(0xFFB4532A))
    Token.ANNOTATION -> SpanStyle(color = if (p.dark) Color(0xFFFFCB6B) else Color(0xFF9A6B00))
    Token.TAG -> SpanStyle(color = if (p.dark) Color(0xFFF07178) else Color(0xFFA63A3A))
    Token.ATTR, Token.KEY -> SpanStyle(color = if (p.dark) Color(0xFFFFCB6B) else Color(0xFF8A5A00))
}

/** Colours a whole text (used for files small enough to edit). */
private fun highlightAll(text: String, h: Highlighter?, p: Paper): AnnotatedString {
    if (h == null) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var mode = Highlighter.Mode.NORMAL
        var offset = 0
        text.split('\n').forEach { line ->
            val (spans, next) = h.line(line, mode)
            spans.forEach { addStyle(tokenColor(it.token, p), offset + it.start, offset + it.end) }
            mode = next
            offset += line.length + 1
        }
    }
}

@Composable
fun TextScreen(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val chrome = rememberChrome()
    val highlighter = remember(source.kind, source.lang) { Highlighter.forKind(source.kind, source.lang) }
    val isCode = highlighter != null || source.kind == FileKind.CODE
    val loaded by produceState<TextLoaded?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = source.local(context)
                val format = TextLoad.format(file.inputStream().use { it.readNBytesCompat(8192) })
                if (file.length() <= EDIT_LIMIT) TextLoaded.Small(TextLoad.read(file, format), format) else TextLoaded.Big(LineIndex(file, format))
            }.getOrElse { TextLoaded.Failed(it.message ?: "Couldn't read this file") }
        }
    }

    var editing by remember { mutableStateOf(false) }
    var rendered by remember { mutableStateOf(source.kind == FileKind.MARKDOWN || source.kind == FileKind.HTML) }
    var pretty by remember { mutableStateOf(source.kind == FileKind.JSON || source.kind == FileKind.XML) }
    var wrap by remember { mutableStateOf(!isCode) }
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var saved by remember { mutableStateOf("") }
    val undo = remember { mutableStateListOf<TextFieldValue>() }
    val redo = remember { mutableStateListOf<TextFieldValue>() }
    var findOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var fontScale by remember { mutableStateOf(1f) }

    LaunchedEffect(loaded) { (loaded as? TextLoaded.Small)?.let { value = TextFieldValue(it.text); saved = it.text } }
    // Unsaved changes stay unsaved when switching back to View, so View shows them too.
    val dirty = loaded is TextLoaded.Small && value.text != saved

    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(source.mime ?: "text/plain")) { uri ->
        val l = loaded as? TextLoaded.Small ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(TextLoad.encode(value.text, l.format)) } }
            saved = value.text
            Toast.makeText(context, "Saved a copy", Toast.LENGTH_SHORT).show()
        }
    }
    fun save() {
        val l = loaded as? TextLoaded.Small ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { if (!source.canWrite(context)) error("read-only"); source.openOutput(context).use { it.write(TextLoad.encode(value.text, l.format)) } }.isSuccess
            }
            if (ok) { saved = value.text; Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() } else saveCopy.launch(source.name)
        }
    }

    BackHandler(enabled = dirty || findOpen) { if (findOpen) findOpen = false else confirmLeave = true }

    ViewerScaffold(
        title = (if (dirty) "• " else "") + source.name,
        subtitle = when (val l = loaded) {
            is TextLoaded.Small -> "${source.kind.label} · ${l.format.charset.displayName()}${if (l.format.crlf) " · CRLF" else ""} · ${l.text.count { it == '\n' } + 1} lines"
            is TextLoaded.Big -> "${source.kind.label} · ${l.index.count} lines · read-only (large file)"
            else -> source.kind.label
        },
        chrome = chrome, autoHide = !editing, onBack = { if (dirty) confirmLeave = true else onBack() },
        actions = {
            val twoModes = loaded is TextLoaded.Small && (source.kind == FileKind.MARKDOWN || source.kind == FileKind.HTML)
            if (twoModes) ModeSwitch(editing, onView = { editing = false; rendered = true; findOpen = false }, onEdit = { editing = true; rendered = false; chrome.show() })
            if (editing) {
                IconButton(enabled = undo.isNotEmpty(), onClick = { redo += value; value = undo.removeAt(undo.lastIndex) }) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
                IconButton(enabled = redo.isNotEmpty(), onClick = { undo += value; value = redo.removeAt(redo.lastIndex) }) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
                IconButton(onClick = { findOpen = !findOpen }) { Icon(Icons.Rounded.Search, "Find and replace") }
                IconButton(enabled = dirty, onClick = ::save) { Icon(Icons.Rounded.Save, "Save") }
            } else if (loaded is TextLoaded.Small && !twoModes) {
                IconButton(onClick = { editing = true; rendered = false; pretty = false; chrome.show() }) { Icon(Icons.Rounded.Edit, "Edit") }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (!editing && (source.kind == FileKind.MARKDOWN || source.kind == FileKind.HTML)) {
                        DropdownMenuItem(text = { Text(if (rendered) "Show source" else "Show formatted") }, onClick = { menu = false; rendered = !rendered })
                    }
                    if (!editing && (source.kind == FileKind.JSON || source.kind == FileKind.XML)) {
                        DropdownMenuItem(text = { Text(if (pretty) "Show as saved" else "Format nicely") }, onClick = { menu = false; pretty = !pretty })
                    }
                    DropdownMenuItem(text = { Text(if (wrap) "Don't wrap lines" else "Wrap lines") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.WrapText, null) }, onClick = { menu = false; wrap = !wrap })
                    DropdownMenuItem(text = { Text("Larger text") }, onClick = { menu = false; fontScale = (fontScale + 0.15f).coerceAtMost(2.5f) })
                    DropdownMenuItem(text = { Text("Smaller text") }, onClick = { menu = false; fontScale = (fontScale - 0.15f).coerceAtLeast(0.6f) })
                    if (editing) DropdownMenuItem(text = { Text("Save a copy…") }, onClick = { menu = false; saveCopy.launch(source.name) })
                    DropdownMenuItem(text = { Text("Print or save as PDF…") }, onClick = { menu = false; (loaded as? TextLoaded.Small)?.let { l -> Printing.printHtml(context, source.name, PrintHtml.text(source.name, if (editing) value.text else l.text, markdown = source.kind == FileKind.MARKDOWN)) } })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareSource(context, source) })
                    DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; openElsewhere(context, source) })
                }
            }
        },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val style = (if (isCode) CodeStyle else ReadingBody).let { it.copy(fontSize = it.fontSize * fontScale, lineHeight = it.lineHeight * fontScale) }
        when (val l = loaded) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is TextLoaded.Failed -> Text(l.reason, color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = 80.dp))
            is TextLoaded.Big -> LinesView(count = l.index.count, line = { l.index.line(it) }, style = style, numbers = isCode, wrap = wrap, insets = insets, onTap = chrome::toggle, highlighter = null, paper = paper)
            is TextLoaded.Small -> when {
                editing -> Column(Modifier.fillMaxSize().imePadding().padding(top = insets.calculateTopPadding() + 64.dp)) {
                    if (findOpen) FindBar(value, onChange = { v -> undo += value; value = v }, onClose = { findOpen = false })
                    Editor(value, onChange = { v ->
                        if (v.text != value.text) {
                            // One undo step per pause or per word.
                            if (undo.isEmpty() || v.text.lastOrNull()?.isWhitespace() == true || v.text.length - (undo.lastOrNull()?.text?.length ?: 0) > 30) undo += value
                            if (undo.size > 300) undo.removeAt(0)
                            redo.clear()
                        }
                        value = v
                    }, highlighter = highlighter.takeIf { value.text.length < HIGHLIGHT_EDIT_LIMIT }, style = style, wrap = wrap, paper = paper)
                }
                rendered && source.kind == FileKind.MARKDOWN -> HtmlView(markdownToHtml(value.text.ifEmpty { l.text }, paper, fontScale), insets.calculateTopPadding(), chrome::toggle)
                rendered && source.kind == FileKind.HTML -> HtmlView(
                    value.text.ifEmpty { l.text }, insets.calculateTopPadding(), chrome::toggle, page = true,
                    base = source.file?.parentFile?.let { android.net.Uri.fromFile(it).toString() + "/" },
                )
                else -> {
                    val shown = remember(l.text, pretty) {
                        val p = if (pretty) (if (source.kind == FileKind.JSON) Pretty.json(l.text) else Pretty.xml(l.text)) else null
                        (p ?: l.text).split('\n')
                    }
                    val modes = remember(shown, highlighter) { lineModes(shown, highlighter) }
                    LinesView(shown.size, { shown[it] }, style, isCode, wrap, insets, chrome::toggle, highlighter, paper, modes)
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Save changes?") },
            text = { Text("You've edited ${source.name}.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; save(); onBack() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Discard") } },
        )
    }
}

/** The highlighter state at the start of each line, so lines can be coloured independently while scrolling. */
private fun lineModes(lines: List<String>, h: Highlighter?): Array<Highlighter.Mode>? {
    if (h == null || lines.size > 200_000) return null
    var m = Highlighter.Mode.NORMAL
    return Array(lines.size) { i -> m.also { m = h.line(lines[i], m).second } }
}

@Composable
private fun LinesView(
    count: Int, line: (Int) -> String, style: androidx.compose.ui.text.TextStyle, numbers: Boolean, wrap: Boolean,
    insets: PaddingValues, onTap: () -> Unit, highlighter: Highlighter?, paper: Paper, modes: Array<Highlighter.Mode>? = null,
) {
    val state = rememberLazyListState()
    val gutter = remember(count) { count.toString().length }
    val hScroll = rememberScrollState()
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        LazyColumn(
            Modifier.fillMaxSize().then(if (wrap) Modifier else Modifier.horizontalScroll(hScroll)),
            state = state,
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 72.dp, bottom = insets.calculateBottomPadding() + 40.dp, start = if (numbers) 8.dp else 22.dp, end = 22.dp),
        ) {
            items(count) { i ->
                val text = line(i)
                val annotated = if (highlighter != null && modes != null) {
                    val spans = highlighter.line(text, modes[i]).first
                    buildAnnotatedString { append(text); spans.forEach { addStyle(tokenColor(it.token, paper), it.start, it.end) } }
                } else AnnotatedString(text)
                Row {
                    if (numbers) Text((i + 1).toString().padStart(gutter), style = style.copy(color = paper.faint), modifier = Modifier.padding(end = 12.dp))
                    Text(annotated, style = style.copy(color = paper.ink), softWrap = wrap, modifier = if (wrap) Modifier.fillMaxWidth() else Modifier)
                }
            }
        }
    }
}

@Composable
private fun Editor(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, highlighter: Highlighter?, style: androidx.compose.ui.text.TextStyle, wrap: Boolean, paper: Paper) {
    val transform = remember(highlighter, paper) {
        VisualTransformation { text -> TransformedText(highlightAll(text.text, highlighter, paper), OffsetMapping.Identity) }
    }
    val v = rememberScrollState()
    val h = rememberScrollState()
    Box(Modifier.fillMaxSize().verticalScroll(v).then(if (wrap) Modifier else Modifier.horizontalScroll(h))) {
        BasicTextField(
            value = value, onValueChange = onChange,
            textStyle = style.copy(color = paper.ink),
            visualTransformation = transform,
            cursorBrush = SolidColor(paper.accent),
            modifier = (if (wrap) Modifier.fillMaxWidth() else Modifier.width(4000.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FindBar(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onClose: () -> Unit) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    val hits = remember(value.text, find, matchCase) {
        if (find.isEmpty()) emptyList() else Regex(Regex.escape(find), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)).findAll(value.text).map { it.range }.toList()
    }
    var current by remember { mutableIntStateOf(0) }
    fun select(i: Int) {
        if (hits.isEmpty()) return
        current = (i + hits.size) % hits.size
        val r = hits[current]
        onChange(value.copy(selection = TextRange(r.first, r.last + 1)))
    }
    val paper = LocalPaper.current
    Surface(color = paper.chrome) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(find, { find = it; current = 0 }, placeholder = { Text("Find") }, singleLine = true, modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                Text(if (hits.isEmpty()) "0" else "${current + 1}/${hits.size}", style = MaterialTheme.typography.labelMedium, color = paper.muted, modifier = Modifier.padding(horizontal = 6.dp))
                IconButton(onClick = { select(current - 1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous") }
                IconButton(onClick = { select(current + 1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Next") }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(replace, { replace = it }, placeholder = { Text("Replace with") }, singleLine = true, modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                TextButton(onClick = { matchCase = !matchCase }) { Text(if (matchCase) "Aa ✓" else "Aa", fontSize = 13.sp) }
                TextButton(enabled = hits.isNotEmpty(), onClick = {
                    val r = hits[current.coerceIn(hits.indices)]
                    val t = value.text.replaceRange(r, replace)
                    onChange(TextFieldValue(t, TextRange(r.first + replace.length)))
                }) { Text("Replace") }
                TextButton(enabled = hits.isNotEmpty(), onClick = {
                    val t = value.text.replace(find, replace, ignoreCase = !matchCase)
                    onChange(TextFieldValue(t, TextRange(0)))
                }) { Text("All") }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlView(html: String, top: androidx.compose.ui.unit.Dp, onTap: () -> Unit, page: Boolean = false, base: String? = null) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // A web page runs as it would in a browser, with its scripts, saved state and the
                // pictures and styles beside it. Markdown we render ourselves needs none of that.
                settings.javaScriptEnabled = page
                settings.domStorageEnabled = page
                settings.allowFileAccess = page
                // OAF has no internet permission: pages show what's on the phone, nothing from the web.
                settings.blockNetworkLoads = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = page
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                if (!page) setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : android.webkit.WebViewClient() {
                    // Links to other sites open in the browser; links within the page stay here.
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                        val url = request.url
                        if (url.scheme == "http" || url.scheme == "https") {
                            runCatching { view.context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url)) }
                            return true
                        }
                        return false
                    }
                }
                setOnClickListener { onTap() }
            }
        },
        // Loaded once per text, so taps that show or hide the bars don't reload the page.
        update = { w ->
            if (w.tag != html) {
                w.tag = html
                w.loadDataWithBaseURL(base ?: "file:///android_res/", html, "text/html", "utf-8", null)
            }
        },
        // A web page uses the whole screen below the status bar, the title bar sliding over it as
        // a browser's does; Markdown keeps clear of the bar.
        modifier = Modifier.fillMaxSize().padding(top = if (page) top else top + 56.dp),
    )
}

/** Markdown rendered in the reader style, with fonts and colours matching the app theme. */
fun markdownToHtml(md: String, p: Paper, scale: Float = 1f): String {
    val ext = listOf(TablesExtension.create())
    val body = HtmlRenderer.builder().extensions(ext).escapeHtml(true).build().render(Parser.builder().extensions(ext).build().parse(md))
    return readerHtml(body, p, scale)
}

fun readerHtml(body: String, p: Paper, scale: Float = 1f): String {
    fun hex(c: Color) = "#%06X".format(0xFFFFFF and android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()))
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>
        @font-face { font-family: Literata; src: url('font/literata.ttf'); }
        @font-face { font-family: Mono; src: url('font/jetbrains_mono.ttf'); }
        body { font-family: Literata, serif; font-size: ${17 * scale}px; line-height: 1.6; color: ${hex(p.ink)}; background: transparent; margin: 0 22px 60px; overflow-wrap: anywhere; }
        h1, h2, h3 { line-height: 1.25; margin: 1.4em 0 .5em; } h1 { font-size: 1.7em } h2 { font-size: 1.35em } h3 { font-size: 1.15em }
        a { color: ${hex(p.accent)}; } img { max-width: 100%; height: auto; }
        code, pre { font-family: Mono, monospace; font-size: .85em; background: ${hex(p.rule)}; border-radius: 6px; }
        code { padding: 1px 5px; } pre { padding: 12px; overflow-x: auto; } pre code { background: none; padding: 0 }
        blockquote { margin: 1em 0; padding-left: 14px; border-left: 3px solid ${hex(p.rule)}; color: ${hex(p.muted)}; }
        table { border-collapse: collapse; display: block; overflow-x: auto; } td, th { border: 1px solid ${hex(p.rule)}; padding: 6px 10px; }
        hr { border: 0; border-top: 1px solid ${hex(p.rule)}; }
        </style></head><body>$body</body></html>"""
}

/** View | Edit, for files that read one way and are written another (Markdown, HTML). */
@Composable
private fun ModeSwitch(editing: Boolean, onView: () -> Unit, onEdit: () -> Unit) {
    val c = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier.padding(end = 4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.surfaceContainerHighest).padding(3.dp),
    ) {
        listOf(false to "View", true to "Edit").forEach { (mode, label) ->
            val on = editing == mode
            Text(
                label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant,
                modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(if (on) c.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { if (!on) { if (mode) onEdit() else onView() } }.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

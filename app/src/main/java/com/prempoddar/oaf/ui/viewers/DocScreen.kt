package com.prempoddar.oaf.ui.viewers

import com.prempoddar.oaf.export.PrintHtml
import com.prempoddar.oaf.export.Printing
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.prempoddar.oaf.doc.Block
import com.prempoddar.oaf.doc.Converters
import com.prempoddar.oaf.doc.DocBody
import com.prempoddar.oaf.doc.DocEditor
import com.prempoddar.oaf.doc.DocStyles
import com.prempoddar.oaf.doc.Docx
import com.prempoddar.oaf.doc.DocxDoc
import com.prempoddar.oaf.doc.Numbering
import com.prempoddar.oaf.doc.Para
import com.prempoddar.oaf.doc.Run
import com.prempoddar.oaf.doc.RunStyle
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.Inter
import com.prempoddar.oaf.ui.Literata
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.Mono
import com.prempoddar.oaf.ui.Paper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** A document ready to show: [native] when it's a .docx that saves in place. */
internal class LoadedDoc(val doc: DocxDoc, val body: DocBody, val native: Boolean)

private sealed interface DocState {
    class Ready(val loaded: LoadedDoc) : DocState
    class Failed(val reason: String) : DocState
}

private fun loadDoc(context: Context, source: DocSource): LoadedDoc {
    val file = source.local(context)
    if (source.kind == FileKind.DOCX) return Docx.read(file).let { LoadedDoc(it, it.body, true) }
    val body = when (source.kind) {
        FileKind.DOC -> Converters.doc(file)
        FileKind.ODT -> Converters.odt(file)
        FileKind.RTF -> Converters.rtf(file)
        else -> error("OAF can't read ${source.kind.label} documents yet")
    }
    return LoadedDoc(Docx.blank(), body, false)
}

private const val SENTINEL = '​'

@Composable
fun DocScreen(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val state by produceState<DocState?>(null, source) {
        value = withContext(Dispatchers.IO) { runCatching { DocState.Ready(loadDoc(context, source)) }.getOrElse { DocState.Failed(it.message ?: "Couldn't read this document") } }
    }
    when (val s = state) {
        is DocState.Ready -> DocBodyScreen(source, s.loaded, onBack)
        else -> ViewerScaffold(title = source.name, subtitle = source.kind.label, chrome = rememberChrome(), autoHide = false, onBack = onBack) {
            if (s is DocState.Failed) Text(s.reason, color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = 100.dp))
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

/** How a document looks on this screen: Word sizes scaled so body text reads at a comfortable size. */
private class Look(val styles: DocStyles, val paper: Paper, val scale: Float, val labels: Map<Long, String>) {
    private val bodySp = 17f * scale

    fun family(font: String?): FontFamily {
        val f = font?.lowercase() ?: return Literata
        return when {
            listOf("mono", "courier", "consolas", "menlo", "code").any { it in f } -> Mono
            listOf("calibri", "arial", "helvetica", "segoe", "roboto", "open sans", "verdana", "tahoma", "aptos", "inter", "sans", "lato", "montserrat").any { it in f } -> Inter
            else -> Literata
        }
    }

    fun sizeSp(pt: Float?) = bodySp * ((pt ?: styles.baseSize) / styles.baseSize).coerceIn(0.6f, 3.2f)

    fun colour(rgb: Int?): Color? {
        val c = Color(0xFF000000.toInt() or (rgb ?: return null))
        // Dark text from the page would vanish on a dark screen.
        return if (paper.dark && c.luminance() < 0.25f) null else c
    }

    fun span(s: RunStyle): SpanStyle {
        val decoration = listOfNotNull(TextDecoration.Underline.takeIf { s.underline == true }, TextDecoration.LineThrough.takeIf { s.strike == true })
        val small = s.vert != null
        return SpanStyle(
            fontFamily = family(s.font),
            fontWeight = if (s.bold == true) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (s.italic == true) FontStyle.Italic else FontStyle.Normal,
            fontSize = (sizeSp(s.size) * if (small) 0.7f else 1f).sp,
            color = colour(s.color) ?: Color.Unspecified,
            background = s.highlight?.let { highlight(it) } ?: Color.Unspecified,
            textDecoration = if (decoration.isEmpty()) null else TextDecoration.combine(decoration),
            baselineShift = when (s.vert) { "superscript" -> BaselineShift.Superscript; "subscript" -> BaselineShift.Subscript; else -> null },
        )
    }

    private fun highlight(name: String): Color {
        val c = when (name) {
            "yellow" -> Color(0xFFFFFF00); "green" -> Color(0xFF00FF00); "cyan" -> Color(0xFF00FFFF); "magenta" -> Color(0xFFFF00FF)
            "blue" -> Color(0xFF0000FF); "red" -> Color(0xFFFF0000); "darkBlue" -> Color(0xFF000080); "darkCyan" -> Color(0xFF008080)
            "darkGreen" -> Color(0xFF008000); "darkMagenta" -> Color(0xFF800080); "darkRed" -> Color(0xFF800000); "darkYellow" -> Color(0xFF808000)
            "darkGray" -> Color(0xFF808080); "lightGray" -> Color(0xFFC0C0C0); "black" -> Color.Black; else -> Color(0xFFFFFF00)
        }
        return c.copy(alpha = if (paper.dark) 0.4f else 0.55f)
    }

    /** The paragraph's text with its formatting; [offset] leaves room for the editor's sentinel. */
    fun annotated(p: Para, offset: Int = 0, marks: List<IntRange> = emptyList(), links: Boolean = false): AnnotatedString = buildAnnotatedString {
        if (offset > 0) append(SENTINEL.toString().repeat(offset))
        for (r in p.runs) {
            val start = length
            append(r.text)
            if (r.isObject) continue
            addStyle(span(styles.effective(p, r)), start, length)
            if (r.linkTarget != null) {
                addStyle(SpanStyle(color = paper.accent, textDecoration = TextDecoration.Underline), start, length)
                if (links && !r.linkTarget.startsWith("#")) addLink(LinkAnnotation.Url(r.linkTarget, TextLinkStyles(SpanStyle(color = paper.accent))), start, length)
            }
        }
        marks.forEach { m -> addStyle(SpanStyle(background = paper.accent.copy(alpha = 0.3f)), m.first + offset, m.last + 1 + offset) }
    }

    fun textStyle(p: Para): TextStyle {
        val base = styles.paraRun(p.props.styleId)
        val heading = styles.headingLevel(p.props.styleId) != null
        return TextStyle(
            color = paper.ink,
            fontFamily = family(base.font),
            fontSize = sizeSp(base.size).sp,
            lineHeight = if (heading) 1.25.em else 1.55.em,
            textAlign = when (styles.align(p)) { "center" -> TextAlign.Center; "right", "end" -> TextAlign.End; "both", "distribute" -> TextAlign.Justify; else -> TextAlign.Start },
        )
    }

    fun spaceAbove(p: Para): Dp = when (styles.headingLevel(p.props.styleId)) { null -> 0.dp; 0 -> 8.dp; 1 -> 22.dp; else -> 14.dp }
    fun spaceBelow(p: Para): Dp = if (labels.containsKey(p.id)) 4.dp else 12.dp
    fun indent(p: Para): Dp = if (labels.containsKey(p.id)) (styles.numbering(p)?.second ?: 0).coerceIn(0, 6).times(22).dp else 0.dp
}

@Composable
private fun DocBodyScreen(source: DocSource, loaded: LoadedDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val doc = loaded.doc
    val editor = remember { DocEditor(loaded.body) }
    var tick by remember { mutableIntStateOf(0) }
    fun act(block: DocEditor.() -> Unit) { editor.block(); tick = editor.version }

    var editing by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var menu by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hit by remember { mutableIntStateOf(0) }
    var contents by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // The paragraph being typed in, its selection, and formatting chosen for the next typed text.
    var focused by remember { mutableStateOf<Long?>(null) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var pending by remember { mutableStateOf<RunStyle?>(null) }
    var caretTarget by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val list = rememberLazyListState()
    val chrome = rememberChrome()

    val body = remember(tick) { editor.body }
    val labels = remember(body) { doc.numbering.labels(body.paragraphs, doc.styles) }
    val look = remember(labels, scale, paper) { Look(doc.styles, paper, scale, labels) }
    val words = remember(body) { body.paragraphs.sumOf { p -> p.text.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } } } }
    val matches = remember(body, query) {
        if (query.length < 2) emptyList() else body.paragraphs.flatMap { p ->
            Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(p.text).map { p.id to it.range }.toList()
        }
    }
    fun blockOf(id: Long) = body.blocks.indexOfFirst { b -> when (b) { is Block.P -> b.para.id == id; is Block.T -> b.rows.flatten().any { c -> c.paras.any { it.id == id } } } }
    fun goToMatch(i: Int) {
        if (matches.isEmpty()) return
        hit = ((i % matches.size) + matches.size) % matches.size
        scope.launch { list.animateScrollToItem(blockOf(matches[hit].first).coerceAtLeast(0)) }
    }

    val baseName = source.name.substringBeforeLast('.')
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            saving = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val target = if (loaded.native) doc else Docx.blank()
                    val bytes = ByteArrayOutputStream()
                    val saved = Docx.write(target, editor.body, bytes)
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes.toByteArray()) }
                    saved.takeIf { loaded.native }
                }
            }
            saving = false
            result.onSuccess { saved -> if (saved != null) act { saved(saved) } else { editor.saved(editor.body); tick = editor.version } }
            Toast.makeText(context, if (result.isSuccess) "Saved as Word document" else "Couldn't save", Toast.LENGTH_SHORT).show()
        }
    }

    fun save(then: () -> Unit = {}) {
        if (!loaded.native) { saveAs.launch("$baseName.docx"); return }
        scope.launch {
            saving = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (!source.canWrite(context)) error("read-only")
                    // Built in memory first: the original file is the one being replaced.
                    val bytes = ByteArrayOutputStream()
                    val saved = Docx.write(doc, editor.body, bytes)
                    source.openOutput(context).use { it.write(bytes.toByteArray()) }
                    saved
                }
            }
            saving = false
            result.onSuccess { saved -> act { saved(saved) }; Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); then() }
                .onFailure { saveAs.launch(source.name) }
        }
    }

    val dirty = editor.unsaved > 0
    BackHandler(enabled = editing || finding || dirty) {
        when {
            finding -> { finding = false; query = "" }
            editing -> { editing = false; focus.clearFocus() }
            else -> confirmLeave = true
        }
    }

    ViewerScaffold(
        title = (if (dirty) "• " else "") + source.name,
        subtitle = source.kind.label + " · " + "%,d words".format(words) + if (!loaded.native) " · saves as .docx" else "",
        chrome = chrome, autoHide = !editing && !finding,
        onBack = { if (dirty) confirmLeave = true else onBack() },
        actions = {
            if (editing) {
                IconButton(enabled = editor.canUndo, onClick = { act { undo() } }) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
                IconButton(enabled = editor.canRedo, onClick = { act { redo() } }) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
            } else {
                IconButton(onClick = { editing = true; finding = false; chrome.show() }) { Icon(Icons.Rounded.Edit, "Edit") }
            }
            IconButton(enabled = dirty && !saving, onClick = { save() }) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Save, "Save")
            }
            if (editing) IconButton(onClick = { editing = false; focus.clearFocus() }) { Icon(Icons.Rounded.Check, "Done editing") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (!editing) DropdownMenuItem(text = { Text("Find…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = { menu = false; finding = true; chrome.show() })
                    DropdownMenuItem(text = { Text("Contents") }, onClick = { menu = false; contents = true })
                    DropdownMenuItem(text = { Text("Larger text") }, onClick = { menu = false; scale = (scale + 0.12f).coerceAtMost(2f) })
                    DropdownMenuItem(text = { Text("Smaller text") }, onClick = { menu = false; scale = (scale - 0.12f).coerceAtLeast(0.6f) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(if (loaded.native) "Save a copy…" else "Save as Word document…") }, onClick = { menu = false; saveAs.launch("$baseName.docx") })
                    DropdownMenuItem(text = { Text("Print or save as PDF…") }, onClick = { menu = false; Printing.printHtml(context, source.name, PrintHtml.document(source.name, editor.body, doc.styles, doc.numbering) { doc.bytes(it) }) })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareSource(context, source) })
                    DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; openElsewhere(context, source) })
                }
            }
        },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val items = body.blocks
                val content: @Composable () -> Unit = {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().then(if (!editing) Modifier.clickable(interactionSource = null, indication = null) { chrome.toggle() } else Modifier),
                        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = insets.calculateTopPadding() + 76.dp + if (finding) 56.dp else 0.dp, bottom = 120.dp),
                    ) {
                        itemsIndexed(items, key = { i, b -> if (b is Block.P) b.para.id else -i - 1L }) { _, b ->
                            when (b) {
                                is Block.P -> ParagraphItem(b.para, doc, look, editing, matches.filter { it.first == b.para.id }.map { it.second }, focused, caretTarget,
                                    onFocus = { id -> focused = id; pending = null },
                                    onSelection = { selection = it; pending = null },
                                    onChange = { id, text, sel -> selection = sel; val target = editor.change(id, text, pending); tick = editor.version; if (target != null) caretTarget = target },
                                    onBackspaceAtStart = { id -> val target = editor.backspaceAtStart(id); tick = editor.version; if (target != null) caretTarget = target },
                                    onCaretPlaced = { caretTarget = null })
                                is Block.T -> TableItem(b, look) { p ->
                                    ParagraphItem(p, doc, look, editing, matches.filter { it.first == p.id }.map { it.second }, focused, caretTarget,
                                        onFocus = { id -> focused = id; pending = null },
                                        onSelection = { selection = it; pending = null },
                                        onChange = { id, text, sel -> selection = sel; val target = editor.change(id, text, pending); tick = editor.version; if (target != null) caretTarget = target },
                                        onBackspaceAtStart = { id -> val target = editor.backspaceAtStart(id); tick = editor.version; if (target != null) caretTarget = target },
                                        onCaretPlaced = { caretTarget = null }, compact = true)
                                }
                            }
                        }
                    }
                }
                if (editing) content() else SelectionContainer { content() }
                if (finding) {
                    FindStrip(query, matches.size, hit, top = insets.calculateTopPadding() + 64.dp,
                        onQuery = { query = it; hit = 0; if (it.length >= 2) scope.launch { kotlinx.coroutines.delay(10); goToMatch(0) } },
                        onNext = { goToMatch(hit + 1) }, onPrevious = { goToMatch(hit - 1) }, onClose = { finding = false; query = "" })
                }
            }
            if (editing) {
                val para = focused?.let { body.find(it) }
                FormatToolbar(
                    para = para, styles = doc.styles, numbering = doc.numbering, selection = selection, pending = pending,
                    onRun = { change ->
                        val p = para ?: return@FormatToolbar
                        if (selection.collapsed) pending = change(pending ?: p.styleAt(selection.start))
                        else act { format(p.id, selection.min, selection.max, change) }
                    },
                    onParagraph = { change -> para?.let { p -> act { setParagraph(p.id, change) } } },
                    onList = { bullet -> para?.let { p -> act { toggleList(p.id, bullet, doc.numbering) } } },
                    onIndent = { d -> para?.let { p -> act { indent(p.id, d) } } },
                )
            }
        }
    }

    if (contents) {
        val headings = body.paragraphs.mapNotNull { p -> doc.styles.headingLevel(p.props.styleId)?.let { lvl -> Triple(p, lvl, p.text.replace(Para.OBJ.toString(), "").trim()) } }.filter { it.third.isNotEmpty() }
        AlertDialog(
            onDismissRequest = { contents = false },
            title = { Text("Contents") },
            text = {
                if (headings.isEmpty()) Text("This document has no headings.", color = paper.muted)
                else LazyColumn {
                    itemsIndexed(headings) { _, (p, lvl, text) ->
                        Text(
                            text, style = if (lvl <= 1) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, maxLines = 2,
                            modifier = Modifier.fillMaxWidth().clickable {
                                contents = false
                                scope.launch { list.animateScrollToItem(blockOf(p.id).coerceAtLeast(0)) }
                            }.padding(start = 14.dp * (lvl.coerceAtLeast(1) - 1), top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { contents = false }) { Text("Close") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Save changes?") },
            text = { Text(if (loaded.native) "You've edited ${source.name}." else "${source.kind.label} files are saved as a new Word document.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; save(then = onBack) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Discard") } },
        )
    }
}

@Composable
private fun FindStrip(query: String, count: Int, hit: Int, top: Dp, onQuery: (String) -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onClose: () -> Unit) {
    val paper = LocalPaper.current
    Surface(color = paper.chrome, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(top = top)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                query, onQuery, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = paper.ink), cursorBrush = SolidColor(paper.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                decorationBox = { inner -> if (query.isEmpty()) Text("Find in document", color = paper.faint); inner() },
            )
            Text(if (count == 0) "0" else "${hit + 1}/$count", style = MaterialTheme.typography.labelMedium, color = paper.muted)
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous") }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.KeyboardArrowDown, "Next") }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
        }
    }
}

@Composable
private fun ParagraphItem(
    p: Para, doc: DocxDoc, look: Look, editing: Boolean, marks: List<IntRange>, focused: Long?, caretTarget: Pair<Long, Int>?,
    onFocus: (Long) -> Unit, onSelection: (TextRange) -> Unit, onChange: (Long, String, TextRange) -> Unit,
    onBackspaceAtStart: (Long) -> Unit, onCaretPlaced: () -> Unit, compact: Boolean = false,
) {
    val label = look.labels[p.id]
    val visible = p.text.any { it != Para.OBJ }
    Column(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else look.spaceAbove(p), bottom = if (compact) 4.dp else look.spaceBelow(p))) {
        p.images.forEach { DocImage(doc, it, look) }
        if (visible || editing || p.images.isEmpty()) {
            Row(Modifier.padding(start = look.indent(p))) {
                if (label != null) Text(label, style = look.textStyle(p).copy(textAlign = TextAlign.End), modifier = Modifier.width((28 + 8 * label.length.coerceAtMost(5)).dp.coerceAtMost(64.dp)).padding(end = 8.dp))
                if (editing) ParaField(p, look, focused == p.id, caretTarget, onFocus, onSelection, onChange, onBackspaceAtStart, onCaretPlaced)
                else Text(look.annotated(p, marks = marks, links = true), style = look.textStyle(p), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One paragraph as a text field. An invisible first character catches backspace at the very start. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ParaField(
    p: Para, look: Look, isFocused: Boolean, caretTarget: Pair<Long, Int>?,
    onFocus: (Long) -> Unit, onSelection: (TextRange) -> Unit, onChange: (Long, String, TextRange) -> Unit,
    onBackspaceAtStart: (Long) -> Unit, onCaretPlaced: () -> Unit,
) {
    val paper = LocalPaper.current
    val requester = remember { FocusRequester() }
    val model = SENTINEL + p.text
    var value by remember(p.id) { mutableStateOf(TextFieldValue(model, TextRange(model.length))) }
    if (value.text != model) {
        value = value.copy(text = model, selection = TextRange(value.selection.start.coerceIn(1, model.length), value.selection.end.coerceIn(1, model.length)))
    }
    LaunchedEffect(caretTarget) {
        if (caretTarget?.first == p.id) {
            val at = (caretTarget.second + 1).coerceIn(1, model.length)
            value = value.copy(selection = TextRange(at))
            requester.requestFocus()
            onCaretPlaced()
        }
    }
    val transformation = remember(p, look) {
        VisualTransformation { text ->
            TransformedText(if (text.text == SENTINEL + p.text) look.annotated(p, offset = 1) else AnnotatedString(text.text), OffsetMapping.Identity)
        }
    }
    BasicTextField(
        value = value,
        onValueChange = { v ->
            val s = v.text.indexOf(SENTINEL)
            when {
                s < 0 -> onBackspaceAtStart(p.id)
                else -> {
                    // Text typed before the sentinel is moved after it.
                    val text = v.text.removeRange(s, s + 1)
                    val sel = TextRange((v.selection.start - 1).coerceAtLeast(0), (v.selection.end - 1).coerceAtLeast(0))
                    if (text != p.text) onChange(p.id, text, sel) else onSelection(sel)
                    if (!text.contains('\n')) value = TextFieldValue(SENTINEL + text, TextRange(sel.start + 1, sel.end + 1), v.composition?.let { TextRange((it.start - 1).coerceAtLeast(1), (it.end - 1).coerceAtLeast(1)) })
                }
            }
        },
        textStyle = look.textStyle(p),
        cursorBrush = SolidColor(paper.accent),
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
        modifier = Modifier.weight(1f).focusRequester(requester).onFocusChanged { if (it.isFocused) onFocus(p.id) }
            .then(if (isFocused) Modifier.background(paper.accent.copy(alpha = 0.05f), RoundedCornerShape(4.dp)) else Modifier),
    )
}

@Composable
private fun DocImage(doc: DocxDoc, run: Run, look: Look) {
    val bitmap by produceState<Bitmap?>(null, run.image) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = doc.bytes(run.image!!) ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1600) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    val wantedWidth = run.imageSize?.first?.let { (it * 1.33f * look.scale).dp }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        val width = wantedWidth?.coerceAtMost(maxWidth) ?: maxWidth
        val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: run.imageSize?.let { it.first / it.second } ?: 1.5f
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.width(width).aspectRatio(ratio))
        else Box(Modifier.width(width).aspectRatio(ratio).background(look.paper.rule.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
    }
}

@Composable
private fun TableItem(t: Block.T, look: Look, cell: @Composable (Para) -> Unit) {
    val paper = look.paper
    val cols = t.rows.maxOfOrNull { it.size } ?: 0
    // Twips to dp, generous enough that narrow phones scroll sideways rather than squash.
    val widths = (0 until cols).map { i -> (t.widths.getOrNull(i)?.takeIf { it > 0 }?.let { it / 15f * 0.9f * look.scale } ?: 140f).coerceIn(72f, 360f).dp }
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).horizontalScroll(rememberScrollState())) {
        Column(Modifier.border(1.dp, paper.rule)) {
            t.rows.forEachIndexed { r, row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { c, cellData ->
                        Column(
                            Modifier.width(widths.getOrElse(c) { 140.dp }).fillMaxHeight().border(0.5.dp, paper.rule)
                                .background(if (r == 0) paper.rule.copy(alpha = 0.25f) else Color.Transparent).padding(horizontal = 8.dp, vertical = 6.dp),
                        ) { cellData.paras.forEach { cell(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatToolbar(
    para: Para?, styles: DocStyles, numbering: Numbering, selection: TextRange, pending: RunStyle?,
    onRun: ((RunStyle) -> RunStyle) -> Unit, onParagraph: ((com.prempoddar.oaf.doc.ParaProps) -> com.prempoddar.oaf.doc.ParaProps) -> Unit,
    onList: (Boolean) -> Unit, onIndent: (Int) -> Unit,
) {
    val paper = LocalPaper.current
    var open by remember { mutableStateOf<String?>(null) }
    // What's on at the caret: the run's look in context, with any formatting chosen for typing.
    val effective = para?.let { p ->
        val run = p.runs.firstOrNull { !it.isObject } ?: Run("")
        var pos = 0
        val at = p.runs.firstOrNull { r -> val hitIt = !r.isObject && selection.min in pos until pos + r.text.length; pos += r.text.length; hitIt } ?: run
        styles.effective(p, at.copy(style = pending ?: at.style))
    } ?: RunStyle()
    val enabled = para != null

    @Composable
    fun Btn(icon: ImageVector, label: String, on: Boolean = false, click: () -> Unit) {
        IconButton(enabled = enabled, onClick = click) { Icon(icon, label, tint = if (on) paper.accent else if (enabled) paper.ink else paper.faint) }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Btn(Icons.Rounded.Title, "Paragraph style", on = para?.props?.styleId != null) { open = "style" }
                DropdownMenu(open == "style", onDismissRequest = { open = null }) {
                    styles.choices().forEach { (id, name) ->
                        val lvl = styles.headingLevel(id)
                        DropdownMenuItem(
                            text = { Text(name, style = when (lvl) { 0 -> MaterialTheme.typography.headlineSmall; 1 -> MaterialTheme.typography.titleLarge; 2 -> MaterialTheme.typography.titleMedium; 3 -> MaterialTheme.typography.titleSmall; else -> MaterialTheme.typography.bodyLarge }) },
                            onClick = { open = null; onParagraph { it.copy(styleId = id) } },
                        )
                    }
                }
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            Btn(Icons.Rounded.FormatBold, "Bold", effective.bold == true) { val v = effective.bold != true; onRun { it.copy(bold = v) } }
            Btn(Icons.Rounded.FormatItalic, "Italic", effective.italic == true) { val v = effective.italic != true; onRun { it.copy(italic = v) } }
            Btn(Icons.Rounded.FormatUnderlined, "Underline", effective.underline == true) { val v = effective.underline != true; onRun { it.copy(underline = v) } }
            Btn(Icons.Rounded.FormatStrikethrough, "Strikethrough", effective.strike == true) { val v = effective.strike != true; onRun { it.copy(strike = v) } }
            Box {
                Btn(Icons.Rounded.FormatColorText, "Colour and highlight", effective.color != null || effective.highlight != null) { open = "colour" }
                DropdownMenu(open == "colour", onDismissRequest = { open = null }) {
                    Text("Text colour", style = MaterialTheme.typography.labelMedium, color = paper.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    DocSwatches(listOf(0xC00000, 0xE36C09, 0x7F6000, 0x00B050, 0x2F5D8C, 0x7030A0, 0x7F7F7F)) { c -> open = null; onRun { it.copy(color = c) } }
                    Text("Highlight", style = MaterialTheme.typography.labelMedium, color = paper.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    val marks = listOf("yellow" to 0xFFFF00, "green" to 0x00FF00, "cyan" to 0x00FFFF, "magenta" to 0xFF00FF, "lightGray" to 0xC0C0C0)
                    DocSwatches(marks.map { it.second }) { c -> open = null; onRun { s -> s.copy(highlight = marks.firstOrNull { it.second == c }?.first) } }
                }
            }
            Box {
                TextButton(enabled = enabled, onClick = { open = "size" }) { Text("${(effective.size ?: styles.baseSize).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}pt", color = paper.ink) }
                DropdownMenu(open == "size", onDismissRequest = { open = null }) {
                    listOf(8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 36f).forEach { pt ->
                        DropdownMenuItem(text = { Text("${pt.toInt()} pt") }, onClick = { open = null; onRun { it.copy(size = pt) } })
                    }
                }
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            val align = para?.let { styles.align(it) }
            val alignIcon = when (align) { "center" -> Icons.Rounded.FormatAlignCenter; "right", "end" -> Icons.AutoMirrored.Rounded.FormatAlignRight; "both" -> Icons.Rounded.FormatAlignJustify; else -> Icons.AutoMirrored.Rounded.FormatAlignLeft }
            Btn(alignIcon, "Alignment", align != null && align != "left") {
                val next = when (align) { null, "left", "start" -> "center"; "center" -> "right"; "right", "end" -> "both"; else -> "left" }
                onParagraph { it.copy(align = next) }
            }
            val listId = para?.let { styles.numbering(it)?.first }
            Btn(Icons.AutoMirrored.Rounded.FormatListBulleted, "Bulleted list", listId != null && numbering.isBullet(listId)) { onList(true) }
            Btn(Icons.Rounded.FormatListNumbered, "Numbered list", listId != null && !numbering.isBullet(listId)) { onList(false) }
            Btn(Icons.AutoMirrored.Rounded.FormatIndentDecrease, "Decrease indent") { onIndent(-1) }
            Btn(Icons.AutoMirrored.Rounded.FormatIndentIncrease, "Increase indent") { onIndent(1) }
        }
    }
}

@Composable
private fun DocSwatches(colours: List<Int>, onPick: (Int?) -> Unit) {
    val paper = LocalPaper.current
    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        colours.forEach { c ->
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or c)).border(1.dp, paper.rule, CircleShape).clickable { onPick(c) })
        }
        TextButton(onClick = { onPick(null) }) { Text("None") }
    }
}


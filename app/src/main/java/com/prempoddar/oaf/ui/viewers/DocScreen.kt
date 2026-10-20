package com.prempoddar.oaf.ui.viewers

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextIndent
import com.prempoddar.oaf.doc.DocLayout
import com.prempoddar.oaf.doc.ParaLayout
import com.prempoddar.oaf.doc.PageSetup
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
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.layout.onSizeChanged
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
import com.prempoddar.oaf.ui.nightPage
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

private val WORD_LINK = Color(0xFF0563C1)

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

/**
 * How a document looks on this screen. With a [page], it's drawn as the document itself: real
 * sizes in points, spacing, indents and colours, black on white paper (the page view). Without
 * one, it's reflowed for reading: sizes scaled so body text reads comfortably.
 */
private class Look(val styles: DocStyles, val paper: Paper, val scale: Float, val labels: Map<Long, String>, val page: PageSetup? = null, val numbering: Numbering? = null) {
    private val bodySp = 17f * scale
    val faithful get() = page != null
    private val layouts = HashMap<Long, ParaLayout>()
    fun layout(p: Para): ParaLayout = layouts.getOrPut(p.id) { styles.layout(p, numbering) }

    fun family(font: String?): FontFamily {
        if (faithful) {
            val f = font?.lowercase() ?: return FontFamily.SansSerif
            return when {
                listOf("mono", "courier", "consolas", "menlo", "code").any { it in f } -> FontFamily.Monospace
                listOf("times", "cambria", "georgia", "garamond", "serif", "book", "palatino", "baskerville", "constantia", "minion", "literata").any { it in f } && "sans" !in f -> FontFamily.Serif
                else -> FontFamily.SansSerif
            }
        }
        val f = font?.lowercase() ?: return Literata
        return when {
            listOf("mono", "courier", "consolas", "menlo", "code").any { it in f } -> Mono
            listOf("calibri", "arial", "helvetica", "segoe", "roboto", "open sans", "verdana", "tahoma", "aptos", "inter", "sans", "lato", "montserrat").any { it in f } -> Inter
            else -> Literata
        }
    }

    fun sizeSp(pt: Float?) = if (faithful) (pt ?: styles.baseSize) else bodySp * ((pt ?: styles.baseSize) / styles.baseSize).coerceIn(0.6f, 3.2f)

    fun colour(rgb: Int?): Color? {
        val c = Color(0xFF000000.toInt() or (rgb ?: return null))
        if (faithful) return c
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
        return c.copy(alpha = if (faithful) 1f else if (paper.dark) 0.4f else 0.55f)
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
                val linkColour = if (faithful) WORD_LINK else paper.accent
                addStyle(SpanStyle(color = linkColour, textDecoration = TextDecoration.Underline), start, length)
                if (links && !r.linkTarget.startsWith("#")) addLink(LinkAnnotation.Url(r.linkTarget, TextLinkStyles(SpanStyle(color = linkColour))), start, length)
            }
        }
        marks.forEach { m -> addStyle(SpanStyle(background = paper.accent.copy(alpha = 0.3f)), m.first + offset, m.last + 1 + offset) }
    }

    fun textStyle(p: Para): TextStyle {
        val base = styles.paraRun(p.props.styleId)
        val heading = styles.headingLevel(p.props.styleId) != null
        if (faithful) {
            val l = layout(p)
            val size = base.size ?: styles.baseSize
            // Word's "single" line is about 1.17 times the font size for common fonts.
            val single = 1.17f
            val lineHeight = when {
                l.lineExact != null -> l.lineExact.sp
                l.lineAtLeast != null -> (maxOf(single * size, l.lineAtLeast) / size).em
                else -> (single * (l.lineMultiple ?: 1f)).em
            }
            val indent = when {
                labels.containsKey(p.id) -> null
                l.firstLine > 0 -> TextIndent(firstLine = l.firstLine.sp)
                l.firstLine < 0 -> TextIndent(firstLine = 0.sp, restLine = (-l.firstLine).sp)
                else -> null
            }
            return TextStyle(
                color = Color.Black,
                fontFamily = family(base.font),
                fontSize = size.sp,
                lineHeight = lineHeight,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
                textIndent = indent,
                textAlign = when (styles.align(p)) { "center" -> TextAlign.Center; "right", "end" -> TextAlign.End; "both", "distribute" -> TextAlign.Justify; else -> TextAlign.Start },
            )
        }
        return TextStyle(
            color = paper.ink,
            fontFamily = family(base.font),
            fontSize = sizeSp(base.size).sp,
            lineHeight = if (heading) 1.25.em else 1.55.em,
            textAlign = when (styles.align(p)) { "center" -> TextAlign.Center; "right", "end" -> TextAlign.End; "both", "distribute" -> TextAlign.Justify; else -> TextAlign.Start },
        )
    }

    fun spaceAbove(p: Para): Dp = if (faithful) layout(p).spaceBefore.dp else when (styles.headingLevel(p.props.styleId)) { null -> 0.dp; 0 -> 8.dp; 1 -> 22.dp; else -> 14.dp }
    fun spaceBelow(p: Para): Dp = if (faithful) layout(p).spaceAfter.dp else if (labels.containsKey(p.id)) 4.dp else 12.dp
    fun indent(p: Para): Dp = if (faithful) layout(p).let { (it.left + minOf(it.firstLine, 0f)).coerceAtLeast(0f).dp } else if (labels.containsKey(p.id)) (styles.numbering(p)?.second ?: 0).coerceIn(0, 6).times(22).dp else 0.dp
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
    // Page view draws the document as it prints; reading view reflows it for the screen.
    var pageView by rememberSaveable { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var menu by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hit by remember { mutableIntStateOf(0) }
    var contents by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var newTable by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    // The paragraph being typed in, its selection, and formatting chosen for the next typed text.
    var focused by remember { mutableStateOf<Long?>(null) }
    var selection by remember { mutableStateOf(TextRange.Zero) }
    var pending by remember { mutableStateOf<RunStyle?>(null) }
    var caretTarget by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val list = rememberLazyListState()
    val chrome = rememberChrome()

    val body = remember(tick) { editor.body }
    val labels = remember(body) { doc.numbering.labels(body.paragraphs, doc.styles) }
    val look = remember(labels, scale, paper, pageView) { Look(doc.styles, paper, scale, labels, if (pageView) doc.page else null, doc.numbering) }
    // Blocks that begin a new sheet of paper in page view.
    val pageStarts = remember(body) {
        val starts = HashSet<Int>()
        body.blocks.forEachIndexed { i, b ->
            val prev = body.blocks.getOrNull(i - 1) as? Block.P
            if (prev != null && (prev.para.runs.any(DocLayout::isPageBreak) || DocLayout.endsSection(prev.para))) starts += i
            if (b is Block.P && i > 0 && doc.styles.layout(b.para).pageBreakBefore) starts += i
        }
        starts
    }
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
                    if (editing) {
                        DropdownMenuItem(text = { Text("Replace…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = { menu = false; replacing = true })
                        val here = focused
                        DropdownMenuItem(text = { Text("Insert table…") }, enabled = here != null, onClick = { menu = false; newTable = true })
                        if (here != null && editor.tableOf(here) != null) {
                            DropdownMenuItem(text = { Text("Add row above") }, onClick = { menu = false; editor.addRow(here, below = false)?.let { caretTarget = it to 0 }; tick = editor.version })
                            DropdownMenuItem(text = { Text("Add row below") }, onClick = { menu = false; editor.addRow(here, below = true)?.let { caretTarget = it to 0 }; tick = editor.version })
                            DropdownMenuItem(text = { Text("Delete row") }, onClick = { menu = false; focused = null; act { deleteRow(here) } })
                            DropdownMenuItem(text = { Text("Delete table") }, onClick = { menu = false; focused = null; act { deleteTable(here) } })
                        }
                        HorizontalDivider()
                    }
                    DropdownMenuItem(text = { Text("Contents") }, onClick = { menu = false; contents = true })
                    DropdownMenuItem(
                        text = { Text(if (pageView) "Reading view" else "Page view") },
                        leadingIcon = { Icon(if (pageView) Icons.AutoMirrored.Rounded.Notes else Icons.Rounded.Description, null) },
                        onClick = { menu = false; pageView = !pageView; zoom = 1f },
                    )
                    if (pageView) {
                        DropdownMenuItem(text = { Text("Zoom in") }, onClick = { menu = false; zoom = (zoom * 1.25f).coerceAtMost(4f) })
                        DropdownMenuItem(text = { Text("Fit width") }, enabled = zoom != 1f, onClick = { menu = false; zoom = 1f })
                    } else {
                        DropdownMenuItem(text = { Text("Larger text") }, onClick = { menu = false; scale = (scale + 0.12f).coerceAtMost(2f) })
                        DropdownMenuItem(text = { Text("Smaller text") }, onClick = { menu = false; scale = (scale - 0.12f).coerceAtLeast(0.6f) })
                    }
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
                val blockContent: @Composable (Block) -> Unit = { b ->
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
                val topPad = insets.calculateTopPadding() + 76.dp + if (finding) 56.dp else 0.dp
                val content: @Composable () -> Unit = {
                    val page = look.page
                    if (page == null) LazyColumn(
                        state = list,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().then(if (!editing) Modifier.clickable(interactionSource = null, indication = null) { chrome.toggle() } else Modifier),
                        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = topPad, bottom = 120.dp),
                    ) {
                        itemsIndexed(items, key = { i, b -> if (b is Block.P) b.para.id else -i - 1L }) { _, b -> blockContent(b) }
                    } else PageColumn(page, items.size, pageStarts, zoom, { zoom = it }, list, topPad, editing, onTap = { chrome.toggle() }) { i -> blockContent(items[i]) }
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

    if (newTable) {
        var rows by remember { mutableStateOf("3") }
        var cols by remember { mutableStateOf("3") }
        val r = rows.toIntOrNull()?.takeIf { it in 1..100 }
        val c = cols.toIntOrNull()?.takeIf { it in 1..20 }
        AlertDialog(
            onDismissRequest = { newTable = false },
            title = { Text("Insert table") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(rows, { rows = it.filter(Char::isDigit).take(3) }, label = { Text("Rows") }, singleLine = true, isError = r == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(cols, { cols = it.filter(Char::isDigit).take(2) }, label = { Text("Columns") }, singleLine = true, isError = c == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            },
            confirmButton = {
                TextButton(enabled = r != null && c != null, onClick = {
                    newTable = false
                    focused?.let { id -> editor.insertTable(id, r!!, c!!)?.let { caretTarget = it to 0 }; tick = editor.version }
                }) { Text("Insert") }
            },
            dismissButton = { TextButton(onClick = { newTable = false }) { Text("Cancel") } },
        )
    }

    if (replacing) {
        var find by remember { mutableStateOf(query) }
        var with by remember { mutableStateOf("") }
        var matchCase by remember { mutableStateOf(false) }
        val count = remember(body, find, matchCase) {
            if (find.isEmpty()) 0 else body.paragraphs.sumOf { p -> Regex(Regex.escape(find), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)).findAll(p.text).count() }
        }
        AlertDialog(
            onDismissRequest = { replacing = false },
            title = { Text("Replace") },
            text = {
                Column {
                    OutlinedTextField(find, { find = it }, label = { Text("Find") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(with, { with = it }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { matchCase = !matchCase }.padding(top = 8.dp)) {
                        Checkbox(matchCase, { matchCase = it })
                        Text("Match case", modifier = Modifier.weight(1f))
                        Text(if (find.isEmpty()) "" else "$count found", color = paper.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = count > 0, onClick = {
                    replacing = false
                    var n = 0
                    act { n = replaceAll(find, with, matchCase) }
                    Toast.makeText(context, "Replaced $n", Toast.LENGTH_SHORT).show()
                }) { Text("Replace all") }
            },
            dismissButton = { TextButton(onClick = { replacing = false }) { Text("Cancel") } },
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
    val layout = if (look.faithful) look.layout(p) else null
    val outer = if (layout == null) Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else look.spaceAbove(p), bottom = if (compact) 4.dp else look.spaceBelow(p))
    else Modifier.fillMaxWidth().padding(top = look.spaceAbove(p)).then(layout.shading?.let { Modifier.background(Color(0xFF000000.toInt() or it)) } ?: Modifier)
        .padding(bottom = look.spaceBelow(p), end = layout.right.coerceAtLeast(0f).dp)
    Column(outer) {
        p.images.forEach { DocImage(doc, it, look) }
        if (visible || editing || p.images.isEmpty()) {
            Row(Modifier.padding(start = look.indent(p))) {
                if (label != null) {
                    // On the page a list label sits in the hanging indent, as in Word.
                    val labelWidth = if (layout != null && layout.firstLine < 0) (-layout.firstLine).dp else (28 + 8 * label.length.coerceAtMost(5)).dp.coerceAtMost(64.dp)
                    Text(label, style = look.textStyle(p).copy(textAlign = if (layout != null) TextAlign.Start else TextAlign.End, textIndent = null), maxLines = 1, modifier = Modifier.width(labelWidth).padding(end = if (layout != null) 0.dp else 8.dp))
                }
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
    val wantedWidth = run.imageSize?.first?.let { if (look.faithful) it.dp else (it * 1.33f * look.scale).dp }
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
    if (look.faithful) { PageTable(t, look, cell); return }
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


/** A table as Word draws it: column widths in points, borders only when the table has them, cell colours. */
@Composable
private fun PageTable(t: Block.T, look: Look, cell: @Composable (Para) -> Unit) {
    val bordered = remember(t) { DocLayout.bordered(t, look.styles) }
    val line = if (bordered) Color.Black else Color.Transparent
    val cols = t.widths.ifEmpty { List(t.rows.maxOfOrNull { it.size } ?: 1) { 0 } }
    val textWidth = look.page?.textWidth ?: 468f
    val total = cols.sum().takeIf { it > 0 }?.div(20f)
    // Columns keep their proportions; a table wider than the text area is fitted to it.
    val fit = if (total != null && total > textWidth) textWidth / total else 1f
    fun width(from: Int, span: Int) = (from until (from + span).coerceAtMost(cols.size)).sumOf { cols[it] }.let { tw ->
        if (tw > 0) (tw / 20f * fit).dp else (textWidth / cols.size * span).dp
    }
    Column(Modifier.padding(vertical = 2.dp).border(0.5.dp, line)) {
        t.rows.forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                var col = 0
                row.forEach { c ->
                    val span = DocLayout.span(c)
                    val fill = DocLayout.cellFill(c)
                    Column(
                        Modifier.width(width(col, span)).fillMaxHeight().border(0.5.dp, line)
                            .then(fill?.let { Modifier.background(Color(0xFF000000.toInt() or it)) } ?: Modifier)
                            .padding(horizontal = 5.4.dp, vertical = 1.dp),
                    ) { c.paras.forEach { cell(it) } }
                    col += span
                }
            }
        }
    }
}

/**
 * The page view: sheets of paper on a grey desk, as wide as the screen (pinch to zoom in and pan),
 * with the document's margins, and a new sheet wherever the document breaks the page. Inside, one
 * point of the document is drawn as one scaled unit, so sizes match the printed page.
 */
@Composable
private fun PageColumn(
    page: PageSetup, count: Int, pageStarts: Set<Int>, zoom: Float, onZoom: (Float) -> Unit, list: androidx.compose.foundation.lazy.LazyListState,
    topPad: Dp, editing: Boolean, onTap: () -> Unit, block: @Composable (Int) -> Unit,
) {
    val desk = Color(0xFF8A8D93).copy(alpha = 0.35f)
    val night = LocalPaper.current.dark
    val zoomNow by androidx.compose.runtime.rememberUpdatedState(zoom)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                // Two fingers zoom; one finger is left alone so the pages scroll as usual. Once a pinch
                // starts, the rest of that touch belongs to it, so lifting one finger doesn't scroll.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var current = zoomNow
                    var pinching = false
                    do {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinching = true
                            current = (current * event.calculateZoom()).coerceIn(1f, 4f)
                            onZoom(current)
                        }
                        if (pinching) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val sheet = (maxWidth - 16.dp) * zoom
        val density = LocalDensity.current
        // Document points to screen pixels.
        val pt = remember(sheet, density) { Density(density.density * (sheet.value / page.width), 1f) }
        val hScroll = rememberScrollState()
        // How tall each piece is, in document points, as it's laid out; the sheets are cut from these.
        val heights = remember(count, page) { androidx.compose.runtime.mutableStateMapOf<Int, Float>() }
        val starts by remember(count, page, pageStarts) {
            androidx.compose.runtime.derivedStateOf { paginate(count, pageStarts, page.height - page.top - page.bottom) { heights[it] } }
        }
        Box(Modifier.fillMaxSize().then(if (zoom > 1f) Modifier.horizontalScroll(hScroll) else Modifier)) {
            LazyColumn(
                state = list,
                modifier = Modifier.width(sheet + 16.dp).fillMaxHeight().then(if (!editing) Modifier.clickable(interactionSource = null, indication = null, onClick = onTap) else Modifier),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = topPad, bottom = 120.dp),
            ) {
                items(count, key = { it }) { i ->
                    val begins = i == 0 || i in starts
                    val ends = i == count - 1 || (i + 1) in starts
                    Column(Modifier.fillMaxWidth()) {
                        if (begins && i > 0) Spacer(Modifier.height(12.dp))
                        CompositionLocalProvider(LocalDensity provides pt) {
                            Column(
                                Modifier.fillMaxWidth().nightPage(night).background(Color.White)
                                    .padding(start = page.left.dp, end = page.right.dp, top = if (begins) page.top.dp else 0.dp, bottom = if (ends) page.bottom.dp else 0.dp),
                            ) {
                                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                                    Box(Modifier.onSizeChanged { heights[i] = it.height.toFloat() / pt.density }) { block(i) }
                                }
                            }
                        }
                        if (ends) Box(Modifier.fillMaxWidth().height(1.dp).background(desk))
                    }
                }
            }
        }
    }
}

/**
 * Where each sheet begins: at the document's own page breaks, and wherever the next piece would
 * run past the bottom of the sheet. Pieces not laid out yet ([height] null) end the reckoning;
 * sheets further down are cut once they've been measured.
 */
internal fun paginate(count: Int, breaks: Set<Int>, room: Float, height: (Int) -> Float?): Set<Int> {
    val out = HashSet<Int>(breaks)
    var used = 0f
    for (i in 0 until count) {
        if (i in breaks) used = 0f
        val h = height(i) ?: break
        if (used > 0f && used + h > room) { out += i; used = 0f }
        used += h
    }
    return out
}

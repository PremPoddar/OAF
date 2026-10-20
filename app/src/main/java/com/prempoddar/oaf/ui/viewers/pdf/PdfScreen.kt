package com.prempoddar.oaf.ui.viewers.pdf

import com.prempoddar.oaf.ui.design.sliding
import com.prempoddar.oaf.ui.design.tap
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.R
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.pdf.Box as PBox
import com.prempoddar.oaf.pdf.FontFamilyChoice
import com.prempoddar.oaf.pdf.FontSource
import com.prempoddar.oaf.pdf.OutlineItem
import com.prempoddar.oaf.pdf.PdfAnnot
import com.prempoddar.oaf.pdf.PdfAnnots
import com.prempoddar.oaf.pdf.PdfContent
import com.prempoddar.oaf.pdf.PdfElement
import com.prempoddar.oaf.pdf.PdfField
import com.prempoddar.oaf.pdf.PdfForms
import com.prempoddar.oaf.pdf.PdfPages
import com.prempoddar.oaf.pdf.PdfSession
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.Paper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import com.prempoddar.oaf.ui.viewers.openElsewhere
import com.prempoddar.oaf.ui.viewers.shareSource
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

private sealed interface Opening {
    data object Loading : Opening
    class NeedsPassword(val wrong: Boolean) : Opening
    class Ready(val session: PdfSession) : Opening
    class Failed(val reason: String) : Opening
}

@Composable
fun PdfScreen(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    var password by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val opening by produceState<Opening>(Opening.Loading, source, attempt) {
        value = Opening.Loading
        value = withContext(Dispatchers.IO) {
            try {
                // Each opening gets its own folder: closing an earlier view of the same PDF deletes
                // its folder, and must not take this one's working copy with it.
                val work = File(context.cacheDir, "pdf/${source.uri.toString().hashCode().toUInt()}-${System.nanoTime()}")
                Opening.Ready(PdfSession.open(source.local(context), work, password))
            } catch (e: PdfSession.PasswordNeeded) {
                Opening.NeedsPassword(wrong = password != null)
            } catch (e: Throwable) {
                Opening.Failed(e.message ?: "Couldn't open this PDF")
            }
        }
    }
    when (val o = opening) {
        is Opening.Ready -> PdfBody(source, o.session, onBack)
        else -> ViewerScaffold(title = source.name, subtitle = "PDF", chrome = rememberChrome(), autoHide = false, onBack = onBack) {
            when (o) {
                is Opening.Failed -> Text(o.reason, color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = 100.dp))
                is Opening.NeedsPassword -> PasswordPrompt(o.wrong, onCancel = onBack) { password = it; attempt++ }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun PasswordPrompt(wrong: Boolean, onCancel: () -> Unit, onOpen: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("Password needed") },
        text = {
            Column {
                Text(if (wrong) "That password didn't work. Try again." else "This PDF is protected. Enter its password to open it.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    text, { text = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { onOpen(text) }),
                    placeholder = { Text("Password") },
                )
            }
        },
        confirmButton = { TextButton(onClick = tap { onOpen(text) }) { Text("Open") } },
        dismissButton = { TextButton(onClick = tap(onCancel)) { Text("Cancel") } },
    )
}

private val COLOURS = listOf(0x2F5D8C, 0xC00000, 0x00897B, 0xF57C00, 0x7030A0, 0x1A1A1A)
private val HIGHLIGHT = 0xFFEB3B

/** Something waiting for a place on the page: a note, text or a picture goes where the next tap lands. */
private sealed interface Pending {
    class NoteAt(val page: Int, val x: Float, val y: Float) : Pending
    class TextAt(val page: Int, val x: Float, val y: Float) : Pending
    class EditText(val page: Int, val element: PdfElement) : Pending
    class ShowNote(val page: Int, val annot: PdfAnnot) : Pending
}

@Composable
private fun PdfBody(source: DocSource, session: PdfSession, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val chrome = rememberChrome()
    val state = remember { PdfState(session, scope, RenderHost()) { msg -> scope.launch { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() } } }
    LaunchedEffect(Unit) {
        runCatching { state.start() }.onFailure { Toast.makeText(context, "Couldn't draw this PDF: ${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
    }
    DisposableEffect(Unit) { onDispose { state.close() } }
    val perms = session.permissions
    val fonts = remember {
        FontSource { family -> context.resources.openRawResource(when (family) { FontFamilyChoice.SERIF -> R.font.literata; FontFamilyChoice.MONO -> R.font.jetbrains_mono; else -> R.font.inter }) }
    }

    val list = rememberLazyListState()
    val hScroll = rememberScrollState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var renderZoom by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(zoom) { delay(220); renderZoom = zoom }

    var menu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var outlineOpen by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    var watermarking by remember { mutableStateOf(false) }
    // Split: one PDF per page, into a folder the person picks.
    val splitInto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@rememberLauncherForActivityResult
        scope.launch {
            val count = runCatching {
                val dir = File(context.cacheDir, "split").apply { deleteRecursively(); mkdirs() }
                val files = state.work { PdfPages.split(it.doc, dir, source.name.removeSuffix(".pdf")) }
                withContext(Dispatchers.IO) {
                    val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
                    files.forEach { f ->
                        val doc = android.provider.DocumentsContract.createDocument(context.contentResolver, parent, "application/pdf", f.name)!!
                        context.contentResolver.openOutputStream(doc)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
                    }
                    dir.deleteRecursively()
                }
                files.size
            }.getOrNull()
            Toast.makeText(context, if (count != null) "Saved $count single-page PDFs" else "Couldn't split the PDF", Toast.LENGTH_SHORT).show()
        }
    }
    var confirmLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var signing by remember { mutableStateOf<PdfField?>(null) }
    var padOpen by remember { mutableStateOf(false) }
    var signature by remember { mutableStateOf<Bitmap?>(null) }
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    var pagesSelected by remember { mutableStateOf(setOf<Int>()) }
    var lastTap by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    val signatureFile = remember { File(context.filesDir, "signature.png") }
    val savedSignature = remember(padOpen) { if (signatureFile.exists()) BitmapFactory.decodeFile(signatureFile.path) else null }

    fun scrollTo(page: Int) = scope.launch { list.animateScrollToItem(page.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0))) }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            picture = withContext(Dispatchers.IO) { decodeScaled(context, uri, 2000) }
            if (picture != null) { state.tool = Tool.ADD_IMAGE; Toast.makeText(context, "Tap where the picture goes", Toast.LENGTH_SHORT).show() }
        }
    }
    val extractTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val pages = pagesSelected.sorted()
        scope.launch {
            val ok = runCatching {
                val tmp = File(context.cacheDir, "extract.pdf")
                state.work { PdfPages.extract(it.doc, pages, tmp) }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }; tmp.delete() }
            }.isSuccess
            Toast.makeText(context, if (ok) "Saved ${pages.size} page${if (pages.size == 1) "" else "s"}" else "Couldn't save the pages", Toast.LENGTH_SHORT).show()
        }
    }
    val appendFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val tmp = withContext(Dispatchers.IO) { File(context.cacheDir, "append.pdf").also { f -> context.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } } } }
            state.change("add the pages") { PdfPages.append(it, tmp) }
        }
    }

    suspend fun bytes(): ByteArray = state.work { s -> ByteArrayOutputStream().also { s.saveTo(it) }.toByteArray() }
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            saving = true
            val ok = runCatching { val b = bytes(); withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(b) } } }.isSuccess
            saving = false
            if (ok) state.markSaved()
            Toast.makeText(context, if (ok) "Saved a copy" else "Couldn't save", Toast.LENGTH_SHORT).show()
        }
    }
    fun save(then: () -> Unit = {}) {
        scope.launch {
            saving = true
            val ok = runCatching {
                if (!source.canWrite(context)) error("read-only")
                val b = bytes()
                withContext(Dispatchers.IO) { source.openOutput(context).use { it.write(b) } }
            }.isSuccess
            saving = false
            if (ok) { state.markSaved(); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); then() } else saveCopy.launch(source.name)
        }
    }

    fun placeImage(bitmap: Bitmap, page: Int, box: PBox, what: String) {
        state.change(what) { doc -> PdfContent.addImage(doc, page, box, LosslessFactory.createFromImage(doc, bitmap)) }
    }

    /** A box [widthFraction] of the page wide, centred on a tap, shaped like [bitmap]. */
    fun boxAround(page: Int, x: Float, y: Float, bitmap: Bitmap, widthFraction: Float): PBox {
        val info = state.pages[page]
        val w = widthFraction
        val h = w * bitmap.height / bitmap.width * info.width / info.height
        val l = (x - w / 2).coerceIn(0f, 1f - w); val t = (y - h / 2).coerceIn(0f, (1f - h).coerceAtLeast(0f))
        return PBox(l, t, l + w, t + h)
    }

    val actions = object : PageActions {
        override fun tap(page: Int, x: Float, y: Float) {
            if (state.selection != null) { state.selection = null; return }
            when (state.mode) {
                PdfMode.READ -> scope.launch {
                    val hit = state.annots(page).lastOrNull { it.box.contains(x, y, 0.004f) }
                    when {
                        hit?.uri != null -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.uri))) }
                        hit?.page != null -> scrollTo(hit.page)
                        hit != null && hit.subtype == "Text" -> pending = Pending.ShowNote(page, hit)
                        else -> chrome.toggle()
                    }
                }
                PdfMode.ANNOTATE -> when (state.tool) {
                    Tool.NOTE -> pending = Pending.NoteAt(page, x, y)
                    Tool.ERASER -> scope.launch {
                        val hit = state.annots(page).lastOrNull { it.isMarkup && it.box.contains(x, y, 0.008f) }
                        if (hit != null) state.change("remove it") { PdfAnnots.delete(it, page, hit.index) }
                    }
                    else -> scope.launch {
                        state.annots(page).lastOrNull { it.subtype == "Text" && it.box.contains(x, y, 0.006f) }?.let { pending = Pending.ShowNote(page, it) }
                    }
                }
                PdfMode.EDIT -> when (state.tool) {
                    Tool.ADD_TEXT -> pending = Pending.TextAt(page, x, y)
                    Tool.ADD_IMAGE -> picture?.let { b -> placeImage(b, page, boxAround(page, x, y, b, 0.4f), "add the picture"); picture = null; state.tool = Tool.NONE }
                    else -> scope.launch {
                        val options = PdfContent.pick(state.elements(page), x, y)
                        val last = lastTap
                        val current = state.picked?.takeIf { it.first == page }?.second
                        // Tapping the same spot again moves to the next thing underneath.
                        val again = last != null && last.first == page && kotlin.math.abs(last.second - x) < 0.01f && kotlin.math.abs(last.third - y) < 0.01f
                        val next = if (again && current != null && current in options) options[(options.indexOf(current) + 1) % options.size] else options.firstOrNull()
                        lastTap = Triple(page, x, y)
                        state.picked = next?.let { page to it }
                    }
                }
                PdfMode.FILL -> if (state.tool == Tool.PLACE_SIGNATURE) signature?.let { b ->
                    placeImage(b, page, boxAround(page, x, y, b, 0.28f), "place the signature"); state.tool = Tool.NONE
                }
                PdfMode.PAGES -> Unit
            }
        }

        override fun longPress(page: Int, x: Float, y: Float) {
            if (state.mode != PdfMode.READ && !(state.mode == PdfMode.ANNOTATE && state.tool == Tool.NONE)) return
            scope.launch {
                val t = state.text(page)
                val i = t.charAt(x, y) ?: return@launch
                val w = t.wordAt(i)
                state.selection = TextSelection(page, w.first, w.last)
            }
        }

        override fun longDrag(page: Int, x: Float, y: Float) {
            val s = state.selection?.takeIf { it.page == page } ?: return
            scope.launch { state.text(page).charAt(x, y)?.let { state.selection = s.copy(focus = it) } }
        }

        override fun stroke(page: Int, points: List<Pair<Float, Float>>) {
            val marker = state.tool == Tool.MARKER
            val colour = if (marker) HIGHLIGHT else state.color
            state.change("draw") { PdfAnnots.ink(it, page, listOf(points), colour, if (marker) 14f else state.strokeWidth, if (marker) 0.4f else 1f) }
        }

        override fun shape(page: Int, from: Pair<Float, Float>, to: Pair<Float, Float>) {
            val kind = when (state.tool) { Tool.RECT -> PdfAnnots.Shape.RECTANGLE; Tool.ELLIPSE -> PdfAnnots.Shape.ELLIPSE; else -> PdfAnnots.Shape.ARROW }
            state.change("draw the shape") { PdfAnnots.shape(it, page, kind, from, to, state.color, state.strokeWidth) }
        }

        override fun fieldTapped(field: PdfField) {
            when (field.kind) {
                PdfField.Kind.CHECK, PdfField.Kind.RADIO -> state.change("fill the form") { PdfForms.setChecked(it, field.name, field.onValue, !field.checked) }
                PdfField.Kind.SIGNATURE -> if (perms.modify) { signing = field; padOpen = true } else Toast.makeText(context, "This PDF's owner doesn't allow signing it here", Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }

        override fun fieldText(field: PdfField, value: String) = state.change("fill the form") { PdfForms.setText(it, field.name, value) }
    }

    val dirty = state.unsaved > 0
    BackHandler(enabled = searching || state.selection != null || state.picked != null || state.mode != PdfMode.READ || dirty) {
        when {
            state.selection != null -> state.selection = null
            state.picked != null -> state.picked = null
            searching -> { searching = false; state.hits = emptyList() }
            state.mode != PdfMode.READ -> { state.mode = PdfMode.READ; state.tool = Tool.NONE }
            else -> confirmLeave = true
        }
    }

    val firstVisible by remember { androidx.compose.runtime.derivedStateOf { list.firstVisibleItemIndex } }
    val restricted = perms != com.prempoddar.oaf.pdf.PdfPermissions.ALL
    ViewerScaffold(
        title = (if (dirty) "• " else "") + source.name,
        subtitle = if (state.pages.isEmpty()) "PDF" else "Page ${firstVisible + 1} of ${state.pages.size}" + if (restricted && !perms.modify) " · restricted" else "",
        chrome = chrome, autoHide = state.mode == PdfMode.READ && !searching && state.selection == null,
        onBack = { if (dirty) confirmLeave = true else onBack() },
        actions = {
            if (state.canUndo) IconButton(onClick = tap { state.undo() }) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
            if (state.canRedo) IconButton(onClick = tap { state.redo() }) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
            if (dirty) IconButton(enabled = !saving, onClick = tap { save() }) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Save, "Save")
            } else IconButton(onClick = tap { searching = true; chrome.show() }) { Icon(Icons.Rounded.Search, "Find") }
            Box {
                IconButton(onClick = tap { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (dirty) DropdownMenuItem(text = { Text("Find…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = tap { menu = false; searching = true })
                    DropdownMenuItem(text = { Text("Contents") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Toc, null) }, onClick = tap { menu = false; outlineOpen = true })
                    DropdownMenuItem(text = { Text("Go to page…") }, onClick = tap { menu = false; goTo = true })
                    DropdownMenuItem(text = { Text("Fit width") }, onClick = tap { menu = false; zoom = 1f })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Save a copy…") }, onClick = tap { menu = false; saveCopy.launch(source.name) })
                    if (perms.modify) DropdownMenuItem(text = { Text("Make smaller") }, onClick = tap {
                        menu = false
                        state.change("make it smaller") { doc ->
                            val n = PdfPages.compressImages(doc)
                            scope.launch { Toast.makeText(context, if (n == 0) "No large pictures to shrink" else "Shrank $n picture${if (n == 1) "" else "s"} — save to keep it", Toast.LENGTH_LONG).show() }
                        }
                    })
                    if (perms.modify) DropdownMenuItem(text = { Text("Add a watermark…") }, onClick = tap { menu = false; watermarking = true })
                    DropdownMenuItem(text = { Text("Split into single pages…") }, onClick = tap { menu = false; splitInto.launch(null) })
                    if (perms.print) DropdownMenuItem(text = { Text("Print…") }, onClick = tap { menu = false; print(context, source.name, state) })
                    if (state.fields.isNotEmpty() && perms.modify) DropdownMenuItem(text = { Text("Flatten form") }, onClick = tap { menu = false; state.change("flatten the form") { PdfForms.flatten(it) } })
                    DropdownMenuItem(text = { Text("Share") }, onClick = tap { menu = false; shareSource(context, source) })
                    DropdownMenuItem(text = { Text("Open with…") }, onClick = tap { menu = false; openElsewhere(context, source) })
                }
            }
        },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding() + 64.dp + if (searching) 56.dp else 0.dp
        Box(Modifier.fillMaxSize()) {
            if (state.mode == PdfMode.PAGES) {
                PagesGrid(state, paper, pagesSelected, top + 8.dp, onToggle = { i -> pagesSelected = if (i in pagesSelected) pagesSelected - i else pagesSelected + i })
            } else {
                PageList(state, paper, list, hScroll, zoom, renderZoom, top, actions, onZoom = { zoom = it })
            }
            if (searching) SearchBar(
                query, state, top - 56.dp,
                onQuery = { q -> query = q; state.search(q) { first -> scrollTo(first.page) } },
                onStep = { d -> if (state.hits.isNotEmpty()) { state.hit = ((state.hit + d) % state.hits.size + state.hits.size) % state.hits.size; scrollTo(state.hits[state.hit].page) } },
                onClose = { searching = false; query = ""; state.hits = emptyList() },
            )
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = top - 4.dp).height(3.dp))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                state.selection?.let { sel ->
                    ActionBar {
                        if (perms.copy) Act(Icons.Rounded.ContentCopy, "Copy") {
                            scope.launch {
                                val t = state.text(sel.page).text
                                val s = t.substring(sel.range.first.coerceIn(0, t.length), (sel.range.last + 1).coerceIn(0, t.length))
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", s))
                                state.selection = null
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                        if (perms.annotate) {
                            fun mark(kind: PdfAnnots.Markup, colour: Int) = scope.launch {
                                val boxes = state.text(sel.page).lineBoxes(sel.range)
                                state.change("mark the text") { PdfAnnots.markup(it, sel.page, kind, boxes, colour) }
                            }
                            Act(Icons.Rounded.BorderColor, "Highlight") { mark(PdfAnnots.Markup.HIGHLIGHT, HIGHLIGHT) }
                            Act(Icons.Rounded.FormatUnderlined, "Underline") { mark(PdfAnnots.Markup.UNDERLINE, state.color) }
                            Act(Icons.Rounded.FormatStrikethrough, "Strike out") { mark(PdfAnnots.Markup.STRIKEOUT, 0xC00000) }
                        }
                        Act(Icons.Rounded.Close, "Clear") { state.selection = null }
                    }
                }
                state.picked?.let { (page, el) ->
                    ActionBar {
                        Text(
                            when (el.kind) { PdfElement.Kind.TEXT -> "Text · ${"%.0f".format(el.fontSize)} pt"; PdfElement.Kind.IMAGE -> "Picture"; PdfElement.Kind.SHAPE -> "Shape"; PdfElement.Kind.OBJECT -> "Drawing" },
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        if (el.kind == PdfElement.Kind.TEXT) Act(Icons.Rounded.Edit, "Edit text") { pending = Pending.EditText(page, el) }
                        Act(Icons.Rounded.Delete, "Delete") { state.change("delete it") { PdfContent.delete(it, page, listOf(el)) } }
                        Act(Icons.Rounded.Close, "Done") { state.picked = null }
                    }
                }
                if (chrome.visible.value || state.mode != PdfMode.READ) {
                    ModeTools(state, perms, paper, pagesSelected,
                        onPickPicture = { pickPicture.launch("image/*") },
                        onSign = { signing = null; padOpen = true },
                        onPages = { action ->
                            val sel = pagesSelected.sorted()
                            when (action) {
                                "left" -> state.change("rotate") { PdfPages.rotate(it, sel, -90) }
                                "right" -> state.change("rotate") { PdfPages.rotate(it, sel, 90) }
                                "up" -> sel.singleOrNull()?.takeIf { it > 0 }?.let { i -> state.change("move the page") { d -> PdfPages.move(d, i, i - 1) }; pagesSelected = setOf(i - 1) }
                                "down" -> sel.singleOrNull()?.takeIf { it < state.pages.size - 1 }?.let { i -> state.change("move the page") { d -> PdfPages.move(d, i, i + 1) }; pagesSelected = setOf(i + 1) }
                                "copy" -> sel.singleOrNull()?.let { i -> state.change("duplicate the page") { d -> PdfPages.duplicate(d, i) } }
                                "blank" -> state.change("add a page") { PdfPages.insertBlank(it, sel.maxOrNull() ?: (state.pages.size - 1)) }
                                "delete" -> if (sel.size < state.pages.size) { state.change("delete pages") { PdfPages.delete(it, sel) }; pagesSelected = emptySet() }
                                "extract" -> extractTo.launch(source.name.removeSuffix(".pdf") + " (pages).pdf")
                                "append" -> appendFrom.launch(arrayOf("application/pdf"))
                            }
                        },
                    )
                }
            }
        }
    }

    when (val p = pending) {
        is Pending.NoteAt -> TextDialog("Add a note", "", multiline = true, onDismiss = { pending = null }) { text ->
            pending = null
            if (text.isNotBlank()) state.change("add the note") { PdfAnnots.note(it, p.page, p.x, p.y, text) }
        }
        is Pending.ShowNote -> NoteDialog(p.annot, canEdit = perms.annotate, onDismiss = { pending = null },
            onSave = { text -> pending = null; state.change("change the note") { PdfAnnots.setContents(it, p.page, p.annot.index, text) } },
            onDelete = { pending = null; state.change("delete the note") { PdfAnnots.delete(it, p.page, p.annot.index) } })
        is Pending.TextAt -> AddTextDialog(onDismiss = { pending = null }) { text, size, family ->
            pending = null
            state.change("add the text") { PdfContent.addText(it, p.page, p.x, p.y, text, size, state.color, family, fonts) }
            state.tool = Tool.NONE
        }
        is Pending.EditText -> TextDialog("Edit text", p.element.text, multiline = false, onDismiss = { pending = null }) { text ->
            pending = null
            state.change("change the text") { PdfContent.replaceText(it, p.page, p.element, text, fonts) }
        }
        null -> Unit
    }

    if (padOpen) SignaturePad(savedSignature, onDismiss = { padOpen = false }) { bmp ->
        padOpen = false
        signature = bmp
        runCatching { FileOutputStream(signatureFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val field = signing
        if (field != null) {
            // Fit the signature inside the field, keeping its shape.
            val info = state.pages[field.page]
            val boxAspect = field.box.width * info.width / (field.box.height * info.height)
            val imgAspect = bmp.width.toFloat() / bmp.height
            val box = if (imgAspect > boxAspect) {
                val h = field.box.width * info.width / imgAspect / info.height
                PBox(field.box.l, field.box.cy - h / 2, field.box.r, field.box.cy + h / 2)
            } else {
                val w = field.box.height * info.height * imgAspect / info.width
                PBox(field.box.cx - w / 2, field.box.t, field.box.cx + w / 2, field.box.b)
            }
            placeImage(bmp, field.page, box, "sign")
            signing = null
        } else {
            state.tool = Tool.PLACE_SIGNATURE
            Toast.makeText(context, "Tap where the signature goes", Toast.LENGTH_SHORT).show()
        }
    }

    if (outlineOpen) {
        val items by produceState<List<OutlineItem>?>(null) { value = state.work { PdfPages.outline(it.doc) } }
        AlertDialog(
            onDismissRequest = { outlineOpen = false },
            title = { Text("Contents") },
            text = {
                when (val i = items) {
                    null -> CircularProgressIndicator()
                    else -> if (i.isEmpty()) Text("This PDF has no contents list.", color = paper.muted) else LazyColumn {
                        itemsIndexed(i) { _, item ->
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = item.page != null) { outlineOpen = false; item.page?.let { scrollTo(it) } }
                                    .padding(start = (item.level * 14).dp, top = 8.dp, bottom = 8.dp),
                            ) {
                                Text(item.title, style = if (item.level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                item.page?.let { Text("${it + 1}", color = paper.muted, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = tap { outlineOpen = false }) { Text("Close") } },
        )
    }

    if (watermarking) {
        var mark by remember { mutableStateOf("CONFIDENTIAL") }
        AlertDialog(
            onDismissRequest = { watermarking = false },
            title = { Text("Watermark") },
            text = { OutlinedTextField(mark, { mark = it.take(40) }, singleLine = true, label = { Text("Text across every page") }) },
            confirmButton = { TextButton(enabled = mark.isNotBlank(), onClick = tap { watermarking = false; val t = mark.trim(); state.change("add the watermark") { PdfPages.watermark(it, t) } }) { Text("Add") } },
            dismissButton = { TextButton(onClick = tap { watermarking = false }) { Text("Cancel") } },
        )
    }
    if (goTo) TextDialog("Go to page", "", multiline = false, number = true, onDismiss = { goTo = false }) { text ->
        goTo = false
        text.trim().toIntOrNull()?.let { scrollTo(it - 1) }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Save changes?") },
            text = { Text("You've changed ${source.name}." + if (session.encrypted) " It stays protected with its password." else "") },
            confirmButton = { TextButton(onClick = tap { confirmLeave = false; save(then = onBack) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = tap { confirmLeave = false; onBack() }) { Text("Discard") } },
        )
    }
}

/** The pages in a column, pinch to zoom; when zoomed in the column scrolls sideways too. */
@Composable
private fun PageList(
    state: PdfState, paper: Paper, list: androidx.compose.foundation.lazy.LazyListState, hScroll: androidx.compose.foundation.ScrollState,
    zoom: Float, renderZoom: Float, top: Dp, actions: PageActions, onZoom: (Float) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(if (paper.dark) Color(0xFF0E0E0E) else Color(0xFFECE8E1))) {
        val viewW = maxWidth
        val margin = 12.dp
        val zoomNow = remember { mutableFloatStateOf(zoom) }
        zoomNow.floatValue = zoom
        val pagePx = with(density) { ((viewW - margin * 2) * renderZoom).roundToPx() }.coerceAtMost(3200)
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // A pinch only zooms: the page doesn't drift with the fingers, and lifting one
                        // finger at the end doesn't turn into a scroll.
                        var pinching = false
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.changes.count { it.pressed } >= 2) {
                                pinching = true
                                val old = zoomNow.floatValue
                                val new = (old * e.calculateZoom()).coerceIn(1f, 5f)
                                val c = e.calculateCentroid(useCurrent = true)
                                val k = new / old
                                onZoom(new)
                                zoomNow.floatValue = new
                                // Keep what's under the fingers there.
                                hScroll.dispatchRawDelta((hScroll.value + c.x) * k - c.x - hScroll.value)
                                val above = (0 until list.firstVisibleItemIndex).sumOf { i ->
                                    val p = state.pages[i]
                                    ((viewW.toPx() * old - margin.toPx() * 2) * p.height / p.width + 12.dp.toPx()).toDouble()
                                }.toFloat() + list.firstVisibleItemScrollOffset
                                list.dispatchRawDelta((above + c.y) * (k - 1))
                            }
                            if (pinching) e.changes.forEach { it.consume() }
                        } while (e.changes.any { it.pressed })
                    }
                }
                .horizontalScroll(hScroll, enabled = zoom > 1f),
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.width(viewW * zoom).fillMaxHeight(),
                contentPadding = PaddingValues(top = top + 12.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.pages, key = { i, _ -> i }) { i, _ ->
                    PdfPageView(state, i, pagePx, paper, actions, Modifier.fillMaxWidth().padding(horizontal = margin))
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, state: PdfState, top: Dp, onQuery: (String) -> Unit, onStep: (Int) -> Unit, onClose: () -> Unit) {
    val paper = LocalPaper.current
    Surface(color = paper.chrome, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(top = top)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                query, onQuery, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = paper.ink), cursorBrush = SolidColor(paper.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onStep(1) }),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                decorationBox = { inner -> if (query.isEmpty()) Text("Find in PDF", color = paper.faint); inner() },
            )
            Text(if (state.hits.isEmpty()) "0" else "${state.hit + 1}/${state.hits.size}", style = MaterialTheme.typography.labelMedium, color = paper.muted)
            IconButton(onClick = tap { onStep(-1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous") }
            IconButton(onClick = tap { onStep(1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Next") }
            IconButton(onClick = tap(onClose)) { Icon(Icons.Rounded.Close, "Close") }
        }
    }
}

@Composable
private fun ActionBar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val paper = LocalPaper.current
    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
        Surface(color = paper.chrome, shadowElevation = 6.dp, shape = CircleShape) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
private fun Act(icon: ImageVector, label: String, on: Boolean = false, enabled: Boolean = true, click: () -> Unit) {
    val paper = LocalPaper.current
    IconButton(enabled = enabled, onClick = tap(click)) { Icon(icon, label, tint = if (on) paper.accent else if (enabled) paper.ink else paper.faint) }
}

/** The mode switcher, with the active mode's tools above it. */
@Composable
private fun ModeTools(
    state: PdfState, perms: com.prempoddar.oaf.pdf.PdfPermissions, paper: Paper, pagesSelected: Set<Int>,
    onPickPicture: () -> Unit, onSign: () -> Unit, onPages: (String) -> Unit,
) {
    Surface(color = paper.chrome, shadowElevation = 8.dp, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            fun toolToggle(t: Tool) { state.tool = if (state.tool == t) Tool.NONE else t; state.picked = null }
            when (state.mode) {
                PdfMode.ANNOTATE -> Column {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Act(Icons.Rounded.Gesture, "Pen", state.tool == Tool.PEN) { toolToggle(Tool.PEN) }
                        Act(Icons.Rounded.BorderColor, "Highlighter", state.tool == Tool.MARKER) { toolToggle(Tool.MARKER) }
                        Act(Icons.AutoMirrored.Rounded.StickyNote2, "Note", state.tool == Tool.NOTE) { toolToggle(Tool.NOTE) }
                        Act(Icons.Rounded.CropSquare, "Rectangle", state.tool == Tool.RECT) { toolToggle(Tool.RECT) }
                        Act(Icons.Rounded.RadioButtonUnchecked, "Ellipse", state.tool == Tool.ELLIPSE) { toolToggle(Tool.ELLIPSE) }
                        Act(Icons.Rounded.NorthEast, "Arrow", state.tool == Tool.ARROW) { toolToggle(Tool.ARROW) }
                        Act(Icons.Rounded.AutoFixNormal, "Eraser", state.tool == Tool.ERASER) { toolToggle(Tool.ERASER) }
                        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                        COLOURS.forEach { c ->
                            Box(
                                Modifier.padding(4.dp).size(26.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or c))
                                    .then(if (state.color == c) Modifier.background(Color.Transparent).padding(2.dp) else Modifier)
                                    .clickable { state.color = c },
                                contentAlignment = Alignment.Center,
                            ) { if (state.color == c) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White)) }
                        }
                    }
                    if (state.tool in setOf(Tool.PEN, Tool.RECT, Tool.ELLIPSE, Tool.ARROW)) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Width", style = MaterialTheme.typography.labelMedium, color = paper.muted)
                            Slider(state.strokeWidth, sliding(0.5f..8f) { state.strokeWidth = it }, valueRange = 0.5f..8f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        }
                    }
                    if (state.tool in setOf(Tool.PEN, Tool.MARKER, Tool.RECT, Tool.ELLIPSE, Tool.ARROW)) {
                        Text("Draw with one finger · scroll and zoom with two", style = MaterialTheme.typography.labelSmall, color = paper.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                    }
                }
                PdfMode.FILL -> Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // A drawn signature becomes page content, which needs permission to change the document.
                    if (perms.modify) Act(Icons.Rounded.Draw, "Sign", state.tool == Tool.PLACE_SIGNATURE) { onSign() }
                    if (perms.modify) Act(Icons.Rounded.TextFields, "Add text", state.tool == Tool.ADD_TEXT) { state.mode = PdfMode.EDIT; toolToggle(Tool.ADD_TEXT) }
                    Text(
                        if (state.fields.isEmpty()) "No form fields · sign or add text anywhere" else "Tap a field to fill it in",
                        style = MaterialTheme.typography.labelMedium, color = paper.muted, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                PdfMode.EDIT -> Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Act(Icons.Rounded.TextFields, "Add text", state.tool == Tool.ADD_TEXT) { toolToggle(Tool.ADD_TEXT) }
                    Act(Icons.Rounded.AddPhotoAlternate, "Add picture", state.tool == Tool.ADD_IMAGE) { onPickPicture() }
                    Text(
                        when (state.tool) { Tool.ADD_TEXT -> "Tap where the text goes"; Tool.ADD_IMAGE -> "Tap where the picture goes"; else -> "Tap text, a picture or a shape to change it" },
                        style = MaterialTheme.typography.labelMedium, color = paper.muted, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                PdfMode.PAGES -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val any = pagesSelected.isNotEmpty(); val one = pagesSelected.size == 1
                    Text(if (any) "${pagesSelected.size} selected" else "Tap pages", style = MaterialTheme.typography.labelMedium, color = paper.muted, modifier = Modifier.padding(horizontal = 8.dp))
                    Act(Icons.AutoMirrored.Rounded.RotateLeft, "Rotate left", enabled = any) { onPages("left") }
                    Act(Icons.AutoMirrored.Rounded.RotateRight, "Rotate right", enabled = any) { onPages("right") }
                    Act(Icons.Rounded.ArrowUpward, "Move earlier", enabled = one) { onPages("up") }
                    Act(Icons.Rounded.ArrowDownward, "Move later", enabled = one) { onPages("down") }
                    Act(Icons.Rounded.FileCopy, "Duplicate", enabled = one) { onPages("copy") }
                    Act(Icons.Rounded.NoteAdd, "Insert blank page") { onPages("blank") }
                    Act(Icons.Rounded.Delete, "Delete", enabled = any && pagesSelected.size < state.pages.size) { onPages("delete") }
                    Act(Icons.Rounded.PostAdd, "Save selected pages as a PDF", enabled = any) { onPages("extract") }
                    TextButton(onClick = tap { onPages("append") }) { Text("Add PDF…") }
                }
                PdfMode.READ -> Unit
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable
                fun Mode(m: PdfMode, label: String, icon: ImageVector, allowed: Boolean) = FilterChip(
                    selected = state.mode == m, enabled = allowed,
                    onClick = tap { state.mode = m; state.tool = Tool.NONE; state.picked = null; state.selection = null },
                    label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                )
                Mode(PdfMode.READ, "Read", Icons.Rounded.MenuBook, true)
                Mode(PdfMode.ANNOTATE, "Annotate", Icons.Rounded.BorderColor, perms.annotate)
                Mode(PdfMode.FILL, "Fill & sign", Icons.Rounded.Draw, perms.fillForms)
                Mode(PdfMode.EDIT, "Edit", Icons.Rounded.Edit, perms.modify)
                Mode(PdfMode.PAGES, "Pages", Icons.Rounded.GridView, perms.assemble)
            }
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, multiline: Boolean, number: Boolean = false, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, singleLine = !multiline, minLines = if (multiline) 3 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = tap { onDone(text) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = tap(onDismiss)) { Text("Cancel") } },
    )
}

@Composable
private fun NoteDialog(annot: PdfAnnot, canEdit: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember { mutableStateOf(annot.contents.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.StickyNote2, null) },
        title = { Text("Note") },
        text = {
            if (canEdit) OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
            else Text(text.ifBlank { "(empty)" })
        },
        confirmButton = { if (canEdit) TextButton(onClick = tap { onSave(text) }) { Text("Save") } else TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { if (canEdit) Row { TextButton(onClick = tap(onDelete)) { Text("Delete") }; TextButton(onClick = onDismiss) { Text("Cancel") } } },
    )
}

@Composable
private fun AddTextDialog(onDismiss: () -> Unit, onDone: (String, Float, FontFamilyChoice) -> Unit) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableFloatStateOf(12f) }
    var family by remember { mutableStateOf(FontFamilyChoice.SANS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add text") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, minLines = 2, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Text") })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FontFamilyChoice.SANS to "Sans", FontFamilyChoice.SERIF to "Serif", FontFamilyChoice.MONO to "Mono").forEach { (f, label) ->
                        FilterChip(selected = family == f, onClick = tap { family = f }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${size.toInt()} pt", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
                    Slider(size, sliding(6f..48f) { size = it }, valueRange = 6f..48f, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = tap { onDone(text, size.toInt().toFloat(), family) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = tap(onDismiss)) { Text("Cancel") } },
    )
}

private fun decodeScaled(context: Context, uri: Uri, max: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > max || bounds.outHeight / sample > max) sample *= 2
    context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()

/** Prints the PDF as it is now, changes included. */
private fun print(context: Context, name: String, state: PdfState) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val file = state.session.file
    manager.print(name, object : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(state.pages.size).build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
            runCatching {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }.onFailure { callback.onWriteFailed(it.message) }
        }
    }, null)
}

package com.prempoddar.oaf.ui.viewers

import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.rounded.FindReplace
import com.prempoddar.oaf.export.PrintHtml
import com.prempoddar.oaf.export.Printing
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatClear
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.sheet.A1
import com.prempoddar.oaf.sheet.CellStyle
import com.prempoddar.oaf.sheet.Clip
import com.prempoddar.oaf.sheet.Csv
import com.prempoddar.oaf.sheet.CsvDialect
import com.prempoddar.oaf.sheet.Evaluator
import com.prempoddar.oaf.sheet.NumFmt
import com.prempoddar.oaf.sheet.Ods
import com.prempoddar.oaf.sheet.SheetEditor
import com.prempoddar.oaf.sheet.Value
import com.prempoddar.oaf.sheet.Xls
import com.prempoddar.oaf.sheet.Xlsx
import com.prempoddar.oaf.sheet.XlsxDoc
import com.prempoddar.oaf.text.TextLoad
import com.prempoddar.oaf.ui.CodeStyle
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

private sealed interface SheetLoaded {
    /** [xlsx] is set for Excel files (saved in place); [csv] for delimited text; neither for read-only formats. */
    class Ready(val editor: SheetEditor, val xlsx: XlsxDoc?, val original: File?, val csv: CsvDialect?) : SheetLoaded
    class Failed(val reason: String) : SheetLoaded
}

private fun loadSheet(context: Context, source: DocSource): SheetLoaded {
    val file = source.local(context)
    return when (source.kind) {
        FileKind.XLSX -> Xlsx.read(file).let { SheetLoaded.Ready(SheetEditor(it.workbook), it, file, null) }
        FileKind.XLS -> SheetLoaded.Ready(SheetEditor(Xls.read(file)), null, null, null)
        FileKind.ODS -> SheetLoaded.Ready(SheetEditor(Ods.read(file)), null, null, null)
        FileKind.TSV -> Csv.read(file, tsv = true).let { (wb, d) -> SheetLoaded.Ready(SheetEditor(wb), null, null, d) }
        else -> Csv.read(file).let { (wb, d) -> SheetLoaded.Ready(SheetEditor(wb), null, null, d) }
    }
}

private val FILL_COLOURS = listOf(0xFFFFF2CC, 0xFFE2EFDA, 0xFFDDEBF7, 0xFFFCE4D6, 0xFFEDEDED, 0xFFFFC000, 0xFF70AD47, 0xFF5B9BD5, 0xFFC00000, 0xFF7030A0).map { it.toInt() }
private val TEXT_COLOURS = listOf(0xFFC00000, 0xFFED7D31, 0xFFBF9000, 0xFF548235, 0xFF2F5D8C, 0xFF7030A0, 0xFF7F7F7F).map { it.toInt() }

private val NUMBER_FORMATS: List<Pair<String, String?>> by lazy {
    val symbol = runCatching { java.util.Currency.getInstance(java.util.Locale.getDefault()).symbol }.getOrDefault("$")
    listOf(
        "General" to null, "Number  1,234.56" to "#,##0.00", "Whole  1234" to "0", "Percent  12.5%" to "0.0%",
        "Currency  ${symbol}1,234.56" to "$symbol#,##0.00", "Date  2026-10-05" to "yyyy-mm-dd", "Long date  5 October 2026" to "d mmmm yyyy",
        "Time  13:30" to "h:mm", "Text" to "@",
    )
}

@Composable
fun SheetScreen(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val loaded by produceState<SheetLoaded?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadSheet(context, source) }.getOrElse { SheetLoaded.Failed(it.message ?: "Couldn't read this spreadsheet") }
        }
    }
    when (val l = loaded) {
        is SheetLoaded.Ready -> SheetBody(source, l, onBack)
        else -> ViewerScaffold(title = source.name, subtitle = source.kind.label, chrome = rememberChrome(), autoHide = false, onBack = onBack) {
            if (l is SheetLoaded.Failed) Text(l.reason, color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = 100.dp))
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun SheetBody(source: DocSource, l: SheetLoaded.Ready, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val editor = l.editor
    var tick by remember { mutableIntStateOf(0) }
    fun act(block: SheetEditor.() -> Unit) { editor.block(); tick = editor.version }
    val grid = remember { GridState() }

    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val fieldFocus = remember { FocusRequester() }
    var clip by remember { mutableStateOf<Clip?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // The formula bar follows the cursor while not typing.
    LaunchedEffect(editor.cursor, editor.active, tick) {
        if (!editing) draft = TextFieldValue(editor.inputAt(editor.cursor.first, editor.cursor.second))
    }

    fun commit(move: Pair<Int, Int>? = 1 to 0) {
        val (r, c) = editor.cursor
        if (draft.text != editor.inputAt(r, c)) act { commit(draft.text) }
        editing = false
        focus.clearFocus()
        if (move != null) act { select(r + move.first, c + move.second) }
        grid.reveal(editor.cursor.first, editor.cursor.second)
    }

    fun cancel() {
        editing = false
        draft = TextFieldValue(editor.inputAt(editor.cursor.first, editor.cursor.second))
        focus.clearFocus()
    }

    fun startEditing() {
        editing = true
        draft = draft.copy(selection = TextRange(draft.text.length))
        scope.launch { fieldFocus.requestFocus() }
    }

    val readOnlyFormat = l.xlsx == null && l.csv == null
    fun encode(out: OutputStream, asXlsx: Boolean) {
        if (asXlsx || l.csv == null) {
            val doc = l.xlsx?.let { XlsxDoc(editor.wb, it.xfStyles) } ?: XlsxDoc(editor.wb, emptyList())
            // Built in memory first: the original may be the very file being overwritten.
            val bytes = ByteArrayOutputStream().also { Xlsx.write(doc, if (asXlsx && l.xlsx == null) null else l.original, it) }.toByteArray()
            out.write(bytes)
        } else {
            out.write(TextLoad.encode(Csv.write(editor.wb.sheets[0], l.csv.delimiter), l.csv.format))
        }
    }

    val saveAsXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            saving = true
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { encode(it, asXlsx = true) } }.isSuccess }
            saving = false
            if (ok) { editor.markSaved(); tick++ }
            Toast.makeText(context, if (ok) "Saved as Excel workbook" else "Couldn't save", Toast.LENGTH_SHORT).show()
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Csv.write(editor.sheet, ',').toByteArray()) } }.isSuccess
            }
            Toast.makeText(context, if (ok) "Exported ${editor.sheet.name}" else "Couldn't export", Toast.LENGTH_SHORT).show()
        }
    }
    val baseName = source.name.substringBeforeLast('.')

    fun save(then: () -> Unit = {}) {
        if (editing) commit(null)
        if (readOnlyFormat) { saveAsXlsx.launch("$baseName.xlsx"); return }
        scope.launch {
            saving = true
            val ok = withContext(Dispatchers.IO) {
                runCatching { if (!source.canWrite(context)) error("read-only"); source.openOutput(context).use { encode(it, asXlsx = false) } }.isSuccess
            }
            saving = false
            if (ok) {
                editor.markSaved(); tick++
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                then()
            } else saveAsXlsx.launch(if (l.csv != null) source.name else "$baseName.xlsx")
        }
    }

    fun copy(cut: Boolean) {
        if (editing) return
        val c = editor.copy(cut)
        clip = c
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("cells", c.text))
        Toast.makeText(context, if (cut) "Cut — tap where to put it, then paste" else "Copied", Toast.LENGTH_SHORT).show()
    }

    fun paste() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        val c = clip
        if (c != null && (text == null || text == c.text)) {
            act { if (paste(c)) clip = null }
        } else if (text != null) {
            act { pasteText(text) }
        }
    }

    val dirty = editor.unsaved > 0 || (editing && draft.text != editor.inputAt(editor.cursor.first, editor.cursor.second))
    BackHandler(enabled = editing || finding || dirty) {
        when {
            editing -> cancel()
            finding -> finding = false
            else -> confirmLeave = true
        }
    }

    ViewerScaffold(
        title = (if (dirty) "• " else "") + source.name,
        subtitle = source.kind.label + if (readOnlyFormat) " · saves as .xlsx" else "",
        chrome = rememberChrome(), autoHide = false,
        onBack = { if (dirty) confirmLeave = true else onBack() },
        actions = {
            IconButton(enabled = editor.canUndo && !editing, onClick = { act { undo() } }) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
            IconButton(enabled = editor.canRedo && !editing, onClick = { act { redo() } }) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
            IconButton(enabled = dirty && !saving, onClick = { save() }) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Save, "Save")
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Find…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = { menu = false; finding = true })
                    DropdownMenuItem(text = { Text("Go to cell…") }, onClick = { menu = false; goTo = true })
                    DropdownMenuItem(text = { Text("Reset zoom") }, onClick = { menu = false; grid.zoom = 1f })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Save as Excel workbook…") }, onClick = { menu = false; saveAsXlsx.launch("$baseName.xlsx") })
                    DropdownMenuItem(text = { Text("Export this sheet as CSV…") }, onClick = { menu = false; exportCsv.launch("$baseName - ${editor.sheet.name}.csv") })
                    DropdownMenuItem(text = { Text("Print or save as PDF…") }, onClick = { menu = false; Printing.printHtml(context, source.name, PrintHtml.sheet(source.name, editor.sheet)) })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareSource(context, source) })
                    DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; openElsewhere(context, source) })
                }
            }
        },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(Modifier.fillMaxSize().imePadding().padding(top = insets.calculateTopPadding() + 64.dp)) {
            FormulaBar(
                address = editor.selection.let { s -> if (s.r1 == s.r2 && s.c1 == s.c2) A1.name(s.r1, s.c1) else A1.name(s.r1, s.c1) + ":" + A1.name(minOf(s.r2, 99_999), s.c2) },
                value = draft, editing = editing, focus = fieldFocus,
                onAddress = { goTo = true },
                onChange = { draft = it },
                onFocus = { if (it && !editing) startEditing() },
                onDone = { commit() }, onCancel = { cancel() },
            )
            if (editing && draft.text.startsWith("=")) FunctionHints(draft) { draft = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SheetGrid(
                    editor = editor, tick = tick, state = grid, paper = paper,
                    headerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxSize(),
                    onTap = { hit ->
                        when (hit) {
                            Hit.Corner -> { if (editing) commit(null); act { selectAll() } }
                            is Hit.Col -> { if (editing) commit(null); act { selectCol(hit.c) } }
                            is Hit.Row -> { if (editing) commit(null); act { selectRow(hit.r) } }
                            is Hit.Cell -> when {
                                // While typing a formula, tapping a cell adds its address.
                                editing && draft.text.startsWith("=") -> {
                                    val ref = A1.name(hit.r, hit.c)
                                    val t = draft.text; val s = draft.selection
                                    draft = TextFieldValue(t.substring(0, s.min) + ref + t.substring(s.max), TextRange(s.min + ref.length))
                                }
                                editing -> { commit(null); act { select(hit.r, hit.c) } }
                                editor.cursor == (hit.r to hit.c) && editor.selection.let { it.r1 == it.r2 && it.c1 == it.c2 || editor.sheet.merges.any { m -> m == it } } -> startEditing()
                                else -> act { select(hit.r, hit.c) }
                            }
                        }
                    },
                    onRangeStart = { r, c -> if (editing) commit(null); act { select(r, c) } },
                    onRangeTo = { r, c -> act { select(r, c, extend = true) } },
                    onResize = { c, w -> act { setColumnWidth(c, w) } },
                )
                SelectionStats(editor, tick, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
            if (finding) FindBar(editor, onGo = { r, c -> act { select(r, c) }; grid.reveal(r, c) }, onClose = { finding = false })
            else if (!editing) FormatBar(editor, tick, act = ::act, onCopy = ::copy, onPaste = ::paste, canPaste = true)
            SheetTabs(editor, tick, allowAdd = l.csv == null, act = ::act, onSwitch = { if (editing) commit(null); act { active = it }; grid.sx = 0f; grid.sy = 0f })
        }
    }

    if (goTo) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { goTo = false },
            title = { Text("Go to cell") },
            text = { OutlinedTextField(text, { text = it }, placeholder = { Text("For example B12") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val target = A1.parse(text.trim())
                    if (target != null) { act { select(target.first, target.second) }; grid.reveal(target.first, target.second); goTo = false }
                    else Toast.makeText(context, "That isn't a cell address", Toast.LENGTH_SHORT).show()
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { goTo = false }) { Text("Cancel") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Save changes?") },
            text = { Text(if (readOnlyFormat) "${source.kind.label} files are saved as a new Excel workbook." else "You've edited ${source.name}.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; save(then = onBack) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Discard") } },
        )
    }
}

@Composable
private fun FormulaBar(
    address: String, value: TextFieldValue, editing: Boolean, focus: FocusRequester,
    onAddress: () -> Unit, onChange: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit, onDone: () -> Unit, onCancel: () -> Unit,
) {
    val paper = LocalPaper.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                address, style = CodeStyle, color = paper.accent, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAddress).widthIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text("ƒx", color = paper.faint, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
            BasicTextField(
                value = value, onValueChange = onChange,
                textStyle = CodeStyle.copy(color = paper.ink), cursorBrush = SolidColor(paper.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                maxLines = 4,
                modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { onFocus(it.isFocused) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (editing) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .then(if (editing) Modifier.border(1.dp, paper.accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            if (editing) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "Cancel") }
                IconButton(onClick = onDone, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Check, "Enter", tint = paper.accent) }
            }
        }
    }
}

/** Function names matching what's being typed, one tap to insert. */
@Composable
private fun FunctionHints(value: TextFieldValue, onPick: (TextFieldValue) -> Unit) {
    val paper = LocalPaper.current
    val before = value.text.substring(0, value.selection.min)
    val word = Regex("[A-Za-z][A-Za-z0-9.]*$").find(before)?.value
    val matches = remember(word) { if (word == null) emptyList() else Evaluator.FUNCTIONS.filter { it.startsWith(word, ignoreCase = true) }.take(12) }
    if (matches.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(matches) { name ->
            Text(
                name, style = MaterialTheme.typography.labelLarge, color = paper.accent,
                modifier = Modifier.clip(CircleShape).background(paper.accent.copy(alpha = 0.12f)).clickable {
                    val start = before.length - word!!.length
                    val rest = value.text.substring(value.selection.max)
                    val text = value.text.substring(0, start) + name + "(" + rest
                    onPick(TextFieldValue(text, TextRange(start + name.length + 1)))
                }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Sum, average and count of the selected numbers, like a spreadsheet's status bar. */
@Composable
private fun SelectionStats(editor: SheetEditor, tick: Int, modifier: Modifier) {
    val paper = LocalPaper.current
    val stats = remember(tick, editor.selection, editor.active) {
        val s = editor.selection
        if (s.r1 == s.r2 && s.c1 == s.c2) null else {
            val sheet = editor.sheet
            val values = sheet.cells.entries.filter { s.contains(sheet.row(it.key), sheet.col(it.key)) }.map { it.value.value }
            val nums = values.mapNotNull { (it as? Value.Num)?.v }
            val count = values.count { it != Value.Empty }
            if (count == 0) null
            else if (nums.isEmpty()) "Count $count"
            else "Sum ${Value.formatNumber(nums.sum())} · Average ${Value.formatNumber(nums.average())} · Count $count"
        }
    } ?: return
    Text(
        stats, style = MaterialTheme.typography.labelMedium, color = paper.ink,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun FormatBar(editor: SheetEditor, tick: Int, act: (SheetEditor.() -> Unit) -> Unit, onCopy: (Boolean) -> Unit, onPaste: () -> Unit, canPaste: Boolean) {
    val paper = LocalPaper.current
    val style = remember(tick, editor.cursor, editor.active) { editor.cursorStyle }
    var open by remember { mutableStateOf<String?>(null) }
    @Composable
    fun Toggle(on: Boolean, icon: ImageVector, label: String, change: (CellStyle) -> CellStyle) {
        IconToggleButton(checked = on, onCheckedChange = { act { style(change) } }) {
            Icon(icon, label, tint = if (on) paper.accent else paper.ink)
        }
    }
    @Composable
    fun Menu(key: String, icon: ImageVector, label: String, content: @Composable () -> Unit) {
        Box {
            IconButton(onClick = { open = key }) { Icon(icon, label) }
            DropdownMenu(open == key, onDismissRequest = { open = null }) { content() }
        }
    }
    fun item(text: String, run: SheetEditor.() -> Unit): @Composable () -> Unit = {
        DropdownMenuItem(text = { Text(text) }, onClick = { open = null; act(run) })
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Toggle(style.bold, Icons.Rounded.FormatBold, "Bold") { it.copy(bold = !style.bold) }
            Toggle(style.italic, Icons.Rounded.FormatItalic, "Italic") { it.copy(italic = !style.italic) }
            Toggle(style.underline, Icons.Rounded.FormatUnderlined, "Underline") { it.copy(underline = !style.underline) }
            Toggle(style.strike, Icons.Rounded.FormatStrikethrough, "Strikethrough") { it.copy(strike = !style.strike) }
            val alignIcon = when (style.align) { CellStyle.Align.CENTER -> Icons.Rounded.FormatAlignCenter; CellStyle.Align.RIGHT -> Icons.AutoMirrored.Rounded.FormatAlignRight; else -> Icons.AutoMirrored.Rounded.FormatAlignLeft }
            IconButton(onClick = {
                val next = when (style.align) { CellStyle.Align.GENERAL, CellStyle.Align.LEFT -> CellStyle.Align.CENTER; CellStyle.Align.CENTER -> CellStyle.Align.RIGHT; CellStyle.Align.RIGHT -> CellStyle.Align.GENERAL }
                act { style { it.copy(align = next) } }
            }) { Icon(alignIcon, "Alignment", tint = if (style.align != CellStyle.Align.GENERAL) paper.accent else paper.ink) }
            Menu("fill", Icons.Rounded.FormatColorFill, "Fill colour") {
                Swatches(FILL_COLOURS, style.fill) { c -> open = null; act { style { it.copy(fill = c) } } }
            }
            Menu("text", Icons.Rounded.FormatColorText, "Text colour") {
                Swatches(TEXT_COLOURS, style.color) { c -> open = null; act { style { it.copy(color = c) } } }
            }
            Box {
                TextButton(onClick = { open = "fmt" }) { Text("123", style = MaterialTheme.typography.labelLarge, color = if (style.numFmt != null) paper.accent else paper.ink) }
                DropdownMenu(open == "fmt", onDismissRequest = { open = null }) {
                    NUMBER_FORMATS.forEach { (label, code) ->
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (style.numFmt == code) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { open = null; act { style { it.copy(numFmt = code) } } },
                        )
                    }
                }
            }
            IconButton(onClick = { act { style { CellStyle.DEFAULT } } }) { Icon(Icons.Rounded.FormatClear, "Clear formatting") }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            IconButton(onClick = { onCopy(false) }) { Icon(Icons.Rounded.ContentCopy, "Copy") }
            IconButton(onClick = { onCopy(true) }) { Icon(Icons.Rounded.ContentCut, "Cut") }
            IconButton(enabled = canPaste, onClick = onPaste) { Icon(Icons.Rounded.ContentPaste, "Paste") }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            Menu("rows", Icons.Rounded.TableRows, "Rows and columns") {
                item("Insert row above") { insertRows(below = false) }()
                item("Insert row below") { insertRows(below = true) }()
                item("Delete row") { deleteRows() }()
                HorizontalDivider()
                item("Insert column left") { insertCols(right = false) }()
                item("Insert column right") { insertCols(right = true) }()
                item("Delete column") { deleteCols() }()
                HorizontalDivider()
                item("Fill down") { fill(down = true) }()
                item("Fill right") { fill(down = false) }()
                item("Clear contents") { clear() }()
                item(if (editor.sheet.merges.any { m -> m.r1 <= editor.selection.r2 && m.r2 >= editor.selection.r1 && m.c1 <= editor.selection.c2 && m.c2 >= editor.selection.c1 }) "Unmerge cells" else "Merge cells") { toggleMerge() }()
                item(if (editor.sheet.frozenRows + editor.sheet.frozenCols > 0) "Unfreeze panes" else "Freeze above and left of cursor") { toggleFreeze() }()
            }
            Menu("sort", Icons.AutoMirrored.Rounded.Sort, "Sort") {
                item("Sort A → Z by this column") { sort(ascending = true) }()
                item("Sort Z → A by this column") { sort(ascending = false) }()
            }
            TextButton(onClick = { act { autoSum() } }) { Text("Σ", style = MaterialTheme.typography.titleMedium, color = paper.ink) }
            IconButton(onClick = { open = "replace" }) { Icon(Icons.Rounded.FindReplace, "Replace") }
        }
    }
    if (open == "replace") ReplaceDialog(onDismiss = { open = null }) { find, with, matchCase ->
        var n = 0
        act { n = replaceAll(find, with, matchCase) }
        n
    }
}

@Composable
private fun ReplaceDialog(onDismiss: () -> Unit, onReplace: (String, String, Boolean) -> Int) {
    val context = LocalContext.current
    var find by remember { mutableStateOf("") }
    var with by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace in this sheet") },
        text = {
            Column {
                OutlinedTextField(find, { find = it }, label = { Text("Find") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(with, { with = it }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { matchCase = !matchCase }.padding(top = 8.dp)) {
                    Checkbox(matchCase, { matchCase = it })
                    Text("Match case")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = find.isNotEmpty(), onClick = {
                val n = onReplace(find, with, matchCase)
                Toast.makeText(context, if (n == 0) "Nothing found" else "Replaced in $n cell${if (n == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Replace all") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Swatches(colours: List<Int>, selected: Int?, onPick: (Int?) -> Unit) {
    val paper = LocalPaper.current
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        colours.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { c ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                            .then(if (selected == c) Modifier.border(2.dp, paper.accent, CircleShape) else Modifier.border(1.dp, paper.rule, CircleShape))
                            .clickable { onPick(c) },
                    )
                }
            }
        }
        TextButton(onClick = { onPick(null) }) { Text("None") }
    }
}

@Composable
private fun FindBar(editor: SheetEditor, onGo: (Int, Int) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(-1) }
    val hits = remember(query, editor.active, editor.version) {
        if (query.isBlank()) emptyList() else {
            val s = editor.sheet
            s.cells.entries.filter { (_, cell) -> NumFmt.format(cell.value, cell.style.numFmt).contains(query, ignoreCase = true) || cell.input.contains(query, ignoreCase = true) }
                .map { s.row(it.key) to s.col(it.key) }.sortedWith(compareBy({ it.first }, { it.second }))
        }
    }
    fun go(step: Int) {
        if (hits.isEmpty()) return
        index = ((index + step) % hits.size + hits.size) % hits.size
        onGo(hits[index].first, hits[index].second)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it; index = -1 }, singleLine = true, placeholder = { Text("Find in sheet") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { go(1) }),
                modifier = Modifier.weight(1f),
            )
            Text(if (hits.isEmpty()) "0" else "${index + 1}/${hits.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { go(-1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous") }
            IconButton(onClick = { go(1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Next") }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SheetTabs(editor: SheetEditor, tick: Int, allowAdd: Boolean, act: (SheetEditor.() -> Unit) -> Unit, onSwitch: (Int) -> Unit) {
    val paper = LocalPaper.current
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    val names = remember(tick, editor.wb.sheets.size) { editor.wb.sheets.map { it.name } }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                itemsIndexed(names) { i, name ->
                    val on = i == editor.active
                    Box {
                        Text(
                            name, style = MaterialTheme.typography.labelLarge, color = if (on) paper.accent else paper.muted, maxLines = 1,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (on) paper.accent.copy(alpha = 0.12f) else Color.Transparent)
                                .combinedClickable(onClick = { if (!on) onSwitch(i) }, onLongClick = { menuFor = i })
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                        DropdownMenu(menuFor == i, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; renaming = i })
                            if (i > 0) DropdownMenuItem(text = { Text("Move left") }, onClick = { menuFor = null; act { moveSheet(i, i - 1) } })
                            if (i < names.lastIndex) DropdownMenuItem(text = { Text("Move right") }, onClick = { menuFor = null; act { moveSheet(i, i + 1) } })
                            if (names.size > 1) DropdownMenuItem(text = { Text("Delete") }, onClick = { menuFor = null; act { deleteSheet(i) } })
                        }
                    }
                }
            }
            if (allowAdd) IconButton(onClick = { act { addSheet() } }) { Icon(Icons.Rounded.Add, "Add sheet") }
            Spacer(Modifier.width(4.dp))
        }
    }
    renaming?.let { i ->
        var text by remember(i) { mutableStateOf(names.getOrElse(i) { "" }) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename sheet") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    var ok = false
                    act { ok = renameSheet(i, text) }
                    if (ok) renaming = null else Toast.makeText(context, "Use a new name without [ ] : * ? / \\", Toast.LENGTH_SHORT).show()
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

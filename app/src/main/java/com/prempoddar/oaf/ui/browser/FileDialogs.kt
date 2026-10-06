package com.prempoddar.oaf.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import com.prempoddar.oaf.data.SortBy
import com.prempoddar.oaf.files.FileOps
import com.prempoddar.oaf.files.Transfer
import com.prempoddar.oaf.ui.CodeStyle
import com.prempoddar.oaf.ui.design.ChoiceChips
import com.prempoddar.oaf.ui.design.SettingsRow
import com.prempoddar.oaf.ui.design.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** A filled, focused text box for a name; the part before the extension starts selected. */
@Composable
fun NameField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, label: String, onDone: () -> Unit, error: String? = null) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    androidx.compose.material3.TextField(
        value, onChange, label = { Text(label) }, singleLine = true, isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

private fun selectStem(name: String) = TextFieldValue(name, TextRange(0, name.lastIndexOf('.').takeIf { it > 0 } ?: name.length))

private fun badName(name: String, dir: File, except: File? = null): String? = when {
    name.isBlank() -> "Enter a name"
    name.contains('/') -> "Names can't contain /"
    name == "." || name == ".." -> "That name can't be used"
    File(dir, name.trim()).let { it.exists() && it != except } -> "Something here already has that name"
    else -> null
}

@Composable
fun RenameDialog(file: File, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var value by remember { mutableStateOf(selectStem(file.name)) }
    val error = badName(value.text, file.parentFile!!, except = file)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = { NameField(value, { value = it }, "Name", onDone = { if (error == null) onRename(value.text.trim()) }, error.takeIf { value.text != file.name }) },
        confirmButton = { TextButton(enabled = error == null && value.text != file.name, onClick = { onRename(value.text.trim()) }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** New folder or new empty file, with the type chosen by chips. */
@Composable
fun NewItemDialog(dir: File, onDismiss: () -> Unit, onCreate: (name: String, folder: Boolean) -> Unit) {
    var folder by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val error = badName(value.text, dir)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(if (folder) Icons.Rounded.CreateNewFolder else Icons.Rounded.NoteAdd, null) },
        title = { Text(if (folder) "New folder" else "New file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceChips(listOf(true to "Folder", false to "File"), folder, { folder = it })
                NameField(value, { value = it }, if (folder) "Folder name" else "File name, like notes.txt", onDone = { if (error == null) onCreate(value.text.trim(), folder) }, error.takeIf { value.text.isNotEmpty() })
            }
        },
        confirmButton = { TextButton(enabled = error == null, onClick = { onCreate(value.text.trim(), folder) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BatchRenameDialog(files: List<File>, onDismiss: () -> Unit, onRename: (pattern: String, start: Int) -> Unit) {
    var pattern by remember { mutableStateOf(TextFieldValue("* ##", TextRange(0, 4))) }
    var start by remember { mutableStateOf("1") }
    val first = start.toIntOrNull() ?: 1
    val preview = remember(pattern.text, first) { FileOps.batchNames(files, pattern.text, first) }
    val clash = preview.toSet().size != preview.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${files.size} items") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NameField(pattern, { pattern = it }, "Pattern", onDone = {}, error = if (clash) "Add # so every name is different" else null)
                Text("* is the current name, # is a number (## pads to 01, 02 …). Extensions stay.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NameField(TextFieldValue(start, TextRange(start.length)), { start = it.text.filter(Char::isDigit).take(6) }, "Start at", onDone = {})
                Text("Preview", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                preview.take(4).zip(files).forEach { (n, f) ->
                    Text("${f.name}  →  $n", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (files.size > 4) Text("and ${files.size - 4} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = !clash && pattern.text.isNotBlank(), onClick = { onRename(pattern.text, first) }) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConflictDialog(question: Clipboard.Question) {
    var all by remember(question) { mutableStateOf(false) }
    fun answer(r: Transfer.Resolution) = question.answer.complete(r to all)
    AlertDialog(
        onDismissRequest = { answer(Transfer.Resolution.SKIP) },
        title = { Text("“${question.existing.name}” is already here") },
        text = {
            Column {
                Text("Replace it (the old one goes to the bin), skip this item, or keep both with a new name.")
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(all, { all = it })
                    Text("Do this for every clash")
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { answer(Transfer.Resolution.SKIP) }) { Text("Skip") }
                TextButton(onClick = { answer(Transfer.Resolution.KEEP_BOTH) }) { Text("Keep both") }
                TextButton(onClick = { answer(Transfer.Resolution.REPLACE) }) { Text("Replace") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(sort: SortBy, foldersFirst: Boolean, showHidden: Boolean, grid: Boolean, onSort: (SortBy) -> Unit, onFoldersFirst: (Boolean) -> Unit, onHidden: (Boolean) -> Unit, onGrid: (Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("View", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text("Sort by", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp))
            ChoiceChips(listOf(SortBy.NAME to "Name", SortBy.DATE to "Newest", SortBy.SIZE to "Largest", SortBy.TYPE to "Type"), sort, onSort, Modifier.padding(horizontal = 20.dp))
            Text("Layout", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp))
            ChoiceChips(listOf(false to "List", true to "Grid"), grid, onGrid, Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(top = 12.dp)) {
                SwitchRow("Folders first", checked = foldersFirst, onChange = onFoldersFirst)
                SwitchRow("Show hidden files", "Names starting with a dot", checked = showHidden, onChange = onHidden)
            }
        }
    }
}

/** Everything about one item, with checksums worked out only when asked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(file: File, onDismiss: () -> Unit) {
    val info by produceState<List<Pair<String, String>>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val dir = file.isDirectory
            val files = if (dir) file.walkTopDown().count { it.isFile } else 0
            listOfNotNull(
                "Name" to file.name,
                "Where" to (file.parent ?: "/"),
                "Type" to if (dir) "Folder" else (android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: kindOf(file).name.lowercase()),
                "Size" to "${humanSize(FileOps.size(file))}${if (!dir) " (" + "%,d".format(file.length()) + " bytes)" else ""}",
                if (dir) "Contains" to "$files files" else null,
                "Modified" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified())),
                "Hidden" to if (file.name.startsWith(".")) "Yes" else "No",
                "Permissions" to listOf(if (file.canRead()) "read" else null, if (file.canWrite()) "write" else null).filterNotNull().joinToString(", ").ifEmpty { "none" },
            )
        }
    }
    val hashes = remember { mutableStateMapOf<String, String>() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            item { Text("Details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
            items(info.orEmpty().size) { i ->
                val (k, v) = info!![i]
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(v, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            if (file.isFile) {
                listOf("MD5", "SHA-1", "SHA-256").forEach { algo ->
                    item(key = algo) {
                        val h = hashes[algo]
                        if (h == null) {
                            SettingsRow(algo, "Tap to work it out", onClick = { hashes[algo] = "…" })
                        } else {
                            if (h == "…") LaunchedEffect(algo) { hashes[algo] = withContext(Dispatchers.IO) { runCatching { FileOps.hash(file, algo) }.getOrElse { "Couldn't read the file" } } }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                                Text(algo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer { Text(if (h == "…") "Working…" else h, style = CodeStyle) }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.prempoddar.oaf.ui.browser

import com.prempoddar.oaf.ui.design.tap
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Lock
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.archive.Archives
import com.prempoddar.oaf.archive.Compressor
import com.prempoddar.oaf.files.ClipMode
import com.prempoddar.oaf.files.FileOps
import com.prempoddar.oaf.format.FileKind
import java.io.File

fun fileUri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

private fun mimeOf(f: File) = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

fun share(context: Context, file: File) = shareAll(context, listOf(file))

fun shareAll(context: Context, files: List<File>) {
    val plain = files.filter { it.isFile }
    if (plain.isEmpty()) {
        Toast.makeText(context, "Folders can't be shared; compress them first", Toast.LENGTH_SHORT).show()
        return
    }
    val uris = ArrayList(plain.map { fileUri(context, it) })
    val mimes = plain.map(::mimeOf).distinct()
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).setType(mimes.first()).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).setType(if (mimes.size == 1) mimes.first() else "*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, if (plain.size == 1) "Share ${plain.first().name}" else "Share ${plain.size} files"))
}

fun openWith(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(fileUri(context, file), mimeOf(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Open ${file.name} with"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No other app can open this", Toast.LENGTH_SHORT).show()
    }
}

private fun isArchive(f: File) = f.isFile && kindOf(f).family == FileKind.Family.ARCHIVE

fun toast(context: Context): (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }

fun compress(context: Context, items: List<File>, options: Compressor.Options) {
    val dir = items.first().parentFile ?: return
    AppGraph.clipboard.run("Compressing", toast(context)) { Compressor.compress(items, dir, options) }
}

/** Extracts beside the archive; a locked archive calls [onLocked] (wrong = the password failed). */
fun extract(context: Context, archive: File, password: String? = null, onLocked: (Boolean) -> Unit = {}) {
    AppGraph.clipboard.run("Extracting", toast(context)) {
        val head = archive.inputStream().use { s -> ByteArray(512).also { s.read(it) } }
        val type = Archives.typeOf(archive.name, head) ?: throw java.io.IOException("This archive type can't be extracted")
        val stem = archive.name.substringBefore(".tar.").substringBeforeLast('.')
        try {
            Archives.list(archive, type, password)
        } catch (e: Archives.PasswordNeeded) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onLocked(e.wrong) }
            return@run
        }
        val dir = FileOps.freeName(archive.parentFile!!, stem).apply { mkdirs() }
        try { Archives.extractAll(archive, type, dir, password = password) } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }
}

/** Asks before sending items to the bin, unless the user turned that off. */
@Composable
fun DeleteDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${if (count == 1) "this item" else "$count items"} to the bin?") },
        text = { Text("You can put things back from the bin for 30 days.") },
        confirmButton = { TextButton(onClick = tap(onConfirm)) { Text("Move to bin") } },
        dismissButton = { TextButton(onClick = tap(onDismiss)) { Text("Cancel") } },
    )
}

/** What can be done with the selected items. [onMore] items appear in the overflow menu. */
@Composable
fun SelectionBar(
    selected: List<File>,
    onDone: () -> Unit,
    onRename: (File) -> Unit,
    onBatchRename: (List<File>) -> Unit,
    onDetails: (File) -> Unit,
    extra: @Composable (closeMenu: () -> Unit) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by AppGraph.prefs.settings.collectAsStateWithLifecycle(null)
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var compressing by remember { mutableStateOf<List<File>?>(null) }
    var locked by remember { mutableStateOf<Pair<File, Boolean>?>(null) }
    val one = selected.singleOrNull()
    val picker = com.prempoddar.oaf.pick.LocalPicker.current
    if (picker != null) {
        // Choosing for another app: the only thing to do with a selection is send it.
        androidx.compose.runtime.DisposableEffect(Unit) { picker.selecting = true; onDispose { picker.selecting = false } }
        val ok = selected.filter { it.isFile && picker.accepts(it) }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = tap(onDone)) { Text("Clear") }
                Text(
                    if (ok.size == selected.size) "${ok.size} chosen" else "${ok.size} of ${selected.size} can be sent",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Button(enabled = ok.isNotEmpty() && (picker.multiple || ok.size == 1), onClick = { picker.send(ok) }) {
                    Text(if (picker.multiple) "Send ${ok.size}" else "Send")
                }
            }
        }
        return
    }
    fun delete() {
        val items = selected.toList()
        onDone()
        AppGraph.clipboard.run("Moving to the bin", toast(context)) { items.forEach { AppGraph.bin.delete(it) } }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Action(Icons.Rounded.ContentCopy, "Copy") { AppGraph.clipboard.copy(selected.toList()); onDone() }
            Action(Icons.Rounded.ContentCut, "Cut") { AppGraph.clipboard.cut(selected.toList()); onDone() }
            Action(Icons.Rounded.Share, "Share") { shareAll(context, selected.toList()) }
            Action(Icons.Rounded.Delete, "Delete") { if (settings?.confirmDelete != false) confirm = true else delete() }
            androidx.compose.foundation.layout.Box {
                Action(Icons.Rounded.MoreVert, "More") { menu = true }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (one != null) DropdownMenuItem(text = { Text("Rename") }, onClick = tap { menu = false; onRename(one) })
                    if (selected.size > 1) DropdownMenuItem(text = { Text("Rename all…") }, onClick = tap { menu = false; onBatchRename(selected.toList()) })
                    DropdownMenuItem(text = { Text("Compress…") }, onClick = tap { menu = false; compressing = selected.toList() })
                    if (one != null && isArchive(one)) DropdownMenuItem(text = { Text("Extract here") }, onClick = tap { menu = false; extract(context, one) { wrong -> locked = one to wrong }; onDone() })
                    if (one != null && one.isFile) DropdownMenuItem(text = { Text("Open with…") }, onClick = tap { menu = false; openWith(context, one) })
                    if (one != null) DropdownMenuItem(text = { Text("Details") }, onClick = tap { menu = false; onDetails(one) })
                    extra { menu = false }
                }
            }
        }
    }
    if (confirm) DeleteDialog(selected.size, onDismiss = { confirm = false }, onConfirm = { confirm = false; delete() })
    compressing?.let { items ->
        CompressDialog(items, onDismiss = { compressing = null }) { options -> compressing = null; compress(context, items, options); onDone() }
    }
    locked?.let { (file, wrong) ->
        PasswordDialog(file.name, wrong, onDismiss = { locked = null }) { pw -> locked = null; extract(context, file, pw) { w -> locked = file to w } }
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = tap(onClick)) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown while a clip waits to be pasted, and while any file job runs. */
@Composable
fun ClipAndJobBar(pasteInto: File?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clip by AppGraph.clipboard.clip.collectAsStateWithLifecycle()
    val job by AppGraph.clipboard.job.collectAsStateWithLifecycle()
    val question by AppGraph.clipboard.question.collectAsStateWithLifecycle()
    question?.let { ConflictDialog(it) }
    val j = job
    val c = clip
    if (j == null && c == null) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 4.dp, shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
            if (j != null) {
                val p = j.progress
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(j.label + "…", style = MaterialTheme.typography.titleSmall)
                        if (p != null && p.total > 0) Text("${humanSize(p.done)} of ${humanSize(p.total)}" + if (p.current.isNotEmpty()) " · ${p.current}" else "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = tap { AppGraph.clipboard.cancel() }) { Text("Cancel") }
                }
                if (p != null && p.total > 0) LinearProgressIndicator(progress = { (p.done.toFloat() / p.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp))
                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp))
            } else if (c != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (c.mode == ClipMode.CUT) Icons.Rounded.ContentCut else Icons.Rounded.ContentCopy, null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${c.files.size} item${if (c.files.size == 1) "" else "s"} to ${if (c.mode == ClipMode.CUT) "move" else "copy"}",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = tap { AppGraph.clipboard.clear() }) { Text("Cancel") }
                    Button(enabled = pasteInto != null, onClick = tap { pasteInto?.let { AppGraph.clipboard.paste(it, toast(context)) } }) {
                        Icon(Icons.Rounded.ContentPaste, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (pasteInto == null) "Open a folder" else "Paste")
                    }
                }
            }
        }
    }
}

/** Compression with the choices that matter: name, format, how hard to squeeze, and a password. */
@Composable
fun CompressDialog(items: List<File>, onDismiss: () -> Unit, onCompress: (Compressor.Options) -> Unit) {
    var name by remember { mutableStateOf(if (items.size == 1) items.first().nameWithoutExtension else "Archive") }
    var format by remember { mutableStateOf(Compressor.Format.ZIP) }
    var level by remember { mutableStateOf(Compressor.Level.NORMAL) }
    var lock by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val mismatch = lock && again.isNotEmpty() && again != password
    val ok = name.isNotBlank() && !name.contains('/') && (!lock || (password.length >= 4 && password == again))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress ${if (items.size == 1) "“${items.first().name}”" else "${items.size} items"}") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { com.prempoddar.oaf.ui.design.PillTextField(name, { name = it }, "Name", supporting = "Saved as $name.${format.ext}") }
                item {
                    Text("Format", style = MaterialTheme.typography.titleSmall)
                    com.prempoddar.oaf.ui.design.ChoiceChips(Compressor.Format.entries.map { it to it.label }, format, { format = it; if (!it.passwords) lock = false })
                }
                item {
                    Text("Compression", style = MaterialTheme.typography.titleSmall)
                    com.prempoddar.oaf.ui.design.ChoiceChips(Compressor.Level.entries.map { it to it.label }, level, { level = it })
                    Text(
                        when (level) { Compressor.Level.STORE -> "Fastest; files keep their size."; Compressor.Level.FAST -> "Quick, a little smaller."; Compressor.Level.NORMAL -> "A good balance."; Compressor.Level.MAXIMUM -> "Smallest file; takes longest." },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Password", style = MaterialTheme.typography.titleSmall)
                            Text(if (format.passwords) "Locked with AES-256" else "Only ZIP archives can have a password", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(lock, { lock = it }, enabled = format.passwords)
                    }
                }
                if (lock) {
                    item {
                        val vt = if (show) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
                        val kb = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.prempoddar.oaf.ui.design.PillTextField(password, { password = it }, "Password", clearable = false, keyboardOptions = kb, visualTransformation = vt,
                                supporting = if (password.isNotEmpty() && password.length < 4) "At least 4 characters" else null,
                                trailing = { IconButton(onClick = tap { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password") } })
                            com.prempoddar.oaf.ui.design.PillTextField(again, { again = it }, "Type it again", clearable = false, keyboardOptions = kb, visualTransformation = vt,
                                isError = mismatch, supporting = if (mismatch) "The passwords don't match" else null)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = tap { onCompress(Compressor.Options(name.trim(), format, level, password.takeIf { lock })) }) { Text("Compress") } },
        dismissButton = { TextButton(onClick = tap(onDismiss)) { Text("Cancel") } },
    )
}

@Composable
fun PasswordDialog(name: String, wrong: Boolean, onDismiss: () -> Unit, onUnlock: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("“$name” is locked") },
        text = {
            Column {
                Text(if (wrong) "That password didn't work. Try again." else "Enter its password to extract it.")
                com.prempoddar.oaf.ui.design.PillTextField(
                    value, { value = it }, "Password", Modifier.padding(top = 12.dp), isError = wrong, clearable = false,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailing = { IconButton(onClick = tap { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password") } },
                )
            }
        },
        confirmButton = { TextButton(enabled = value.isNotEmpty(), onClick = tap { onUnlock(value) }) { Text("Extract") } },
        dismissButton = { TextButton(onClick = tap(onDismiss)) { Text("Cancel") } },
    )
}

package com.prempoddar.oaf.ui.browser

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

fun compress(context: Context, items: List<File>) {
    val dir = items.first().parentFile ?: return
    AppGraph.clipboard.run("Compressing", toast(context)) {
        FileOps.zip(items, FileOps.freeName(dir, (if (items.size == 1) items.first().nameWithoutExtension else "Archive") + ".zip"))
    }
}

fun extract(context: Context, archive: File) {
    AppGraph.clipboard.run("Extracting", toast(context)) {
        val head = archive.inputStream().use { s -> ByteArray(8).also { s.read(it) } }
        val type = Archives.typeOf(archive.name, head) ?: throw java.io.IOException("This archive type can't be extracted")
        val stem = archive.name.substringBefore(".tar.").substringBeforeLast('.')
        Archives.extractAll(archive, type, FileOps.freeName(archive.parentFile!!, stem).apply { mkdirs() })
    }
}

/** Asks before sending items to the bin, unless the user turned that off. */
@Composable
fun DeleteDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${if (count == 1) "this item" else "$count items"} to the bin?") },
        text = { Text("You can put things back from the bin for 30 days.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Move to bin") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
    val one = selected.singleOrNull()
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
                    if (one != null) DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename(one) })
                    if (selected.size > 1) DropdownMenuItem(text = { Text("Rename all…") }, onClick = { menu = false; onBatchRename(selected.toList()) })
                    DropdownMenuItem(text = { Text("Compress to ZIP") }, onClick = { menu = false; compress(context, selected.toList()); onDone() })
                    if (one != null && isArchive(one)) DropdownMenuItem(text = { Text("Extract here") }, onClick = { menu = false; extract(context, one); onDone() })
                    if (one != null && one.isFile) DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; openWith(context, one) })
                    if (one != null) DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onDetails(one) })
                    extra { menu = false }
                }
            }
        }
    }
    if (confirm) DeleteDialog(selected.size, onDismiss = { confirm = false }, onConfirm = { confirm = false; delete() })
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label) }
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
                    TextButton(onClick = { AppGraph.clipboard.cancel() }) { Text("Cancel") }
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
                    TextButton(onClick = { AppGraph.clipboard.clear() }) { Text("Cancel") }
                    Button(enabled = pasteInto != null, onClick = { pasteInto?.let { AppGraph.clipboard.paste(it, toast(context)) } }) {
                        Icon(Icons.Rounded.ContentPaste, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (pasteInto == null) "Open a folder" else "Paste")
                    }
                }
            }
        }
    }
}

package com.prempoddar.oaf.ui.viewers

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.prempoddar.oaf.archive.ArchiveEntry
import com.prempoddar.oaf.archive.ArchiveType
import com.prempoddar.oaf.archive.Archives
import com.prempoddar.oaf.files.FileOps
import com.prempoddar.oaf.format.Detector
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.io.readNBytesCompat
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.browser.KindMark
import com.prempoddar.oaf.ui.browser.humanSize
import com.prempoddar.oaf.ui.rememberChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private sealed interface ArchiveState {
    class Ready(val file: File, val type: ArchiveType, val entries: List<ArchiveEntry>) : ArchiveState
    class Failed(val reason: String) : ArchiveState
}

/** Kind from the name alone, for showing what's inside without unpacking it. */
private fun kindOf(name: String): FileKind = runCatching { Detector.detect(name, null, ByteArray(0), { emptyList() }, { null }).kind }.getOrDefault(FileKind.UNKNOWN)

/** Browses an archive like a folder; files open straight from it, and it can be unpacked. */
@Composable
fun ArchiveScreen(source: DocSource, onBack: () -> Unit, onOpen: (DocSource) -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val state by produceState<ArchiveState?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = source.local(context)
                val type = Archives.typeOf(source.name, file.inputStream().use { it.readNBytesCompat(512) }) ?: error("OAF doesn't know this kind of archive")
                ArchiveState.Ready(file, type, Archives.list(file, type))
            }.getOrElse { ArchiveState.Failed(it.message ?: "Couldn't read this archive") }
        }
    }
    var folder by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val cache = remember { File(context.cacheDir, "archive/${source.uri.toString().hashCode().toUInt()}") }

    fun open(s: ArchiveState.Ready, e: ArchiveEntry) = scope.launch {
        working = true
        val f = File(cache, e.path)
        val ok = withContext(Dispatchers.IO) { runCatching { if (!f.exists() || f.length() != e.size) Archives.extract(s.file, s.type, e.path, f) } }
        working = false
        ok.onSuccess { onOpen(DocSource.fromFile(f)) }.onFailure { Toast.makeText(context, it.message ?: "Couldn't open ${e.name}", Toast.LENGTH_SHORT).show() }
    }

    val extractTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        val s = state as? ArchiveState.Ready ?: return@rememberLauncherForActivityResult
        tree ?: return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    // Unpacked into the cache first, then copied into the chosen folder.
                    val temp = File(context.cacheDir, "unpack").apply { deleteRecursively(); mkdirs() }
                    val count = Archives.extractAll(s.file, s.type, temp, folder)
                    val root = DocumentFile.fromTreeUri(context, tree) ?: error("Can't write there")
                    val base = root.createDirectory(source.name.substringBefore('.').ifBlank { "Extracted" }) ?: root
                    copyInto(context, temp, base)
                    temp.deleteRecursively()
                    count
                }
            }
            working = false
            Toast.makeText(context, n.fold({ "Extracted $it file${if (it == 1) "" else "s"}" }, { it.message ?: "Couldn't extract" }), Toast.LENGTH_LONG).show()
        }
    }

    fun extractHere(s: ArchiveState.Ready) = scope.launch {
        working = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val parent = source.file!!.parentFile!!
                val dir = FileOps.freeName(parent, source.name.substringBefore('.').ifBlank { "Extracted" }).apply { mkdirs() }
                Archives.extractAll(s.file, s.type, dir, folder) to dir
            }
        }
        working = false
        Toast.makeText(context, result.fold({ (n, dir) -> "Extracted $n file${if (n == 1) "" else "s"} to ${dir.name}" }, { it.message ?: "Couldn't extract" }), Toast.LENGTH_LONG).show()
    }

    BackHandler(enabled = folder.isNotEmpty()) { folder = folder.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } }

    ViewerScaffold(
        title = source.name,
        subtitle = when (val s = state) {
            is ArchiveState.Ready -> (if (folder.isEmpty()) "" else "/${folder.trimEnd('/')} · ") + "${s.entries.count { !it.isDirectory }} files · ${s.type.name.replace('_', ' ').lowercase()}"
            else -> "Archive"
        },
        chrome = rememberChrome(), autoHide = false, onBack = onBack,
        actions = {
            val s = state as? ArchiveState.Ready
            if (working) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            if (s != null) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    val what = if (folder.isEmpty()) "everything" else "this folder"
                    val parentWritable = source.file?.parentFile?.canWrite() == true
                    if (parentWritable) DropdownMenuItem(text = { Text("Extract $what here") }, leadingIcon = { Icon(Icons.Rounded.Unarchive, null) }, onClick = { menu = false; extractHere(s) })
                    DropdownMenuItem(text = { Text("Extract $what to…") }, onClick = { menu = false; extractTree.launch(null) })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareSource(context, source) })
                    DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; openElsewhere(context, source) })
                }
            }
        },
    ) {
        val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 64.dp
        when (val s = state) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is ArchiveState.Failed -> Text(s.reason, color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = top))
            is ArchiveState.Ready -> {
                val here = remember(s, folder) {
                    s.entries.filter { it.parent == folder.trimEnd('/') && it.path != folder }
                        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                }
                if (here.isEmpty()) Text("Nothing in here", color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = top))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 4.dp, bottom = 24.dp)) {
                    items(here, key = { it.path }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (e.isDirectory) folder = e.path.trimEnd('/') + "/" else open(s, e) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            KindMark(if (e.isDirectory) null else kindOf(e.name))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val bits = listOfNotNull(
                                    if (e.isDirectory) "${s.entries.count { it.parent == e.path.trimEnd('/') }} items" else e.size.takeIf { it >= 0 }?.let { humanSize(it) },
                                    e.modified?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
                                )
                                Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = paper.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyInto(context: Context, from: File, to: DocumentFile) {
    from.listFiles()?.forEach { f ->
        if (f.isDirectory) copyInto(context, f, to.findFile(f.name)?.takeIf { it.isDirectory } ?: to.createDirectory(f.name) ?: return@forEach)
        else {
            val target = to.createFile("application/octet-stream", f.name) ?: return@forEach
            context.contentResolver.openOutputStream(target.uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        }
    }
}

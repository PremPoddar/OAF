package com.prempoddar.oaf.ui.browser

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.data.OafSettings
import com.prempoddar.oaf.data.SortBy
import com.prempoddar.oaf.data.ThemeMode
import com.prempoddar.oaf.files.FileOps
import com.prempoddar.oaf.format.Detector
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.familyColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private class Entry(val file: File, val kind: FileKind?) {
    val isDir get() = kind == null
}

private enum class Pending { COPY, MOVE }

private fun kindOf(f: File) = Detector.detect(f.name, null, ByteArray(0)).kind

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(onOpen: (DocSource) -> Unit, onBin: () -> Unit, onStorage: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.prefs.settings.collectAsStateWithLifecycle(OafSettings())
    var access by remember { mutableStateOf(Storage.hasAllFilesAccess()) }
    LifecycleResumeEffect(Unit) { access = Storage.hasAllFilesAccess(); onPauseOrDispose { } }
    val legacyAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { access = Storage.hasAllFilesAccess() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        onOpen(DocSource.fromUri(context, uri))
    }

    var dir by remember { mutableStateOf<File?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<File>() }
    var pending by remember { mutableStateOf<Pair<Pending, List<File>>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var menu by remember { mutableStateOf(false) }

    BackHandler(enabled = selected.isNotEmpty() || query != null || dir != null) {
        when {
            selected.isNotEmpty() -> selected.clear()
            query != null -> query = null
            else -> {
                val parent = dir?.parentFile
                dir = if (parent == null || Storage.roots(context).none { parent.path.startsWith(it.dir.path) }) null else parent
            }
        }
    }

    val entries by produceState<List<Entry>?>(null, dir, refresh, settings.sort, settings.showHidden, query) {
        val d = dir
        value = if (d == null) null else withContext(Dispatchers.IO) {
            val q = query?.trim()?.lowercase()
            val files = if (!q.isNullOrEmpty()) {
                d.walkTopDown().maxDepth(8).filter { it != d && it.name.lowercase().contains(q) }.take(500).toList()
            } else {
                d.listFiles()?.toList().orEmpty()
            }
            files.filter { settings.showHidden || !it.name.startsWith(".") }
                .map { Entry(it, if (it.isDirectory) null else kindOf(it)) }
                .sortedWith(compareBy<Entry> { !it.isDir }.then(
                    when (settings.sort) {
                        SortBy.NAME -> compareBy { it.file.name.lowercase() }
                        SortBy.DATE -> compareByDescending { it.file.lastModified() }
                        SortBy.SIZE -> compareByDescending { it.file.length() }
                        SortBy.TYPE -> compareBy<Entry> { it.kind?.ordinal ?: -1 }.thenBy { it.file.name.lowercase() }
                    },
                ))
        }
    }

    fun run(label: String, block: suspend () -> Unit) {
        busy = label
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }.onFailure { Toast.makeText(context, it.message ?: "That didn't work", Toast.LENGTH_LONG).show() }
            busy = null
            selected.clear()
            refresh++
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(paper.page)) {
        // Header
        if (selected.isNotEmpty()) {
            Surface(color = paper.chrome) {
                Row(Modifier.fillMaxWidth().padding(top = top).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected.clear() }) { Icon(Icons.Rounded.Close, "Clear selection") }
                    Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { pending = Pending.COPY to selected.toList(); selected.clear() }) { Icon(Icons.Rounded.ContentCopy, "Copy") }
                    IconButton(onClick = { pending = Pending.MOVE to selected.toList(); selected.clear() }) { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, "Move") }
                    if (selected.size == 1) IconButton(onClick = { renaming = selected.first() }) { Icon(Icons.Rounded.DriveFileRenameOutline, "Rename") }
                    IconButton(onClick = {
                        val items = selected.toList()
                        run("Zipping…") { FileOps.zip(items, FileOps.freeName(items.first().parentFile!!, (if (items.size == 1) items.first().nameWithoutExtension else "Archive") + ".zip")) }
                    }) { Icon(Icons.Rounded.FolderZip, "Compress to ZIP") }
                    IconButton(onClick = {
                        val items = selected.toList()
                        run("Moving to the bin…") { items.forEach { AppGraph.bin.delete(it) } }
                        Toast.makeText(context, "Moved to the bin", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = top + 8.dp, start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dir != null) IconButton(onClick = { dir = dir?.parentFile?.takeIf { p -> Storage.roots(context).any { p.path.startsWith(it.dir.path) } } }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Up")
                }
                Column(Modifier.weight(1f)) {
                    Text(dir?.name?.takeIf { d -> Storage.roots(context).none { it.dir == dir } } ?: if (dir == null) "OAF" else "Phone", style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (dir == null) Text("Open all files", style = MaterialTheme.typography.bodyMedium, color = paper.muted)
                }
                if (dir != null) IconButton(onClick = { query = if (query == null) "" else null }) { Icon(Icons.Rounded.Search, "Search this folder") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Open a file…") }, onClick = { menu = false; pick.launch(arrayOf("*/*")) })
                        Text("SORT BY", style = MaterialTheme.typography.labelSmall, color = paper.faint, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                        SortBy.entries.forEach { s ->
                            DropdownMenuItem(text = { Text((if (settings.sort == s) "✓ " else "   ") + s.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = {
                                menu = false; scope.launch { AppGraph.prefs.update { it.copy(sort = s) } }
                            })
                        }
                        DropdownMenuItem(text = { Text(if (settings.showHidden) "Hide hidden files" else "Show hidden files") }, onClick = {
                            menu = false; scope.launch { AppGraph.prefs.update { it.copy(showHidden = !it.showHidden) } }
                        })
                        Text("THEME", style = MaterialTheme.typography.labelSmall, color = paper.faint, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                        ThemeMode.entries.forEach { t ->
                            DropdownMenuItem(text = { Text((if (settings.theme == t) "✓ " else "   ") + t.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = {
                                menu = false; scope.launch { AppGraph.prefs.update { it.copy(theme = t) } }
                            })
                        }
                        DropdownMenuItem(text = { Text("Bin") }, onClick = { menu = false; onBin() })
                        DropdownMenuItem(text = { Text("Storage") }, onClick = { menu = false; onStorage() })
                    }
                }
            }
            if (query != null) {
                TextField(
                    query!!, { query = it }, placeholder = { Text("Search in ${dir?.name}") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.medium,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = paper.page, unfocusedIndicatorColor = paper.page),
                )
            }
            dir?.let { d -> Breadcrumbs(d, context) { dir = it } }
        }
        busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        if (!access) {
            NoAccess(
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null)))
                        }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    } else {
                        legacyAsk.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                },
                onPick = { pick.launch(arrayOf("*/*")) },
                recents = settings.recents.map { it.name to it.uri },
                onRecent = { uri -> onOpen(DocSource.fromUri(context, Uri.parse(uri))) },
            )
            return@Column
        }

        val list = entries
        if (dir == null) {
            Home(settings, context, onFolder = { dir = it }, onRecent = { uri ->
                val u = Uri.parse(uri)
                onOpen(if (u.scheme == "file") DocSource.fromFile(File(u.path!!)) else DocSource.fromUri(context, u))
            })
        } else if (list == null) {
            Box(Modifier.fillMaxSize())
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = bottom + if (pending != null) 80.dp else 16.dp)) {
                if (list.isEmpty()) item {
                    Text(if (query.isNullOrBlank()) "This folder is empty." else "Nothing here matches.", color = paper.muted, modifier = Modifier.padding(24.dp))
                }
                items(list, key = { it.file.path }) { e ->
                    val isSel = e.file in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    when {
                                        selected.isNotEmpty() -> if (isSel) selected.remove(e.file) else selected.add(e.file)
                                        e.isDir -> { dir = e.file; query = null }
                                        else -> onOpen(DocSource.fromFile(e.file))
                                    }
                                },
                                onLongClick = { if (isSel) selected.remove(e.file) else selected.add(e.file) },
                            )
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else paper.page)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KindMark(e.kind)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    if (e.isDir) "${e.file.list()?.size ?: 0} items" else humanSize(e.file.length()),
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.file.lastModified())),
                                    if (!query.isNullOrBlank()) e.file.parentFile?.name else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = paper.muted,
                            )
                        }
                        if (selected.isNotEmpty()) Checkbox(isSel, { if (isSel) selected.remove(e.file) else selected.add(e.file) })
                        else if (e.isDir) {
                            val fav = e.file.path in settings.favourites
                            IconButton(onClick = {
                                scope.launch { AppGraph.prefs.update { s -> s.copy(favourites = if (fav) s.favourites - e.file.path else s.favourites + e.file.path) } }
                            }) { Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (fav) "Remove from favourites" else "Add to favourites", tint = if (fav) paper.accent else paper.faint) }
                        } else {
                            IconButton(onClick = { share(context, e.file) }) { Icon(Icons.Rounded.Share, "Share", tint = paper.faint) }
                        }
                    }
                }
            }
        }

        // Paste bar for a pending copy or move.
        pending?.let { (mode, files) ->
            Surface(color = paper.chrome, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = bottom), verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (mode == Pending.COPY) "Copy" else "Move"} ${files.size} item${if (files.size > 1) "s" else ""} here?", modifier = Modifier.weight(1f))
                    TextButton(onClick = { pending = null }) { Text("Cancel") }
                    Button(enabled = dir != null, onClick = {
                        val target = dir!!
                        pending = null
                        run(if (mode == Pending.COPY) "Copying…" else "Moving…") {
                            files.forEach { if (mode == Pending.COPY) FileOps.copy(it, target) else FileOps.move(it, target) }
                        }
                    }) { Text(if (mode == Pending.COPY) "Paste" else "Move here") }
                }
            }
        }
    }

    renaming?.let { f ->
        var name by remember { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming = null; run("Renaming…") { FileOps.rename(f, name) } }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Breadcrumbs(dir: File, context: android.content.Context, onGo: (File) -> Unit) {
    val paper = LocalPaper.current
    val root = Storage.roots(context).firstOrNull { dir.path.startsWith(it.dir.path) } ?: return
    val parts = generateSequence(dir) { it.parentFile }.takeWhile { it.path.length >= root.dir.path.length }.toList().reversed()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, f ->
            if (i > 0) Text(" / ", color = paper.faint, style = MaterialTheme.typography.bodySmall)
            Text(
                if (f == root.dir) root.label else f.name,
                style = MaterialTheme.typography.bodySmall, color = if (i == parts.lastIndex) paper.ink else paper.accent,
                modifier = Modifier.clip(CircleShape).combinedClickableCompat { onGo(f) }.padding(4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.then(Modifier.combinedClickable(onClick = onClick))

@Composable
fun KindMark(kind: FileKind?) {
    val paper = LocalPaper.current
    val color = if (kind == null) paper.accent else familyColor(kind, paper.dark)
    Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        if (kind == null) Icon(Icons.Outlined.Folder, null, tint = color)
        else Text(extLabel(kind), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun extLabel(k: FileKind) = when (k) {
    FileKind.PDF -> "PDF"; FileKind.DOCX, FileKind.DOC -> "DOC"; FileKind.ODT -> "ODT"; FileKind.RTF -> "RTF"; FileKind.EPUB -> "EPUB"
    FileKind.XLSX, FileKind.XLS -> "XLS"; FileKind.ODS -> "ODS"; FileKind.CSV, FileKind.TSV -> "CSV"
    FileKind.PPTX, FileKind.PPT -> "PPT"; FileKind.ODP -> "ODP"; FileKind.MARKDOWN -> "MD"; FileKind.HTML -> "HTML"
    FileKind.JSON -> "{ }"; FileKind.XML -> "XML"; FileKind.YAML -> "YML"; FileKind.CODE -> "</>"; FileKind.TEXT -> "TXT"
    FileKind.IMAGE -> "IMG"; FileKind.SVG -> "SVG"; FileKind.AUDIO -> "♪"; FileKind.VIDEO -> "▶"
    FileKind.ZIP, FileKind.SEVEN_Z, FileKind.TAR, FileKind.GZIP, FileKind.BZIP2, FileKind.XZ, FileKind.RAR -> "ZIP"
    FileKind.APK -> "APK"; FileKind.FONT -> "Aa"; FileKind.UNKNOWN -> "•••"
}

@Composable
private fun Home(settings: OafSettings, context: android.content.Context, onFolder: (File) -> Unit, onRecent: (String) -> Unit) {
    val paper = LocalPaper.current
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (settings.recents.isNotEmpty()) {
            item { Label("Recent") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(settings.recents.take(20), key = { it.uri }) { r ->
                        val kind = runCatching { FileKind.valueOf(r.kind) }.getOrDefault(FileKind.UNKNOWN)
                        Column(
                            Modifier.width(132.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
                                .combinedClickableCompat { onRecent(r.uri) }.padding(12.dp),
                        ) {
                            KindMark(kind)
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).height(40.dp))
                        }
                    }
                }
            }
        }
        if (settings.favourites.isNotEmpty()) {
            item { Label("Favourites") }
            items(settings.favourites, key = { "fav$it" }) { path -> FolderRow(File(path).name, path, onClick = { onFolder(File(path)) }) }
        }
        item { Label("Storage") }
        items(Storage.roots(context), key = { "root" + it.dir.path }) { r -> FolderRow(r.label, humanSize(r.dir.freeSpace) + " free", onClick = { onFolder(r.dir) }) }
        item { Label("Folders") }
        items(Storage.quickFolders(), key = { "q" + it.dir.path }) { r -> FolderRow(r.label, null, onClick = { onFolder(r.dir) }) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalPaper.current.faint, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun FolderRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val paper = LocalPaper.current
    Row(Modifier.fillMaxWidth().combinedClickableCompat(onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        KindMark(null)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = paper.muted)
        }
    }
}

@Composable
private fun NoAccess(onAllow: () -> Unit, onPick: () -> Unit, recents: List<Pair<String, String>>, onRecent: (String) -> Unit) {
    val paper = LocalPaper.current
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text("See every file on your phone", style = MaterialTheme.typography.headlineSmall)
        Text(
            "To browse folders, OAF needs Android's “All files access”. It only reads what you open, and never sends anything anywhere. You can also just pick a file.",
            style = MaterialTheme.typography.bodyMedium, color = paper.muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text("Allow access to files") }
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null); Text("  Pick a file instead")
        }
        if (recents.isNotEmpty()) {
            Text("RECENT", style = MaterialTheme.typography.labelSmall, color = paper.faint, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
            recents.take(10).forEach { (name, uri) ->
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().combinedClickableCompat { onRecent(uri) }.padding(vertical = 10.dp))
            }
        }
    }
}

fun share(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ${file.name}"))
}

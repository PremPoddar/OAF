package com.prempoddar.oaf.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.data.OafSettings
import com.prempoddar.oaf.data.SortBy
import com.prempoddar.oaf.files.FileOps
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.design.EmptyState
import com.prempoddar.oaf.ui.design.PillTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun sortEntries(list: List<Entry>, sort: SortBy, foldersFirst: Boolean): List<Entry> {
    val byField: Comparator<Entry> = when (sort) {
        SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.file.name }
        SortBy.DATE -> compareByDescending { it.modified }
        SortBy.SIZE -> compareByDescending<Entry> { it.size }.thenBy { it.file.name.lowercase() }
        SortBy.TYPE -> compareBy<Entry> { it.kind?.ordinal ?: -1 }.thenBy { it.file.name.lowercase() }
    }
    return list.sortedWith(if (foldersFirst) compareBy<Entry> { !it.isDir }.then(byField) else byField)
}

/**
 * One folder: list or grid, sorting, hidden files, search inside it (and below), selection with
 * copy/cut/share/delete/rename/compress, paste, and new files and folders.
 */
@Composable
fun FolderScreen(start: File, startSearch: Boolean = false, onOpen: (DocSource) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.prefs.settings.collectAsStateWithLifecycle(OafSettings())
    val changes by AppGraph.clipboard.changes.collectAsStateWithLifecycle()
    var path by rememberSaveable { mutableStateOf(start.path) }
    val dir = File(path)
    var query by rememberSaveable { mutableStateOf(if (startSearch) "" else null as String?) }
    val selected = remember { mutableStateListOf<File>() }
    var sheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var newItem by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var batch by remember { mutableStateOf<List<File>?>(null) }
    var details by remember { mutableStateOf<File?>(null) }
    var tick by remember { mutableStateOf(0) }
    val root = remember { Storage.roots(context).firstOrNull { start.path.startsWith(it.dir.path) } }

    fun go(f: File) { path = f.path; selected.clear(); query = null }
    fun up(): Boolean {
        val parent = dir.parentFile ?: return false
        if (root == null || !parent.path.startsWith(root.dir.path)) return false
        go(parent); return true
    }
    BackHandler {
        when {
            selected.isNotEmpty() -> selected.clear()
            query != null -> query = null
            dir.path != start.path && up() -> Unit
            else -> onBack()
        }
    }

    // Searching waits for a pause in typing, then looks through this folder and everything below.
    var debounced by remember { mutableStateOf(query) }
    LaunchedEffect(query) { if (query.isNullOrBlank()) debounced = query else { delay(250); debounced = query } }

    val entries by produceState<List<Entry>?>(null, path, debounced, settings.showHidden, settings.sort, settings.foldersFirst, changes, tick) {
        val q = debounced?.trim()?.lowercase()
        value = withContext(Dispatchers.IO) {
            val files = if (!q.isNullOrEmpty()) {
                dir.walkTopDown().onEnter { settings.showHidden || !it.name.startsWith(".") || it == dir }.maxDepth(12)
                    .filter { it != dir && it.name.lowercase().contains(q) }.take(1000).toList()
            } else dir.listFiles()?.toList().orEmpty()
            sortEntries(files.filter { settings.showHidden || !it.name.startsWith(".") }.map(::entryOf), settings.sort, settings.foldersFirst)
        }
    }
    val list = entries
    val selecting = selected.isNotEmpty()
    val isFav = path in settings.favourites

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // Top bar: selection mode or folder title.
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    IconButton(onClick = { selected.clear() }) { Icon(Icons.Rounded.Close, "Clear selection") }
                    Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { list?.forEach { if (it.file !in selected) selected.add(it.file) } }) { Icon(Icons.Rounded.SelectAll, "Select all") }
                } else {
                    IconButton(onClick = { if (dir.path == start.path || !up()) onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(if (root?.dir?.path == dir.path) root.label else dir.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        list?.let { Text(if (query.isNullOrBlank()) "${it.size} item${if (it.size == 1) "" else "s"}" else "${it.size} found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = { query = if (query == null) "" else null }) { Icon(if (query == null) Icons.Rounded.Search else Icons.Rounded.SearchOff, if (query == null) "Search" else "Stop searching") }
                    IconButton(onClick = { sheet = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort and view") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("New folder or file") }, onClick = { menu = false; newItem = true })
                            DropdownMenuItem(text = { Text("Select all") }, onClick = { menu = false; list?.forEach { selected.add(it.file) } })
                            DropdownMenuItem(
                                text = { Text(if (isFav) "Remove from favourites" else "Add to favourites") },
                                leadingIcon = { Icon(if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                                onClick = { menu = false; scope.launch { AppGraph.prefs.update { s -> s.copy(favourites = if (isFav) s.favourites - path else s.favourites + path) } } },
                            )
                            DropdownMenuItem(text = { Text(if (settings.showHidden) "Hide hidden files" else "Show hidden files") }, onClick = {
                                menu = false; scope.launch { AppGraph.prefs.update { it.copy(showHidden = !it.showHidden) } }
                            })
                            DropdownMenuItem(text = { Text("Folder details") }, onClick = { menu = false; details = dir })
                        }
                    }
                }
            }
            if (query != null && !selecting) {
                PillTextField(query!!, { query = it }, "Search in ${dir.name.ifEmpty { "storage" }}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), leading = Icons.Rounded.Search)
            }
            if (root != null) Breadcrumbs(dir, root) { go(it) }

            when {
                list == null -> Box(Modifier.weight(1f).fillMaxWidth())
                list.isEmpty() -> EmptyState(
                    if (query.isNullOrBlank()) Icons.Rounded.FolderOpen else Icons.Rounded.SearchOff,
                    if (query.isNullOrBlank()) "This folder is empty" else "Nothing matches “$query”",
                    if (query.isNullOrBlank()) "Paste something here or create a new folder." else "Search looks in this folder and every folder inside it.",
                    Modifier.weight(1f).fillMaxWidth(),
                )
                else -> LazyVerticalGrid(
                    columns = if (settings.grid) GridCells.Adaptive(112.dp) else GridCells.Adaptive(360.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = if (settings.grid) 12.dp else 0.dp, end = if (settings.grid) 12.dp else 0.dp, top = 4.dp, bottom = 120.dp),
                ) {
                    items(list, key = { it.file.path }) { e ->
                        val isSel = e.file in selected
                        val click: () -> Unit = {
                            when {
                                selecting -> { if (isSel) selected.remove(e.file) else selected.add(e.file) }
                                e.isDir -> go(e.file)
                                else -> onOpen(DocSource.fromFile(e.file))
                            }
                        }
                        val long: () -> Unit = { if (isSel) selected.remove(e.file) else selected.add(e.file) }
                        if (settings.grid) FileCell(e, isSel, click, long)
                        else FileRow(e, isSel, selecting, click, long, extra = if (!query.isNullOrBlank()) e.file.parentFile?.name else null) {
                            if (e.isDir) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (selecting) SelectionBar(
                selected.toList(), onDone = { selected.clear() },
                onRename = { renaming = it }, onBatchRename = { batch = it }, onDetails = { details = it },
            )
        }

        if (!selecting) Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
            val clip by AppGraph.clipboard.clip.collectAsStateWithLifecycle()
            val job by AppGraph.clipboard.job.collectAsStateWithLifecycle()
            if (clip == null && job == null) {
                ExtendedFloatingActionButton(
                    onClick = { newItem = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New") },
                    modifier = Modifier.navigationBarsPadding().padding(20.dp),
                )
            }
            ClipAndJobBar(pasteInto = if (query.isNullOrBlank()) dir else null)
        }
    }

    if (sheet) SortSheet(
        settings.sort, settings.foldersFirst, settings.showHidden, settings.grid,
        onSort = { s -> scope.launch { AppGraph.prefs.update { it.copy(sort = s) } } },
        onFoldersFirst = { v -> scope.launch { AppGraph.prefs.update { it.copy(foldersFirst = v) } } },
        onHidden = { v -> scope.launch { AppGraph.prefs.update { it.copy(showHidden = v) } } },
        onGrid = { v -> scope.launch { AppGraph.prefs.update { it.copy(grid = v) } } },
        onDismiss = { sheet = false },
    )
    if (newItem) NewItemDialog(dir, onDismiss = { newItem = false }) { name, folder ->
        newItem = false
        AppGraph.clipboard.run(if (folder) "Creating folder" else "Creating file", toast(context)) {
            if (folder) FileOps.newFolder(dir, name) else FileOps.newFile(dir, name)
        }
    }
    renaming?.let { f ->
        RenameDialog(f, onDismiss = { renaming = null }) { name ->
            renaming = null; selected.clear()
            AppGraph.clipboard.run("Renaming", toast(context)) { FileOps.rename(f, name) }
        }
    }
    batch?.let { files ->
        BatchRenameDialog(files, onDismiss = { batch = null }) { pattern, first ->
            batch = null; selected.clear()
            AppGraph.clipboard.run("Renaming", toast(context)) { FileOps.batchRename(files, pattern, first) }
        }
    }
    details?.let { DetailsSheet(it, onDismiss = { details = null }) }
}

@Composable
private fun Breadcrumbs(dir: File, root: Root, onGo: (File) -> Unit) {
    val parts = generateSequence(dir) { it.parentFile }.takeWhile { it.path.length >= root.dir.path.length }.toList().reversed()
    val scroll = rememberScrollState()
    LaunchedEffect(dir) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { i, f ->
            if (i > 0) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AssistChip(onClick = { onGo(f) }, label = { Text(if (f == root.dir) root.label else f.name, maxLines = 1) })
        }
    }
}

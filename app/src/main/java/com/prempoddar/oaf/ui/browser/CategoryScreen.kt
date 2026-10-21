package com.prempoddar.oaf.ui.browser

import com.prempoddar.oaf.data.sortFor
import com.prempoddar.oaf.data.sortedAt
import com.prempoddar.oaf.ui.design.tap
import android.content.Context
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.data.OafSettings
import com.prempoddar.oaf.files.FileOps
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.design.EmptyState
import com.prempoddar.oaf.ui.design.PillTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Category(val label: String, val grid: Boolean) {
    IMAGES("Images", true), VIDEOS("Videos", true), AUDIO("Audio", false),
    DOCUMENTS("Documents", false), ARCHIVES("Archives", false), APKS("APKs", false),
}

private val documentMimes = listOf(
    "application/pdf", "application/msword", "application/vnd.%", "application/rtf", "application/epub+zip",
    "text/plain", "text/csv", "text/markdown", "text/html", "application/json",
)
private val apkExts = listOf("apk", "apks", "xapk", "apkm")
private val archiveExts = listOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz")

/**
 * Every file of one kind on the phone, straight from Android's media index: names, sizes and
 * dates come with the query, so nothing on disk is touched until a thumbnail is drawn.
 */
fun queryCategory(context: Context, category: Category): List<Entry> {
    val files = MediaStore.Files.getContentUri("external")
    val col = MediaStore.Files.FileColumns.MIME_TYPE
    val data = MediaStore.Files.FileColumns.DATA
    val (selection, args) = when (category) {
        Category.IMAGES -> "$col LIKE ?" to arrayOf("image/%")
        Category.VIDEOS -> "$col LIKE ?" to arrayOf("video/%")
        Category.AUDIO -> "$col LIKE ?" to arrayOf("audio/%")
        Category.DOCUMENTS -> documentMimes.joinToString(" OR ") { "$col LIKE ?" } to documentMimes.toTypedArray()
        Category.ARCHIVES -> archiveExts.joinToString(" OR ") { "$data LIKE ?" } to archiveExts.map { "%.$it" }.toTypedArray()
        // Single apps and split-app bundles from any store or backup tool.
        Category.APKS -> apkExts.joinToString(" OR ") { "$data LIKE ?" } to apkExts.map { "%.$it" }.toTypedArray()
    }
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, data, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED)
    val thumbs = category == Category.IMAGES || category == Category.VIDEOS || category == Category.AUDIO
    val out = ArrayList<Entry>()
    runCatching {
        context.contentResolver.query(files, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(1) ?: continue
                val f = File(path)
                out += Entry(
                    f, kindOf(f), c.getLong(2), c.getLong(3) * 1000, 0,
                    if (thumbs) android.content.ContentUris.withAppendedId(files, c.getLong(0)) else null,
                )
            }
        }
    }
    return out
}

@Composable
fun CategoryScreen(category: Category, onOpen: (DocSource) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.prefs.settings.collectAsStateWithLifecycle(OafSettings())
    val changes by AppGraph.clipboard.changes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<File>() }
    var sheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var batch by remember { mutableStateOf<List<File>?>(null) }
    var details by remember { mutableStateOf<File?>(null) }
    var grid by rememberSaveable { mutableStateOf(category.grid) }
    BackHandler(selected.isNotEmpty() || query != null) { if (selected.isNotEmpty()) selected.clear() else query = null }

    val place = "category:${category.name}"
    val all by produceState<List<Entry>?>(null, changes, settings.showHidden) {
        value = withContext(Dispatchers.IO) {
            queryCategory(context, category).filter { settings.showHidden || !it.file.path.contains("/.") }
        }
    }
    val list = all?.let { l ->
        val q = query?.trim()?.lowercase()
        val filtered = if (q.isNullOrEmpty()) l else l.filter { it.file.name.lowercase().contains(q) }
        val (by, down) = settings.sortFor(place)
        sortEntries(filtered, by, false, down)
    }
    val selecting = selected.isNotEmpty()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    IconButton(onClick = tap { selected.clear() }) { Icon(Icons.Rounded.Close, "Clear selection") }
                    Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = tap { list?.forEach { if (it.file !in selected) selected.add(it.file) } }) { Icon(Icons.Rounded.SelectAll, "Select all") }
                } else {
                    IconButton(onClick = tap(onBack)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(category.label, style = MaterialTheme.typography.titleLarge)
                        list?.let { l -> Text("${l.size} files · ${humanSize(l.sumOf { it.size })}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = tap { query = if (query == null) "" else null }) { Icon(if (query == null) Icons.Rounded.Search else Icons.Rounded.SearchOff, "Search") }
                    IconButton(onClick = tap { sheet = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort and view") }
                }
            }
            if (query != null && !selecting) PillTextField(query!!, { query = it }, "Search ${category.label.lowercase()}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), leading = Icons.Rounded.Search)
            when {
                list == null -> Box(Modifier.weight(1f))
                list.isEmpty() -> EmptyState(categoryIcon(category), "No ${category.label.lowercase()} found", null, Modifier.weight(1f).fillMaxWidth())
                else -> LazyVerticalGrid(
                    columns = if (grid) GridCells.Adaptive(112.dp) else GridCells.Adaptive(360.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = if (grid) 12.dp else 0.dp, end = if (grid) 12.dp else 0.dp, bottom = 120.dp),
                ) {
                    items(list, key = { it.file.path }) { e ->
                        val isSel = e.file in selected
                        val click = { if (selecting) { if (isSel) selected.remove(e.file) else selected.add(e.file) } else onOpen(DocSource.fromFile(e.file)); Unit }
                        val long = { if (isSel) selected.remove(e.file) else selected.add(e.file); Unit }
                        if (grid) FileCell(e, isSel, click, long) else FileRow(e, isSel, selecting, click, long, extra = e.file.parentFile?.name)
                    }
                }
            }
            if (selecting) SelectionBar(selected.toList(), onDone = { selected.clear() }, onRename = { renaming = it }, onBatchRename = { batch = it }, onDetails = { details = it })
        }
        if (!selecting) Box(Modifier.align(Alignment.BottomCenter)) { ClipAndJobBar(pasteInto = null) }
    }

    if (sheet) SortSheet(
        settings.sortFor(place).first, settings.sortFor(place).second, settings.foldersFirst, settings.showHidden, grid,
        onSort = { s, d -> scope.launch { AppGraph.prefs.update { it.sortedAt(place, s, d) } } },
        onFoldersFirst = { v -> scope.launch { AppGraph.prefs.update { it.copy(foldersFirst = v) } } },
        onHidden = { v -> scope.launch { AppGraph.prefs.update { it.copy(showHidden = v) } } },
        onGrid = { grid = it },
        onDismiss = { sheet = false },
    )
    renaming?.let { f -> RenameDialog(f, onDismiss = { renaming = null }) { name -> renaming = null; selected.clear(); AppGraph.clipboard.run("Renaming", toast(context)) { FileOps.rename(f, name) } } }
    batch?.let { files -> BatchRenameDialog(files, onDismiss = { batch = null }) { p, n -> batch = null; selected.clear(); AppGraph.clipboard.run("Renaming", toast(context)) { FileOps.batchRename(files, p, n) } } }
    details?.let { DetailsSheet(it, onDismiss = { details = null }) }
}

fun categoryIcon(c: Category): ImageVector = when (c) {
    Category.IMAGES -> Icons.Rounded.Image
    Category.VIDEOS -> Icons.Rounded.Movie
    Category.AUDIO -> Icons.Rounded.MusicNote
    Category.DOCUMENTS -> Icons.Rounded.Description
    Category.ARCHIVES -> Icons.Rounded.FolderZip
    Category.APKS -> Icons.Rounded.Android
}

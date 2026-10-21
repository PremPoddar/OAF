package com.prempoddar.oaf.ui.browser

import com.prempoddar.oaf.ui.design.MiddleText
import com.prempoddar.oaf.ui.design.tap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.files.Bin
import com.prempoddar.oaf.files.CleanUp
import java.io.File
import com.prempoddar.oaf.ui.LocalPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BinScreen(onBack: () -> Unit) {
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val items by produceState<List<Bin.Item>>(emptyList(), tick) { value = withContext(Dispatchers.IO) { AppGraph.bin.items().sortedByDescending { it.deletedAt } } }
    fun io(block: () -> Unit) = scope.launch { withContext(Dispatchers.IO) { block() }; tick++ }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize().background(paper.page).padding(top = top)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = tap(onBack)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Bin", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) TextButton(onClick = tap { confirmEmpty = true }) { Text("Empty bin") }
        }
        Text("Deleted files stay here for 30 days.", style = MaterialTheme.typography.bodySmall, color = paper.muted, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn {
            if (items.isEmpty()) item { Text("The bin is empty.", color = paper.muted, modifier = Modifier.padding(24.dp)) }
            items(items, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MiddleText(item.name, MaterialTheme.typography.titleMedium)
                        Text("${humanSize(item.size)} · from ${item.originalPath.substringBeforeLast('/').substringAfterLast('/')}", style = MaterialTheme.typography.bodySmall, color = paper.muted)
                    }
                    TextButton(onClick = tap { io { AppGraph.bin.restore(item) } }) { Text("Restore") }
                    TextButton(onClick = tap { io { AppGraph.bin.purge(item) } }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty the bin?") },
            text = { Text("${items.size} item${if (items.size == 1) "" else "s"} will be deleted for good.") },
            confirmButton = { TextButton(onClick = tap { confirmEmpty = false; io { AppGraph.bin.empty() } }) { Text("Empty", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = tap { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val usage by produceState<Storage.Usage?>(null) { value = withContext(Dispatchers.IO) { Storage.usage(context) } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize().background(paper.page).verticalScroll(rememberScrollState()).padding(top = top)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = tap(onBack)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Storage", style = MaterialTheme.typography.headlineSmall)
        }
        val u = usage ?: run { LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp)); return@Column }
        val used = u.total - u.free
        Column(Modifier.padding(20.dp)) {
            Text("${humanSize(used)} used of ${humanSize(u.total)}", style = MaterialTheme.typography.headlineSmall)
            Text("${humanSize(u.free)} free", style = MaterialTheme.typography.bodyMedium, color = paper.muted)
            val other = (used - u.images - u.video - u.audio - u.documents).coerceAtLeast(0)
            val parts = listOf(
                Triple("Photos & images", u.images, Color(0xFF7446A6)), Triple("Videos", u.video, Color(0xFFA63A66)),
                Triple("Music & audio", u.audio, Color(0xFF2E7D4F)), Triple("Documents", u.documents, Color(0xFF2F5D8C)),
                Triple("Apps & other", other, Color(0xFF8A847A)),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp).clip(MaterialTheme.shapes.small)) {
                parts.forEach { (_, size, color) ->
                    val w = size.toFloat() / u.total
                    if (w > 0.002f) Column(Modifier.weight(w).background(color).padding(vertical = 8.dp)) {}
                }
                Column(Modifier.weight((u.free.toFloat() / u.total).coerceAtLeast(0.001f)).background(paper.rule).padding(vertical = 8.dp)) {}
            }
            parts.forEach { (label, size, color) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.padding(end = 12.dp).clip(MaterialTheme.shapes.small).background(color).padding(6.dp)) {}
                    Text(label, modifier = Modifier.weight(1f))
                    Text(humanSize(size), color = paper.muted)
                }
            }
        }
        CleanUpSection()
    }
}

/** Suggestions for freeing space: found on request, each one sent to the bin with a tap. */
@Composable
private fun CleanUpSection() {
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<CleanUp.Report?>(null) }
    var gone by remember { mutableStateOf(setOf<String>()) }
    fun toBin(f: File) = scope.launch {
        withContext(Dispatchers.IO) { runCatching { AppGraph.bin.delete(f) } }
        gone = gone + f.path
    }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Free up space", style = MaterialTheme.typography.titleLarge)
        if (!Storage.hasAllFilesAccess()) {
            Text("OAF needs access to all files to look for things to clear.", color = paper.muted, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val r = report
        if (r == null) {
            Text("Looks for big files, copies of the same file, old app installers and empty folders.", color = paper.muted, style = MaterialTheme.typography.bodyMedium)
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
            else Button(onClick = tap {
                scanning = true
                scope.launch {
                    report = withContext(Dispatchers.IO) { CleanUp.scan(android.os.Environment.getExternalStorageDirectory()) }
                    scanning = false
                }
            }, Modifier.padding(top = 12.dp)) { Text("Look for things to clear") }
            return@Column
        }
        if (r.isEmpty) { Text("Nothing to suggest. Your storage is tidy.", color = paper.muted); return@Column }

        @Composable fun Heading(title: String, note: String) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = paper.muted)
        }
        @Composable fun FileLine(f: File, detail: String) {
            if (f.path in gone) return
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MiddleText(f.name, MaterialTheme.typography.bodyLarge)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = paper.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = tap { toBin(f) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
        fun where(f: File) = f.parentFile?.name ?: ""

        if (r.large.isNotEmpty()) {
            Heading("Big files", "Files over ${humanSize(CleanUp.LARGE_BYTES)}, biggest first.")
            r.large.forEach { FileLine(it, "${humanSize(it.length())} · in ${where(it)}") }
        }
        val dupes = r.duplicates.map { d -> d.copy(files = d.files.filter { it.path !in gone }) }.filter { it.files.size > 1 }
        if (dupes.isNotEmpty()) {
            Heading("Copies of the same file", "${humanSize(dupes.sumOf { it.spare })} could be freed. The oldest copy of each is kept.")
            dupes.take(30).forEach { d ->
                Text("${d.files.size} copies of ${d.files[0].name} · ${humanSize(d.size)} each", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Text("Keeping the one in ${where(d.files[0])}", style = MaterialTheme.typography.bodySmall, color = paper.muted)
                d.files.drop(1).forEach { FileLine(it, "Copy in ${it.parent}") }
            }
        }
        if (r.installers.isNotEmpty()) {
            Heading("Old app installers", "Installer files more than 30 days old. Apps already installed don't need them.")
            r.installers.forEach { FileLine(it, "${humanSize(it.length())} · in ${where(it)}") }
        }
        val empties = r.emptyFolders.filter { it.path !in gone }
        if (empties.isNotEmpty()) {
            Heading("Empty folders", "${empties.size} folder${if (empties.size == 1) "" else "s"} with nothing in them.")
            empties.take(10).forEach { Text(it.path.removePrefix(android.os.Environment.getExternalStorageDirectory().path), style = MaterialTheme.typography.bodySmall, color = paper.muted) }
            if (empties.size > 10) Text("and ${empties.size - 10} more", style = MaterialTheme.typography.bodySmall, color = paper.muted)
            TextButton(onClick = tap {
                scope.launch {
                    // Nothing is inside them, so there's nothing for the bin to keep.
                    val removed = withContext(Dispatchers.IO) { empties.filter { it.delete() }.map { it.path } }
                    gone = gone + removed
                }
            }) { Text("Remove empty folders") }
        }
        TextButton(onClick = tap { report = null; gone = emptySet() }, Modifier.padding(top = 8.dp)) { Text("Look again") }
    }
}

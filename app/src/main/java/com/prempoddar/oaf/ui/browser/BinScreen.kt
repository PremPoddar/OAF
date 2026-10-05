package com.prempoddar.oaf.ui.browser

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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Bin", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Empty bin") }
        }
        Text("Deleted files stay here for 30 days.", style = MaterialTheme.typography.bodySmall, color = paper.muted, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn {
            if (items.isEmpty()) item { Text("The bin is empty.", color = paper.muted, modifier = Modifier.padding(24.dp)) }
            items(items, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${humanSize(item.size)} · from ${item.originalPath.substringBeforeLast('/').substringAfterLast('/')}", style = MaterialTheme.typography.bodySmall, color = paper.muted)
                    }
                    TextButton(onClick = { io { AppGraph.bin.restore(item) } }) { Text("Restore") }
                    TextButton(onClick = { io { AppGraph.bin.purge(item) } }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty the bin?") },
            text = { Text("${items.size} item${if (items.size == 1) "" else "s"} will be deleted for good.") },
            confirmButton = { TextButton(onClick = { confirmEmpty = false; io { AppGraph.bin.empty() } }) { Text("Empty", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
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
    }
}

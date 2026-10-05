package com.prempoddar.oaf.ui.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.CodeStyle
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.browser.humanSize
import com.prempoddar.oaf.ui.rememberChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile

/** For anything OAF can't read: what it is, its bytes, and a way to open it elsewhere. */
@Composable
fun HexViewer(source: DocSource, onBack: () -> Unit, note: String? = null) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val file by produceState<java.io.File?>(null, source) { value = withContext(Dispatchers.IO) { runCatching { source.local(context) }.getOrNull() } }
    val rows = ((file?.length() ?: 0) + 15) / 16
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    ViewerScaffold(
        title = source.name, subtitle = "${source.kind.label} · ${humanSize(file?.length() ?: source.size.coerceAtLeast(0))}",
        chrome = rememberChrome(), autoHide = false, onBack = onBack,
        actions = { IconButton(onClick = { openElsewhere(context, source) }) { Icon(Icons.Rounded.OpenInNew, "Open with") } },
    ) {
        val f = file
        val raf = androidx.compose.runtime.remember(f) { f?.let { RandomAccessFile(it, "r") } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 72.dp, bottom = 32.dp, start = 12.dp, end = 12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    Text(note ?: "OAF doesn't have a viewer for this kind of file.", style = MaterialTheme.typography.titleMedium)
                    Text("Below are its raw bytes. Another app might open it properly.", style = MaterialTheme.typography.bodySmall, color = paper.muted, modifier = Modifier.padding(vertical = 6.dp))
                    Button(onClick = { openElsewhere(context, source) }) { Text("Open with another app") }
                }
            }
            items(rows.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) { i ->
                val bytes = ByteArray(16)
                val n = raf?.let { synchronized(it) { it.seek(i * 16L); it.read(bytes) } } ?: 0
                val hex = (0 until 16).joinToString(" ") { if (it < n) "%02x".format(bytes[it]) else "  " }
                val ascii = (0 until n).joinToString("") { val c = bytes[it].toInt() and 0xFF; if (c in 32..126) c.toChar().toString() else "·" }
                Text("%08x  %s  %s".format(i * 16L, hex, ascii), style = CodeStyle.copy(fontSize = CodeStyle.fontSize * 0.85f), color = paper.ink, softWrap = false)
            }
        }
    }
}

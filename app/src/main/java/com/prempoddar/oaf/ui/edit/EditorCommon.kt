package com.prempoddar.oaf.ui.edit

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.edit.EditGeometry
import com.prempoddar.oaf.files.FileOps
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.browser.openWith
import com.prempoddar.oaf.ui.browser.share
import java.io.File

/** Where an edited copy goes: beside the original, or in Downloads/OAF Edits when that isn't a plain file. */
fun outputFor(source: DocSource, ext: String): File {
    val dir = source.file?.parentFile?.takeIf { it.canWrite() }
        ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OAF Edits").apply { mkdirs() }
    return FileOps.freeName(dir, EditGeometry.editedName(source.name, ext))
}

@Composable
fun EditorBar(title: String, onClose: () -> Unit, action: String, enabled: Boolean, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close editor") }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Button(onClick = onAction, enabled = enabled, modifier = Modifier.padding(end = 8.dp)) { Text(action) }
    }
}

/** Progress while saving, then where the copy went with ways to use it. */
@Composable
fun ExportDialog(progress: Float?, saved: File?, error: String?, onCancel: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (saved != null || error != null) onDone() },
        icon = if (saved != null) ({ Icon(Icons.Rounded.CheckCircle, null) }) else null,
        title = { Text(when { error != null -> "Couldn't save"; saved != null -> "Saved"; else -> "Saving…" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    error != null -> Text(error)
                    saved != null -> Text("${saved.name}\nin ${saved.parentFile?.name}")
                    progress != null -> {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            when {
                saved != null -> Row {
                    TextButton(onClick = { share(context, saved) }) { Text("Share") }
                    TextButton(onClick = { openWith(context, saved) }) { Text("Open") }
                    TextButton(onClick = onDone) { Text("Done") }
                }
                error != null -> TextButton(onClick = onDone) { Text("OK") }
                else -> TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

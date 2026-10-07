package com.prempoddar.oaf.save

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Save as…" in Android's open-with and share sheets: saves the file another app is offering
 * to a place the user picks. One file gets a save dialog with its name; several go into a folder.
 */
class SaveAsActivity : ComponentActivity() {
    private var incoming: List<Uri> = emptyList()

    private val saveOne = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { target ->
        if (target == null) finish() else copy(listOf(incoming.first() to target))
    }

    private val saveMany = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val dir = tree?.let { DocumentFile.fromTreeUri(this, it) }
        if (dir == null) { finish(); return@registerForActivityResult }
        val pairs = incoming.mapNotNull { src ->
            val name = nameOf(src)
            dir.createFile(contentResolver.getType(src) ?: "application/octet-stream", name)?.uri?.let { src to it }
        }
        copy(pairs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        incoming = urisOf(intent)
        when {
            incoming.isEmpty() -> { Toast.makeText(this, "There's nothing to save", Toast.LENGTH_SHORT).show(); finish() }
            incoming.size == 1 -> saveOne.launch(nameOf(incoming.first()))
            else -> saveMany.launch(null)
        }
    }

    private fun urisOf(i: Intent): List<Uri> = when (i.action) {
        Intent.ACTION_SEND -> listOfNotNull(@Suppress("DEPRECATION") (i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri))
        Intent.ACTION_SEND_MULTIPLE -> @Suppress("DEPRECATION") i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> listOfNotNull(i.data)
    }

    private fun nameOf(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    private fun copy(pairs: List<Pair<Uri, Uri>>) {
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                pairs.count { (src, dst) ->
                    runCatching {
                        contentResolver.openInputStream(src)!!.use { i -> contentResolver.openOutputStream(dst, "w")!!.use { i.copyTo(it) } }
                    }.isSuccess
                }
            }
            val msg = when {
                saved == pairs.size && saved == 1 -> "Saved"
                saved == pairs.size -> "Saved $saved files"
                saved == 0 -> "Couldn't save"
                else -> "Saved $saved of ${pairs.size} files"
            }
            Toast.makeText(this@SaveAsActivity, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

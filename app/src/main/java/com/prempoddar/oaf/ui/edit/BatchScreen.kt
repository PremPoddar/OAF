package com.prempoddar.oaf.ui.edit

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.edit.ImageEffects
import com.prempoddar.oaf.edit.ImageOps
import com.prempoddar.oaf.ui.design.BigButton
import com.prempoddar.oaf.ui.design.ChoiceChips
import com.prempoddar.oaf.ui.design.EmptyState
import com.prempoddar.oaf.ui.design.ExpressiveScaffold
import com.prempoddar.oaf.ui.design.Haptics
import com.prempoddar.oaf.ui.design.SectionLabel
import com.prempoddar.oaf.ui.design.TonalBigButton
import com.prempoddar.oaf.ui.design.sliding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Many pictures at once: made smaller, converted, or kept under a file size, each saved as a copy;
 * or joined into one long picture, side by side or one under another. Copies go to Pictures/OAF.
 */
@Composable
fun BatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var mode by remember { mutableStateOf("each") }
    var maxSide by remember { mutableIntStateOf(0) }
    var format by remember { mutableStateOf(ImageOps.Format.JPEG) }
    var quality by remember { mutableFloatStateOf(85f) }
    var limitKb by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(200)) { if (it.isNotEmpty()) { picked = it; result = null } }

    fun load(uri: Uri): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        val limit = if (maxSide > 0) maxSide else 4096
        if (longest > limit) d.setTargetSize(info.size.width * limit / longest, info.size.height * limit / longest)
    }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }

    fun save(b: Bitmap, name: String) {
        val bytes = if (limitKb > 0 && format.lossy) ImageEffects.encodeWithin(b, format, limitKb * 1024L).first
        else java.io.ByteArrayOutputStream().also { ImageOps.encode(b, format, quality.toInt(), it) }.toByteArray()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.${format.ext}")
            put(MediaStore.Images.Media.MIME_TYPE, when (format) { ImageOps.Format.JPEG -> "image/jpeg"; ImageOps.Format.PNG -> "image/png"; ImageOps.Format.WEBP -> "image/webp" })
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OAF")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't make the file")
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
    }

    fun nameOf(uri: Uri) = com.prempoddar.oaf.io.DocSource.fromUri(context, uri).name.substringBeforeLast('.')

    fun run() = scope.launch {
        val list = picked
        progress = 0 to list.size
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                if (mode == "each") {
                    var done = 0; var failed = 0
                    list.forEach { uri ->
                        runCatching { save(load(uri), nameOf(uri) + " (edited)") }.onFailure { failed++ }
                        done++; withContext(Dispatchers.Main) { progress = done to list.size }
                    }
                    "Saved ${done - failed} of ${list.size} to Pictures/OAF" + if (failed > 0) ". $failed couldn't be read." else ""
                } else {
                    val pics = list.mapIndexed { i, uri -> load(uri).also { withContext(Dispatchers.Main) { progress = i + 1 to list.size } } }
                    save(ImageEffects.stitch(pics, across = mode == "across", gap = 0), "Joined ${list.size} pictures")
                    "Joined ${list.size} pictures into one, in Pictures/OAF"
                }
            }
        }
        progress = null
        outcome.onSuccess { Haptics.confirm(); result = it }.onFailure { Haptics.reject(); result = it.message ?: "Something went wrong" }
    }

    ExpressiveScaffold("Batch pictures", onBack = onBack) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item {
                if (picked.isEmpty()) EmptyState(Icons.Rounded.Collections, "Choose pictures", "Make many smaller or convert them at once, or join them into one.")
                else Text("${picked.size} picture${if (picked.size == 1) "" else "s"} chosen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                TonalBigButton(if (picked.isEmpty()) "Choose pictures" else "Choose again", onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                SectionLabel("What to do")
                ChoiceChips(listOf("each" to "A copy of each", "down" to "Join, one under another", "across" to "Join, side by side"), mode, { mode = it })
                SectionLabel("Size")
                ChoiceChips(listOf(0 to "As they are", 4096 to "4096 px", 2048 to "2048 px", 1600 to "1600 px", 1080 to "1080 px", 720 to "720 px"), maxSide, { maxSide = it })
                Text("The longest side, at most. Pictures already smaller stay as they are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                SectionLabel("Save as")
                ChoiceChips(ImageOps.Format.entries.map { it to it.label }, format, { format = it })
                if (format.lossy) {
                    SectionLabel("File size")
                    ChoiceChips(listOf(0 to "Any", 100 to "Under 100 KB", 300 to "Under 300 KB", 500 to "Under 500 KB", 1024 to "Under 1 MB"), limitKb, { limitKb = it })
                    if (limitKb == 0) Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Quality ${quality.toInt()}", style = MaterialTheme.typography.labelLarge)
                        Slider(quality, sliding(40f..100f) { quality = it }, valueRange = 40f..100f)
                    }
                }
                Text("Copies are saved without the camera's location details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    progress?.let { (done, total) ->
                        Text("Working on $done of $total…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    }
                    result?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                    BigButton(if (mode == "each") "Save copies" else "Join and save", onClick = { run() }, enabled = picked.isNotEmpty() && progress == null && (mode == "each" || picked.size > 1))
                }
            }
        }
    }
}

package com.prempoddar.oaf.ui.edit

import androidx.compose.foundation.lazy.items
import com.prempoddar.oaf.ui.design.CardSection
import com.prempoddar.oaf.ui.design.CardGroup
import com.prempoddar.oaf.ui.design.SegmentedBar
import com.prempoddar.oaf.ui.design.OptionGrid
import com.prempoddar.oaf.ui.design.SheetLabel
import com.prempoddar.oaf.ui.design.IconTile
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
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

    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val total by androidx.compose.runtime.produceState(0L, picked) {
        value = withContext(Dispatchers.IO) { picked.sumOf { u -> runCatching { com.prempoddar.oaf.io.DocSource.fromUri(context, u).size.coerceAtLeast(0) }.getOrDefault(0L) } }
    }
    ExpressiveScaffold(
        "Batch pictures", onBack = onBack,
        bottomBar = {
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    BigButton(
                        when { picked.isEmpty() -> "Choose pictures first"; mode == "each" -> "Save ${picked.size} cop${if (picked.size == 1) "y" else "ies"}"; else -> "Join ${picked.size} pictures" },
                        onClick = { run() }, icon = if (mode == "each") Icons.Rounded.Collections else Icons.Rounded.ViewAgenda,
                        enabled = picked.isNotEmpty() && progress == null && (mode == "each" || picked.size > 1),
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (picked.isEmpty()) {
                    androidx.compose.material3.Surface(onClick = { Haptics.tap(); choose() }, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconTile(Icons.Rounded.AddPhotoAlternate, size = 72.dp, container = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.onPrimary)
                            Text("Choose pictures", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp))
                            Text("Make many smaller or convert them at once, or join them into one.", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                } else {
                    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 14.dp)) {
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${picked.size} picture${if (picked.size == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge)
                                    if (total > 0) Text(com.prempoddar.oaf.ui.browser.humanSize(total) + " together", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                androidx.compose.material3.FilledTonalButton(onClick = { Haptics.tap(); choose() }) { Text("Change") }
                            }
                            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                                items(picked.size) { i -> Thumb(picked[i]) }
                            }
                        }
                    }
                }
            }
            item {
                SheetLabel("What to do")
                Spacer(Modifier.height(8.dp))
                OptionGrid(listOf("each", "down", "across"), mode, columns = 3, onPick = { mode = it }) { m ->
                    when (m) {
                        "each" -> Triple(Icons.Rounded.Collections, "Copies", "One of each")
                        "down" -> Triple(Icons.Rounded.ViewAgenda, "Stack", "One tall picture")
                        else -> Triple(Icons.Rounded.ViewColumn, "Across", "One wide picture")
                    }
                }
            }
            item {
                SheetLabel("Size")
                Spacer(Modifier.height(8.dp))
                SegmentedBar(listOf(0 to "Same", 4096 to "4096", 2048 to "2048", 1080 to "1080", 720 to "720"), maxSide) { maxSide = it }
                Text(if (maxSide == 0) "Pictures keep their size." else "The longest side at most $maxSide pixels; smaller pictures stay as they are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
            item {
                SheetLabel("Save as")
                Spacer(Modifier.height(8.dp))
                OptionGrid(ImageOps.Format.entries.toList(), format, columns = 3, onPick = { format = it }) { f ->
                    when (f) {
                        ImageOps.Format.JPEG -> Triple(Icons.Rounded.Photo, "JPEG", "Small photos")
                        ImageOps.Format.PNG -> Triple(Icons.Rounded.Gradient, "PNG", "Lossless")
                        ImageOps.Format.WEBP -> Triple(Icons.Rounded.Bolt, "WebP", "Small, modern")
                    }
                }
            }
            if (format.lossy) item {
                SheetLabel("File size")
                Spacer(Modifier.height(8.dp))
                SegmentedBar(listOf(0 to "Any", 100 to "100 KB", 300 to "300 KB", 500 to "500 KB", 1024 to "1 MB"), limitKb) { limitKb = it }
                if (limitKb == 0) { Spacer(Modifier.height(10.dp)); CardGroup {
                    CardSection {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quality", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${quality.toInt()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(quality, sliding(40f..100f) { quality = it }, valueRange = 40f..100f)
                    }
                } } else Text("Each copy is made as good as it can be while staying under ${if (limitKb >= 1024) "${limitKb / 1024} MB" else "$limitKb KB"}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
            item {
                androidx.compose.material3.Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Copies go to Pictures/OAF, without the camera's location details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (progress != null || result != null) item {
                androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        progress?.let { (done, total) ->
                            Text("Working on $done of $total…", style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        }
                        result?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small square preview of a chosen picture. */
@Composable
private fun Thumb(uri: Uri) {
    val context = LocalContext.current
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(240, 240), null).asImageBitmap() }.getOrNull() }
    }
    androidx.compose.foundation.layout.Box(Modifier.size(76.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bmp?.let { androidx.compose.foundation.Image(it, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

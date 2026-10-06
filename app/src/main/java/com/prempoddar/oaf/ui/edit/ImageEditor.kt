package com.prempoddar.oaf.ui.edit

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.edit.EditGeometry
import com.prempoddar.oaf.edit.ImageOps
import com.prempoddar.oaf.edit.Rect
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.design.ChoiceChips
import com.prempoddar.oaf.ui.design.PillTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private enum class Tool(val label: String) { CROP("Crop"), TURN("Turn"), RESIZE("Resize"), HIDE("Hide"), ADJUST("Adjust") }

/** Crop, turn, flip, resize, blur or black out parts, adjust colours, and save as JPEG, PNG or WebP. */
@Composable
fun ImageEditor(source: DocSource, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by produceState<Bitmap?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(source.local(context))) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = maxOf(info.size.width, info.size.height) / 6000f
                    if (scale > 1) d.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                }.copy(Bitmap.Config.ARGB_8888, false)
            }.getOrNull()
        }
    }
    var edits by remember { mutableStateOf<Bitmap?>(null) }
    val undo = remember { mutableStateListOf<Bitmap>() }
    val bitmap = edits ?: loaded
    var tool by remember { mutableStateOf(Tool.CROP) }
    var busy by remember { mutableStateOf(false) }

    // Tool state.
    var aspect by remember { mutableFloatStateOf(0f) }
    var crop by remember(bitmap) { mutableStateOf(bitmap?.let { Rect(0f, 0f, it.width.toFloat(), it.height.toFloat()) }) }
    var hideHow by remember { mutableStateOf(ImageOps.Hide.BLUR) }
    var drawing by remember { mutableStateOf<Rect?>(null) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var saving by remember { mutableStateOf(false) }

    fun apply(op: (Bitmap) -> Bitmap) {
        val b = bitmap ?: return
        busy = true
        scope.launch {
            val next = withContext(Dispatchers.Default) { runCatching { op(b) }.getOrNull() }
            if (next != null) {
                undo.add(b); if (undo.size > 12) undo.removeAt(0)
                edits = next
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditorBar("Edit picture", onClose, "Save", enabled = bitmap != null && !busy, onAction = { saving = true })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(bitmap?.let { "${it.width} × ${it.height}" } ?: "", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(enabled = undo.isNotEmpty() && !busy, onClick = { edits = undo.removeAt(undo.lastIndex) }) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            val b = bitmap
            if (b != null) {
                val density = LocalDensity.current
                val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
                val shown = EditGeometry.fit(b.width, b.height, boxW, boxH)
                val image = remember(b) { b.asImageBitmap() }
                val filter = if (tool == Tool.ADJUST) ColorFilter.colorMatrix(ColorMatrix(ImageOps.adjustMatrix(brightness, contrast, saturation).array)) else null
                Image(image, null, contentScale = ContentScale.Fit, colorFilter = filter, modifier = Modifier.fillMaxSize())
                fun toScreen(r: Rect) = Rect(
                    shown.left + r.left / b.width * shown.width, shown.top + r.top / b.height * shown.height,
                    shown.left + r.right / b.width * shown.width, shown.top + r.bottom / b.height * shown.height,
                )
                val handle = with(density) { 28.dp.toPx() }
                if (tool == Tool.CROP) crop?.let { c ->
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(b, aspect) {
                            var corner = -1 // 0 tl, 1 tr, 2 bl, 3 br, 4 move
                            detectDragGestures(
                                onDragStart = { o ->
                                    val s = toScreen(crop!!)
                                    val corners = listOf(Offset(s.left, s.top), Offset(s.right, s.top), Offset(s.left, s.bottom), Offset(s.right, s.bottom))
                                    corner = corners.indexOfFirst { (it - o).getDistance() < handle * 1.5f }
                                    if (corner < 0 && o.x in s.left..s.right && o.y in s.top..s.bottom) corner = 4
                                },
                            ) { change, d ->
                                change.consume()
                                val cur = crop ?: return@detectDragGestures
                                val dx = d.x / shown.width * b.width; val dy = d.y / shown.height * b.height
                                val min = maxOf(b.width, b.height) / 20f
                                var (l, t, r, bt) = listOf(cur.left, cur.top, cur.right, cur.bottom)
                                when (corner) {
                                    0 -> { l += dx; t += dy }
                                    1 -> { r += dx; t += dy }
                                    2 -> { l += dx; bt += dy }
                                    3 -> { r += dx; bt += dy }
                                    4 -> {
                                        val mx = dx.coerceIn(-l, b.width - r); val my = dy.coerceIn(-t, b.height - bt)
                                        l += mx; r += mx; t += my; bt += my
                                    }
                                    else -> return@detectDragGestures
                                }
                                l = l.coerceIn(0f, r - min); t = t.coerceIn(0f, bt - min)
                                r = r.coerceIn(l + min, b.width.toFloat()); bt = bt.coerceIn(t + min, b.height.toFloat())
                                if (aspect > 0 && corner in 0..3) {
                                    // Keep the chosen shape by following the width.
                                    val h = (r - l) / aspect
                                    if (corner < 2) t = (bt - h).coerceAtLeast(0f) else bt = (t + h).coerceAtMost(b.height.toFloat())
                                    if (abs((r - l) / (bt - t) - aspect) > 0.01f) { val w = (bt - t) * aspect; if (corner % 2 == 0) l = r - w else r = l + w }
                                }
                                crop = Rect(l, t, r, bt)
                            }
                        },
                    ) {
                        val s = toScreen(c)
                        val shade = Color.Black.copy(alpha = 0.55f)
                        drawRect(shade, Offset(0f, 0f), Size(size.width, s.top))
                        drawRect(shade, Offset(0f, s.bottom), Size(size.width, size.height - s.bottom))
                        drawRect(shade, Offset(0f, s.top), Size(s.left, s.height))
                        drawRect(shade, Offset(s.right, s.top), Size(size.width - s.right, s.height))
                        drawRect(Color.White, Offset(s.left, s.top), Size(s.width, s.height), style = Stroke(2.dp.toPx()))
                        for (i in 1..2) {
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(s.left + s.width * i / 3, s.top), Offset(s.left + s.width * i / 3, s.bottom))
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(s.left, s.top + s.height * i / 3), Offset(s.right, s.top + s.height * i / 3))
                        }
                        listOf(Offset(s.left, s.top), Offset(s.right, s.top), Offset(s.left, s.bottom), Offset(s.right, s.bottom)).forEach { drawCircle(Color.White, 9.dp.toPx(), it) }
                    }
                }
                if (tool == Tool.HIDE) Canvas(
                    Modifier.fillMaxSize().pointerInput(b, hideHow) {
                        var start = Offset.Zero
                        detectDragGestures(
                            onDragStart = { start = it; drawing = null },
                            onDragEnd = {
                                drawing?.let { r -> apply { bmp -> ImageOps.hide(bmp, r, hideHow) } }
                                drawing = null
                            },
                        ) { change, _ ->
                            change.consume()
                            val (x1, y1) = EditGeometry.toImage(shown, b.width, b.height, start.x, start.y)
                            val (x2, y2) = EditGeometry.toImage(shown, b.width, b.height, change.position.x, change.position.y)
                            drawing = Rect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
                        }
                    },
                ) {
                    drawing?.let { r ->
                        val s = toScreen(r)
                        drawRect(Color.White.copy(alpha = 0.25f), Offset(s.left, s.top), Size(s.width, s.height))
                        drawRect(Color.White, Offset(s.left, s.top), Size(s.width, s.height), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }

        // Tool options.
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).verticalScroll(rememberScrollState())) {
            val b = bitmap
            when (tool) {
                Tool.CROP -> {
                    ChoiceChips(
                        listOf(0f to "Free", 1f to "Square", 4f / 3 to "4:3", 3f / 2 to "3:2", 16f / 9 to "16:9", 9f / 16 to "9:16", 4f / 5 to "4:5"),
                        aspect, { a -> aspect = a; if (b != null) crop = if (a > 0) EditGeometry.aspectCrop(b.width, b.height, a) else Rect(0f, 0f, b.width.toFloat(), b.height.toFloat()) },
                        Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                    )
                    Button(
                        enabled = b != null && crop != null && crop != Rect(0f, 0f, b.width.toFloat(), b.height.toFloat()),
                        onClick = { crop?.let { r -> apply { ImageOps.crop(it, r) } } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text("Crop") }
                }
                Tool.TURN -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { apply { ImageOps.rotate(it, -90f) } }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Rounded.RotateLeft, "Turn left") }
                    FilledTonalButton(onClick = { apply { ImageOps.rotate(it, 90f) } }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Rounded.RotateRight, "Turn right") }
                    FilledTonalButton(onClick = { apply { ImageOps.flip(it, true) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Flip, "Flip sideways") }
                    FilledTonalButton(onClick = { apply { ImageOps.flip(it, false) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Flip, "Flip upside down", Modifier.rotate(90f)) }
                }
                Tool.RESIZE -> if (b != null) {
                    var w by remember(b) { mutableStateOf(b.width.toString()) }
                    var h by remember(b) { mutableStateOf(b.height.toString()) }
                    ChoiceChips(listOf(75 to "75%", 50 to "50%", 25 to "25%"), -1, { pct ->
                        w = (b.width * pct / 100).toString(); h = (b.height * pct / 100).toString()
                    }, Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillTextField(w, { v -> w = v.filter(Char::isDigit).take(5); w.toIntOrNull()?.let { h = EditGeometry.scaledTo(b.width, b.height, width = it).second.toString() } }, "Width", Modifier.weight(1f), clearable = false, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        PillTextField(h, { v -> h = v.filter(Char::isDigit).take(5); h.toIntOrNull()?.let { w = EditGeometry.scaledTo(b.width, b.height, height = it).first.toString() } }, "Height", Modifier.weight(1f), clearable = false, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    val nw = w.toIntOrNull(); val nh = h.toIntOrNull()
                    Button(
                        enabled = nw != null && nh != null && nw in 1..20000 && nh in 1..20000 && (nw != b.width || nh != b.height),
                        onClick = { apply { ImageOps.resize(it, nw!!, nh!!) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text("Resize to ${w} × ${h}") }
                }
                Tool.HIDE -> {
                    ChoiceChips(listOf(ImageOps.Hide.BLUR to "Blur", ImageOps.Hide.PIXELATE to "Pixelate", ImageOps.Hide.BLACK to "Black box"), hideHow, { hideHow = it }, Modifier.padding(horizontal = 16.dp))
                    Text("Drag over faces, numbers or anything private.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                Tool.ADJUST -> {
                    LabeledSlider("Brightness", brightness, -0.5f..0.5f) { brightness = it }
                    LabeledSlider("Contrast", contrast, 0.5f..1.8f) { contrast = it }
                    LabeledSlider("Saturation", saturation, 0f..2f) { saturation = it }
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { brightness = 0f; contrast = 1f; saturation = 1f }) { Text("Reset") }
                        Button(
                            enabled = brightness != 0f || contrast != 1f || saturation != 1f,
                            onClick = { val (br, co, sa) = Triple(brightness, contrast, saturation); apply { ImageOps.adjust(it, br, co, sa) }; brightness = 0f; contrast = 1f; saturation = 1f },
                            modifier = Modifier.weight(1f),
                        ) { Text("Apply") }
                    }
                }
            }
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Tool.entries.forEach { t ->
                NavigationBarItem(
                    selected = tool == t, onClick = { tool = t }, label = { Text(t.label) },
                    icon = {
                        Icon(
                            when (t) { Tool.CROP -> Icons.Rounded.Crop; Tool.TURN -> Icons.Rounded.Rotate90DegreesCw; Tool.RESIZE -> Icons.Rounded.PhotoSizeSelectLarge; Tool.HIDE -> Icons.Rounded.BlurOn; Tool.ADJUST -> Icons.Rounded.Tune },
                            null,
                        )
                    },
                )
            }
        }
    }

    if (saving) bitmap?.let { b ->
        SaveImageDialog(source, b, adjust = Triple(brightness, contrast, saturation), onDismiss = { saving = false }, onSaved = { saving = false })
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
private fun SaveImageDialog(source: DocSource, bitmap: Bitmap, adjust: Triple<Float, Float, Float>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val original = source.name.substringAfterLast('.', "").lowercase()
    var format by remember { mutableStateOf(when (original) { "png" -> ImageOps.Format.PNG; "webp" -> ImageOps.Format.WEBP; else -> ImageOps.Format.JPEG }) }
    var quality by remember { mutableFloatStateOf(90f) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    if (saving || saved != null || error != null) {
        ExportDialog(null, saved, error, onCancel = {}, onDone = { if (saved != null) onSaved() else onDismiss() })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save a copy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceChips(ImageOps.Format.entries.map { it to it.label }, format, { format = it })
                if (format.lossy) {
                    Text("Quality ${quality.toInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(quality, { quality = it }, valueRange = 40f..100f)
                }
                Text(
                    if (format == ImageOps.Format.PNG) "PNG is lossless and keeps transparency; files are larger." else "Smaller files. ${if (format == ImageOps.Format.JPEG) "Transparent areas become white." else "Keeps transparency."}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                saving = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val (br, co, sa) = adjust
                            val final = if (br != 0f || co != 1f || sa != 1f) ImageOps.adjust(bitmap, br, co, sa) else bitmap
                            val out = outputFor(source, format.ext)
                            out.outputStream().use { ImageOps.encode(final, format, quality.toInt(), it) }
                            out
                        }
                    }
                    saving = false
                    result.onSuccess { saved = it; AppGraph.clipboard.changed() }.onFailure { error = it.message ?: "Couldn't save the picture" }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.prempoddar.oaf.ui.edit

import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material.icons.rounded.Deblur
import androidx.compose.material.icons.rounded.Compare
import com.prempoddar.oaf.ui.design.Swatch
import com.prempoddar.oaf.ui.design.Haptics
import com.prempoddar.oaf.edit.Smart
import com.prempoddar.oaf.edit.ImageEffects
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import android.widget.Toast
import com.prempoddar.oaf.ui.design.sliding
import com.prempoddar.oaf.ui.design.tap
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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

private enum class Tool(val label: String) {
    CROP("Crop"), ADJUST("Adjust"), FILTERS("Filters"), DRAW("Draw"), HIDE("Blur"), SHAPE("Shape"), FRAME("Frame"), RESIZE("Resize"), MORE("More")
}

/** What the brush or box does in the Blur tool. */
private enum class Shape { BRUSH, BOX }

/** Colour changes waiting to be applied; shown live as a filter until then. */
private data class Look(
    val brightness: Float = 0f, val contrast: Float = 1f, val saturation: Float = 1f, val warmth: Float = 0f,
    val hue: Float = 0f, val preset: ImageEffects.Preset = ImageEffects.Preset.NONE,
) {
    val changed get() = this != Look()
    fun matrix() = preset.matrix().apply {
        postConcat(ImageOps.adjustMatrix(brightness, contrast, saturation, warmth))
        if (hue != 0f) postConcat(ImageEffects.hueMatrix(hue))
    }
    fun apply(b: Bitmap) = if (changed) ImageEffects.withMatrix(b, matrix()) else b
}

/** Colours offered for drawing and text. */
private val INKS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFEC407A).map { it.toInt() }

/**
 * Crop (with turn and flip), adjust colours, one-tap filters and effects, draw and write on it,
 * blur or hide parts, cut to a shape, fit into a frame, resize, remove the background or read its
 * text; save a copy.
 */
@Composable
fun ImageEditor(source: DocSource, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded by produceState<Bitmap?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val src = source.file?.let { ImageDecoder.createSource(it) } ?: ImageDecoder.createSource(context.contentResolver, source.uri)
                ImageDecoder.decodeBitmap(src) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    d.isMutableRequired = false
                    val scale = maxOf(info.size.width, info.size.height) / 4096f
                    if (scale > 1) d.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
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
    var shape by remember { mutableStateOf(Shape.BRUSH) }
    var strength by remember { mutableFloatStateOf(0.5f) }
    var brushDp by remember { mutableFloatStateOf(36f) }
    var box by remember { mutableStateOf<Rect?>(null) }
    val stroke = remember { mutableStateListOf<Offset>() }
    var look by remember { mutableStateOf(Look()) }
    var saving by remember { mutableStateOf(false) }
    var pen by remember { mutableStateOf(ImageEffects.Pen.PEN) }
    var ink by remember { mutableIntStateOf(INKS[2]) }
    var penDp by remember { mutableFloatStateOf(6f) }
    var textAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var watermarking by remember { mutableStateOf(false) }
    var readText by remember { mutableStateOf<String?>(null) }

    fun smart(label: String, op: suspend (Bitmap) -> Bitmap) {
        val b = bitmap ?: return
        busy = true
        scope.launch {
            runCatching { op(b) }.onSuccess { next -> undo.add(b); if (undo.size > 12) undo.removeAt(0); edits = next; Haptics.confirm() }
                .onFailure { Haptics.reject(); Toast.makeText(context, "$label: ${Smart.explain(it)}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }

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
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            // Hold to see the picture as it was before any changes.
            if (edits != null || look.changed) Box(
                Modifier.size(48.dp).pointerInput(Unit) { detectTapGestures(onPress = { comparing = true; Haptics.tap(); tryAwaitRelease(); comparing = false }) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Compare, "Hold to compare with the original") }
            IconButton(enabled = undo.isNotEmpty() && !busy, onClick = tap { edits = undo.removeAt(undo.lastIndex) }) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
        }
        // The edges of the screen belong to the picture here, not to the back gesture, so crop
        // handles and brush strokes near them don't close the editor. A margin keeps handles reachable.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().systemGestureExclusion().padding(horizontal = 24.dp, vertical = 12.dp)) {
            val b = if (comparing) loaded else bitmap
            if (b == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val density = LocalDensity.current
                val boxW = constraints.maxWidth.toFloat(); val boxH = constraints.maxHeight.toFloat()
                val shown = EditGeometry.fit(b.width, b.height, boxW, boxH)
                val image = remember(b) { b.asImageBitmap() }
                val filter = if (look.changed && !comparing) ColorFilter.colorMatrix(ColorMatrix(look.matrix().array)) else null
                Image(image, null, contentScale = ContentScale.Fit, colorFilter = filter, modifier = Modifier.fillMaxSize())
                fun toScreen(r: Rect) = Rect(
                    shown.left + r.left / b.width * shown.width, shown.top + r.top / b.height * shown.height,
                    shown.left + r.right / b.width * shown.width, shown.top + r.bottom / b.height * shown.height,
                )
                val reach = with(density) { 36.dp.toPx() }
                if (tool == Tool.CROP) crop?.let { c ->
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(b, aspect) {
                            var grab = Grab.NONE
                            detectDragGestures(
                                onDragStart = { o -> grab = grabAt(toScreen(crop!!), o, reach) },
                            ) { change, d ->
                                change.consume()
                                val cur = crop ?: return@detectDragGestures
                                crop = dragCrop(cur, grab, d.x / shown.width * b.width, d.y / shown.height * b.height, b.width, b.height, aspect)
                            }
                        },
                    ) {
                        val s = toScreen(c)
                        val shade = Color.Black.copy(alpha = 0.6f)
                        drawRect(shade, Offset(0f, 0f), Size(size.width, s.top))
                        drawRect(shade, Offset(0f, s.bottom), Size(size.width, size.height - s.bottom))
                        drawRect(shade, Offset(0f, s.top), Size(s.left, s.height))
                        drawRect(shade, Offset(s.right, s.top), Size(size.width - s.right, s.height))
                        drawRect(Color.White, Offset(s.left, s.top), Size(s.width, s.height), style = Stroke(1.5.dp.toPx()))
                        for (i in 1..2) {
                            drawLine(Color.White.copy(alpha = 0.45f), Offset(s.left + s.width * i / 3, s.top), Offset(s.left + s.width * i / 3, s.bottom))
                            drawLine(Color.White.copy(alpha = 0.45f), Offset(s.left, s.top + s.height * i / 3), Offset(s.right, s.top + s.height * i / 3))
                        }
                        // L-shaped corners and short bars on the sides, like a camera app's crop frame.
                        val arm = 22.dp.toPx(); val t = 4.dp.toPx()
                        listOf(Offset(s.left, s.top) to Offset(1f, 1f), Offset(s.right, s.top) to Offset(-1f, 1f), Offset(s.left, s.bottom) to Offset(1f, -1f), Offset(s.right, s.bottom) to Offset(-1f, -1f)).forEach { (p, dir) ->
                            drawLine(Color.White, p, p + Offset(arm * dir.x, 0f), t)
                            drawLine(Color.White, p, p + Offset(0f, arm * dir.y), t)
                        }
                        val mid = Offset((s.left + s.right) / 2, (s.top + s.bottom) / 2)
                        drawLine(Color.White, Offset(mid.x - arm / 2, s.top), Offset(mid.x + arm / 2, s.top), t)
                        drawLine(Color.White, Offset(mid.x - arm / 2, s.bottom), Offset(mid.x + arm / 2, s.bottom), t)
                        drawLine(Color.White, Offset(s.left, mid.y - arm / 2), Offset(s.left, mid.y + arm / 2), t)
                        drawLine(Color.White, Offset(s.right, mid.y - arm / 2), Offset(s.right, mid.y + arm / 2), t)
                    }
                }
                if (tool == Tool.HIDE) {
                    val brushPx = with(density) { brushDp.dp.toPx() }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(b, hideHow, shape, strength, brushPx) {
                            var start = Offset.Zero
                            detectDragGestures(
                                onDragStart = { o -> start = o; box = null; stroke.clear(); stroke += o },
                                onDragCancel = { box = null; stroke.clear() },
                                onDragEnd = {
                                    val how = hideHow; val k = strength
                                    if (shape == Shape.BOX) box?.let { r -> apply { ImageOps.hide(it, r, how, k) } }
                                    else {
                                        val pts = stroke.map { EditGeometry.toImage(shown, b.width, b.height, it.x, it.y) }
                                        val width = brushPx / shown.width * b.width
                                        apply { ImageOps.hideStroke(it, pts, width, how, k) }
                                    }
                                    box = null; stroke.clear()
                                },
                            ) { change, _ ->
                                change.consume()
                                if (shape == Shape.BOX) {
                                    val (x1, y1) = EditGeometry.toImage(shown, b.width, b.height, start.x, start.y)
                                    val (x2, y2) = EditGeometry.toImage(shown, b.width, b.height, change.position.x, change.position.y)
                                    box = Rect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
                                } else stroke += change.position
                            }
                        },
                    ) {
                        box?.let { r ->
                            val s = toScreen(r)
                            drawRect(Color.White.copy(alpha = 0.3f), Offset(s.left, s.top), Size(s.width, s.height))
                            drawRect(Color.White, Offset(s.left, s.top), Size(s.width, s.height), style = Stroke(2.dp.toPx()))
                        }
                        if (stroke.isNotEmpty()) {
                            val path = Path().apply { moveTo(stroke[0].x, stroke[0].y); stroke.drop(1).forEach { lineTo(it.x, it.y) } }
                            drawPath(path, Color.White.copy(alpha = 0.45f), style = Stroke(brushPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                if (tool == Tool.DRAW && !comparing) {
                    val penPx = with(density) { penDp.dp.toPx() }
                    val toImageWidth = penPx / shown.width * b.width
                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(b, pen) { if (pen == ImageEffects.Pen.TEXT) detectTapGestures { o -> textAt = EditGeometry.toImage(shown, b.width, b.height, o.x, o.y) } }
                            .pointerInput(b, pen, ink, penPx) {
                                if (pen == ImageEffects.Pen.TEXT) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { o -> stroke.clear(); stroke += o },
                                    onDragCancel = { stroke.clear() },
                                    onDragEnd = {
                                        val pts = stroke.map { EditGeometry.toImage(shown, b.width, b.height, it.x, it.y) }
                                        val mark = ImageEffects.Mark(pen, pts, ink, toImageWidth)
                                        apply { ImageEffects.draw(it, listOf(mark)) }
                                        stroke.clear()
                                    },
                                ) { change, _ -> change.consume(); stroke += change.position }
                            },
                    ) {
                        if (stroke.isNotEmpty()) {
                            // The mark as it will be, drawn on screen until it's added to the picture.
                            drawIntoCanvas { c ->
                                ImageEffects.drawMark(c.nativeCanvas, ImageEffects.Mark(pen, stroke.map { it.x to it.y }, ink, penPx))
                            }
                        }
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
                        listOf(0f to "Free", 1f to "Square", 4f / 3 to "4:3", 3f / 4 to "3:4", 16f / 9 to "16:9", 9f / 16 to "9:16", 4f / 5 to "4:5"),
                        aspect, { a -> aspect = a; if (b != null) crop = if (a > 0) EditGeometry.aspectCrop(b.width, b.height, a) else Rect(0f, 0f, b.width.toFloat(), b.height.toFloat()) },
                        Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = tap { apply { ImageOps.rotate(it, -90f) } }) { Icon(Icons.AutoMirrored.Rounded.RotateLeft, "Turn left") }
                        IconButton(onClick = tap { apply { ImageOps.rotate(it, 90f) } }) { Icon(Icons.AutoMirrored.Rounded.RotateRight, "Turn right") }
                        IconButton(onClick = tap { apply { ImageOps.flip(it, true) } }) { Icon(Icons.Rounded.Flip, "Flip sideways") }
                        IconButton(onClick = tap { apply { ImageOps.flip(it, false) } }) { Icon(Icons.Rounded.Flip, "Flip upside down", Modifier.rotate(90f)) }
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = b != null && crop != null && crop != Rect(0f, 0f, b.width.toFloat(), b.height.toFloat()) && !busy,
                            onClick = tap { crop?.let { r -> apply { ImageOps.crop(it, r) } } },
                        ) { Text("Crop") }
                    }
                }
                Tool.ADJUST -> {
                    LabeledSlider("Brightness", look.brightness, -0.5f..0.5f) { look = look.copy(brightness = it) }
                    LabeledSlider("Contrast", look.contrast, 0.5f..1.8f) { look = look.copy(contrast = it) }
                    LabeledSlider("Saturation", look.saturation, 0f..2f) { look = look.copy(saturation = it) }
                    LabeledSlider("Warmth", look.warmth, -1f..1f) { look = look.copy(warmth = it) }
                    LabeledSlider("Hue", look.hue, -180f..180f) { look = look.copy(hue = it) }
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = look.changed, onClick = tap { look = Look() }) { Text("Reset") }
                        Button(
                            enabled = look.changed && !busy,
                            onClick = tap { val l = look; apply { l.apply(it) }; look = Look() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Apply") }
                    }
                }
                Tool.HIDE -> {
                    ChoiceChips(listOf(ImageOps.Hide.BLUR to "Blur", ImageOps.Hide.PIXELATE to "Pixelate", ImageOps.Hide.BLACK to "Black out"), hideHow, { hideHow = it }, Modifier.padding(horizontal = 16.dp))
                    ChoiceChips(listOf(Shape.BRUSH to "Brush", Shape.BOX to "Box"), shape, { shape = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (hideHow != ImageOps.Hide.BLACK) LabeledSlider("Strength ${(strength * 100).toInt()}%", strength, 0f..1f) { strength = it }
                    if (shape == Shape.BRUSH) LabeledSlider("Brush size", brushDp, 12f..96f) { brushDp = it }
                    Text(
                        if (shape == Shape.BRUSH) "Paint over faces, numbers or anything private." else "Drag a box over what to hide.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                Tool.FILTERS -> {
                    val thumb = remember(b) { b?.let { Bitmap.createScaledBitmap(it, 120, maxOf(1, 120 * it.height / it.width), true).asImageBitmap() } }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ImageEffects.Preset.entries.forEach { p ->
                            val on = look.preset == p
                            Column(
                                Modifier.clip(MaterialTheme.shapes.medium).clickable { if (!on) Haptics.select(); look = look.copy(preset = p) }
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                thumb?.let { Image(it, p.label, Modifier.size(72.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(ColorMatrix(p.matrix().array))) }
                                Text(p.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = look.changed && !busy, onClick = tap { val l = look; apply { l.apply(it) }; look = Look() }, modifier = Modifier.weight(1f)) { Text("Apply") }
                    }
                    Text("Effects", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = tap { apply { ImageEffects.sharpen(it, 0.8f) } }, label = { Text("Sharpen") }, leadingIcon = { Icon(Icons.Rounded.Deblur, null) })
                        AssistChip(onClick = tap { apply { ImageEffects.vignette(it, 0.55f) } }, label = { Text("Vignette") }, leadingIcon = { Icon(Icons.Rounded.Vignette, null) })
                        AssistChip(onClick = tap { apply { ImageEffects.grain(it, 0.35f) } }, label = { Text("Film grain") }, leadingIcon = { Icon(Icons.Rounded.Grain, null) })
                        AssistChip(onClick = tap { apply { ImageOps.blur(it, android.graphics.Rect(0, 0, it.width, it.height), minOf(it.width, it.height) / 60f) } }, label = { Text("Soften") }, leadingIcon = { Icon(Icons.Rounded.BlurOn, null) })
                    }
                }
                Tool.DRAW -> {
                    ChoiceChips(ImageEffects.Pen.entries.map { it to it.label }, pen, { pen = it }, Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        INKS.forEach { c -> Swatch(Color(c), ink == c, "Colour") { ink = c } }
                    }
                    LabeledSlider(if (pen == ImageEffects.Pen.TEXT) "Text size" else "Thickness", penDp, 2f..24f) { penDp = it }
                    Text(
                        if (pen == ImageEffects.Pen.TEXT) "Tap where the text should start." else "Draw on the picture. Each mark can be undone.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                Tool.SHAPE -> {
                    Text("Cut the middle of the picture to a shape. Outside it becomes see-through, so save as PNG or WebP.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ImageEffects.Mask.entries.forEach { m ->
                            Column(Modifier.clip(MaterialTheme.shapes.medium).clickable { Haptics.tap(); apply { ImageEffects.cutToShape(it, m) } }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val tint = MaterialTheme.colorScheme.primary
                                Canvas(Modifier.size(48.dp)) { drawIntoCanvas { c -> c.nativeCanvas.drawPath(ImageEffects.maskPath(m, size.minDimension), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = tint.toArgb() }) } }
                                Text(m.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                Tool.FRAME -> {
                    var ratio by remember { mutableFloatStateOf(1f) }
                    var fill by remember { mutableStateOf<Int?>(null) }
                    Text("Fit the whole picture into a shape for posting, with space around it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    ChoiceChips(listOf(1f to "Square", 4f / 5 to "4:5", 9f / 16 to "Story 9:16", 16f / 9 to "16:9", 3f / 2 to "3:2"), ratio, { ratio = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    ChoiceChips(listOf<Pair<Int?, String>>(null to "Blurred", android.graphics.Color.WHITE to "White", android.graphics.Color.BLACK to "Black"), fill, { fill = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    Button(enabled = !busy, onClick = tap { val r = ratio; val f = fill; apply { ImageEffects.frame(it, r, f) } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text("Add frame") }
                }
                Tool.MORE -> {
                    val tiles = listOf(
                        Triple(Icons.Rounded.AutoFixHigh, "Remove background") { smart("Remove background") { Smart.removeBackground(it) } },
                        Triple(Icons.Rounded.TextFields, "Copy text") {
                            val pic = bitmap
                            if (pic != null) { busy = true; scope.launch { runCatching { Smart.readText(pic) }.onSuccess { readText = it }.onFailure { Toast.makeText(context, Smart.explain(it), Toast.LENGTH_LONG).show() }; busy = false } }
                        },
                        Triple(Icons.Rounded.BrandingWatermark, "Watermark") { watermarking = true },
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.forEach { (icon, label, go) ->
                            Surface(onClick = tap(go), enabled = !busy, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                                }
                            }
                        }
                    }
                    Text(
                        "Removing the background and reading text use Google's on-phone tools from Play services; the picture stays on your phone. Save as PNG or WebP to keep a removed background see-through.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                    )
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
                        enabled = nw != null && nh != null && nw in 1..20000 && nh in 1..20000 && (nw != b.width || nh != b.height) && !busy,
                        onClick = tap { apply { ImageOps.resize(it, nw!!, nh!!) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text("Resize to ${w} × ${h}") }
                }
            }
        }
        // Nine tools: a row that scrolls, each an icon over its name.
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Tool.entries.forEach { t ->
                    val on = tool == t
                    Column(
                        Modifier.clip(MaterialTheme.shapes.large).clickable { if (!on) Haptics.select(); tool = t }
                            .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            when (t) {
                                Tool.CROP -> Icons.Rounded.Crop; Tool.ADJUST -> Icons.Rounded.Tune; Tool.FILTERS -> Icons.Rounded.AutoAwesome
                                Tool.DRAW -> Icons.Rounded.Brush; Tool.HIDE -> Icons.Rounded.BlurOn; Tool.SHAPE -> Icons.Rounded.Interests
                                Tool.FRAME -> Icons.Rounded.CropFree; Tool.RESIZE -> Icons.Rounded.PhotoSizeSelectLarge; Tool.MORE -> Icons.Rounded.MoreHoriz
                            },
                            null, tint = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    textAt?.let { at ->
        var text by remember(at) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textAt = null },
            title = { Text("Add text") },
            text = { PillTextField(text, { text = it }, "Text", singleLine = false, minLines = 2) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = tap {
                    val b = bitmap; textAt = null
                    if (b != null) { val size = penDp / 400f * maxOf(b.width, b.height) * 0.6f; val mark = ImageEffects.Mark(ImageEffects.Pen.TEXT, listOf(at), ink, size, text); apply { ImageEffects.draw(it, listOf(mark)) } }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { textAt = null }) { Text("Cancel") } },
        )
    }
    if (watermarking) {
        var text by remember { mutableStateOf("") }
        var opacity by remember { mutableFloatStateOf(0.35f) }
        AlertDialog(
            onDismissRequest = { watermarking = false },
            title = { Text("Watermark") },
            text = {
                Column {
                    PillTextField(text, { text = it }, "Text, like © Your Name")
                    Text("See-through ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    Slider(opacity, sliding(0.1f..0.9f) { opacity = it }, valueRange = 0.1f..0.9f)
                }
            },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = tap { val t = text; val o = opacity; watermarking = false; apply { ImageEffects.watermark(it, t, o) } }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { watermarking = false }) { Text("Cancel") } },
        )
    }
    readText?.let { found ->
        AlertDialog(
            onDismissRequest = { readText = null },
            title = { Text(if (found.isBlank()) "No text found" else "Text in the picture") },
            text = { if (found.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer { Text(found, modifier = Modifier.verticalScroll(rememberScrollState())) } },
            confirmButton = {
                if (found.isNotBlank()) TextButton(onClick = tap {
                    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Text", found))
                    Haptics.confirm(); readText = null
                }) { Text("Copy") } else TextButton(onClick = { readText = null }) { Text("OK") }
            },
            dismissButton = { if (found.isNotBlank()) TextButton(onClick = { readText = null }) { Text("Close") } },
        )
    }

    if (saving) bitmap?.let { b ->
        val l = look
        SaveImageDialog(source, b, adjust = { l.apply(it) }, onDismiss = { saving = false }, onSaved = { saving = false })
    }
}

/** Which part of the crop frame a drag holds. */
private enum class Grab { NONE, TL, TR, BL, BR, L, R, T, B, MOVE }

private fun grabAt(s: Rect, o: Offset, reach: Float): Grab {
    fun near(x: Float, y: Float) = (Offset(x, y) - o).getDistance() < reach
    val inY = o.y in s.top..s.bottom; val inX = o.x in s.left..s.right
    return when {
        near(s.left, s.top) -> Grab.TL
        near(s.right, s.top) -> Grab.TR
        near(s.left, s.bottom) -> Grab.BL
        near(s.right, s.bottom) -> Grab.BR
        inY && abs(o.x - s.left) < reach -> Grab.L
        inY && abs(o.x - s.right) < reach -> Grab.R
        inX && abs(o.y - s.top) < reach -> Grab.T
        inX && abs(o.y - s.bottom) < reach -> Grab.B
        inX && inY -> Grab.MOVE
        else -> Grab.NONE
    }
}

/** The crop after dragging [grab] by [dx], [dy] image pixels, kept inside the picture and to [aspect] if set. */
private fun dragCrop(cur: Rect, grab: Grab, dx: Float, dy: Float, w: Int, h: Int, aspect: Float): Rect {
    val min = maxOf(w, h) / 20f
    var l = cur.left; var t = cur.top; var r = cur.right; var bt = cur.bottom
    when (grab) {
        Grab.TL -> { l += dx; t += dy }
        Grab.TR -> { r += dx; t += dy }
        Grab.BL -> { l += dx; bt += dy }
        Grab.BR -> { r += dx; bt += dy }
        Grab.L -> l += dx
        Grab.R -> r += dx
        Grab.T -> t += dy
        Grab.B -> bt += dy
        Grab.MOVE -> {
            val mx = dx.coerceIn(-l, w - r); val my = dy.coerceIn(-t, h - bt)
            l += mx; r += mx; t += my; bt += my
        }
        Grab.NONE -> return cur
    }
    l = l.coerceIn(0f, r - min); t = t.coerceIn(0f, bt - min)
    r = r.coerceIn(l + min, w.toFloat()); bt = bt.coerceIn(t + min, h.toFloat())
    if (aspect > 0 && grab != Grab.MOVE) {
        // Keep the chosen shape: sides that moved sideways set the height, the rest set the width.
        if (grab == Grab.T || grab == Grab.B) {
            val nw = ((bt - t) * aspect).coerceAtMost(w.toFloat())
            val cx = (l + r) / 2
            l = (cx - nw / 2).coerceIn(0f, w - nw); r = l + nw
        } else {
            val nh = (r - l) / aspect
            if (grab == Grab.TL || grab == Grab.TR) t = (bt - nh).coerceAtLeast(0f) else if (grab == Grab.L || grab == Grab.R) {
                val cy = (t + bt) / 2; t = (cy - nh / 2).coerceIn(0f, (h - nh).coerceAtLeast(0f))
            }
            bt = (t + nh).coerceAtMost(h.toFloat())
            if (abs((r - l) / (bt - t) - aspect) > 0.01f) { val nw = (bt - t) * aspect; if (grab == Grab.TL || grab == Grab.BL || grab == Grab.L) l = r - nw else r = l + nw }
        }
    }
    return Rect(l, t, r, bt)
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value, sliding(range, onChange), valueRange = range)
    }
}

@Composable
private fun SaveImageDialog(source: DocSource, bitmap: Bitmap, adjust: (Bitmap) -> Bitmap, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val original = source.name.substringAfterLast('.', "").lowercase()
    var format by remember { mutableStateOf(when (original) { "png" -> ImageOps.Format.PNG; "webp" -> ImageOps.Format.WEBP; else -> ImageOps.Format.JPEG }) }
    var quality by remember { mutableFloatStateOf(90f) }
    var limitKb by remember { mutableIntStateOf(0) }
    var keepDetails by remember { mutableStateOf(false) }
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
                    Text("File size", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(listOf(0 to "Any", 200 to "Under 200 KB", 500 to "Under 500 KB", 1024 to "Under 1 MB", 2048 to "Under 2 MB"), limitKb, { limitKb = it }, Modifier.padding(horizontal = 0.dp))
                    if (limitKb == 0) {
                        Text("Quality ${quality.toInt()}", style = MaterialTheme.typography.labelLarge)
                        Slider(quality, sliding(40f..100f) { quality = it }, valueRange = 40f..100f)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { keepDetails = !keepDetails }) {
                    Checkbox(keepDetails, { Haptics.toggle(it); keepDetails = it })
                    Text("Keep the date, camera and location details", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (format == ImageOps.Format.PNG) "PNG is lossless and keeps transparency; files are larger." else "Smaller files. ${if (format == ImageOps.Format.JPEG) "Transparent areas become white." else "Keeps transparency."}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = tap {
                saving = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val final = adjust(bitmap)
                            val out = outputFor(source, format.ext)
                            if (limitKb > 0 && format.lossy) out.writeBytes(ImageEffects.encodeWithin(final, format, limitKb * 1024L).first)
                            else out.outputStream().use { ImageOps.encode(final, format, quality.toInt(), it) }
                            // Re-encoding drops the camera's details; put back the ones asked for.
                            if (keepDetails && format == ImageOps.Format.JPEG) runCatching { copyDetails(context, source, out) }
                            out
                        }
                    }
                    saving = false
                    result.onSuccess { saved = it; AppGraph.clipboard.changed() }.onFailure { error = it.message ?: "Couldn't save the picture" }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = tap(onDismiss)) { Text("Cancel") } },
    )
}

/** Copies the date, camera and location from the original picture's EXIF into [out] (a JPEG). */
private fun copyDetails(context: android.content.Context, source: DocSource, out: File) {
    val from = source.open(context).use { androidx.exifinterface.media.ExifInterface(it) }
    val to = androidx.exifinterface.media.ExifInterface(out)
    listOf(
        androidx.exifinterface.media.ExifInterface.TAG_DATETIME, androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL,
        androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED, androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        androidx.exifinterface.media.ExifInterface.TAG_MAKE, androidx.exifinterface.media.ExifInterface.TAG_MODEL,
        androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER, androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME,
        androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE, androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
        androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE, androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
    ).forEach { tag -> from.getAttribute(tag)?.let { to.setAttribute(tag, it) } }
    to.saveAttributes()
}

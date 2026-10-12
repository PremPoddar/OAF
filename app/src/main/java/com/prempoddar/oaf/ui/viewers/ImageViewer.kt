package com.prempoddar.oaf.ui.viewers

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.caverock.androidsvg.SVG
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import com.prempoddar.oaf.export.PrintHtml
import com.prempoddar.oaf.export.Printing
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data class Still(val bitmap: Bitmap) : Loaded
    data class Animated(val drawable: Drawable) : Loaded
    data class Failed(val reason: String) : Loaded
}

/** Photos and pictures, including HEIC/AVIF, animated GIF/WebP and SVG. Pinch to zoom, drag to pan. */
@Composable
fun ImageViewer(source: DocSource, onBack: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing && source.kind != FileKind.SVG) {
        com.prempoddar.oaf.ui.edit.ImageEditor(source, onClose = { editing = false })
        return
    }
    val context = LocalContext.current
    val paper = LocalPaper.current
    val chrome = rememberChrome()
    var rotation by remember { mutableFloatStateOf(0f) }
    val screenPx = with(androidx.compose.ui.platform.LocalConfiguration.current) { (maxOf(screenWidthDp, screenHeightDp) * context.resources.displayMetrics.density).toInt() }
    val loaded by produceState<Loaded?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (source.kind == FileKind.SVG) {
                    val svg = source.open(context).use { SVG.getFromInputStream(it) }
                    val w = (svg.documentWidth.takeIf { it > 0 } ?: 1024f).coerceAtMost(2048f)
                    val h = (svg.documentHeight.takeIf { it > 0 } ?: 1024f).coerceAtMost(2048f)
                    if (svg.documentViewBox == null) svg.setDocumentViewBox(0f, 0f, w, h)
                    svg.setDocumentWidth(w); svg.setDocumentHeight(h)
                    val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
                    svg.renderToCanvas(android.graphics.Canvas(bmp))
                    Loaded.Still(bmp)
                } else if (mayAnimate(source)) {
                    val d = ImageDecoder.decodeDrawable(imageSource(context, source)) { decoder, info, _ -> fitInside(decoder, info, 2048) }
                    if (d is AnimatedImageDrawable) Loaded.Animated(d.also { it.start() })
                    else Loaded.Still(ImageDecoder.decodeBitmap(imageSource(context, source)) { decoder, info, _ -> fitInside(decoder, info, screenPx) })
                } else {
                    // The size of the screen first, decoded straight into graphics memory: quick to
                    // show. A sharper copy is decoded only if the picture is zoomed into.
                    Loaded.Still(ImageDecoder.decodeBitmap(imageSource(context, source)) { decoder, info, _ -> fitInside(decoder, info, screenPx) })
                }
            }.getOrElse { Loaded.Failed(it.message ?: "This image couldn't be decoded") }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Zoomed in: the full picture (up to 4096 pixels across), decoded once and kept.
    val zoomed = scale > 1.4f
    val sharp by produceState<Bitmap?>(null, source, zoomed) {
        if (zoomed && value == null && loaded is Loaded.Still) {
            value = withContext(Dispatchers.IO) {
                runCatching { ImageDecoder.decodeBitmap(imageSource(context, source)) { d, info, _ -> fitInside(d, info, 4096) } }.getOrNull()
            }
        }
    }
    var menu by remember { mutableStateOf(false) }
    var pendingMime by remember { mutableStateOf("image/png") }
    val scope = rememberCoroutineScope()
    // Converts the picture as shown (turned if it was rotated) to another format.
    val convert = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val mime = pendingMime
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val base = ImageDecoder.decodeBitmap(imageSource(context, source)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                    val turned = if (rotation == 0f) base else Bitmap.createBitmap(base, 0, 0, base.width, base.height, android.graphics.Matrix().apply { postRotate(rotation) }, true)
                    val format = when (mime) { "image/jpeg" -> Bitmap.CompressFormat.JPEG; "image/webp" -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP; else -> Bitmap.CompressFormat.PNG }
                    // JPEG has no transparency: transparent areas become white rather than black.
                    val out = if (format == Bitmap.CompressFormat.JPEG && turned.hasAlpha()) Bitmap.createBitmap(turned.width, turned.height, Bitmap.Config.ARGB_8888).also { b ->
                        android.graphics.Canvas(b).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(turned, 0f, 0f, null) }
                    } else turned
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { out.compress(format, 92, it) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Saved" else "Couldn't convert this picture", Toast.LENGTH_SHORT).show()
        }
    }

    ViewerScaffold(
        title = source.name, subtitle = (loaded as? Loaded.Still)?.bitmap?.let { "${it.width} × ${it.height}" },
        chrome = chrome, themeToggle = false, onBack = onBack,
        actions = {
            if (source.kind != FileKind.SVG) IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "Edit") }
            IconButton(onClick = { rotation = (rotation + 90f) % 360f }) { Icon(Icons.AutoMirrored.Rounded.RotateRight, "Rotate") }
            IconButton(onClick = { shareSource(context, source) }) { Icon(Icons.Rounded.Share, "Share") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    listOf("PNG" to "image/png", "JPEG" to "image/jpeg", "WebP" to "image/webp").forEach { (label, mime) ->
                        DropdownMenuItem(text = { Text("Save as $label…") }, onClick = { menu = false; pendingMime = mime; convert.launch(source.name.substringBeforeLast('.') + "." + label.lowercase().replace("jpeg", "jpg")) })
                    }
                    DropdownMenuItem(text = { Text("Print or save as PDF…") }, onClick = {
                        menu = false
                        scope.launch {
                            val bytes = withContext(Dispatchers.IO) { runCatching { source.open(context).use { it.readBytes() } }.getOrNull() }
                            if (bytes != null) Printing.printHtml(context, source.name, PrintHtml.image(source.name, bytes, source.mime ?: "image/*"))
                        }
                    })
                    DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; openElsewhere(context, source) })
                }
            }
        },
    ) {
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { chrome.toggle() }, onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            val m = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y; rotationZ = rotation
            }
            when (val l = loaded) {
                null -> CircularProgressIndicator()
                is Loaded.Still -> Image((sharp ?: l.bitmap).asImageBitmap(), source.name, contentScale = ContentScale.Fit, modifier = m)
                is Loaded.Animated -> AndroidView({ ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, m, update = { it.setImageDrawable(l.drawable) })
                is Loaded.Failed -> Text(l.reason, color = paper.muted)
            }
        }
    }
}

/** Read straight from the file or the other app's link; nothing is copied first. */
private fun imageSource(context: android.content.Context, source: DocSource): ImageDecoder.Source =
    source.file?.let { ImageDecoder.createSource(it) } ?: ImageDecoder.createSource(context.contentResolver, source.uri)

/** GIFs and WebPs can move; everything else is decoded as a still, which is quicker. */
private fun mayAnimate(source: DocSource): Boolean {
    val ext = source.name.substringAfterLast('.', "").lowercase()
    return ext == "gif" || ext == "webp" || ext == "avif" || source.mime == "image/gif" || source.mime == "image/webp"
}

private fun fitInside(decoder: ImageDecoder, info: ImageDecoder.ImageInfo, max: Int) {
    val scale = maxOf(info.size.width, info.size.height).toFloat() / max
    if (scale > 1) decoder.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1), (info.size.height / scale).toInt().coerceAtLeast(1))
}

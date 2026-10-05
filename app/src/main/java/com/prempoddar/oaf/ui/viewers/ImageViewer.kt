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
    val context = LocalContext.current
    val paper = LocalPaper.current
    val chrome = rememberChrome()
    var rotation by remember { mutableFloatStateOf(0f) }
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
                } else {
                    val src = ImageDecoder.createSource(source.local(context))
                    val d = ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                        // Huge photos are scaled to what the screen can use, keeping memory in check.
                        val max = 4096
                        val scale = maxOf(info.size.width, info.size.height).toFloat() / max
                        if (scale > 1) decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    if (d is AnimatedImageDrawable) Loaded.Animated(d.also { it.start() })
                    else Loaded.Still(ImageDecoder.decodeBitmap(ImageDecoder.createSource(source.local(context))) { decoder, info, _ ->
                        val scale = maxOf(info.size.width, info.size.height).toFloat() / 4096
                        if (scale > 1) decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    })
                }
            }.getOrElse { Loaded.Failed(it.message ?: "This image couldn't be decoded") }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    ViewerScaffold(
        title = source.name, subtitle = (loaded as? Loaded.Still)?.bitmap?.let { "${it.width} × ${it.height}" },
        chrome = chrome, onBack = onBack,
        actions = {
            IconButton(onClick = { rotation = (rotation + 90f) % 360f }) { Icon(Icons.AutoMirrored.Rounded.RotateRight, "Rotate") }
            IconButton(onClick = { shareSource(context, source) }) { Icon(Icons.Rounded.Share, "Share") }
            IconButton(onClick = { openElsewhere(context, source) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open with") }
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
                is Loaded.Still -> Image(l.bitmap.asImageBitmap(), source.name, contentScale = ContentScale.Fit, modifier = m)
                is Loaded.Animated -> AndroidView({ ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, m, update = { it.setImageDrawable(l.drawable) })
                is Loaded.Failed -> Text(l.reason, color = paper.muted)
            }
        }
    }
}

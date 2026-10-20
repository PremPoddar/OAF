package com.prempoddar.oaf.ui.viewers.pdf

import com.prempoddar.oaf.ui.design.tap
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.prempoddar.oaf.ui.Paper
import kotlin.math.max
import kotlin.math.min

/** Draw a signature with a finger. It comes back as a transparent picture trimmed to the ink. */
@Composable
internal fun SignaturePad(saved: Bitmap?, onDismiss: () -> Unit, onUse: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    val dims = remember { intArrayOf(0, 0) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Sign", style = MaterialTheme.typography.titleLarge)
                Text("Draw your signature in the box.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .border(1.dp, Color(0xFFD9D4CA), RoundedCornerShape(12.dp))
                        .onSizeChanged { s: IntSize -> dims[0] = s.width; dims[1] = s.height }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current.clear(); current += it },
                                onDrag = { change, _ -> current += change.position; change.consume() },
                                onDragEnd = { strokes += current.toList(); current.clear() },
                                onDragCancel = { strokes += current.toList(); current.clear() },
                            )
                        },
                ) {
                    drawLine(Color(0xFFD9D4CA), Offset(size.width * 0.08f, size.height * 0.75f), Offset(size.width * 0.92f, size.height * 0.75f), 1.dp.toPx())
                    (strokes + listOf(current.toList())).forEach { s ->
                        if (s.isEmpty()) return@forEach
                        val path = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(path, Color(0xFF1A1A2E), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (saved != null) TextButton(onClick = tap { onUse(saved) }) { Text("Use saved") }
                    TextButton(onClick = tap { strokes.clear() }) { Text("Clear") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = tap(onDismiss)) { Text("Cancel") }
                    TextButton(enabled = strokes.isNotEmpty(), onClick = tap { render(strokes, dims[0], dims[1])?.let(onUse) }) { Text("Use") }
                }
            }
        }
    }
}

private fun render(strokes: List<List<Offset>>, w: Int, h: Int): Bitmap? {
    val points = strokes.flatten()
    if (points.isEmpty() || w <= 0 || h <= 0) return null
    val pad = 12f
    val l = max(0f, points.minOf { it.x } - pad); val t = max(0f, points.minOf { it.y } - pad)
    val r = min(w.toFloat(), points.maxOf { it.x } + pad); val b = min(h.toFloat(), points.maxOf { it.y } + pad)
    val bmp = Bitmap.createBitmap((r - l).toInt().coerceAtLeast(1), (b - t).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A1A2E.toInt(); style = Paint.Style.STROKE; strokeWidth = w / 110f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { s ->
        if (s.isEmpty()) return@forEach
        val p = android.graphics.Path().apply { moveTo(s[0].x - l, s[0].y - t); s.drop(1).forEach { lineTo(it.x - l, it.y - t) } }
        c.drawPath(p, paint)
    }
    return bmp
}

/** Every page as a thumbnail; tapping selects pages for the page tools. */
@Composable
internal fun PagesGrid(state: PdfState, paper: Paper, selected: Set<Int>, top: androidx.compose.ui.unit.Dp, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        GridCells.Adaptive(112.dp), modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(state.pages.indices.toList(), key = { it }) { i ->
            val info = state.pages[i]
            val thumb by produceState<Bitmap?>(null, i, state.version) { value = state.renderer.render(i, 320) }
            val on = i in selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(info.width / info.height).clip(RoundedCornerShape(6.dp)).background(Color.White)
                        .border(if (on) 3.dp else 1.dp, if (on) paper.accent else paper.rule, RoundedCornerShape(6.dp))
                        .clickable { onToggle(i) },
                ) { thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) } }
                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (on) paper.accent else paper.muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

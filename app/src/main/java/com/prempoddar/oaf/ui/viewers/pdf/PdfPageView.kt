package com.prempoddar.oaf.ui.viewers.pdf

import com.prempoddar.oaf.pdf.PdfElement
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.pdf.Box as PBox
import com.prempoddar.oaf.pdf.PdfField
import com.prempoddar.oaf.ui.Paper

/** What a page asks the screen to do. Positions are 0–1 across and down the page. */
internal interface PageActions {
    fun tap(page: Int, x: Float, y: Float)
    fun longPress(page: Int, x: Float, y: Float)
    fun longDrag(page: Int, x: Float, y: Float)
    fun stroke(page: Int, points: List<Pair<Float, Float>>)
    fun shape(page: Int, from: Pair<Float, Float>, to: Pair<Float, Float>)
    fun fieldTapped(field: PdfField)
    fun fieldText(field: PdfField, value: String)
}

private val DRAW_TOOLS = setOf(Tool.PEN, Tool.MARKER, Tool.RECT, Tool.ELLIPSE, Tool.ARROW)

/** Tap, long-press-and-drag, or (when [draw]) a one-finger drawing stroke. Plain drags are left to the list to scroll. */
private suspend fun PointerInputScope.pageGestures(
    draw: () -> Boolean,
    onTap: (Offset) -> Unit,
    onLong: (Offset) -> Unit, onLongMove: (Offset) -> Unit,
    onDrawStart: (Offset) -> Unit, onDrawMove: (Offset) -> Unit, onDrawEnd: () -> Unit, onDrawCancel: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (draw()) {
        down.consume()
        onDrawStart(down.position)
        while (true) {
            val e = awaitPointerEvent()
            // A second finger means scroll or zoom, not draw.
            if (e.changes.count { it.pressed } > 1) { onDrawCancel(); return@awaitEachGesture }
            val c = e.changes.first()
            if (!c.pressed) { onDrawEnd(); return@awaitEachGesture }
            onDrawMove(c.position)
            c.consume()
        }
    }
    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val e = awaitPointerEvent()
            val c = e.changes.first()
            if (e.changes.count { it.pressed } > 1) return@withTimeoutOrNull "multi"
            if (!c.pressed) return@withTimeoutOrNull if (c.isConsumed) "moved" else "up"
            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop || c.isConsumed) return@withTimeoutOrNull "moved"
        }
        @Suppress("UNREACHABLE_CODE")
        "moved"
    }
    when (outcome) {
        "up" -> onTap(down.position)
        null -> {
            onLong(down.position)
            while (true) {
                val e = awaitPointerEvent()
                val c = e.changes.first()
                if (!c.pressed) break
                onLongMove(c.position)
                c.consume()
            }
        }
    }
}

@Composable
internal fun PdfPageView(state: PdfState, index: Int, widthPx: Int, paper: Paper, actions: PageActions, modifier: Modifier = Modifier) {
    val info = state.pages.getOrNull(index) ?: return
    val version = state.version
    val rendered by produceState<Bitmap?>(null, index, version, widthPx) { value = state.renderer.render(index, widthPx) }
    // The previous drawing stays up while a new one renders, so pages don't flash.
    var shown by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(rendered) { rendered?.let { shown = it } }
    val selection = state.selection?.takeIf { it.page == index }
    val selectionBoxes by produceState(emptyList<PBox>(), selection, version) {
        value = selection?.let { s -> state.text(index).lineBoxes(s.range) } ?: emptyList()
    }
    // In edit mode every editable piece is outlined, so it's clear what a tap will pick.
    val outlining = state.mode == PdfMode.EDIT && state.tool == Tool.NONE
    val outlines by produceState(emptyList<PdfElement>(), index, version, outlining) {
        value = if (outlining) state.elements(index) else emptyList()
    }
    val stroke = remember { mutableStateListOf<Offset>() }
    var drawing by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(actions)
    val accent = paper.accent
    val ink = Color(0xFF000000.toInt() or state.color)

    BoxWithConstraints(modifier.aspectRatio(info.width / info.height).shadow(2.dp).background(Color.White)) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        fun norm(o: Offset) = (o.x / w).coerceIn(0f, 1f) to (o.y / h).coerceIn(0f, 1f)
        shown?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Canvas(
            Modifier.fillMaxSize().pointerInput(index) {
                pageGestures(
                    draw = { state.mode == PdfMode.ANNOTATE && state.tool in DRAW_TOOLS },
                    onTap = { o -> val (x, y) = norm(o); current.tap(index, x, y) },
                    onLong = { o -> val (x, y) = norm(o); current.longPress(index, x, y) },
                    onLongMove = { o -> val (x, y) = norm(o); current.longDrag(index, x, y) },
                    onDrawStart = { o -> stroke.clear(); stroke += o; drawing = true },
                    onDrawMove = { o -> if (state.tool in setOf(Tool.PEN, Tool.MARKER)) stroke += o else { if (stroke.size > 1) stroke.removeAt(stroke.lastIndex); stroke += o } },
                    onDrawEnd = {
                        drawing = false
                        val pts = stroke.map(::norm)
                        stroke.clear()
                        if (pts.size >= 2) {
                            if (state.tool == Tool.PEN || state.tool == Tool.MARKER) current.stroke(index, pts) else current.shape(index, pts.first(), pts.last())
                        }
                    },
                    onDrawCancel = { drawing = false; stroke.clear() },
                )
            },
        ) {
            fun rect(b: PBox) = Offset(b.l * size.width, b.t * size.height) to Size(b.width * size.width, b.height * size.height)
            state.hits.forEachIndexed { i, hit ->
                if (hit.page != index) return@forEachIndexed
                val c = if (i == state.hit) Color(0xFFFF9800).copy(alpha = 0.45f) else Color(0xFFFFEB3B).copy(alpha = 0.4f)
                hit.boxes.forEach { b -> val (o, s) = rect(b); drawRect(c, o, s) }
            }
            selectionBoxes.forEach { b -> val (o, s) = rect(b); drawRect(accent.copy(alpha = 0.28f), o, s) }
            val picked = state.picked?.takeIf { it.first == index }?.second
            outlines.forEach { el ->
                if (el === picked) return@forEach
                val colour = when (el.kind) {
                    PdfElement.Kind.TEXT -> accent
                    PdfElement.Kind.IMAGE -> Color(0xFF2E9E6B)
                    else -> Color(0xFF8A84A0)
                }
                val (o, s) = rect(el.box)
                // Whole-page backgrounds get only a faint edge so they don't hide everything else.
                val big = el.box.width * el.box.height > 0.5f
                if (!big) drawRect(colour.copy(alpha = 0.07f), o, s)
                drawRect(colour.copy(alpha = if (big) 0.25f else 0.7f), o, s, style = Stroke(1.dp.toPx()))
            }
            state.picked?.takeIf { it.first == index }?.second?.let { el ->
                val (o, s) = rect(el.box)
                drawRect(accent.copy(alpha = 0.1f), o, s)
                drawRect(accent, o, s, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
            }
            if (drawing && stroke.size >= 1) {
                val marker = state.tool == Tool.MARKER
                val width = (if (marker) 14f else state.strokeWidth) / info.width * size.width
                val colour = if (marker) ink.copy(alpha = 0.4f) else ink
                val a = stroke.first(); val b = stroke.last()
                when (state.tool) {
                    Tool.PEN, Tool.MARKER -> {
                        val path = Path().apply { moveTo(a.x, a.y); stroke.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(path, colour, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    Tool.RECT -> drawRect(colour, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(width))
                    Tool.ELLIPSE -> drawOval(colour, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(width))
                    else -> drawLine(colour, a, b, width, cap = StrokeCap.Round)
                }
            }
        }
        if (state.mode == PdfMode.FILL) {
            state.fields.filter { it.page == index && !it.readOnly && it.kind != PdfField.Kind.BUTTON }.forEach { f ->
                FieldOverlay(f, maxWidth * f.box.l, maxHeight * f.box.t, maxWidth * f.box.width, maxHeight * f.box.height, paper, version, actions)
            }
        }
    }
}

@Composable
private fun FieldOverlay(f: PdfField, x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, paper: Paper, version: Int, actions: PageActions) {
    val accent = paper.accent
    val base = Modifier.offset(x, y).size(w, h).background(accent.copy(alpha = 0.10f)).border(1.dp, accent.copy(alpha = 0.55f))
    when (f.kind) {
        PdfField.Kind.TEXT -> {
            var value by remember(f.name, version) { mutableStateOf(f.value) }
            var focused by remember { mutableStateOf(false) }
            val size = with(LocalDensity.current) { (if (f.multiline) 14.dp else h * 0.62f).toSp() }
            BasicTextField(
                value = value,
                onValueChange = { v -> value = if (f.maxLength > 0) v.take(f.maxLength) else v },
                singleLine = !f.multiline,
                // The page shows the saved value; the field only draws while being typed in.
                textStyle = TextStyle(color = if (focused) Color(0xFF1F1D1A) else Color.Transparent, fontSize = size),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = if (f.multiline) ImeAction.Default else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value != f.value) actions.fieldText(f, value) }),
                modifier = base.then(if (focused) Modifier.background(Color.White) else Modifier).padding(horizontal = 2.dp)
                    .onFocusChanged { s ->
                        if (focused && !s.isFocused && value != f.value) actions.fieldText(f, value)
                        focused = s.isFocused
                    },
            )
        }
        PdfField.Kind.CHOICE -> {
            var open by remember { mutableStateOf(false) }
            Box(base.clickable { open = true }) {
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    f.options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; actions.fieldText(f, o) }) }
                }
            }
        }
        PdfField.Kind.SIGNATURE -> Box(base.clickable { actions.fieldTapped(f) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Draw, "Sign here", tint = accent)
        }
        // Check boxes and radio buttons: the page itself shows the tick once it's set.
        else -> Box(base.clickable { actions.fieldTapped(f) })
    }
}

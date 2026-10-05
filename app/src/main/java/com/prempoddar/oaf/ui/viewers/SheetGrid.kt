package com.prempoddar.oaf.ui.viewers

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.core.content.res.ResourcesCompat
import com.prempoddar.oaf.R
import com.prempoddar.oaf.sheet.A1
import com.prempoddar.oaf.sheet.CellStyle
import com.prempoddar.oaf.sheet.NumFmt
import com.prempoddar.oaf.sheet.Sheet
import com.prempoddar.oaf.sheet.SheetEditor
import com.prempoddar.oaf.sheet.Value
import com.prempoddar.oaf.ui.Paper
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Where the grid is scrolled to and how far it's zoomed. */
class GridState {
    var sx by mutableFloatStateOf(0f)
    var sy by mutableFloatStateOf(0f)
    var zoom by mutableFloatStateOf(1f)
    internal var viewW = 0f
    internal var viewH = 0f
    internal var fling: Job? = null
    internal var geo: Geo? = null

    /** Scrolls just enough to show a cell. */
    fun reveal(r: Int, c: Int) {
        val g = geo ?: return
        if (c >= g.fc) {
            val left = g.cols.start(c) - g.frozenW
            val right = left + g.cols.size(c)
            val span = viewW - g.headerW - g.frozenW
            if (left < sx) sx = left else if (right > sx + span) sx = (right - span).coerceAtMost(left)
        }
        if (r >= g.fr) {
            val top = g.rows.start(r) - g.frozenH
            val bottom = top + g.rows.size(r)
            val span = viewH - g.headerH - g.frozenH
            if (top < sy) sy = top else if (bottom > sy + span) sy = (bottom - span).coerceAtMost(top)
        }
    }
}

sealed interface Hit {
    data object Corner : Hit
    data class Col(val c: Int) : Hit
    data class Row(val r: Int) : Hit
    data class Cell(val r: Int, val c: Int) : Hit
}

/** Positions along one axis with a few custom sizes, in pixels. */
internal class Axis(private val def: Float, custom: Map<Int, Float>, private val max: Int) {
    private val keys = custom.keys.filter { it <= max }.sorted().toIntArray()
    private val sizes = FloatArray(keys.size) { custom.getValue(keys[it]) }
    private val extra = FloatArray(keys.size + 1).also { for (i in keys.indices) it[i + 1] = it[i] + sizes[i] - def }

    fun size(i: Int): Float { val k = keys.binarySearch(i); return if (k >= 0) sizes[k] else def }

    fun start(i: Int): Float {
        var k = keys.binarySearch(i)
        if (k < 0) k = -k - 1
        return i * def + extra[k]
    }

    fun indexAt(p: Float): Int {
        var lo = 0; var hi = max
        while (lo < hi) { val mid = (lo + hi + 1) ushr 1; if (start(mid) <= p) lo = mid else hi = mid - 1 }
        return lo
    }
}

/** The grid's layout for one frame: header sizes, frozen panes and where every row and column sits. */
internal class Geo(sheet: Sheet, zoom: Float, density: Float, widthOverride: Pair<Int, Float>?) {
    val headerW = 44f * density
    val headerH = 26f * density
    private val unit = density * zoom
    val cols = Axis(COL_DEFAULT * unit, sheet.colWidths.mapValues { (c, w) -> if (widthOverride?.first == c) widthOverride.second else charsToDp(w) * unit }
        .let { m -> if (widthOverride != null && widthOverride.first !in m) m + (widthOverride.first to widthOverride.second) else m }, SheetEditor.MAX_COL)
    val rows = Axis(ROW_H * unit, emptyMap(), SheetEditor.MAX_ROW)
    val fr = sheet.frozenRows
    val fc = sheet.frozenCols
    val frozenW = cols.start(fc)
    val frozenH = rows.start(fr)
    val zoomUnit = unit

    fun x(c: Int, sx: Float) = headerW + cols.start(c) - if (c >= fc) sx else 0f
    fun y(r: Int, sy: Float) = headerH + rows.start(r) - if (r >= fr) sy else 0f

    fun hit(p: Offset, sx: Float, sy: Float): Hit {
        if (p.x < headerW && p.y < headerH) return Hit.Corner
        val px = p.x - headerW; val py = p.y - headerH
        val c = if (px < frozenW) cols.indexAt(px) else cols.indexAt(px + sx)
        val r = if (py < frozenH) rows.indexAt(py) else rows.indexAt(py + sy)
        return when {
            p.y < headerH -> Hit.Col(c)
            p.x < headerW -> Hit.Row(r)
            else -> Hit.Cell(r, c)
        }
    }

    companion object {
        const val COL_DEFAULT = 64f
        const val ROW_H = 24f
        /** Excel widths are in characters of the default font: about 7 px each plus 5 px padding. */
        fun charsToDp(w: Float) = w * 7f + 5f
        fun dpToChars(dp: Float) = ((dp - 5f) / 7f).coerceAtLeast(1f)
    }
}

@Composable
fun SheetGrid(
    editor: SheetEditor,
    tick: Int,
    state: GridState,
    paper: Paper,
    headerColor: Color,
    modifier: Modifier = Modifier,
    onTap: (Hit) -> Unit,
    onRangeStart: (Int, Int) -> Unit,
    onRangeTo: (Int, Int) -> Unit,
    onResize: (col: Int, widthChars: Float) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    val config = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    var resize by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    val paints = remember {
        val base = runCatching { ResourcesCompat.getFont(context, R.font.inter) }.getOrNull() ?: Typeface.DEFAULT
        Paints(base)
    }
    val current by rememberUpdatedState(editor)
    val tapCb by rememberUpdatedState(onTap)
    val startCb by rememberUpdatedState(onRangeStart)
    val toCb by rememberUpdatedState(onRangeTo)
    val resizeCb by rememberUpdatedState(onResize)

    fun geo() = Geo(current.sheet, state.zoom, density, resize)

    fun clamp() {
        val g = state.geo ?: return
        val s = current.sheet
        val maxX = (g.cols.start(maxOf(s.maxCol + 6, 26)) - (state.viewW - g.headerW)).coerceAtLeast(0f)
        val maxY = (g.rows.start(maxOf(s.maxRow + 30, 100)) - (state.viewH - g.headerH)).coerceAtLeast(0f)
        state.sx = state.sx.coerceIn(0f, maxX)
        state.sy = state.sy.coerceIn(0f, maxY)
    }

    Canvas(
        modifier.pointerInput(Unit) {
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    state.fling?.cancel()
                    val g = geo().also { state.geo = it }
                    // Dragging the edge of a column header resizes the column.
                    if (down.position.y < g.headerH && down.position.x > g.headerW) {
                        val edge = (0..1).map { d -> g.cols.indexAt(down.position.x - g.headerW + (if (down.position.x - g.headerW >= g.frozenW) state.sx else 0f)) - d }
                            .firstOrNull { c -> c >= 0 && abs(g.x(c, state.sx) + g.cols.size(c) - down.position.x) < 14f * density }
                        if (edge != null) {
                            down.consume()
                            var w = g.cols.size(edge)
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.first()
                                if (!ch.pressed) break
                                w = (w + ch.positionChange().x).coerceAtLeast(16f * density)
                                resize = edge to w
                                ch.consume()
                            }
                            resize = null
                            resizeCb(edge, Geo.dpToChars(w / (density * state.zoom)))
                            return@awaitEachGesture
                        }
                    }
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var mode = 0 // 0 pending, 1 pan, 2 zoom, 3 select
                    var moved = Offset.Zero
                    var now = down.uptimeMillis
                    while (true) {
                        val event = if (mode == 0) {
                            // Counted from touch-down, so a slightly shaky finger still long-presses.
                            val remaining = down.uptimeMillis + config.longPressTimeoutMillis - now
                            (if (remaining > 0) withTimeoutOrNull(remaining) { awaitPointerEvent() } else null) ?: run {
                                val hit = g.hit(down.position, state.sx, state.sy)
                                if (hit is Hit.Cell) { mode = 3; haptics.performHapticFeedback(HapticFeedbackType.LongPress); startCb(hit.r, hit.c) } else mode = 1
                                null
                            }
                        } else awaitPointerEvent()
                        if (event == null) continue
                        now = event.changes.first().uptimeMillis
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            if (mode == 0) tapCb(g.hit(down.position, state.sx, state.sy))
                            if (mode == 1) {
                                val v = tracker.calculateVelocity()
                                state.fling = launch {
                                    launch { AnimationState(state.sx, -v.x).animateDecay(exponentialDecay<Float>()) { state.sx = value; clamp(); if (state.sx != value) cancelAnimation() } }
                                    launch { AnimationState(state.sy, -v.y).animateDecay(exponentialDecay<Float>()) { state.sy = value; clamp(); if (state.sy != value) cancelAnimation() } }
                                }
                            }
                            break
                        }
                        if (pressed.size > 1 && mode != 3) mode = 2
                        when (mode) {
                            0 -> {
                                moved += event.changes.first().positionChange()
                                if (moved.getDistance() > config.touchSlop) mode = 1
                            }
                            1 -> {
                                val ch = pressed.first()
                                val d = ch.positionChange()
                                state.sx -= d.x; state.sy -= d.y; clamp()
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                            }
                            2 -> {
                                val z = event.calculateZoom()
                                val centre = event.calculateCentroid()
                                val pan = pressed.fold(Offset.Zero) { acc, c -> acc + c.positionChange() } / pressed.size.toFloat()
                                val old = state.zoom
                                val new = (old * z).coerceIn(0.4f, 3f)
                                val k = new / old
                                state.zoom = new
                                state.sx = (state.sx + centre.x - g.headerW) * k - (centre.x - g.headerW) - pan.x
                                state.sy = (state.sy + centre.y - g.headerH) * k - (centre.y - g.headerH) - pan.y
                                state.geo = geo(); clamp()
                            }
                            3 -> {
                                val ch = pressed.first()
                                val p = ch.position
                                // Nudge the view along when the finger reaches an edge.
                                val edge = 36f * density
                                if (p.x > state.viewW - edge) state.sx += 12f * density else if (p.x < g.headerW + edge) state.sx -= 12f * density
                                if (p.y > state.viewH - edge) state.sy += 12f * density else if (p.y < g.headerH + edge) state.sy -= 12f * density
                                clamp()
                                val hit = g.hit(p, state.sx, state.sy)
                                if (hit is Hit.Cell) toCb(hit.r, hit.c)
                            }
                        }
                        event.changes.forEach(PointerInputChange::consume)
                    }
                }
            }
        },
    ) {
        tick.hashCode() // redraw on every model change
        state.viewW = size.width; state.viewH = size.height
        val g = Geo(editor.sheet, state.zoom, density, resize).also { state.geo = it }
        drawGrid(editor, g, state.sx, state.sy, paper, headerColor, paints)
    }
}

internal class Paints(base: Typeface) {
    private val faces = HashMap<Int, Typeface>()
    private val baseFace = base
    val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    fun face(bold: Boolean, italic: Boolean): Typeface = faces.getOrPut((if (bold) 1 else 0) + (if (italic) 2 else 0)) {
        Typeface.create(baseFace, if (bold) 650 else 400, italic)
    }
}

private val ERROR_RED = Color(0xFFD9534F)

private fun DrawScope.drawGrid(editor: SheetEditor, g: Geo, sx: Float, sy: Float, paper: Paper, headerColor: Color, paints: Paints) {
    val sheet = editor.sheet
    drawRect(paper.page)
    val w = size.width; val h = size.height
    // Visible columns and rows in the scrolling part.
    val c0 = g.cols.indexAt(g.frozenW + sx).coerceAtLeast(g.fc)
    val c1 = g.cols.indexAt(g.frozenW + sx + (w - g.headerW - g.frozenW))
    val r0 = g.rows.indexAt(g.frozenH + sy).coerceAtLeast(g.fr)
    val r1 = g.rows.indexAt(g.frozenH + sy + (h - g.headerH - g.frozenH))
    val frozenCols = 0 until g.fc
    val frozenRows = 0 until g.fr
    val bodyLeft = g.headerW + g.frozenW
    val bodyTop = g.headerH + g.frozenH

    fun region(rows: IntRange, cols: IntRange, clip: Rect) {
        if (rows.isEmpty() || cols.isEmpty() || clip.width <= 0 || clip.height <= 0) return
        clipRect(clip.left, clip.top, clip.right, clip.bottom) { drawCells(sheet, rows, cols, g, sx, sy, paper, paints) }
    }
    region(r0..r1, c0..c1, Rect(bodyLeft, bodyTop, w, h))
    region(frozenRows, c0..c1, Rect(bodyLeft, g.headerH, w, bodyTop))
    region(r0..r1, frozenCols, Rect(g.headerW, bodyTop, bodyLeft, h))
    region(frozenRows, frozenCols, Rect(g.headerW, g.headerH, bodyLeft, bodyTop))

    // Selection.
    val sel = editor.selection
    fun rectOf(r1x: Int, c1x: Int, r2x: Int, c2x: Int): Rect {
        val left = g.x(c1x, sx); val top = g.y(r1x, sy)
        val right = g.x(minOf(c2x, c1 + 1), sx) + g.cols.size(minOf(c2x, c1 + 1))
        val bottom = g.y(minOf(r2x, r1 + 1), sy) + g.rows.size(minOf(r2x, r1 + 1))
        return Rect(left, top, right, bottom)
    }
    clipRect(g.headerW, g.headerH, w, h) {
        val sr = rectOf(sel.r1, sel.c1, sel.r2, sel.c2)
        if (sel.r1 != sel.r2 || sel.c1 != sel.c2) drawRect(paper.accent.copy(alpha = 0.10f), sr.topLeft, sr.size)
        drawRect(paper.accent, sr.topLeft, sr.size, style = Stroke(2f * density()))
        val (cr, cc) = editor.cursor
        val merge = sheet.merges.firstOrNull { it.r1 == cr && it.c1 == cc }
        val cur = if (merge != null) rectOf(merge.r1, merge.c1, merge.r2, merge.c2) else rectOf(cr, cc, cr, cc)
        drawRect(paper.accent, cur.topLeft, cur.size, style = Stroke(2.5f * density()))
    }

    // Headers.
    drawRect(headerColor, Offset(0f, 0f), Size(w, g.headerH))
    drawRect(headerColor, Offset(0f, 0f), Size(g.headerW, h))
    val tp = paints.text
    tp.typeface = paints.face(bold = false, italic = false)
    tp.textSize = 11.5f * density()
    tp.textAlign = Paint.Align.CENTER
    fun headerCell(rect: Rect, label: String, on: Boolean) {
        if (on) drawRect(paper.accent.copy(alpha = 0.16f), rect.topLeft, rect.size)
        drawLine(paper.rule, Offset(rect.right, rect.top), Offset(rect.right, rect.bottom))
        drawLine(paper.rule, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom))
        tp.color = (if (on) paper.accent else paper.muted).toArgb()
        drawIntoCanvas { it.nativeCanvas.drawText(label, rect.center.x, rect.center.y - (tp.ascent() + tp.descent()) / 2, tp) }
    }
    clipRect(g.headerW, 0f, w, g.headerH) {
        (frozenCols + (c0..c1)).forEach { c -> val x = g.x(c, sx); headerCell(Rect(x, 0f, x + g.cols.size(c), g.headerH), A1.colName(c), c in sel.c1..sel.c2) }
    }
    clipRect(0f, g.headerH, g.headerW, h) {
        (frozenRows + (r0..r1)).forEach { r -> val y = g.y(r, sy); headerCell(Rect(0f, y, g.headerW, y + g.rows.size(r)), (r + 1).toString(), r in sel.r1..sel.r2) }
    }
    if (g.fc > 0) drawLine(paper.muted, Offset(bodyLeft, 0f), Offset(bodyLeft, h), strokeWidth = 1.5f * density())
    if (g.fr > 0) drawLine(paper.muted, Offset(0f, bodyTop), Offset(w, bodyTop), strokeWidth = 1.5f * density())
    drawLine(paper.rule, Offset(g.headerW, 0f), Offset(g.headerW, h))
    drawLine(paper.rule, Offset(0f, g.headerH), Offset(w, g.headerH))
}

private fun DrawScope.density() = density

private fun shownFill(fill: Int?, paper: Paper): Color? {
    val c = Color(fill ?: return null)
    if (!paper.dark) return c
    // Dark pages: plain white fills vanish and colours are dimmed so text stays readable.
    if (c.luminance() > 0.93f) return null
    return lerp(c, Color.Black, 0.55f)
}

private fun shownText(style: CellStyle, fill: Color?, paper: Paper, v: Value): Color {
    if (v is Value.Err) return ERROR_RED
    style.color?.let { val c = Color(it); if (!paper.dark || c.luminance() > 0.25f) return c }
    if (fill != null) return if (fill.luminance() > 0.5f) Color(0xFF1F1D1A) else Color(0xFFF1EEE8)
    return paper.ink
}

private fun DrawScope.drawCells(sheet: Sheet, rows: IntRange, cols: IntRange, g: Geo, sx: Float, sy: Float, paper: Paper, paints: Paints) {
    val merges = sheet.merges.filter { m -> m.r2 >= rows.first && m.r1 <= rows.last && m.c2 >= cols.first && m.c1 <= cols.last }
    fun covered(r: Int, c: Int) = merges.any { it.contains(r, c) }

    // Fills.
    for (r in rows) for (c in cols) {
        if (covered(r, c)) continue
        val cell = sheet[r, c] ?: continue
        val fill = shownFill(cell.style.fill, paper) ?: continue
        drawRect(fill, Offset(g.x(c, sx), g.y(r, sy)), Size(g.cols.size(c), g.rows.size(r)))
    }
    // Grid lines.
    val left = g.x(cols.first, sx); val right = g.x(cols.last, sx) + g.cols.size(cols.last)
    val top = g.y(rows.first, sy); val bottom = g.y(rows.last, sy) + g.rows.size(rows.last)
    for (c in cols) { val x = g.x(c, sx) + g.cols.size(c); drawLine(paper.rule, Offset(x, top), Offset(x, bottom)) }
    for (r in rows) { val y = g.y(r, sy) + g.rows.size(r); drawLine(paper.rule, Offset(left, y), Offset(right, y)) }
    // Merged cells cover the lines inside them.
    merges.forEach { m ->
        val x = g.x(m.c1, sx); val y = g.y(m.r1, sy)
        val mw = g.cols.start(m.c2 + 1) - g.cols.start(m.c1); val mh = g.rows.start(m.r2 + 1) - g.rows.start(m.r1)
        val fill = sheet[m.r1, m.c1]?.style?.fill?.let { shownFill(it, paper) } ?: paper.page
        drawRect(fill, Offset(x + 0.5f, y + 0.5f), Size(mw - 1f, mh - 1f))
        drawRect(paper.rule, Offset(x, y), Size(mw, mh), style = Stroke(1f))
        sheet[m.r1, m.c1]?.let { drawText(it, Rect(x, y, x + mw, y + mh), Rect(x, y, x + mw, y + mh), g, paper, paints) }
    }
    // Text, spilling into empty neighbours on the right like spreadsheets do.
    for (r in rows) for (c in cols) {
        if (covered(r, c)) continue
        val cell = sheet[r, c] ?: continue
        if (cell.value == Value.Empty && cell.input.isEmpty()) continue
        val x = g.x(c, sx); val y = g.y(r, sy)
        val box = Rect(x, y, x + g.cols.size(c), y + g.rows.size(r))
        var spill = box
        if (cell.value is Value.Text && cell.style.align == CellStyle.Align.GENERAL || cell.style.align == CellStyle.Align.LEFT) {
            var n = c + 1
            while (n <= cols.last + 8 && n - c <= 12 && (sheet[r, n]?.value ?: Value.Empty) == Value.Empty && merges.none { it.contains(r, n) }) n++
            spill = Rect(x, y, g.x(n, sx), box.bottom)
        }
        drawText(cell, box, spill, g, paper, paints)
    }
}

private fun DrawScope.drawText(cell: com.prempoddar.oaf.sheet.Cell, box: Rect, clip: Rect, g: Geo, paper: Paper, paints: Paints) {
    val v = cell.value
    val st = cell.style
    val text = NumFmt.format(v, st.numFmt)
    if (text.isEmpty()) return
    val tp = paints.text
    tp.typeface = paints.face(st.bold, st.italic)
    tp.textSize = 13f * g.zoomUnit * ((st.fontSize ?: 11.0) / 11.0).toFloat().coerceIn(0.6f, 2.2f)
    tp.isUnderlineText = st.underline
    tp.isStrikeThruText = st.strike
    tp.color = shownText(st, shownFill(st.fill, paper), paper, v).toArgb()
    val pad = 4f * g.zoomUnit
    val align = when (st.align) {
        CellStyle.Align.LEFT -> Paint.Align.LEFT
        CellStyle.Align.CENTER -> Paint.Align.CENTER
        CellStyle.Align.RIGHT -> Paint.Align.RIGHT
        CellStyle.Align.GENERAL -> when (v) { is Value.Num -> Paint.Align.RIGHT; is Value.Bool, is Value.Err -> Paint.Align.CENTER; else -> Paint.Align.LEFT }
    }
    tp.textAlign = align
    val x = when (align) { Paint.Align.LEFT -> box.left + pad; Paint.Align.CENTER -> box.center.x; else -> box.right - pad }
    val y = box.center.y - (tp.ascent() + tp.descent()) / 2
    val shown = if (v is Value.Num && align == Paint.Align.RIGHT && tp.measureText(text) > box.width - pad) "#".repeat(((box.width - pad) / tp.measureText("#")).toInt().coerceAtLeast(1)) else text
    clipRect(clip.left, clip.top, clip.right, clip.bottom) {
        drawIntoCanvas { it.nativeCanvas.drawText(shown, x, y, tp) }
    }
    tp.isUnderlineText = false
    tp.isStrikeThruText = false
}

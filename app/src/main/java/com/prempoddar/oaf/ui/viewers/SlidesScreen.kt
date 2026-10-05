package com.prempoddar.oaf.ui.viewers

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.slides.Deck
import com.prempoddar.oaf.slides.SPara
import com.prempoddar.oaf.slides.Slide
import com.prempoddar.oaf.slides.SlideShape
import com.prempoddar.oaf.slides.Slides
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SlidesScreen(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val deck by produceState<Result<Deck>?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = source.local(context)
                when (source.kind) { FileKind.PPT -> Slides.ppt(f); FileKind.ODP -> Slides.odp(f); else -> Slides.pptx(f) }
            }
        }
    }
    DisposableEffect(deck) { onDispose { deck?.getOrNull()?.close() } }
    var notes by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var presenting by remember { mutableStateOf<Int?>(null) }
    val d = deck?.getOrNull()

    val start = presenting
    if (d != null && start != null) {
        Presenter(d, start) { presenting = null }
        return
    }

    ViewerScaffold(
        title = source.name,
        subtitle = d?.let { "${source.kind.label} · ${it.slides.size} slides" + if (source.kind == FileKind.PPT) " · text only" else "" } ?: source.kind.label,
        chrome = rememberChrome(), autoHide = false, onBack = onBack,
        actions = {
            if (d != null && d.slides.isNotEmpty()) IconButton(onClick = { presenting = 0 }) { Icon(Icons.Rounded.PlayArrow, "Present") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (notes) "Hide speaker notes" else "Show speaker notes") }, onClick = { menu = false; notes = !notes })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareSource(context, source) })
                    DropdownMenuItem(text = { Text("Open with…") }, onClick = { menu = false; openElsewhere(context, source) })
                }
            }
        },
    ) {
        val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 72.dp
        when {
            deck == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            d == null -> Text(deck?.exceptionOrNull()?.message ?: "Couldn't open this presentation", color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = top))
            else -> LazyColumn(
                Modifier.fillMaxSize().background(if (paper.dark) Color(0xFF0E0E0E) else Color(0xFFECE8E1)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(d.slides) { i, slide ->
                    Column(Modifier.widthIn(max = 760.dp)) {
                        Text("${i + 1}" + (slide.title?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = paper.muted, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
                        SlideView(d, slide, Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).clickable { presenting = i })
                        if (notes && slide.notes != null) Text(slide.notes, style = MaterialTheme.typography.bodyMedium, color = paper.ink, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

/** Full screen and sideways, one slide at a time; tap or swipe to move on. */
@Composable
private fun Presenter(deck: Deck, start: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(Unit) {
        val before = activity?.requestedOrientation
        if (deck.width > deck.height) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (before != null) activity.requestedOrientation = before }
    }
    BackHandler(onBack = onExit)
    val pager = rememberPagerState(start) { deck.slides.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            Box(Modifier.fillMaxSize().clickable { scope.launch { if (i < deck.slides.lastIndex) pager.animateScrollToPage(i + 1) else onExit() } }, contentAlignment = Alignment.Center) {
                SlideView(deck, deck.slides[i], Modifier.fillMaxSize())
            }
        }
        Text("${pager.currentPage + 1} / ${deck.slides.size}", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
    }
}

@Composable
private fun SlideView(deck: Deck, slide: Slide, modifier: Modifier) {
    val pictures by produceState<Map<String, Bitmap>>(emptyMap(), slide) {
        value = withContext(Dispatchers.IO) {
            slide.shapes.filterIsInstance<SlideShape.Picture>().map { it.path }.distinct().mapNotNull { path ->
                val bytes = deck.image(path) ?: return@mapNotNull null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1600) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.let { path to it }
            }.toMap()
        }
    }
    Canvas(modifier.aspectRatio(deck.width / deck.height, matchHeightConstraintsFirst = false)) {
        val s = size.width / deck.width
        drawIntoCanvas { c -> drawSlide(c.nativeCanvas, slide, s, pictures) }
    }
}

private val sans = Typeface.create("sans-serif", Typeface.NORMAL)

private fun drawSlide(canvas: android.graphics.Canvas, slide: Slide, s: Float, pictures: Map<String, Bitmap>) {
    canvas.drawColor(0xFF000000.toInt() or (slide.background ?: 0xFFFFFF))
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    for (shape in slide.shapes) {
        val r = RectF(shape.x * s, shape.y * s, (shape.x + shape.w) * s, (shape.y + shape.h) * s)
        when (shape) {
            is SlideShape.Picture -> pictures[shape.path]?.let { canvas.drawBitmap(it, null, r, bitmapPaint) }
            is SlideShape.Text -> {
                canvas.save()
                if (shape.rotation != 0f) canvas.rotate(shape.rotation, r.centerX(), r.centerY())
                shape.fill?.let { fill.color = 0xFF000000.toInt() or it; outline(canvas, shape.geometry, r, fill) }
                shape.line?.let { stroke.color = 0xFF000000.toInt() or it; stroke.strokeWidth = (shape.lineWidth * s).coerceAtLeast(1f); outline(canvas, shape.geometry, r, stroke) }
                val inset = RectF(r.left + 7.2f * s, r.top + 3.6f * s, r.right - 7.2f * s, r.bottom - 3.6f * s)
                val textColor = shape.fill?.let { if (luminance(it) < 0.45f) 0xFFFFFF else 0x1A1A1A } ?: 0x1A1A1A
                drawText(canvas, shape.paragraphs, inset, s, textColor, shape.anchor)
                canvas.restore()
            }
            is SlideShape.Table -> {
                val cols = shape.columns.ifEmpty { List(shape.rows.maxOfOrNull { it.size } ?: 1) { shape.w / (shape.rows.maxOfOrNull { it.size } ?: 1) } }
                var y = r.top
                stroke.color = 0xFF9E9E9E.toInt(); stroke.strokeWidth = s.coerceAtLeast(1f)
                shape.rows.forEachIndexed { ri, row ->
                    val layouts = row.mapIndexed { ci, cell -> layout(cell, ((cols.getOrElse(ci) { 60f } - 7.2f) * s).toInt().coerceAtLeast(1), s, 0x1A1A1A, 1f) }
                    val h = (layouts.maxOfOrNull { it.height } ?: 0) + 7.2f * s
                    var x = r.left
                    row.indices.forEach { ci ->
                        val w = cols.getOrElse(ci) { 60f } * s
                        if (ri == 0) { fill.color = 0xFFE8EEF7.toInt(); canvas.drawRect(x, y, x + w, y + h, fill) }
                        canvas.drawRect(x, y, x + w, y + h, stroke)
                        canvas.save(); canvas.translate(x + 3.6f * s, y + 3.6f * s); layouts[ci].draw(canvas); canvas.restore()
                        x += w
                    }
                    y += h
                }
            }
        }
    }
}

private fun outline(canvas: android.graphics.Canvas, geometry: String, r: RectF, paint: Paint) = when (geometry) {
    "ellipse" -> canvas.drawOval(r, paint)
    "roundRect", "flowChartAlternateProcess" -> canvas.drawRoundRect(r, minOf(r.width(), r.height()) * 0.16f, minOf(r.width(), r.height()) * 0.16f, paint)
    "line", "straightConnector1" -> canvas.drawLine(r.left, r.top, r.right, r.bottom, paint)
    else -> canvas.drawRect(r, paint)
}

private fun luminance(rgb: Int) = (0.2126f * (rgb shr 16 and 0xFF) + 0.7152f * (rgb shr 8 and 0xFF) + 0.0722f * (rgb and 0xFF)) / 255f

private fun layout(paras: List<SPara>, width: Int, s: Float, defaultColor: Int, shrink: Float): StaticLayout {
    val text = SpannableStringBuilder()
    paras.forEachIndexed { i, p ->
        if (i > 0) text.append('\n')
        val start = text.length
        p.bullet?.let { b ->
            val bs = text.length
            text.append(b).append(" ")
            (p.runs.firstOrNull()?.size)?.let { text.setSpan(AbsoluteSizeSpan((it * s * shrink).toInt().coerceAtLeast(1)), bs, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        p.runs.forEach { run ->
            val rs = text.length
            text.append(run.text)
            val re = text.length
            if (re == rs) return@forEach
            run.size?.let { text.setSpan(AbsoluteSizeSpan((it * s * shrink).toInt().coerceAtLeast(1)), rs, re, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            val style = (if (run.bold) Typeface.BOLD else 0) or (if (run.italic) Typeface.ITALIC else 0)
            if (style != 0) text.setSpan(StyleSpan(style), rs, re, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (run.underline) text.setSpan(UnderlineSpan(), rs, re, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(0xFF000000.toInt() or (run.color ?: defaultColor)), rs, re, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val end = text.length
        if (end > start) {
            val align = when (p.align) { "ctr" -> Layout.Alignment.ALIGN_CENTER; "r" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
            text.setSpan(AlignmentSpan.Standard(align), start, end, Spanned.SPAN_PARAGRAPH)
            if (p.level > 0) text.setSpan(LeadingMarginSpan.Standard((p.level * 27f * s).toInt()), start, end, Spanned.SPAN_PARAGRAPH)
        }
    }
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = sans; textSize = 18f * s * shrink; color = 0xFF000000.toInt() or defaultColor }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setIncludePad(false).setLineSpacing(0f, 0.95f).build()
}

/** Lays text out in [box], shrinking it until it fits the way PowerPoint's autofit does. */
private fun drawText(canvas: android.graphics.Canvas, paras: List<SPara>, box: RectF, s: Float, defaultColor: Int, anchor: String) {
    if (paras.all { p -> p.runs.all { it.text.isBlank() } && p.bullet == null }) return
    val width = box.width().toInt().coerceAtLeast(1)
    var shrink = 1f
    var l = layout(paras, width, s, defaultColor, shrink)
    while (l.height > box.height() && shrink > 0.45f) { shrink -= 0.08f; l = layout(paras, width, s, defaultColor, shrink) }
    val y = when (anchor) { "ctr" -> box.top + (box.height() - l.height) / 2; "b" -> box.bottom - l.height; else -> box.top }
    canvas.save()
    canvas.translate(box.left, y)
    l.draw(canvas)
    canvas.restore()
}

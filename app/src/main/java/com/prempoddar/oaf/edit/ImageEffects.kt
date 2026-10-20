package com.prempoddar.oaf.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Looks, effects, shapes, frames, drawing and watermarks for the picture editor. */
object ImageEffects {
    // ------------------------------------------------------------ filters

    /** One-tap looks, each a colour matrix so it can be previewed live. */
    enum class Preset(val label: String) {
        NONE("Original"), VIVID("Vivid"), WARM("Warm"), COOL("Cool"), FADE("Fade"), VINTAGE("Vintage"),
        SEPIA("Sepia"), MONO("Mono"), NOIR("Noir"), DRAMA("Drama"), INVERT("Invert");

        fun matrix(): ColorMatrix = when (this) {
            NONE -> ColorMatrix()
            VIVID -> ImageOps.adjustMatrix(0.02f, 1.12f, 1.45f)
            WARM -> ImageOps.adjustMatrix(0.02f, 1.02f, 1.08f, 0.8f)
            COOL -> ImageOps.adjustMatrix(0f, 1.02f, 1f, -0.8f)
            FADE -> ImageOps.adjustMatrix(0.08f, 0.78f, 0.8f)
            VINTAGE -> ImageOps.adjustMatrix(0.04f, 0.9f, 0.7f, 0.6f).apply { postConcat(scale(1.05f, 1f, 0.85f)) }
            SEPIA -> ColorMatrix().apply { setSaturation(0f); postConcat(scale(1.07f, 0.95f, 0.75f)) }
            MONO -> ColorMatrix().apply { setSaturation(0f) }
            NOIR -> ColorMatrix().apply { setSaturation(0f); postConcat(ImageOps.adjustMatrix(-0.03f, 1.5f, 1f)) }
            DRAMA -> ImageOps.adjustMatrix(-0.04f, 1.35f, 0.85f)
            INVERT -> ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
        }
    }

    private fun scale(r: Float, g: Float, b: Float) = ColorMatrix().apply { setScale(r, g, b, 1f) }

    /** Turns every colour around the colour wheel by [degrees]. */
    fun hueMatrix(degrees: Float): ColorMatrix {
        val a = Math.toRadians(degrees.toDouble()); val c = cos(a).toFloat(); val s = sin(a).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        return ColorMatrix(floatArrayOf(
            lr + c * (1 - lr) + s * -lr, lg + c * -lg + s * -lg, lb + c * -lb + s * (1 - lb), 0f, 0f,
            lr + c * -lr + s * 0.143f, lg + c * (1 - lg) + s * 0.14f, lb + c * -lb + s * -0.283f, 0f, 0f,
            lr + c * -lr + s * -(1 - lr), lg + c * -lg + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    fun withMatrix(b: Bitmap, m: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        return out
    }

    /** Crisper edges: the picture plus [amount] of the difference from a slightly blurred copy. */
    fun sharpen(b: Bitmap, amount: Float): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        val soft = ImageOps.boxBlur(px, w, h, max(1, min(w, h) / 600))
        val out = IntArray(px.size)
        for (i in px.indices) {
            val p = px[i]; val q = soft[i]
            fun ch(shift: Int): Int { val a = (p shr shift) and 255; val bl = (q shr shift) and 255; return (a + amount * (a - bl)).toInt().coerceIn(0, 255) }
            out[i] = (p and -0x1000000) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Darker corners, drawing the eye to the middle. */
    fun vignette(b: Bitmap, strength: Float): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val cx = b.width / 2f; val cy = b.height / 2f
        val r = kotlin.math.hypot(cx, cy)
        val shade = (255 * strength.coerceIn(0f, 1f)).toInt() shl 24
        Canvas(out).drawRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), Paint().apply {
            shader = RadialGradient(cx, cy, r, intArrayOf(0, 0, shade), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        return out
    }

    /** Film grain: a little random light and dark in every pixel. [seed] keeps a preview and the result the same. */
    fun grain(b: Bitmap, amount: Float, seed: Int = 7): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        val rnd = Random(seed); val k = (amount.coerceIn(0f, 1f) * 60).toInt()
        if (k == 0) return b.copy(Bitmap.Config.ARGB_8888, false)
        for (i in px.indices) {
            val n = rnd.nextInt(-k, k + 1); val p = px[i]
            val r = (((p shr 16) and 255) + n).coerceIn(0, 255); val g = (((p shr 8) and 255) + n).coerceIn(0, 255); val bl = ((p and 255) + n).coerceIn(0, 255)
            px[i] = (p and -0x1000000) or (r shl 16) or (g shl 8) or bl
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------ shapes

    enum class Mask(val label: String) { CIRCLE("Circle"), ROUNDED("Rounded"), SQUIRCLE("Squircle"), HEART("Heart"), STAR("Star"), HEXAGON("Hexagon") }

    /** The middle of the picture cut to [mask]; outside it is see-through (save as PNG or WebP to keep that). */
    fun cutToShape(b: Bitmap, mask: Mask): Bitmap {
        val side = min(b.width, b.height)
        val square = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawPath(maskPath(mask, side.toFloat()), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(square, 0f, 0f, paint)
        return out
    }

    fun maskPath(mask: Mask, s: Float): Path = Path().apply {
        val h = s / 2
        when (mask) {
            Mask.CIRCLE -> addCircle(h, h, h, Path.Direction.CW)
            Mask.ROUNDED -> addRoundRect(0f, 0f, s, s, s * 0.18f, s * 0.18f, Path.Direction.CW)
            Mask.SQUIRCLE -> {
                // A superellipse, |x|^4 + |y|^4 = 1: softer than a rounded square.
                for (i in 0..360) {
                    val t = Math.toRadians(i.toDouble())
                    val ct = cos(t); val st = sin(t)
                    val x = h + h * Math.signum(ct) * Math.pow(kotlin.math.abs(ct), 0.5)
                    val y = h + h * Math.signum(st) * Math.pow(kotlin.math.abs(st), 0.5)
                    if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                }
                close()
            }
            Mask.HEART -> {
                moveTo(h, s * 0.92f)
                cubicTo(s * -0.05f, s * 0.55f, s * 0.1f, s * 0.02f, h, s * 0.25f)
                cubicTo(s * 0.9f, s * 0.02f, s * 1.05f, s * 0.55f, h, s * 0.92f)
                close()
            }
            Mask.STAR -> polygon(this, h, 10) { i -> if (i % 2 == 0) h else h * 0.45f }
            Mask.HEXAGON -> polygon(this, h, 6) { h }
        }
    }

    private fun polygon(p: Path, c: Float, points: Int, radius: (Int) -> Float) {
        for (i in 0 until points) {
            val a = -Math.PI / 2 + i * 2 * Math.PI / points
            val x = c + radius(i) * cos(a).toFloat(); val y = c + radius(i) * sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    // ------------------------------------------------------------ frames

    /**
     * The whole picture fitted into a [ratio] (width / height) frame, the space around it filled
     * with a blurred, enlarged copy of the picture or with [color].
     */
    fun frame(b: Bitmap, ratio: Float, color: Int?): Bitmap {
        val w: Int; val h: Int
        if (b.width.toFloat() / b.height > ratio) { w = b.width; h = (b.width / ratio).toInt() } else { h = b.height; w = (b.height * ratio).toInt() }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (color != null) c.drawColor(color) else {
            // Cover the frame with a small blurred copy, scaled up (blurring small is quick and looks the same).
            val small = Bitmap.createScaledBitmap(b, max(1, b.width / 8), max(1, b.height / 8), true)
            val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
            val blurred = Bitmap.createBitmap(ImageOps.boxBlur(px, small.width, small.height, max(2, min(small.width, small.height) / 12)), small.width, small.height, Bitmap.Config.ARGB_8888)
            val scale = max(w.toFloat() / blurred.width, h.toFloat() / blurred.height)
            val dw = blurred.width * scale; val dh = blurred.height * scale
            c.drawBitmap(blurred, null, android.graphics.RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawColor(0x33000000)
        }
        c.drawBitmap(b, (w - b.width) / 2f, (h - b.height) / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    // ------------------------------------------------------------ drawing

    enum class Pen(val label: String) { PEN("Pen"), MARKER("Highlighter"), LINE("Line"), ARROW("Arrow"), BOX("Box"), OVAL("Oval"), TEXT("Text") }

    /** One mark on the picture, in image pixels. */
    data class Mark(val pen: Pen, val points: List<Pair<Float, Float>>, val color: Int, val width: Float, val text: String = "")

    fun draw(b: Bitmap, marks: List<Mark>): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        marks.forEach { drawMark(c, it) }
        return out
    }

    fun drawMark(c: Canvas, m: Mark) {
        if (m.points.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = m.color; strokeWidth = m.width; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val (x0, y0) = m.points.first(); val (x1, y1) = m.points.last()
        when (m.pen) {
            Pen.PEN, Pen.MARKER -> {
                if (m.pen == Pen.MARKER) { p.alpha = 110; p.strokeCap = Paint.Cap.SQUARE; p.strokeWidth = m.width * 2.2f }
                val path = Path().apply { moveTo(x0, y0); if (m.points.size == 1) lineTo(x0 + 0.1f, y0); m.points.drop(1).forEach { (x, y) -> lineTo(x, y) } }
                c.drawPath(path, p)
            }
            Pen.LINE -> c.drawLine(x0, y0, x1, y1, p)
            Pen.ARROW -> {
                c.drawLine(x0, y0, x1, y1, p)
                val a = kotlin.math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
                val head = m.width * 4.5f
                listOf(a + 2.6, a - 2.6).forEach { t -> c.drawLine(x1, y1, x1 + head * cos(t).toFloat(), y1 + head * sin(t).toFloat(), p) }
            }
            Pen.BOX -> c.drawRect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), p)
            Pen.OVAL -> c.drawOval(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), p)
            Pen.TEXT -> {
                p.style = Paint.Style.FILL; p.textSize = m.width * 6; p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                // A soft outline keeps text readable on any background.
                val outline = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = p.textSize / 10; color = if (luminance(m.color) > 0.5f) 0x99000000.toInt() else 0x99FFFFFF.toInt() }
                m.text.lines().forEachIndexed { i, line -> val y = y0 + i * p.textSize * 1.2f; c.drawText(line, x0, y, outline); c.drawText(line, x0, y, p) }
            }
        }
    }

    private fun luminance(c: Int) = (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f

    // ------------------------------------------------------------ watermark

    /** [text] repeated across the picture on a slant, at [opacity]. */
    fun watermark(b: Bitmap, text: String, opacity: Float, color: Int = android.graphics.Color.WHITE): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        if (text.isBlank()) return out
        val c = Canvas(out)
        val size = min(b.width, b.height) / 14f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = (255 * opacity.coerceIn(0.05f, 1f)).toInt(); textSize = size; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val shadow = Paint(p).apply { this.color = android.graphics.Color.BLACK; alpha = p.alpha / 2 }
        val stepX = p.measureText(text) + size * 2; val stepY = size * 4
        c.save(); c.rotate(-30f, b.width / 2f, b.height / 2f)
        val reach = kotlin.math.hypot(b.width.toFloat(), b.height.toFloat())
        var y = -reach / 2; var row = 0
        while (y < b.height + reach / 2) {
            var x = -reach / 2 + (if (row % 2 == 0) 0f else stepX / 2)
            while (x < b.width + reach / 2) { c.drawText(text, x + size / 20, y + size / 20, shadow); c.drawText(text, x, y, p); x += stepX }
            y += stepY; row++
        }
        c.restore()
        return out
    }

    // ------------------------------------------------------------ saving

    /**
     * Encodes to at most [maxBytes]: the highest quality that fits, and if even low quality is too
     * big, a smaller picture. Returns the bytes and the quality used.
     */
    fun encodeWithin(b: Bitmap, format: ImageOps.Format, maxBytes: Long): Pair<ByteArray, Int> {
        var pic = b
        repeat(8) {
            fun at(q: Int) = ByteArrayOutputStream().also { ImageOps.encode(pic, format, q, it) }.toByteArray()
            if (!format.lossy) { val bytes = at(100); if (bytes.size <= maxBytes) return bytes to 100 }
            else {
                var lo = 10; var hi = 95; var best: ByteArray? = null; var bestQ = 0
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val bytes = at(mid)
                    if (bytes.size <= maxBytes) { best = bytes; bestQ = mid; lo = mid + 1 } else hi = mid - 1
                }
                if (best != null) return best to bestQ
            }
            pic = Bitmap.createScaledBitmap(pic, max(1, (pic.width * 0.8f).toInt()), max(1, (pic.height * 0.8f).toInt()), true)
        }
        return ByteArrayOutputStream().also { ImageOps.encode(pic, format, 10, it) }.toByteArray() to 10
    }

    /** Pictures side by side ([across]) or one under another, each scaled to match. */
    fun stitch(pictures: List<Bitmap>, across: Boolean, gap: Int = 0, background: Int = android.graphics.Color.WHITE): Bitmap {
        require(pictures.isNotEmpty())
        val target = if (across) pictures.minOf { it.height } else pictures.minOf { it.width }
        val scaled = pictures.map { p ->
            if (across) Bitmap.createScaledBitmap(p, max(1, p.width * target / p.height), target, true)
            else Bitmap.createScaledBitmap(p, target, max(1, p.height * target / p.width), true)
        }
        val w = if (across) scaled.sumOf { it.width } + gap * (scaled.size - 1) else target
        val h = if (across) target else scaled.sumOf { it.height } + gap * (scaled.size - 1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(background)
        var at = 0
        scaled.forEach { p -> if (across) { c.drawBitmap(p, at.toFloat(), 0f, null); at += p.width + gap } else { c.drawBitmap(p, 0f, at.toFloat(), null); at += p.height + gap } }
        return out
    }
}

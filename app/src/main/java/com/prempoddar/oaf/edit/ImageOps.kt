package com.prempoddar.oaf.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import java.io.OutputStream

/** Pixel operations for the image editor. Each returns a new bitmap and leaves the input alone. */
object ImageOps {
    enum class Format(val label: String, val ext: String, val lossy: Boolean) {
        JPEG("JPEG", "jpg", true), PNG("PNG", "png", false), WEBP("WebP", "webp", true)
    }

    fun crop(b: Bitmap, r: Rect): Bitmap {
        val x = r.left.toInt().coerceIn(0, b.width - 1)
        val y = r.top.toInt().coerceIn(0, b.height - 1)
        val w = r.width.toInt().coerceIn(1, b.width - x)
        val h = r.height.toInt().coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    fun rotate(b: Bitmap, degrees: Float): Bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(degrees) }, true)

    fun flip(b: Bitmap, horizontal: Boolean): Bitmap =
        Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f) }, true)

    fun resize(b: Bitmap, w: Int, h: Int): Bitmap = Bitmap.createScaledBitmap(b, w.coerceAtLeast(1), h.coerceAtLeast(1), true)

    enum class Hide { BLUR, PIXELATE, BLACK }

    /** Hides a box: blurred, in big pixels, or covered. [strength] 0..1 sets how much. */
    fun hide(b: Bitmap, r: Rect, how: Hide, strength: Float = 0.6f): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val area = EditGeometry.round(r).apply { intersect(0, 0, b.width, b.height) }
        if (area.width() < 2 || area.height() < 2) return out
        Canvas(out).drawBitmap(effect(b, area, how, strength), area.left.toFloat(), area.top.toFloat(), null)
        return out
    }

    /**
     * Hides what a brush went over: [points] in image pixels, [brush] wide. The effect is worked
     * out for the stroke's bounds once, then painted through the stroke's shape.
     */
    fun hideStroke(b: Bitmap, points: List<Pair<Float, Float>>, brush: Float, how: Hide, strength: Float = 0.6f): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        if (points.isEmpty()) return out
        val half = brush / 2 + 2
        val area = android.graphics.Rect(
            (points.minOf { it.first } - half).toInt(), (points.minOf { it.second } - half).toInt(),
            (points.maxOf { it.first } + half).toInt() + 1, (points.maxOf { it.second } + half).toInt() + 1,
        ).apply { intersect(0, 0, b.width, b.height) }
        if (area.width() < 2 || area.height() < 2) return out
        val fx = effect(b, area, how, strength)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = brush; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            shader = android.graphics.BitmapShader(fx, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(area.left.toFloat(), area.top.toFloat()) })
            }
        }
        val path = android.graphics.Path().apply {
            moveTo(points[0].first, points[0].second)
            if (points.size == 1) lineTo(points[0].first + 0.1f, points[0].second)
            for (i in 1 until points.size) lineTo(points[i].first, points[i].second)
        }
        Canvas(out).drawPath(path, paint)
        return out
    }

    /** The hidden look of one area of [b], the area's size. */
    private fun effect(b: Bitmap, area: android.graphics.Rect, how: Hide, strength: Float): Bitmap {
        val side = minOf(b.width, b.height).toFloat()
        val piece = Bitmap.createBitmap(b, area.left, area.top, area.width(), area.height())
        return when (how) {
            Hide.BLACK -> Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
            Hide.PIXELATE -> {
                val block = (side * (0.008f + 0.05f * strength)).toInt().coerceAtLeast(3)
                val small = Bitmap.createScaledBitmap(piece, (area.width() / block).coerceAtLeast(1), (area.height() / block).coerceAtLeast(1), true)
                Bitmap.createScaledBitmap(small, area.width(), area.height(), false)
            }
            Hide.BLUR -> blur(b, area, (side * (0.004f + 0.04f * strength)).coerceAtLeast(2f))
        }
    }

    /**
     * A smooth blur of [area] with about [radius] pixels of spread. The area is read with a margin
     * so its edges blur into their surroundings, and big blurs are done on a smaller copy (which
     * looks the same) so they stay quick.
     */
    fun blur(b: Bitmap, area: android.graphics.Rect, radius: Float): Bitmap {
        val pad = (radius * 2).toInt()
        val outer = android.graphics.Rect(area.left - pad, area.top - pad, area.right + pad, area.bottom + pad).apply { intersect(0, 0, b.width, b.height) }
        val shrink = (radius / 6f).coerceAtLeast(1f)
        val w = (outer.width() / shrink).toInt().coerceAtLeast(1); val h = (outer.height() / shrink).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(Bitmap.createBitmap(b, outer.left, outer.top, outer.width(), outer.height()), w, h, true)
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        val done = Bitmap.createBitmap(boxBlur(px, w, h, (radius / shrink / 2).toInt().coerceAtLeast(1)), w, h, Bitmap.Config.ARGB_8888)
        val big = Bitmap.createScaledBitmap(done, outer.width(), outer.height(), true)
        return Bitmap.createBitmap(big, area.left - outer.left, area.top - outer.top, area.width(), area.height())
    }

    /** Three box blurs in a row, close to a Gaussian, on packed ARGB pixels; edges repeat. */
    fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val a = px.copyOf()
        if (radius < 1) return a
        val b = IntArray(px.size)
        repeat(3) {
            pass(a, b, w, h, radius, horizontal = true)
            pass(b, a, w, h, radius, horizontal = false)
        }
        return a
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val step = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = src[base + i.coerceIn(0, len - 1) * step]
                sa += p ushr 24; sr += (p shr 16) and 255; sg += (p shr 8) and 255; sb += p and 255
            }
            for (i in 0 until len) {
                dst[base + i * step] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val o = src[base + (i - r).coerceIn(0, len - 1) * step]
                val n = src[base + (i + r + 1).coerceIn(0, len - 1) * step]
                sa += (n ushr 24) - (o ushr 24)
                sr += ((n shr 16) and 255) - ((o shr 16) and 255)
                sg += ((n shr 8) and 255) - ((o shr 8) and 255)
                sb += (n and 255) - (o and 255)
            }
        }
    }

    /** Brightness -1..1, contrast 0..2 (1 = unchanged), saturation 0..2 (1 = unchanged), warmth -1..1. */
    fun adjustMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float = 0f): ColorMatrix {
        val m = ColorMatrix().apply { setSaturation(saturation) }
        val t = (1 - contrast) * 128 + brightness * 255
        m.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        // Warmer adds red and takes away blue; cooler the opposite.
        m.postConcat(ColorMatrix(floatArrayOf(1f + 0.15f * warmth, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - 0.15f * warmth, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        return m
    }

    fun adjust(b: Bitmap, brightness: Float, contrast: Float, saturation: Float, warmth: Float = 0f): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(adjustMatrix(brightness, contrast, saturation, warmth)) })
        return out
    }

    fun encode(b: Bitmap, format: Format, quality: Int, out: OutputStream) {
        val f = when (format) {
            Format.JPEG -> Bitmap.CompressFormat.JPEG
            Format.PNG -> Bitmap.CompressFormat.PNG
            Format.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }
        // JPEG has no transparency: see-through parts become white, not black.
        val flat = if (format == Format.JPEG && b.hasAlpha()) Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        } else b
        flat.compress(f, quality, out)
    }
}

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

    /** Hides an area: a strong blur, big pixels, or a solid box for things that must not be read. */
    fun hide(b: Bitmap, r: Rect, how: Hide): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val area = EditGeometry.round(r).apply { intersect(0, 0, b.width, b.height) }
        if (area.width() < 2 || area.height() < 2) return out
        val canvas = Canvas(out)
        when (how) {
            Hide.BLACK -> canvas.drawRect(area, Paint().apply { color = android.graphics.Color.BLACK })
            Hide.PIXELATE -> {
                val piece = Bitmap.createBitmap(b, area.left, area.top, area.width(), area.height())
                val block = (maxOf(area.width(), area.height()) / 16).coerceAtLeast(8)
                val small = Bitmap.createScaledBitmap(piece, (area.width() / block).coerceAtLeast(1), (area.height() / block).coerceAtLeast(1), false)
                canvas.drawBitmap(Bitmap.createScaledBitmap(small, area.width(), area.height(), false), area.left.toFloat(), area.top.toFloat(), null)
            }
            Hide.BLUR -> {
                // Shrinking and growing again a few times blurs smoothly and fast, with no extra library.
                var piece = Bitmap.createBitmap(b, area.left, area.top, area.width(), area.height())
                repeat(3) {
                    val small = Bitmap.createScaledBitmap(piece, (piece.width / 10).coerceAtLeast(1), (piece.height / 10).coerceAtLeast(1), true)
                    piece = Bitmap.createScaledBitmap(small, area.width(), area.height(), true)
                }
                canvas.drawBitmap(piece, area.left.toFloat(), area.top.toFloat(), null)
            }
        }
        return out
    }

    /** Brightness -1..1, contrast 0..2 (1 = unchanged), saturation 0..2 (1 = unchanged). */
    fun adjustMatrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
        val m = ColorMatrix().apply { setSaturation(saturation) }
        val t = (1 - contrast) * 128 + brightness * 255
        m.postConcat(ColorMatrix(floatArrayOf(contrast, 0f, 0f, 0f, t, 0f, contrast, 0f, 0f, t, 0f, 0f, contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        return m
    }

    fun adjust(b: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(adjustMatrix(brightness, contrast, saturation)) })
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

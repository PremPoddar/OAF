package com.prempoddar.oaf.edit

import kotlin.math.roundToInt

/** A rectangle in floating point, left/top inclusive, right/bottom exclusive. */
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** A crop in OpenGL's normalised device coordinates, -1..1 on each axis (y up). */
data class NdcCrop(val left: Float, val right: Float, val bottom: Float, val top: Float)

/** Arithmetic shared by the image and video editors. */
object EditGeometry {
    /** Where an image of [w]×[h] is drawn when fitted inside a box, centred. */
    fun fit(w: Int, h: Int, boxW: Float, boxH: Float): Rect {
        val scale = minOf(boxW / w, boxH / h)
        val dw = w * scale; val dh = h * scale
        val x = (boxW - dw) / 2; val y = (boxH - dh) / 2
        return Rect(x, y, x + dw, y + dh)
    }

    /** A point on screen as a pixel in the image, clamped to the image. */
    fun toImage(shown: Rect, w: Int, h: Int, x: Float, y: Float): Pair<Float, Float> {
        val ix = ((x - shown.left) / shown.width * w).coerceIn(0f, w.toFloat())
        val iy = ((y - shown.top) / shown.height * h).coerceIn(0f, h.toFloat())
        return ix to iy
    }

    /** The largest centred rectangle of [aspect] (width / height) inside a [w]×[h] image. */
    fun aspectCrop(w: Int, h: Int, aspect: Float): Rect {
        val current = w.toFloat() / h
        return if (current > aspect) {
            val cw = h * aspect
            Rect((w - cw) / 2, 0f, (w + cw) / 2, h.toFloat())
        } else {
            val ch = w / aspect
            Rect(0f, (h - ch) / 2, w.toFloat(), (h + ch) / 2)
        }
    }

    fun ndcCrop(w: Int, h: Int, aspect: Float): NdcCrop {
        val current = w.toFloat() / h
        return if (current > aspect) {
            val f = aspect / current
            NdcCrop(-f, f, -1f, 1f)
        } else {
            val f = current / aspect
            NdcCrop(-1f, 1f, -f, f)
        }
    }

    /** New size for a resize where one side is given and the other follows the aspect. */
    fun scaledTo(w: Int, h: Int, width: Int? = null, height: Int? = null): Pair<Int, Int> = when {
        width != null -> width to (h.toLong() * width / w).toInt().coerceAtLeast(1)
        height != null -> (w.toLong() * height / h).toInt().coerceAtLeast(1) to height
        else -> w to h
    }

    fun editedName(original: String, ext: String): String {
        val stem = if (original.lastIndexOf('.') > 0) original.substring(0, original.lastIndexOf('.')) else original
        return "$stem (edited).$ext"
    }

    fun round(r: Rect) = android.graphics.Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
}

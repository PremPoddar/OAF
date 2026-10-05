package com.prempoddar.oaf.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlin.math.max
import kotlin.math.min

/** A rectangle on the page as shown, from 0 to 1 across and down (origin top-left). */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val width get() = r - l
    val height get() = b - t
    val cx get() = (l + r) / 2
    val cy get() = (t + b) / 2
    fun contains(x: Float, y: Float, slop: Float = 0f) = x >= l - slop && x <= r + slop && y >= t - slop && y <= b + slop
    fun union(o: Box) = Box(min(l, o.l), min(t, o.t), max(r, o.r), max(b, o.b))
    fun intersects(o: Box) = l < o.r && o.l < r && t < o.b && o.t < b

    companion object {
        fun of(x1: Float, y1: Float, x2: Float, y2: Float) = Box(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }
}

/**
 * Converts between PDF user space and the page as displayed (crop box, rotation applied),
 * normalised to 0–1 so it doesn't matter what size the page is drawn at.
 */
class PageGeom(page: PDPage) {
    private val crop = page.cropBox
    private val llx = crop.lowerLeftX
    private val ury = crop.upperRightY
    private val w = crop.width
    private val h = crop.height
    val rotation = ((page.rotation % 360) + 360) % 360

    /** Size of the page as shown, in points. */
    val displayWidth get() = if (rotation == 90 || rotation == 270) h else w
    val displayHeight get() = if (rotation == 90 || rotation == 270) w else h

    /** User space → normalised display. */
    fun toDisplay(ux: Float, uy: Float): Pair<Float, Float> {
        val px = ux - llx; val py = ury - uy
        val (dx, dy) = when (rotation) {
            90 -> (h - py) to px
            180 -> (w - px) to (h - py)
            270 -> py to (w - px)
            else -> px to py
        }
        return dx / displayWidth to dy / displayHeight
    }

    /** Normalised display → user space. */
    fun toUser(nx: Float, ny: Float): Pair<Float, Float> {
        val dx = nx * displayWidth; val dy = ny * displayHeight
        val (px, py) = when (rotation) {
            90 -> dy to (h - dx)
            180 -> (w - dx) to (h - dy)
            270 -> (w - dy) to dx
            else -> dx to dy
        }
        return (px + llx) to (ury - py)
    }

    /** A user-space rectangle as a display box. */
    fun boxOf(x1: Float, y1: Float, x2: Float, y2: Float): Box {
        val a = toDisplay(x1, y1); val b = toDisplay(x2, y2)
        return Box.of(a.first, a.second, b.first, b.second)
    }

    /** A display box as a user-space rectangle: llx, lly, urx, ury. */
    fun userRect(b: Box): FloatArray {
        val p = toUser(b.l, b.t); val q = toUser(b.r, b.b)
        return floatArrayOf(min(p.first, q.first), min(p.second, q.second), max(p.first, q.first), max(p.second, q.second))
    }

    /** Points per normalised unit across the display, for sizes given in points. */
    fun pointsAcross(n: Float) = n * displayWidth
}

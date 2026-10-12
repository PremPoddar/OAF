package com.prempoddar.oaf.ui.viewers.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

/** Something that can draw a PDF's pages: MuPDF, or Android's own renderer if MuPDF can't. */
private interface PageDrawer : Closeable {
    val count: Int
    /** Page [index]'s size in points, as shown (turned pages already turned). */
    fun size(index: Int): Pair<Float, Float>
    /** Draws the part of the page from [l],[t] to [r],[b] (0–1 of the page) to fill [bitmap]. */
    fun draw(index: Int, bitmap: Bitmap, l: Float, t: Float, r: Float, b: Float)
}

/** MuPDF: faster and more faithful than the platform renderer, with fonts and shading done right. */
private class MuPdfDrawer(file: File) : PageDrawer {
    private val doc = com.artifex.mupdf.fitz.Document.openDocument(file.path)
    override val count = doc.countPages()

    override fun size(index: Int): Pair<Float, Float> {
        val page = doc.loadPage(index)
        try {
            val b = page.bounds
            return (b.x1 - b.x0) to (b.y1 - b.y0)
        } finally {
            page.destroy()
        }
    }

    override fun draw(index: Int, bitmap: Bitmap, l: Float, t: Float, r: Float, b: Float) {
        val page = doc.loadPage(index)
        try {
            val bounds = page.bounds
            val w = bounds.x1 - bounds.x0; val h = bounds.y1 - bounds.y0
            val sx = bitmap.width / ((r - l) * w); val sy = bitmap.height / ((b - t) * h)
            val ctm = com.artifex.mupdf.fitz.Matrix(sx, 0f, 0f, sy, -(bounds.x0 + l * w) * sx, -(bounds.y0 + t * h) * sy)
            val device = com.artifex.mupdf.fitz.android.AndroidDrawDevice(bitmap, 0, 0)
            try {
                page.run(device, ctm, null)
                device.close()
            } finally {
                device.destroy()
            }
        } finally {
            page.destroy()
        }
    }

    override fun close() = doc.destroy()
}

private class PlatformDrawer(file: File) : PageDrawer {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
    override val count = renderer.pageCount

    override fun size(index: Int) = renderer.openPage(index).use { it.width.toFloat() to it.height.toFloat() }

    override fun draw(index: Int, bitmap: Bitmap, l: Float, t: Float, r: Float, b: Float) {
        renderer.openPage(index).use { page ->
            val sx = bitmap.width / ((r - l) * page.width); val sy = bitmap.height / ((b - t) * page.height)
            val m = Matrix().apply { setScale(sx, sy); postTranslate(-l * page.width * sx, -t * page.height * sy) }
            page.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    override fun close() {
        renderer.close(); fd.close()
    }
}

/**
 * Draws pages with MuPDF (or the platform renderer if MuPDF can't read the file), one at a time
 * on its own thread, since neither may be used from two at once, keeping recent bitmaps within a
 * slice of the app's memory.
 */
internal class RenderHost : Closeable {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "pdf-render") }
    private val dispatcher = thread.asCoroutineDispatcher()
    private var drawer: PageDrawer? = null
    private var generation = 0

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun open(file: File) = withContext(dispatcher) {
        drawer?.close()
        drawer = null
        drawer = runCatching<PageDrawer> { MuPdfDrawer(file) }.getOrElse { PlatformDrawer(file) }
        generation++
        cache.evictAll()
    }

    /** Page [index] [width] pixels wide. Widths are rounded so nearby zoom levels share bitmaps. */
    suspend fun render(index: Int, width: Int): Bitmap? = withContext(dispatcher) {
        val d = drawer ?: return@withContext null
        if (index !in 0 until d.count) return@withContext null
        val w = ((width + 127) / 128 * 128).coerceIn(128, 4096)
        val key = "$generation:$index:$w"
        cache.get(key)?.let { return@withContext it }
        val (pw, ph) = d.size(index)
        val h = (w * ph / pw).toInt().coerceAtLeast(1)
        val bitmap = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrElse {
            cache.evictAll()
            Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
        }
        bitmap.eraseColor(Color.WHITE)
        d.draw(index, bitmap, 0f, 0f, 1f, 1f)
        cache.put(key, bitmap)
        bitmap
    }

    /** A sharp piece of a page for deep zoom: [l]..[b] in 0–1 page coordinates, drawn [width] by [height] pixels. */
    suspend fun renderRegion(index: Int, l: Float, t: Float, r: Float, b: Float, width: Int, height: Int): Bitmap? = withContext(dispatcher) {
        val d = drawer ?: return@withContext null
        if (index !in 0 until d.count || width <= 0 || height <= 0) return@withContext null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        d.draw(index, bitmap, l, t, r, b)
        bitmap
    }

    override fun close() {
        thread.execute { drawer?.close(); drawer = null }
        thread.shutdown()
        cache.evictAll()
    }
}

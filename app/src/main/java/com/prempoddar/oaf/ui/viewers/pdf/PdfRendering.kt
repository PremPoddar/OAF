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

/**
 * Draws pages with the platform renderer, one at a time on its own thread (the renderer allows
 * no more), keeping recent bitmaps within a slice of the app's memory.
 */
internal class RenderHost : Closeable {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "pdf-render") }
    private val dispatcher = thread.asCoroutineDispatcher()
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var generation = 0

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun open(file: File) = withContext(dispatcher) {
        renderer?.close(); fd?.close()
        fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fd!!)
        generation++
        cache.evictAll()
    }

    /** Page [index] [width] pixels wide. Widths are rounded so nearby zoom levels share bitmaps. */
    suspend fun render(index: Int, width: Int): Bitmap? = withContext(dispatcher) {
        val r = renderer ?: return@withContext null
        if (index !in 0 until r.pageCount) return@withContext null
        val w = ((width + 127) / 128 * 128).coerceIn(128, 4096)
        val key = "$generation:$index:$w"
        cache.get(key)?.let { return@withContext it }
        r.openPage(index).use { page ->
            val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrElse {
                cache.evictAll()
                Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
            }
            bitmap.eraseColor(Color.WHITE)
            val m = Matrix().apply { setScale(bitmap.width.toFloat() / page.width, bitmap.height.toFloat() / page.height) }
            page.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache.put(key, bitmap)
            bitmap
        }
    }

    /** A sharp piece of a page for deep zoom: [region] is in 0–1 page coordinates, drawn [width] by [height] pixels. */
    suspend fun renderRegion(index: Int, l: Float, t: Float, r: Float, b: Float, width: Int, height: Int): Bitmap? = withContext(dispatcher) {
        val rend = renderer ?: return@withContext null
        if (index !in 0 until rend.pageCount || width <= 0 || height <= 0) return@withContext null
        rend.openPage(index).use { page ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val sx = width / ((r - l) * page.width); val sy = height / ((b - t) * page.height)
            val m = Matrix().apply { setScale(sx, sy); postTranslate(-l * page.width * sx, -t * page.height * sy) }
            page.render(bitmap, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() {
        thread.execute { renderer?.close(); fd?.close(); renderer = null; fd = null }
        thread.shutdown()
        cache.evictAll()
    }
}

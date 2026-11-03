package com.prempoddar.oaf.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import com.prempoddar.oaf.io.DocSource

/**
 * Decodes a picture at most [max] pixels across, trying each of Android's decoders in turn: some
 * phones' photos (HEIC saved with a .jpg name, HDR photos with a gain map, odd colour profiles)
 * fail in one and decode fine in another.
 */
object Pictures {
    fun decode(context: Context, source: DocSource, max: Int, software: Boolean = false): Bitmap {
        val errors = ArrayList<Throwable>()
        fun <T> attempt(f: () -> T?): T? = runCatching(f).onFailure { errors += it }.getOrNull()
        fun src() = source.file?.let { ImageDecoder.createSource(it) } ?: ImageDecoder.createSource(context.contentResolver, source.uri)
        fun fit(d: ImageDecoder, info: ImageDecoder.ImageInfo) {
            val scale = maxOf(info.size.width, info.size.height).toFloat() / max
            if (scale > 1) d.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1), (info.size.height / scale).toInt().coerceAtLeast(1))
        }
        if (!software) attempt { ImageDecoder.decodeBitmap(src()) { d, info, _ -> fit(d, info) } }?.let { return it }
        attempt { ImageDecoder.decodeBitmap(src()) { d, info, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; fit(d, info) } }?.let { return it }
        attempt { factory(context, source, max) }?.let { return it }
        attempt { heif(context, source, max) }?.let { return it }
        throw errors.firstOrNull() ?: IllegalStateException("This image couldn't be decoded")
    }

    private fun factory(context: Context, source: DocSource, max: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.open(context).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        return source.open(context).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    /** HEIF's main picture, read the way the media framework reads it (as it does for galleries). */
    private fun heif(context: Context, source: DocSource, max: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            source.file?.let { r.setDataSource(it.path) } ?: r.setDataSource(context, source.uri)
            val b = r.primaryImage ?: return null
            val scale = maxOf(b.width, b.height).toFloat() / max
            return if (scale > 1) Bitmap.createScaledBitmap(b, (b.width / scale).toInt(), (b.height / scale).toInt(), true) else b
        } finally {
            r.release()
        }
    }
}

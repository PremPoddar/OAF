package com.prempoddar.oaf.edit

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Picture tools that need a trained model: Google's ML Kit, through Play services, so the model
 * comes with the phone's Google apps (fetched once, the first time) rather than inside OAF. The
 * pictures themselves are worked on on the phone and never sent anywhere.
 */
object Smart {
    /** The main subject (people, pets, things) on a see-through background. */
    suspend fun removeBackground(b: Bitmap): Bitmap {
        val client = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        try {
            return client.process(InputImage.fromBitmap(b, 0)).await().foregroundBitmap
                ?: throw IllegalStateException("No subject was found in this picture")
        } finally { client.close() }
    }

    /** Text in the picture (Latin alphabets), line by line. */
    suspend fun readText(b: Bitmap): String {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return client.process(InputImage.fromBitmap(b, 0)).await().text
        } finally { client.close() }
    }

    /** What went wrong, in words; Play services' own messages are codes. */
    fun explain(t: Throwable): String = when {
        (t.message ?: "").contains("download", true) || (t.message ?: "").contains("waiting", true) ->
            "The tool is still being fetched by Google Play services. Try again in a minute, on Wi-Fi."
        (t.message ?: "").contains("No subject") -> "Couldn't find a clear subject in this picture."
        else -> t.message ?: "This needs Google Play services, which isn't available."
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }
}

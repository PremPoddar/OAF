package com.prempoddar.oaf.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.compose.runtime.mutableStateOf

/** Picture-in-picture for videos: whether it's on, and how to enter it when the user leaves. */
object Pip {
    val active = mutableStateOf(false)

    /** Set while a video plays; the activity calls it when the user heads home. */
    var onLeave: (() -> Unit)? = null

    fun enter(activity: Activity, width: Int, height: Int) {
        val ratio = if (width > 0 && height > 0) Rational(width, height).let {
            // Android refuses ratios beyond 2.39:1 either way.
            val r = width.toFloat() / height
            when {
                r > 2.39f -> Rational(239, 100)
                r < 1 / 2.39f -> Rational(100, 239)
                else -> it
            }
        } else Rational(16, 9)
        runCatching { activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }
    }
}

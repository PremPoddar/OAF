package com.prempoddar.oaf.pick

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.OafNav
import com.prempoddar.oaf.data.OafSettings
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.OafTheme
import com.prempoddar.oaf.ui.design.Haptics
import java.io.File

/**
 * Another app asked for a file (Android's "Open from" list): which kinds it takes, whether it
 * takes several, and how to hand them over or give up.
 */
class PickRequest(
    val mimes: List<String>,
    val multiple: Boolean,
    /** The asking app's name, for "Choose a picture for WhatsApp". */
    val caller: String?,
    private val deliver: (List<File>) -> Unit,
    val cancel: () -> Unit,
) {
    private val anything get() = mimes.isEmpty() || mimes.any { it == "*/*" }

    /** Files are being selected (for sending several), so the banner gives way to the selection bar. */
    var selecting by mutableStateOf(false)

    fun accepts(file: File): Boolean = anything || accepts(mimeOf(file))

    fun accepts(mime: String?): Boolean = anything || (mime != null && mimes.any { want ->
        want.equals(mime, true) || (want.endsWith("/*") && mime.startsWith(want.removeSuffix("*"), true))
    })

    /** What's wanted, in words: "a picture", "videos", "a file". */
    val what: String get() {
        val families = mimes.map { it.substringBefore('/') }.toSet()
        val noun = when {
            anything -> "file"
            families == setOf("image") -> "picture"
            families == setOf("video") -> "video"
            families == setOf("audio") -> "sound or song"
            families.all { it == "image" || it == "video" } -> "photo or video"
            mimes.all { it == "application/pdf" } -> "PDF"
            else -> "file"
        }
        return if (multiple) "${noun}s" else if (noun.first() in "aeiouAEIOU") "an $noun" else "a $noun"
    }

    /** Where to begin: the photos for a photo, the videos for a video, else the home screen. */
    val startRoute: String get() {
        val families = mimes.map { it.substringBefore('/') }.toSet()
        return when {
            anything -> "browser"
            families == setOf("image") -> "category/IMAGES"
            families == setOf("video") -> "category/VIDEOS"
            families == setOf("audio") -> "category/AUDIO"
            else -> "browser"
        }
    }

    /** A file the user tapped: sent back if it's a kind the app takes. */
    fun offer(context: Context, source: DocSource) {
        val file = source.file ?: run { Toast.makeText(context, "Choose a file stored on this phone", Toast.LENGTH_SHORT).show(); return }
        if (!accepts(file)) {
            Haptics.reject()
            Toast.makeText(context, "${caller ?: "This app"} wants $what", Toast.LENGTH_SHORT).show()
            return
        }
        send(listOf(file))
    }

    fun send(files: List<File>) {
        val ok = files.filter { it.isFile && accepts(it) }
        if (ok.isNotEmpty()) { Haptics.confirm(); deliver(if (multiple) ok else ok.take(1)) }
    }

    companion object {
        fun mimeOf(f: File): String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
    }
}

val LocalPicker = staticCompositionLocalOf<PickRequest?> { null }

/**
 * OAF as a file picker for other apps: their "Choose a file" can open it, browse as usual, and a
 * tap on a file sends that file back, shared for reading only.
 */
class PickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Haptics.attach(window.decorView)
        val mimes = (intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList().orEmpty() + listOfNotNull(intent.type)).distinct()
        val caller = callingPackage?.let { p -> runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString() }.getOrNull() }
        val request = PickRequest(
            mimes, intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false), caller,
            deliver = { files -> setResult(Activity.RESULT_OK, resultFor(files)); finish() },
            cancel = { setResult(Activity.RESULT_CANCELED); finish() },
        )
        val incoming = mutableStateOf<DocSource?>(null)
        setContent {
            val settings: OafSettings? by AppGraph.prefs.settings.collectAsStateWithLifecycle(null)
            val s = settings ?: return@setContent
            OafTheme(s.appearance) {
                androidx.compose.runtime.CompositionLocalProvider(LocalPicker provides request) {
                    OafNav(s, incoming, openedFromOutside = false, finish = ::finish, picker = request)
                }
            }
        }
    }

    private fun resultFor(files: List<File>): Intent {
        val uris = files.map { FileProvider.getUriForFile(this, "$packageName.files", it) }
        return Intent().apply {
            data = uris.first()
            type = PickRequest.mimeOf(files.first()) ?: "application/octet-stream"
            if (uris.size > 1 || intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)) {
                clipData = ClipData.newUri(contentResolver, files.first().name, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

/** Along the bottom while picking: what's wanted and for which app, and a way out. */
@androidx.compose.runtime.Composable
fun PickBanner(p: PickRequest, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val s = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        color = s.primaryContainer, contentColor = s.onPrimaryContainer, shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp, modifier = modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, null)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Choose ${p.what}", style = MaterialTheme.typography.titleMedium)
                Text(
                    (p.caller?.let { "For $it" } ?: "For the app that asked") + if (p.multiple) " · long-press to choose several" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { Haptics.tap(); p.cancel() }) { Text("Cancel") }
        }
    }
}

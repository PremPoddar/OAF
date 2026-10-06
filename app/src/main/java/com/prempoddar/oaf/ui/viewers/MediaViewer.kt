package com.prempoddar.oaf.ui.viewers

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.rememberChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Audio and video, with the platform's controls. */
@OptIn(UnstableApi::class)
@Composable
fun MediaViewer(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val audio = source.kind == FileKind.AUDIO
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(source.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val meta by produceState<Triple<String?, String?, ImageBitmap?>>(Triple(null, null, null), source) {
        if (!audio) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, source.uri)
                    Triple(
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                        r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() },
                    )
                } finally { r.release() }
            }.getOrDefault(Triple(null, null, null))
        }
    }
    ViewerScaffold(
        title = source.name, chrome = rememberChrome(), autoHide = !audio, onBack = onBack,
        actions = { IconButton(onClick = { openElsewhere(context, source) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open with") } },
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (audio) {
                Box(Modifier.padding(top = 120.dp).size(240.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    meta.third?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        ?: Icon(Icons.Rounded.MusicNote, null, tint = paper.muted, modifier = Modifier.size(72.dp))
                }
                Text(meta.first ?: source.name.substringBeforeLast('.'), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp))
                meta.second?.let { Text(it, color = paper.muted) }
            }
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; controllerShowTimeoutMs = if (audio) 0 else 3_000; controllerHideOnTouch = !audio } },
                modifier = if (audio) Modifier.fillMaxWidth().padding(top = 24.dp).height(160.dp) else Modifier.fillMaxSize(),
            )
        }
    }
}

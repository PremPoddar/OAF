package com.prempoddar.oaf.ui.edit

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.compose.foundation.horizontalScroll
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.edit.EditGeometry
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.media.formatTime
import com.prempoddar.oaf.ui.design.ChoiceChips
import com.prempoddar.oaf.ui.design.SectionLabel
import com.prempoddar.oaf.ui.design.SwitchRow
import kotlinx.coroutines.delay
import java.io.File

/** What a media file can be saved as. */
enum class MediaOutput(val label: String, val ext: String) {
    VIDEO("MP4", "mp4"),
    /** HEVC: about half the size at the same quality; plays on phones and computers from recent years. */
    VIDEO_SMALL("MP4, smaller (HEVC)", "mp4"),
    AUDIO("Audio only (M4A)", "m4a"),
    /** A song or recording with a picture, for sharing where only videos can go. */
    PICTURE_VIDEO("Video with a picture", "mp4"),
}

/**
 * Trims video and audio, and for videos crops to a shape, turns, shrinks, removes the sound or
 * keeps only the sound. Saves an edited copy; the original is never changed.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun MediaEditor(source: DocSource, onClose: () -> Unit, start: MediaOutput? = null) {
    val context = LocalContext.current
    val isVideo = source.kind == FileKind.VIDEO
    val wide = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val player = remember { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(source.uri)); prepare() } }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(0f..1f) }
    var videoW by remember { mutableStateOf(0) }
    var videoH by remember { mutableStateOf(0) }
    var aspect by remember { mutableFloatStateOf(0f) } // 0 = keep
    var rotation by remember { mutableStateOf(0) }
    var height by remember { mutableStateOf(0) } // 0 = original
    var mute by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf(start ?: if (isVideo) MediaOutput.VIDEO else MediaOutput.AUDIO) }
    // For a video from audio: the song's own cover unless another picture is chosen.
    var picture by remember { mutableStateOf<Uri?>(null) }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> picture = u } }

    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var saved by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }
    var outFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                playing = p.isPlaying
                if (p.duration != C.TIME_UNSET) duration = p.duration
                videoW = p.videoSize.width; videoH = p.videoSize.height
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    // Plays only inside the chosen range.
    LaunchedEffect(playing) {
        while (true) {
            position = player.currentPosition
            if (duration > 0 && playing && position >= (range.endInclusive * duration).toLong()) {
                player.pause(); player.seekTo((range.start * duration).toLong())
            }
            delay(100)
        }
    }
    val startMs = (range.start * duration).toLong()
    val endMs = (range.endInclusive * duration).toLong()

    fun export() {
        val out = outputFor(source, output.ext).also { outFile = it }
        val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).apply { if (endMs < duration) setEndPositionMs(endMs) }.build()
        val item = MediaItem.Builder().setUri(source.uri).setClippingConfiguration(clip).build()
        val effects = buildList {
            if (aspect > 0 && videoW > 0) EditGeometry.ndcCrop(videoW, videoH, aspect).let { add(Crop(it.left, it.right, it.bottom, it.top)) }
            if (rotation != 0) add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-rotation.toFloat()).build())
            if (height > 0) add(Presentation.createForHeight(height))
        }
        val video = output == MediaOutput.VIDEO || output == MediaOutput.VIDEO_SMALL
        val edited = EditedMediaItem.Builder(item)
            .setRemoveAudio(video && mute)
            .setRemoveVideo(output == MediaOutput.AUDIO || (!isVideo && output == MediaOutput.PICTURE_VIDEO))
            .setEffects(Effects(emptyList(), if (video) effects else emptyList()))
            .build()
        val composition = if (output == MediaOutput.PICTURE_VIDEO) {
            // The sound as it is, and the picture repeated for as long as the sound lasts.
            val still = MediaItem.Builder().setUri(picture ?: coverPicture(context, source)).setImageDurationMs(1_000).build()
            val frame = EditedMediaItem.Builder(still).setFrameRate(10)
                .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(1280, 720, Presentation.LAYOUT_SCALE_TO_FIT)))).build()
            Composition.Builder(EditedMediaItemSequence(edited), EditedMediaItemSequence(listOf(frame), true)).build()
        } else Composition.Builder(EditedMediaItemSequence(edited)).build()
        val t = Transformer.Builder(context)
            .setVideoMimeType(if (output == MediaOutput.VIDEO_SMALL) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    saved = out; transformer = null
                    AppGraph.clipboard.changed()
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    out.delete()
                    error = exportException.localizedMessage ?: "This file couldn't be converted on this phone."
                    transformer = null
                }
            })
            .build()
        player.pause()
        exporting = true; saved = null; error = null; progress = null
        transformer = t
        t.start(composition, out.path)
    }
    LaunchedEffect(transformer) {
        val t = transformer ?: return@LaunchedEffect
        val holder = ProgressHolder()
        while (transformer === t) {
            if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress = holder.progress / 100f
            delay(200)
        }
    }

    val preview: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(if (isVideo) Color.Black else MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.Center) {
            if (isVideo) AndroidView({ PlayerView(it).apply { useController = false; this.player = player } }, Modifier.fillMaxSize())
            else Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(96.dp))
            FilledTonalIconButton(
                onClick = { if (player.isPlaying) player.pause() else { if (position !in startMs..endMs) player.seekTo(startMs); player.play() } },
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(52.dp),
            ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play") }
        }
    }
    val options: @Composable () -> Unit = {
        SectionLabel("Trim")
        Column(Modifier.padding(horizontal = 20.dp)) {
            RangeSlider(value = range, onValueChange = { r -> if (r.endInclusive - r.start > 0.002f) range = r }, onValueChangeFinished = { player.seekTo(startMs) })
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(startMs), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("${formatTime(endMs - startMs)} long", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(formatTime(endMs), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { if (duration > 0) range = (position.toFloat() / duration).coerceAtMost(range.endInclusive - 0.002f)..range.endInclusive }, modifier = Modifier.weight(1f)) { Text("Start here") }
                FilledTonalButton(onClick = { if (duration > 0) range = range.start..(position.toFloat() / duration).coerceAtLeast(range.start + 0.002f) }, modifier = Modifier.weight(1f)) { Text("End here") }
            }
        }
        SectionLabel("Save as")
        val choices = if (isVideo) listOf(MediaOutput.VIDEO, MediaOutput.VIDEO_SMALL, MediaOutput.AUDIO) else listOf(MediaOutput.AUDIO, MediaOutput.PICTURE_VIDEO)
        ChoiceChips(choices.map { it to it.label }, output, { output = it }, Modifier.padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()))
        if (isVideo && (output == MediaOutput.VIDEO || output == MediaOutput.VIDEO_SMALL)) {
            SectionLabel("Shape")
            ChoiceChips(listOf(0f to "Original", 1f to "Square", 16f / 9 to "16:9", 9f / 16 to "9:16", 4f / 3 to "4:3", 4f / 5 to "4:5"), aspect, { aspect = it }, Modifier.padding(horizontal = 20.dp))
            SectionLabel("Turn")
            ChoiceChips(listOf(0 to "None", 90 to "90° right", 180 to "180°", 270 to "90° left"), rotation, { rotation = it }, Modifier.padding(horizontal = 20.dp))
            SectionLabel("Size")
            ChoiceChips(listOf(0 to "Original", 1080 to "1080p", 720 to "720p", 480 to "480p"), height, { height = it }, Modifier.padding(horizontal = 20.dp))
            SwitchRow("Remove sound", null, null, mute) { mute = it }
        }
        if (output == MediaOutput.PICTURE_VIDEO) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (picture == null) "Uses the song's cover, or a plain card with its name if it has none." else "Uses the picture you chose.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose picture") }
            }
        }
        if (output == MediaOutput.AUDIO) {
            Text(
                if (isVideo) "Keeps only the sound, as M4A (AAC), which plays everywhere." else "Saves the trimmed part as M4A (AAC), which plays everywhere.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.navigationBarsPadding().padding(bottom = 24.dp))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditorBar(if (isVideo) "Edit video" else "Edit audio", onClose, "Save copy", enabled = duration > 0 && !exporting, onAction = ::export)
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                preview(Modifier.weight(1f).fillMaxSize().padding(bottom = 16.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { options() }
            }
        } else {
            preview(Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(max = 320.dp).aspectRatio(if (isVideo && videoW > 0) (videoW.toFloat() / videoH).coerceIn(0.56f, 1.78f) else 1.78f))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { options() }
        }
    }

    if (exporting) ExportDialog(
        progress, saved, error,
        onCancel = { transformer?.cancel(); transformer = null; exporting = false; outFile?.delete() },
        onDone = { exporting = false; if (saved != null) onClose() },
    )
}

/**
 * A picture for a song: its embedded cover if it has one, otherwise a card in the app's colours
 * with the song's name. Kept in the cache, as a file the converter can read.
 */
private fun coverPicture(context: android.content.Context, source: DocSource): Uri {
    val out = File(context.cacheDir, "cover-${source.uri.toString().hashCode().toUInt()}.png")
    val art = runCatching {
        android.media.MediaMetadataRetriever().run {
            try { setDataSource(context, source.uri); embeddedPicture } finally { release() }
        }
    }.getOrNull()
    val bitmap = art?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } ?: run {
        val b = android.graphics.Bitmap.createBitmap(1280, 720, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawPaint(android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 1280f, 720f, 0xFF1F3B63.toInt(), 0xFF0E1A2C.toInt(), android.graphics.Shader.TileMode.CLAMP) })
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 64f; textAlign = android.graphics.Paint.Align.CENTER }
        val title = source.name.substringBeforeLast('.').let { if (it.length > 32) it.take(31) + "…" else it }
        c.drawText("♪", 640f, 300f, p.apply { textSize = 120f })
        c.drawText(title, 640f, 440f, p.apply { textSize = 56f })
        b
    }
    out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    return Uri.fromFile(out)
}

package com.prempoddar.oaf.ui.viewers

import com.prempoddar.oaf.ui.design.Haptics
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.graphics.graphicsLayer
import com.prempoddar.oaf.ui.design.tap
import com.prempoddar.oaf.ui.edit.MediaOutput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.media.Pip
import com.prempoddar.oaf.media.PlayerState
import com.prempoddar.oaf.media.Playlist
import com.prempoddar.oaf.media.SleepTimer
import com.prempoddar.oaf.media.formatTime
import com.prempoddar.oaf.media.rememberMediaController
import com.prempoddar.oaf.media.rememberPlayerState
import com.prempoddar.oaf.ui.edit.MediaEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun itemFor(f: File, video: Boolean, subtitles: List<File> = if (video) Playlist.subtitlesFor(f) else emptyList()): MediaItem {
    val subs = subtitles.map { s ->
        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(s)).setMimeType(Playlist.mimeOfSubtitle(s))
            .setLanguage(Playlist.language(s, f.nameWithoutExtension)).setLabel(s.name).build()
    }
    return MediaItem.Builder().setUri(Uri.fromFile(f)).setMediaId(f.path)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(f.nameWithoutExtension).build())
        .setSubtitleConfigurations(subs).build()
}

/** Just the file that was opened, so playback starts at once. */
private fun firstItem(source: DocSource, video: Boolean): MediaItem = source.file?.let { itemFor(it, video) }
    ?: MediaItem.Builder().setUri(source.uri).setMediaId(source.uri.toString())
        // Streams named only by their type (no .m3u8 or .mpd in the link) need it said.
        .apply { source.mime?.lowercase()?.let { m -> if ("mpegurl" in m) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8) else if ("dash" in m) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD) } }
        .setMediaMetadata(MediaMetadata.Builder().setTitle(source.name.substringBeforeLast('.')).build()).build()

/** The rest of the folder, before and after the opened file, read off the main thread. */
private suspend fun neighbours(source: DocSource, video: Boolean): Pair<List<MediaItem>, List<MediaItem>> = withContext(Dispatchers.IO) {
    val file = source.file ?: return@withContext emptyList<MediaItem>() to emptyList()
    val list = Playlist.around(file)
    if (list.items.size <= 1) return@withContext emptyList<MediaItem>() to emptyList()
    val subs = if (video) Playlist.subtitleIndex(file.parentFile) else emptyMap()
    fun item(f: File) = itemFor(f, video, if (video) Playlist.subtitlesFrom(subs, f) else emptyList())
    list.items.take(list.start).map(::item) to list.items.drop(list.start + 1).map(::item)
}

/** Adds the folder around the playing item without interrupting it. */
private suspend fun Player.fillQueue(source: DocSource, video: Boolean) {
    val (before, after) = neighbours(source, video)
    if (after.isNotEmpty()) addMediaItems(after)
    if (before.isNotEmpty()) addMediaItems(0, before)
}

/**
 * Music plays through the app's media service, so it carries on with the screen off and shows
 * lock-screen controls. Videos get their own player on this screen.
 */
@OptIn(UnstableApi::class)
private fun cantEditStream(context: android.content.Context) =
    android.widget.Toast.makeText(context, "This is playing from a link. Save it to the phone first to edit it.", android.widget.Toast.LENGTH_LONG).show()

@OptIn(UnstableApi::class)
@Composable
fun MediaViewer(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var editAs by remember { mutableStateOf<MediaOutput?>(null) }
    if (editing) {
        MediaEditor(source, onClose = { editing = false; editAs = null }, start = editAs)
        return
    }
    if (source.kind == FileKind.VIDEO) {
        val player = remember {
            ExoPlayer.Builder(context)
                .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                .setHandleAudioBecomingNoisy(true)
                // Local files are fast: start as soon as half a second is ready rather than 2.5 s.
                .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 500, 1_000).build())
                .build().apply {
                    setMediaItem(firstItem(source, video = true))
                    prepare()
                    playWhenReady = true
                }
        }
        DisposableEffect(Unit) { onDispose { player.release() } }
        LaunchedEffect(player) { player.fillQueue(source, video = true) }
        VideoPlayer(rememberPlayerState(player), source, onBack, onEdit = { as_ -> if (source.isStream) cantEditStream(context) else { player.pause(); editAs = as_; editing = true } })
        return
    }
    val controller = rememberMediaController()
    if (controller == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    // Start this song (with its folder as the queue) unless it's already what's playing.
    LaunchedEffect(controller, source.uri) {
        val id = source.file?.path ?: source.uri.toString()
        if (controller.currentMediaItem?.mediaId != id) {
            controller.setMediaItem(firstItem(source, video = false))
            controller.prepare()
            controller.play()
            controller.fillQueue(source, video = false)
        } else controller.play()
    }
    AudioPlayer(rememberPlayerState(controller), source, onBack, onEdit = { as_ -> if (source.isStream) cantEditStream(context) else { controller.pause(); editAs = as_; editing = true } })
}

// ---------------------------------------------------------------- shared

/**
 * A thin seek bar, like the ones in good music and video apps: a 4 dp line that thickens while
 * you drag, a round knob, what's already loaded in a lighter shade, and the times underneath.
 */
@Composable
private fun SeekRow(state: PlayerState, active: Color, track: Color, text: Color, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = state.duration.coerceAtLeast(1)
    val fraction = drag ?: (state.position.toFloat() / dur).coerceIn(0f, 1f)
    val height by animateDpAsState(if (drag != null) 8.dp else 4.dp, label = "track")
    val knob by animateDpAsState(if (drag != null) 9.dp else 6.dp, label = "knob")
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(36.dp)
                .pointerInput(dur) { detectTapGestures { o -> state.player.seekTo((o.x / size.width * dur).toLong()) } }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { drag?.let { state.player.seekTo((it * dur).toLong()) }; drag = null },
                        onDragCancel = { drag = null },
                    ) { change, dx -> change.consume(); drag = ((drag ?: fraction) + dx / size.width).coerceIn(0f, 1f) }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = height.toPx(); val y = size.height / 2 - h / 2
                val r = CornerRadius(h / 2)
                drawRoundRect(track, Offset(0f, y), Size(size.width, h), r)
                val buffered = (state.buffered.toFloat() / dur).coerceIn(0f, 1f)
                drawRoundRect(active.copy(alpha = 0.35f), Offset(0f, y), Size(size.width * buffered, h), r)
                drawRoundRect(active, Offset(0f, y), Size(size.width * fraction, h), r)
                drawCircle(active, knob.toPx(), Offset(size.width * fraction, size.height / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            val shownAt = drag?.let { (it * dur).toLong() } ?: state.position
            Text(formatTime(shownAt), style = style, color = text, modifier = Modifier.weight(1f))
            Text("−" + formatTime((state.duration - shownAt).coerceAtLeast(0)), style = style, color = text)
        }
    }
}

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(s: Float) = "${s.toString().removeSuffix(".0")}×"

@Composable
private fun SpeedButton(state: PlayerState, tint: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = tap { open = true }) { Text(speedLabel(state.speed), color = tint, style = MaterialTheme.typography.labelLarge) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            speeds.forEach { s -> DropdownMenuItem(text = { Text(if (s == 1f) "Normal" else speedLabel(s)) }, onClick = tap { open = false; state.player.setPlaybackSpeed(s) }) }
        }
    }
}

@Composable
private fun SleepButton(tint: Color, activeTint: Color) {
    val timer by SleepTimer.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) { while (timer is SleepTimer.State.At) { now = System.currentTimeMillis(); delay(1000) } }
    Box {
        Row(Modifier.clip(CircleShape).clickable { open = true }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val on = timer != SleepTimer.State.Off
            Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (on) activeTint else tint)
            when (val t = timer) {
                is SleepTimer.State.At -> Text(" " + formatTime(t.endsAt - now), color = activeTint, style = MaterialTheme.typography.labelLarge)
                SleepTimer.State.EndOfTrack -> Text(" End of track", color = activeTint, style = MaterialTheme.typography.labelLarge)
                SleepTimer.State.Off -> Unit
            }
        }
    }
    if (open) SleepSheet(onDismiss = { open = false })
}

/** The sleep timer as a sheet: what's set now, then the choices as chips. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary)
                Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
            }
            Text("Playback pauses when the time is up.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            SleepChoices(onPicked = onDismiss)
        }
    }
}

/** The sleep timer's choices, highlighting the one that's set and showing the time left. */
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepChoices(modifier: Modifier = Modifier, onPicked: () -> Unit = {}) {
    val timer by SleepTimer.state.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) { while (timer is SleepTimer.State.At) { now = System.currentTimeMillis(); delay(1000) } }
    Column(modifier) {
        when (val t = timer) {
            is SleepTimer.State.At -> Text("Stops in ${formatTime(t.endsAt - now)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            SleepTimer.State.EndOfTrack -> Text("Stops when this one ends", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            SleepTimer.State.Off -> Unit
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                FilterChip(false, onClick = tap { SleepTimer.minutes(m); onPicked() }, label = { Text(if (m < 60) "$m min" else if (m == 60) "1 hour" else "1 h 30") })
            }
            FilterChip(timer == SleepTimer.State.EndOfTrack, onClick = tap { SleepTimer.endOfTrack(); onPicked() }, label = { Text("End of this one") })
        }
        if (timer != SleepTimer.State.Off) {
            OutlinedButton(onClick = tap { SleepTimer.cancel(); onPicked() }, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) { Text("Turn off the timer") }
        }
    }
}

@Composable
private fun ErrorNote(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.large) {
            Text(message, modifier = Modifier.padding(16.dp))
        }
    }
}

// ---------------------------------------------------------------- audio

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioPlayer(state: PlayerState, source: DocSource, onBack: () -> Unit, onEdit: (MediaOutput?) -> Unit) {
    val context = LocalContext.current
    val wide = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    var queue by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val art by produceState<ImageBitmap?>(null, state.metadata) {
        value = withContext(Dispatchers.Default) { state.metadata.artworkData?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    }
    // The screen takes its colour from the cover, like a record sleeve.
    val scheme = MaterialTheme.colorScheme
    val tintTarget by produceState(scheme.primaryContainer, art) {
        val a = art ?: return@produceState
        value = withContext(Dispatchers.Default) {
            Palette.from(a.asAndroidBitmap()).generate().let { p -> (p.darkMutedSwatch ?: p.mutedSwatch ?: p.dominantSwatch)?.rgb?.let { Color(it) } }
        } ?: scheme.primaryContainer
    }
    val tint by animateColorAsState(tintTarget, label = "tint")
    val fg = scheme.onSurface
    val dim = scheme.onSurfaceVariant

    Box(Modifier.fillMaxSize().background(scheme.background).background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.75f), tint.copy(alpha = 0.15f), scheme.background)))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = tap(onBack)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = dim)
                    Text(source.file?.parentFile?.name ?: "Your files", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = tap { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Trim or convert") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = tap { menu = false; onEdit(null) })
                        DropdownMenuItem(text = { Text("Make a video") }, leadingIcon = { Icon(Icons.Rounded.Movie, null) }, onClick = tap { menu = false; onEdit(MediaOutput.PICTURE_VIDEO) })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = tap { menu = false; shareSource(context, source) })
                        DropdownMenuItem(text = { Text("Open with…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }, onClick = tap { menu = false; openElsewhere(context, source) })
                    }
                }
            }
            val artwork: @Composable (Modifier) -> Unit = { m ->
                Box(m.aspectRatio(1f).shadow(24.dp, MaterialTheme.shapes.large).clip(MaterialTheme.shapes.large).background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    art?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        ?: Icon(Icons.Rounded.MusicNote, null, tint = dim, modifier = Modifier.size(96.dp))
                }
            }
            val controls: @Composable () -> Unit = {
                Text(state.title.ifEmpty { source.name.substringBeforeLast('.') }, style = MaterialTheme.typography.headlineSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist ?: "Unknown artist", style = MaterialTheme.typography.titleMedium, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                SeekRow(state, active = fg, track = fg.copy(alpha = 0.18f), text = dim, modifier = Modifier.padding(top = 16.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = tap { state.player.shuffleModeEnabled = !state.shuffle }) {
                        Icon(Icons.Rounded.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", tint = if (state.shuffle) scheme.primary else dim)
                    }
                    IconButton(onClick = tap { state.player.seekToPrevious() }, modifier = Modifier.size(64.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(44.dp), tint = fg) }
                    Surface(onClick = { state.toggle() }, shape = CircleShape, color = fg, contentColor = scheme.background, modifier = Modifier.size(76.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.buffering) CircularProgressIndicator(Modifier.size(32.dp), color = scheme.background, strokeWidth = 3.dp)
                            else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(42.dp))
                        }
                    }
                    IconButton(onClick = tap { state.player.seekToNext() }, modifier = Modifier.size(64.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(44.dp), tint = fg) }
                    IconButton(onClick = tap { state.cycleRepeat() }) {
                        Icon(
                            if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            when (state.repeat) { Player.REPEAT_MODE_OFF -> "Repeat off"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat one" },
                            tint = if (state.repeat != Player.REPEAT_MODE_OFF) scheme.primary else dim,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SpeedButton(state, dim)
                    SleepButton(dim, scheme.primary)
                    IconButton(onClick = tap { queue = true }, enabled = state.count > 1) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", tint = if (state.count > 1) dim else dim.copy(alpha = 0.38f)) }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    artwork(Modifier.fillMaxHeight())
                    Column(Modifier.weight(1f)) { controls() }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
                    Spacer(Modifier.weight(1f))
                    artwork(Modifier.widthIn(max = 420.dp).fillMaxWidth().align(Alignment.CenterHorizontally))
                    Spacer(Modifier.weight(1f))
                    controls()
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    if (queue) ModalBottomSheet(onDismissRequest = { queue = false }) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            item { Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
            itemsIndexed(List(state.count) { state.player.getMediaItemAt(it) }) { i, item ->
                val current = i == state.index
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(MaterialTheme.shapes.large)
                        .background(if (current) scheme.secondaryContainer else Color.Transparent)
                        .clickable { state.player.seekTo(i, 0); state.player.play() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                    Text(item.mediaMetadata.title?.toString() ?: "", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (current) Icon(if (state.playing) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.Pause, null, tint = scheme.primary)
                }
            }
        }
    }
    state.error?.let { ErrorNote(it) }
}

// ---------------------------------------------------------------- video

@Composable
private fun VideoIcon(icon: ImageVector, label: String, size: Dp = 24.dp, onClick: () -> Unit) {
    IconButton(onClick = tap(onClick)) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size)) }
}

@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(state: PlayerState, source: DocSource, onBack: () -> Unit, onEdit: (MediaOutput?) -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.activity() }
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val inPip by Pip.active
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Triple<ImageVector, String, Alignment>?>(null) }
    var fit by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var more by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f) }
    // Pinch to zoom in on the picture and move around it.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var view by remember { mutableStateOf<PlayerView?>(null) }
    var subtitleSize by remember { mutableFloatStateOf(1f) }
    var subtitleBox by remember { mutableStateOf(false) }
    // A-B repeat: the part between two points plays over and over.
    var loopA by remember { mutableStateOf<Long?>(null) }
    var loopB by remember { mutableStateOf<Long?>(null) }
    var resumedFrom by remember { mutableStateOf<Long?>(null) }
    // Holding the picture plays it at double speed until the finger lifts; this is what to go back to.
    var held by remember { mutableStateOf<Pair<Float, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    val key = source.uri.toString()
    val loadSubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val p = state.player
        val item = p.currentMediaItem ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment.orEmpty().lowercase()
        val mime = when { name.endsWith(".vtt") -> "text/vtt"; name.endsWith(".ass") || name.endsWith(".ssa") -> "text/x-ssa"; else -> "application/x-subrip" }
        val withSubs = item.buildUpon().setSubtitleConfigurations(
            item.localConfiguration?.subtitleConfigurations.orEmpty() + MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(),
        ).build()
        val at = p.currentPosition
        p.replaceMediaItem(p.currentMediaItemIndex, withSubs)
        p.seekTo(at)
        state.setSubtitles(true)
    }

    // Keep the screen on while watching; put brightness and rotation back when leaving.
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Pip.onLeave = { if (state.player.isPlaying && activity != null) Pip.enter(activity, state.video.width, state.video.height) }
        onDispose {
            Pip.onLeave = null
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // Carry on where this video was left last time, with a way back to the start.
    LaunchedEffect(key) {
        if (source.isStream) return@LaunchedEffect
        val saved = com.prempoddar.oaf.AppGraph.prefs.position(key).toLong()
        if (saved < 5_000) return@LaunchedEffect
        while (state.duration <= 0) delay(100)
        if (saved < state.duration - 10_000) { state.player.seekTo(saved); resumedFrom = saved; delay(6_000); resumedFrom = null }
    }
    LaunchedEffect(key, state.playing) {
        if (source.isStream) return@LaunchedEffect
        while (true) {
            val at = state.player.currentPosition
            val near = state.duration > 0 && at > state.duration - 5_000
            com.prempoddar.oaf.AppGraph.prefs.savePosition(key, if (near) 0 else at.toInt())
            if (!state.playing) break
            delay(5_000)
        }
    }
    LaunchedEffect(loopA, loopB) {
        val a = loopA ?: return@LaunchedEffect
        val b = loopB ?: return@LaunchedEffect
        while (true) { if (state.player.currentPosition >= b) state.player.seekTo(a); delay(100) }
    }
    LaunchedEffect(view, subtitleSize, subtitleBox) {
        view?.subtitleView?.let { sv ->
            sv.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleSize)
            sv.setStyle(
                androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE, if (subtitleBox) 0x99000000.toInt() else android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                    if (subtitleBox) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null,
                ),
            )
        }
    }
    BackHandler { if (locked) controls = true else if (zoom != 1f) { zoom = 1f; pan = Offset.Zero } else onBack() }
    LaunchedEffect(controls, state.playing, locked) { if (controls && (state.playing || locked)) { delay(if (locked) 2000 else 3500); controls = false } }
    LaunchedEffect(feedback) { if (feedback != null) { delay(700); feedback = null } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; player = state.player; setKeepContentOnPlayerReset(true); setShutterBackgroundColor(android.graphics.Color.BLACK) }.also { v -> view = v } },
            update = { it.resizeMode = fit; if (it.player !== state.player) it.player = state.player },
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
        )
        if (!inPip) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        // Two fingers zoom and move the picture; they're taken before the one-finger
                        // gestures (seek, brightness, volume) can see them.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    val z = event.calculateZoom(); val p = event.calculatePan()
                                    val next = (zoom * z).coerceIn(1f, 5f)
                                    val limitX = w * (next - 1) / 2; val limitY = h * (next - 1) / 2
                                    zoom = next
                                    pan = if (next == 1f) Offset.Zero else Offset((pan.x + p.x).coerceIn(-limitX, limitX), (pan.y + p.y).coerceIn(-limitY, limitY))
                                    feedback = Triple(Icons.Rounded.ZoomIn, "${(zoom * 100).toInt()}%", Alignment.Center)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(locked) {
                        detectTapGestures(
                            onPress = {
                                tryAwaitRelease()
                                held?.let { (speed, wasPlaying) ->
                                    state.player.setPlaybackSpeed(speed)
                                    if (!wasPlaying) state.player.pause()
                                    held = null
                                }
                            },
                            onLongPress = {
                                if (locked) return@detectTapGestures
                                held = state.speed to state.player.isPlaying
                                state.player.setPlaybackSpeed(2f)
                                state.player.play()
                                controls = false
                                Haptics.threshold()
                            },
                            onTap = { controls = !controls },
                            onDoubleTap = { o ->
                                if (locked) return@detectTapGestures
                                if (o.x < w / 2) { state.seekBy(-10_000); feedback = Triple(Icons.Rounded.FastRewind, "10 s", Alignment.CenterStart) }
                                else { state.seekBy(10_000); feedback = Triple(Icons.Rounded.FastForward, "10 s", Alignment.CenterEnd) }
                            },
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        var mode = 0 // 1 seek, 2 brightness, 3 volume
                        var startX = 0f
                        var seekTo = 0L
                        var volumeAcc = 0f
                        detectDragGestures(
                            onDragStart = { o -> mode = 0; startX = o.x; seekTo = state.player.currentPosition; volumeAcc = 0f },
                            onDragEnd = { if (mode == 1) state.player.seekTo(seekTo); mode = 0 },
                            onDragCancel = { mode = 0 },
                        ) { change, drag ->
                            change.consume()
                            if (mode == 0) mode = if (kotlin.math.abs(drag.x) > kotlin.math.abs(drag.y)) 1 else if (startX < w / 2) 2 else 3
                            when (mode) {
                                1 -> {
                                    seekTo = (seekTo + (drag.x / w * 90_000).toLong()).coerceIn(0, state.duration.coerceAtLeast(0))
                                    feedback = Triple(Icons.Rounded.Forward10, "${formatTime(seekTo)} / ${formatTime(state.duration)}", Alignment.Center)
                                }
                                2 -> {
                                    brightness = (brightness - drag.y / h).coerceIn(0.01f, 1f)
                                    activity?.window?.let { win -> win.attributes = win.attributes.apply { screenBrightness = brightness } }
                                    feedback = Triple(Icons.Rounded.BrightnessMedium, "${(brightness * 100).toInt()}%", Alignment.Center)
                                }
                                3 -> {
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    volumeAcc -= drag.y / h * max * 1.5f
                                    val steps = volumeAcc.toInt()
                                    if (steps != 0) {
                                        volumeAcc -= steps
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max), 0)
                                    }
                                    feedback = Triple(Icons.AutoMirrored.Rounded.VolumeUp, "${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}%", Alignment.Center)
                                }
                            }
                        }
                    },
            )
            if (state.buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White, strokeWidth = 4.dp)
            resumedFrom?.let { from ->
                Surface(
                    onClick = { Haptics.tap(); state.player.seekTo(0); resumedFrom = null }, color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, bottom = 120.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Replay, null, Modifier.size(18.dp))
                        Text("Resumed at ${formatTime(from)} · Start over", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            AnimatedVisibility(held != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp)) {
                Surface(color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("2×", style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.Rounded.FastForward, null, Modifier.padding(start = 6.dp).size(20.dp))
                    }
                }
            }
            feedback?.let { (icon, text, where) ->
                Surface(color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(where).padding(horizontal = 48.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null)
                        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    if (locked) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                            Surface(onClick = { locked = false; controls = true }, shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, modifier = Modifier.align(Alignment.CenterStart)) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.LockOpen, null)
                                    Text("Unlock", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
                            Row(
                                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VideoIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                    Text(state.title.ifEmpty { source.name }, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (state.count > 1) Text("${state.index + 1} of ${state.count} in this folder", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                }
                                VideoIcon(
                                    if (state.hasSubtitles && state.subtitlesOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled,
                                    if (state.hasSubtitles) "Subtitles" else "Load subtitles",
                                ) { if (state.hasSubtitles) state.setSubtitles(!state.subtitlesOn) else loadSubs.launch(arrayOf("*/*")) }
                                VideoIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture") { activity?.let { Pip.enter(it, state.video.width, state.video.height) } }
                                VideoIcon(Icons.Rounded.Tune, "More") { more = true }
                            }
                            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                if (state.count > 1) GlassButton(Icons.Rounded.SkipPrevious, "Previous video", 48.dp, enabled = state.index > 0) { state.player.seekToPrevious() }
                                GlassButton(Icons.Rounded.Replay10, "Back 10 seconds", 56.dp) { state.seekBy(-10_000) }
                                if (!state.buffering) Surface(onClick = { state.toggle() }, shape = CircleShape, color = Color.White, contentColor = Color.Black, modifier = Modifier.size(76.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(44.dp)) }
                                } else Spacer(Modifier.size(76.dp))
                                GlassButton(Icons.Rounded.Forward10, "Forward 10 seconds", 56.dp) { state.seekBy(10_000) }
                                if (state.count > 1) GlassButton(Icons.Rounded.SkipNext, "Next video", 48.dp, enabled = state.index < state.count - 1) { state.player.seekToNext() }
                            }
                            Column(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                                    .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                SeekRow(state, active = Color.White, track = Color.White.copy(alpha = 0.25f), text = Color.White.copy(alpha = 0.85f))
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassPill(Icons.Rounded.Lock, "Lock") { locked = true }
                                    GlassPill(Icons.Rounded.Speed, speedLabel(state.speed)) { more = true }
                                    if (!state.playing) {
                                        // One frame at a time, for finding the exact moment.
                                        GlassPill(Icons.Rounded.ChevronLeft, "Frame") { stepFrame(state, -1) }
                                        GlassPill(Icons.Rounded.ChevronRight, "Frame") { stepFrame(state, 1) }
                                    }
                                    if (loopA != null) GlassPill(Icons.Rounded.Repeat, if (loopB == null) "A…" else "A–B") { loopA = null; loopB = null }
                                    if (zoom != 1f) GlassPill(Icons.Rounded.ZoomOut, "${(zoom * 100).toInt()}%") { zoom = 1f; pan = Offset.Zero }
                                    Spacer(Modifier.weight(1f))
                                    GlassPill(Icons.Rounded.PhotoCamera, null) { view?.let { v -> screenshot(context, v, source.name, state.player.currentPosition) } }
                                    GlassPill(Icons.Rounded.AspectRatio, if (fit == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Fit" else "Fill") {
                                        zoom = 1f; pan = Offset.Zero
                                        fit = if (fit == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    GlassPill(Icons.Rounded.ScreenRotation, null) {
                                        activity?.requestedOrientation = if (activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        state.error?.let { ErrorNote(it) }
    }
    if (more) VideoMoreSheet(
        state, onDismiss = { more = false },
        subtitleSize = subtitleSize, onSubtitleSize = { subtitleSize = it }, subtitleBox = subtitleBox, onSubtitleBox = { subtitleBox = it },
        loop = loopA to loopB,
        onLoop = {
            val at = state.player.currentPosition
            when {
                loopA == null -> loopA = at
                loopB == null && at > loopA!! -> { loopB = at; state.player.seekTo(loopA!!) }
                else -> { loopA = null; loopB = null }
            }
        },
        onSubtitles = { more = false; loadSubs.launch(arrayOf("*/*")) },
        onEdit = { as_ -> more = false; onEdit(as_) },
        onShare = { more = false; shareSource(context, source) },
        onOpenWith = { more = false; openElsewhere(context, source) },
    )
}

/** A round see-through button over the picture. */
@Composable
private fun GlassButton(icon: ImageVector, label: String, size: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape, color = Color.Black.copy(alpha = 0.35f),
        contentColor = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(size),
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(size * 0.55f)) } }
}

/** A small see-through pill with an icon and an optional word, for the bar under the seek line. */
@Composable
private fun GlassPill(icon: ImageVector, text: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.14f), contentColor = Color.White) {
        Row(Modifier.padding(horizontal = if (text == null) 10.dp else 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, text, Modifier.size(18.dp))
            if (text != null) Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Everything else about the video, as big tiles in a sheet rather than a list of words. */
@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VideoMoreSheet(
    state: PlayerState, onDismiss: () -> Unit,
    subtitleSize: Float, onSubtitleSize: (Float) -> Unit, subtitleBox: Boolean, onSubtitleBox: (Boolean) -> Unit,
    loop: Pair<Long?, Long?>, onLoop: () -> Unit,
    onSubtitles: () -> Unit, onEdit: (MediaOutput?) -> Unit, onShare: () -> Unit, onOpenWith: () -> Unit,
) {
    val audioTracks = remember(state.player.currentTracks) {
        state.player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).map { i -> g to i } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
            Text("Playback speed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                speeds.forEach { sp -> FilterChip(state.speed == sp, onClick = tap { state.player.setPlaybackSpeed(sp) }, label = { Text(if (sp == 1f) "Normal" else speedLabel(sp)) }) }
            }
            if (audioTracks.size > 1) {
                Text("Sound track", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    audioTracks.forEachIndexed { n, (g, i) ->
                        val f = g.getTrackFormat(i)
                        val name = f.label ?: f.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage.ifEmpty { it } } ?: "Track ${n + 1}"
                        FilterChip(g.isTrackSelected(i), onClick = tap {
                            state.player.trackSelectionParameters = state.player.trackSelectionParameters.buildUpon()
                                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                        }, label = { Text(name) })
                    }
                }
            }
            if (state.hasSubtitles) {
                Text("Subtitles", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.8f to "Small", 1f to "Normal", 1.3f to "Large", 1.7f to "Huge").forEach { (k, label) ->
                        FilterChip(subtitleSize == k, onClick = tap { onSubtitleSize(k) }, label = { Text(label) })
                    }
                    FilterChip(subtitleBox, onClick = { Haptics.toggle(!subtitleBox); onSubtitleBox(!subtitleBox) }, label = { Text("Dark box behind") })
                }
            }
            Text("Repeat a part", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val (a, b) = loop
                FilledTonalButton(onClick = tap(onLoop)) {
                    Text(when { a == null -> "Start here (A)"; b == null -> "End here (B)"; else -> "Stop repeating" })
                }
                Text(
                    when { a == null -> "Mark where the part begins"; b == null -> "From ${formatTime(a)}, now mark the end"; else -> "${formatTime(a)} to ${formatTime(b)} plays on repeat" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text("Sleep timer", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            SleepChoices(Modifier.padding(vertical = 8.dp))
            Text("Do more", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            val tiles = listOf(
                Triple(Icons.Rounded.ClosedCaption, "Subtitles file", onSubtitles),
                Triple(Icons.Rounded.ContentCut, "Trim or crop", { onEdit(null) }),
                Triple(Icons.Rounded.MusicNote, "Save the sound", { onEdit(MediaOutput.AUDIO) }),
                Triple(Icons.Rounded.Compress, "Make smaller", { onEdit(MediaOutput.VIDEO_SMALL) }),
                Triple(Icons.Rounded.Share, "Share", onShare),
                Triple(Icons.AutoMirrored.Rounded.OpenInNew, "Open with", onOpenWith),
            )
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (icon, label, go) ->
                        Surface(onClick = go, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Moves one frame forward or back, exactly rather than to the nearest key frame. */
@OptIn(UnstableApi::class)
private fun stepFrame(state: PlayerState, frames: Int) {
    val exo = state.player as? ExoPlayer ?: return
    val fps = exo.videoFormat?.frameRate?.takeIf { it > 0 } ?: 30f
    exo.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
    exo.pause()
    exo.seekTo((exo.currentPosition + (frames * 1000f / fps).toLong()).coerceAtLeast(0))
}

/** Saves the frame on screen to Pictures/OAF, named after the video and the moment. */
@OptIn(UnstableApi::class)
private fun screenshot(context: android.content.Context, view: PlayerView, videoName: String, at: Long) {
    val surface = view.videoSurfaceView as? android.view.SurfaceView ?: return
    if (surface.width <= 0 || surface.height <= 0) return
    val bitmap = android.graphics.Bitmap.createBitmap(surface.width, surface.height, android.graphics.Bitmap.Config.ARGB_8888)
    android.view.PixelCopy.request(surface, bitmap, { result ->
        if (result != android.view.PixelCopy.SUCCESS) { Haptics.reject(); android.widget.Toast.makeText(context, "Couldn't take a picture of this video", android.widget.Toast.LENGTH_SHORT).show(); return@request }
        val name = videoName.substringBeforeLast('.') + " " + formatTime(at).replace(':', '.') + ".png"
        val saved = runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (android.os.Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/OAF")
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
        if (saved) Haptics.confirm() else Haptics.reject()
        android.widget.Toast.makeText(context, if (saved) "Saved to Pictures/OAF" else "Couldn't save the picture", android.widget.Toast.LENGTH_SHORT).show()
    }, android.os.Handler(android.os.Looper.getMainLooper()))
}

package com.prempoddar.oaf.ui.viewers

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
@Composable
fun MediaViewer(source: DocSource, onBack: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        MediaEditor(source, onClose = { editing = false })
        return
    }
    if (source.kind == FileKind.VIDEO) {
        val context = LocalContext.current
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
        VideoPlayer(rememberPlayerState(player), source, onBack, onEdit = { player.pause(); editing = true })
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
    AudioPlayer(rememberPlayerState(controller), source, onBack, onEdit = { controller.pause(); editing = true })
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
        TextButton(onClick = { open = true }) { Text(speedLabel(state.speed), color = tint, style = MaterialTheme.typography.labelLarge) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            speeds.forEach { s -> DropdownMenuItem(text = { Text(if (s == 1f) "Normal" else speedLabel(s)) }, onClick = { open = false; state.player.setPlaybackSpeed(s) }) }
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
        DropdownMenu(open, onDismissRequest = { open = false }) {
            listOf(5, 15, 30, 45, 60, 90).forEach { m -> DropdownMenuItem(text = { Text("$m minutes") }, onClick = { open = false; SleepTimer.minutes(m) }) }
            DropdownMenuItem(text = { Text("End of this track") }, onClick = { open = false; SleepTimer.endOfTrack() })
            if (timer != SleepTimer.State.Off) DropdownMenuItem(text = { Text("Turn off") }, onClick = { open = false; SleepTimer.cancel() })
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
internal fun AudioPlayer(state: PlayerState, source: DocSource, onBack: () -> Unit, onEdit: () -> Unit) {
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
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = dim)
                    Text(source.file?.parentFile?.name ?: "Your files", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Trim or convert") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; shareSource(context, source) })
                        DropdownMenuItem(text = { Text("Open with…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }, onClick = { menu = false; openElsewhere(context, source) })
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
                    IconButton(onClick = { state.player.shuffleModeEnabled = !state.shuffle }) {
                        Icon(Icons.Rounded.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", tint = if (state.shuffle) scheme.primary else dim)
                    }
                    IconButton(onClick = { state.player.seekToPrevious() }, modifier = Modifier.size(64.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(44.dp), tint = fg) }
                    Surface(onClick = { state.toggle() }, shape = CircleShape, color = fg, contentColor = scheme.background, modifier = Modifier.size(76.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.buffering) CircularProgressIndicator(Modifier.size(32.dp), color = scheme.background, strokeWidth = 3.dp)
                            else Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(42.dp))
                        }
                    }
                    IconButton(onClick = { state.player.seekToNext() }, modifier = Modifier.size(64.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(44.dp), tint = fg) }
                    IconButton(onClick = { state.cycleRepeat() }) {
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
                    IconButton(onClick = { queue = true }, enabled = state.count > 1) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", tint = if (state.count > 1) dim else dim.copy(alpha = 0.38f)) }
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
    IconButton(onClick = onClick) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size)) }
}

@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(state: PlayerState, source: DocSource, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.activity() }
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val inPip by Pip.active
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Triple<ImageVector, String, Alignment>?>(null) }
    var fit by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var menu by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f) }
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
    BackHandler { if (locked) controls = true else onBack() }
    LaunchedEffect(controls, state.playing, locked) { if (controls && (state.playing || locked)) { delay(if (locked) 2000 else 3500); controls = false } }
    LaunchedEffect(feedback) { if (feedback != null) { delay(700); feedback = null } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; player = state.player; setKeepContentOnPlayerReset(true); setShutterBackgroundColor(android.graphics.Color.BLACK) } },
            update = { it.resizeMode = fit; if (it.player !== state.player) it.player = state.player },
            modifier = Modifier.fillMaxSize(),
        )
        if (!inPip) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
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
                                Box {
                                    VideoIcon(Icons.Rounded.MoreVert, "More") { menu = true }
                                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Load subtitles…") }, leadingIcon = { Icon(Icons.Rounded.ClosedCaption, null) }, onClick = { menu = false; loadSubs.launch(arrayOf("*/*")) })
                                        DropdownMenuItem(text = { Text("Trim, crop or convert") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; shareSource(context, source) })
                                        DropdownMenuItem(text = { Text("Open with…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }, onClick = { menu = false; openElsewhere(context, source) })
                                    }
                                }
                            }
                            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                                VideoIcon(Icons.Rounded.Replay10, "Back 10 seconds", 36.dp) { state.seekBy(-10_000) }
                                if (!state.buffering) Surface(onClick = { state.toggle() }, shape = CircleShape, color = Color.White.copy(alpha = 0.18f), contentColor = Color.White, modifier = Modifier.size(80.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(48.dp)) }
                                } else Spacer(Modifier.size(80.dp))
                                VideoIcon(Icons.Rounded.Forward10, "Forward 10 seconds", 36.dp) { state.seekBy(10_000) }
                            }
                            Column(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                SeekRow(state, active = Color.White, track = Color.White.copy(alpha = 0.25f), text = Color.White.copy(alpha = 0.85f))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    VideoIcon(Icons.Rounded.Lock, "Lock controls") { locked = true }
                                    SpeedButton(state, Color.White)
                                    Spacer(Modifier.weight(1f))
                                    VideoIcon(Icons.Rounded.AspectRatio, if (fit == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Fill the screen" else "Fit to screen") {
                                        fit = if (fit == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    VideoIcon(Icons.Rounded.ScreenRotation, "Rotate") {
                                        activity?.requestedOrientation = if (activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                    VideoIcon(Icons.Rounded.PictureInPictureAlt, "Picture in picture") { activity?.let { Pip.enter(it, state.video.width, state.video.height) } }
                                    if (state.count > 1) VideoIcon(Icons.Rounded.SkipNext, "Next video") { state.player.seekToNext() }
                                }
                            }
                        }
                    }
                }
            }
        }
        state.error?.let { ErrorNote(it) }
    }
}

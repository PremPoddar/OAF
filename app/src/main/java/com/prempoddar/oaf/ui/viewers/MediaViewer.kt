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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forward10
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
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
import kotlinx.coroutines.delay
import java.io.File

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun itemFor(f: File, video: Boolean): MediaItem {
    val subs = if (video) Playlist.subtitlesFor(f).map { s ->
        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(s)).setMimeType(Playlist.mimeOfSubtitle(s))
            .setLanguage(Playlist.language(s, f.nameWithoutExtension)).setLabel(s.name).build()
    } else emptyList()
    return MediaItem.Builder().setUri(Uri.fromFile(f)).setMediaId(f.path)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(f.nameWithoutExtension).build())
        .setSubtitleConfigurations(subs).build()
}

/** Music and video: a full player that keeps playing in the background, with a queue of the folder. */
@Composable
fun MediaViewer(source: DocSource, onBack: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        MediaEditor(source, onClose = { editing = false })
        return
    }
    val controller = rememberMediaController()
    val video = source.kind == FileKind.VIDEO
    if (controller == null) {
        Box(Modifier.fillMaxSize().background(if (video) Color.Black else MaterialTheme.colorScheme.background))
        return
    }
    // Start this file (and its folder as the queue) unless it's already what's playing.
    LaunchedEffect(controller, source.uri) {
        val id = source.file?.path ?: source.uri.toString()
        if (controller.currentMediaItem?.mediaId != id) {
            val file = source.file
            if (file != null) {
                val list = Playlist.around(file)
                controller.setMediaItems(list.items.map { itemFor(it, video) }, list.start, 0)
            } else {
                controller.setMediaItem(
                    MediaItem.Builder().setUri(source.uri).setMediaId(id)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(source.name.substringBeforeLast('.')).build()).build(),
                )
            }
            controller.prepare()
        }
        controller.play()
    }
    val state = rememberPlayerState(controller)
    if (video) VideoPlayer(state, source, onBack, onEdit = { controller.pause(); editing = true })
    else AudioPlayer(state, source, onBack, onEdit = { controller.pause(); editing = true })
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPlayer(state: PlayerState, source: DocSource, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val wide = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    var queue by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val art by produceState<ImageBitmap?>(null, state.metadata) {
        value = state.metadata.artworkData?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), MaterialTheme.colorScheme.background)),
        ),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text(if (state.count > 1) "${state.index + 1} of ${state.count}" else "Now playing", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                if (state.count > 1) IconButton(onClick = { queue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") }
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
                Surface(m.aspectRatio(1f), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 8.dp) {
                    art?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        ?: Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(96.dp)) }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    artwork(Modifier.fillMaxHeight())
                    Column(Modifier.weight(1f)) { AudioControls(state) }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(0.5f))
                    artwork(Modifier.widthIn(max = 360.dp).fillMaxWidth())
                    Spacer(Modifier.weight(0.5f))
                    AudioControls(state)
                    Spacer(Modifier.height(24.dp))
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
                        .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .pointerInput(i) { detectTapGestures { state.player.seekTo(i, 0); state.player.play() } }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (current && state.playing) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                    Text(item.mediaMetadata.title?.toString() ?: "", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
    state.error?.let { ErrorNote(it) }
}

@Composable
private fun AudioControls(state: PlayerState) {
    Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    state.artist?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
    SeekBar(state, Modifier.padding(top = 20.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { state.player.shuffleModeEnabled = !state.shuffle }) {
            Icon(Icons.Rounded.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { state.player.seekToPrevious() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp)) }
        FilledIconButton(onClick = { state.toggle() }, modifier = Modifier.size(80.dp), shape = MaterialTheme.shapes.extraLarge) {
            Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(40.dp))
        }
        IconButton(onClick = { state.player.seekToNext() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp)) }
        IconButton(onClick = { state.cycleRepeat() }) {
            Icon(
                if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                when (state.repeat) { Player.REPEAT_MODE_OFF -> "Repeat off"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat one" },
                tint = if (state.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
        SpeedChip(state)
        Spacer(Modifier.size(8.dp))
        SleepChip()
    }
}

@Composable
private fun SeekBar(state: PlayerState, modifier: Modifier = Modifier, onLight: Boolean = false) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = state.duration.coerceAtLeast(1)
    val textColor = if (onLight) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Slider(
            value = dragging ?: (state.position.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { state.player.seekTo((it * dur).toLong()) }; dragging = null },
            colors = if (onLight) SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)) else SliderDefaults.colors(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(dragging?.let { (it * dur).toLong() } ?: state.position), style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.weight(1f))
            Text(formatTime(state.duration), style = MaterialTheme.typography.labelMedium, color = textColor)
        }
    }
}

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
private fun SpeedChip(state: PlayerState) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(if (state.speed == 1f) "Speed" else "${state.speed}×") }, leadingIcon = { Icon(Icons.Rounded.Speed, null) })
        DropdownMenu(open, onDismissRequest = { open = false }) {
            speeds.forEach { s -> DropdownMenuItem(text = { Text(if (s == 1f) "Normal" else "$s×") }, onClick = { open = false; state.player.setPlaybackSpeed(s) }) }
        }
    }
}

@Composable
private fun SleepChip() {
    val timer by SleepTimer.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) { while (timer is SleepTimer.State.At) { now = System.currentTimeMillis(); delay(1000) } }
    val label = when (val t = timer) {
        SleepTimer.State.Off -> "Sleep"
        SleepTimer.State.EndOfTrack -> "End of track"
        is SleepTimer.State.At -> formatTime(t.endsAt - now)
    }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label) }, leadingIcon = { Icon(Icons.Rounded.Bedtime, null) })
        DropdownMenu(open, onDismissRequest = { open = false }) {
            listOf(5, 15, 30, 45, 60).forEach { m -> DropdownMenuItem(text = { Text("$m minutes") }, onClick = { open = false; SleepTimer.minutes(m) }) }
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

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(state: PlayerState, source: DocSource, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.activity() }
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val inPip by Pip.active
    var controls by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    var fit by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var menu by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f) }
    val loadSubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val p = state.player
        val item = p.currentMediaItem ?: return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment.orEmpty().lowercase()
        val mime = when { name.endsWith(".vtt") -> "text/vtt"; name.endsWith(".ass") || name.endsWith(".ssa") -> "text/x-ssa"; else -> "application/x-subrip" }
        val withSubs = item.buildUpon().setSubtitleConfigurations(item.localConfiguration?.subtitleConfigurations.orEmpty() + MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build()).build()
        val at = p.currentPosition
        p.replaceMediaItem(p.currentMediaItemIndex, withSubs)
        p.seekTo(at)
        state.setSubtitles(true)
    }

    // Keep the screen on while watching; put brightness, rotation and PiP back when leaving.
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Pip.onLeave = { if (state.player.isPlaying && activity != null) Pip.enter(activity, state.video.width, state.video.height) }
        onDispose {
            Pip.onLeave = null
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // Videos don't keep playing once you leave them.
            state.player.pause()
        }
    }
    BackHandler { onBack() }
    LaunchedEffect(controls, state.playing) { if (controls && state.playing) { delay(3500); controls = false } }
    LaunchedEffect(feedback) { if (feedback != null) { delay(900); feedback = null } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; player = state.player; setKeepContentOnPlayerReset(true) } },
            update = { it.resizeMode = fit; it.player = state.player },
            modifier = Modifier.fillMaxSize(),
        )
        if (!inPip) {
            // Gestures: tap shows controls, double-tap a side skips 10 s, drag sideways seeks,
            // drag up and down on the left for brightness and on the right for volume.
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controls = !controls },
                            onDoubleTap = { o ->
                                if (o.x < w / 2) { state.seekBy(-10_000); feedback = Icons.Rounded.Replay10 to "−10 s" }
                                else { state.seekBy(10_000); feedback = Icons.Rounded.Forward10 to "+10 s" }
                            },
                        )
                    }
                    .pointerInput(Unit) {
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
                                    feedback = Icons.Rounded.Forward10 to "${formatTime(seekTo)} / ${formatTime(state.duration)}"
                                }
                                2 -> {
                                    brightness = (brightness - drag.y / h).coerceIn(0.01f, 1f)
                                    activity?.window?.let { win -> win.attributes = win.attributes.apply { screenBrightness = brightness } }
                                    feedback = Icons.Rounded.BrightnessMedium to "${(brightness * 100).toInt()}%"
                                }
                                3 -> {
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    volumeAcc -= drag.y / h * max * 1.5f
                                    val steps = volumeAcc.toInt()
                                    if (steps != 0) {
                                        volumeAcc -= steps
                                        val v = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                    }
                                    feedback = Icons.AutoMirrored.Rounded.VolumeUp to "${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} / $max"
                                }
                            }
                        }
                    },
            )
            feedback?.let { (icon, text) ->
                Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(Alignment.Center)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null)
                        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        IconButton(onClick = onBack, colors = white) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        Text(state.title.ifEmpty { source.name }, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (state.hasSubtitles) state.setSubtitles(!state.subtitlesOn) else loadSubs.launch(arrayOf("*/*")) }, colors = white) {
                            Icon(if (state.hasSubtitles && state.subtitlesOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled, if (state.hasSubtitles) "Subtitles" else "Load subtitles")
                        }
                        IconButton(onClick = { activity?.let { Pip.enter(it, state.video.width, state.video.height) } }, colors = white) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture in picture") }
                        Box {
                            IconButton(onClick = { menu = true }, colors = white) { Icon(Icons.Rounded.MoreVert, "More") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (fit == AspectRatioFrameLayout.RESIZE_MODE_FIT) "Fill the screen" else "Fit to screen") }, leadingIcon = { Icon(Icons.Rounded.AspectRatio, null) }, onClick = {
                                    menu = false; fit = if (fit == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                })
                                DropdownMenuItem(text = { Text("Rotate") }, leadingIcon = { Icon(Icons.Rounded.ScreenRotation, null) }, onClick = {
                                    menu = false
                                    activity?.requestedOrientation = if (activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                })
                                speeds.forEach { s -> DropdownMenuItem(text = { Text(if (s == 1f) "Normal speed" else "$s× speed") }, leadingIcon = { Icon(Icons.Rounded.Speed, null) }, onClick = { menu = false; state.player.setPlaybackSpeed(s) }) }
                                DropdownMenuItem(text = { Text("Load subtitles…") }, leadingIcon = { Icon(Icons.Rounded.ClosedCaption, null) }, onClick = { menu = false; loadSubs.launch(arrayOf("*/*")) })
                                DropdownMenuItem(text = { Text("Trim, crop or convert") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; shareSource(context, source) })
                                DropdownMenuItem(text = { Text("Open with…") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) }, onClick = { menu = false; openElsewhere(context, source) })
                            }
                        }
                    }
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        if (state.count > 1) IconButton(onClick = { state.player.seekToPrevious() }, colors = white, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                        FilledIconButton(onClick = { state.toggle() }, modifier = Modifier.size(76.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.92f), contentColor = Color.Black)) {
                            Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.playing) "Pause" else "Play", Modifier.size(40.dp))
                        }
                        if (state.count > 1) IconButton(onClick = { state.player.seekToNext() }, colors = white, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp)) }
                    }
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        SeekBar(state, onLight = true)
                        if (state.speed != 1f) Text("${state.speed}× speed", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        state.error?.let { ErrorNote(it) }
    }
}

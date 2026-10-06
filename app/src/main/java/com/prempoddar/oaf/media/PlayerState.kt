package com.prempoddar.oaf.media

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

/** Connects to the app's playback service; null until connected. */
@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future) }
    }
    return controller
}

/** What the player screens draw, kept current by listening to the player. */
@Stable
class PlayerState(val player: Player) {
    var playing by mutableStateOf(player.isPlaying); internal set
    var buffering by mutableStateOf(false); internal set
    var metadata by mutableStateOf(player.mediaMetadata); internal set
    var position by mutableLongStateOf(0L); internal set
    var duration by mutableLongStateOf(0L); internal set
    var index by mutableIntStateOf(player.currentMediaItemIndex); internal set
    var count by mutableIntStateOf(player.mediaItemCount); internal set
    var speed by mutableFloatStateOf(player.playbackParameters.speed); internal set
    var repeat by mutableIntStateOf(player.repeatMode); internal set
    var shuffle by mutableStateOf(player.shuffleModeEnabled); internal set
    var video by mutableStateOf(player.videoSize); internal set
    var hasSubtitles by mutableStateOf(false); internal set
    var subtitlesOn by mutableStateOf(true); internal set
    var error by mutableStateOf<String?>(null); internal set

    val title: String get() = metadata.title?.toString() ?: player.currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
    val artist: String? get() = (metadata.artist ?: metadata.albumArtist)?.toString()

    internal fun refresh() {
        playing = player.isPlaying
        buffering = player.playbackState == Player.STATE_BUFFERING
        metadata = player.mediaMetadata
        position = player.currentPosition.coerceAtLeast(0)
        duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
        index = player.currentMediaItemIndex
        count = player.mediaItemCount
        speed = player.playbackParameters.speed
        repeat = player.repeatMode
        shuffle = player.shuffleModeEnabled
        video = player.videoSize
        hasSubtitles = player.currentTracks.containsType(C.TRACK_TYPE_TEXT)
        subtitlesOn = C.TRACK_TYPE_TEXT !in player.trackSelectionParameters.disabledTrackTypes
    }

    fun toggle() = if (player.isPlaying) player.pause() else {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(player.currentMediaItemIndex, 0)
        player.play()
    }

    fun seekBy(ms: Long) = player.seekTo((player.currentPosition + ms).coerceIn(0, duration.takeIf { it > 0 } ?: Long.MAX_VALUE))

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSubtitles(on: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on).build()
    }
}

@Composable
fun rememberPlayerState(player: Player): PlayerState {
    val state = remember(player) { PlayerState(player) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = state.refresh()
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                state.error = error.localizedMessage ?: "This file can't be played"
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { state.metadata = mediaMetadata }
            override fun onVideoSizeChanged(videoSize: VideoSize) { state.video = videoSize }
            override fun onTracksChanged(tracks: Tracks) { state.hasSubtitles = tracks.containsType(C.TRACK_TYPE_TEXT) }
        }
        player.addListener(listener)
        state.refresh()
        onDispose { player.removeListener(listener) }
    }
    // The position isn't an event; read it often while playing.
    LaunchedEffect(player, state.playing) {
        while (true) {
            state.position = player.currentPosition.coerceAtLeast(0)
            player.duration.takeIf { it != C.TIME_UNSET }?.let { state.duration = it }
            delay(if (state.playing) 250 else 1000)
        }
    }
    return state
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun Context.mainExecutor() = ContextCompat.getMainExecutor(this)

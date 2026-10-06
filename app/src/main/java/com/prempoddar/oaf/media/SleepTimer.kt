package com.prempoddar.oaf.media

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Pauses playback after a while, or when the current track ends. */
object SleepTimer {
    sealed interface State {
        data object Off : State
        data class At(val endsAt: Long) : State
        data object EndOfTrack : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var player: Player? = null
    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            if (_state.value == State.EndOfTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                player?.pause()
                cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_state.value == State.EndOfTrack && playbackState == Player.STATE_ENDED) cancel()
        }
    }

    fun attach(p: Player) {
        player?.removeListener(listener)
        player = p
        p.addListener(listener)
    }

    fun minutes(m: Int) {
        job?.cancel()
        val end = System.currentTimeMillis() + m * 60_000L
        _state.value = State.At(end)
        job = scope.launch {
            delay(m * 60_000L)
            player?.pause()
            _state.value = State.Off
        }
    }

    fun endOfTrack() {
        job?.cancel()
        _state.value = State.EndOfTrack
    }

    fun cancel() {
        job?.cancel()
        _state.value = State.Off
    }
}

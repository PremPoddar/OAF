package com.prempoddar.oaf.ui.browser

import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.files.Clip
import com.prempoddar.oaf.files.ClipMode
import com.prempoddar.oaf.files.Transfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cut, copy and paste for the whole app, so a clip survives moving between folders, categories
 * and search. Also runs any long file job with a progress bar that every screen shows.
 */
class Clipboard {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    data class Job(val label: String, val progress: Transfer.Progress? = null)
    private val _job = MutableStateFlow<Job?>(null)
    /** The file job running now, if any. */
    val job: StateFlow<Job?> = _job.asStateFlow()

    /** A name clash waiting for the user's choice. */
    class Question(val existing: File, val answer: CompletableDeferred<Pair<Transfer.Resolution, Boolean>>)
    private val _question = MutableStateFlow<Question?>(null)
    val question: StateFlow<Question?> = _question.asStateFlow()

    /** Bumped after every job so open folders reload. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private var running: kotlinx.coroutines.Job? = null

    fun copy(files: List<File>) { _clip.value = Clip(ClipMode.COPY, files) }
    fun cut(files: List<File>) { _clip.value = Clip(ClipMode.CUT, files) }
    fun clear() { _clip.value = null }

    fun paste(into: File, onError: (String) -> Unit) {
        val clip = _clip.value ?: return
        val transfer = Transfer(
            ask = { existing ->
                val q = Question(existing, CompletableDeferred())
                _question.value = q
                try { q.answer.await() } finally { _question.value = null }
            },
            discard = { AppGraph.bin.delete(it) },
        )
        run(if (clip.mode == ClipMode.COPY) "Copying" else "Moving", onError) {
            transfer.run(clip, into) { p -> _job.value = Job(_job.value?.label ?: "", p) }
            if (clip.mode == ClipMode.CUT) _clip.value = null
        }
    }

    /** Runs [block] off the main thread with the shared progress bar. */
    fun run(label: String, onError: (String) -> Unit, block: suspend () -> Unit) {
        if (running?.isActive == true) {
            onError("Wait for “${_job.value?.label}” to finish")
            return
        }
        _job.value = Job(label)
        running = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: "That didn't work"
                launch(Dispatchers.Main) { onError(message) }
            } finally {
                _job.value = null
                _changes.value++
            }
        }
    }

    fun cancel() {
        running?.cancel()
        _question.value?.answer?.cancel()
    }

    /** Tells open folders to reload after a change made elsewhere. */
    fun changed() { _changes.value++ }
}

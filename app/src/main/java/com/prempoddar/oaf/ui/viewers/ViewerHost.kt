package com.prempoddar.oaf.ui.viewers

import androidx.compose.material.icons.rounded.Lock
import com.prempoddar.oaf.ui.rememberChrome
import com.prempoddar.oaf.ui.ViewerScaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.engine.Engines
import com.prempoddar.oaf.io.DocSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Works out what the file is, remembers it in Recents, and hands it to the right viewer. */
@Composable
fun ViewerHost(source: DocSource, onBack: () -> Unit, onOpen: (DocSource) -> Unit) {
    val context = LocalContext.current
    // Null while checking; a failure means the file can't be read at all.
    val detected by produceState<Result<DocSource>?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching { source.open(context).close() }.fold(
                onSuccess = { Result.success(runCatching { source.detect(context) }.getOrElse { source }) },
                onFailure = { Result.failure(it) },
            )
        }
    }
    val failed = detected?.exceptionOrNull()
    if (failed != null) {
        // Usually a file from another app whose permission has run out, opened from Recents.
        LaunchedEffect(source) { AppGraph.prefs.update { st -> st.copy(recents = st.recents.filter { it.uri != source.uri.toString() }) } }
        ViewerScaffold(title = source.name, chrome = rememberChrome(), autoHide = false, onBack = onBack) {
            com.prempoddar.oaf.ui.design.EmptyState(
                androidx.compose.material.icons.Icons.Rounded.Lock, "OAF can't open this file any more",
                if (failed is SecurityException) "The app it came from no longer lets OAF read it. Open it again from that app." else (failed.message ?: "The file may have been moved or deleted."),
                Modifier.fillMaxSize(),
            )
        }
        return
    }
    val s = detected?.getOrNull()
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LaunchedEffect(s) { AppGraph.prefs.opened(s.uri.toString(), s.name, s.kind.name) }
    // Where another engine can open the file too, a button in the title bar swaps between them.
    var other by remember(s) { mutableStateOf(false) }
    val alternative = com.prempoddar.oaf.engine.WebEngine.pageFor(s.kind)
    val switch = alternative?.let { (_, label) ->
        com.prempoddar.oaf.ui.EngineSwitch(if (other) "OAF's own viewer" else label) { other = !other }
    }
    androidx.compose.runtime.CompositionLocalProvider(com.prempoddar.oaf.ui.LocalEngineSwitch provides switch) {
        if (other) com.prempoddar.oaf.engine.WebEngine.viewer(s.kind)!!(s, onBack, onOpen)
        else Engines.viewerFor(s.kind)(s, onBack, onOpen)
    }
}

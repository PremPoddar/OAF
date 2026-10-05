package com.prempoddar.oaf.ui.viewers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
    val detected by produceState<DocSource?>(null, source) {
        value = withContext(Dispatchers.IO) { runCatching { source.detect(context) }.getOrElse { source } }
    }
    val s = detected
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LaunchedEffect(s) { AppGraph.prefs.opened(s.uri.toString(), s.name, s.kind.name) }
    Engines.viewerFor(s.kind)(s, onBack, onOpen)
}

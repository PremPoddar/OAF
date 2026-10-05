package com.prempoddar.oaf.ui.viewers.pdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prempoddar.oaf.pdf.Box
import com.prempoddar.oaf.pdf.PageGeom
import com.prempoddar.oaf.pdf.PageText
import com.prempoddar.oaf.pdf.PdfAnnot
import com.prempoddar.oaf.pdf.PdfAnnots
import com.prempoddar.oaf.pdf.PdfContent
import com.prempoddar.oaf.pdf.PdfElement
import com.prempoddar.oaf.pdf.PdfField
import com.prempoddar.oaf.pdf.PdfForms
import com.prempoddar.oaf.pdf.PdfSession
import com.prempoddar.oaf.pdf.PdfText
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

internal enum class PdfMode { READ, ANNOTATE, FILL, EDIT, PAGES }

internal enum class Tool { NONE, PEN, MARKER, NOTE, RECT, ELLIPSE, ARROW, ERASER, ADD_TEXT, ADD_IMAGE, PLACE_SIGNATURE }

/** A page's shape as shown, in points. */
internal data class PageInfo(val width: Float, val height: Float)

/** Text picked by long-pressing: [anchor] where it started, [focus] where the finger is. */
internal data class TextSelection(val page: Int, val anchor: Int, val focus: Int) {
    val range get() = minOf(anchor, focus)..maxOf(anchor, focus)
}

internal data class SearchHit(val page: Int, val boxes: List<Box>)

/**
 * Everything the PDF screen shares. Document work runs on one worker thread, because PDFBox
 * documents aren't safe to use from two at once; [version] bumps after each change so pages redraw.
 */
internal class PdfState(val session: PdfSession, private val scope: CoroutineScope, val renderer: RenderHost, private val onError: (String) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "pdf-work") }.asCoroutineDispatcher()

    var version by mutableIntStateOf(0)
        private set
    var pages by mutableStateOf<List<PageInfo>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var unsaved by mutableIntStateOf(0)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    var mode by mutableStateOf(PdfMode.READ)
    var tool by mutableStateOf(Tool.NONE)
    var color by mutableIntStateOf(0x2F5D8C)
    var strokeWidth by mutableFloatStateOf(2f)
    var selection by mutableStateOf<TextSelection?>(null)
    var picked by mutableStateOf<Pair<Int, PdfElement>?>(null)
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
    var hit by mutableIntStateOf(-1)
    var fields by mutableStateOf<List<PdfField>>(emptyList())
        private set

    private val texts = HashMap<Int, PageText>()
    private val elements = HashMap<Int, List<PdfElement>>()
    private val annots = HashMap<Int, List<PdfAnnot>>()

    suspend fun start() {
        refresh()
    }

    private suspend fun refresh() {
        val (infos, f) = withContext(worker) {
            texts.clear(); elements.clear(); annots.clear()
            val d = session.doc
            (0 until d.numberOfPages).map { PageGeom(d.getPage(it)).let { g -> PageInfo(g.displayWidth, g.displayHeight) } } to
                runCatching { PdfForms.fields(d) }.getOrDefault(emptyList())
        }
        renderer.open(session.file)
        pages = infos
        fields = f
        unsaved = session.unsaved
        canUndo = session.canUndo
        canRedo = session.canRedo
        version++
    }

    suspend fun text(page: Int): PageText = withContext(worker) { texts.getOrPut(page) { runCatching { PdfText.page(session.doc, page) }.getOrElse { PageText("", emptyList()) } } }
    suspend fun elements(page: Int): List<PdfElement> = withContext(worker) { elements.getOrPut(page) { PdfContent.elements(session.doc, page) } }
    suspend fun annots(page: Int): List<PdfAnnot> = withContext(worker) { annots.getOrPut(page) { runCatching { PdfAnnots.list(session.doc, page) }.getOrDefault(emptyList()) } }

    /** Applies a change to the document, keeps it as a new version and redraws. */
    fun change(what: String, block: (PDDocument) -> Any?) {
        scope.launch {
            busy = true
            val ok = withContext(worker) { runCatching { session.mutate(block) } }
            ok.exceptionOrNull()?.let { onError("Couldn't $what: ${it.message ?: it.javaClass.simpleName}") }
            selection = null; picked = null; hits = emptyList(); hit = -1
            refresh()
            busy = false
        }
    }

    fun undo() = scope.launch { busy = true; withContext(worker) { session.undo() }; refresh(); busy = false }
    fun redo() = scope.launch { busy = true; withContext(worker) { session.redo() }; refresh(); busy = false }

    /** Searches every page, page by page, showing hits as they're found. */
    fun search(query: String, onFirst: (SearchHit) -> Unit) {
        hits = emptyList(); hit = -1
        if (query.length < 2) return
        scope.launch {
            val found = ArrayList<SearchHit>()
            for (p in pages.indices) {
                val t = text(p)
                t.find(query).forEach { r -> found += SearchHit(p, t.lineBoxes(r)) }
                if (found.isNotEmpty() && hit < 0) { hits = found.toList(); hit = 0; onFirst(found[0]) }
            }
            hits = found
        }
    }

    suspend fun <T> work(block: (PdfSession) -> T): T = withContext(worker) { block(session) }

    fun markSaved() { unsaved = 0 }

    fun close() {
        renderer.close()
        worker.close()
        session.close()
    }
}

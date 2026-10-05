package com.prempoddar.oaf.engine

import androidx.compose.runtime.Composable
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.viewers.ArchiveScreen
import com.prempoddar.oaf.ui.viewers.DocScreen
import com.prempoddar.oaf.ui.viewers.HexViewer
import com.prempoddar.oaf.ui.viewers.ImageViewer
import com.prempoddar.oaf.ui.viewers.MediaViewer
import com.prempoddar.oaf.ui.viewers.SheetScreen
import com.prempoddar.oaf.ui.viewers.pdf.PdfScreen
import com.prempoddar.oaf.ui.viewers.TextScreen

typealias Viewer = @Composable (source: DocSource, onBack: () -> Unit, onOpen: (DocSource) -> Unit) -> Unit

/**
 * Which engine opens which kind of file. Every format goes through here, so a different engine
 * (for example a full LibreOffice build) can take over formats without the rest of the app changing.
 */
interface DocumentEngine {
    val name: String
    fun viewer(kind: FileKind): Viewer?
}

/** OAF's own viewers and editors. */
object NativeEngine : DocumentEngine {
    override val name = "OAF"

    override fun viewer(kind: FileKind): Viewer? = when (kind) {
        FileKind.IMAGE, FileKind.SVG -> { s, back, _ -> ImageViewer(s, back) }
        FileKind.AUDIO, FileKind.VIDEO -> { s, back, _ -> MediaViewer(s, back) }
        FileKind.TEXT, FileKind.CODE, FileKind.MARKDOWN, FileKind.HTML, FileKind.JSON, FileKind.XML, FileKind.YAML -> { s, back, _ -> TextScreen(s, back) }
        FileKind.ZIP, FileKind.SEVEN_Z, FileKind.TAR, FileKind.GZIP, FileKind.BZIP2, FileKind.XZ, FileKind.RAR -> { s, back, open -> ArchiveScreen(s, back, open) }
        FileKind.PDF -> { s, back, _ -> PdfScreen(s, back) }
        FileKind.DOCX, FileKind.DOC, FileKind.ODT, FileKind.RTF -> { s, back, _ -> DocScreen(s, back) }
        FileKind.XLSX, FileKind.XLS, FileKind.ODS, FileKind.CSV, FileKind.TSV -> { s, back, _ -> SheetScreen(s, back) }
        else -> null
    }
}

object Engines {
    /** Checked in order; the first engine with a viewer wins. */
    private val engines = mutableListOf<DocumentEngine>(NativeEngine)

    /** A plugged-in engine is asked before the native one. */
    fun register(engine: DocumentEngine) = engines.add(0, engine)

    fun viewerFor(kind: FileKind): Viewer =
        engines.firstNotNullOfOrNull { it.viewer(kind) } ?: { s, back, _ -> HexViewer(s, back) }
}

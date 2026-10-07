package com.prempoddar.oaf.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.prempoddar.oaf.format.Detector
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.familyColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** A file or folder as the browser shows it; [kind] is null for folders. */
class Entry(
    val file: File, val kind: FileKind?, val size: Long, val modified: Long, val children: Int,
    /** Android's media index entry, when known: its thumbnails are ready-made and quick. */
    val media: android.net.Uri? = null,
) {
    val isDir get() = kind == null
}

fun kindOf(f: File): FileKind = Detector.detect(f.name, null, ByteArray(0)).kind

/** Reads what the list needs in one go, off the main thread. */
fun entryOf(f: File): Entry {
    val dir = f.isDirectory
    return Entry(f, if (dir) null else kindOf(f), if (dir) 0 else f.length(), f.lastModified(), if (dir) f.list()?.size ?: 0 else 0)
}

private val thumbs = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}
private val thumbDispatcher = Dispatchers.IO.limitedParallelism(4)

/** A small preview for pictures, videos, songs with cover art and app packages; null otherwise. */
@Composable
fun rememberThumbnail(file: File, kind: FileKind?, px: Int = 256, media: android.net.Uri? = null): ImageBitmap? {
    val context = LocalContext.current
    val canPreview = kind == FileKind.IMAGE || kind == FileKind.VIDEO || kind == FileKind.AUDIO || kind == FileKind.APK
    val bitmap by produceState<ImageBitmap?>(null, file.path, file.lastModified()) {
        if (!canPreview) return@produceState
        val key = file.path + ":" + file.lastModified() + ":" + px
        thumbs.get(key)?.let { value = it.asImageBitmap(); return@produceState }
        value = withContext(thumbDispatcher) {
            runCatching {
                media?.let { runCatching { context.contentResolver.loadThumbnail(it, Size(px, px), null) }.getOrNull() } ?: loadThumb(context, file, kind!!, px)
            }.getOrNull()?.also { thumbs.put(key, it) }?.asImageBitmap()
        }
    }
    return bitmap
}

private fun loadThumb(context: Context, file: File, kind: FileKind, px: Int): Bitmap? {
    val size = Size(px, px)
    return when (kind) {
        FileKind.IMAGE -> ThumbnailUtils.createImageThumbnail(file, size, null)
        FileKind.VIDEO -> ThumbnailUtils.createVideoThumbnail(file, size, null)
        FileKind.AUDIO -> ThumbnailUtils.createAudioThumbnail(file, size, null)
        FileKind.APK -> context.packageManager.getPackageArchiveInfo(file.path, 0)?.applicationInfo?.let { ai ->
            ai.sourceDir = file.path
            ai.publicSourceDir = file.path
            ai.loadIcon(context.packageManager).toBitmap(px / 2, px / 2)
        }
        else -> null
    }
}

/** The coloured tile beside a name: a folder, a preview, or the file type. */
@Composable
fun KindMark(kind: FileKind?, file: File? = null, size: Dp = 44.dp, media: android.net.Uri? = null) {
    val paper = LocalPaper.current
    val color = if (kind == null) MaterialTheme.colorScheme.primary else familyColor(kind, paper.dark)
    val thumb = if (file != null) rememberThumbnail(file, kind, media = media) else null
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.medium)
            .background(if (kind == null) MaterialTheme.colorScheme.primaryContainer else color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            thumb != null -> Image(thumb, null, contentScale = if (kind == FileKind.APK) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (kind == FileKind.APK) Modifier.padding(6.dp) else Modifier))
            kind == null -> Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            else -> Text(extLabel(kind), style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

fun extLabel(k: FileKind) = when (k) {
    FileKind.PDF -> "PDF"; FileKind.DOCX, FileKind.DOC -> "DOC"; FileKind.ODT -> "ODT"; FileKind.RTF -> "RTF"; FileKind.EPUB -> "EPUB"
    FileKind.XLSX, FileKind.XLS -> "XLS"; FileKind.ODS -> "ODS"; FileKind.CSV, FileKind.TSV -> "CSV"
    FileKind.PPTX, FileKind.PPT -> "PPT"; FileKind.ODP -> "ODP"; FileKind.MARKDOWN -> "MD"; FileKind.HTML -> "HTML"
    FileKind.JSON -> "{ }"; FileKind.XML -> "XML"; FileKind.YAML -> "YML"; FileKind.CODE -> "</>"; FileKind.TEXT -> "TXT"
    FileKind.IMAGE -> "IMG"; FileKind.SVG -> "SVG"; FileKind.AUDIO -> "♪"; FileKind.VIDEO -> "▶"
    FileKind.ZIP, FileKind.SEVEN_Z, FileKind.TAR, FileKind.GZIP, FileKind.BZIP2, FileKind.XZ, FileKind.RAR -> "ZIP"
    FileKind.APK -> "APK"; FileKind.FONT -> "Aa"; FileKind.UNKNOWN -> "•••"
}

private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

fun describe(e: Entry, extra: String? = null): String = listOfNotNull(
    if (e.isDir) "${e.children} item${if (e.children == 1) "" else "s"}" else humanSize(e.size),
    dateFormat.format(Date(e.modified)),
    extra,
).joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(e: Entry, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, extra: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            KindMark(e.kind, e.file, media = e.media)
            if (selected) Icon(
                Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).size(18.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surface),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(remember(e, extra) { describe(e, extra) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!selecting) trailing()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCell(e: Entry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val thumb = rememberThumbnail(e.file, e.kind, 384, e.media)
    Column(
        Modifier.padding(4.dp).clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large), contentAlignment = Alignment.Center) {
            if (thumb != null && e.kind != FileKind.APK) {
                Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (e.kind == FileKind.VIDEO) Icon(Icons.Rounded.PlayCircle, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(36.dp))
            } else {
                KindMark(e.kind, e.file, 64.dp)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        // Always two lines tall, so every card in the grid is the same size; long names end in "…".
        Text(
            e.file.name, style = MaterialTheme.typography.bodyMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

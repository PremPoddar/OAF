package com.prempoddar.oaf.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.caverock.androidsvg.SVG
import com.prempoddar.oaf.data.ThumbType
import com.prempoddar.oaf.doc.Block
import com.prempoddar.oaf.doc.Converters
import com.prempoddar.oaf.doc.DocBody
import com.prempoddar.oaf.doc.Docx
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.sheet.Csv
import com.prempoddar.oaf.sheet.Ods
import com.prempoddar.oaf.sheet.Value
import com.prempoddar.oaf.sheet.Workbook
import com.prempoddar.oaf.sheet.Xls
import com.prempoddar.oaf.sheet.Xlsx
import com.prempoddar.oaf.slides.Slides
import com.prempoddar.oaf.ui.viewers.drawSlide
import com.prempoddar.oaf.xml.Xml
import com.prempoddar.oaf.xml.descendants
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.zip.ZipFile

/** Which preview setting covers [kind], or null when it has no preview at all. */
fun thumbTypeOf(kind: FileKind?): ThumbType? = when (kind) {
    FileKind.IMAGE, FileKind.SVG -> ThumbType.PICTURES
    FileKind.VIDEO -> ThumbType.VIDEOS
    FileKind.AUDIO -> ThumbType.SONGS
    FileKind.APK -> ThumbType.APPS
    FileKind.PDF -> ThumbType.PDF
    FileKind.DOCX, FileKind.DOC, FileKind.ODT, FileKind.RTF -> ThumbType.DOCUMENTS
    FileKind.XLSX, FileKind.XLS, FileKind.ODS, FileKind.CSV, FileKind.TSV -> ThumbType.SHEETS
    FileKind.PPTX, FileKind.PPT, FileKind.ODP -> ThumbType.SLIDES
    FileKind.EPUB -> ThumbType.BOOKS
    FileKind.TEXT, FileKind.CODE, FileKind.MARKDOWN, FileKind.HTML, FileKind.JSON, FileKind.XML, FileKind.YAML -> ThumbType.TEXT
    FileKind.FONT -> ThumbType.FONTS
    else -> null
}

/** Previews shown as the top of a page, rather than centred. */
fun isPage(kind: FileKind?) = thumbTypeOf(kind).let { it == ThumbType.PDF || it == ThumbType.DOCUMENTS || it == ThumbType.SHEETS || it == ThumbType.TEXT }

/**
 * Previews of documents, drawn from OAF's own readers: the first page of a PDF or Word document,
 * the top corner of a spreadsheet, the first slide, a book's cover, a font's letters. Reading a
 * document takes a moment, so each preview is kept on disk until the file changes.
 */
object DocThumbs {
    /** Bigger files are left with their type label: reading them for a small picture isn't worth it. */
    private const val MAX_BYTES = 20L * 1024 * 1024

    fun load(context: Context, file: File, kind: FileKind, px: Int): Bitmap? {
        if (file.length() > MAX_BYTES && kind != FileKind.PDF) return null
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val cached = File(dir, "${(file.path + ":" + file.lastModified() + ":" + file.length() + ":" + px).hashCode().toUInt()}.webp")
        if (cached.isFile) BitmapFactory.decodeFile(cached.path)?.let { return it }
        val made = draw(context, file, kind, px) ?: return null
        runCatching { FileOutputStream(cached).use { made.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it) } }
        return made
    }

    private fun draw(context: Context, file: File, kind: FileKind, px: Int): Bitmap? = when (thumbTypeOf(kind)) {
        ThumbType.PDF -> pdf(file, px)
        ThumbType.DOCUMENTS -> document(file, kind, px)
        ThumbType.SHEETS -> sheet(file, kind, px)
        ThumbType.SLIDES -> slide(file, kind, px)
        ThumbType.BOOKS -> cover(file, px)
        ThumbType.TEXT -> text(file, px)
        ThumbType.FONTS -> font(file, px)
        ThumbType.PICTURES -> if (kind == FileKind.SVG) svg(file, px) else null
        else -> null
    }

    // ---------------------------------------------------------------- pages

    /** A4's shape: documents are drawn on a page this tall for its width. */
    private const val PAGE = 1.414f

    private fun page(px: Int): Pair<Bitmap, Canvas> {
        val w = (px / PAGE).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, px, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        return b to Canvas(b)
    }

    private fun pdf(file: File, px: Int): Bitmap? =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { p ->
                    // As wide as the preview is tall, so a page fills a square tile across.
                    val w = px
                    val h = (w.toFloat() * p.height / p.width).toInt().coerceIn(1, px * 2)
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    p.render(b, null, Matrix().apply { setScale(w.toFloat() / p.width, h.toFloat() / p.height) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    b
                }
            }
        }

    private fun document(file: File, kind: FileKind, px: Int): Bitmap {
        val body: DocBody = when (kind) {
            FileKind.DOC -> Converters.doc(file)
            FileKind.ODT -> Converters.odt(file)
            FileKind.RTF -> Converters.rtf(file)
            else -> Docx.read(file).body
        }
        val (b, c) = page(px)
        // Drawn a little larger than a real page (as if 450 points wide, not 595), so the
        // words still read at a glance in a small tile.
        val scale = b.width / 450f
        val margin = 36f * scale
        val width = (b.width - 2 * margin).toInt().coerceAtLeast(1)
        var y = margin
        val paras = body.blocks.flatMap { bl -> when (bl) { is Block.P -> listOf(bl.para); is Block.T -> bl.rows.flatten().flatMap { it.paras } } }
        for (p in paras) {
            if (y > b.height - margin) break
            val style = p.props.styleId.orEmpty()
            val baseSize = when {
                style.equals("Title", true) -> 26f
                style.startsWith("Heading1", true) -> 18f
                style.startsWith("Heading2", true) -> 15f
                style.startsWith("Heading", true) -> 13f
                style.equals("Subtitle", true) -> 15f
                else -> 11f
            }
            val text = SpannableStringBuilder()
            for (r in p.runs) {
                if (r.text.isEmpty()) continue
                val start = text.length
                text.append(r.text)
                val end = text.length
                val bold = r.style.bold ?: style.startsWith("Heading", true) || style.equals("Title", true)
                val italic = r.style.italic == true
                if (bold || italic) text.setSpan(StyleSpan(when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; else -> Typeface.ITALIC }), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                r.style.size?.let { text.setSpan(AbsoluteSizeSpan(((it) * scale).toInt().coerceAtLeast(1)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                r.style.color?.let { text.setSpan(ForegroundColorSpan(Color.BLACK or it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202124.toInt(); textSize = baseSize * scale }
            val align = when (p.props.align) { "center" -> Layout.Alignment.ALIGN_CENTER; "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align).build()
            c.save(); c.translate(margin, y); layout.draw(c); c.restore()
            y += layout.height + 6f * scale
        }
        return b
    }

    // ---------------------------------------------------------------- spreadsheets

    private fun sheet(file: File, kind: FileKind, px: Int): Bitmap {
        val wb: Workbook = when (kind) {
            FileKind.XLSX -> Xlsx.read(file).workbook
            FileKind.XLS -> Xls.read(file)
            FileKind.ODS -> Ods.read(file)
            FileKind.TSV -> Csv.read(file, tsv = true).first
            else -> Csv.read(file).first
        }
        runCatching { wb.recalculate() }
        val s = wb.sheets.firstOrNull { it.cells.isNotEmpty() } ?: wb.sheets.first()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        val c = Canvas(b)
        val rows = 12; val cols = 4
        val rowH = px.toFloat() / rows; val colW = px.toFloat() / cols
        val fill = Paint()
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = rowH * 0.55f }
        val grid = Paint().apply { color = 0xFFDADCE0.toInt(); strokeWidth = (px / 256f).coerceAtLeast(1f) }
        for (r in 0 until rows) for (col in 0 until cols) {
            val cell = s[r, col] ?: continue
            val l = col * colW; val t = r * rowH
            cell.style.fill?.let { fill.color = Color.BLACK or it; c.drawRect(l, t, l + colW, t + rowH, fill) }
            val shown = cell.value.takeIf { it != Value.Empty }?.display() ?: cell.input.takeUnless { it.startsWith("=") } ?: continue
            text.color = cell.style.color?.let { Color.BLACK or it } ?: 0xFF202124.toInt()
            text.typeface = Typeface.create(Typeface.DEFAULT, if (cell.style.bold) Typeface.BOLD else Typeface.NORMAL)
            val number = cell.value is Value.Num
            val pad = colW * 0.08f
            val fitted = android.text.TextUtils.ellipsize(shown, text, colW - 2 * pad, android.text.TextUtils.TruncateAt.END).toString()
            val x = if (number) l + colW - pad - text.measureText(fitted) else l + pad
            c.save(); c.clipRect(l, t, l + colW, t + rowH)
            c.drawText(fitted, x, t + rowH * 0.72f, text)
            c.restore()
        }
        for (r in 1 until rows) c.drawLine(0f, r * rowH, px.toFloat(), r * rowH, grid)
        for (col in 1 until cols) c.drawLine(col * colW, 0f, col * colW, px.toFloat(), grid)
        return b
    }

    // ---------------------------------------------------------------- slides, books, text, fonts

    private fun slide(file: File, kind: FileKind, px: Int): Bitmap? {
        val deck = when (kind) { FileKind.PPT -> Slides.ppt(file); FileKind.ODP -> Slides.odp(file); else -> Slides.pptx(file) }
        return deck.use { d ->
            val slide = d.slides.firstOrNull() ?: return null
            val ratio = (d.width / d.height).takeIf { it.isFinite() && it > 0f } ?: (4f / 3f)
            val w = px; val h = (px / ratio).toInt().coerceAtLeast(1)
            val pictures = (slide.shapes.filterIsInstance<com.prempoddar.oaf.slides.SlideShape.Picture>().map { it.path } + listOfNotNull(slide.backgroundImage)).distinct()
                .mapNotNull { path -> d.image(path)?.let { bytes -> decodeSmall(bytes, w)?.let { path to it } } }.toMap()
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawSlide(Canvas(b), slide, w / d.width, pictures, w.toFloat(), h.toFloat())
            b
        }
    }

    /** The book's cover picture: the one its package names, else the first picture in it. */
    private fun cover(file: File, px: Int): Bitmap? = ZipFile(file).use { zip ->
        fun bytes(path: String) = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        val container = bytes("META-INF/container.xml")?.let { Xml.parse(it) } ?: return null
        val opfPath = container.documentElement.descendants("rootfile").firstOrNull()?.getAttribute("full-path") ?: return null
        val opf = bytes(opfPath)?.let { Xml.parse(it).documentElement } ?: return null
        val base = opfPath.substringBeforeLast('/', "")
        val items = opf.descendants("item")
        val coverId = opf.descendants("meta").firstOrNull { it.getAttribute("name") == "cover" }?.getAttribute("content")
        val item = items.firstOrNull { it.getAttribute("properties").split(' ').contains("cover-image") }
            ?: items.firstOrNull { coverId != null && it.getAttribute("id") == coverId }
            ?: items.firstOrNull { it.getAttribute("media-type").startsWith("image/") }
            ?: return null
        val href = URLDecoder.decode(item.getAttribute("href"), "UTF-8")
        val path = if (base.isEmpty()) href else "$base/$href"
        bytes(path)?.let { decodeSmall(it, px) }
    }

    private fun text(file: File, px: Int): Bitmap {
        val head = file.inputStream().use { it.readNBytes(4096) }
        val lines = String(head, Charsets.UTF_8).replace("\t", "    ").lines().take(40)
        val (b, c) = page(px)
        val margin = b.width * 0.08f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3C4043.toInt(); typeface = Typeface.MONOSPACE; textSize = b.width / 22f }
        var y = margin + paint.textSize
        for (line in lines) {
            if (y > b.height - margin) break
            c.drawText(line, 0, line.length.coerceAtMost(60), margin, y, paint)
            y += paint.textSize * 1.35f
        }
        return b
    }

    private fun font(file: File, px: Int): Bitmap? {
        val face = runCatching { Typeface.createFromFile(file) }.getOrNull() ?: return null
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; color = 0xFF202124.toInt(); textSize = px * 0.42f; textAlign = Paint.Align.CENTER }
        Canvas(b).drawText("Aa", px / 2f, px / 2f - (paint.ascent() + paint.descent()) / 2, paint)
        return b
    }

    private fun svg(file: File, px: Int): Bitmap? {
        val svg = file.inputStream().use { SVG.getFromInputStream(it) }
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        svg.setDocumentWidth("100%"); svg.setDocumentHeight("100%")
        svg.renderToCanvas(Canvas(b), RectF(0f, 0f, px.toFloat(), px.toFloat()))
        return b
    }

    private fun decodeSmall(bytes: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull()
    }
}

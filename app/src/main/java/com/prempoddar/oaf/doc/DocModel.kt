package com.prempoddar.oaf.doc

import org.w3c.dom.Element
import java.util.concurrent.atomic.AtomicLong

/**
 * Direct character formatting. Null means "not set here": the value comes from the paragraph's
 * style, so a document's own look survives editing.
 */
data class RunStyle(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    /** RGB, no alpha. */
    val color: Int? = null,
    /** A Word highlight name such as "yellow". */
    val highlight: String? = null,
    /** Points. */
    val size: Float? = null,
    val font: String? = null,
    /** "superscript" or "subscript". */
    val vert: String? = null,
) {
    /** [over] wins wherever it sets something. */
    infix fun under(over: RunStyle) = RunStyle(
        over.bold ?: bold, over.italic ?: italic, over.underline ?: underline, over.strike ?: strike,
        over.color ?: color, over.highlight ?: highlight, over.size ?: size, over.font ?: font, over.vert ?: vert,
    )

    companion object { val NONE = RunStyle() }
}

/**
 * A stretch of text with one formatting. Objects (pictures, fields, breaks Word draws itself) are a
 * single [OBJ] character carrying the original XML, so they keep their place as text is edited.
 */
data class Run(
    val text: String,
    val style: RunStyle = RunStyle.NONE,
    /** The run's original properties, kept so formatting OAF doesn't model (fonts, spacing…) survives. */
    val rPr: Element? = null,
    val obj: Element? = null,
    /** Part path of a picture, when [obj] is one. */
    val image: String? = null,
    val imageSize: Pair<Float, Float>? = null,
    /** The w:hyperlink this run sat in. */
    val link: Element? = null,
    val linkTarget: String? = null,
    /** A character style (w:rStyle) the run uses. */
    val charStyle: String? = null,
) {
    val isObject get() = obj != null
}

data class ParaProps(
    val styleId: String? = null,
    /** "left", "center", "right" or "both". */
    val align: String? = null,
    val numId: String? = null,
    val level: Int = 0,
)

private val ids = AtomicLong(1)
fun newId() = ids.getAndIncrement()

/** A paragraph. Immutable: edits make a new one with the same [id]. */
data class Para(
    val id: Long,
    val runs: List<Run>,
    val props: ParaProps = ParaProps(),
    /** The w:p this came from; null for paragraphs made while editing. */
    val el: Element? = null,
    /** The original paragraph properties, kept for spacing, indents, borders and the rest. */
    val pPr: Element? = null,
    val dirty: Boolean = false,
) {
    val text: String by lazy { runs.joinToString("") { it.text } }
    val length get() = text.length
    val images get() = runs.filter { it.image != null }

    /** The run whose formatting text typed at [at] takes: the one before the caret, as word processors do. */
    private fun templateRun(at: Int): Run? {
        var pos = 0
        for (r in runs) {
            val end = pos + r.text.length
            if (!r.isObject && at - 1 in pos until end) return r
            pos = end
        }
        return runs.firstOrNull { !it.isObject }
    }

    fun styleAt(at: Int): RunStyle = templateRun(at)?.style ?: RunStyle.NONE

    /** Whether [at] is strictly inside a hyperlink, so typing there extends the link. */
    private fun insideLink(at: Int): Boolean {
        var pos = 0
        for (r in runs) { if (at > pos && at < pos + r.text.length) return r.link != null; pos += r.text.length }
        return false
    }

    /** Applies a text field change: whatever differs between the current text and [new] is replaced. */
    fun edit(new: String, typing: RunStyle? = null): Para {
        val old = text
        if (old == new) return this
        var p = 0
        while (p < old.length && p < new.length && old[p] == new[p]) p++
        var s = 0
        while (s < old.length - p && s < new.length - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        val inserted = new.substring(p, new.length - s)
        val src = templateRun(p)
        val link = if (insideLink(p)) src else null
        val trimmed = delete(p, old.length - s)
        if (inserted.isEmpty()) return trimmed
        return trimmed.insert(p, Run(inserted, typing ?: src?.style ?: RunStyle.NONE, src?.rPr, link = link?.link, linkTarget = link?.linkTarget, charStyle = src?.charStyle))
    }

    /** Removes characters [from]..[to]. */
    fun delete(from: Int, to: Int): Para {
        if (from >= to) return this
        val out = ArrayList<Run>()
        var pos = 0
        for (r in runs) {
            val end = pos + r.text.length
            when {
                end <= from || pos >= to -> out += r
                r.isObject -> Unit
                else -> {
                    val keep = r.text.substring(0, (from - pos).coerceAtLeast(0)) + r.text.substring((to - pos).coerceAtMost(r.text.length))
                    if (keep.isNotEmpty()) out += r.copy(text = keep)
                }
            }
            pos = end
        }
        return copy(runs = normalize(out), dirty = true)
    }

    fun insert(at: Int, run: Run): Para {
        if (run.text.isEmpty()) return this
        val (a, b) = splitRuns(at)
        return copy(runs = normalize(a + run + b), dirty = true)
    }

    /** Runs before and after [at], splitting the run it falls inside. */
    fun splitRuns(at: Int): Pair<List<Run>, List<Run>> {
        val a = ArrayList<Run>(); val b = ArrayList<Run>()
        var pos = 0
        for (r in runs) {
            val end = pos + r.text.length
            when {
                end <= at -> a += r
                pos >= at -> b += r
                else -> { a += r.copy(text = r.text.substring(0, at - pos)); b += r.copy(text = r.text.substring(at - pos)) }
            }
            pos = end
        }
        return a to b
    }

    /** Changes the formatting of [from]..[to]. */
    fun format(from: Int, to: Int, change: (RunStyle) -> RunStyle): Para {
        if (from >= to) return this
        val (head, rest) = splitRuns(from)
        val (mid, tail) = copy(runs = rest).splitRuns(to - from)
        return copy(runs = normalize(head + mid.map { if (it.isObject) it else it.copy(style = change(it.style)) } + tail), dirty = true)
    }

    /** Splits at [at]: this keeps the start; the returned new paragraph gets the rest. */
    fun split(at: Int): Pair<Para, Para> {
        val (a, b) = splitRuns(at)
        val style = styleAt(at)
        val first = copy(runs = normalize(a), dirty = true)
        val nextProps = if (props.styleId?.let { isHeadingLike(it) } == true && b.all { it.text.isBlank() }) props.copy(styleId = null) else props
        val second = Para(newId(), normalize(b).ifEmpty { listOf(Run("", style, a.lastOrNull { !it.isObject }?.rPr)) }, nextProps, pPr = pPr, dirty = true)
        return first to second
    }

    fun merge(next: Para): Para = copy(runs = normalize(runs + next.runs), dirty = true)

    companion object {
        /** The character standing for an object; zero width, so it never shows. */
        const val OBJ = '⁠'

        fun isHeadingLike(styleId: String) = styleId.startsWith("Heading", true) || styleId.equals("Title", true) || styleId.equals("Subtitle", true)

        /** Joins neighbours that look the same and drops empty runs (keeping one so the paragraph has a style). */
        fun normalize(runs: List<Run>): List<Run> {
            val out = ArrayList<Run>()
            for (r in runs) {
                if (r.text.isEmpty() && !r.isObject) continue
                val last = out.lastOrNull()
                if (last != null && !last.isObject && !r.isObject && last.style == r.style && last.link === r.link && last.linkTarget == r.linkTarget && last.rPr === r.rPr && last.charStyle == r.charStyle) {
                    out[out.lastIndex] = last.copy(text = last.text + r.text)
                } else out += r
            }
            if (out.isEmpty()) runs.firstOrNull()?.let { out += it.copy(text = "") }
            return out
        }
    }
}

class Cell(val el: Element?, val paras: List<Para>)

sealed interface Block {
    data class P(val para: Para) : Block
    /** A table; [widths] are column widths in twips when the file gives them. */
    data class T(val el: Element?, val rows: List<List<Cell>>, val widths: List<Int>) : Block
}

/** Everything that shows: the body's blocks in order. Immutable, so undo is keeping old ones. */
data class DocBody(val blocks: List<Block>) {
    /** Every paragraph, tables included, in reading order. */
    val paragraphs: List<Para> by lazy {
        blocks.flatMap { b -> when (b) { is Block.P -> listOf(b.para); is Block.T -> b.rows.flatten().flatMap { it.paras } } }
    }

    fun find(id: Long): Para? = paragraphs.firstOrNull { it.id == id }

    /** Replaces paragraph [id] with [with] (none removes it; several insert). */
    fun replace(id: Long, with: List<Para>): DocBody = DocBody(blocks.flatMap { b ->
        when (b) {
            is Block.P -> if (b.para.id == id) with.map { Block.P(it) } else listOf(b)
            is Block.T -> listOf(b.copy(rows = b.rows.map { row ->
                row.map { c -> if (c.paras.any { it.id == id }) Cell(c.el, c.paras.flatMap { if (it.id == id) with else listOf(it) }.ifEmpty { listOf(Para(newId(), listOf(Run("")))) }) else c }
            }))
        }
    })

    fun update(id: Long, change: (Para) -> Para): DocBody = find(id)?.let { replace(id, listOf(change(it))) } ?: this

    /** The paragraph before [id] in the same container (body or table cell), if any. */
    fun previous(id: Long): Para? {
        val top = blocks.indexOfFirst { it is Block.P && it.para.id == id }
        if (top >= 0) return (blocks.getOrNull(top - 1) as? Block.P)?.para
        blocks.filterIsInstance<Block.T>().forEach { t -> t.rows.flatten().forEach { c -> val i = c.paras.indexOfFirst { it.id == id }; if (i >= 0) return c.paras.getOrNull(i - 1) } }
        return null
    }
}

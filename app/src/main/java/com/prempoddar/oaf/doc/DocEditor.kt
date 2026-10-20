package com.prempoddar.oaf.doc

/**
 * Edits to a document body, with undo. Bodies are immutable, so undo is a stack of earlier ones.
 * Plain Kotlin so it's tested without a device; [version] bumps on every change.
 */
class DocEditor(start: DocBody) {
    var body = start
        private set
    var version = 0
        private set
    var unsaved = 0
        private set

    private val undoStack = ArrayDeque<DocBody>()
    private val redoStack = ArrayDeque<DocBody>()
    /** Typing into one paragraph is one undo step until a word ends or another paragraph is touched. */
    private var typingIn: Long? = null

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** After saving: carry on from the saved body (it knows its new XML) without an undo step. */
    fun saved(as_: DocBody) { body = as_; unsaved = 0; version++ }

    private fun commit(next: DocBody, typing: Long? = null) {
        if (next === body) return
        if (typing == null || typing != typingIn) {
            undoStack.addLast(body)
            if (undoStack.size > 200) undoStack.removeFirst()
        }
        typingIn = typing
        redoStack.clear()
        body = next
        unsaved++
        version++
    }

    fun undo() { val prev = undoStack.removeLastOrNull() ?: return; redoStack.addLast(body); body = prev; typingIn = null; unsaved++; version++ }
    fun redo() { val next = redoStack.removeLastOrNull() ?: return; undoStack.addLast(body); body = next; typingIn = null; unsaved++; version++ }

    /**
     * The text of paragraph [id] became [text]. New lines in it (typed or pasted) start new
     * paragraphs. Returns where the caret belongs: the paragraph and offset after the last new line,
     * or null when it stays put.
     */
    fun change(id: Long, text: String, typing: RunStyle? = null): Pair<Long, Int>? {
        val para = body.find(id) ?: return null
        val edited = para.edit(text, typing)
        if (!text.contains('\n')) {
            val wordEnd = text.length > para.length && text.getOrNull(text.length - 1)?.isWhitespace() == true
            commit(body.replace(id, listOf(edited)), typing = if (wordEnd) null else id)
            if (wordEnd) typingIn = null
            return null
        }
        val out = ArrayList<Para>()
        var rest = edited
        while (true) {
            val i = rest.text.indexOf('\n')
            if (i < 0) break
            val (a, b) = rest.split(i)
            out += a
            rest = b.delete(0, 1).let { n -> if (n.text.isBlank() && a.props.styleId?.let(Para::isHeadingLike) == true) n.copy(props = n.props.copy(styleId = null)) else n }
        }
        out += rest
        commit(body.replace(id, out))
        // The caret goes where the last line break was typed: the start of what followed it.
        val tailLength = text.length - text.lastIndexOf('\n') - 1
        val suffix = commonSuffix(para.text, text)
        return rest.id to (tailLength - suffix).coerceIn(0, rest.length)
    }

    private fun commonSuffix(a: String, b: String): Int {
        var s = 0
        while (s < a.length && s < b.length && a[a.length - 1 - s] == b[b.length - 1 - s]) s++
        return s
    }

    /**
     * Backspace at the start of paragraph [id]. A list item or heading first turns into plain text,
     * as word processors do; otherwise it joins the paragraph before. Returns the caret position.
     */
    fun backspaceAtStart(id: Long): Pair<Long, Int>? {
        val para = body.find(id) ?: return null
        if (para.props.numId != null && para.props.numId != "0") {
            commit(body.replace(id, listOf(para.copy(props = para.props.copy(numId = null, level = 0), dirty = true))))
            return id to 0
        }
        val prev = body.previous(id) ?: run {
            if (para.props.styleId == null) return null
            commit(body.replace(id, listOf(para.copy(props = para.props.copy(styleId = null), dirty = true))))
            return id to 0
        }
        // A picture-only paragraph before is removed rather than joined.
        if (prev.text.all { it == Para.OBJ } && prev.text.isNotEmpty()) {
            commit(body.replace(prev.id, emptyList()))
            return id to 0
        }
        val merged = prev.merge(para)
        commit(body.replace(id, emptyList()).replace(prev.id, listOf(merged)))
        return prev.id to prev.length
    }

    fun format(id: Long, from: Int, to: Int, change: (RunStyle) -> RunStyle) {
        val p = body.find(id) ?: return
        commit(body.replace(id, listOf(p.format(from, to, change))))
    }

    fun setParagraph(id: Long, change: (ParaProps) -> ParaProps) {
        val p = body.find(id) ?: return
        val props = change(p.props)
        if (props != p.props) commit(body.replace(id, listOf(p.copy(props = props, dirty = true))))
    }

    /** Turns a list of the given kind on or off; a different kind of list switches over. */
    fun toggleList(id: Long, bullet: Boolean, numbering: Numbering) = setParagraph(id) { props ->
        val isOn = props.numId != null && props.numId != "0" && numbering.isBullet(props.numId) == bullet
        if (isOn) props.copy(numId = null, level = 0) else props.copy(numId = if (bullet) Numbering.BULLET else Numbering.DECIMAL)
    }

    fun indent(id: Long, delta: Int) = setParagraph(id) { props ->
        if (props.numId == null || props.numId == "0") props else props.copy(level = (props.level + delta).coerceIn(0, 8))
    }
    private fun emptyPara() = Para(newId(), listOf(Run("")))

    /** The table holding paragraph [id], and the row it's in. */
    fun tableOf(id: Long): Pair<Block.T, Int>? {
        for (b in body.blocks) if (b is Block.T) {
            val r = b.rows.indexOfFirst { row -> row.any { c -> c.paras.any { it.id == id } } }
            if (r >= 0) return b to r
        }
        return null
    }

    /**
     * Puts an empty [rows] by [cols] table after paragraph [id] (or after the table [id] is in).
     * A paragraph follows it when it would otherwise end the document, so there's somewhere to type
     * after it. Returns the first cell's paragraph, for the caret.
     */
    fun insertTable(id: Long, rows: Int, cols: Int): Long? {
        val at = body.blocks.indexOfFirst { b -> b is Block.P && b.para.id == id || b is Block.T && b.rows.flatten().any { c -> c.paras.any { it.id == id } } }
        if (at < 0 || rows < 1 || cols < 1) return null
        val table = Block.T(null, List(rows) { List(cols) { Cell(null, listOf(emptyPara())) } }, emptyList())
        val after = if (at == body.blocks.lastIndex) listOf(table, Block.P(emptyPara())) else listOf(table)
        commit(DocBody(body.blocks.take(at + 1) + after + body.blocks.drop(at + 1)))
        return table.rows[0][0].paras[0].id
    }

    /** Adds an empty row above or below the one paragraph [id] is in, as many cells wide. */
    fun addRow(id: Long, below: Boolean): Long? {
        val (t, r) = tableOf(id) ?: return null
        val row = List(t.rows[r].size) { Cell(null, listOf(emptyPara())) }
        val at = if (below) r + 1 else r
        commit(swap(t, t.copy(rows = t.rows.take(at) + listOf(row) + t.rows.drop(at))))
        return row[0].paras[0].id
    }

    /** Removes the row paragraph [id] is in; the last row takes the whole table with it. */
    fun deleteRow(id: Long) {
        val (t, r) = tableOf(id) ?: return
        if (t.rows.size == 1) deleteTable(id) else commit(swap(t, t.copy(rows = t.rows.filterIndexed { i, _ -> i != r })))
    }

    fun deleteTable(id: Long) {
        val (t, _) = tableOf(id) ?: return
        val blocks = body.blocks.filter { it !== t }
        commit(DocBody(blocks.ifEmpty { listOf(Block.P(emptyPara())) }))
    }

    private fun swap(old: Block.T, new: Block.T) = DocBody(body.blocks.map { if (it === old) new else it })

    /**
     * Replaces every [find] with [with] in one undo step, keeping each match's formatting. Pictures
     * and other objects are never part of a match. Returns how many were replaced.
     */
    fun replaceAll(find: String, with: String, matchCase: Boolean = false): Int {
        if (find.isEmpty()) return 0
        val pattern = Regex(Regex.escape(find), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
        var count = 0
        val changed = HashMap<Long, Para>()
        for (p in body.paragraphs) {
            val hits = pattern.findAll(p.text).filter { m -> Para.OBJ !in m.value }.toList()
            if (hits.isEmpty()) continue
            var q = p
            for (m in hits.asReversed()) {
                val style = q.styleAt(m.range.first + 1)
                val src = q.runs.let { runs -> var pos = 0; runs.firstOrNull { r -> val hit = !r.isObject && m.range.first in pos until pos + r.text.length; pos += r.text.length; hit } }
                q = q.delete(m.range.first, m.range.last + 1)
                q = q.insert(m.range.first, Run(with, style, src?.rPr, link = src?.link, linkTarget = src?.linkTarget, charStyle = src?.charStyle))
                if (with.isEmpty()) q = q.copy(dirty = true)
            }
            changed[p.id] = q
            count += hits.size
        }
        if (count == 0) return 0
        var next = body
        changed.forEach { (id, para) -> next = next.replace(id, listOf(para)) }
        commit(next)
        return count
    }
}

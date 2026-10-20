package com.prempoddar.oaf.doc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocEditorTest {
    private fun para(text: String, props: ParaProps = ParaProps()) = Para(newId(), listOf(Run(text)), props)
    private fun editor(vararg paras: Para) = DocEditor(DocBody(paras.map { Block.P(it) }))
    private val DocEditor.texts get() = body.paragraphs.map { it.text }

    @Test fun typingIsUndoneAWordAtATime() {
        val a = para("")
        val e = editor(a)
        "hello world".forEachIndexed { i, _ -> e.change(a.id, "hello world".substring(0, i + 1)) }
        assertThat(e.texts).containsExactly("hello world")
        e.undo()
        assertThat(e.texts).containsExactly("hello ")
        e.undo()
        assertThat(e.texts).containsExactly("hello")
        e.undo()
        assertThat(e.texts).containsExactly("")
        e.redo(); e.redo()
        assertThat(e.texts).containsExactly("hello ")
    }

    @Test fun enterSplitsAndHeadingsEndWithPlainText() {
        val h = para("Title", ParaProps(styleId = "Heading1"))
        val e = editor(h)
        val caret = e.change(h.id, "Title\n")
        assertThat(e.texts).containsExactly("Title", "").inOrder()
        assertThat(e.body.paragraphs[1].props.styleId).isNull()
        assertThat(caret).isEqualTo(e.body.paragraphs[1].id to 0)
        // Enter in the middle keeps the style on both halves.
        val mid = e.change(h.id, "Ti\ntle")!!
        assertThat(e.texts).containsExactly("Ti", "tle", "").inOrder()
        assertThat(e.body.paragraphs[1].props.styleId).isEqualTo("Heading1")
        assertThat(mid.second).isEqualTo(0)
    }

    @Test fun pastingLinesMakesParagraphs() {
        val p = para("ab")
        val e = editor(p)
        val caret = e.change(p.id, "a1\n2\n3b")!!
        assertThat(e.texts).containsExactly("a1", "2", "3b").inOrder()
        assertThat(caret.second).isEqualTo(1)
        e.undo()
        assertThat(e.texts).containsExactly("ab")
    }

    @Test fun backspaceAtStartLeavesListsThenJoins() {
        val a = para("one")
        val b = para("two", ParaProps(numId = Numbering.BULLET))
        val e = editor(a, b)
        assertThat(e.backspaceAtStart(b.id)).isEqualTo(b.id to 0)
        assertThat(e.body.paragraphs[1].props.numId).isNull()
        assertThat(e.backspaceAtStart(b.id)).isEqualTo(a.id to 3)
        assertThat(e.texts).containsExactly("onetwo")
        assertThat(e.backspaceAtStart(a.id)).isNull()
    }

    @Test fun listsToggleAndIndent() {
        val a = para("item")
        val e = editor(a)
        val n = Numbering(emptyMap())
        e.toggleList(a.id, bullet = true, numbering = n)
        assertThat(e.body.paragraphs[0].props.numId).isEqualTo(Numbering.BULLET)
        e.toggleList(a.id, bullet = false, numbering = n)
        assertThat(e.body.paragraphs[0].props.numId).isEqualTo(Numbering.DECIMAL)
        e.indent(a.id, 1)
        assertThat(e.body.paragraphs[0].props.level).isEqualTo(1)
        e.toggleList(a.id, bullet = false, numbering = n)
        assertThat(e.body.paragraphs[0].props).isEqualTo(ParaProps())
    }

    @Test fun formattingASelection() {
        val a = para("make this bold")
        val e = editor(a)
        e.format(a.id, 10, 14) { it.copy(bold = true) }
        val runs = e.body.paragraphs[0].runs
        assertThat(runs.map { it.text }).containsExactly("make this ", "bold").inOrder()
        assertThat(runs[1].style.bold).isTrue()
        // Typing right after the bold word carries on in bold.
        e.change(a.id, "make this bolder")
        assertThat(e.body.paragraphs[0].runs.last().text).isEqualTo("bolder")
    }

    @Test fun replaceAllKeepsFormattingAndIsOneUndo() {
        val bold = RunStyle(bold = true)
        val a = Para(newId(), listOf(Run("Cat and "), Run("cat", bold), Run(" sat")))
        val b = para("no match")
        val e = editor(a, b)
        assertThat(e.replaceAll("cat", "dog")).isEqualTo(2)
        assertThat(e.texts).containsExactly("dog and dog sat", "no match").inOrder()
        val p = e.body.paragraphs[0]
        assertThat(p.runs.first { it.text == "dog" && it.style == bold }).isNotNull()
        e.undo()
        assertThat(e.texts).containsExactly("Cat and cat sat", "no match").inOrder()
        assertThat(e.replaceAll("cat", "", matchCase = true)).isEqualTo(1)
        assertThat(e.texts[0]).isEqualTo("Cat and  sat")
    }

    @Test fun tablesAreInsertedAndRowsAddedAndRemoved() {
        val a = para("Only")
        val e = editor(a)
        val first = e.insertTable(a.id, 2, 3)!!
        val t = e.body.blocks[1] as Block.T
        assertThat(t.rows.map { it.size }).containsExactly(3, 3)
        // Something to type in after a table at the end.
        assertThat(e.body.blocks.last()).isInstanceOf(Block.P::class.java)
        e.change(first, "x")
        val added = e.addRow(first, below = false)!!
        assertThat((e.body.blocks[1] as Block.T).rows.size).isEqualTo(3)
        assertThat((e.body.blocks[1] as Block.T).rows[1][0].paras[0].text).isEqualTo("x")
        e.deleteRow(added)
        e.deleteRow(first)
        val last = (e.body.blocks[1] as Block.T).rows.single()[0].paras[0].id
        e.deleteRow(last)
        assertThat(e.body.blocks.none { it is Block.T }).isTrue()
    }
}

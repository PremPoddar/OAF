package com.prempoddar.oaf.text

import com.google.common.truth.Truth.assertThat
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.format.Lang
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TextTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tokens(h: Highlighter, line: String, mode: Highlighter.Mode = Highlighter.Mode.NORMAL) =
        h.line(line, mode).first.map { line.substring(it.start, it.end) to it.token }

    @Test fun kotlinKeywordsStringsCommentsNumbers() {
        val h = Highlighter.forLang(Lang.KOTLIN)
        val t = tokens(h, "val x: Int = 42 // answer \"no\"")
        assertThat(t).contains("val" to Token.KEYWORD)
        assertThat(t).contains("Int" to Token.TYPE)
        assertThat(t).contains("42" to Token.NUMBER)
        assertThat(t.last()).isEqualTo("// answer \"no\"" to Token.COMMENT)
        assertThat(tokens(h, "@Composable fun a() = \"hi \\\" there\"")).contains("\"hi \\\" there\"" to Token.STRING)
    }

    @Test fun blockCommentsCarryAcrossLines() {
        val h = Highlighter.forLang(Lang.JAVA)
        val (spans, mode) = h.line("int a; /* start", Highlighter.Mode.NORMAL)
        assertThat(mode).isEqualTo(Highlighter.Mode.BLOCK_COMMENT)
        assertThat(spans.last().token).isEqualTo(Token.COMMENT)
        val (next, after) = h.line("still comment */ return 1;", mode)
        assertThat(after).isEqualTo(Highlighter.Mode.NORMAL)
        assertThat(next.first().token).isEqualTo(Token.COMMENT)
        assertThat(tokens(h, "still comment */ return 1;", mode)).contains("return" to Token.KEYWORD)
    }

    @Test fun pythonTripleStrings() {
        val h = Highlighter.forLang(Lang.PYTHON)
        val (_, mode) = h.line("doc = \"\"\"begins here", Highlighter.Mode.NORMAL)
        assertThat(mode).isEqualTo(Highlighter.Mode.TRIPLE_STRING)
        assertThat(h.line("and ends\"\"\" + x", mode).second).isEqualTo(Highlighter.Mode.NORMAL)
    }

    @Test fun sqlIsCaseInsensitive() {
        assertThat(tokens(Highlighter.forLang(Lang.SQL), "SELECT name FROM users -- all")).contains("SELECT" to Token.KEYWORD)
    }

    @Test fun jsonKeysAreNotValues() {
        val h = Highlighter.forKind(FileKind.JSON, Lang.PLAIN)!!
        val t = tokens(h, """  "name": "Asha", "age": 31, "ok": true""")
        assertThat(t).contains("\"name\"" to Token.KEY)
        assertThat(t).contains("\"Asha\"" to Token.STRING)
        assertThat(t).contains("31" to Token.NUMBER)
        assertThat(t).contains("true" to Token.LITERAL)
    }

    @Test fun markupTagsAttributesAndComments() {
        val h = Highlighter.forKind(FileKind.HTML, Lang.PLAIN)!!
        val t = tokens(h, """<a href="x.html">link &amp; more</a> <!-- note""")
        assertThat(t).contains("<a" to Token.TAG)
        assertThat(t).contains("href" to Token.ATTR)
        assertThat(t).contains("\"x.html\"" to Token.STRING)
        assertThat(t).contains("&amp;" to Token.LITERAL)
        assertThat(h.line("""<!-- note""", Highlighter.Mode.NORMAL).second).isEqualTo(Highlighter.Mode.BLOCK_COMMENT)
    }

    @Test fun prettyJsonAndInvalidJson() {
        assertThat(Pretty.json("""{"a":[1,2],"b":{"c":null}}""")).isEqualTo("{\n  \"a\": [\n    1,\n    2\n  ],\n  \"b\": {\n    \"c\": null\n  }\n}")
        assertThat(Pretty.json("{nope")).isNull()
    }

    @Test fun prettyXml() {
        val out = Pretty.xml("""<?xml version="1.0"?><root><item id="1">One</item><group><x a="b>c"/></group><!-- c --></root>""")!!
        assertThat(out).isEqualTo(
            """
            <?xml version="1.0"?>
            <root>
              <item id="1">One</item>
              <group>
                <x a="b>c"/>
              </group>
              <!-- c -->
            </root>
            """.trimIndent(),
        )
        assertThat(Pretty.xml("not xml")).isNull()
    }

    @Test fun encodingsAndLineEndingsRoundTrip() {
        val text = "Grüße\nzweite Zeile"
        listOf(
            TextFormat(Charsets.UTF_8, ByteArray(0), false),
            TextFormat(Charsets.UTF_8, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), true),
            TextFormat(Charsets.UTF_16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte()), false),
            TextFormat(java.nio.charset.Charset.forName("windows-1252"), ByteArray(0), true),
        ).forEach { fmt ->
            val bytes = TextLoad.encode(text, fmt)
            val detected = TextLoad.format(bytes.copyOf(minOf(bytes.size, 4096)))
            assertThat(detected.charset).isEqualTo(fmt.charset)
            assertThat(detected.crlf).isEqualTo(fmt.crlf)
            val f = tmp.newFile().apply { writeBytes(bytes) }
            assertThat(TextLoad.read(f, detected)).isEqualTo(text)
        }
    }

    @Test fun lineIndexReadsAnyLine() {
        val f = tmp.newFile().apply { writeText((1..10_000).joinToString("\n") { "line $it" } + "\n") }
        LineIndex(f, TextLoad.format(f.readBytes().copyOf(4096))).use { idx ->
            assertThat(idx.count).isEqualTo(10_000)
            assertThat(idx.line(0)).isEqualTo("line 1")
            assertThat(idx.line(9_999)).isEqualTo("line 10000")
            assertThat(idx.line(4_321)).isEqualTo("line 4322")
        }
    }
}

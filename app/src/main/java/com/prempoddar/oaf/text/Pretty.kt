package com.prempoddar.oaf.text

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Re-indents JSON and XML for reading. Returns null when the input isn't valid. */
object Pretty {
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun json(text: String): String? = runCatching {
        json.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))
    }.getOrNull()

    fun xml(text: String): String? {
        val out = StringBuilder()
        var depth = 0
        var i = 0
        val src = text.trim()
        if (!src.startsWith("<")) return null
        fun line(s: String) { if (s.isNotBlank()) out.append("  ".repeat(depth.coerceAtLeast(0))).append(s.trim()).append('\n') }
        while (i < src.length) {
            if (src[i] == '<') {
                val end = when {
                    src.startsWith("<!--", i) -> src.indexOf("-->", i).let { if (it < 0) return null else it + 3 }
                    src.startsWith("<![CDATA[", i) -> src.indexOf("]]>", i).let { if (it < 0) return null else it + 3 }
                    else -> tagEnd(src, i) ?: return null
                }
                val tag = src.substring(i, end)
                when {
                    tag.startsWith("</") -> { depth--; line(tag) }
                    tag.startsWith("<?") || tag.startsWith("<!") || tag.endsWith("/>") -> line(tag)
                    else -> {
                        // Keep short text content on the same line: <a>text</a>
                        val close = src.indexOf('<', end)
                        val closing = if (close >= 0) src.startsWith("</", close) else false
                        val inner = if (close >= 0) src.substring(end, close) else ""
                        if (closing && !inner.contains('\n') && inner.length < 80) {
                            val closeEnd = tagEnd(src, close) ?: return null
                            line(tag + inner.trim() + src.substring(close, closeEnd))
                            i = closeEnd
                            continue
                        }
                        line(tag); depth++
                    }
                }
                i = end
            } else {
                val next = src.indexOf('<', i).let { if (it < 0) src.length else it }
                line(src.substring(i, next))
                i = next
            }
        }
        return out.toString().trimEnd()
    }

    /** End of a tag, skipping '>' inside quoted attribute values. */
    private fun tagEnd(s: String, start: Int): Int? {
        var q: Char? = null
        for (j in start + 1 until s.length) {
            val c = s[j]
            if (q != null) { if (c == q) q = null; continue }
            if (c == '"' || c == '\'') q = c else if (c == '>') return j + 1
        }
        return null
    }
}

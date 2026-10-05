package com.prempoddar.oaf.text

import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.format.Lang

enum class Token { KEYWORD, TYPE, STRING, COMMENT, NUMBER, ANNOTATION, TAG, ATTR, LITERAL, KEY }

data class Span(val start: Int, val end: Int, val token: Token)

/**
 * A small line-at-a-time highlighter. It carries state between lines (inside a block comment or
 * a multi-line string), so any line can be coloured as soon as the lines before it have been seen.
 */
class Highlighter private constructor(private val spec: Spec) {

    /** Where the previous line left off. */
    enum class Mode { NORMAL, BLOCK_COMMENT, TRIPLE_STRING }

    data class Spec(
        val keywords: Set<String> = emptySet(),
        val types: Set<String> = emptySet(),
        val literals: Set<String> = setOf("true", "false", "null"),
        val lineComments: List<String> = emptyList(),
        val blockStart: String? = null,
        val blockEnd: String? = null,
        val quotes: String = "\"'",
        val triple: Boolean = false,
        val caseInsensitive: Boolean = false,
        val annotations: Char? = null,
        val markup: Boolean = false,
        val json: Boolean = false,
        val keyValue: Boolean = false,
    )

    fun line(text: String, start: Mode): Pair<List<Span>, Mode> {
        if (spec.markup) return markup(text, start)
        val out = ArrayList<Span>()
        var i = 0
        var mode = start
        val n = text.length
        while (i < n) {
            if (mode == Mode.BLOCK_COMMENT) {
                val end = text.indexOf(spec.blockEnd!!, i)
                if (end < 0) { out += Span(i, n, Token.COMMENT); return out to mode }
                out += Span(i, end + spec.blockEnd.length, Token.COMMENT)
                i = end + spec.blockEnd.length
                mode = Mode.NORMAL
                continue
            }
            if (mode == Mode.TRIPLE_STRING) {
                val end = text.indexOf("\"\"\"", i).takeIf { it >= 0 } ?: text.indexOf("'''", i)
                if (end < 0) { out += Span(i, n, Token.STRING); return out to mode }
                out += Span(i, end + 3, Token.STRING)
                i = end + 3
                mode = Mode.NORMAL
                continue
            }
            val c = text[i]
            // Comments.
            val lc = spec.lineComments.firstOrNull { text.startsWith(it, i) }
            if (lc != null) { out += Span(i, n, Token.COMMENT); return out to mode }
            if (spec.blockStart != null && text.startsWith(spec.blockStart, i)) { mode = Mode.BLOCK_COMMENT; i += spec.blockStart.length; out += Span(i - spec.blockStart.length, i, Token.COMMENT); continue }
            // Strings.
            if (spec.triple && (text.startsWith("\"\"\"", i) || text.startsWith("'''", i))) {
                val q = text.substring(i, i + 3)
                val end = text.indexOf(q, i + 3)
                if (end < 0) { out += Span(i, n, Token.STRING); return out to Mode.TRIPLE_STRING }
                out += Span(i, end + 3, Token.STRING); i = end + 3; continue
            }
            if (c in spec.quotes) {
                var j = i + 1
                while (j < n && text[j] != c) { if (text[j] == '\\') j++; j++ }
                val end = minOf(n, j + 1)
                // In JSON a string followed by ':' is a key.
                val isKey = spec.json && text.substring(end).trimStart().startsWith(":")
                val isYamlKey = spec.keyValue && text.substring(end).trimStart().startsWith(":")
                out += Span(i, end, if (isKey || isYamlKey) Token.KEY else Token.STRING)
                i = end; continue
            }
            if (c.isDigit() || (c == '.' && i + 1 < n && text[i + 1].isDigit())) {
                var j = i
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '_')) j++
                if (i == 0 || !text[i - 1].isLetter()) out += Span(i, j, Token.NUMBER)
                i = j; continue
            }
            if (spec.annotations != null && c == spec.annotations && i + 1 < n && text[i + 1].isLetter()) {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '.')) j++
                out += Span(i, j, Token.ANNOTATION); i = j; continue
            }
            if (c.isLetter() || c == '_' || c == '$') {
                var j = i
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '$')) j++
                val word = text.substring(i, j)
                val w = if (spec.caseInsensitive) word.lowercase() else word
                when {
                    spec.keyValue && text.substring(j).trimStart().startsWith(if (spec.json) ":" else ":") && out.none { it.start < i } && text.substring(0, i).isBlank() -> out += Span(i, j, Token.KEY)
                    spec.keyValue && text.substring(j).trimStart().startsWith("=") && text.substring(0, i).isBlank() -> out += Span(i, j, Token.KEY)
                    w in spec.keywords -> out += Span(i, j, Token.KEYWORD)
                    w in spec.literals -> out += Span(i, j, Token.LITERAL)
                    w in spec.types || (word[0].isUpperCase() && word.length > 1 && word.any { it.isLowerCase() } && spec.types.isNotEmpty()) -> out += Span(i, j, Token.TYPE)
                }
                i = j; continue
            }
            i++
        }
        return out to mode
    }

    /** HTML/XML/SVG: tags, attribute names, attribute values, comments. */
    private fun markup(text: String, start: Mode): Pair<List<Span>, Mode> {
        val out = ArrayList<Span>()
        var i = 0
        var mode = start
        val n = text.length
        while (i < n) {
            if (mode == Mode.BLOCK_COMMENT) {
                val end = text.indexOf("-->", i)
                if (end < 0) { out += Span(i, n, Token.COMMENT); return out to mode }
                out += Span(i, end + 3, Token.COMMENT); i = end + 3; mode = Mode.NORMAL; continue
            }
            if (text.startsWith("<!--", i)) { mode = Mode.BLOCK_COMMENT; continue }
            if (text[i] == '<') {
                var j = i + 1
                if (j < n && (text[j] == '/' || text[j] == '?' || text[j] == '!')) j++
                val nameStart = j
                while (j < n && (text[j].isLetterOrDigit() || text[j] in ":-_.")) j++
                out += Span(i, j, Token.TAG)
                // Attributes until '>'.
                while (j < n && text[j] != '>') {
                    when {
                        text[j].isLetter() -> {
                            val a = j
                            while (j < n && (text[j].isLetterOrDigit() || text[j] in ":-_.")) j++
                            out += Span(a, j, Token.ATTR)
                        }
                        text[j] == '"' || text[j] == '\'' -> {
                            val q = text[j]; val a = j; j++
                            while (j < n && text[j] != q) j++
                            j = minOf(n, j + 1)
                            out += Span(a, j, Token.STRING)
                        }
                        else -> j++
                    }
                }
                if (j < n) out += Span(j, j + 1, Token.TAG)
                if (nameStart == j) Unit
                i = j + 1; continue
            }
            if (text[i] == '&') {
                val end = text.indexOf(';', i)
                if (end in i + 1..i + 10) { out += Span(i, end + 1, Token.LITERAL); i = end + 1; continue }
            }
            i++
        }
        return out to mode
    }

    companion object {
        private val cKeywords = setOf("if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "goto", "sizeof", "typedef", "struct", "union", "enum", "const", "static", "extern", "volatile", "inline", "register", "auto", "signed", "unsigned")
        private val cTypes = setOf("int", "char", "float", "double", "long", "short", "void", "bool", "size_t", "uint8_t", "int32_t", "uint32_t", "int64_t", "uint64_t")
        private val javaish = setOf(
            "abstract", "as", "break", "case", "catch", "class", "companion", "const", "continue", "data", "default", "do", "else", "enum", "extends", "external",
            "final", "finally", "for", "fun", "if", "implements", "import", "in", "inline", "interface", "internal", "is", "lateinit", "new", "object", "open",
            "operator", "out", "override", "package", "private", "protected", "public", "return", "sealed", "static", "super", "suspend", "switch", "synchronized",
            "this", "throw", "throws", "try", "typealias", "val", "var", "void", "when", "where", "while", "instanceof", "native", "transient", "volatile", "assert",
            "record", "yield", "permits", "by", "init", "constructor", "get", "set", "reified", "crossinline", "noinline", "vararg", "tailrec", "infix", "value",
        )
        private val jsKeywords = setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "export", "extends", "finally",
            "for", "from", "function", "if", "import", "in", "instanceof", "let", "new", "of", "return", "static", "super", "switch", "this", "throw", "try",
            "typeof", "var", "void", "while", "with", "yield", "interface", "type", "enum", "implements", "private", "public", "protected", "readonly", "as",
            "declare", "namespace", "abstract", "keyof", "satisfies",
        )
        private val pyKeywords = setOf("and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield", "match", "case", "self")
        private val shKeywords = setOf("if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac", "in", "function", "return", "local", "export", "readonly", "echo", "exit", "set", "unset", "source", "alias", "cd", "shift")
        private val sqlKeywords = setOf("select", "from", "where", "and", "or", "not", "insert", "into", "values", "update", "set", "delete", "create", "table", "drop", "alter", "add", "index", "on", "join", "left", "right", "inner", "outer", "full", "group", "by", "order", "having", "limit", "offset", "as", "distinct", "union", "all", "primary", "key", "foreign", "references", "default", "unique", "view", "case", "when", "then", "else", "end", "is", "in", "like", "between", "exists", "begin", "commit", "rollback", "with", "returning")
        private val sqlTypes = setOf("int", "integer", "bigint", "smallint", "varchar", "char", "text", "boolean", "date", "timestamp", "float", "double", "decimal", "numeric", "blob", "real", "serial", "uuid", "json", "jsonb")
        private val goKeywords = setOf("break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map", "package", "range", "return", "select", "struct", "switch", "type", "var")
        private val rustKeywords = setOf("as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum", "extern", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super", "trait", "type", "unsafe", "use", "where", "while")
        private val swiftKeywords = setOf("associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func", "import", "init", "inout", "internal", "let", "open", "operator", "private", "protocol", "public", "static", "struct", "subscript", "typealias", "var", "break", "case", "continue", "default", "defer", "do", "else", "fallthrough", "for", "guard", "if", "in", "repeat", "return", "switch", "where", "while", "as", "catch", "is", "rethrows", "throw", "throws", "try", "self", "Self", "super", "async", "await", "some", "any")
        private val rubyKeywords = setOf("def", "end", "if", "elsif", "else", "unless", "while", "until", "for", "in", "do", "return", "class", "module", "self", "nil", "true", "false", "and", "or", "not", "begin", "rescue", "ensure", "yield", "case", "when", "then", "require", "attr_accessor", "puts", "lambda", "proc")
        private val luaKeywords = setOf("and", "break", "do", "else", "elseif", "end", "for", "function", "goto", "if", "in", "local", "not", "or", "repeat", "return", "then", "until", "while", "nil")

        fun forKind(kind: FileKind, lang: Lang): Highlighter? = when (kind) {
            FileKind.JSON -> Highlighter(Spec(json = true, quotes = "\""))
            FileKind.XML, FileKind.HTML, FileKind.SVG -> Highlighter(Spec(markup = true))
            FileKind.YAML -> Highlighter(Spec(lineComments = listOf("#"), keyValue = true, literals = setOf("true", "false", "null", "yes", "no", "on", "off", "~")))
            FileKind.CODE -> forLang(lang)
            else -> null
        }

        fun forLang(lang: Lang): Highlighter = Highlighter(
            when (lang) {
                Lang.KOTLIN, Lang.JAVA, Lang.SCALA, Lang.GRADLE, Lang.DART, Lang.CSHARP ->
                    Spec(javaish, setOf("String", "Int", "Long", "Boolean", "Double", "Float", "Unit", "Any", "List", "Map", "Set") + cTypes, lineComments = listOf("//"), blockStart = "/*", blockEnd = "*/", triple = lang == Lang.KOTLIN, annotations = '@')
                Lang.C, Lang.CPP -> Spec(cKeywords + setOf("class", "namespace", "public", "private", "protected", "template", "typename", "virtual", "new", "delete", "using", "nullptr", "this", "try", "catch", "throw", "operator", "friend", "constexpr", "noexcept", "override", "#include", "#define", "#ifdef", "#ifndef", "#endif", "#if", "#else", "#pragma"), cTypes + setOf("string", "vector", "std"), setOf("true", "false", "NULL", "nullptr"), listOf("//"), "/*", "*/")
                Lang.GO -> Spec(goKeywords, setOf("string", "int", "int64", "uint", "byte", "rune", "bool", "error", "float64", "any"), setOf("true", "false", "nil", "iota"), listOf("//"), "/*", "*/", quotes = "\"'`")
                Lang.RUST -> Spec(rustKeywords, setOf("i32", "i64", "u8", "u32", "u64", "usize", "f64", "bool", "str", "String", "Vec", "Option", "Result", "Box"), setOf("true", "false", "None", "Some", "Ok", "Err"), listOf("//"), "/*", "*/", annotations = '#')
                Lang.SWIFT -> Spec(swiftKeywords, setOf("Int", "String", "Double", "Bool", "Array", "Dictionary"), setOf("true", "false", "nil"), listOf("//"), "/*", "*/", triple = true, annotations = '@')
                Lang.JAVASCRIPT, Lang.TYPESCRIPT -> Spec(jsKeywords, setOf("string", "number", "boolean", "any", "unknown", "never", "object", "Promise", "Array"), setOf("true", "false", "null", "undefined", "NaN"), listOf("//"), "/*", "*/", quotes = "\"'`", annotations = '@')
                Lang.PHP -> Spec(jsKeywords + setOf("echo", "foreach", "endforeach", "elseif", "namespace", "use", "public", "private", "function", "array"), emptySet(), setOf("true", "false", "null"), listOf("//", "#"), "/*", "*/")
                Lang.PYTHON -> Spec(pyKeywords, setOf("int", "str", "float", "bool", "list", "dict", "set", "tuple", "None"), setOf("True", "False", "None"), listOf("#"), triple = true, annotations = '@')
                Lang.RUBY -> Spec(rubyKeywords, literals = setOf("true", "false", "nil"), lineComments = listOf("#"))
                Lang.SHELL, Lang.DOCKERFILE, Lang.MAKEFILE -> Spec(shKeywords + setOf("FROM", "RUN", "CMD", "COPY", "ADD", "ENV", "ARG", "WORKDIR", "EXPOSE", "ENTRYPOINT", "USER", "VOLUME", "LABEL"), lineComments = listOf("#"))
                Lang.SQL -> Spec(sqlKeywords, sqlTypes, setOf("true", "false", "null"), listOf("--"), "/*", "*/", quotes = "'\"`", caseInsensitive = true)
                Lang.LUA -> Spec(luaKeywords, literals = setOf("true", "false", "nil"), lineComments = listOf("--"))
                Lang.R, Lang.PERL -> Spec(setOf("if", "else", "for", "while", "function", "return", "in", "next", "break", "repeat", "my", "sub", "use", "foreach", "unless", "elsif"), literals = setOf("TRUE", "FALSE", "NULL", "NA", "undef"), lineComments = listOf("#"))
                Lang.CSS -> Spec(setOf("important", "media", "import", "keyframes", "font-face", "supports"), lineComments = listOf("//"), blockStart = "/*", blockEnd = "*/", annotations = '@')
                Lang.INI -> Spec(lineComments = listOf(";", "#"), keyValue = true, literals = setOf("true", "false", "yes", "no", "on", "off"))
                Lang.PLAIN -> Spec()
            },
        )
    }
}

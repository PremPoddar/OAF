package com.prempoddar.oaf.format

/**
 * Works out what a file is from its first bytes (and, for ZIP-based formats, the names inside),
 * using the name and MIME type only to break ties — so renamed and extension-less files still open
 * in the right viewer.
 */
object Detector {
    data class Result(val kind: FileKind, val lang: Lang = Lang.PLAIN)

    /**
     * [head]: the first few KB of the file. [zipEntries]: names inside, asked for only when the file
     * is a ZIP. [zipText]: the text of one entry (for ODF/EPUB `mimetype`).
     */
    fun detect(
        name: String?,
        mime: String?,
        head: ByteArray,
        zipEntries: () -> List<String> = { emptyList() },
        zipText: (String) -> String? = { null },
    ): Result {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val base = name?.substringAfterLast('/')?.lowercase().orEmpty()

        magic(head, ext)?.let { kind ->
            return if (kind == FileKind.ZIP) Result(zipKind(ext, zipEntries, zipText)) else Result(kind)
        }
        if (looksLikeText(head)) return textKind(ext, base, mime, head)
        // No signature and not text: fall back on what the name says, else unknown.
        byExtension(ext)?.let { if (it.family != FileKind.Family.TEXT && it.family != FileKind.Family.SHEET) return Result(it) }
        return Result(FileKind.UNKNOWN)
    }

    private fun magic(h: ByteArray, ext: String): FileKind? {
        fun at(offset: Int, vararg b: Int) = h.size >= offset + b.size && b.indices.all { h[offset + it].toInt() and 0xFF == b[it] }
        fun ascii(offset: Int, s: String) = at(offset, *s.map { it.code }.toIntArray())
        return when {
            ascii(0, "%PDF") -> FileKind.PDF
            at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) -> FileKind.ZIP
            at(0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) -> when (ext) {
                "xls", "xlt" -> FileKind.XLS
                "ppt", "pps", "pot" -> FileKind.PPT
                else -> FileKind.DOC
            }
            ascii(0, "{\\rtf") -> FileKind.RTF
            at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> FileKind.SEVEN_Z
            ascii(0, "Rar!") -> FileKind.RAR
            at(0, 0x1F, 0x8B) -> FileKind.GZIP
            ascii(0, "BZh") -> FileKind.BZIP2
            at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> FileKind.XZ
            ascii(257, "ustar") -> FileKind.TAR
            at(0, 0xFF, 0xD8, 0xFF) -> FileKind.IMAGE
            at(0, 0x89, 0x50, 0x4E, 0x47) -> FileKind.IMAGE
            ascii(0, "GIF8") -> FileKind.IMAGE
            ascii(0, "BM") && h.size > 14 && ext in setOf("bmp", "dib", "") -> FileKind.IMAGE
            ascii(0, "RIFF") && ascii(8, "WEBP") -> FileKind.IMAGE
            ascii(0, "RIFF") && ascii(8, "WAVE") -> FileKind.AUDIO
            ascii(0, "RIFF") && ascii(8, "AVI ") -> FileKind.VIDEO
            at(0, 0x49, 0x49, 0x2A, 0x00) || at(0, 0x4D, 0x4D, 0x00, 0x2A) -> FileKind.IMAGE // TIFF and most camera RAW
            ascii(4, "ftyp") -> ftyp(h)
            ascii(0, "ID3") || at(0, 0xFF, 0xFB) || at(0, 0xFF, 0xF3) || at(0, 0xFF, 0xF2) -> FileKind.AUDIO
            ascii(0, "fLaC") -> FileKind.AUDIO
            ascii(0, "OggS") -> if (ext in setOf("ogv")) FileKind.VIDEO else FileKind.AUDIO
            at(0, 0x1A, 0x45, 0xDF, 0xA3) -> if (ext == "mka") FileKind.AUDIO else FileKind.VIDEO
            at(0, 0x00, 0x01, 0x00, 0x00, 0x00) || ascii(0, "OTTO") || ascii(0, "wOFF") || ascii(0, "wOF2") || ascii(0, "ttcf") -> FileKind.FONT
            else -> null
        }
    }

    /** ISO media: images (HEIC/AVIF), audio (M4A) or video by brand. */
    private fun ftyp(h: ByteArray): FileKind {
        val brand = if (h.size >= 12) String(h, 8, 4, Charsets.ISO_8859_1) else ""
        return when (brand) {
            "heic", "heix", "hevc", "hevx", "mif1", "msf1", "avif", "avis" -> FileKind.IMAGE
            "M4A ", "M4B ", "M4P ", "F4A " -> FileKind.AUDIO
            else -> FileKind.VIDEO
        }
    }

    private fun zipKind(ext: String, entries: () -> List<String>, text: (String) -> String?): FileKind {
        val names = runCatching(entries).getOrDefault(emptyList())
        val set = names.toHashSet()
        if ("mimetype" in set) {
            when (text("mimetype")?.trim()) {
                "application/vnd.oasis.opendocument.text" -> return FileKind.ODT
                "application/vnd.oasis.opendocument.spreadsheet" -> return FileKind.ODS
                "application/vnd.oasis.opendocument.presentation" -> return FileKind.ODP
                "application/epub+zip" -> return FileKind.EPUB
            }
        }
        if ("[Content_Types].xml" in set) {
            when {
                names.any { it.startsWith("word/") } -> return FileKind.DOCX
                names.any { it.startsWith("xl/") } -> return FileKind.XLSX
                names.any { it.startsWith("ppt/") } -> return FileKind.PPTX
            }
        }
        if ("AndroidManifest.xml" in set && names.any { it.endsWith(".dex") }) return FileKind.APK
        return when (ext) {
            "docx", "docm", "dotx" -> FileKind.DOCX
            "xlsx", "xlsm", "xltx" -> FileKind.XLSX
            "pptx", "pptm", "ppsx" -> FileKind.PPTX
            "apk" -> FileKind.APK
            "epub" -> FileKind.EPUB
            else -> FileKind.ZIP
        }
    }

    /** True when the first bytes decode as UTF-8 (or UTF-16 with a BOM) with almost no control characters. */
    fun looksLikeText(h: ByteArray): Boolean {
        if (h.isEmpty()) return true
        if (h.size >= 2 && ((h[0] == 0xFE.toByte() && h[1] == 0xFF.toByte()) || (h[0] == 0xFF.toByte() && h[1] == 0xFE.toByte()))) return true
        val sample = h.copyOf(minOf(h.size, 4096))
        // Cut a possibly split multi-byte character off the end before decoding.
        var end = sample.size
        while (end > 0 && sample.size - end < 4 && (sample[end - 1].toInt() and 0xC0) == 0x80) end--
        if (end > 0 && sample[end - 1].toInt() and 0xC0 == 0xC0) end--
        val decoder = Charsets.UTF_8.newDecoder()
        val text = runCatching { decoder.decode(java.nio.ByteBuffer.wrap(sample, 0, end)).toString() }
            .getOrElse { String(sample, Charsets.ISO_8859_1) }
        if (text.isEmpty()) return true
        val bad = text.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' && it != '\u000C' }
        return bad.toDouble() / text.length < 0.02
    }

    private fun textKind(ext: String, base: String, mime: String?, head: ByteArray): Result {
        byExtension(ext)?.let { k -> return Result(k, if (k == FileKind.CODE) langOf(ext, base) else Lang.PLAIN) }
        when (base) {
            "dockerfile" -> return Result(FileKind.CODE, Lang.DOCKERFILE)
            "makefile", "gnumakefile" -> return Result(FileKind.CODE, Lang.MAKEFILE)
        }
        val start = String(head, 0, minOf(head.size, 512), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return when {
            start.startsWith("<?xml") && start.contains("<svg") -> Result(FileKind.SVG)
            start.startsWith("<svg") -> Result(FileKind.SVG)
            start.startsWith("<!DOCTYPE html", true) || start.startsWith("<html", true) -> Result(FileKind.HTML)
            start.startsWith("<?xml") -> Result(FileKind.XML)
            start.startsWith("#!") -> Result(FileKind.CODE, if (start.contains("python")) Lang.PYTHON else Lang.SHELL)
            (start.startsWith("{") || start.startsWith("[")) && mime?.contains("json") != false -> Result(FileKind.JSON)
            mime == "text/csv" -> Result(FileKind.CSV)
            mime == "text/markdown" -> Result(FileKind.MARKDOWN)
            else -> Result(FileKind.TEXT)
        }
    }

    private val codeExt = mapOf(
        "kt" to Lang.KOTLIN, "kts" to Lang.KOTLIN, "java" to Lang.JAVA, "c" to Lang.C, "h" to Lang.C,
        "cpp" to Lang.CPP, "cc" to Lang.CPP, "cxx" to Lang.CPP, "hpp" to Lang.CPP, "hh" to Lang.CPP, "cs" to Lang.CSHARP,
        "go" to Lang.GO, "rs" to Lang.RUST, "swift" to Lang.SWIFT, "dart" to Lang.DART, "scala" to Lang.SCALA,
        "js" to Lang.JAVASCRIPT, "mjs" to Lang.JAVASCRIPT, "cjs" to Lang.JAVASCRIPT, "jsx" to Lang.JAVASCRIPT,
        "ts" to Lang.TYPESCRIPT, "tsx" to Lang.TYPESCRIPT, "py" to Lang.PYTHON, "pyw" to Lang.PYTHON, "rb" to Lang.RUBY,
        "php" to Lang.PHP, "sh" to Lang.SHELL, "bash" to Lang.SHELL, "zsh" to Lang.SHELL, "fish" to Lang.SHELL,
        "sql" to Lang.SQL, "lua" to Lang.LUA, "r" to Lang.R, "pl" to Lang.PERL, "pm" to Lang.PERL, "css" to Lang.CSS,
        "scss" to Lang.CSS, "less" to Lang.CSS, "gradle" to Lang.GRADLE, "ini" to Lang.INI, "cfg" to Lang.INI,
        "conf" to Lang.INI, "toml" to Lang.INI, "properties" to Lang.INI, "env" to Lang.INI, "bat" to Lang.SHELL,
        "ps1" to Lang.SHELL, "m" to Lang.C, "mm" to Lang.CPP, "vue" to Lang.JAVASCRIPT, "svelte" to Lang.JAVASCRIPT,
    )

    private fun langOf(ext: String, base: String) = codeExt[ext] ?: if (base == "dockerfile") Lang.DOCKERFILE else Lang.PLAIN

    private fun byExtension(ext: String): FileKind? = when (ext) {
        "pdf" -> FileKind.PDF
        "docx", "docm", "dotx" -> FileKind.DOCX
        "doc", "dot" -> FileKind.DOC
        "odt" -> FileKind.ODT
        "rtf" -> FileKind.RTF
        "epub" -> FileKind.EPUB
        "xlsx", "xlsm" -> FileKind.XLSX
        "xls" -> FileKind.XLS
        "ods" -> FileKind.ODS
        "csv" -> FileKind.CSV
        "tsv", "tab" -> FileKind.TSV
        "pptx", "ppsx" -> FileKind.PPTX
        "ppt", "pps" -> FileKind.PPT
        "odp" -> FileKind.ODP
        "md", "markdown", "mdown", "mkd" -> FileKind.MARKDOWN
        "html", "htm", "xhtml" -> FileKind.HTML
        "json", "geojson", "jsonc", "ipynb" -> FileKind.JSON
        "xml", "plist", "xsd", "xsl", "rss", "atom", "csproj", "pom" -> FileKind.XML
        "svg" -> FileKind.SVG
        "yaml", "yml" -> FileKind.YAML
        "txt", "log", "text", "nfo", "srt", "vtt", "license" -> FileKind.TEXT
        in codeExt.keys -> FileKind.CODE
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff", "dng", "cr2", "nef", "arw", "ico" -> FileKind.IMAGE
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "amr", "mka" -> FileKind.AUDIO
        "mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts", "ogv" -> FileKind.VIDEO
        "zip", "jar" -> FileKind.ZIP
        "7z" -> FileKind.SEVEN_Z
        "tar" -> FileKind.TAR
        "gz", "tgz" -> FileKind.GZIP
        "bz2", "tbz2" -> FileKind.BZIP2
        "xz", "txz" -> FileKind.XZ
        "rar" -> FileKind.RAR
        "apk" -> FileKind.APK
        "ttf", "otf", "woff", "woff2", "ttc" -> FileKind.FONT
        else -> null
    }
}

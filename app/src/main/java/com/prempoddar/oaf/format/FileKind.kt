package com.prempoddar.oaf.format

/** What a file really is, which decides how OAF opens it. */
enum class FileKind(val family: Family, val label: String) {
    PDF(Family.DOCUMENT, "PDF"),
    DOCX(Family.DOCUMENT, "Word document"),
    DOC(Family.DOCUMENT, "Word 97–2003 document"),
    ODT(Family.DOCUMENT, "OpenDocument text"),
    RTF(Family.DOCUMENT, "Rich text"),
    EPUB(Family.DOCUMENT, "E-book"),
    XLSX(Family.SHEET, "Excel workbook"),
    XLS(Family.SHEET, "Excel 97–2003 workbook"),
    ODS(Family.SHEET, "OpenDocument spreadsheet"),
    CSV(Family.SHEET, "CSV"),
    TSV(Family.SHEET, "Tab-separated values"),
    PPTX(Family.SLIDES, "PowerPoint"),
    PPT(Family.SLIDES, "PowerPoint 97–2003"),
    ODP(Family.SLIDES, "OpenDocument presentation"),
    MARKDOWN(Family.TEXT, "Markdown"),
    HTML(Family.TEXT, "HTML"),
    JSON(Family.TEXT, "JSON"),
    XML(Family.TEXT, "XML"),
    YAML(Family.TEXT, "YAML"),
    CODE(Family.TEXT, "Source code"),
    TEXT(Family.TEXT, "Text"),
    IMAGE(Family.IMAGE, "Image"),
    SVG(Family.IMAGE, "SVG image"),
    AUDIO(Family.MEDIA, "Audio"),
    VIDEO(Family.MEDIA, "Video"),
    ZIP(Family.ARCHIVE, "ZIP archive"),
    SEVEN_Z(Family.ARCHIVE, "7z archive"),
    TAR(Family.ARCHIVE, "TAR archive"),
    GZIP(Family.ARCHIVE, "GZip file"),
    BZIP2(Family.ARCHIVE, "BZip2 file"),
    XZ(Family.ARCHIVE, "XZ file"),
    RAR(Family.ARCHIVE, "RAR archive"),
    APK(Family.OTHER, "Android app"),
    FONT(Family.OTHER, "Font"),
    UNKNOWN(Family.OTHER, "File"),
    ;

    enum class Family { DOCUMENT, SHEET, SLIDES, TEXT, IMAGE, MEDIA, ARCHIVE, OTHER }
}

/** Programming languages the highlighter knows, by family of syntax. */
enum class Lang(val label: String) {
    KOTLIN("Kotlin"), JAVA("Java"), C("C"), CPP("C++"), CSHARP("C#"), GO("Go"), RUST("Rust"), SWIFT("Swift"),
    DART("Dart"), SCALA("Scala"), JAVASCRIPT("JavaScript"), TYPESCRIPT("TypeScript"), PYTHON("Python"),
    RUBY("Ruby"), PHP("PHP"), SHELL("Shell"), SQL("SQL"), LUA("Lua"), R("R"), PERL("Perl"), CSS("CSS"),
    GRADLE("Gradle"), INI("INI"), DOCKERFILE("Dockerfile"), MAKEFILE("Makefile"), PLAIN("Text"),
}

<div align="center">

# OAF

**Open All Files.**

One quiet, fast app that opens almost anything on your phone — and edits most of it.

![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

</div>

---

OAF is reader-first: the page is the interface. Bars slide away while you read and come back with a
tap. Open a file from its own browser, from Recents, or from any other app with *Open with*. If OAF
doesn't recognise a file, it says what it can tell and shows the bytes.

## What it opens

| | Read | Edit | Save as |
|---|---|---|---|
| 📄 **PDF** | search, select, contents, links, pinch zoom | annotate, fill forms, sign, edit text, delete and add text, pictures and shapes, page tools | PDF |
| 📝 **Word** `.docx` | headings, lists, tables, pictures, links, contents | text, styles, bold/italic/underline/strike, colours, highlight, sizes, alignment, lists | `.docx` (in place) |
| 📝 **Other documents** `.doc` `.odt` `.rtf` | ✓ | ✓ | `.docx` |
| 📊 **Excel** `.xlsx` | formulas, formats, frozen panes, merges | cells, formulas, formatting, rows and columns, sort, sheets | `.xlsx` (in place) |
| 📊 **Other sheets** `.xls` `.ods` `.csv` `.tsv` | ✓ | ✓ | `.xlsx`; CSV/TSV in place |
| 📽️ **Slides** `.pptx` `.odp` `.ppt` | slides, notes, full-screen presenting | — | — |
| 📚 **EPUB** | chapters, contents, text size, remembers your place | — | — |
| 🗜️ **Archives** zip, 7z, tar, gz, bz2, xz, rar | browse, open files inside | extract all or one folder | — |
| 🖼️ **Pictures** incl. HEIC, AVIF, animated GIF/WebP, SVG | zoom, rotate | — | PNG, JPEG, WebP |
| 🎧 **Audio & video** | player with metadata | — | — |
| 💻 **Text & code** | syntax highlighting for about 30 languages and formats, Markdown and HTML rendered, JSON/XML tidied, any size | find and replace, undo | same encoding and line endings |
| 📦 **Apps** `.apk` | name, version, SDK levels, signer fingerprint, permissions, files inside | — | — |
| 🔤 **Fonts** `.ttf` `.otf` | names, preview, character set | — | — |

Every document can be printed or saved as a PDF through Android's print dialog.

### Files

A built-in browser with Recents, favourites, storage use by type, search, sorting, multi-select
copy/move/rename/zip, and a bin that keeps deleted files for 30 days. Light, dark or follow the
system.

## How it works

**Engines.** Every format is opened through one registry (`engine/Engines.kt`). OAF's own viewers
and editors are the default engine; a different engine — a full office suite, say — can be plugged
in ahead of it without the rest of the app changing.

**Saving keeps what OAF doesn't understand.** Word and Excel files are patched, not rewritten:

- *Word:* each paragraph remembers the XML it came from. Only paragraphs you changed are rebuilt;
  pictures, fields, comments, headers, footers, section settings and the parts of formatting OAF
  doesn't model are carried over. Saving is worked out from the document as it is now, so undoing
  after a save, or saving twice, never duplicates or loses paragraphs.
- *Excel:* cell data, column widths, merges and frozen panes are rewritten in each sheet's XML;
  charts, drawings, defined names, conditional formats and the shared strings (rich text included)
  are kept. Excel is asked to recalculate on open.

**Spreadsheets.** A formula engine with about a hundred functions (maths, statistics, logic, text,
lookups including `XLOOKUP`, dates), cross-sheet references, error values and loop detection.
Inserting or deleting rows and columns rewrites the formulas that point across them, as Excel
does. Formulas using a function OAF doesn't have show the value saved in the file.

**PDF.** Pages are drawn by Android's renderer; changes go through PDFBox. Every change is saved
to a new working copy, which is what you see — undo steps back through copies. Text is found with
its position on the page, so search, selection and highlights land exactly. Editing a line of text
reuses the document's font when it has every character needed; otherwise (usually because only the
characters already used were embedded) a matching family is substituted. Deleting text leaves an
equal gap so the rest of its line doesn't move. Protected PDFs open with their password and are
protected again with it when saved; what a PDF's owner doesn't allow (changes, annotations, form
filling, printing, copying) stays off.

## Limits

- Editing is native, not a full office suite: no tracked-changes review, comments, footnotes,
  page layout or charts. Tracked changes inside a paragraph you edit are accepted.
- Documents read as flowing text sized for the phone, not as printed pages.
- Slides, EPUBs and pictures are view-only. `.ppt` shows each slide's text only.
- A drawn signature is a picture on the page, not a cryptographic signature. XFA forms can't be
  filled. Edited PDF text uses a regular weight when a font has to be substituted.
- Contents links into the middle of an EPUB chapter open at the chapter's start.
- RAR5 archives and password-protected ZIP and RAR files can't be opened.
- Everything is checked by unit tests (below); it hasn't yet been tried across a wide range of
  real-world files and phones.

## Project layout

```
format/   file type detection (magic bytes, zip contents, text heuristics)
files/    copy, move, rename, zip, the bin
text/     encodings, big-file line index, syntax highlighting, JSON/XML tidying
sheet/    workbook model, formula engine, XLSX/CSV/ODS/XLS, the grid editor's model
doc/      document model, DOCX read and patch-save, DOC/ODT/RTF readers, editor with undo
pdf/      geometry, text with positions, content editing, annotations, forms, pages, sessions
slides/   PPTX, ODP and PPT into one slide model
book/     EPUB
archive/  zip, 7z, tar, compressed files, RAR
export/   print-ready HTML and printing
engine/   which viewer opens which format
ui/       Compose screens: browser, viewers, editors
```

## Testing

```
./gradlew testDebugUnitTest
```

96 tests: type detection; file operations and the bin; encodings and highlighting; the formula
engine function by function; XLSX round trips that keep drawings and calculation settings right;
shared formulas; CSV dialects; ODS and XLS; the spreadsheet editor (undo, structure changes,
sorting, clipboard, sheets); DOCX reading, saving only what changed, and undo-after-save; the
document editor; DOC, ODT and RTF; PDF geometry for every rotation, text positions, element
editing, font reuse and substitution, annotations, forms, page tools, outline and password
round trips; archives including path-traversal attempts; EPUB 2 and 3; slides; print HTML.

## Building

Requires JDK 17 or 21 for Gradle.

```
./gradlew assembleDebug
```

## License

Apache 2.0.

Literata, Inter and JetBrains Mono are used under the SIL Open Font License. Built with Apache POI,
PDFBox for Android, Apache Commons Compress (Apache 2.0); AndroidSVG (Apache 2.0); commonmark-java
(BSD 2-Clause); XZ for Java (public domain); and junrar, whose UnRAR licence allows extracting RAR
archives but not making a RAR compressor from it.

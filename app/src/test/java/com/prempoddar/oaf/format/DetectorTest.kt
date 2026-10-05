package com.prempoddar.oaf.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectorTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() } + ByteArray(600)
    private fun detect(name: String?, head: ByteArray, entries: List<String> = emptyList(), mimetype: String? = null, mime: String? = null) =
        Detector.detect(name, mime, head, { entries }, { if (it == "mimetype") mimetype else null })

    @Test fun signaturesBeatExtensions() {
        assertThat(detect("report.txt", "%PDF-1.7\n".toByteArray()).kind).isEqualTo(FileKind.PDF)
        assertThat(detect("photo.pdf", bytes(0xFF, 0xD8, 0xFF, 0xE0)).kind).isEqualTo(FileKind.IMAGE)
        assertThat(detect(null, bytes(0x89, 0x50, 0x4E, 0x47)).kind).isEqualTo(FileKind.IMAGE)
        assertThat(detect("x", "Rar!\u001a\u0007\u0000".toByteArray()).kind).isEqualTo(FileKind.RAR)
        assertThat(detect("x", bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)).kind).isEqualTo(FileKind.SEVEN_Z)
        assertThat(detect("x", bytes(0x1F, 0x8B, 0x08)).kind).isEqualTo(FileKind.GZIP)
        assertThat(detect("x", "{\\rtf1\\ansi".toByteArray()).kind).isEqualTo(FileKind.RTF)
        assertThat(detect("x", "fLaC".toByteArray() + ByteArray(10)).kind).isEqualTo(FileKind.AUDIO)
    }

    @Test fun tarHasItsMagicAtOffset257() {
        val h = ByteArray(600); "ustar".toByteArray().copyInto(h, 257)
        assertThat(detect("backup", h).kind).isEqualTo(FileKind.TAR)
    }

    @Test fun isoMediaByBrand() {
        fun ftyp(brand: String) = ByteArray(4) + "ftyp$brand".toByteArray() + ByteArray(20)
        assertThat(detect("a", ftyp("heic")).kind).isEqualTo(FileKind.IMAGE)
        assertThat(detect("a", ftyp("M4A ")).kind).isEqualTo(FileKind.AUDIO)
        assertThat(detect("a", ftyp("isom")).kind).isEqualTo(FileKind.VIDEO)
    }

    @Test fun zipContainersByWhatsInside() {
        val zip = bytes(0x50, 0x4B, 0x03, 0x04)
        assertThat(detect("thing.zip", zip, listOf("[Content_Types].xml", "word/document.xml")).kind).isEqualTo(FileKind.DOCX)
        assertThat(detect("a", zip, listOf("[Content_Types].xml", "xl/workbook.xml")).kind).isEqualTo(FileKind.XLSX)
        assertThat(detect("a", zip, listOf("[Content_Types].xml", "ppt/presentation.xml")).kind).isEqualTo(FileKind.PPTX)
        assertThat(detect("a", zip, listOf("mimetype", "content.xml"), "application/vnd.oasis.opendocument.text").kind).isEqualTo(FileKind.ODT)
        assertThat(detect("a", zip, listOf("mimetype"), "application/epub+zip").kind).isEqualTo(FileKind.EPUB)
        assertThat(detect("a", zip, listOf("AndroidManifest.xml", "classes.dex")).kind).isEqualTo(FileKind.APK)
        assertThat(detect("photos.zip", zip, listOf("a.jpg")).kind).isEqualTo(FileKind.ZIP)
    }

    @Test fun oleDocumentsUseTheExtension() {
        val ole = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
        assertThat(detect("budget.xls", ole).kind).isEqualTo(FileKind.XLS)
        assertThat(detect("deck.ppt", ole).kind).isEqualTo(FileKind.PPT)
        assertThat(detect("letter", ole).kind).isEqualTo(FileKind.DOC)
    }

    @Test fun textByExtensionAndContent() {
        assertThat(detect("notes.md", "# Hi".toByteArray()).kind).isEqualTo(FileKind.MARKDOWN)
        val code = detect("Main.kt", "fun main() {}".toByteArray())
        assertThat(code.kind).isEqualTo(FileKind.CODE)
        assertThat(code.lang).isEqualTo(Lang.KOTLIN)
        assertThat(detect("data", "  {\"a\": 1}".toByteArray()).kind).isEqualTo(FileKind.JSON)
        assertThat(detect("page", "<!DOCTYPE html><html>".toByteArray()).kind).isEqualTo(FileKind.HTML)
        assertThat(detect("drawing", "<?xml version=\"1.0\"?><svg>".toByteArray()).kind).isEqualTo(FileKind.SVG)
        assertThat(detect("run", "#!/usr/bin/env python3\n".toByteArray()).lang).isEqualTo(Lang.PYTHON)
        assertThat(detect("Dockerfile", "FROM alpine".toByteArray()).lang).isEqualTo(Lang.DOCKERFILE)
        assertThat(detect("README", "Hello there, plain words.".toByteArray()).kind).isEqualTo(FileKind.TEXT)
        assertThat(detect("ünïcødé.txt", "Grüße aus Köln — ☕".toByteArray()).kind).isEqualTo(FileKind.TEXT)
    }

    @Test fun binaryJunkIsUnknown() {
        val junk = ByteArray(512) { (it * 37 % 251).toByte() }
        assertThat(detect("mystery.bin", junk).kind).isEqualTo(FileKind.UNKNOWN)
    }

    @Test fun emptyFileIsText() {
        assertThat(detect("empty.txt", ByteArray(0)).kind).isEqualTo(FileKind.TEXT)
    }
}

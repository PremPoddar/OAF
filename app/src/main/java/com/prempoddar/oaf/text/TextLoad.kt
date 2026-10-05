package com.prempoddar.oaf.text

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** How a text file is encoded and laid out, so saving gives back the same kind of file. */
data class TextFormat(val charset: Charset, val bom: ByteArray, val crlf: Boolean)

object TextLoad {
    /** Detects BOMs, then tries strict UTF-8, then falls back to Windows-1252 (which never fails). */
    fun format(head: ByteArray): TextFormat {
        val crlf = String(head, Charsets.ISO_8859_1).contains("\r\n")
        return when {
            head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte() -> TextFormat(Charsets.UTF_8, head.copyOf(3), crlf)
            head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte() -> TextFormat(Charsets.UTF_16LE, head.copyOf(2), crlf)
            head.size >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte() -> TextFormat(Charsets.UTF_16BE, head.copyOf(2), crlf)
            isUtf8(head) -> TextFormat(Charsets.UTF_8, ByteArray(0), crlf)
            else -> TextFormat(Charset.forName("windows-1252"), ByteArray(0), crlf)
        }
    }

    private fun isUtf8(b: ByteArray): Boolean {
        // Ignore a character cut off at the end of the sample.
        var end = b.size
        var back = 0
        while (end > 0 && back < 3 && (b[end - 1].toInt() and 0xC0) == 0x80) { end--; back++ }
        if (end > 0 && (b[end - 1].toInt() and 0xC0) == 0xC0) end-- else end += back
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(b, 0, end.coerceAtMost(b.size))) }.isSuccess
    }

    fun read(file: File, format: TextFormat): String {
        val bytes = file.readBytes()
        val body = bytes.copyOfRange(format.bom.size.coerceAtMost(bytes.size), bytes.size)
        return String(body, format.charset).replace("\r\n", "\n")
    }

    fun encode(text: String, format: TextFormat): ByteArray {
        val t = if (format.crlf) text.replace("\n", "\r\n") else text
        return format.bom + t.toByteArray(format.charset)
    }
}

/**
 * Lines of a file of any size without loading it: byte offsets of each line start, found once,
 * so any line can be read on demand while scrolling.
 */
class LineIndex(private val file: File, private val format: TextFormat) : AutoCloseable {
    private val raf = RandomAccessFile(file, "r")
    private var offsets = LongArray(1024)
    var count = 0
        private set

    init {
        offsets[count++] = format.bom.size.toLong()
        val buf = ByteArray(1 shl 16)
        var pos = 0L
        val wide = format.charset == Charsets.UTF_16LE || format.charset == Charsets.UTF_16BE
        raf.seek(0)
        while (true) {
            val n = raf.read(buf)
            if (n <= 0) break
            for (i in 0 until n) {
                if (buf[i] == '\n'.code.toByte()) {
                    val next = pos + i + if (wide) 2 else 1
                    if (wide && format.charset == Charsets.UTF_16BE) continue // the newline byte is the second of the pair
                    if (count == offsets.size) offsets = offsets.copyOf(offsets.size * 2)
                    offsets[count++] = next
                }
            }
            pos += n
        }
        if (count > 1 && offsets[count - 1] >= file.length()) count-- // trailing newline isn't an extra line
    }

    @Synchronized fun line(i: Int): String {
        val start = offsets[i]
        val end = if (i + 1 < count) offsets[i + 1] else file.length()
        val len = (end - start).coerceAtMost(64 * 1024).toInt()
        val bytes = ByteArray(len)
        raf.seek(start)
        raf.readFully(bytes)
        return String(bytes, format.charset).trimEnd('\n', '\r')
    }

    override fun close() = raf.close()
}

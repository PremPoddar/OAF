package com.prempoddar.oaf.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** What the document's owner allows. Unrestricted documents allow everything. */
data class PdfPermissions(val modify: Boolean, val annotate: Boolean, val fillForms: Boolean, val assemble: Boolean, val copy: Boolean, val print: Boolean) {
    companion object {
        val ALL = PdfPermissions(true, true, true, true, true, true)
        fun of(p: AccessPermission) = PdfPermissions(p.canModify(), p.canModifyAnnotations(), p.canFillInForm() || p.canModifyAnnotations(), p.canAssembleDocument() || p.canModify(), p.canExtractContent(), p.canPrint())
    }
}

/**
 * An open PDF being worked on. Every change is saved to a new working copy, which is what the
 * screen renders; undo and redo step between copies. The original is only written on save.
 */
class PdfSession private constructor(
    private val dir: File,
    private val password: String?,
    private val permission: AccessPermission?,
    val encrypted: Boolean,
    val permissions: PdfPermissions,
    first: File,
) : Closeable {
    private val versions = arrayListOf(first)
    private var at = 0
    var doc: PDDocument = load(first)
        private set
    /** Changes since opening or the last save. */
    var unsaved = 0
        private set

    val file: File get() = versions[at]
    val canUndo get() = at > 0
    val canRedo get() = at < versions.lastIndex
    val pageCount get() = doc.numberOfPages

    private fun load(f: File) = PDDocument.load(f, MemoryUsageSetting.setupMixed(64L * 1024 * 1024))

    /** Runs [change] on the document and keeps the result as a new version. */
    fun <T> mutate(change: (PDDocument) -> T): T {
        val result = change(doc)
        val next = File(dir, "v${System.nanoTime()}.pdf")
        doc.save(next)
        (result as? Closeable)?.close()
        while (versions.size > at + 1) versions.removeAt(versions.lastIndex).delete()
        versions += next
        at++
        // Old versions beyond what undo reaches are dropped from disk.
        if (versions.size > MAX_VERSIONS) { versions.removeAt(1).delete(); at-- }
        doc.close()
        doc = load(next)
        unsaved++
        return result
    }

    fun undo() { if (canUndo) { at--; reload(); unsaved++ } }
    fun redo() { if (canRedo) { at++; reload(); unsaved++ } }

    private fun reload() { doc.close(); doc = load(file) }

    /**
     * Writes the current version. A password-protected document is protected again with the same
     * password and permissions.
     */
    fun saveTo(out: OutputStream) {
        if (!encrypted) {
            file.inputStream().use { it.copyTo(out) }
        } else {
            load(file).use { copy ->
                val policy = StandardProtectionPolicy(UUID.randomUUID().toString(), password.orEmpty(), permission ?: AccessPermission())
                policy.encryptionKeyLength = 128
                copy.protect(policy)
                copy.save(out)
            }
        }
        unsaved = 0
    }

    override fun close() {
        runCatching { doc.close() }
        dir.deleteRecursively()
    }

    class PasswordNeeded : Exception("This PDF needs a password")

    companion object {
        private const val MAX_VERSIONS = 30

        /** Opens [source]; throws [PasswordNeeded] when it's protected and [password] is missing or wrong. */
        fun open(source: File, workDir: File, password: String? = null): PdfSession {
            workDir.deleteRecursively()
            workDir.mkdirs()
            val first = File(workDir, "v0.pdf")
            val probe = try {
                PDDocument.load(source, password.orEmpty(), MemoryUsageSetting.setupMixed(64L * 1024 * 1024))
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                throw PasswordNeeded()
            }
            probe.use { d ->
                if (!d.isEncrypted) {
                    source.copyTo(first, overwrite = true)
                    return PdfSession(workDir, null, null, false, PdfPermissions.ALL, first)
                }
                // The working copy is kept decrypted in the app's private cache; it's encrypted again on save.
                val permission = d.currentAccessPermission
                d.isAllSecurityToBeRemoved = true
                d.save(first)
                return PdfSession(workDir, password, permission, true, PdfPermissions.of(permission), first)
            }
        }
    }
}

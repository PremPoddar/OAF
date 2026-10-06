package com.prempoddar.oaf.files

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** File-manager operations on plain files. Nothing here is Android-specific. */
object FileOps {
    class Conflict(message: String) : IOException(message)

    /** "name.txt", "name (2).txt", "name (3).txt" … the first that doesn't exist in [dir]. */
    fun freeName(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.').takeIf { it > 0 && !File(dir, name).isDirectory }
        val stem = if (dot != null) name.substring(0, dot) else name
        val ext = if (dot != null) name.substring(dot) else ""
        var n = 2
        while (f.exists()) f = File(dir, "$stem ($n)$ext").also { n++ }
        return f
    }

    fun copy(src: File, intoDir: File, onProgress: (Long) -> Unit = {}): File {
        val target = freeName(intoDir, src.name)
        if (src.isDirectory && intoDir.canonicalPath.startsWith(src.canonicalPath + File.separator) || intoDir.canonicalFile == src.canonicalFile) {
            throw Conflict("A folder can't be copied into itself")
        }
        var done = 0L
        src.walkTopDown().forEach { f ->
            val rel = f.relativeTo(src).path
            val out = if (rel.isEmpty()) target else File(target, rel)
            if (f.isDirectory) out.mkdirs() else {
                out.parentFile?.mkdirs()
                f.inputStream().use { i -> out.outputStream().use { o -> done += i.copyTo(o); onProgress(done) } }
            }
        }
        return target
    }

    fun move(src: File, intoDir: File): File {
        if (src.parentFile?.canonicalFile == intoDir.canonicalFile) return src
        val target = freeName(intoDir, src.name)
        if (src.renameTo(target)) return target
        // Different volumes: copy, then delete the original.
        copy(src, intoDir).let { copied ->
            if (copied != target) copied.renameTo(target)
        }
        src.deleteRecursively()
        return target
    }

    fun rename(f: File, newName: String): File {
        val clean = newName.trim()
        require(clean.isNotEmpty() && !clean.contains('/') && clean != "." && clean != "..") { "That name can't be used" }
        val target = File(f.parentFile, clean)
        if (target.exists() && !target.equals(f)) throw Conflict("“$clean” already exists here")
        if (!f.renameTo(target)) throw IOException("Couldn't rename")
        return target
    }

    /** Creates a ZIP of [items] (files or folders) at [out]. */
    fun zip(items: List<File>, out: File) {
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            items.forEach { root ->
                root.walkTopDown().forEach { f ->
                    val rel = (if (f == root) root.name else root.name + "/" + f.relativeTo(root).path).replace(File.separatorChar, '/')
                    if (f.isDirectory) {
                        zos.putNextEntry(ZipEntry("$rel/")); zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(rel).apply { time = f.lastModified() })
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    /** Extracts into [dir]; entries trying to escape it ("zip slip") are refused. */
    fun unzip(zip: File, dir: File): List<File> {
        val root = dir.canonicalFile.also { it.mkdirs() }
        val made = ArrayList<File>()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val out = safeChild(root, e.name)
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                    made += out
                }
            }
        }
        return made
    }

    fun safeChild(root: File, entryName: String): File {
        val out = File(root, entryName).canonicalFile
        if (out != root && !out.path.startsWith(root.path + File.separator)) throw IOException("Unsafe path in archive: $entryName")
        return out
    }

    fun size(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun checkName(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty() && !clean.contains('/') && clean != "." && clean != "..") { "That name can't be used" }
        return clean
    }

    fun newFolder(dir: File, name: String): File {
        val f = File(dir, checkName(name))
        if (f.exists()) throw Conflict("“${f.name}” already exists here")
        if (!f.mkdirs()) throw IOException("Couldn't create the folder")
        return f
    }

    fun newFile(dir: File, name: String): File {
        val f = File(dir, checkName(name))
        if (f.exists()) throw Conflict("“${f.name}” already exists here")
        if (!f.createNewFile()) throw IOException("Couldn't create the file")
        return f
    }

    /**
     * New names for [files] from [pattern]: `#` is the counter (### pads to three digits), `*` is
     * the original name. Extensions are kept.
     */
    fun batchNames(files: List<File>, pattern: String, start: Int): List<String> = files.mapIndexed { i, f ->
        val ext = if (f.isFile && f.name.lastIndexOf('.') > 0) f.name.substring(f.name.lastIndexOf('.')) else ""
        val stem = if (ext.isEmpty()) f.name else f.name.dropLast(ext.length)
        val withCounter = Regex("#+").replace(pattern) { m -> (start + i).toString().padStart(m.value.length, '0') }
        withCounter.replace("*", stem).trim() + ext
    }

    fun batchRename(files: List<File>, pattern: String, start: Int): List<File> {
        val names = batchNames(files, pattern, start)
        names.forEach { checkName(it) }
        if (names.toSet().size != names.size) throw Conflict("Add # to the pattern so every name is different")
        // Two passes so names can be swapped or shifted among the same files.
        val temp = files.map { f -> File(f.parentFile, ".oaf-rename-" + System.nanoTime() + "-" + f.name).also { if (!f.renameTo(it)) throw IOException("Couldn't rename “${f.name}”") } }
        return temp.mapIndexed { i, t ->
            val target = File(t.parentFile, names[i]).let { if (it.exists()) freeName(t.parentFile!!, names[i]) else it }
            if (!t.renameTo(target)) throw IOException("Couldn't rename to “${names[i]}”")
            target
        }
    }

    /** Hex digest of a file, for MD5, SHA-1 or SHA-256. */
    fun hash(f: File, algorithm: String): String {
        val md = java.security.MessageDigest.getInstance(algorithm)
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Deleted files go to a bin first: moved into [binDir] with a record of where they came from, so
 * they can be put back. Emptying the bin is the only permanent delete.
 */
class Bin(private val binDir: File) {
    data class Item(val id: String, val name: String, val originalPath: String, val deletedAt: Long, val size: Long)

    private val index = File(binDir, ".index")

    init { binDir.mkdirs() }

    @Synchronized fun items(): List<Item> = if (!index.exists()) emptyList() else index.readLines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size != 5) null else Item(p[0], p[1], p[2], p[3].toLongOrNull() ?: 0, p[4].toLongOrNull() ?: 0)
    }.filter { File(binDir, it.id).exists() }

    @Synchronized fun delete(f: File, now: Long = System.currentTimeMillis()): Item {
        val id = "${now}_${(Math.random() * 1e9).toLong()}"
        val item = Item(id, f.name, f.absolutePath, now, FileOps.size(f))
        val target = File(binDir, id)
        if (!f.renameTo(target)) {
            f.copyRecursively(target, overwrite = true)
            f.deleteRecursively()
        }
        write(items() + item)
        return item
    }

    /** Puts an item back where it was (renamed if something new is there now). */
    @Synchronized fun restore(item: Item): File {
        val original = File(item.originalPath)
        original.parentFile?.mkdirs()
        val target = if (original.exists()) FileOps.freeName(original.parentFile!!, original.name) else original
        val stored = File(binDir, item.id)
        if (!stored.renameTo(target)) {
            stored.copyRecursively(target, overwrite = true)
            stored.deleteRecursively()
        }
        write(items().filter { it.id != item.id })
        return target
    }

    @Synchronized fun purge(item: Item) {
        File(binDir, item.id).deleteRecursively()
        write(items().filter { it.id != item.id })
    }

    @Synchronized fun empty() = items().forEach { purge(it) }

    /** Items older than [maxAgeMs] are removed for good. */
    @Synchronized fun expire(now: Long, maxAgeMs: Long) = items().filter { now - it.deletedAt > maxAgeMs }.forEach { purge(it) }

    private fun write(list: List<Item>) {
        index.writeText(list.joinToString("") { "${it.id}\t${it.name.replace('\t', ' ')}\t${it.originalPath}\t${it.deletedAt}\t${it.size}\n" })
    }
}

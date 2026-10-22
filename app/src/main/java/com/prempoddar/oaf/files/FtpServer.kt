package com.prempoddar.oaf.files

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small FTP server for sharing a folder of this phone with other devices on the network: what
 * Windows Explorer, macOS Finder, FileZilla and file manager apps need (passive and active data
 * connections, LIST/MLSD, resume, rename, delete, folders), names in UTF-8, and nothing outside
 * [Config.root] reachable whatever path a client sends. One thread per client.
 */
class FtpServer(private val config: Config, private val listener: Listener = object : Listener {}) {

    data class Config(
        val root: File,
        val port: Int = 2121,
        /** Null for anonymous access: any name and password are let in. */
        val user: String? = null,
        val password: String? = null,
        val readOnly: Boolean = false,
    )

    interface Listener {
        fun clients(count: Int) {}
        /** A file was added, changed or removed, so others (the media library) can catch up. */
        fun changed(file: File) {}
        fun log(line: String) {}
    }

    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null
    private val open = AtomicInteger()
    private val sockets = java.util.Collections.synchronizedSet(HashSet<Socket>())
    private val rootPath = config.root.canonicalFile

    val port: Int get() = server?.localPort ?: config.port
    val running: Boolean get() = server?.isClosed == false

    /** Starts listening; throws if the port can't be had (another app has it, say). */
    @Synchronized fun start() {
        if (running) return
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(config.port))
        server = s
        val p = Executors.newCachedThreadPool { r -> Thread(r, "ftp").apply { isDaemon = true } }
        pool = p
        p.execute {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: IOException) { break }
                sockets += client
                p.execute { serve(client) }
            }
        }
    }

    @Synchronized fun stop() {
        server?.close(); server = null
        synchronized(sockets) { sockets.forEach { runCatching { it.close() } }; sockets.clear() }
        pool?.shutdownNow(); pool = null
    }

    private fun serve(socket: Socket) {
        listener.clients(open.incrementAndGet())
        try {
            socket.soTimeout = IDLE_MS
            Session(socket).run()
        } catch (_: IOException) {
        } finally {
            runCatching { socket.close() }
            sockets -= socket
            listener.clients(open.decrementAndGet())
        }
    }

    private inner class Session(private val control: Socket) {
        private val input = BufferedReader(InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8))
        private val output = control.getOutputStream()
        private val who = control.inetAddress.hostAddress.orEmpty()
        private var user: String? = null
        private var loggedIn = false
        private var failures = 0
        /** Where the client is, as a path from the shared folder ("/" is the folder itself). */
        private var cwd = "/"
        private var passive: ServerSocket? = null
        private var active: InetSocketAddress? = null
        private var restart = 0L
        private var renameFrom: File? = null

        fun run() {
            reply(220, "OAF FTP ready")
            while (true) {
                val line = input.readLine() ?: return
                val space = line.indexOf(' ')
                val cmd = (if (space < 0) line else line.substring(0, space)).uppercase(Locale.US)
                val arg = if (space < 0) "" else line.substring(space + 1)
                if (cmd == "QUIT") { reply(221, "Goodbye"); return }
                try {
                    handle(cmd, arg)
                } catch (e: SocketException) {
                    throw e
                } catch (e: Exception) {
                    reply(451, "Couldn't do that: ${e.message ?: e.javaClass.simpleName}")
                }
                if (cmd != "REST") restart = 0
            }
        }

        private fun handle(cmd: String, arg: String) {
            when (cmd) {
                "USER" -> { user = arg; loggedIn = false; if (config.user == null) reply(331, "Any password will do") else reply(331, "Password, please") }
                "PASS" -> login(arg)
                "SYST" -> reply(215, "UNIX Type: L8")
                "FEAT" -> lines(211, listOf("Features:", " UTF8", " PASV", " EPSV", " EPRT", " SIZE", " MDTM", " MFMT", " REST STREAM", " MLST type*;size*;modify*;perm*;", " TVFS"), "End")
                "OPTS" -> if (arg.uppercase(Locale.US).startsWith("UTF8")) reply(200, "Always in UTF-8") else reply(501, "Option not understood")
                "NOOP" -> reply(200, "OK")
                "HELP" -> reply(214, "Ask for anything in RFC 959")
                "AUTH", "PBSZ", "PROT" -> reply(502, "This server doesn't encrypt")
                else -> {
                    if (!loggedIn) { reply(530, "Sign in first"); return }
                    when (cmd) {
                        "PWD", "XPWD" -> reply(257, "\"${cwd.replace("\"", "\"\"")}\" is the current folder")
                        "CWD", "XCWD" -> changeTo(arg)
                        "CDUP", "XCUP" -> changeTo("..")
                        "TYPE", "MODE", "STRU" -> reply(200, "OK")
                        "ALLO" -> reply(202, "No need")
                        "SITE" -> reply(202, "Nothing to do")
                        "PASV" -> openPassive(extended = false)
                        "EPSV" -> openPassive(extended = true)
                        "PORT" -> portTo(arg)
                        "EPRT" -> eprtTo(arg)
                        "LIST" -> listing(stripOptions(arg)) { f -> unixLine(f) }
                        "NLST" -> listing(stripOptions(arg)) { f -> f.name }
                        "MLSD" -> listing(arg) { f -> facts(f) + " " + f.name }
                        "MLST" -> mlst(arg)
                        "STAT" -> if (arg.isBlank()) reply(211, "OAF FTP, signed in as ${user ?: "anyone"}") else statPath(arg)
                        "SIZE" -> existing(arg)?.let { f -> if (f.isFile) reply(213, f.length().toString()) else reply(550, "Not a file") }
                        "MDTM" -> existing(arg)?.let { f -> reply(213, stamp(f.lastModified())) }
                        "REST" -> { restart = arg.trim().toLongOrNull()?.coerceAtLeast(0) ?: 0; reply(350, "Will start at $restart") }
                        "RETR" -> send(arg)
                        "STOR" -> receive(arg, append = false)
                        "APPE" -> receive(arg, append = true)
                        "DELE" -> writable { delete(arg) }
                        "MKD", "XMKD" -> writable { makeDir(arg) }
                        "RMD", "XRMD" -> writable { removeDir(arg) }
                        "RNFR" -> writable { existing(arg)?.let { renameFrom = it; reply(350, "Ready for the new name") } }
                        "RNTO" -> writable { renameTo(arg) }
                        "MFMT" -> writable { setTime(arg) }
                        "ABOR" -> { closeData(); reply(226, "Nothing to stop") }
                        else -> reply(502, "$cmd isn't supported")
                    }
                }
            }
        }

        private fun login(password: String) {
            val expected = config.user
            val ok = expected == null || (same(user.orEmpty(), expected) && same(password, config.password.orEmpty()))
            if (ok) {
                loggedIn = true; failures = 0
                reply(230, "Signed in")
                listener.log("$who signed in")
            } else {
                failures++
                // Slow down guessing, and stop after a few wrong passwords.
                Thread.sleep(1_000L * failures)
                reply(530, "Wrong name or password")
                listener.log("$who: wrong password")
                if (failures >= 5) throw SocketException("too many tries")
            }
        }

        private fun same(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

        // Paths

        /** The client's path, made absolute and tidy; never above the shared folder. */
        private fun virtual(arg: String): String {
            val p = arg.trim().ifEmpty { "." }
            val parts = ArrayList<String>()
            val start = if (p.startsWith("/")) "" else cwd
            for (seg in "$start/$p".split('/')) when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += seg
            }
            return "/" + parts.joinToString("/")
        }

        private fun fileOf(arg: String): File? {
            val v = virtual(arg)
            if ('\u0000' in v) return null
            val f = if (v == "/") rootPath else File(rootPath, v.removePrefix("/"))
            // A link pointing out of the shared folder is treated as missing.
            val real = runCatching { f.canonicalFile }.getOrNull() ?: return null
            return if (real == rootPath || real.path.startsWith(rootPath.path + File.separator)) f else null
        }

        private fun existing(arg: String): File? {
            val f = fileOf(arg)
            return if (f != null && f.exists()) f else { reply(550, "No such file or folder"); null }
        }

        private fun writable(block: () -> Unit) = if (config.readOnly) reply(550, "This share is read-only") else block()

        private fun changeTo(arg: String) {
            val f = fileOf(arg)
            if (f != null && f.isDirectory) { cwd = virtual(arg); reply(250, "Now in $cwd") } else reply(550, "No such folder")
        }

        // Data connections

        private fun openPassive(extended: Boolean) {
            closeData()
            val local = control.localAddress
            val s = ServerSocket()
            s.bind(InetSocketAddress(local, 0))
            s.soTimeout = DATA_WAIT_MS
            passive = s
            if (extended) {
                reply(229, "Entering Extended Passive Mode (|||${s.localPort}|)")
            } else {
                val v4 = (local as? Inet4Address) ?: run { reply(522, "Use EPSV on this network"); return }
                val a = v4.address.joinToString(",") { (it.toInt() and 0xFF).toString() }
                reply(227, "Entering Passive Mode ($a,${s.localPort shr 8},${s.localPort and 0xFF})")
            }
        }

        private fun portTo(arg: String) {
            val n = arg.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (n.size != 6) { reply(501, "Bad PORT"); return }
            val host = InetAddress.getByName(n.take(4).joinToString("."))
            // Only back to the client itself, so the server can't be used to reach other machines.
            if (host != control.inetAddress) { reply(504, "Data must go to your own address"); return }
            closeData()
            active = InetSocketAddress(host, (n[4] shl 8) or n[5])
            reply(200, "PORT OK")
        }

        private fun eprtTo(arg: String) {
            val d = arg.firstOrNull() ?: run { reply(501, "Bad EPRT"); return }
            val p = arg.split(d)
            if (p.size < 4) { reply(501, "Bad EPRT"); return }
            val host = InetAddress.getByName(p[2])
            if (host != control.inetAddress) { reply(504, "Data must go to your own address"); return }
            closeData()
            active = InetSocketAddress(host, p[3].toIntOrNull() ?: run { reply(501, "Bad EPRT"); return })
            reply(200, "EPRT OK")
        }

        private fun data(): Socket? {
            val s = try {
                passive?.accept() ?: active?.let { a -> Socket().apply { connect(a, DATA_WAIT_MS) } }
            } catch (e: IOException) {
                null
            }
            closeData()
            if (s == null) reply(425, "Couldn't open a data connection")
            return s
        }

        private fun closeData() {
            passive?.let { runCatching { it.close() } }
            passive = null; active = null
        }

        // Listings

        private fun stripOptions(arg: String) = arg.split(' ').filterNot { it.startsWith("-") }.joinToString(" ")

        private fun listing(arg: String, line: (File) -> String) {
            val target = fileOf(arg)
            if (target == null || !target.exists()) { closeData(); reply(550, "No such folder"); return }
            val files = if (target.isDirectory) target.listFiles()?.sortedBy { it.name.lowercase() }.orEmpty() else listOf(target)
            reply(150, "Here it comes")
            val d = data() ?: return
            d.use { s ->
                val out = s.getOutputStream().bufferedWriter(StandardCharsets.UTF_8)
                files.forEach { out.write(line(it)); out.write("\r\n") }
                out.flush()
            }
            reply(226, "${files.size} entries")
        }

        private fun mlst(arg: String) {
            val f = existing(arg) ?: return
            lines(250, listOf("Facts:", " " + facts(f) + " " + virtual(arg)), "End")
        }

        private fun statPath(arg: String) {
            val f = existing(arg) ?: return
            val files = if (f.isDirectory) f.listFiles()?.toList().orEmpty() else listOf(f)
            lines(213, listOf("Status of $arg:") + files.map { unixLine(it) }, "End")
        }

        private fun facts(f: File): String {
            val type = if (f.isDirectory) "dir" else "file"
            val perm = when {
                f.isDirectory -> if (config.readOnly) "el" else "elcmfdp"
                else -> if (config.readOnly) "r" else "rwadf"
            }
            return "type=$type;" + (if (f.isFile) "size=${f.length()};" else "") + "modify=${stamp(f.lastModified())};perm=$perm;"
        }

        private fun unixLine(f: File): String {
            val mode = if (f.isDirectory) "drwxr-xr-x" else if (config.readOnly) "-r--r--r--" else "-rw-r--r--"
            val size = if (f.isDirectory) 0 else f.length()
            val modified = f.lastModified()
            val recent = System.currentTimeMillis() - modified < 180L * 24 * 3600 * 1000
            val date = SimpleDateFormat(if (recent) "MMM dd HH:mm" else "MMM dd  yyyy", Locale.US).format(Date(modified))
            return "$mode 1 owner group ${size.toString().padStart(12)} $date ${f.name}"
        }

        // Files

        private fun send(arg: String) {
            val f = fileOf(arg)
            if (f == null || !f.isFile) { closeData(); reply(550, "No such file"); return }
            reply(150, "Sending ${f.name} (${f.length() - restart} bytes)")
            val d = data() ?: return
            var ok = false
            d.use { s ->
                RandomAccessFile(f, "r").use { raf ->
                    if (restart > 0) raf.seek(restart)
                    copy({ b -> raf.read(b) }, s.getOutputStream())
                }
                ok = true
            }
            if (ok) { reply(226, "Sent"); listener.log("$who downloaded ${virtual(arg)}") }
        }

        private fun receive(arg: String, append: Boolean) {
            if (config.readOnly) { closeData(); reply(550, "This share is read-only"); return }
            val f = fileOf(arg)
            if (f == null || f.isDirectory || f.parentFile?.isDirectory != true) { closeData(); reply(553, "Can't save there"); return }
            reply(150, "Ready for ${f.name}")
            val d = data() ?: return
            d.use { s ->
                RandomAccessFile(f, "rw").use { raf ->
                    when {
                        append -> raf.seek(raf.length())
                        restart > 0 -> raf.seek(restart)
                        else -> raf.setLength(0)
                    }
                    val input: InputStream = s.getInputStream()
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                    }
                    if (restart > 0 && !append) raf.setLength(raf.filePointer)
                }
            }
            reply(226, "Saved")
            listener.changed(f)
            listener.log("$who uploaded ${virtual(arg)}")
        }

        private fun copy(read: (ByteArray) -> Int, out: OutputStream) {
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.flush()
        }

        private fun delete(arg: String) {
            val f = existing(arg) ?: return
            if (f.isDirectory) { reply(550, "That's a folder; use RMD"); return }
            if (f.delete()) { reply(250, "Deleted"); listener.changed(f); listener.log("$who deleted ${virtual(arg)}") } else reply(550, "Couldn't delete it")
        }

        private fun makeDir(arg: String) {
            val f = fileOf(arg) ?: run { reply(550, "Can't make that"); return }
            if (f.exists()) { reply(550, "Already there"); return }
            if (f.mkdir()) reply(257, "\"${virtual(arg)}\" made") else reply(550, "Couldn't make it")
        }

        private fun removeDir(arg: String) {
            val f = existing(arg) ?: return
            if (f == rootPath || !f.isDirectory) { reply(550, "Can't remove that"); return }
            if (f.delete()) { reply(250, "Removed"); listener.log("$who removed ${virtual(arg)}") } else reply(550, "The folder isn't empty")
        }

        private fun renameTo(arg: String) {
            val from = renameFrom ?: run { reply(503, "RNFR first"); return }
            renameFrom = null
            val to = fileOf(arg) ?: run { reply(553, "Can't rename to that"); return }
            if (from == rootPath || to.exists()) { reply(553, "Can't rename to that"); return }
            if (from.renameTo(to)) { reply(250, "Renamed"); listener.changed(from); listener.changed(to) } else reply(553, "Couldn't rename it")
        }

        private fun setTime(arg: String) {
            val space = arg.indexOf(' ')
            if (space < 0) { reply(501, "MFMT time path"); return }
            val at = parseStamp(arg.substring(0, space)) ?: run { reply(501, "Bad time"); return }
            val f = existing(arg.substring(space + 1)) ?: return
            f.setLastModified(at)
            reply(213, "Modify=${stamp(at)}; ${virtual(arg.substring(space + 1))}")
        }

        // Replies

        private fun reply(code: Int, text: String) = write("$code $text\r\n")

        private fun lines(code: Int, body: List<String>, end: String) =
            write(body.mapIndexed { i, l -> if (i == 0) "$code-$l" else l }.joinToString("\r\n", postfix = "\r\n") + "$code $end\r\n")

        private fun write(s: String) {
            output.write(s.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }
    }

    companion object {
        const val DEFAULT_PORT = 2121
        private const val IDLE_MS = 10 * 60_000
        private const val DATA_WAIT_MS = 30_000
        private const val BUFFER = 64 * 1024

        private fun utc(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun stamp(ms: Long): String = utc("yyyyMMddHHmmss").format(Date(ms))
        fun parseStamp(s: String): Long? = runCatching { utc("yyyyMMddHHmmss").parse(s.take(14))?.time }.getOrNull()
    }
}

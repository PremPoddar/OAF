package com.prempoddar.oaf.files.remote

import kotlinx.serialization.Serializable
import java.io.Closeable
import java.io.InputStream

/** A file server the user has saved: a NAS, a computer sharing a folder, or a web drive. */
@Serializable
data class Server(
    val id: String,
    val type: Type,
    val name: String,
    val host: String,
    val port: Int = type.port,
    /** Folder to start in. For SMB its first part is the share's name. */
    val path: String = "/",
    val user: String = "",
    /** FTPS for FTP, HTTPS for WebDAV. */
    val secure: Boolean = false,
    /** The password, encrypted with a key only this phone holds; empty for none. */
    val secret: String = "",
) {
    enum class Type(val label: String, val port: Int) { SMB("Windows / SMB", 445), SFTP("SFTP (SSH)", 22), FTP("FTP", 21), WEBDAV("WebDAV", 443) }

    /** "smb://nas.local/Media" and the like, for showing where it is. */
    val address: String get() {
        val scheme = when (type) { Type.SMB -> "smb"; Type.SFTP -> "sftp"; Type.FTP -> if (secure) "ftps" else "ftp"; Type.WEBDAV -> if (secure) "davs" else "dav" }
        val p = if (port == type.port || (type == Type.WEBDAV && !secure && port == 80)) "" else ":$port"
        return "$scheme://$host$p${RemotePath.normal(path).takeIf { it != "/" }.orEmpty()}"
    }
}

/** One item in a folder on a server. [path] is absolute from the server's root, "/"-separated. */
data class RemoteEntry(val name: String, val path: String, val dir: Boolean, val size: Long, val modified: Long)

/**
 * A connection to a server, opened by [Remotes.connect]. Calls block, so run them off the main
 * thread; one connection does one thing at a time.
 */
interface RemoteFs : Closeable {
    fun list(path: String): List<RemoteEntry>
    /** The file's bytes; close the stream when done. */
    fun read(path: String): InputStream
    /** Writes [input] to [path], replacing what's there. */
    fun write(path: String, input: InputStream, size: Long)
    /** Removes a file, or a folder and everything in it. */
    fun delete(entry: RemoteEntry)
    fun rename(from: String, to: String)
    fun mkdir(path: String)
}

/** "/"-separated paths on a server, kept tidy. */
object RemotePath {
    fun normal(p: String): String {
        val parts = ArrayList<String>()
        p.replace('\\', '/').split('/').forEach { s ->
            when (s) { "", "." -> Unit; ".." -> parts.removeLastOrNull(); else -> parts += s }
        }
        return "/" + parts.joinToString("/")
    }
    fun join(dir: String, name: String) = normal("$dir/$name")
    fun parent(p: String) = normal(p).substringBeforeLast('/').ifEmpty { "/" }
    fun name(p: String) = normal(p).substringAfterLast('/')
}

object Remotes {
    /** Opens [server] with [password] (empty for none or anonymous). Throws when it can't. */
    fun connect(server: Server, password: String, knownHosts: java.io.File): RemoteFs = when (server.type) {
        Server.Type.FTP -> FtpFs(server, password)
        Server.Type.SFTP -> SftpFs(server, password, knownHosts)
        Server.Type.SMB -> SmbFs(server, password)
        Server.Type.WEBDAV -> WebDavFs(server, password)
    }

    /** Folders first, then by name, as the phone's own folders are shown. */
    fun sorted(list: List<RemoteEntry>) = list.sortedWith(compareBy<RemoteEntry> { !it.dir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** A clear sentence for what went wrong, rather than a library's exception text. */
    fun explain(t: Throwable): String {
        val m = (t.message ?: "").lowercase()
        return when {
            t is java.net.UnknownHostException || "unknownhost" in m || "unable to resolve" in m -> "Couldn't find that server. Check the address, and that you're on the same network."
            t is java.net.ConnectException || "refused" in m -> "The server didn't answer on that port. Is file sharing turned on?"
            t is java.net.SocketTimeoutException || "timed out" in m || "timeout" in m -> "The server took too long to answer."
            "auth" in m || "login" in m || "logon" in m || "password" in m || "401" in m || "530" in m -> "The user name or password wasn't accepted."
            "host key" in m || "hostkey" in m -> "This server's identity has changed since you last connected. If you didn't expect that, don't continue."
            "bad_network_name" in m || "network_name" in m -> "There's no shared folder with that name on this server."
            "access" in m && "denied" in m || "403" in m || "permission" in m -> "You don't have permission for that on this server."
            else -> t.message ?: "Something went wrong talking to the server."
        }
    }
}

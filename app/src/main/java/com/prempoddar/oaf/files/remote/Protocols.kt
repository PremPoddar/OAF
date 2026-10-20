package com.prempoddar.oaf.files.remote

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------- FTP / FTPS

internal class FtpFs(server: Server, password: String) : RemoteFs {
    private val c: FTPClient = if (server.secure) FTPSClient(false) else FTPClient()

    init {
        c.connectTimeout = 15_000
        c.controlEncoding = "UTF-8"
        c.connect(server.host, server.port)
        if (!FTPReply.isPositiveCompletion(c.replyCode)) { c.disconnect(); throw IOException("The FTP server refused the connection") }
        val user = server.user.ifEmpty { "anonymous" }
        if (!c.login(user, if (server.user.isEmpty()) "oaf@" else password)) { c.disconnect(); throw IOException("Login refused (530)") }
        (c as? FTPSClient)?.let { it.execPBSZ(0); it.execPROT("P") }
        c.enterLocalPassiveMode()
        c.setFileType(FTP.BINARY_FILE_TYPE)
        c.soTimeout = 30_000
    }

    @Synchronized override fun list(path: String) = c.listFiles(RemotePath.normal(path))
        .filter { it.name != "." && it.name != ".." }
        .map { RemoteEntry(it.name, RemotePath.join(path, it.name), it.isDirectory, it.size, it.timestamp?.timeInMillis ?: 0) }

    @Synchronized override fun read(path: String): InputStream {
        val s = c.retrieveFileStream(RemotePath.normal(path)) ?: throw IOException("Couldn't read ${RemotePath.name(path)} (${c.replyString.trim()})")
        // The transfer only finishes once the server confirms it, after the stream is closed.
        return object : FilterInputStream(s) { override fun close() { super.close(); synchronized(this@FtpFs) { c.completePendingCommand() } } }
    }

    @Synchronized override fun write(path: String, input: InputStream, size: Long) {
        if (!c.storeFile(RemotePath.normal(path), input)) throw IOException("Couldn't save ${RemotePath.name(path)} (${c.replyString.trim()})")
    }

    @Synchronized override fun delete(entry: RemoteEntry) {
        if (entry.dir) {
            list(entry.path).forEach { delete(it) }
            if (!c.removeDirectory(entry.path)) throw IOException("Couldn't delete ${entry.name}")
        } else if (!c.deleteFile(entry.path)) throw IOException("Couldn't delete ${entry.name}")
    }

    @Synchronized override fun rename(from: String, to: String) { if (!c.rename(RemotePath.normal(from), RemotePath.normal(to))) throw IOException("Couldn't rename") }
    @Synchronized override fun mkdir(path: String) { if (!c.makeDirectory(RemotePath.normal(path))) throw IOException("Couldn't make the folder") }
    override fun close() { runCatching { c.logout() }; runCatching { c.disconnect() } }
}

// ---------------------------------------------------------------- SFTP

internal class SftpFs(server: Server, password: String, knownHosts: java.io.File) : RemoteFs {
    private val session = JSch().apply {
        knownHosts.parentFile?.mkdirs(); if (!knownHosts.exists()) knownHosts.createNewFile()
        setKnownHosts(knownHosts.path)
    }.getSession(server.user, server.host, server.port).apply {
        if (password.isNotEmpty()) setPassword(password)
        // Trust a server the first time, then insist it's the same one (as ssh does).
        setConfig("StrictHostKeyChecking", "accept-new")
        setConfig("PreferredAuthentications", "password,keyboard-interactive")
        connect(15_000)
    }
    private val ch = (session.openChannel("sftp") as ChannelSftp).apply { connect(15_000) }

    @Synchronized override fun list(path: String): List<RemoteEntry> {
        val out = ArrayList<RemoteEntry>()
        ch.ls(RemotePath.normal(path)) { e ->
            if (e.filename != "." && e.filename != "..") out += RemoteEntry(e.filename, RemotePath.join(path, e.filename), e.attrs.isDir, e.attrs.size, e.attrs.mTime * 1000L)
            ChannelSftp.LsEntrySelector.CONTINUE
        }
        return out
    }

    @Synchronized override fun read(path: String): InputStream = ch.get(RemotePath.normal(path))
    @Synchronized override fun write(path: String, input: InputStream, size: Long) = ch.put(input, RemotePath.normal(path))

    @Synchronized override fun delete(entry: RemoteEntry) {
        if (entry.dir) { list(entry.path).forEach { delete(it) }; ch.rmdir(entry.path) } else ch.rm(entry.path)
    }

    @Synchronized override fun rename(from: String, to: String) = ch.rename(RemotePath.normal(from), RemotePath.normal(to))
    @Synchronized override fun mkdir(path: String) = ch.mkdir(RemotePath.normal(path))
    override fun close() { runCatching { ch.disconnect() }; runCatching { session.disconnect() } }
}

// ---------------------------------------------------------------- SMB

internal class SmbFs(server: Server, password: String) : RemoteFs {
    private val client = SMBClient()
    private val conn = client.connect(server.host, server.port)
    private val session = conn.authenticate(
        if (server.user.isEmpty()) AuthenticationContext.anonymous()
        else {
            // "DOMAIN\user" or "user@domain" both work.
            val (domain, name) = when {
                '\\' in server.user -> server.user.substringBefore('\\') to server.user.substringAfter('\\')
                '@' in server.user -> server.user.substringAfter('@') to server.user.substringBefore('@')
                else -> null to server.user
            }
            AuthenticationContext(name, password.toCharArray(), domain)
        },
    )
    /** The share is the first part of every path: "/Media/Films" is Films in the Media share. */
    private val shareName = RemotePath.normal(server.path).split('/').getOrNull(1)?.takeIf { it.isNotEmpty() }
        ?: throw IOException("Add the shared folder's name to the path, like /Media (bad_network_name)")
    private val share = session.connectShare(shareName) as DiskShare

    /** The path inside the share, in SMB's backslash form. */
    private fun inShare(path: String): String {
        val parts = RemotePath.normal(path).split('/').drop(2)
        return parts.joinToString("\\")
    }

    @Synchronized override fun list(path: String) = share.list(inShare(path))
        .filter { it.fileName != "." && it.fileName != ".." }
        .map {
            val dir = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            RemoteEntry(it.fileName, RemotePath.join(path, it.fileName), dir, it.endOfFile, it.lastWriteTime.toEpochMillis())
        }

    @Synchronized override fun read(path: String): InputStream {
        val f = share.openFile(inShare(path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        return object : FilterInputStream(f.inputStream) { override fun close() { super.close(); f.close() } }
    }

    @Synchronized override fun write(path: String, input: InputStream, size: Long) {
        share.openFile(inShare(path), EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null).use { f ->
            f.outputStream.use { input.copyTo(it) }
        }
    }

    @Synchronized override fun delete(entry: RemoteEntry) { if (entry.dir) share.rmdir(inShare(entry.path), true) else share.rm(inShare(entry.path)) }

    @Synchronized override fun rename(from: String, to: String) {
        val dir = share.folderExists(inShare(from))
        val access = EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE)
        val e = if (dir) share.openDirectory(inShare(from), access, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        else share.openFile(inShare(from), access, null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        e.use { it.rename(inShare(to), false) }
    }

    @Synchronized override fun mkdir(path: String) = share.mkdir(inShare(path))
    override fun close() { runCatching { share.close() }; runCatching { session.close() }; runCatching { conn.close() }; runCatching { client.close() } }
}

// ---------------------------------------------------------------- WebDAV

internal class WebDavFs(private val server: Server, password: String) : RemoteFs {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val auth = if (server.user.isEmpty()) null else Credentials.basic(server.user, password)
    private val base = (if (server.secure) "https" else "http") + "://" + server.host +
        (if ((server.secure && server.port == 443) || (!server.secure && server.port == 80)) "" else ":${server.port}")

    private fun url(path: String) = base + RemotePath.normal(path).split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun call(b: Request.Builder): okhttp3.Response {
        auth?.let { b.header("Authorization", it) }
        val r = http.newCall(b.build()).execute()
        if (!r.isSuccessful) { r.close(); throw IOException("The server said ${r.code} ${r.message}") }
        return r
    }

    override fun list(path: String): List<RemoteEntry> {
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
        val xml = call(Request.Builder().url(url(path).trimEnd('/') + "/").method("PROPFIND", body.toRequestBody("application/xml".toMediaType())).header("Depth", "1")).use { it.body!!.string() }
        return WebDav.parse(xml, RemotePath.normal(path))
    }

    override fun read(path: String): InputStream {
        val r = call(Request.Builder().url(url(path)).get())
        return object : FilterInputStream(r.body!!.byteStream()) { override fun close() { super.close(); r.close() } }
    }

    override fun write(path: String, input: InputStream, size: Long) {
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) { input.source().use { sink.writeAll(it) } }
        }
        call(Request.Builder().url(url(path)).put(body)).close()
    }

    override fun delete(entry: RemoteEntry) = call(Request.Builder().url(url(entry.path)).delete()).close()
    override fun rename(from: String, to: String) = call(Request.Builder().url(url(from)).method("MOVE", null).header("Destination", url(to)).header("Overwrite", "F")).close()
    override fun mkdir(path: String) = call(Request.Builder().url(url(path)).method("MKCOL", null)).close()
    override fun close() { http.connectionPool.evictAll() }
}

/** Reading a WebDAV folder listing (a PROPFIND "multistatus"). */
object WebDav {
    fun parse(xml: String, dir: String): List<RemoteEntry> {
        val f = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = f.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        val dav = "DAV:"
        val here = RemotePath.normal(dir)
        val responses = doc.getElementsByTagNameNS(dav, "response")
        val out = ArrayList<RemoteEntry>()
        for (i in 0 until responses.length) {
            val r = responses.item(i) as org.w3c.dom.Element
            fun text(name: String) = r.getElementsByTagNameNS(dav, name).item(0)?.textContent?.trim()
            val href = text("href") ?: continue
            // hrefs may be full URLs or paths, and are percent-encoded.
            val rawPath = runCatching { java.net.URI(href).rawPath }.getOrNull() ?: href
            val path = RemotePath.normal(java.net.URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8"))
            if (path == here) continue
            val isDir = r.getElementsByTagNameNS(dav, "collection").length > 0
            val modified = text("getlastmodified")?.let { runCatching { java.time.ZonedDateTime.parse(it, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() } ?: 0L
            out += RemoteEntry(RemotePath.name(path), path, isDir, text("getcontentlength")?.toLongOrNull() ?: 0L, modified)
        }
        return out
    }
}

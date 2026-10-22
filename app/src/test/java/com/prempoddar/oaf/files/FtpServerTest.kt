package com.prempoddar.oaf.files

import com.google.common.truth.Truth.assertThat
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Drives the server with a real FTP client, as a computer on the network would. */
class FtpServerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var root: File
    private var server: FtpServer? = null
    private val client = FTPClient().apply { controlEncoding = "UTF-8" }

    @Before fun setUp() {
        root = tmp.newFolder("share")
        File(root, "notes.txt").writeText("hello from the phone")
        File(root, "Photos").mkdir()
        // Outside the shared folder: must never be reachable.
        File(tmp.root, "secret.txt").writeText("keep out")
    }

    @After fun tearDown() {
        runCatching { client.disconnect() }
        server?.stop()
    }

    private fun start(config: FtpServer.Config = FtpServer.Config(root, port = 0)): FtpServer {
        val s = FtpServer(config.copy(root = root, port = 0)).also { it.start() }
        server = s
        client.connect("127.0.0.1", s.port)
        client.enterLocalPassiveMode()
        return s
    }

    private fun signIn(user: String = "anonymous", password: String = "x") {
        assertThat(client.login(user, password)).isTrue()
        client.setFileType(FTP.BINARY_FILE_TYPE)
    }

    private fun download(path: String): String? {
        val out = ByteArrayOutputStream()
        return if (client.retrieveFile(path, out)) out.toString(Charsets.UTF_8) else null
    }

    @Test fun listsDownloadsAndUploads() {
        start(); signIn()
        assertThat(client.listFiles().map { it.name }).containsExactly("notes.txt", "Photos")
        assertThat(client.listFiles().first { it.name == "Photos" }.isDirectory).isTrue()
        assertThat(client.mlistDir().map { it.name }).containsExactly("notes.txt", "Photos")
        assertThat(download("notes.txt")).isEqualTo("hello from the phone")

        assertThat(client.changeWorkingDirectory("Photos")).isTrue()
        assertThat(client.printWorkingDirectory()).isEqualTo("/Photos")
        assertThat(client.storeFile("beach.jpg", ByteArrayInputStream(ByteArray(300_000) { it.toByte() }))).isTrue()
        assertThat(File(root, "Photos/beach.jpg").length()).isEqualTo(300_000)
        assertThat(client.listFiles().single().size).isEqualTo(300_000)
    }

    @Test fun resumesAPartDownload() {
        start(); signIn()
        client.restartOffset = 11
        assertThat(download("notes.txt")).isEqualTo("the phone")
    }

    @Test fun renamesDeletesAndMakesFolders() {
        start(); signIn()
        assertThat(client.makeDirectory("Work")).isTrue()
        assertThat(File(root, "Work").isDirectory).isTrue()
        assertThat(client.rename("notes.txt", "Work/notes.md")).isTrue()
        assertThat(File(root, "Work/notes.md").readText()).isEqualTo("hello from the phone")
        assertThat(client.removeDirectory("Work")).isFalse() // not empty
        assertThat(client.deleteFile("Work/notes.md")).isTrue()
        assertThat(client.removeDirectory("Work")).isTrue()
        assertThat(File(root, "Work").exists()).isFalse()
    }

    @Test fun namesInAnyLanguage() {
        start(); signIn()
        assertThat(client.storeFile("Café ☕ 日本.txt", ByteArrayInputStream("ok".toByteArray()))).isTrue()
        assertThat(File(root, "Café ☕ 日本.txt").readText()).isEqualTo("ok")
        assertThat(client.listNames().toList()).contains("Café ☕ 日本.txt")
    }

    @Test fun nothingOutsideTheSharedFolder() {
        start(); signIn()
        assertThat(download("../secret.txt")).isNull()
        assertThat(download("/../../secret.txt")).isNull()
        client.changeWorkingDirectory("../..")
        assertThat(client.printWorkingDirectory()).isEqualTo("/")
        assertThat(client.listNames().toList()).doesNotContain("secret.txt")
        assertThat(client.storeFile("../escaped.txt", ByteArrayInputStream("x".toByteArray()))).isTrue()
        // "../escaped.txt" lands in the shared folder itself, never above it.
        assertThat(File(tmp.root, "escaped.txt").exists()).isFalse()
        assertThat(File(root, "escaped.txt").exists()).isTrue()
    }

    @Test fun passwordIsChecked() {
        start(FtpServer.Config(root, user = "prem", password = "s3cret"))
        assertThat(client.login("prem", "wrong")).isFalse()
        assertThat(client.listNames()).isNull()
        assertThat(client.login("prem", "s3cret")).isTrue()
        assertThat(client.listNames().toList()).contains("notes.txt")
    }

    @Test fun readOnlyShareRefusesChanges() {
        start(FtpServer.Config(root, readOnly = true)); signIn()
        assertThat(download("notes.txt")).isEqualTo("hello from the phone")
        assertThat(client.storeFile("new.txt", ByteArrayInputStream("x".toByteArray()))).isFalse()
        assertThat(client.deleteFile("notes.txt")).isFalse()
        assertThat(client.makeDirectory("New")).isFalse()
        assertThat(File(root, "notes.txt").exists()).isTrue()
        assertThat(File(root, "new.txt").exists()).isFalse()
    }

    @Test fun sizeAndTimes() {
        start(); signIn()
        val f = File(root, "notes.txt")
        f.setLastModified(1_700_000_000_000)
        assertThat(client.getSize("notes.txt")).isEqualTo(f.length().toString())
        assertThat(client.getModificationTime("notes.txt")).isEqualTo(FtpServer.stamp(1_700_000_000_000))
        assertThat(client.setModificationTime("notes.txt", "20240101120000")).isTrue()
        assertThat(FtpServer.stamp(f.lastModified())).isEqualTo("20240101120000")
    }
}

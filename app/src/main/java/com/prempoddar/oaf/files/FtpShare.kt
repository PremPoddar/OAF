package com.prempoddar.oaf.files

import android.content.Context
import android.content.Intent
import com.prempoddar.oaf.files.remote.Secrets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/** How this phone's files are shared over FTP. The password is kept encrypted. */
@Serializable
data class FtpSettings(
    /** The folder shared; empty for the whole of the phone's storage. */
    val folder: String = "",
    val port: Int = FtpServer.DEFAULT_PORT,
    /** Let anyone on the network in without a password. */
    val anonymous: Boolean = false,
    val user: String = "oaf",
    val secret: String = "",
    val readOnly: Boolean = false,
    /** Keep the CPU and Wi-Fi awake so transfers carry on with the screen off. */
    val keepAwake: Boolean = true,
)

/** Starting and stopping the share, and what it's doing, for the screen and the service. */
object FtpShare {
    data class State(
        val running: Boolean = false,
        val starting: Boolean = false,
        /** ftp:// addresses others can use, one per network this phone is on. */
        val addresses: List<String> = emptyList(),
        val clients: Int = 0,
        val log: List<String> = emptyList(),
        val error: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val _settings = MutableStateFlow<FtpSettings?>(null)
    /** Set when the notification is tapped, so the app opens on the share screen. */
    val openScreen = MutableStateFlow(false)

    private fun file(context: Context) = File(context.filesDir, "ftp.json")

    fun settings(context: Context): StateFlow<FtpSettings?> {
        if (_settings.value == null) {
            var s = runCatching { json.decodeFromString(FtpSettings.serializer(), file(context).readText()) }.getOrNull() ?: FtpSettings()
            // A password from the start, so the share is never open by accident.
            if (s.secret.isEmpty()) s = s.copy(secret = Secrets.encrypt(newPassword())).also { save(context, it) }
            _settings.value = s
        }
        return _settings
    }

    fun save(context: Context, s: FtpSettings) {
        _settings.value = s
        file(context).writeText(json.encodeToString(FtpSettings.serializer(), s))
    }

    fun password(s: FtpSettings): String = runCatching { Secrets.decrypt(s.secret) }.getOrDefault("")

    fun newPassword(): String {
        val r = SecureRandom()
        return (1..10).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    /** The shared folder: the chosen one, or all of the phone's storage. */
    fun root(s: FtpSettings): File = s.folder.takeIf { it.isNotEmpty() }?.let(::File) ?: android.os.Environment.getExternalStorageDirectory()

    fun start(context: Context) {
        update { it.copy(error = null, starting = true) }
        context.startForegroundService(Intent(context, FtpService::class.java))
    }

    fun stop(context: Context) {
        context.startService(Intent(context, FtpService::class.java).setAction(FtpService.STOP))
    }

    internal fun update(f: (State) -> State) { _state.value = f(_state.value) }

    internal fun log(line: String) {
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
        update { it.copy(log = (listOf("$time  $line") + it.log).take(40)) }
    }

    /** This phone's addresses on local networks (Wi-Fi, its own hotspot, Ethernet). */
    fun addresses(port: Int): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { "ftp://${it.hostAddress}:$port" }
            .distinct()
    }.getOrDefault(emptyList())

    // No look-alikes (0/O, 1/l/I), so it can be read off the screen and typed on a computer.
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
}

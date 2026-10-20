package com.prempoddar.oaf.files.remote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The saved servers, kept in the app's private storage. Passwords are encrypted with a key in the
 * phone's secure hardware ([Secrets]), so the file alone doesn't give them away.
 */
class Servers(private val dir: File) {
    private val file = File(dir, "servers.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val _list = MutableStateFlow(load())
    val list: StateFlow<List<Server>> = _list.asStateFlow()
    val knownHosts get() = File(dir, "known_hosts")

    private fun load(): List<Server> = runCatching { json.decodeFromString(ListSerializer(Server.serializer()), file.readText()) }.getOrDefault(emptyList())

    @Synchronized fun save(server: Server, password: String?) {
        val withSecret = if (password == null) server else server.copy(secret = if (password.isEmpty()) "" else Secrets.encrypt(password))
        val next = _list.value.filter { it.id != server.id } + withSecret
        dir.mkdirs()
        file.writeText(json.encodeToString(ListSerializer(Server.serializer()), next))
        _list.value = next
    }

    @Synchronized fun remove(id: String) {
        val next = _list.value.filter { it.id != id }
        file.writeText(json.encodeToString(ListSerializer(Server.serializer()), next))
        _list.value = next
    }

    fun get(id: String) = _list.value.firstOrNull { it.id == id }
    fun password(s: Server): String = if (s.secret.isEmpty()) "" else runCatching { Secrets.decrypt(s.secret) }.getOrDefault("")
}

/** AES-GCM with a key that never leaves the Android Keystore. */
object Secrets {
    private const val ALIAS = "oaf_servers"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12)) }
        return String(c.doFinal(b, 12, b.size - 12))
    }
}

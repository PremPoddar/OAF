package com.prempoddar.oaf.files

import com.android.apksig.ApkVerifier
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * Merges a real split app into one APK. Needs split parts on this computer (set OAF_SPLIT_DIR or
 * keep them in ~/Desktop/Waypoint 1.9.1); skipped otherwise.
 */
class UniversalTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun splitAppBecomesOneSignedApk() {
        val dir = File(System.getenv("OAF_SPLIT_DIR") ?: (System.getProperty("user.home") + "/Desktop/Waypoint 1.9.1"))
        val parts = dir.listFiles { f -> f.name.endsWith(".apk") }?.sortedBy { if (it.name == "base.apk") 0 else 1 }.orEmpty()
        assumeTrue("no split parts here", parts.size > 1)

        val jks = File(tmp.root, "k.jks")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        ProcessBuilder(keytool, "-genkeypair", "-keystore", jks.path, "-storepass", "testpass", "-keypass", "testpass", "-alias", "t",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "365", "-dname", "CN=Test").inheritIO().start().waitFor()
        val ks = KeyStore.getInstance("JKS").apply { jks.inputStream().use { load(it, "testpass".toCharArray()) } }
        val signer = Universal.Signer(ks.getKey("t", "testpass".toCharArray()) as PrivateKey, listOf(ks.getCertificate("t") as X509Certificate))

        val out = File(System.getenv("OAF_UNIVERSAL_OUT") ?: File(tmp.root, "universal.apk").path)
        Universal.make(parts, out, signer, tmp.newFolder())

        val result = ApkVerifier.Builder(out).build().verify()
        assertThat(result.errors).isEmpty()
        assertThat(result.isVerified).isTrue()
        ZipFile(out).use { z ->
            val names = z.entries().asSequence().map { it.name }.toList()
            // The processor's native code, from its split, is now inside.
            if (parts.any { it.name.contains("arm64") }) assertThat(names.any { it.startsWith("lib/arm64-v8a/") }).isTrue()
            assertThat(names).contains("AndroidManifest.xml")
            assertThat(names).contains("resources.arsc")
        }
        // Android mustn't ask for the parts any more.
        val manifest = com.reandroid.apk.ApkModule.loadApkFile(out).androidManifest
        val attrs = listOfNotNull(manifest.applicationElement, manifest.applicationElement?.parentElement)
            .flatMap { e -> listOf(0x01010591, 0x0101064e, 0x0101064f).mapNotNull { e.searchAttributeByResourceId(it) } }
        assertThat(attrs).isEmpty()
    }
}

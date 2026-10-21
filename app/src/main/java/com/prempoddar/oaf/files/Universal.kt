package com.prempoddar.oaf.files

import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkBundle
import com.reandroid.arsc.chunk.xml.ResXmlElement
import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * One installable APK from an app that ships in parts (a base and "splits" for the screen,
 * processor and language), so anyone can install it with Android's own installer.
 *
 * The parts are merged, the manifest stops asking for splits, and the result is signed with a
 * key made on this phone. Because that key isn't the developer's, the copy installs on phones that
 * don't have the app but can't update an existing install from the Play Store.
 */
object Universal {
    /** Signing identity: a key and its certificate. */
    class Signer(val key: PrivateKey, val chain: List<X509Certificate>)

    /** Manifest attributes and meta-data that make Android insist on the split parts. */
    private val splitAttributes = setOf("isSplitRequired", "requiredSplitTypes", "splitTypes")
    /** The same attributes by Android's ids, which is how compiled manifests usually name them. */
    private val splitAttributeIds = listOf(0x01010591, 0x0101064e, 0x0101064f)
    private val splitMetaData = setOf(
        "com.android.vending.splits.required", "com.android.vending.splits",
        "com.android.stamp.source", "com.android.stamp.type", "com.android.vending.derived.apk.id",
    )

    /** Merges the APKs in [parts] (base first or named base.apk) into a signed APK at [out]. */
    fun make(parts: List<File>, out: File, signer: Signer, workDir: File) {
        val dir = File(workDir, "merge-${System.nanoTime()}").apply { mkdirs() }
        try {
            // The library reads a folder of APKs and finds the base by its manifest.
            parts.forEachIndexed { i, f -> f.copyTo(File(dir, if (i == 0) "base.apk" else "split_$i.apk")) }
            val unsigned = File(workDir, "unsigned-${System.nanoTime()}.apk")
            ApkBundle().use { bundle ->
                bundle.loadApkDirectory(dir)
                val merged = bundle.mergeModules()
                val manifest = merged.androidManifest
                val app = manifest.applicationElement
                val root = app?.parentElement
                listOfNotNull(root, app).forEach { e ->
                    splitAttributes.forEach { e.removeAttributesWithName(it) }
                    splitAttributeIds.forEach { e.removeAttributesWithId(it) }
                }
                app?.removeElements { child: ResXmlElement ->
                    child.name == "meta-data" && child.searchAttributeByName("name")?.valueString in splitMetaData
                }
                merged.refreshManifest()
                merged.writeApk(unsigned)
            }
            try {
                ApkSigner.Builder(listOf(ApkSigner.SignerConfig.Builder("OAF", signer.key, signer.chain).build()))
                    .setInputApk(unsigned).setOutputApk(out)
                    .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false)
                    .setMinSdkVersion(24)
                    .build().sign()
            } finally { unsigned.delete() }
        } finally { dir.deleteRecursively() }
    }

    /** OAF's own signing key, made once in the phone's Keystore with a self-signed certificate. */
    fun phoneSigner(): Signer {
        val alias = "oaf_universal_apk"
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            java.security.KeyPairGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(
                    android.security.keystore.KeyGenParameterSpec.Builder(alias, android.security.keystore.KeyProperties.PURPOSE_SIGN)
                        .setKeySize(2048)
                        .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=OAF universal APK"))
                        .setCertificateNotAfter(java.util.Date(System.currentTimeMillis() + 30L * 365 * 24 * 3600 * 1000))
                        .build(),
                )
            }.generateKeyPair()
        }
        val key = ks.getKey(alias, null) as PrivateKey
        val cert = ks.getCertificate(alias) as X509Certificate
        return Signer(key, listOf(cert))
    }
}

package com.prempoddar.oaf.files

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** An installed app, as the Apps screen lists it. */
data class InstalledApp(
    val pkg: String,
    val label: String,
    val version: String,
    val versionCode: Long,
    val system: Boolean,
    val size: Long,
    val installed: Long,
    val updated: Long,
    val apks: List<File>,
) {
    /** Split apps ship several APKs; they can only be shared together as one .apks bundle. */
    val split get() = apks.size > 1
}

object Apks {
    /** Last full list, so coming back to the Apps screen is instant. */
    @Volatile var cached: List<InstalledApp>? = null
        private set

    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val icons = object : android.util.LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * Every installed app. Reading labels is the slow part, so they're read in parallel and
     * remembered on disk by version: after the first visit only new or updated apps cost anything.
     */
    suspend fun list(context: Context): List<InstalledApp> = kotlinx.coroutines.coroutineScope {
        val pm = context.packageManager
        val store = File(context.cacheDir, "app-labels.txt")
        if (labels.isEmpty()) runCatching {
            store.readLines().forEach { line -> line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> labels[k] = v } }
        }
        @Suppress("DEPRECATION")
        val infos: List<PackageInfo> = pm.getInstalledPackages(0)
        val io = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(8)
        val apps = infos.map { pi ->
            async(io) {
                val ai = pi.applicationInfo ?: return@async null
                val key = pi.packageName + "@" + pi.lastUpdateTime
                val label = labels[key] ?: runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pi.packageName).also { labels[key] = it }
                val apks = listOf(File(ai.sourceDir)) + ai.splitSourceDirs.orEmpty().map(::File)
                InstalledApp(
                    pkg = pi.packageName,
                    label = label,
                    version = pi.versionName ?: "",
                    versionCode = pi.longVersionCode,
                    system = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    size = apks.sumOf { it.length() },
                    installed = pi.firstInstallTime,
                    updated = pi.lastUpdateTime,
                    apks = apks,
                )
            }
        }.awaitAll().filterNotNull().sortedBy { it.label.lowercase() }
        runCatching { store.writeText(labels.entries.joinToString("\n") { it.key + "\t" + it.value.replace('\n', ' ') }) }
        cached = apps
        apps
    }

    fun icon(context: Context, pkg: String, px: Int): Bitmap? {
        icons.get("$pkg:$px")?.let { return it }
        return runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(px, px) }.getOrNull()?.also { icons.put("$pkg:$px", it) }
    }

    fun cachedIcon(pkg: String, px: Int): Bitmap? = icons.get("$pkg:$px")

    /** How a split app goes out: one merged APK, one bundle file, or its parts as they are. */
    enum class Form { UNIVERSAL, BUNDLE }

    private fun cleanName(app: InstalledApp) = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifEmpty { app.pkg }

    /** A file name people will recognise: "Signal 7.12.apk". */
    fun fileName(app: InstalledApp, form: Form = Form.UNIVERSAL): String =
        "${cleanName(app)} ${app.version}".trim() + if (app.split && form == Form.BUNDLE) ".apks" else ".apk"

    /** What one part of a split app is for, in words: "Processor: arm64-v8a", "Language: English". */
    fun partLabel(app: InstalledApp, apk: File): String {
        if (apk == app.apks.first()) return "Main app"
        val name = apk.nameWithoutExtension.removePrefix("split_")
        val config = name.substringAfter("config.", "")
        return when {
            config.isEmpty() -> "Feature: ${name.replace('_', ' ')}"
            config in setOf("arm64_v8a", "armeabi_v7a", "x86", "x86_64", "armeabi") -> "Processor: ${config.replace('_', '-')}"
            config.endsWith("dpi") -> "Screen: $config"
            else -> "Language: " + java.util.Locale.forLanguageTag(config.replace('_', '-')).displayName.ifEmpty { config }
        }
    }

    /** The parts themselves, named after the app and what each is for, ready to share. */
    fun exportParts(app: InstalledApp, parts: List<File>, dir: File): List<File> {
        dir.mkdirs()
        return parts.map { apk ->
            val what = if (apk == app.apks.first()) "base" else apk.nameWithoutExtension.removePrefix("split_")
            FileOps.freeName(dir, "${cleanName(app)} ${app.version} $what.apk".replace("  ", " ")).also { apk.copyTo(it) }
        }
    }

    /**
     * Writes the app into [dir] and returns it: its APK, or for a split app one merged APK
     * ([Form.UNIVERSAL], made with [signer]) or a bundle of the parts ([Form.BUNDLE]).
     */
    fun export(app: InstalledApp, dir: File, form: Form = Form.UNIVERSAL, signer: () -> Universal.Signer = { Universal.phoneSigner() }, work: File = dir): File {
        dir.mkdirs()
        val out = FileOps.freeName(dir, fileName(app, form))
        if (!app.split) {
            app.apks.first().copyTo(out)
        } else if (form == Form.UNIVERSAL) {
            Universal.make(app.apks, out, signer(), work)
        } else {
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                app.apks.forEach { apk ->
                    zip.putNextEntry(ZipEntry(if (apk == app.apks.first()) "base.apk" else apk.name))
                    apk.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return out
    }

    /** Shared copies live in the cache and are cleared on the next start after a day. */
    fun shareDir(context: Context) = File(context.cacheDir, "open/apps")

    /** Saved copies go to Downloads/OAF Apps, where other apps and computers can see them. */
    fun saveDir(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OAF Apps")

    fun canLaunch(context: Context, pkg: String) = context.packageManager.getLaunchIntentForPackage(pkg) != null

    fun isInstalled(pm: PackageManager, pkg: String) = runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
}

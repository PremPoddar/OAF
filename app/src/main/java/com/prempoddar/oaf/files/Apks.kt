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

    /** A file name people will recognise: "Signal 7.12.apk". */
    fun fileName(app: InstalledApp): String {
        val clean = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifEmpty { app.pkg }
        return "$clean ${app.version}".trim() + if (app.split) ".apks" else ".apk"
    }

    /** Writes the app's package (or bundle of splits) into [dir] and returns it. */
    fun export(app: InstalledApp, dir: File): File {
        dir.mkdirs()
        val out = FileOps.freeName(dir, fileName(app))
        if (!app.split) {
            app.apks.first().copyTo(out)
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

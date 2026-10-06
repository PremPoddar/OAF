package com.prempoddar.oaf.files

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import androidx.core.graphics.drawable.toBitmap
import java.io.File
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
    fun list(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val infos: List<PackageInfo> = pm.getInstalledPackages(0)
        return infos.mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            val apks = listOf(File(ai.sourceDir)) + ai.splitSourceDirs.orEmpty().map(::File)
            InstalledApp(
                pkg = pi.packageName,
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pi.packageName),
                version = pi.versionName ?: "",
                versionCode = pi.longVersionCode,
                system = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                size = apks.sumOf { it.length() },
                installed = pi.firstInstallTime,
                updated = pi.lastUpdateTime,
                apks = apks,
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun icon(context: Context, pkg: String, px: Int): Bitmap? = runCatching {
        context.packageManager.getApplicationIcon(pkg).toBitmap(px, px)
    }.getOrNull()

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

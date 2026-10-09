package com.prempoddar.oaf.files

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import java.io.File
import java.util.zip.ZipFile

/**
 * Installs apps from a single .apk or a split bundle (.apks, .xapk, .apkm: a zip of APKs) using
 * Android's package installer, which shows its own confirmation.
 */
object Installer {
    val BUNDLES = setOf("apks", "xapk", "apkm")

    fun isBundle(file: File) = file.extension.lowercase() in BUNDLES

    /** Whether the user has let OAF install apps (Android asks once per app). */
    fun allowed(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** The APK to read an app's details from: the file itself, or a bundle's base APK copied out. */
    fun baseApk(file: File, cacheDir: File): File {
        if (!isBundle(file)) return file
        ZipFile(file).use { zip ->
            val apks = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.toList()
            val base = apks.firstOrNull { it.name.substringAfterLast('/').equals("base.apk", true) }
                ?: apks.firstOrNull { !it.name.contains("split", true) && !it.name.contains("config.", true) }
                ?: apks.maxByOrNull { it.size } ?: error("There's no app inside this bundle")
            val out = File(cacheDir, "open/bundle-base.apk").apply { parentFile?.mkdirs() }
            zip.getInputStream(base).use { i -> out.outputStream().use { i.copyTo(it) } }
            return out
        }
    }

    /**
     * A single APK goes to Android's own installer screen, which handles permission and errors
     * itself; bundles need an install session ([install]).
     */
    fun openInstaller(context: Context, apk: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // OAF opens any file too, so name the system installer rather than offer a choice.
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(intent, 0)
            .firstOrNull { it.activityInfo.packageName != context.packageName }
            ?.let { intent.setClassName(it.activityInfo.packageName, it.activityInfo.name) }
        context.startActivity(intent)
    }

    /** Starts installing; the result arrives in [Result]. Run off the main thread. */
    fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            fun write(name: String, size: Long, input: java.io.InputStream) {
                session.openWrite(name, 0, size).use { out -> input.copyTo(out); session.fsync(out) }
            }
            if (isBundle(file)) {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.forEachIndexed { i, e ->
                        zip.getInputStream(e).use { write("$i-" + e.name.substringAfterLast('/'), e.size, it) }
                    }
                }
            } else file.inputStream().use { write("base.apk", file.length(), it) }
            val intent = Intent(context, Result::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    /** Hears back from the installer: shows its confirmation, then says how it went. */
    class Result : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "Installed", Toast.LENGTH_SHORT).show()
                PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
                else -> Toast.makeText(context, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { "Couldn't install: $it" } ?: "Couldn't install this app", Toast.LENGTH_LONG).show()
            }
        }
    }
}

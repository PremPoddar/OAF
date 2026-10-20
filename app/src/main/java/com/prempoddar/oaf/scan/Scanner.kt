package com.prempoddar.oaf.scan

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scanning paper into a PDF with Google's document scanner, which runs on the phone through Play
 * services: it finds the page edges, straightens and cleans each page, and OAF needs no camera
 * permission. Scans are saved in Documents/Scans.
 */
object Scanner {
    private val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(100)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    /** Copies the scanner's PDF into Documents/Scans; returns where it went. */
    fun keep(context: Context, pdf: Uri, prefix: String = "Scan"): Uri? = runCatching {
        val name = "$prefix " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date()) + ".pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Scans")
        }
        val out = context.contentResolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        context.contentResolver.openOutputStream(out)!!.use { o -> context.contentResolver.openInputStream(pdf)!!.use { it.copyTo(o) } }
        out
    }.getOrNull()

    /** A function that starts a scan; [onScanned] gets the saved PDF, or null if it failed. */
    @Composable
    fun rememberScan(onScanned: (Uri?) -> Unit): () -> Unit {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pdf?.uri
            scope.launch { onScanned(pdf?.let { withContext(Dispatchers.IO) { keep(context, it) } }) }
        }
        return {
            val activity = context as? Activity
            if (activity == null) onScanned(null)
            else GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { onScanned(null) }
        }
    }
}

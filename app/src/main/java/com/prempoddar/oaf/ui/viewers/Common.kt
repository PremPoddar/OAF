package com.prempoddar.oaf.ui.viewers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.prempoddar.oaf.io.DocSource

/** Hands the file to another app: "open with…". */
fun openElsewhere(context: Context, source: DocSource) {
    val uri: Uri = source.file?.let { FileProvider.getUriForFile(context, context.packageName + ".files", it) } ?: source.uri
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, source.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Open ${source.name} with"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No other app can open this", Toast.LENGTH_SHORT).show()
    }
}

fun shareSource(context: Context, source: DocSource) {
    val uri: Uri = source.file?.let { FileProvider.getUriForFile(context, context.packageName + ".files", it) } ?: source.uri
    context.startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType(source.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ${source.name}"),
    )
}

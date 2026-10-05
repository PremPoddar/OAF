package com.prempoddar.oaf.export

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/** Sends HTML to Android's print dialog, which can also save it as a PDF. */
object Printing {
    /** The page being printed; kept until the next print so the print job can finish reading it. */
    @SuppressLint("StaticFieldLeak")
    private var current: WebView? = null

    fun printHtml(context: Context, job: String, html: String) {
        current?.destroy()
        val web = WebView(context)
        current = web
        web.settings.javaScriptEnabled = false
        web.settings.allowFileAccess = false
        web.settings.blockNetworkLoads = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(job, view.createPrintDocumentAdapter(job), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}

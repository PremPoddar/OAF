package com.prempoddar.oaf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OafApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            PDFBoxResourceLoader.init(this@OafApp)
            // Things in the bin for more than 30 days are removed for good.
            runCatching { AppGraph.bin.expire(System.currentTimeMillis(), 30L * 24 * 60 * 60 * 1000) }
            cacheDir.resolve("open").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        }
    }
}

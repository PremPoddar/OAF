package com.prempoddar.oaf

import android.app.Application
import com.prempoddar.oaf.data.Prefs
import com.prempoddar.oaf.files.Bin
import com.prempoddar.oaf.io.DocSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object AppGraph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
    }

    val prefs by lazy { Prefs(app) }
    /** Deleted files wait here (on the same storage, so deleting is instant) until restored or emptied. */
    val bin by lazy { Bin(File(app.getExternalFilesDir(null) ?: app.filesDir, "bin")) }

    /** Files handed between screens: navigation arguments can only carry a key. */
    private val open = ConcurrentHashMap<String, DocSource>()
    fun put(source: DocSource): String = source.uri.toString().also { open[it] = source }
    fun get(key: String): DocSource? = open[key]
}

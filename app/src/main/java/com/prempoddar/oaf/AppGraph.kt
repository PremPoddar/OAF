package com.prempoddar.oaf

import android.app.Application
import com.prempoddar.oaf.data.Prefs
import com.prempoddar.oaf.data.ThumbType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val servers by lazy { com.prempoddar.oaf.files.remote.Servers(File(app.filesDir, "network")) }

    /** The file types whose previews are shown, kept ready for every row of every list. */
    val thumbTypes by lazy {
        prefs.settings.map { s -> if (s.thumbnails) ThumbType.entries.toSet() - s.thumbsOff else emptySet() }
            .distinctUntilChanged()
            .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, ThumbType.entries.toSet())
    }

    val clipboard by lazy { com.prempoddar.oaf.ui.browser.Clipboard() }

    /**
     * The files of the list a file was last opened from, in the order shown, so the viewer can
     * swipe to the ones beside it (photos in a category sorted by date, say, not by name).
     */
    @Volatile var browsing: List<File> = emptyList()

    /** Files handed between screens: navigation arguments can only carry a key. */
    private val open = ConcurrentHashMap<String, DocSource>()
    fun put(source: DocSource): String = source.uri.toString().also { open[it] = source }
    fun get(key: String): DocSource? = open[key]
}

package com.prempoddar.oaf.data

import com.prempoddar.oaf.ui.design.Appearance
import androidx.datastore.preferences.core.intPreferencesKey
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class SortBy { NAME, DATE, SIZE, TYPE }

@Serializable
data class Recent(
    val uri: String,
    val name: String,
    val kind: String,
    val openedAt: Long,
    /** Where reading stopped: page, scroll offset or sheet — meaning depends on the viewer. */
    val position: Int = 0,
)

data class OafSettings(
    val appearance: Appearance = Appearance(),
    val sort: SortBy = SortBy.NAME,
    /** Z to A, newest, largest first. */
    val descending: Boolean = false,
    /** Light or dark pages in the viewers, whatever the rest of the app uses; SYSTEM follows the app. */
    val viewerMode: Appearance.Mode = Appearance.Mode.SYSTEM,
    val showHidden: Boolean = false,
    val grid: Boolean = false,
    val foldersFirst: Boolean = true,
    /** Ask before moving to the bin. */
    val confirmDelete: Boolean = true,
    val recents: List<Recent> = emptyList(),
    val favourites: List<String> = emptyList(),
)

private val Context.dataStore by preferencesDataStore("settings")

class Prefs(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val strings = ListSerializer(String.serializer())
    private val recentList = ListSerializer(Recent.serializer())

    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val accent = intPreferencesKey("accent")
        val black = booleanPreferencesKey("pure_black")
        val foldersFirst = booleanPreferencesKey("folders_first")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val sort = stringPreferencesKey("sort")
        val descending = booleanPreferencesKey("descending")
        val viewerMode = stringPreferencesKey("viewer_mode")
        val hidden = booleanPreferencesKey("hidden")
        val grid = booleanPreferencesKey("grid")
        val recents = stringPreferencesKey("recents")
        val favourites = stringPreferencesKey("favourites")
    }

    val settings: Flow<OafSettings> = context.dataStore.data.map { it.toSettings() }
    suspend fun current() = settings.first()

    suspend fun update(f: (OafSettings) -> OafSettings) {
        context.dataStore.edit { p ->
            val n = f(p.toSettings())
            p[K.theme] = n.appearance.mode.name
            p[K.dynamic] = n.appearance.dynamic
            p[K.black] = n.appearance.pureBlack
            n.appearance.accent?.let { p[K.accent] = it } ?: p.remove(K.accent)
            p[K.foldersFirst] = n.foldersFirst
            p[K.confirmDelete] = n.confirmDelete
            p[K.sort] = n.sort.name
            p[K.descending] = n.descending
            p[K.viewerMode] = n.viewerMode.name
            p[K.hidden] = n.showHidden
            p[K.grid] = n.grid
            p[K.recents] = json.encodeToString(recentList, n.recents.take(MAX_RECENTS))
            p[K.favourites] = json.encodeToString(strings, n.favourites)
        }
    }

    suspend fun opened(uri: String, name: String, kind: String, now: Long = System.currentTimeMillis()) = update { s ->
        val old = s.recents.firstOrNull { it.uri == uri }
        s.copy(recents = listOf(Recent(uri, name, kind, now, old?.position ?: 0)) + s.recents.filter { it.uri != uri })
    }

    suspend fun position(uri: String): Int = current().recents.firstOrNull { it.uri == uri }?.position ?: 0

    suspend fun savePosition(uri: String, position: Int) = update { s ->
        s.copy(recents = s.recents.map { if (it.uri == uri) it.copy(position = position) else it })
    }

    private fun Preferences.toSettings(): OafSettings {
        val d = OafSettings()
        return OafSettings(
            appearance = Appearance(
                mode = this[K.theme]?.let { runCatching { Appearance.Mode.valueOf(it) }.getOrNull() } ?: Appearance.Mode.SYSTEM,
                dynamic = this[K.dynamic] ?: false,
                accent = this[K.accent],
                pureBlack = this[K.black] ?: false,
            ),
            foldersFirst = this[K.foldersFirst] ?: d.foldersFirst,
            confirmDelete = this[K.confirmDelete] ?: d.confirmDelete,
            sort = this[K.sort]?.let { runCatching { SortBy.valueOf(it) }.getOrNull() } ?: d.sort,
            // Before there was a direction, dates and sizes always listed the newest and largest first.
            descending = this[K.descending] ?: (this[K.sort] == SortBy.DATE.name || this[K.sort] == SortBy.SIZE.name),
            viewerMode = this[K.viewerMode]?.let { runCatching { Appearance.Mode.valueOf(it) }.getOrNull() } ?: d.viewerMode,
            showHidden = this[K.hidden] ?: d.showHidden,
            grid = this[K.grid] ?: d.grid,
            recents = this[K.recents]?.let { runCatching { json.decodeFromString(recentList, it) }.getOrNull() } ?: d.recents,
            favourites = this[K.favourites]?.let { runCatching { json.decodeFromString(strings, it) }.getOrNull() } ?: d.favourites,
        )
    }

    companion object {
        const val MAX_RECENTS = 60
    }
}

package com.prempoddar.oaf.ui.browser

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.data.OafSettings
import com.prempoddar.oaf.data.SortBy
import com.prempoddar.oaf.ui.design.ExpressiveScaffold
import com.prempoddar.oaf.ui.design.SettingsGroup
import com.prempoddar.oaf.ui.design.SettingsRow
import com.prempoddar.oaf.ui.design.SwitchRow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onAppearance: () -> Unit, onAbout: () -> Unit, onBin: () -> Unit, onStorage: () -> Unit) {
    val scope = rememberCoroutineScope()
    val s by AppGraph.prefs.settings.collectAsStateWithLifecycle(OafSettings())
    fun update(f: (OafSettings) -> OafSettings) = scope.launch { AppGraph.prefs.update(f) }

    ExpressiveScaffold("Settings", onBack = onBack) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item {
                SettingsGroup("Look") {
                    SettingsRow("Appearance", "Theme, colours and black backgrounds", Icons.Rounded.Palette, onClick = onAppearance) { Chevron() }
                    SwitchRow("Grid view", "Big previews instead of a list", Icons.Rounded.GridView, s.grid) { v -> update { it.copy(grid = v) } }
                }
            }
            item {
                SettingsGroup("Files") {
                    SwitchRow("Show hidden files", "Names that start with a dot", Icons.Rounded.VisibilityOff, s.showHidden) { v -> update { it.copy(showHidden = v) } }
                    SwitchRow("Folders first", null, Icons.Rounded.CreateNewFolder, s.foldersFirst) { v -> update { it.copy(foldersFirst = v) } }
                    SettingsRow(
                        "Sort by", when (s.sort) { SortBy.NAME -> "Name"; SortBy.DATE -> "Newest first"; SortBy.SIZE -> "Largest first"; SortBy.TYPE -> "Type" },
                        Icons.AutoMirrored.Rounded.Sort,
                        onClick = { update { it.copy(sort = SortBy.entries[(s.sort.ordinal + 1) % SortBy.entries.size]) } },
                    )
                    SwitchRow("Ask before deleting", "Deleted files go to the bin either way", Icons.Rounded.DeleteOutline, s.confirmDelete) { v -> update { it.copy(confirmDelete = v) } }
                    SettingsRow("Clear recent files", "${s.recents.size} remembered", Icons.Rounded.History, enabled = s.recents.isNotEmpty(), onClick = { update { it.copy(recents = emptyList()) } })
                }
            }
            item {
                SettingsGroup("Tools") {
                    SettingsRow("Bin", "Restore or empty", Icons.Rounded.DeleteOutline, onClick = onBin) { Chevron() }
                    SettingsRow("Storage", "What's using space", Icons.Rounded.PieChart, onClick = onStorage) { Chevron() }
                }
            }
            item {
                SettingsGroup("About") {
                    SettingsRow("About OAF", "Version, licences", Icons.Rounded.Info, onClick = onAbout) { Chevron() }
                }
            }
        }
    }
}

@Composable
private fun Chevron() = Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

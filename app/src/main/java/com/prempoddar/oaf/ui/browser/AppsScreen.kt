package com.prempoddar.oaf.ui.browser

import java.io.File
import com.prempoddar.oaf.ui.design.Haptics
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import com.prempoddar.oaf.ui.design.tap
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.files.Apks
import com.prempoddar.oaf.files.InstalledApp
import com.prempoddar.oaf.ui.design.ChoiceChips
import com.prempoddar.oaf.ui.design.EmptyState
import com.prempoddar.oaf.ui.design.PillTextField
import com.prempoddar.oaf.ui.design.SettingsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class AppSort { NAME, SIZE, UPDATED }

/** Installed apps, ready to share as APK files, back up, open, inspect or remove. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var system by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(AppSort.NAME) }
    var reload by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf<String>() }
    var open by remember { mutableStateOf<InstalledApp?>(null) }
    // An uninstall happens in the system's dialog; reload when coming back.
    LifecycleResumeEffect(Unit) { reload++; onPauseOrDispose { } }
    BackHandler(selected.isNotEmpty()) { selected.clear() }

    // Shows the last list at once, then refreshes it quietly.
    val all by produceState(Apks.cached, reload) { value = withContext(Dispatchers.IO) { Apks.list(context) } }
    val list = all?.filter { it.system == system && (query.isBlank() || it.label.contains(query.trim(), true) || it.pkg.contains(query.trim(), true)) }?.let { l ->
        when (sort) {
            AppSort.NAME -> l
            AppSort.SIZE -> l.sortedByDescending { it.size }
            AppSort.UPDATED -> l.sortedByDescending { it.updated }
        }
    }
    val byPkg = all?.associateBy { it.pkg }.orEmpty()

    var sharing by remember { mutableStateOf<Pair<InstalledApp, Boolean>?>(null) }

    fun shareParts(app: InstalledApp, parts: List<File>) {
        AppGraph.clipboard.run("Preparing to share", toast(context)) {
            val files = Apks.exportParts(app, parts, Apks.shareDir(context))
            withContext(Dispatchers.Main) { shareAll(context, files) }
        }
    }

    fun export(apps: List<InstalledApp>, share: Boolean, form: Apks.Form = Apks.Form.UNIVERSAL) {
        val merging = form == Apks.Form.UNIVERSAL && apps.any { it.split }
        AppGraph.clipboard.run(if (merging) "Making one APK" else if (share) "Preparing to share" else "Saving to Downloads", toast(context)) {
            val dir = if (share) Apks.shareDir(context) else Apks.saveDir()
            val files = apps.map { Apks.export(it, dir, form, work = Apks.shareDir(context)) }
            withContext(Dispatchers.Main) {
                if (share) shareAll(context, files)
                else android.widget.Toast.makeText(context, "Saved to Downloads/OAF Apps", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected.isNotEmpty()) {
                    IconButton(onClick = tap { selected.clear() }) { Icon(Icons.Rounded.Close, "Clear selection") }
                    Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = tap { export(selected.mapNotNull { byPkg[it] }, share = true); selected.clear() }) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton(onClick = tap { export(selected.mapNotNull { byPkg[it] }, share = false); selected.clear() }) { Icon(Icons.Rounded.Download, "Save to Downloads") }
                } else {
                    IconButton(onClick = tap(onBack)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Apps", style = MaterialTheme.typography.titleLarge)
                        list?.let { Text("${it.size} app${if (it.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            PillTextField(query, { query = it }, "Search apps", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), leading = Icons.Rounded.Search)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(!system, { system = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Installed") }
                SegmentedButton(system, { system = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("System") }
            }
            ChoiceChips(listOf(AppSort.NAME to "Name", AppSort.SIZE to "Largest", AppSort.UPDATED to "Recently updated"), sort, { sort = it }, Modifier.padding(horizontal = 16.dp))
            when {
                list == null -> Box(Modifier.weight(1f))
                list.isEmpty() -> EmptyState(Icons.Rounded.Android, "No apps match", null, Modifier.weight(1f).fillMaxWidth())
                else -> LazyVerticalGrid(GridCells.Adaptive(360.dp), Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)) {
                    items(list, key = { it.pkg }) { app ->
                        val isSel = app.pkg in selected
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(MaterialTheme.shapes.large)
                                .background(if (isSel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .combinedClickable(
                                    onClick = { if (selected.isNotEmpty()) { if (isSel) selected.remove(app.pkg) else selected.add(app.pkg) } else open = app },
                                    onLongClick = { if (isSel) selected.remove(app.pkg) else selected.add(app.pkg) },
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                AppIcon(app.pkg, 44)
                                if (isSel) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd).size(18.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(app.version.ifEmpty { null }, humanSize(app.size), if (app.split) "split" else null).filterNotNull().joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                )
                            }
                            if (selected.isEmpty()) IconButton(onClick = tap { export(listOf(app), share = true) }) {
                                Icon(Icons.Rounded.Share, "Share ${app.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { ClipAndJobBar(pasteInto = null) }
    }

    sharing?.let { (app, share) ->
        SplitShareSheet(app, share, onDismiss = { sharing = null },
            onForm = { form -> sharing = null; export(listOf(app), share, form) },
            onParts = { parts -> sharing = null; shareParts(app, parts) })
    }

    open?.let { app ->
        ModalBottomSheet(onDismissRequest = { open = null }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.pkg, 56)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(app.label, style = MaterialTheme.typography.titleLarge)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Version ${app.version} · ${humanSize(app.size)} · updated ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(app.updated))}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (app.split) {
                        SettingsRow("Share", "As one APK anyone can install, or as a bundle or parts", Icons.Rounded.Share, onClick = { open = null; sharing = app to true })
                        SettingsRow("Save to Downloads", "Downloads/OAF Apps, in the form you choose", Icons.Rounded.Download, onClick = { open = null; sharing = app to false })
                    } else {
                        SettingsRow("Share", "As an .apk file", Icons.Rounded.Share, onClick = { open = null; export(listOf(app), true) })
                        SettingsRow("Save to Downloads", "Downloads/OAF Apps", Icons.Rounded.Download, onClick = { open = null; export(listOf(app), false) })
                    }
                    if (Apks.canLaunch(context, app.pkg)) SettingsRow("Open", null, Icons.AutoMirrored.Rounded.OpenInNew, onClick = {
                        open = null
                        context.packageManager.getLaunchIntentForPackage(app.pkg)?.let { context.startActivity(it) }
                    })
                    SettingsRow("App info", "Permissions, storage, notifications", Icons.Rounded.Info, onClick = {
                        open = null
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)))
                    })
                    if (!app.system) SettingsRow("Uninstall", null, Icons.Rounded.Delete, onClick = {
                        open = null
                        @Suppress("DEPRECATION")
                        context.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", app.pkg, null)))
                    })
                }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String, sizeDp: Int) {
    val context = LocalContext.current
    val px = sizeDp * 3
    val icon by produceState(Apks.cachedIcon(pkg, px)?.asImageBitmap(), pkg) {
        if (value == null) value = withContext(Dispatchers.IO) { Apks.icon(context, pkg, px)?.asImageBitmap() }
    }
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        icon?.let { Image(it, null, Modifier.size(sizeDp.dp)) }
            ?: Icon(Icons.Rounded.Android, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/**
 * How to send an app that ships in parts: one APK made from them (the default, as anyone can
 * install it), the parts bundled in one .apks file, or chosen parts as they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitShareSheet(app: InstalledApp, share: Boolean, onDismiss: () -> Unit, onForm: (Apks.Form) -> Unit, onParts: (List<File>) -> Unit) {
    val chosen = remember { mutableStateListOf<File>().apply { addAll(app.apks) } }
    var parts by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.pkg, 48)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(if (share) "Share ${app.label}" else "Save ${app.label}", style = MaterialTheme.typography.titleLarge)
                    Text("This app comes in ${app.apks.size} parts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            FormCard(Icons.Rounded.Android, "One APK", "Recommended",
                "The parts joined into a single app that Android's own installer opens. It's signed by OAF, so it can't update a copy installed from a store; remove that first.",
                highlight = true) { onForm(Apks.Form.UNIVERSAL) }
            FormCard(Icons.Rounded.FolderZip, "Bundle (.apks)", null,
                "All parts in one file, exactly as published. Installs with OAF, SAI or another split installer and keeps the developer's signature.") { onForm(Apks.Form.BUNDLE) }
            if (share) {
                FormCard(Icons.Rounded.ViewModule, "The parts", null, "Each part as its own .apk, choosing which ones to send.", selected = parts) { parts = !parts }
                AnimatedVisibility(parts) {
                    Column(Modifier.clip(MaterialTheme.shapes.extraLarge), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        app.apks.forEach { apk ->
                            val on = apk in chosen
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { Haptics.toggle(!on); if (on) chosen.remove(apk) else chosen.add(apk) }.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(on, { Haptics.toggle(it); if (it) chosen.add(apk) else chosen.remove(apk) })
                                Column(Modifier.weight(1f)) {
                                    Text(Apks.partLabel(app, apk), style = MaterialTheme.typography.titleSmall)
                                    Text("${apk.name} · ${humanSize(apk.length())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Button(
                            enabled = chosen.isNotEmpty(), onClick = tap { onParts(app.apks.filter { it in chosen }) },
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                        ) { Text("Share ${chosen.size} part${if (chosen.size == 1) "" else "s"}") }
                    }
                }
            }
        }
    }
}

/** A choice of form: icon, name, an optional tag, and what it means in practice. */
@Composable
private fun FormCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tag: String?, body: String, highlight: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Surface(
        onClick = tap(onClick), shape = MaterialTheme.shapes.extraLarge,
        color = if (highlight || selected) s.primaryContainer else s.surfaceContainerHigh,
        contentColor = if (highlight || selected) s.onPrimaryContainer else s.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            com.prempoddar.oaf.ui.design.IconTile(icon, size = 44.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (tag != null) Surface(color = s.primary, contentColor = s.onPrimary, shape = CircleShape, modifier = Modifier.padding(start = 8.dp)) {
                        Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

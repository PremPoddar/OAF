package com.prempoddar.oaf.ui.browser

import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.DocumentScanner
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.data.OafSettings
import com.prempoddar.oaf.format.FileKind
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.design.BigButton
import com.prempoddar.oaf.ui.design.OutlinedBigButton
import com.prempoddar.oaf.ui.design.SettingsGroup
import com.prempoddar.oaf.ui.design.SettingsRow
import com.prempoddar.oaf.ui.familyColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where the home screen can send the user. */
sealed interface HomeTarget {
    data class Folder(val dir: File, val search: Boolean = false) : HomeTarget
    data class Of(val category: Category) : HomeTarget
    data object Apps : HomeTarget
    data object Bin : HomeTarget
    data object Storage : HomeTarget
    data object Settings : HomeTarget
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesHome(onOpen: (DocSource) -> Unit, onGo: (HomeTarget) -> Unit) {
    val scanContext = androidx.compose.ui.platform.LocalContext.current
    val scan = com.prempoddar.oaf.scan.Scanner.rememberScan { uri ->
        if (uri == null) android.widget.Toast.makeText(scanContext, "The scanner isn't available on this phone", android.widget.Toast.LENGTH_LONG).show()
        else onOpen(DocSource.fromUri(scanContext, uri, "application/pdf"))
    }
    val pdfScope = androidx.compose.runtime.rememberCoroutineScope()
    val picturesToPdf = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pdfScope.launch {
            val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val tmp = java.io.File(scanContext.cacheDir, "pictures.pdf")
                    com.prempoddar.oaf.pdf.ImagesToPdf.make(scanContext, uris, tmp)
                    com.prempoddar.oaf.scan.Scanner.keep(scanContext, android.net.Uri.fromFile(tmp), "Pictures").also { tmp.delete() }
                }.getOrNull()
            }
            if (saved == null) android.widget.Toast.makeText(scanContext, "Couldn't make the PDF", android.widget.Toast.LENGTH_LONG).show()
            else onOpen(DocSource.fromUri(scanContext, saved, "application/pdf"))
        }
    }
    val context = LocalContext.current
    val settings by AppGraph.prefs.settings.collectAsStateWithLifecycle(OafSettings())
    var access by remember { mutableStateOf(Storage.hasAllFilesAccess()) }
    LifecycleResumeEffect(Unit) { access = Storage.hasAllFilesAccess(); onPauseOrDispose { } }
    val legacyAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { access = Storage.hasAllFilesAccess() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        onOpen(DocSource.fromUri(context, uri))
    }
    val roots = remember(access) { Storage.roots(context) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    fun openRecent(uri: String) {
        val u = Uri.parse(uri)
        onOpen(if (u.scheme == "file") DocSource.fromFile(File(u.path!!)) else DocSource.fromUri(context, u))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Files", style = MaterialTheme.typography.displaySmall)
                        Text("Open all files", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { onGo(HomeTarget.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
                }
            }
            item {
                // Looks like a search box; opens a search across the whole phone.
                Surface(
                    onClick = { if (access) onGo(HomeTarget.Folder(roots.first().dir, search = true)) else pick.launch(arrayOf("*/*")) },
                    shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(56.dp),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Text(if (access) "Search files on this phone" else "Open a file", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (!access) {
                item {
                    NoAccess(
                        onAllow = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null)))
                                }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                            } else {
                                legacyAsk.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            }
                        },
                        onPick = { pick.launch(arrayOf("*/*")) },
                    )
                }
            } else {
                items(roots, key = { "root" + it.dir.path }) { r -> StorageCard(r, primary = r == roots.first()) { onGo(HomeTarget.Folder(r.dir)) } }
                item { Label("Categories") }
                item { Categories(onGo) }
            }

            if (settings.recents.isNotEmpty()) {
                item { Label("Recent") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(settings.recents.take(20), key = { it.uri }) { r ->
                            val kind = runCatching { FileKind.valueOf(r.kind) }.getOrDefault(FileKind.UNKNOWN)
                            val file = Uri.parse(r.uri).takeIf { it.scheme == "file" }?.path?.let(::File)
                            Column(
                                Modifier.width(136.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .combinedClickable(onClick = { openRecent(r.uri) }).padding(12.dp),
                            ) {
                                KindMark(kind, file, 48.dp)
                                Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).height(40.dp))
                            }
                        }
                    }
                }
            }

            if (access && settings.favourites.isNotEmpty()) {
                item {
                    SettingsGroup("Favourites") {
                        settings.favourites.forEach { path ->
                            SettingsRow(File(path).name.ifEmpty { path }, File(path).parent, Icons.Rounded.Star, onClick = { onGo(HomeTarget.Folder(File(path))) }) { Chevron() }
                        }
                    }
                }
            }
            if (access) item {
                SettingsGroup("Collections") {
                    Storage.quickFolders().forEach { q ->
                        SettingsRow(q.label, null, if (q.label == "Downloads") Icons.Rounded.Download else Icons.Rounded.Folder, onClick = { onGo(HomeTarget.Folder(q.dir)) }) { Chevron() }
                    }
                }
            }
            item {
                SettingsGroup("Tools") {
                    SettingsRow("Scan to PDF", "Pages from the camera, straightened and cleaned", Icons.Rounded.DocumentScanner, onClick = scan) { Chevron() }
                    SettingsRow("Pictures to PDF", "Choose photos; each becomes a page", Icons.Rounded.PictureAsPdf, onClick = { picturesToPdf.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Chevron() }
                    SettingsRow("Apps", "Share installed apps as APK files", Icons.Rounded.Apps, onClick = { onGo(HomeTarget.Apps) }) { Chevron() }
                    if (access) SettingsRow("Storage", "What's using space", Icons.Rounded.PieChart, onClick = { onGo(HomeTarget.Storage) }) { Chevron() }
                    SettingsRow("Bin", "Deleted files stay for 30 days", Icons.Rounded.DeleteOutline, onClick = { onGo(HomeTarget.Bin) }) { Chevron() }
                    SettingsRow("Open a file", "Pick from any app or cloud drive", Icons.AutoMirrored.Outlined.InsertDriveFile, onClick = { pick.launch(arrayOf("*/*")) }) { Chevron() }
                }
            }
        }
    }
}

@Composable
private fun Chevron() = Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Label(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
)

@Composable
private fun StorageCard(root: Root, primary: Boolean, onClick: () -> Unit) {
    val total = root.dir.totalSpace
    val free = root.dir.usableSpace
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.extraLarge,
        color = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (primary) Icons.Rounded.Smartphone else Icons.Rounded.SdStorage, null)
                Spacer(Modifier.width(12.dp))
                Text(root.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null)
            }
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(10.dp).clip(CircleShape),
                    color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    trackColor = (if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.12f),
                    drawStopIndicator = {},
                )
                Text("${humanSize(total - free)} used · ${humanSize(free)} free", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun Categories(onGo: (HomeTarget) -> Unit) {
    val paper = LocalPaper.current
    val items: List<Triple<String, ImageVector, Pair<Color, HomeTarget>>> = listOf(
        Triple("Images", categoryIcon(Category.IMAGES), familyColor(FileKind.IMAGE, paper.dark) to HomeTarget.Of(Category.IMAGES)),
        Triple("Videos", categoryIcon(Category.VIDEOS), familyColor(FileKind.VIDEO, paper.dark) to HomeTarget.Of(Category.VIDEOS)),
        Triple("Audio", categoryIcon(Category.AUDIO), (if (paper.dark) Color(0xFF8CCBC3) else Color(0xFF1F7A70)) to HomeTarget.Of(Category.AUDIO)),
        Triple("Documents", categoryIcon(Category.DOCUMENTS), familyColor(FileKind.PDF, paper.dark) to HomeTarget.Of(Category.DOCUMENTS)),
        Triple("Archives", categoryIcon(Category.ARCHIVES), familyColor(FileKind.ZIP, paper.dark) to HomeTarget.Of(Category.ARCHIVES)),
        Triple("APKs", categoryIcon(Category.APKS), familyColor(FileKind.XLSX, paper.dark) to HomeTarget.Of(Category.APKS)),
        Triple("Apps", Icons.Rounded.Apps, familyColor(FileKind.PPTX, paper.dark) to HomeTarget.Apps),
        Triple("Downloads", Icons.Rounded.FolderOpen, MaterialTheme.colorScheme.primary to (Storage.quickFolders().firstOrNull { it.label == "Downloads" }?.let { HomeTarget.Folder(it.dir) } ?: HomeTarget.Apps)),
    )
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, icon, rest) ->
                    val (color, target) = rest
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.large).clickable { onGo(target) }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.large).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                        }
                        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoAccess(onAllow: () -> Unit, onPick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text("See every file on your phone", style = MaterialTheme.typography.headlineSmall)
            Text(
                "To browse folders, OAF needs Android's “All files access”. It only reads what you open and never sends anything anywhere. You can also just pick a file.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BigButton("Allow access to files", onAllow, Modifier.fillMaxWidth())
            OutlinedBigButton("Pick a file instead", onPick, Modifier.fillMaxWidth())
        }
    }
}

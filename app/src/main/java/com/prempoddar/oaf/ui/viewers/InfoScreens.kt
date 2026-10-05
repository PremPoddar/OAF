package com.prempoddar.oaf.ui.viewers

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.CodeStyle
import com.prempoddar.oaf.ui.LocalPaper
import com.prempoddar.oaf.ui.ViewerScaffold
import com.prempoddar.oaf.ui.browser.humanSize
import com.prempoddar.oaf.ui.rememberChrome
import com.tom_roush.fontbox.ttf.OTFParser
import com.tom_roush.fontbox.ttf.TTFParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    val paper = LocalPaper.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = paper.muted, modifier = Modifier.width(120.dp))
        Text(value, style = if (mono) CodeStyle else MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private class ApkInfo(
    val label: String, val icon: Drawable?, val pkg: String, val version: String, val code: Long, val minSdk: Int?, val targetSdk: Int?,
    val permissions: List<String>, val signer: String?, val installed: String?, val size: Long,
)

private fun sdkName(level: Int) = "API $level" + when (level) {
    29 -> " (Android 10)"; 30 -> " (Android 11)"; 31 -> " (Android 12)"; 32 -> " (Android 12L)"; 33 -> " (Android 13)"
    34 -> " (Android 14)"; 35 -> " (Android 15)"; 36 -> " (Android 16)"; 28 -> " (Android 9)"; 26, 27 -> " (Android 8)"; 24, 25 -> " (Android 7)"; 23 -> " (Android 6)"; 21, 22 -> " (Android 5)"
    else -> ""
}

/** What an app package is, who signed it and what it asks for. Installing is left to the system installer. */
@Composable
fun ApkScreen(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    var browsing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    if (browsing) { ArchiveScreen(source, onBack = { browsing = false }, onOpen = {}); return }
    val info by produceState<Result<ApkInfo>?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = source.local(context)
                val pm = context.packageManager
                val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
                val p: PackageInfo = pm.getPackageArchiveInfo(file.path, flags) ?: error("This isn't a readable app package")
                val app = p.applicationInfo!!.apply { sourceDir = file.path; publicSourceDir = file.path }
                val cert = p.signingInfo?.apkContentsSigners?.firstOrNull()
                val installed = runCatching { pm.getPackageInfo(p.packageName, 0).versionName }.getOrNull()
                ApkInfo(
                    app.loadLabel(pm).toString(), runCatching { app.loadIcon(pm) }.getOrNull(), p.packageName, p.versionName ?: "—",
                    p.longVersionCode, app.minSdkVersion, app.targetSdkVersion,
                    p.requestedPermissions?.toList().orEmpty().sorted(),
                    cert?.toByteArray()?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString(":") { b -> "%02X".format(b) } },
                    installed, file.length(),
                )
            }
        }
    }
    ViewerScaffold(
        title = source.name, subtitle = "Android app", chrome = rememberChrome(), autoHide = false, onBack = onBack,
        actions = {
            IconButton(onClick = { browsing = true }) { Icon(Icons.Rounded.FolderZip, "Browse files inside") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Install with…") }, onClick = { menu = false; openElsewhere(context, source) })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareSource(context, source) })
                }
            }
        },
    ) {
        val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 72.dp
        when (val r = info) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> r.fold(
                onFailure = { Text(it.message ?: "Couldn't read this app", color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = top)) },
                onSuccess = { a ->
                    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = 32.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                a.icon?.let { Image(it.toBitmap(144, 144).asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))) }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(a.label, style = MaterialTheme.typography.headlineSmall)
                                    Text(a.pkg, style = CodeStyle, color = paper.muted)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            InfoRow("Version", "${a.version} (${a.code})")
                            a.installed?.let { InfoRow("Installed", "version $it") }
                            a.minSdk?.let { InfoRow("Needs", sdkName(it) + " or newer") }
                            a.targetSdk?.let { InfoRow("Built for", sdkName(it)) }
                            InfoRow("Size", humanSize(a.size))
                            a.signer?.let { InfoRow("Signed by", "SHA-256 $it", mono = true) }
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            Text("Permissions (${a.permissions.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        items(a.permissions) { perm ->
                            Text(perm.removePrefix("android.permission."), style = CodeStyle, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                },
            )
        }
    }
}

private class FontInfo(val typeface: Typeface?, val fields: List<Pair<String, String>>)

/** A font's names and a preview at several sizes, with text you can change. */
@Composable
fun FontScreen(source: DocSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    var sample by remember { mutableStateOf("The quick brown fox jumps over the lazy dog") }
    val info by produceState<Result<FontInfo>?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = source.local(context)
                val typeface = runCatching { Typeface.createFromFile(file) }.getOrNull()
                val fields = runCatching {
                    val font = if (file.inputStream().use { s -> ByteArray(4).also { s.read(it) } }.let { String(it, Charsets.US_ASCII) } == "OTTO") OTFParser().parse(file) else TTFParser().parse(file)
                    font.use { f ->
                        val n = f.naming
                        // English names: the Windows table first, then the old Mac one.
                        fun name(id: Int): String? = n?.let { t -> t.getName(id, 3, 1, 0x409) ?: t.getName(id, 1, 0, 0) }?.trim()?.ifEmpty { null }
                        listOfNotNull(
                            n?.fontFamily?.let { "Family" to it }, n?.fontSubFamily?.let { "Style" to it }, n?.postScriptName?.let { "PostScript" to it },
                            name(5)?.let { "Version" to it }, name(9)?.let { "Designer" to it }, name(8)?.let { "Maker" to it },
                            name(0)?.let { "Copyright" to it }, name(13)?.let { "Licence" to it },
                            "Glyphs" to f.numberOfGlyphs.toString(),
                        )
                    }
                }.getOrDefault(emptyList())
                if (typeface == null && fields.isEmpty()) error("This font format isn't supported")
                FontInfo(typeface, fields + ("Size" to humanSize(file.length())))
            }
        }
    }
    ViewerScaffold(title = source.name, subtitle = "Font", chrome = rememberChrome(), autoHide = false, onBack = onBack, actions = {
        IconButton(onClick = { shareSource(context, source) }) { Icon(Icons.Rounded.Share, "Share") }
    }) {
        val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 72.dp
        when (val r = info) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> r.fold(
                onFailure = { Text(it.message ?: "Couldn't read this font", color = paper.muted, modifier = Modifier.padding(24.dp).padding(top = top)) },
                onSuccess = { f ->
                    val family = f.typeface?.let { FontFamily(it) }
                    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            OutlinedTextField(sample, { sample = it }, label = { Text("Sample text") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                        }
                        if (family != null) items(listOf(48, 32, 24, 18, 14, 11)) { size ->
                            Column {
                                Text("$size", style = MaterialTheme.typography.labelSmall, color = paper.faint)
                                Text(sample, style = TextStyle(fontFamily = family, fontSize = size.sp, color = paper.ink))
                            }
                        }
                        if (family != null) item {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Text(
                                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !?&@#%(){}[]\nÀÉÎÕÜßæøñç€£¥",
                                    style = TextStyle(fontFamily = family, fontSize = 22.sp, lineHeight = 32.sp, color = paper.ink), modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        items(f.fields) { (k, v) -> InfoRow(k, v) }
                    }
                },
            )
        }
    }
}

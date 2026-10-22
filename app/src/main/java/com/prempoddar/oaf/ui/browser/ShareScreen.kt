package com.prempoddar.oaf.ui.browser

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.files.FtpServer
import com.prempoddar.oaf.files.FtpSettings
import com.prempoddar.oaf.files.FtpShare
import com.prempoddar.oaf.ui.design.BigButton
import com.prempoddar.oaf.ui.design.CardGroup
import com.prempoddar.oaf.ui.design.CardSection
import com.prempoddar.oaf.ui.design.ExpressiveScaffold
import com.prempoddar.oaf.ui.design.IconTile
import com.prempoddar.oaf.ui.design.OptionGrid
import com.prempoddar.oaf.ui.design.OutlinedBigButton
import com.prempoddar.oaf.ui.design.PillTextField
import com.prempoddar.oaf.ui.design.SegmentedBar
import com.prempoddar.oaf.ui.design.SheetLabel
import com.prempoddar.oaf.ui.design.ToggleCard
import com.prempoddar.oaf.ui.design.tap
import java.io.File

/**
 * Share this phone's files with computers and other devices on the same Wi-Fi (or this phone's
 * hotspot) over FTP: what's shared, who can sign in, and whether they can change things.
 */
@Composable
fun ShareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by FtpShare.state.collectAsStateWithLifecycle()
    val stored by FtpShare.settings(context).collectAsStateWithLifecycle()
    val s = stored ?: return
    val busy = state.running || state.starting
    fun change(f: (FtpSettings) -> FtpSettings) = FtpShare.save(context, f(s))
    val access = Storage.hasAllFilesAccess()
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { FtpShare.start(context) }
    fun start() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else FtpShare.start(context)
    }
    val password = remember(s.secret) { FtpShare.password(s) }
    var showPassword by remember { mutableStateOf(false) }
    var port by remember(s.port) { mutableStateOf(s.port.toString()) }

    ExpressiveScaffold("Share over Wi-Fi", onBack = onBack) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { StatusCard(state, s, onStart = ::start, onStop = { FtpShare.stop(context) }, enabled = access) }
            if (!access) item {
                Text(
                    "Allow OAF to see all files first (on the home screen), so there's something to share.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp),
                )
            }
            item {
                if (busy) Text(
                    "Stop sharing to change these.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
                SheetLabel("What to share")
                val roots = remember { Storage.roots(context) }
                val places = remember(roots) { listOf("" to "Whole phone") + Storage.quickFolders().map { it.dir.path to it.label } + roots.drop(1).map { it.dir.path to it.label } }
                OptionGrid(places.map { it.first }, s.folder, enabled = { !busy }, onPick = { f -> change { it.copy(folder = f) } }) { f ->
                    val label = places.first { it.first == f }.second
                    Triple(
                        when { f.isEmpty() -> Icons.Rounded.Smartphone; roots.drop(1).any { it.dir.path == f } -> Icons.Rounded.SdStorage; else -> Icons.Rounded.Folder },
                        label, if (f.isEmpty()) "Everything in storage" else File(f).name.takeUnless { it.equals(label, ignoreCase = true) } ?: "Just this folder",
                    )
                }
            }
            item {
                SheetLabel("What others can do")
                SegmentedBar(listOf(false to "Open and change", true to "Only open"), s.readOnly, enabled = !busy) { ro -> change { it.copy(readOnly = ro) } }
            }
            item {
                SheetLabel("Who can sign in")
                SegmentedBar(listOf(false to "With a password", true to "Anyone here"), s.anonymous, enabled = !busy) { a -> change { it.copy(anonymous = a) } }
                AnimatedVisibility(!s.anonymous) {
                    Column(Modifier.padding(top = 10.dp)) {
                        CardGroup {
                            CardSection {
                                PillTextField(
                                    s.user, { u -> change { it.copy(user = u.filter { c -> !c.isWhitespace() }.take(32)) } }, "User name",
                                    enabled = !busy, clearable = false, isError = s.user.isBlank(),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Password", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (showPassword) password else "•".repeat(password.length), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                                    }
                                    IconButton(onClick = tap { showPassword = !showPassword }) {
                                        Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showPassword) "Hide password" else "Show password")
                                    }
                                    IconButton(enabled = !busy, onClick = tap { change { it.copy(secret = com.prempoddar.oaf.files.remote.Secrets.encrypt(FtpShare.newPassword())) }; showPassword = true }) {
                                        Icon(Icons.Rounded.Refresh, "New password")
                                    }
                                }
                            }
                        }
                    }
                }
                if (s.anonymous) Text(
                    "Anyone on this network can open${if (s.readOnly) "" else " and change"} the shared files without a password.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
            item {
                SheetLabel("More")
                CardGroup {
                    ToggleCard(Icons.Rounded.Bedtime, "Keep sharing with the screen off", "Holds Wi-Fi awake so long copies don't stall", s.keepAwake, enabled = !busy) { k -> change { it.copy(keepAwake = k) } }
                    CardSection {
                        val n = port.toIntOrNull()
                        PillTextField(
                            port, { p ->
                                port = p.filter(Char::isDigit).take(5)
                                port.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { v -> change { it.copy(port = v) } }
                            },
                            "Port", enabled = !busy, clearable = false, isError = n == null || n !in 1024..65535,
                            supporting = "${FtpServer.DEFAULT_PORT} unless something else uses it; 1024 to 65535",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
            item { HowToConnect(state.addresses.firstOrNull() ?: "ftp://<this phone's address>:${s.port}") }
            if (state.log.isNotEmpty()) item {
                SheetLabel("Activity")
                CardGroup {
                    CardSection {
                        state.log.take(12).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item {
                Text(
                    "FTP isn't encrypted: anyone watching the network could see the files and the password. Share on networks you trust, such as your home Wi-Fi or this phone's hotspot.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp, 16.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: FtpShare.State, s: FtpSettings, onStart: () -> Unit, onStop: () -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    val on = state.running
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(32.dp), color = if (on) scheme.primaryContainer else scheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.WifiTethering, container = if (on) scheme.primary else scheme.surfaceContainerHighest, tint = if (on) scheme.onPrimary else scheme.onSurfaceVariant, size = 52.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        when { on -> "Sharing is on"; state.starting -> "Starting…"; else -> "Sharing is off" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        when {
                            on && state.clients > 0 -> "${state.clients} connected"
                            on -> if (s.readOnly) "Others can open files" else "Others can open and change files"
                            else -> "Open this phone's files from a computer on the same Wi-Fi"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    )
                }
                if (on) Box(Modifier.size(12.dp).background(scheme.primary, CircleShape))
            }
            if (on) {
                if (state.addresses.isEmpty()) Text("Connect this phone to Wi-Fi, or turn on its hotspot, to get an address.", style = MaterialTheme.typography.bodyMedium)
                state.addresses.forEach { a ->
                    Surface(shape = RoundedCornerShape(18.dp), color = scheme.surface.copy(alpha = 0.7f)) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            IconButton(onClick = tap { copy(context, a) }) { Icon(Icons.Rounded.ContentCopy, "Copy address") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (s.anonymous) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null, Modifier.size(18.dp))
                    Text(
                        if (s.anonymous) "  No password needed" else "  Sign in as ${s.user} with the password below",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (on) OutlinedBigButton("Stop sharing", onStop, icon = Icons.Rounded.Stop)
            else BigButton(if (state.starting) "Starting…" else "Start sharing", onStart, icon = Icons.Rounded.WifiTethering, enabled = enabled && !state.starting && (s.anonymous || s.user.isNotBlank()))
        }
    }
}

@Composable
private fun HowToConnect(address: String) {
    SheetLabel("How to connect")
    CardGroup {
        Step(Icons.Rounded.Computer, "Windows", "Open File Explorer and type $address in the address bar.")
        Step(Icons.Rounded.Computer, "Mac", "In Finder choose Go → Connect to Server and enter $address. Finder can only read over FTP; use an app like Cyberduck to copy files to the phone.")
        Step(Icons.Rounded.Edit, "Anything else", "Any FTP app (FileZilla, a file manager on another phone or a TV) with the address, user name and password.")
    }
}

@Composable
private fun Step(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun copy(context: android.content.Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Address", text))
}

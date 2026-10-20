package com.prempoddar.oaf.ui.browser

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prempoddar.oaf.AppGraph
import com.prempoddar.oaf.files.remote.RemoteEntry
import com.prempoddar.oaf.files.remote.RemoteFs
import com.prempoddar.oaf.files.remote.RemotePath
import com.prempoddar.oaf.files.remote.Remotes
import com.prempoddar.oaf.files.remote.Server
import com.prempoddar.oaf.format.Detector
import com.prempoddar.oaf.io.DocSource
import com.prempoddar.oaf.ui.design.ChoiceChips
import com.prempoddar.oaf.ui.design.EmptyState
import com.prempoddar.oaf.ui.design.ExpressiveScaffold
import com.prempoddar.oaf.ui.design.Haptics
import com.prempoddar.oaf.ui.design.PillTextField
import com.prempoddar.oaf.ui.design.SectionLabel
import com.prempoddar.oaf.ui.design.SettingsGroup
import com.prempoddar.oaf.ui.design.SettingsRow
import com.prempoddar.oaf.ui.design.tap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Saved servers, and adding or changing one. */
@Composable
fun NetworkScreen(onBack: () -> Unit, onOpenServer: (Server) -> Unit) {
    val servers by AppGraph.servers.list.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Server?>(null) }
    var adding by remember { mutableStateOf(false) }
    ExpressiveScaffold(
        "Network", onBack = onBack,
        floatingActionButton = { FloatingActionButton(onClick = tap { adding = true }) { Icon(Icons.Rounded.Add, "Add a server") } },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp)) {
            if (servers.isEmpty()) item {
                EmptyState(
                    Icons.Rounded.Lan, "No servers yet",
                    "Add a NAS, a computer sharing a folder, or a web drive. OAF can open, download, upload and tidy files on it, over Windows sharing (SMB), SFTP, FTP or WebDAV.",
                    Modifier.padding(top = 32.dp),
                ) { TextButton(onClick = tap { adding = true }) { Text("Add a server") } }
            } else item {
                SettingsGroup("Servers") {
                    servers.sortedBy { it.name.lowercase() }.forEach { s ->
                        SettingsRow(s.name, s.address, if (s.type == Server.Type.SMB) Icons.Rounded.Storage else Icons.Rounded.Dns, onClick = { onOpenServer(s) }) {
                            IconButton(onClick = tap { editing = s }) { Icon(Icons.Rounded.MoreVert, "Change ${s.name}") }
                        }
                    }
                }
                Text(
                    "Passwords are kept encrypted with a key that stays on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
    if (adding || editing != null) ServerDialog(editing, onDismiss = { adding = false; editing = null })
}

@Composable
private fun ServerDialog(existing: Server?, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: Server.Type.SMB) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember { mutableStateOf(existing?.port?.toString().orEmpty()) }
    var path by remember { mutableStateOf(existing?.path?.takeIf { it != "/" }.orEmpty()) }
    var user by remember { mutableStateOf(existing?.user.orEmpty()) }
    // Left empty when changing a server, the saved password stays.
    var password by remember { mutableStateOf("") }
    var secure by remember { mutableStateOf(existing?.secure ?: false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val portNumber = port.toIntOrNull()
    val ok = host.isNotBlank() && (port.isEmpty() || portNumber in 1..65535) && (type != Server.Type.SMB || RemotePath.normal(path) != "/")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a server" else "Change ${existing.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChips(Server.Type.entries.map { it to it.label }, type, { type = it; port = "" }, Modifier.padding(horizontal = 0.dp))
                PillTextField(host, { host = it.trim() }, "Address", supporting = "Like 192.168.1.20 or nas.local", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                PillTextField(port, { port = it.filter(Char::isDigit).take(5) }, "Port", supporting = "Leave empty for ${if (type == Server.Type.WEBDAV) (if (secure) 443 else 80) else type.port}", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                PillTextField(
                    path, { path = it }, if (type == Server.Type.SMB) "Shared folder" else "Folder (optional)",
                    supporting = if (type == Server.Type.SMB) "The share's name, like Media or /Media/Films" else "Where to start, like /home/me",
                    isError = type == Server.Type.SMB && path.isNotEmpty() && RemotePath.normal(path) == "/",
                )
                PillTextField(user, { user = it.trim() }, "User name", supporting = if (type == Server.Type.SFTP) null else "Empty for guest or anonymous access")
                PillTextField(
                    password, { password = it }, "Password",
                    supporting = if (existing?.secret?.isNotEmpty() == true) "Leave empty to keep the saved one" else null,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (type == Server.Type.FTP || type == Server.Type.WEBDAV) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(secure, { Haptics.toggle(it); secure = it })
                        Text(if (type == Server.Type.FTP) "Encrypted (FTPS)" else "Encrypted (HTTPS)")
                    }
                }
                PillTextField(name, { name = it }, "Name (optional)")
                if (existing != null) TextButton(onClick = tap { confirmRemove = true }) { Text("Remove this server", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = tap {
                val defaultPort = if (type == Server.Type.WEBDAV) (if (secure) 443 else 80) else type.port
                val s = Server(
                    id = existing?.id ?: UUID.randomUUID().toString(), type = type,
                    name = name.trim().ifEmpty { host.trim() }, host = host.trim(), port = portNumber ?: defaultPort,
                    path = RemotePath.normal(path), user = user, secure = secure, secret = existing?.secret.orEmpty(),
                )
                AppGraph.servers.save(s, if (existing != null && password.isEmpty()) null else password)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmRemove && existing != null) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remove ${existing.name}?") },
        text = { Text("Only OAF's shortcut and saved password go; nothing on the server changes.") },
        confirmButton = { TextButton(onClick = tap { AppGraph.servers.remove(existing.id); confirmRemove = false; onDismiss() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
}

/**
 * A folder on a server. Files open in OAF after a copy comes down to the phone; folders open in
 * place. Long-press (or the menu) to download, rename or delete; the button adds files.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RemoteScreen(server: Server, onBack: () -> Unit, onOpen: (DocSource) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fs by remember { mutableStateOf<RemoteFs?>(null) }
    var path by remember { mutableStateOf(RemotePath.normal(server.path)) }
    var entries by remember { mutableStateOf<List<RemoteEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<RemoteEntry?>(null) }
    var renaming by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleting by remember { mutableStateOf<RemoteEntry?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    val start = RemotePath.normal(server.path)

    suspend fun <T> io(label: String?, block: (RemoteFs) -> T): T? {
        val f = fs ?: return null
        busy = label
        return try {
            withContext(Dispatchers.IO) { block(f) }
        } catch (t: Throwable) {
            Haptics.reject()
            Toast.makeText(context, Remotes.explain(t), Toast.LENGTH_LONG).show(); null
        } finally { busy = null }
    }

    LaunchedEffect(server) {
        error = null
        fs = runCatching { withContext(Dispatchers.IO) { Remotes.connect(server, AppGraph.servers.password(server), AppGraph.servers.knownHosts) } }
            .onFailure { error = Remotes.explain(it) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { fs?.let { f -> Thread { runCatching { f.close() } }.start() } } }
    LaunchedEffect(fs, path, tick) {
        val f = fs ?: return@LaunchedEffect
        entries = null
        runCatching { withContext(Dispatchers.IO) { Remotes.sorted(f.list(path)) } }
            .onSuccess { entries = it; error = null }.onFailure { error = Remotes.explain(it) }
    }
    BackHandler(path != start) { path = RemotePath.parent(path) }

    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            uris.forEachIndexed { i, uri ->
                val src = DocSource.fromUri(context, uri)
                io("Uploading ${src.name}${if (uris.size > 1) " (${i + 1} of ${uris.size})" else ""}") { f ->
                    context.contentResolver.openInputStream(uri)!!.use { f.write(RemotePath.join(path, src.name), it, src.size) }
                }
            }
            Haptics.confirm(); tick++
        }
    }

    /** A copy on the phone: in the cache to open, or in Downloads to keep. */
    fun fetch(e: RemoteEntry, keep: Boolean) = scope.launch {
        val dir = if (keep) File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OAF Network").apply { mkdirs() }
        else File(context.cacheDir, "network/${server.id.take(8)}${RemotePath.parent(e.path).hashCode().toUInt()}").apply { mkdirs() }
        val target = if (keep) com.prempoddar.oaf.files.FileOps.freeName(dir, e.name) else File(dir, e.name)
        val ok = io("${if (keep) "Downloading" else "Opening"} ${e.name}") { f ->
            if (!keep && target.exists() && target.length() == e.size && target.lastModified() >= e.modified) return@io true
            f.read(e.path).use { input -> target.outputStream().use { input.copyTo(it) } }
            if (e.modified > 0) target.setLastModified(e.modified)
            true
        }
        if (ok == true) {
            if (keep) { Haptics.confirm(); Toast.makeText(context, "Saved to Downloads/OAF Network", Toast.LENGTH_SHORT).show() }
            else onOpen(DocSource.fromFile(target))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = tap { if (path != start) path = RemotePath.parent(path) else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(if (path == "/") server.name else RemotePath.name(path), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(server.name + path.takeIf { it != "/" }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    IconButton(onClick = tap { newFolder = true }, enabled = fs != null) { Icon(Icons.Rounded.CreateNewFolder, "New folder") }
                    IconButton(onClick = tap { tick++ }, enabled = fs != null) { Icon(Icons.Rounded.Refresh, "Refresh") }
                },
            )
        },
        floatingActionButton = { if (fs != null) FloatingActionButton(onClick = tap { upload.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.CloudUpload, "Upload files here") } },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                error != null && entries == null -> EmptyState(Icons.Rounded.Lan, "Couldn't open ${server.name}", error, Modifier.padding(top = 48.dp)) {
                    TextButton(onClick = tap {
                        error = null
                        if (fs == null) scope.launch {
                            fs = runCatching { withContext(Dispatchers.IO) { Remotes.connect(server, AppGraph.servers.password(server), AppGraph.servers.knownHosts) } }
                                .onFailure { error = Remotes.explain(it) }.getOrNull()
                        } else tick++
                    }) { Text("Try again") }
                }
                entries == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                entries!!.isEmpty() -> EmptyState(Icons.Rounded.CreateNewFolder, "This folder is empty", "Add files with the upload button.", Modifier.padding(top = 48.dp))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(entries!!, key = { it.path }) { e ->
                        val kind = if (e.dir) null else runCatching { Detector.detect(e.name, null, ByteArray(0)).kind }.getOrNull()
                        Box {
                            Row(
                                Modifier.fillMaxWidth().combinedClickable(
                                    onClick = { Haptics.tap(); if (e.dir) path = e.path else fetch(e, keep = false) },
                                    onLongClick = { menuFor = e },
                                ).padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                KindMark(kind)
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val date = if (e.modified > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.modified)) else null
                                    Text(listOfNotNull(if (e.dir) "Folder" else humanSize(e.size), date).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = tap { menuFor = e }) { Icon(Icons.Rounded.MoreVert, "More for ${e.name}") }
                            }
                            DropdownMenu(menuFor == e, onDismissRequest = { menuFor = null }) {
                                if (!e.dir) DropdownMenuItem(text = { Text("Download to phone") }, onClick = tap { menuFor = null; fetch(e, keep = true) })
                                DropdownMenuItem(text = { Text("Rename") }, onClick = tap { menuFor = null; renaming = e })
                                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = tap { menuFor = null; deleting = e })
                            }
                        }
                    }
                }
            }
            busy?.let { label ->
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }

    renaming?.let { e ->
        var name by remember(e) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { PillTextField(name, { name = it }, "Name") },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && name != e.name && '/' !in name, onClick = tap {
                    renaming = null
                    scope.launch { if (io("Renaming") { it.rename(e.path, RemotePath.join(RemotePath.parent(e.path), name.trim())) } != null) tick++ }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${e.name}”?") },
            text = { Text(if (e.dir) "The folder and everything in it are deleted from the server. There's no bin there." else "It's deleted from the server. There's no bin there.") },
            confirmButton = {
                TextButton(onClick = tap {
                    deleting = null
                    scope.launch { if (io("Deleting ${e.name}") { it.delete(e) } != null) { Haptics.confirm(); tick++ } }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text("New folder") },
            text = { PillTextField(name, { name = it }, "Name") },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && '/' !in name, onClick = tap {
                    newFolder = false
                    scope.launch { if (io("Making the folder") { it.mkdir(RemotePath.join(path, name.trim())) } != null) tick++ }
                }) { Text("Make") }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Cancel") } },
        )
    }
}

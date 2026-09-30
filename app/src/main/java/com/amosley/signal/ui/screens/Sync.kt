package com.amosley.signal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.BuildConfig
import com.amosley.signal.core.Fmt
import com.amosley.signal.core.Library
import com.amosley.signal.data.DlState
import com.amosley.signal.data.FoundPc
import com.amosley.signal.data.LibMode
import com.amosley.signal.data.PairedPc
import com.amosley.signal.data.RemoteMode
import com.amosley.signal.data.Repository
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.Screen
import com.amosley.signal.ui.components.CardBox
import com.amosley.signal.ui.components.Chip
import com.amosley.signal.ui.components.FilledBtn
import com.amosley.signal.ui.components.Hairline
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.components.SettingRow
import com.amosley.signal.ui.components.Spinner
import com.amosley.signal.ui.components.Toggle
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun CardTitle(text: String) {
    Mono(text, color = C.Muted, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun SyncScreen(c: Ctx) {
    val repo = c.repo
    val s = c.settings
    val libs by repo.libraries.collectAsState()
    val conflicts by repo.conflicts.collectAsState()
    val tagJobs by repo.tagJobs.collectAsState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c.st.stack.size > 1) {
                BackButton(c)
                Spacer(Modifier.width(12.dp))
            }
            Text("PC sync", style = T.sectionTitle)
        }
        Spacer(Modifier.height(8.dp))
        StatusLine(c)
        val pc = s.pc
        if (pc == null) {
            Spacer(Modifier.height(20.dp))
            CardBox {
                Column {
                    Text("No PC paired", style = T.rowSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("On your Windows PC, download SignalAgent.exe from the Signal releases page on GitHub and double-click it. Then pair here to stream and download your library.", style = T.ui(13.sp), color = C.Muted)
                    Spacer(Modifier.height(12.dp))
                    FilledBtn("Pair a PC") { c.st.push(Screen.Pair) }
                }
            }
        } else {
            if (!c.status.reachable && !c.status.checking) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().border(1.dp, C.Amber).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Can't reach ${pc.name}", style = T.rowSecondary, color = C.AmberText)
                        Text("Downloads keep playing; streaming items are greyed out.", style = T.ui(12.5.sp), color = C.Muted)
                    }
                    OutlineBtn("Retry", color = C.AmberText, border = C.Amber) { repo.refresh(force = true) }
                }
            }
            Spacer(Modifier.height(16.dp))
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pc.name, style = T.ui(17.sp, 600))
                        Mono(listOfNotNull(pc.lanUrl.removePrefix("http://"), pc.agentVersion?.let { "Signal Agent $it" }).joinToString(" · "), style = T.metaMono, color = C.Faint)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (c.status.reachable) "Synced ${Fmt.ago(System.currentTimeMillis(), c.status.lastSync)}" else "Last synced ${Fmt.ago(System.currentTimeMillis(), c.status.lastSync)}",
                            style = T.ui(13.sp, 500), color = if (c.status.reachable) C.Green else C.AmberText,
                        )
                    }
                    if (c.status.checking) Spinner(Modifier.size(16.dp)) else OutlineBtn("Sync now") {
                        scope.launch {
                            c.status.baseUrl?.let { b -> runCatching { repo.agent.rescan(b, pc.token) } }
                            repo.refreshNow(force = true)
                            c.toast("Synced with ${pc.name}")
                        }
                    }
                }
            }
            val pending = c.dl.values.count { it is DlState.Queued || it is DlState.Running } + tagJobs.count { it.state == "QUEUED" }
            val attention = c.dl.values.count { it is DlState.Failed } + conflicts.size + tagJobs.count { it.state == "FAILED" }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().background(C.Card).clickable { c.st.push(Screen.Activity) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sync activity", style = T.rowSecondary)
                    Text("$pending pending · $attention need attention", style = T.ui(12.5.sp), color = if (attention > 0) C.AmberText else C.Muted)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = C.Faint)
            }

            CardTitle("Libraries · same folders as Plex")
            CardBox {
                Column {
                    if (libs.isEmpty()) Mono("Libraries load when ${pc.name} is reachable", style = T.metaMono, color = C.Faint)
                    libs.forEachIndexed { i, lib ->
                        if (i > 0) Hairline()
                        val mode = s.libModes[lib.id]
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lib.name, style = T.row)
                                Mono(lib.path, style = T.metaMono, color = C.Faint)
                                Mono("${lib.count} files · ${Fmt.bytes(lib.bytes)}", style = T.metaMono, color = C.Faint)
                            }
                            Box(
                                Modifier.border(1.dp, if (mode == null) C.HairStrong else C.Amber).clickable {
                                    // Off → Stream → Download new → Download all → Off
                                    val next = when (mode) {
                                        null -> LibMode.STREAM
                                        LibMode.DOWNLOAD_ALL -> null
                                        else -> mode.next()
                                    }
                                    if (next == null) repo.updateSettings { it.copy(libModes = it.libModes - lib.id) } else repo.setLibMode(lib.id, next)
                                }.padding(horizontal = 9.dp, vertical = 6.dp),
                            ) { Text("${mode?.label ?: "Off"} ▾", style = T.mono(11.sp, 600, 0.04.sp), color = if (mode == null) C.Faint else C.AmberText) }
                        }
                    }
                }
            }
        }

        CardTitle("Downloads")
        CardBox {
            Column {
                val used = c.repo.downloads.usedBytes()
                val free = c.repo.downloads.freeBytes()
                Text("${Fmt.bytes(used)} downloaded · ${Fmt.bytes(free)} free", style = T.ui(13.5.sp, 500))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(C.Hair)) {
                    Box(Modifier.fillMaxWidth((used.toFloat() / (used + free).coerceAtLeast(1)).coerceIn(0.01f, 1f)).height(6.dp).background(C.Amber))
                }
                Spacer(Modifier.height(6.dp))
                SettingRow("Download on Wi-Fi only") { Toggle(s.wifiOnly) { v -> repo.updateSettings { it.copy(wifiOnly = v) } } }
                Hairline()
                SettingRow("Download quality", "Original keeps 24-bit WAV; Lossless 16/44 saves ~60% (needs ffmpeg on the PC)") {
                    OutlineBtn(if (s.dlQualityLossless1644) "Lossless 16/44" else "Original") { repo.updateSettings { it.copy(dlQualityLossless1644 = !it.dlQualityLossless1644) } }
                }
                Hairline()
                SettingRow("Translate lyrics", "Translations are made on ${c.pcName} at scan time and travel with downloads") {
                    OutlineBtn(if (s.translateLyrics) "English" else "Off") { repo.updateSettings { it.copy(translateLyrics = !it.translateLyrics) } }
                }
                Hairline()
                SettingRow("Movie & TV metadata", "From Plex first; otherwise from file names and poster/fanart images next to the files") {
                    Mono(if (s.pc?.plex == true) "From Plex" else "Files", style = T.metaMono, color = C.Faint)
                }
                Hairline()
                SettingRow("Offline mode", "Hide anything that isn't downloaded") { Toggle(s.offline) { v -> repo.updateSettings { it.copy(offline = v) } } }
            }
        }

        CardTitle("Storage rules")
        CardBox {
            Column {
                SettingRow("Remove watched episodes", "24 h after you finish them") { Toggle(s.autoRemoveWatched) { v -> repo.updateSettings { it.copy(autoRemoveWatched = v) } } }
                Hairline()
                SettingRow("Shows set to \"Download new\"", "Keep the next ${s.keepEpisodes} unwatched episodes") {
                    OutlineBtn("Keep ${s.keepEpisodes}") {
                        val next = when (s.keepEpisodes) { 1 -> 3; 3 -> 5; else -> 1 }
                        repo.updateSettings { it.copy(keepEpisodes = next) }
                    }
                }
                Hairline()
                SettingRow("Low storage", "Downloads pause at ${s.lowStoragePauseGb} GB free") {
                    OutlineBtn("${s.lowStoragePauseGb} GB") {
                        val next = when (s.lowStoragePauseGb) { 2 -> 5; 5 -> 10; else -> 2 }
                        repo.updateSettings { it.copy(lowStoragePauseGb = next) }
                    }
                }
            }
        }

        if (s.pc != null) {
            CardTitle("Signal Agent on ${s.pc.name}")
            CardBox {
                Column {
                    var remoteAddr by remember { mutableStateOf(s.pc.remoteUrl?.removePrefix("http://") ?: "") }
                    SettingRow("Remote access", when (s.remoteMode) {
                        RemoteMode.TAILSCALE -> "Tailscale · works on mobile data"
                        RemoteMode.HOME_ONLY -> "Home Wi-Fi only"
                        RemoteMode.PORT_FORWARD -> "Port forward · your own host"
                    }) {
                        OutlineBtn(s.remoteMode.label) {
                            val next = RemoteMode.entries[(s.remoteMode.ordinal + 1) % RemoteMode.entries.size]
                            repo.updateSettings { it.copy(remoteMode = next) }
                        }
                    }
                    if (s.remoteMode != RemoteMode.HOME_ONLY) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Field(remoteAddr, { remoteAddr = it }, if (s.remoteMode == RemoteMode.TAILSCALE) "Tailscale address, e.g. 100.101.102.103:8765" else "host.example.com:8765", Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            OutlineBtn("Save") {
                                val url = normalizeUrl(remoteAddr)
                                repo.updateSettings { it.copy(pc = it.pc?.copy(remoteUrl = url)) }
                                c.toast(if (url == null) "Remote address cleared" else "Saved")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Hairline()
                    SettingRow("Upload phone-only songs", "Copy songs that are only on this phone to the PC's music folder so Plex sees them") {
                        Toggle(s.uploadPhoneOnly) { v -> repo.updateSettings { it.copy(uploadPhoneOnly = v) }; if (v) repo.refresh(force = true) }
                    }
                    Hairline()
                    SettingRow("Watched state → Plex", if (s.pc.plex) "Progress and watched state sync to Plex" else "Plex isn't configured on the agent") {
                        Toggle(s.watchedToPlex) { v -> repo.updateSettings { it.copy(watchedToPlex = v) } }
                    }
                    Hairline()
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineBtn("Pair again") { c.st.push(Screen.Pair) }
                        OutlineBtn("Forget PC", color = C.AmberText) { repo.unpair(); c.toast("Forgot ${s.pc.name}") }
                    }
                }
            }
        }

        CardTitle("This phone")
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Music & videos on this phone", style = T.row)
                    Text(
                        if (s.phoneFolders.isEmpty()) "No folders chosen yet" else "${s.phoneFolders.size} folder${if (s.phoneFolders.size != 1) "s" else ""} · ${c.lib.tracks.count { it.origin == com.amosley.signal.core.Origin.PHONE }} songs",
                        style = T.ui(12.5.sp), color = C.Muted,
                    )
                }
                OutlineBtn("Choose folders") { c.st.push(Screen.PhoneFolders) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Mono("Signal Player ${BuildConfig.VERSION_NAME}", style = T.metaMono, color = C.Faint)
        Spacer(Modifier.height(30.dp))
    }
}

fun normalizeUrl(input: String): String? {
    val t = input.trim().removeSuffix("/")
    if (t.isEmpty()) return null
    val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    val hostPart = withScheme.substringAfter("://")
    return if (hostPart.contains(':')) withScheme else "$withScheme:8765"
}

// ---- Sync activity ------------------------------------------------------------------------------

private data class ActRow(val key: String, val title: String, val detail: String, val state: String, val progress: Float? = null, val actions: List<Pair<String, () -> Unit>> = emptyList())

@Composable
fun ActivityScreen(c: Ctx) {
    val repo = c.repo
    val titles by repo.downloads.titles.collectAsState()
    val tagJobs by repo.tagJobs.collectAsState()
    val conflicts by repo.conflicts.collectAsState()
    val uploads by repo.uploads.collectAsState()
    val remote by repo.remoteActivity.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            repo.refreshActivity()
            delay(3000)
        }
    }
    val rows = buildList {
        conflicts.forEach { cf ->
            add(ActRow("cf-${cf.trackId}", cf.title, "Artist changed on both · phone \"${cf.phoneArtist}\" · PC \"${cf.pcArtist}\"", "DECIDE",
                actions = listOf("Keep phone" to { repo.resolveConflict(cf.trackId, true) }, "Keep PC" to { repo.resolveConflict(cf.trackId, false); c.toast("Kept PC version") })))
        }
        c.dl.forEach { (id, st) ->
            val title = titles[id] ?: c.track(id)?.title ?: id
            when (st) {
                is DlState.Running -> add(ActRow("dl-$id", title, "Download from ${c.pcName}", "RUNNING", st.progress))
                is DlState.Queued -> add(ActRow("dl-$id", title, "Download from ${c.pcName}", st.reason ?: "QUEUED"))
                is DlState.Failed -> add(ActRow("dl-$id", title, "Download failed after ${st.attempts} tries · ${st.message}", "FAILED",
                    actions = listOf("Retry" to { repo.downloads.retry(id) }, "Skip" to { repo.downloads.cancel(id) })))
                else -> Unit
            }
        }
        tagJobs.filter { it.state != "DECIDE" }.forEach { j ->
            add(ActRow("tag-${j.trackId}", j.title, "Write ARTIST = \"${j.artist}\" to file on ${c.pcName}${j.message?.let { " · $it" } ?: ""}", j.state,
                actions = if (j.state == "FAILED") listOf("Retry" to { repo.retryTagJob(j.trackId) }, "Skip" to { repo.skipTagJob(j.trackId) }) else emptyList()))
        }
        uploads.forEach { (id, st) ->
            add(ActRow("up-$id", c.track(id)?.title ?: id, "Upload phone-only song → ${c.pcName}", if (st == "RUNNING") "RUNNING" else st))
        }
        remote.forEach { a ->
            add(ActRow("pc-${a.id}", a.title, listOfNotNull(a.kind.replaceFirstChar { it.uppercase() } + " on ${c.pcName}", a.detail).joinToString(" · "), a.state.uppercase(), a.progress?.toFloat()))
        }
        c.dl.filterValues { it is DlState.Done }.entries.sortedByDescending { (it.value as DlState.Done).file.finishedAt }.take(10).forEach { (id, st) ->
            add(ActRow("done-$id", titles[id] ?: c.track(id)?.title ?: id, "Downloaded · ${Fmt.bytes((st as DlState.Done).file.size)}", "DONE"))
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackButton(c)
                    Spacer(Modifier.width(12.dp))
                    Text("Sync activity", style = T.ui(26.sp, 500))
                }
            }
        }
        if (rows.isEmpty()) item { Mono("All caught up", color = C.Faint, modifier = Modifier.padding(20.dp)) }
        items(rows, key = { it.key }) { r ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = T.row, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Mono(r.detail, style = T.metaMono, color = C.Faint, maxLines = 2)
                    }
                    Spacer(Modifier.width(10.dp))
                    StateChip(r.state, r.progress)
                }
                if (r.progress != null && r.state == "RUNNING") {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(C.Hair)) { Box(Modifier.fillMaxWidth(r.progress.coerceIn(0f, 1f)).height(2.dp).background(C.Amber)) }
                }
                if (r.actions.isNotEmpty()) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.actions.forEach { (label, fn) -> OutlineBtn(label, color = C.AmberText, border = C.Amber, onClick = fn) }
                    }
                }
            }
            Hairline()
        }
        item {
            Text(
                "Downloads resume where they stopped. Failed items retry 3× with back-off. Conflicts only appear when the phone and the PC both changed the same tag.",
                style = T.ui(12.5.sp), color = C.Faint, modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun StateChip(state: String, progress: Float?) {
    when (state) {
        "RUNNING" -> Chip(progress?.let { "${(it * 100).toInt()}%" } ?: "Running", bg = Color.Transparent, fg = C.AmberText, border = C.Amber)
        "DONE" -> Chip("Done", bg = C.Green.copy(alpha = 0.18f), fg = C.Green)
        "FAILED" -> Chip("Failed", bg = C.Amber, fg = C.OnAmber)
        "DECIDE" -> Chip("Decide", bg = C.Amber, fg = C.OnAmber)
        "WI-FI" -> Chip("Wi-Fi", bg = C.Badge, fg = C.Muted)
        else -> Chip(state, bg = C.Badge, fg = C.Muted)
    }
}

// ---- Pairing ------------------------------------------------------------------------------------

private val STEPS = listOf("Find your PC", "Confirm the code", "Choose libraries", "Away from home", "You're connected")

@Composable
fun PairScreen(c: Ctx) {
    val repo = c.repo
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<FoundPc?>(null) }
    var manual by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    var agentName by remember { mutableStateOf("") }
    var agentVersion by remember { mutableStateOf<String?>(null) }
    var plex by remember { mutableStateOf(false) }
    var tailscaleGuess by remember { mutableStateOf("") }
    var libs by remember { mutableStateOf<List<Library>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var remoteMode by remember { mutableStateOf(RemoteMode.TAILSCALE) }
    var remoteAddr by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val found by repo.discovery.found.collectAsState()
    val searching by repo.discovery.searching.collectAsState()

    LaunchedEffect(step) {
        if (step == 0) repo.discovery.start() else repo.discovery.stop()
    }

    fun connectTo(pc: FoundPc) {
        target = pc
        error = null
        busy = true
        scope.launch {
            runCatching {
                val info = repo.agent.info(pc.baseUrl)
                agentName = info.name
                agentVersion = info.version
                plex = info.plex
                tailscaleGuess = info.addresses.firstOrNull { it.startsWith("100.") }?.let { "$it:${pc.port}" } ?: ""
                val start = repo.agent.pairStart(pc.baseUrl, Repository.deviceName)
                code = start.code
                step = 1
                busy = false
                while (true) {
                    delay(1500)
                    val st = repo.agent.pairStatus(pc.baseUrl, start.requestId)
                    when (st.status) {
                        "approved" -> {
                            token = st.token
                            libs = repo.agent.libraries(pc.baseUrl, st.token!!)
                            picked = libs.map { it.id }.toSet()
                            step = 2
                            return@launch
                        }
                        "denied", "expired" -> {
                            error = if (st.status == "denied") "Pairing was denied on ${info.name}" else "The code expired — try again"
                            step = 0
                            return@launch
                        }
                    }
                }
            }.onFailure {
                busy = false
                error = "Couldn't reach ${pc.host}:${pc.port} — is Signal Agent running and allowed through the firewall?"
                step = 0
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            STEPS.indices.forEach { i ->
                Box(Modifier.weight(1f).height(3.dp).background(when {
                    i == step -> C.Amber
                    i < step -> C.Fg.copy(alpha = 0.4f)
                    else -> C.Hair
                }))
            }
        }
        Spacer(Modifier.height(18.dp))
        Mono("Step ${step + 1} of ${STEPS.size}", color = C.Faint)
        Text(STEPS[step], style = T.ui(30.sp, 500, (-0.03).sp))
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            error?.let {
                Text(it, style = T.ui(13.5.sp), color = C.AmberText)
                Spacer(Modifier.height(12.dp))
            }
            when (step) {
                0 -> {
                    Text("Make sure Signal Agent is running on your PC and both devices are on the same Wi-Fi.", style = T.ui(14.sp), color = C.Muted)
                    Spacer(Modifier.height(14.dp))
                    found.forEach { pc ->
                        Row(Modifier.fillMaxWidth().border(1.dp, C.HairStrong).clickable(enabled = !busy) { connectTo(pc) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(C.Green))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pc.name, style = T.ui(16.sp, 600))
                                Mono(listOfNotNull(pc.host, pc.version?.let { "Signal Agent $it" }, if (pc.plex) "Plex detected" else null).joinToString(" · "), style = T.metaMono, color = C.Faint)
                            }
                            if (busy && target == pc) Spinner(Modifier.size(16.dp)) else Icon(Icons.Filled.ChevronRight, null, tint = C.Faint)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (searching || found.isEmpty()) Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spinner(Modifier.size(12.dp))
                        Spacer(Modifier.width(10.dp))
                        Mono("Searching the network…", style = T.metaMono, color = C.Faint)
                    }
                    Spacer(Modifier.height(16.dp))
                    Mono("Or enter address manually", color = C.Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(manual, { manual = it }, "192.168.1.20:8765", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        OutlineBtn("Connect") {
                            val url = normalizeUrl(manual) ?: return@OutlineBtn
                            val hostPort = url.substringAfter("://")
                            connectTo(FoundPc(hostPort.substringBefore(':'), hostPort.substringBefore(':'), hostPort.substringAfter(':').toIntOrNull() ?: 8765, null, false, null))
                        }
                    }
                }
                1 -> {
                    Text("Check that ${agentName.ifEmpty { "your PC" }} shows the same code, then approve it there (open http://localhost:8765 on the PC, or type approve ${code ?: ""} in the agent window).", style = T.ui(14.sp), color = C.Muted)
                    Spacer(Modifier.height(28.dp))
                    val cd = code ?: "------"
                    Text("${cd.take(3)} · ${cd.drop(3)}", style = T.mono(44.sp, 600, 0.06.sp), color = C.Fg, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Spinner(Modifier.size(12.dp))
                        Spacer(Modifier.width(10.dp))
                        Mono("Waiting for approval on ${agentName.ifEmpty { "the PC" }}", style = T.metaMono, color = C.Faint)
                    }
                }
                2 -> {
                    Text("Signal uses the same folders Plex already indexes. Music libraries download new songs; everything else streams. You can change this later.", style = T.ui(14.sp), color = C.Muted)
                    Spacer(Modifier.height(14.dp))
                    libs.forEach { lib ->
                        val on = lib.id in picked
                        Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - lib.id else picked + lib.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp).background(if (on) C.Amber else Color.Transparent).border(1.dp, if (on) C.Amber else C.Muted), contentAlignment = Alignment.Center) {
                                if (on) Icon(Icons.Filled.Check, null, tint = C.OnAmber, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(lib.name, style = T.row)
                                Mono("${lib.path} · ${lib.count} files", style = T.metaMono, color = C.Faint)
                            }
                            Chip(lib.type, bg = C.Badge, fg = C.Muted)
                        }
                        Hairline()
                    }
                    if (libs.isEmpty()) Mono("No libraries configured on the agent yet", style = T.metaMono, color = C.Faint)
                }
                3 -> {
                    listOf(
                        Triple(RemoteMode.TAILSCALE, "Tailscale — recommended", "Free · works on mobile data · install Tailscale on the PC and phone"),
                        Triple(RemoteMode.HOME_ONLY, "Home only", "Stream on home Wi-Fi; downloads still play anywhere"),
                        Triple(RemoteMode.PORT_FORWARD, "Port forward — advanced", "Your own host name forwarded to the PC"),
                    ).forEach { (m, title, sub) ->
                        val on = remoteMode == m
                        Row(
                            Modifier.fillMaxWidth().border(1.dp, if (on) C.Amber else C.HairStrong).clickable {
                                remoteMode = m
                                if (m == RemoteMode.TAILSCALE && remoteAddr.isEmpty()) remoteAddr = tailscaleGuess
                            }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(14.dp).border(1.dp, if (on) C.Amber else C.Muted).padding(3.dp)) { if (on) Box(Modifier.fillMaxSize().background(C.Amber)) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(title, style = T.ui(15.sp, 600))
                                Text(sub, style = T.ui(12.5.sp), color = C.Muted)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (remoteMode != RemoteMode.HOME_ONLY) {
                        Spacer(Modifier.height(6.dp))
                        Field(remoteAddr, { remoteAddr = it }, if (remoteMode == RemoteMode.TAILSCALE) "PC's Tailscale address, e.g. 100.101.102.103:8765" else "home.example.com:8765")
                        Spacer(Modifier.height(6.dp))
                        Mono("Optional · you can add it later in Sync", style = T.metaMono, color = C.Faint)
                    }
                }
                4 -> {
                    val cat by repo.catalog.collectAsState()
                    val status = c.status
                    val done = cat.generatedAt > 0
                    LaunchedEffect(Unit) { repo.refreshNow(force = true) }
                    Check("Connected to $agentName", true)
                    Check("Scanning library (${cat.tracks.size} songs, ${cat.movies.size} movies, ${cat.shows.size} shows)", done)
                    Check(if (plex) "Reading Plex metadata" else "Reading metadata from file names", done)
                    Check("Syncing lyrics from .lrc files", done)
                    Check("Suggesting names for untagged songs", done)
                    status.error?.let { Text(it, style = T.ui(13.sp), color = C.AmberText, modifier = Modifier.padding(top = 10.dp)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (step) {
                0 -> Mono("Skip for now", color = C.Muted, modifier = Modifier.clickable {
                    repo.discovery.stop()
                    repo.updateSettings { it.copy(skippedPairing = true) }
                    c.st.back()
                }.padding(8.dp))
                4 -> Unit
                else -> OutlineBtn("Back") { step = (step - 1).coerceAtLeast(0); if (step == 0) { code = null; error = null } }
            }
            Spacer(Modifier.weight(1f))
            when (step) {
                2 -> FilledBtn("Continue", bg = C.Amber, fg = C.OnAmber) { step = 3; if (remoteAddr.isEmpty()) remoteAddr = tailscaleGuess }
                3 -> FilledBtn("Continue", bg = C.Amber, fg = C.OnAmber) {
                    val pc = target ?: return@FilledBtn
                    val modes = libs.filter { it.id in picked }.associate { it.id to if (it.type == "music") LibMode.DOWNLOAD_NEW else LibMode.STREAM }
                    repo.savePairing(
                        PairedPc(
                            id = pc.id ?: pc.baseUrl, name = agentName.ifEmpty { pc.name }, lanUrl = pc.baseUrl,
                            remoteUrl = if (remoteMode == RemoteMode.HOME_ONLY) null else normalizeUrl(remoteAddr),
                            token = token ?: return@FilledBtn, plex = plex, agentVersion = agentVersion,
                        ),
                        modes, remoteMode,
                    )
                    step = 4
                }
                4 -> FilledBtn("Open library", bg = C.Amber, fg = C.OnAmber) {
                    c.st.stack.clear()
                    c.st.stack.add(Screen.Library)
                    repo.startAutoRefresh()
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun Check(text: String, done: Boolean) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (done) Box(Modifier.size(18.dp).background(C.Green), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = C.OnGreen, modifier = Modifier.size(13.dp)) }
        else Spinner(Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = T.ui(14.5.sp), color = if (done) C.Fg else C.Muted)
    }
}

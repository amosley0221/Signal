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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.amosley.signal.ui.components.FilledBtn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.data.PhoneFolder
import com.amosley.signal.data.PhoneFolderType
import com.amosley.signal.data.PhoneLibrary
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.Screen
import com.amosley.signal.ui.Sheet
import com.amosley.signal.ui.components.Hairline
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.components.SettingRow
import com.amosley.signal.ui.components.Toggle
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T

/** Pick which phone folders feed the library, and as what. Everything else stays hidden. */
@Composable
fun PhoneFoldersScreen(c: Ctx) {
    val folders by c.repo.phoneFolders.collectAsState()
    val chosen = c.settings.phoneFolders
    LaunchedEffect(Unit) { c.repo.rescanPhone() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackButton(c)
                    Spacer(Modifier.width(12.dp))
                    Text("Phone folders", style = T.ui(26.sp, 500))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Only the folders you choose show up in Signal. Everything else, like ringtones, recordings and sample music, stays hidden. " +
                        "Choosing a folder includes the folders inside it.",
                    style = T.ui(13.5.sp), color = C.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineBtn("Rescan") { c.repo.rescanPhone(); c.toast("Scanning this phone") }
                    if (chosen.isNotEmpty()) OutlineBtn("Clear all", color = C.AmberText) {
                        c.repo.updateSettings { it.copy(phoneFolders = emptyMap(), phoneFoldersChosen = true) }
                    }
                }
            }
        }
        phoneFolderItems(c, folders)
        item { Spacer(Modifier.height(30.dp)) }
    }
}

/** Settings tab: PC sync on top, then which phone folders feed the library. */
@Composable
fun SettingsScreen(c: Ctx) {
    val folders by c.repo.phoneFolders.collectAsState()
    val chosen = c.settings.phoneFolders
    LaunchedEffect(Unit) { c.repo.rescanPhone() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.st.stack.size > 1) {
                        BackButton(c)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text("Settings", style = T.sectionTitle)
                }
                Spacer(Modifier.height(8.dp))
                StatusLine(c)
                Spacer(Modifier.height(18.dp))
                Mono("PC sync", color = C.Muted)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().background(C.Card).border(1.dp, C.HairStrong).clickable { c.st.push(Screen.Sync) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.settings.pc?.name ?: "No PC paired", style = T.rowSecondary)
                        Text(
                            if (c.settings.pc == null) "Pair a PC to stream and download your library" else "Libraries, downloads, storage rules, remote access",
                            style = T.ui(12.5.sp), color = C.Muted,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = C.Faint)
                }
                // Songs without an artist (moved here from the Songs tab).
                val missing = untagged(c)
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Mono("Music", color = C.Muted)
                    SettingRow(
                        "${missing.size} song${if (missing.size > 1) "s have" else " has"} no artist",
                        "Suggest fictional artist names from the title, lyrics and mood, or match ones you've used before.",
                    ) { OutlineBtn("Fix", color = C.AmberText, border = C.Amber) { c.st.sheet = Sheet.Batch } }
                }
                Spacer(Modifier.height(22.dp))
                Mono("Playback", color = C.Muted)
                SettingRow(
                    "Autoplay",
                    "When your album, playlist or queue ends, keep playing similar songs from your library: the same and related artists, similar genres and era. Turn it off or on from the queue too.",
                ) { Toggle(c.settings.autoplay) { v -> c.hub.setAutoplay(v) } }
                Spacer(Modifier.height(22.dp))
                Mono("Lyrics", color = C.Muted)
                SettingRow(
                    "Find lyrics online",
                    "When a song has no .lrc file, look up time-synced lyrics on LRCLIB (free, no account). Uses the song's title, artist and length.",
                ) { Toggle(c.settings.onlineLyrics) { v -> c.repo.updateSettings { it.copy(onlineLyrics = v) } } }
                SettingRow(
                    "Find movie & TV details online",
                    "For videos only on this phone: posters and summaries from Apple TV (movies) and TVmaze (shows). Titles in your PC's Plex library use Plex instead.",
                ) { Toggle(c.settings.onlineVideoInfo) { v -> c.repo.updateSettings { it.copy(onlineVideoInfo = v) }; if (v) c.repo.enrichPhoneVideos() } }
                SettingRow(
                    "Include Plex's Continue Watching",
                    "Continue Watching and Up Next show what you watch in Signal. Turn this on to also show what's in Plex's Continue Watching row (things you watched on the TV or in the Plex app).",
                ) { Toggle(c.settings.plexContinueWatching) { v -> c.repo.updateSettings { it.copy(plexContinueWatching = v) } } }
                Spacer(Modifier.height(16.dp))
                Mono("Songs on both phone and PC", color = C.Muted)
                Spacer(Modifier.height(6.dp))
                Text(
                    "The same song (title, artist, album and length) on this phone and on ${c.pcName} shows once. Choose which copy plays: the phone copy works without the PC; the PC copy keeps edits and lyrics in sync.",
                    style = T.ui(13.sp), color = C.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.amosley.signal.core.DuplicateMode.entries.forEach { m ->
                        val on = c.settings.duplicates == m
                        Box(
                            Modifier.background(if (on) C.Amber else Color.Transparent).border(1.dp, if (on) C.Amber else C.HairStrong)
                                .clickable { c.repo.updateSettings { it.copy(duplicates = m) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) { Text(m.label, style = T.ui(12.5.sp, 600), color = if (on) C.OnAmber else C.Fg) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Mono("Folders on this phone", color = C.Muted)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Only the folders you choose show up in Signal. Choosing a folder includes the folders inside it.",
                    style = T.ui(13.sp), color = C.Muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineBtn("Rescan") { c.repo.rescanPhone(); c.toast("Scanning this phone") }
                    if (chosen.isNotEmpty()) OutlineBtn("Clear all", color = C.AmberText) {
                        c.repo.updateSettings { it.copy(phoneFolders = emptyMap(), phoneFoldersChosen = true) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        phoneFolderItems(c, folders)
        item {
            Mono("Signal Player ${com.amosley.signal.BuildConfig.VERSION_NAME}", style = T.metaMono, color = C.Faint, modifier = Modifier.padding(20.dp))
        }
    }
}

/**
 * Chosen folders plus a folder browser: start at the top of the phone's storage, tap into folders,
 * then "Use this folder for Music / Music Videos / Movies / TV Shows". Everything inside it is included.
 */
fun LazyListScope.phoneFolderItems(c: Ctx, folders: List<PhoneFolder>) {
    item(key = "pf-browser") { PhoneFolderPicker(c, folders) }
}

@Composable
private fun PhoneFolderPicker(c: Ctx, folders: List<PhoneFolder>) {
    val chosen = c.settings.phoneFolders
    var browsing by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (chosen.isEmpty()) {
            Text("No folders chosen yet. Add the folders that hold your music and videos.", style = T.ui(13.sp), color = C.AmberText, modifier = Modifier.padding(vertical = 6.dp))
        }
        chosen.entries.sortedBy { it.key.lowercase() }.forEach { (path, type) ->
            val counts = folders.filter { it.path == path || it.path.startsWith("$path/") }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(path, style = T.row, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Mono("${type.label} · ${counts.sumOf { it.audio }} audio · ${counts.sumOf { it.video }} video", style = T.metaMono, color = C.Faint)
                }
                OutlineBtn("Change") { browsing = path }
                Spacer(Modifier.width(6.dp))
                OutlineBtn("Remove", color = C.AmberText) { c.repo.setPhoneFolder(path, null) }
            }
            Hairline()
        }
        Spacer(Modifier.height(10.dp))
        if (browsing == null) {
            FilledBtn("+ Add a folder", bg = C.Amber, fg = C.OnAmber, modifier = Modifier.fillMaxWidth()) { browsing = "" }
            return@Column
        }
        // ---- browser ----
        val here = browsing!!
        Column(Modifier.fillMaxWidth().border(1.dp, C.HairStrong).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (here.isNotEmpty()) {
                    OutlineBtn("↑ Up") { browsing = here.substringBeforeLast('/', "") }
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (here.isEmpty()) "Phone storage" else here, style = T.rowSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Mono("Close", color = C.Muted, modifier = Modifier.clickable { browsing = null }.padding(6.dp))
            }
            val own = PhoneLibrary.own(here, folders)
            if (own != null) Mono("In this folder: ${own.audio} audio · ${own.video} video", style = T.metaMono, color = C.Faint, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(6.dp))
            val kids = PhoneLibrary.children(here, folders)
            if (kids.isEmpty() && own == null) Mono("No music or videos here", style = T.metaMono, color = C.Faint, modifier = Modifier.padding(vertical = 8.dp))
            kids.forEach { k ->
                val type = chosen[k.path]
                Row(Modifier.fillMaxWidth().clickable { browsing = k.path }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, null, tint = if (type != null) C.Amber else C.Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(k.path.substringAfterLast('/'), style = T.row, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Mono(
                            listOfNotNull(k.audio.takeIf { it > 0 }?.let { "$it audio" }, k.video.takeIf { it > 0 }?.let { "$it video" }, type?.label).joinToString(" · "),
                            style = T.metaMono, color = if (type != null) C.AmberText else C.Faint,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = C.Faint)
                }
                Hairline()
            }
            if (here.isNotEmpty()) {
                val all = folders.filter { it.path == here || it.path.startsWith("$here/") }
                val audio = all.sumOf { it.audio }
                val video = all.sumOf { it.video }
                Spacer(Modifier.height(12.dp))
                Mono("Use “${here.substringAfterLast('/')}” for", color = C.Muted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val types = when {
                        audio > 0 && video == 0 -> listOf(PhoneFolderType.MUSIC)
                        video > 0 && audio == 0 -> listOf(PhoneFolderType.MOVIES, PhoneFolderType.TV, PhoneFolderType.MUSIC_VIDEOS)
                        else -> PhoneFolderType.entries
                    }
                    types.forEach { t ->
                        Option(t.label, chosen[here] == t) {
                            c.repo.setPhoneFolder(here, t)
                            c.toast("${here.substringAfterLast('/')} added as ${t.label}")
                            browsing = null
                        }
                    }
                }
                Text(
                    "Everything inside is included. Music uses its tags (artist, album); TV Shows reads Show / Season folders and names like S01E02; Movies reads names like \"Title (2010)\".",
                    style = T.ui(12.sp), color = C.Faint, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Option(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (on) C.Amber else Color.Transparent)
            .border(1.dp, if (on) C.Amber else C.HairStrong)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) { Text(label, style = T.ui(12.sp, 600), color = if (on) C.OnAmber else C.Fg, maxLines = 1) }
}

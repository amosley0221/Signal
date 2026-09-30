package com.amosley.signal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.cast.SonosOutput
import com.amosley.signal.core.ArtistSuggester
import com.amosley.signal.core.ArtistSuggestion
import com.amosley.signal.core.Fmt
import com.amosley.signal.core.Origin
import com.amosley.signal.data.DlState
import com.amosley.signal.playback.OutputKind
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.Sheet
import com.amosley.signal.ui.components.Art
import com.amosley.signal.ui.components.Chip
import com.amosley.signal.ui.components.FilledBtn
import com.amosley.signal.ui.components.Hairline
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.components.QualityBadge
import com.amosley.signal.ui.components.Spinner
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SheetHost(c: Ctx) {
    val sheet = c.st.sheet ?: return
    if (sheet is Sheet.NowPlaying) {
        if (c.unfolded) {
            // Unfolded: Now Playing opens in the right pane instead of covering the screen.
            LaunchedEffect(Unit) { c.st.nowPlayingPane = true; c.st.sheet = null }
            return
        }
        Box(
            Modifier.fillMaxSize().background(C.Bg).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) { NowPlayingContent(c, pane = false) }
        return
    }
    if (sheet is Sheet.LyricsEditor) {
        Box(
            Modifier.fillMaxSize().background(C.Bg).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) { LyricsEditor(c, sheet.trackId) }
        return
    }
    val dismiss = { c.st.sheet = null }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() },
        contentAlignment = if (c.unfolded) Alignment.Center else Alignment.BottomCenter,
    ) {
        val shape = if (c.unfolded) {
            Modifier.width(480.dp).heightIn(max = 640.dp).shadow(30.dp)
        } else {
            Modifier.fillMaxWidth().heightIn(max = 760.dp).windowInsetsPadding(WindowInsets.navigationBars)
        }
        // Tall sheets stop below the status bar (plus a gap), so the title and Done stay easy to reach.
        Column(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = if (c.unfolded) 0.dp else 48.dp),
        ) {
        Column(
            shape.background(C.Surface).imePadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
        ) {
            when (sheet) {
                is Sheet.Queue -> QueueSheet(c)
                is Sheet.Edit -> EditSheet(c, sheet.trackId)
                is Sheet.Batch -> BatchSheet(c)
                is Sheet.Import -> ImportSheet(c)
                is Sheet.Cast -> CastSheet(c, sheet.video)
                is Sheet.Actions -> ActionsSheet(c, sheet.trackId)
                is Sheet.AddTo -> AddToSheet(c, sheet.trackId)
                is Sheet.NewPlaylist -> NewPlaylistSheet(c, sheet.trackId)
                is Sheet.Art -> ArtSheet(c, sheet.album, sheet.artist)
                is Sheet.FixMatch -> FixMatchSheet(c, sheet.movieId, sheet.showKey)
                is Sheet.NowPlaying, is Sheet.LyricsEditor -> Unit
            }
        }
        }
    }
}

@Composable
private fun SheetHeader(title: String, sub: String? = null, action: String, onAction: () -> Unit, extra: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.sheetTitle)
            if (sub != null) Mono(sub, style = T.metaMono, color = C.Faint, maxLines = 3, modifier = Modifier.padding(top = 3.dp))
        }
        extra()
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.border(1.dp, C.HairStrong).clickable(onClick = onAction).padding(horizontal = 14.dp, vertical = 10.dp),
        ) { Mono(action, color = C.Fg, style = T.mono(12.sp, 600)) }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(44.dp).border(1.dp, C.HairStrong).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = T.ui(14.sp), color = C.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(value, onChange, textStyle = T.ui(14.sp, color = C.Fg), singleLine = true, cursorBrush = SolidColor(C.Amber), modifier = Modifier.fillMaxWidth())
    }
}

// ---- Edit / suggest artist ----------------------------------------------------------------------

@Composable
private fun ColumnScope.EditSheet(c: Ctx, trackId: String) {
    val t = c.track(trackId) ?: return
    var manual by remember(trackId) { mutableStateOf("") }
    var remote by remember(trackId, c.st.stylePrompt) { mutableStateOf<List<ArtistSuggestion>?>(null) }
    val lyrics = c.repo.cachedLyrics(t.id)?.lines
    LaunchedEffect(trackId, c.st.stylePrompt) {
        // Prefer the PC's suggester (it can read the file's lyrics); fall back to the on-device port.
        val pc = c.settings.pc
        val base = c.status.baseUrl
        if (t.origin == Origin.PC && pc != null && base != null) {
            remote = runCatching { c.repo.agent.suggest(base, pc.token, t.id, c.st.stylePrompt) }.getOrNull()
        }
    }
    val suggestions = remote ?: ArtistSuggester.suggest(t, c.lib.tracks, lyrics, c.st.stylePrompt)
    val existing = c.lib.artists.map { it.first }.toSet()
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(t.albumKey + t.title, c.art(t), Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = T.rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Mono(listOfNotNull(t.album, t.bitsLabel).joinToString(" · "), style = T.metaMono, color = C.Faint)
        }
        Mono("Done", color = C.Fg, style = T.mono(11.sp, 600), modifier = Modifier.clickable { c.st.sheet = null }.padding(4.dp))
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TagFields(c, t)
        Spacer(Modifier.height(20.dp))
        Mono("Artist", color = C.Muted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.artist ?: "—", style = T.ui(17.sp, 600), modifier = Modifier.weight(1f))
            if (t.artist == null) Chip("Empty", bg = C.AmberTint, fg = C.AmberText)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(manual, { manual = it }, "Type an artist name", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            EnabledOutlineBtn("Set", enabled = manual.isNotBlank()) {
                c.repo.setArtist(t, manual.trim())
                c.toast("Artist set to ${manual.trim()}")
            }
        }
        Spacer(Modifier.height(16.dp))
        Mono("Suggested fictional artists", color = C.Muted)
        Spacer(Modifier.height(8.dp))
        Field(c.st.stylePrompt, { c.st.stylePrompt = it }, "Optional style prompt — e.g. \"a duo from a rainy port city\"")
        Spacer(Modifier.height(6.dp))
        suggestions.forEach { s ->
            val chosen = t.artist == s.name
            Row(
                Modifier.fillMaxWidth().background(if (chosen) C.Amber else Color.Transparent).padding(horizontal = if (chosen) 10.dp else 0.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = T.ui(15.sp, 600), color = if (chosen) C.OnAmber else C.Fg)
                        if (s.kind == "existing" || s.name in existing) {
                            Spacer(Modifier.width(6.dp))
                            Chip("In library", bg = if (chosen) C.OnAmber.copy(alpha = 0.15f) else C.Badge, fg = if (chosen) C.OnAmber else C.Muted)
                        }
                    }
                    Mono(s.why, style = T.metaMono, color = if (chosen) C.OnAmber.copy(alpha = 0.7f) else C.Faint, maxLines = 2)
                }
                if (chosen) {
                    Mono("Applied", color = C.OnAmber, style = T.mono(11.sp, 600))
                } else {
                    Mono("Use", color = C.AmberText, style = T.mono(11.sp, 600), modifier = Modifier.clickable {
                        c.repo.setArtist(t, s.name)
                        c.toast("Artist set to ${s.name}")
                    }.padding(6.dp))
                }
            }
            Hairline()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Suggestions use the title, synced lyrics, mood tags and artists already in your library. " +
                if (t.origin == Origin.PC) "The ARTIST tag is written back to the file on ${c.pcName}." else "The name is saved on this phone.",
            style = T.ui(12.5.sp), color = C.Faint,
        )
    }
}

/** Title / album / genre / year / track editor. Only changed fields are saved (and written to PC files). */
@Composable
private fun TagFields(c: Ctx, t: com.amosley.signal.core.Track) {
    var title by remember(t.id) { mutableStateOf(t.title) }
    var artist by remember(t.id) { mutableStateOf(t.artist.orEmpty()) }
    var album by remember(t.id) { mutableStateOf(t.album.orEmpty()) }
    var albumArtist by remember(t.id) { mutableStateOf(t.albumArtist.orEmpty()) }
    var genre by remember(t.id) { mutableStateOf(t.genres.joinToString("; ")) }
    var year by remember(t.id) { mutableStateOf(t.year?.toString().orEmpty()) }
    var track by remember(t.id) { mutableStateOf(t.track?.toString().orEmpty()) }
    var disc by remember(t.id) { mutableStateOf(t.disc?.toString().orEmpty()) }
    fun changed(new: String, old: String?) = new.trim().takeIf { it.isNotEmpty() && it != old.orEmpty() }
    fun changedInt(new: String, old: Int?) = new.trim().toIntOrNull()?.takeIf { it != old }
    val edit = com.amosley.signal.data.TrackEdit(
        title = changed(title, t.title), artist = changed(artist, t.artist), album = changed(album, t.album),
        albumArtist = changed(albumArtist, t.albumArtist), genre = changed(genre, t.genres.joinToString("; ")),
        year = changedInt(year, t.year), track = changedInt(track, t.track), disc = changedInt(disc, t.disc),
    )
    val dirty = edit != com.amosley.signal.data.TrackEdit()
    Mono("Song details", color = C.Muted)
    Spacer(Modifier.height(8.dp))
    LabeledField("Title", title) { title = it }
    LabeledField("Artist", artist) { artist = it }
    LabeledField("Album", album) { album = it }
    LabeledField("Album artist", albumArtist) { albumArtist = it }
    LabeledField("Genre", genre, hint = "e.g. dream pop; slow") { genre = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { LabeledField("Year", year) { year = it.filter(Char::isDigit).take(4) } }
        Box(Modifier.weight(1f)) { LabeledField("Track", track) { track = it.filter(Char::isDigit).take(3) } }
        Box(Modifier.weight(1f)) { LabeledField("Disc", disc) { disc = it.filter(Char::isDigit).take(2) } }
    }
    Spacer(Modifier.height(4.dp))
    FilledBtn("Save changes", bg = C.Amber, fg = C.OnAmber, enabled = dirty, modifier = Modifier.fillMaxWidth()) {
        c.repo.editTrack(t, edit)
        c.toast(if (t.origin == Origin.PC) "Saved · writing tags on ${c.pcName}" else "Saved")
    }
    if (t.origin == Origin.PHONE) {
        Text("Changes to songs on this phone are saved in Signal; the file itself isn't changed.", style = T.ui(12.sp), color = C.Faint, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LabeledField(label: String, value: String, hint: String = "", onChange: (String) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Mono(label, style = T.metaMono, color = C.Faint)
        Spacer(Modifier.height(3.dp))
        Field(value, onChange, hint)
    }
}

@Composable
private fun EnabledOutlineBtn(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.alpha(if (enabled) 1f else 0.4f)) { OutlineBtn(text, color = C.AmberText, border = C.Amber) { if (enabled) onClick() } }
}

// ---- Batch fix ----------------------------------------------------------------------------------

@Composable
private fun ColumnScope.BatchSheet(c: Ctx) {
    val list = untagged(c)
    val idx = c.st.batchIdx.value
    SheetHeader("Name ${list.size} untagged song${if (list.size != 1) "s" else ""}", "Tap a name to cycle alternatives", "Cancel", { c.st.sheet = null })
    val picks = list.map { t ->
        val sg = ArtistSuggester.suggest(t, c.lib.tracks, c.repo.cachedLyrics(t.id)?.lines)
        val i = (idx[t.id] ?: 0) % sg.size
        Triple(t, sg[i], i)
    }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        picks.forEach { (t, pick, i) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(t.albumKey + t.title, c.art(t), Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = T.ui(14.5.sp, 500), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Mono(pick.why, style = T.metaMono, color = C.Faint)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.background(C.Amber).clickable { c.st.batchIdx.value = idx + (t.id to i + 1) }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("${pick.name} ↻", style = T.ui(12.5.sp, 600), color = C.OnAmber, maxLines = 1)
                }
            }
            Hairline()
        }
        if (list.isEmpty()) Mono("Every song has an artist", color = C.Faint, modifier = Modifier.padding(vertical = 20.dp))
    }
    Spacer(Modifier.height(14.dp))
    FilledBtn("Apply all ${list.size} names", bg = C.Amber, fg = C.OnAmber, enabled = list.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        picks.forEach { (t, pick, _) -> c.repo.setArtist(t, pick.name) }
        c.st.sheet = null
        c.toast("${picks.size} artists assigned")
    }
}

// ---- Import review ------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ImportSheet(c: Ctx) {
    val files = importCandidates(c)
    val idx = c.st.importIdx.value
    SheetHeader("${files.size} new file${if (files.size != 1) "s" else ""} from ${c.pcName}", "Review artists before adding", "Later", { c.st.sheet = null })
    val picks = files.map { f ->
        val sg = ArtistSuggester.suggest(f, c.lib.tracks, c.repo.cachedLyrics(f.id)?.lines)
        val i = (idx[f.id] ?: 0) % sg.size
        Triple(f, sg, i)
    }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        picks.forEach { (f, sg, i) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Mono(f.album?.let { "$it / ${f.title}.${f.container?.lowercase()}" } ?: "${f.title}.${f.container?.lowercase()}", style = T.metaMono, color = C.Faint)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.title, style = T.rowSecondary, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    QualityBadge(f.quality.label)
                }
                Mono((f.tags + Fmt.dur(f.durationMs)).joinToString(" · "), style = T.metaMono, color = C.Faint)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.background(C.Amber).padding(horizontal = 8.dp, vertical = 5.dp)) { Text(sg[i].name, style = T.ui(12.5.sp, 600), color = C.OnAmber) }
                    sg.withIndex().filter { it.index != i }.take(3).forEach { (k, s) ->
                        Box(Modifier.border(1.dp, C.HairStrong).clickable { c.st.importIdx.value = idx + (f.id to k) }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Text(s.name, style = T.ui(12.5.sp, 500), color = C.Fg)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Mono(sg[i].why, style = T.metaMono, color = C.Faint, maxLines = 2)
            }
            Hairline()
        }
    }
    Spacer(Modifier.height(14.dp))
    FilledBtn("Add to library & write tags", bg = C.Amber, fg = C.OnAmber, modifier = Modifier.fillMaxWidth()) {
        picks.forEach { (f, sg, i) -> c.repo.setArtist(f, sg[i].name) }
        c.repo.updateSettings { it.copy(importReviewedAt = System.currentTimeMillis()) }
        c.st.sheet = null
        c.toast("${picks.size} songs added")
    }
}

// ---- Track actions / add to playlist / new playlist ---------------------------------------------

@Composable
private fun ActionRow(title: String, hint: String? = null, hintColor: Color = C.Faint, chip: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.row, modifier = Modifier.weight(1f))
        if (chip != null) Chip(chip, bg = C.AmberTint, fg = C.AmberText)
        if (hint != null) Mono(hint, color = hintColor, style = T.metaMono)
    }
    Hairline()
}

@Composable
private fun ColumnScope.ActionsSheet(c: Ctx, trackId: String) {
    val t = c.track(trackId) ?: return
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(t.albumKey + t.title, c.art(t), Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = T.rowSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Mono(listOfNotNull(t.artist ?: "Unknown artist", t.album).joinToString(" · "), style = T.metaMono, color = C.Faint)
        }
    }
    Hairline()
    ActionRow(if (c.isFavorite(t.id)) "Remove from Favorites" else "Add to Favorites", if (c.isFavorite(t.id)) "♥" else "♡", hintColor = C.Amber) {
        c.toggleFavorite(t); c.st.sheet = null
    }
    ActionRow("Play next", "Top of queue") { c.hub.playNext(t); c.st.sheet = null }
    ActionRow("Add to queue", "End of queue") { c.hub.addToQueue(t); c.st.sheet = null }
    ActionRow("Add to playlist…") { c.st.sheet = Sheet.AddTo(t.id) }
    if (t.artist != null) ActionRow("Go to artist", t.artist) { c.st.openArtist(t.artist) }
    ActionRow("Edit song details / artist", chip = if (t.artist == null) "No artist" else null) { c.st.sheet = Sheet.Edit(t.id) }
    ActionRow("Add / edit lyrics", "Paste · tap to sync") { c.st.sheet = Sheet.LyricsEditor(t.id) }
    if (t.origin == Origin.PC) {
        when (val d = c.dlState(t.id)) {
            is DlState.Done -> ActionRow("Remove download", Fmt.bytes(d.file.size)) { c.repo.downloads.remove(t.id); c.st.sheet = null; c.toast("Removed download") }
            is DlState.Running, is DlState.Queued -> ActionRow("Cancel download") { c.repo.downloads.cancel(t.id); c.st.sheet = null }
            else -> ActionRow("Download", Fmt.bytes(t.size)) { c.repo.download(t); c.st.sheet = null; c.toast("Downloading · ${t.title}") }
        }
    }
}

@Composable
private fun ColumnScope.AddToSheet(c: Ctx, trackId: String) {
    val t = c.track(trackId) ?: return
    SheetHeader("Add to playlist", t.title, "Cancel", { c.st.sheet = null })
    Row(Modifier.fillMaxWidth().clickable { c.st.sheet = Sheet.NewPlaylist(trackId) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Add, null, tint = C.AmberText)
        Spacer(Modifier.width(10.dp))
        Text("New playlist", style = T.row)
    }
    Hairline()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        c.playlists.forEach { pl ->
            ActionRow(pl.name, "${pl.trackIds.size} songs") {
                c.repo.addToPlaylist(pl.id, t.id)
                c.st.sheet = null
                c.toast("Added to ${pl.name}")
            }
        }
    }
}

@Composable
private fun ColumnScope.NewPlaylistSheet(c: Ctx, trackId: String?) {
    var name by remember { mutableStateOf("") }
    SheetHeader("New playlist", null, "Cancel", { c.st.sheet = null })
    Field(name, { name = it }, "Playlist name")
    Spacer(Modifier.height(8.dp))
    Mono("Saved on this phone", style = T.metaMono, color = C.Faint)
    Spacer(Modifier.height(16.dp))
    FilledBtn("Create", bg = C.Amber, fg = C.OnAmber, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        val pl = c.repo.createPlaylist(name, trackId)
        c.st.sheet = null
        c.toast(if (trackId != null) "Added to ${pl.name}" else "Created ${pl.name}")
    }
}

// ---- Queue --------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.QueueSheet(c: Ctx) {
    val next = c.player.upNext
    SheetHeader("Up next", "Playing from your queue · long-press to reorder", "Done", { c.st.sheet = null }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlineBtn("Shuffle", color = if (c.player.shuffle) C.OnAmber else C.Fg, border = if (c.player.shuffle) C.Amber else C.HairStrong,
                modifier = Modifier.background(if (c.player.shuffle) C.Amber else Color.Transparent)) { c.hub.setShuffle(!c.player.shuffle) }
            OutlineBtn("Clear") { c.hub.clearQueue() }
        }
    }
    c.player.current?.let { t ->
        Mono("Now playing", color = C.Muted)
        Row(Modifier.fillMaxWidth().background(C.Card).padding(8.dp).padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(t.albumKey + t.title, c.art(t), Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = T.ui(14.5.sp, 600), color = C.AmberText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Mono("${t.artist ?: "Unknown artist"} · ${t.quality.label}", style = T.metaMono, color = C.Faint)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    if (next.isEmpty()) {
        Mono("Queue is empty · use ⋯ on any song → Play next", color = C.Faint, modifier = Modifier.padding(vertical = 24.dp))
        return
    }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        next.forEachIndexed { i, t ->
            val isDragged = dragging == i
            Row(
                Modifier.fillMaxWidth().height(60.dp)
                    .offset { IntOffset(0, if (isDragged) dragY.roundToInt() else 0) }
                    .background(if (isDragged) C.Card else Color.Transparent)
                    .clickable { c.hub.jumpTo(i) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%02d".format(i + 1), style = T.mono(11.sp, 500, 0.sp), color = C.Faint, modifier = Modifier.width(26.dp))
                Art(t.albumKey + t.title, c.art(t), Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = T.ui(14.5.sp, 500), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Mono("${t.artist ?: "Unknown artist"} · ${t.quality.label}", style = T.metaMono, color = C.Faint)
                }
                Box(Modifier.size(36.dp).clickable { c.hub.removeFromQueue(i) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Remove", tint = C.Muted, modifier = Modifier.size(18.dp))
                }
                Box(
                    Modifier.size(36.dp).pointerInput(i, next.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = i; dragY = 0f },
                            onDragEnd = {
                                val to = (i + (dragY / rowPx).roundToInt()).coerceIn(0, next.lastIndex)
                                if (to != i) c.hub.moveInQueue(i, to)
                                dragging = -1; dragY = 0f
                            },
                            onDragCancel = { dragging = -1; dragY = 0f },
                        ) { change, amount -> change.consume(); dragY += amount.y }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.DragHandle, "Reorder", tint = C.Faint) }
            }
        }
    }
}

// ---- Play on (Cast + Sonos) ---------------------------------------------------------------------

@Composable
private fun OutputRow(name: String, sub: String, active: Boolean, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).then(
                if (active) Modifier.shadow(6.dp, spotColor = C.Amber, ambientColor = C.Amber).background(C.Amber) else Modifier.border(1.dp, C.Muted),
            ),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = T.row, color = if (active) C.AmberText else C.Fg)
            Mono(sub, style = T.metaMono, color = C.Faint)
        }
        trailing()
    }
    Hairline()
}

@Composable
private fun ColumnScope.CastSheet(c: Ctx, video: Boolean) {
    val app = c.app
    val castDevices by app.cast.devices.collectAsState()
    val rooms by app.sonos.rooms.collectAsState()
    val scanning by app.sonos.scanning.collectAsState()
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        app.cast.startScan()
        if (!video) app.sonos.discover()
        onDispose { app.cast.stopScan() }
    }
    val activeSonos = app.hub.remoteOutput as? SonosOutput
    val note = when {
        video -> "Video and audio on the TV · phone is the remote"
        c.player.outputKind == OutputKind.SONOS -> "Sonos streams directly from ${c.pcName} · hi-res files play at 48 kHz"
        else -> "Cast to Sonos or a TV · audio streams from ${c.pcName}, not the phone"
    }
    SheetHeader("Play on", note, "Done", { c.st.sheet = null })
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        OutputRow("This phone", "Speaker / headphones", c.player.output == null && app.videoCast == null) {
            if (c.player.outputKind == OutputKind.CAST || app.videoCast != null) app.cast.disconnect()
            app.hub.setRemote(null)
            app.videoCast = null
            c.st.sonosGroup = emptySet()
            c.toast("Playing on this phone")
        }
        castDevices.forEach { d ->
            val active = d.selected
            OutputRow(d.name, "${d.description} · Cast", active) {
                if (video) app.pendingVideoCast = true
                if (!active) app.cast.select(d.id) else if (video) app.startVideoCast()
                c.toast("Playing on ${d.name}")
                c.st.sheet = null
            }
        }
        if (!video) {
            rooms.forEach { r ->
                val active = activeSonos?.room?.uuid == r.uuid
                val grouped = r.uuid in c.st.sonosGroup
                OutputRow(r.name, "${if (r.model.startsWith("Sonos", true)) r.model else "Sonos ${r.model}"} · up to 24-bit / 48 kHz", active || grouped, trailing = {
                    if (activeSonos != null && !active) {
                        GroupToggle(grouped) {
                            scope.safeLaunch {
                                runCatching {
                                    if (grouped) app.sonos.leave(r) else app.sonos.join(r, activeSonos.room)
                                }.onSuccess {
                                    c.st.sonosGroup = if (grouped) c.st.sonosGroup - r.uuid else c.st.sonosGroup + r.uuid
                                }.onFailure { c.toast("Couldn't group ${r.name}") }
                            }
                        }
                    }
                }) {
                    if (active) return@OutputRow
                    if (c.player.outputKind == OutputKind.CAST) app.cast.disconnect()
                    c.st.sonosGroup = emptySet()
                    app.hub.setRemote(SonosOutput(app.sonos, r, app.scope) { app.toast(it) })
                    scope.safeLaunch { runCatching { app.sonos.groupVolume(r) }.getOrNull()?.let { c.st.sonosVolume = it } }
                    c.toast("Playing on ${r.name}")
                }
            }
            if (scanning) Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(Modifier.size(12.dp))
                Spacer(Modifier.width(10.dp))
                Mono("Looking for Sonos and Cast devices…", style = T.metaMono, color = C.Faint)
            }
            if (!scanning && rooms.isEmpty() && castDevices.isEmpty()) {
                Mono("No speakers or TVs found on this Wi-Fi", style = T.metaMono, color = C.Faint, modifier = Modifier.padding(vertical = 12.dp))
            }
            if (!scanning && rooms.isEmpty()) SonosByIp(c)
            if (activeSonos != null) {
                val names = listOf(activeSonos.room.name) + rooms.filter { it.uuid in c.st.sonosGroup }.map { it.name }
                Spacer(Modifier.height(14.dp))
                Mono("Volume · ${names.joinToString(" + ")}", color = C.Muted)
                Slider(
                    value = c.st.sonosVolume.toFloat(), onValueChange = { c.st.sonosVolume = it.roundToInt() },
                    onValueChangeFinished = { scope.safeLaunch { runCatching { app.sonos.setGroupVolume(activeSonos.room, c.st.sonosVolume) } } },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = C.Amber, activeTrackColor = C.Amber, inactiveTrackColor = C.HairStrong),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.st.sonosGroup.isNotEmpty()) OutlineBtn("Ungroup all") {
                        scope.safeLaunch {
                            rooms.filter { it.uuid in c.st.sonosGroup }.forEach { runCatching { app.sonos.leave(it) } }
                            c.st.sonosGroup = emptySet()
                        }
                    }
                    OutlineBtn("Group all rooms") {
                        scope.safeLaunch {
                            val others = rooms.filter { it.uuid != activeSonos.room.uuid }
                            others.forEach { runCatching { app.sonos.join(it, activeSonos.room) } }
                            c.st.sonosGroup = others.map { it.uuid }.toSet()
                        }
                    }
                    FilledBtn("Back to phone", bg = C.Amber, fg = C.OnAmber, modifier = Modifier.height(32.dp)) {
                        app.hub.setRemote(null)
                        c.st.sonosGroup = emptySet()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Grouping and volume are sent to your Sonos system, so the group stays after the app closes.", style = T.ui(12.5.sp), color = C.Faint)
            }
        }
    }
}

@Composable
private fun GroupToggle(grouped: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp).background(if (grouped) C.Amber else Color.Transparent).border(1.dp, if (grouped) C.Amber else C.HairStrong)
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(if (grouped) "GROUPED ✓" else "+ GROUP", style = T.mono(10.sp, 600, 0.08.sp), color = if (grouped) C.OnAmber else C.Fg) }
}

/** No Sonos found: say what was tried, and let the user type a speaker's IP (Sonos app → Settings → System → About My System). */
@Composable
private fun SonosByIp(c: Ctx) {
    val app = c.app
    val last by app.sonos.lastScan.collectAsState()
    val scope = rememberCoroutineScope()
    var ip by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 12.dp)) {
        Mono("No Sonos found" + (last?.let { " · $it" } ?: ""), style = T.metaMono, color = C.Faint)
        Spacer(Modifier.height(6.dp))
        Text("Add a speaker by its IP address. Find it in the Sonos app under Settings → System → About My System.", style = T.ui(12.5.sp), color = C.Muted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                ip, { ip = it.filter { ch -> ch.isDigit() || ch == '.' } }, singleLine = true,
                textStyle = T.ui(15.sp).copy(color = C.Fg), cursorBrush = SolidColor(C.Amber),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).border(1.dp, C.HairStrong).padding(horizontal = 10.dp, vertical = 9.dp),
                decorationBox = { inner -> if (ip.isEmpty()) Text("192.168.1.50", style = T.ui(15.sp), color = C.Faint); inner() },
            )
            Spacer(Modifier.width(8.dp))
            OutlineBtn(if (busy) "Adding…" else "Add") {
                if (busy || ip.isBlank()) return@OutlineBtn
                busy = true
                scope.safeLaunch {
                    c.toast(app.sonos.addByIp(ip))
                    busy = false
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlineBtn("Search again") { app.sonos.discover() }
    }
}

/** Like launch, but a failure (a speaker or the PC refusing a request) is logged instead of crashing the app. */
private fun kotlinx.coroutines.CoroutineScope.safeLaunch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) = launch {
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        android.util.Log.e("Signal", "action failed", e)
    }
}

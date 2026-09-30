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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
import com.amosley.signal.data.PhoneFolderType
import com.amosley.signal.data.PhoneLibrary
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.components.Hairline
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
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
        if (folders.isEmpty()) item {
            Mono("No music or videos found · allow access to music and videos in Android settings", color = C.Faint, maxLines = 3, modifier = Modifier.padding(20.dp))
        }
        items(folders, key = { it.path }) { f ->
            val own = chosen[f.path]
            val inherited = if (own == null) PhoneLibrary.typeOf(f.path, chosen) else null
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(f.path, style = T.row, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Mono(
                    listOfNotNull(
                        f.audio.takeIf { it > 0 }?.let { "$it audio" },
                        f.video.takeIf { it > 0 }?.let { "$it video" },
                        inherited?.let { "included as ${it.label} from parent folder" },
                    ).joinToString(" · "),
                    style = T.metaMono, color = if (inherited != null) C.AmberText else C.Faint,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Option("Off", own == null && inherited == null) { c.repo.setPhoneFolder(f.path, null) }
                    val types = if (f.audio > 0 && f.video == 0) listOf(PhoneFolderType.MUSIC)
                    else if (f.video > 0 && f.audio == 0) listOf(PhoneFolderType.MUSIC_VIDEOS, PhoneFolderType.MOVIES, PhoneFolderType.TV)
                    else PhoneFolderType.entries
                    types.forEach { t -> Option(t.label, own == t) { c.repo.setPhoneFolder(f.path, t) } }
                }
            }
            Hairline()
        }
        item { Spacer(Modifier.height(30.dp)) }
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

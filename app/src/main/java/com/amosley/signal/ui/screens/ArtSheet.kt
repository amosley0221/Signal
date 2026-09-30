package com.amosley.signal.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amosley.signal.art.ArtGenerator
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.components.FilledBtn
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.components.Spinner
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Choose a photo, or pick one of the generated designs, as album art or an artist picture. */
@Composable
fun ColumnScope.ArtSheet(c: Ctx, albumKey: String?, artistName: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val album = albumKey?.let { k -> c.lib.albums.firstOrNull { it.key == k } }
    val isArtist = artistName != null
    val title = artistName ?: album?.title ?: return
    val subtitle = if (isArtist) null else album?.artist
    var page by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val hasCustom = if (isArtist) c.repo.artistArtFile(title) != null else album?.let { c.repo.albumArtFile(it.key) } != null

    fun save(jpeg: ByteArray) {
        busy = true
        scope.launch {
            val msg = if (isArtist) c.repo.saveArtistArt(title, jpeg) else c.repo.saveAlbumArt(album!!, jpeg)
            c.toast(msg)
            c.st.sheet = null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val jpeg = withContext(Dispatchers.Default) { ArtGenerator.fromPicked(context, uri)?.let { ArtGenerator.toJpeg(it) } }
            if (jpeg == null) { busy = false; c.toast("Couldn't open that image") } else save(jpeg)
        }
    }

    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(if (isArtist) "Artist picture" else "Album art", style = T.sheetTitle)
            Mono(title, style = T.metaMono, color = C.Faint)
        }
        Mono("Cancel", color = C.Fg, style = T.mono(11.sp, 600), modifier = Modifier.clickable { c.st.sheet = null }.padding(4.dp))
    }
    FilledBtn(if (isArtist) "Choose a photo" else "Choose an image", bg = C.Amber, fg = C.OnAmber, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mono("Or generate one", color = C.Muted, modifier = Modifier.weight(1f))
        Mono("More designs ↻", color = C.AmberText, modifier = Modifier.clickable { page++ }.padding(4.dp))
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        (0 until ArtGenerator.STYLES).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { i ->
                    // "More designs" moves to the next set of variants: same styles, new layouts and colours.
                    val variant = i + page * ArtGenerator.STYLES
                    val preview by produceState<ImageBitmap?>(null, title, variant) {
                        value = withContext(Dispatchers.Default) { ArtGenerator.draw(context, title, subtitle, variant, 360, isArtist).asImageBitmap() }
                    }
                    Column(Modifier.weight(1f).clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            val jpeg = withContext(Dispatchers.Default) { ArtGenerator.toJpeg(ArtGenerator.draw(context, title, subtitle, variant, 1200, isArtist)) }
                            save(jpeg)
                        }
                    }) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(if (isArtist) CircleShape else RectangleShape).background(C.Card).border(1.dp, C.Hair, if (isArtist) CircleShape else RectangleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val img = preview
                            if (img == null) Spinner(Modifier.padding(20.dp)) else Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Mono(ArtGenerator.styleNames[i], style = T.metaMono, color = C.Faint, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
    if (hasCustom) {
        Spacer(Modifier.height(6.dp))
        OutlineBtn(if (isArtist) "Remove picture" else "Remove custom art", color = C.AmberText) {
            if (isArtist) c.repo.removeArtistArt(title) else album?.let { c.repo.removeAlbumArt(it.key) }
            c.toast("Removed")
            c.st.sheet = null
        }
    }
    if (!isArtist && album?.tracks?.any { it.origin == com.amosley.signal.core.Origin.PC } == true) {
        Text("Also saved as cover.jpg in the album's folder on ${c.pcName}, so Plex sees it too.", style = T.ui(12.sp), color = C.Faint, modifier = Modifier.padding(top = 10.dp))
    }
}

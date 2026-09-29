package com.amosley.signal.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amosley.signal.core.Origin
import com.amosley.signal.core.Track
import com.amosley.signal.data.DlState
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T
import kotlin.math.abs

/** Deterministic gradient placeholder art (the design's `art(h1, h2)`), keyed by album/title. */
fun artBrush(key: String, lightness: Float = 0.42f): Brush {
    val h = abs(key.hashCode())
    val h1 = (h % 360).toFloat()
    val h2 = ((h / 360) % 360).toFloat()
    val c1 = Color.hsv(h1, 0.55f, (lightness + 0.2f).coerceAtMost(1f))
    val c2 = Color.hsv(h2, 0.6f, (lightness - 0.1f).coerceAtLeast(0.1f))
    return Brush.linearGradient(listOf(c1, c2), start = Offset.Zero, end = Offset.Infinite)
}

@Composable
fun Art(
    key: String,
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.clip(shape).background(artBrush(key)).drawBehind {
        // 2px / 9px stripe texture
        var x = 0f
        while (x < size.width) {
            drawRect(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Size(2.dp.toPx(), size.height))
            x += 9.dp.toPx()
        }
    }) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        overlay()
    }
}

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color = C.Muted, style: TextStyle = T.label, maxLines: Int = 1) {
    Text(text.uppercase(), modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, bg: Color = C.Badge, fg: Color = C.Fg, border: Color? = null) {
    Box(
        modifier
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border) else Modifier)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) { Text(text.uppercase(), style = T.badge, color = fg, maxLines = 1) }
}

@Composable fun HiResChip() = Chip("Hi-Res", bg = C.AmberTint, fg = C.AmberText)
@Composable fun FormatChip(fmt: String) = Chip(fmt, bg = Color.Transparent, fg = C.Muted, border = C.HairStrong)
@Composable fun QualityBadge(label: String) = Chip(label, bg = C.Badge, fg = C.Fg)

@Composable
fun OutlineBtn(text: String, modifier: Modifier = Modifier, color: Color = C.Fg, border: Color = C.HairStrong, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier
            .height(32.dp)
            .border(1.dp, border)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = T.mono(11.sp, 600, 0.1.sp), color = color, maxLines = 1)
    }
}

@Composable
fun FilledBtn(text: String, modifier: Modifier = Modifier, bg: Color = C.Fg, fg: Color = C.Bg, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = T.ui(14.sp, 600), color = fg, maxLines = 1)
    }
}

@Composable
fun SquareBtn(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 38.dp, bg: Color = Color.Transparent, tint: Color = C.Fg, border: Color? = C.HairStrong, desc: String? = null, onClick: () -> Unit) {
    Box(
        modifier
            .size(size)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** Three animated bars shown over the art / track number of the song that's playing. */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, color: Color = C.Amber, playing: Boolean = true) {
    val tr = rememberInfiniteTransition(label = "bars")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by tr.animateFloat(1f, 0.35f, infiniteRepeatable(tween(950, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by tr.animateFloat(0.5f, 0.9f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Canvas(modifier.size(16.dp, 14.dp)) {
        val w = size.width / 5
        listOf(a, b, c).forEachIndexed { i, v ->
            val hgt = size.height * (if (playing) v else 0.3f)
            drawRect(color, Offset(i * 2 * w, size.height - hgt), Size(w, hgt))
        }
    }
}

/** Source indicator: filled check = downloaded, hollow circle = on PC only, spinner = downloading. */
@Composable
fun SourceDot(origin: Origin, dl: DlState, modifier: Modifier = Modifier) {
    when {
        origin == Origin.PHONE -> Box(modifier.size(12.dp).background(C.Green, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = C.OnGreen, modifier = Modifier.size(9.dp))
        }
        dl is DlState.Done -> Box(modifier.size(12.dp).background(C.Green, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = C.OnGreen, modifier = Modifier.size(9.dp))
        }
        dl is DlState.Running || dl is DlState.Queued -> Spinner(modifier.size(12.dp), (dl as? DlState.Running)?.progress)
        else -> Canvas(modifier.size(12.dp)) { drawCircle(C.Muted, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.2.dp.toPx())) }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, progress: Float? = null, color: Color = C.Amber) {
    val tr = rememberInfiniteTransition(label = "spin")
    val rot by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "r")
    Canvas(modifier) {
        val stroke = Stroke(1.6.dp.toPx())
        drawCircle(color.copy(alpha = 0.25f), radius = size.minDimension / 2 - 1.dp.toPx(), style = stroke)
        val sweep = if (progress != null && progress > 0f) 360f * progress else 90f
        val start = if (progress != null && progress > 0f) -90f else rot
        drawArc(color, start, sweep, false, topLeft = Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), style = stroke)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Mono(text, color = C.Muted)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Mono(trailing, color = C.Faint)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(C.Hair))

@Composable
fun CardBox(modifier: Modifier = Modifier, border: Color = C.HairStrong, bg: Color = C.Card, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().background(bg).border(1.dp, border).padding(14.dp)) { content() }
}

/** Standard song row: art · title · (HI-RES) artist · source · ⋯ */
@Composable
fun SongRow(
    t: Track,
    artModel: Any?,
    dl: DlState,
    isCurrent: Boolean,
    playing: Boolean,
    unavailable: Boolean,
    onPlay: () -> Unit,
    onArtist: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (unavailable) 0.35f else 1f)
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(t.albumKey + t.title, artModel, Modifier.size(52.dp)) {
            if (isCurrent) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { PlayingBars(playing = playing) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = T.row, color = if (isCurrent) C.AmberText else C.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.isHiRes) {
                    HiResChip()
                    Spacer(Modifier.width(6.dp))
                }
                if (t.artist == null) {
                    Text("Unknown artist", style = T.meta, color = C.AmberText, maxLines = 1)
                } else {
                    Text(
                        t.artist, style = T.meta.copy(textDecoration = TextDecoration.Underline), color = C.Muted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable { onArtist(t.artist) },
                    )
                }
                Spacer(Modifier.width(8.dp))
                SourceDot(t.origin, dl)
            }
        }
        if (trailingText != null) {
            Text(trailingText, style = T.metaMono, color = C.Faint)
            Spacer(Modifier.width(4.dp))
        }
        Box(Modifier.size(36.dp).clickable(onClick = onMore), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MoreHoriz, "More", tint = C.Muted)
        }
    }
}

@Composable
fun PlayDisc(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(Color.White.copy(alpha = 0.92f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(40.dp, 22.dp)
            .background(if (on) C.Amber else Color.White.copy(alpha = 0.12f))
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(16.dp).background(if (on) C.OnAmber else C.Fg)) }
}

@Composable
fun SettingRow(title: String, sub: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.row, color = C.Fg)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, style = T.ui(12.5.sp), color = C.Muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

val ContentPad = PaddingValues(horizontal = 20.dp)

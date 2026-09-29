package com.amosley.signal.ui.theme

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import com.amosley.signal.R

object C {
    val Bg = Color(0xFF0B0B0B)
    val Surface = Color(0xFF141414)
    val Canvas = Color(0xFF070707)
    val Fg = Color(0xFFECECEC)
    val Muted = Fg.copy(alpha = 0.55f)
    val Faint = Fg.copy(alpha = 0.42f)
    val Hair = Color.White.copy(alpha = 0.10f)
    val HairStrong = Color.White.copy(alpha = 0.15f)
    val Card = Color.White.copy(alpha = 0.06f)
    val Hover = Color.White.copy(alpha = 0.04f)
    val Amber = Color(0xFFF2A93B)
    val AmberText = Color(0xFFF6B85A)
    val OnAmber = Color(0xFF1A1300)
    val AmberTint = Color(255, 170, 60).copy(alpha = 0.16f)
    val Green = Color(0xFF3BD17A)
    val OnGreen = Color(0xFF006622)
    val Badge = Color.White.copy(alpha = 0.10f)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Grotesk = FontFamily(
    variable(R.font.space_grotesk, 400),
    variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 600),
    variable(R.font.space_grotesk, 700),
)

val Mono = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 500),
    variable(R.font.jetbrains_mono, 600),
)

object T {
    fun ui(size: TextUnit, weight: Int = 400, spacing: TextUnit = 0.sp, color: Color = Color.Unspecified, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = Grotesk, fontSize = size, fontWeight = FontWeight(weight), letterSpacing = spacing, color = color, lineHeight = lineHeight)

    fun mono(size: TextUnit = 11.sp, weight: Int = 500, spacing: TextUnit = 0.12.em, color: Color = Color.Unspecified) =
        TextStyle(fontFamily = Mono, fontSize = size, fontWeight = FontWeight(weight), letterSpacing = spacing, color = color)

    val sectionTitle = ui(36.sp, 500, (-0.045).em, lineHeight = 36.sp)
    val npTitleFolded = ui(30.sp, 500, (-0.04).em, lineHeight = 32.sp)
    val npTitleUnfolded = ui(26.sp, 500, (-0.04).em, lineHeight = 28.sp)
    val videoTitle = ui(28.sp, 700, (-0.03).em, lineHeight = 30.sp)
    val albumTitle = ui(22.sp, 700, (-0.02).em)
    val sheetTitle = ui(18.sp, 600)
    val row = ui(15.sp, 500)
    val rowSecondary = ui(14.5.sp, 600)
    val meta = ui(13.sp, 400)
    val label = mono(11.sp, 500, 0.12.em)
    val metaMono = mono(10.5.sp, 500, 0.07.em)
    val badge = mono(9.5.sp, 600, 0.08.em)
    val lyric = ui(20.sp, 500, (-0.02).em, lineHeight = 24.sp)
    val lyricTr = ui(14.sp, 400, lineHeight = 18.sp)
}

@Composable
fun SignalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Amber, onPrimary = C.OnAmber, background = C.Bg, onBackground = C.Fg,
            surface = C.Surface, onSurface = C.Fg, surfaceVariant = C.Surface, secondary = C.Amber,
        ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides C.Fg,
            LocalTextSelectionColors provides TextSelectionColors(C.Amber, C.Amber.copy(alpha = 0.3f)),
            content = content,
        )
    }
}

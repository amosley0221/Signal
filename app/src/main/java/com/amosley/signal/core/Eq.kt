package com.amosley.signal.core

import kotlinx.serialization.Serializable
import kotlin.math.ln

/** Equalizer settings: a gain in dB for each of [Eq.FREQS]. */
@Serializable
data class EqSettings(
    val enabled: Boolean = false,
    val gains: List<Float> = List(Eq.FREQS.size) { 0f },
)

object Eq {
    /** Band centres in Hz (the usual 10-band graphic EQ). */
    val FREQS = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    const val MAX_DB = 12f

    val PRESETS: List<Pair<String, List<Float>>> = listOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(6f, 5f, 4f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f),
        "Bass Reducer" to listOf(-6f, -5f, -4f, -2f, -0.5f, 0f, 0f, 0f, 0f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2f, 4f, 5f, 6f),
        "Vocal" to listOf(-2f, -2f, -1f, 0f, 2f, 3.5f, 3.5f, 2f, 0f, -1f),
        "Hip-Hop" to listOf(5f, 4.5f, 3f, 1f, -1f, -1f, 0.5f, 1f, 2f, 3f),
        "R&B" to listOf(3f, 5f, 4f, 1f, -1.5f, -1f, 1f, 1.5f, 2.5f, 3f),
        "Pop" to listOf(-1f, 0f, 1.5f, 3f, 3.5f, 2.5f, 1f, 0f, -0.5f, -1f),
        "Rock" to listOf(4f, 3f, 2f, 0.5f, -1f, -0.5f, 1f, 2.5f, 3.5f, 4f),
        "Electronic" to listOf(4.5f, 4f, 1.5f, 0f, -1.5f, 1f, 0.5f, 1.5f, 4f, 4.5f),
        "Jazz" to listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
        "Classical" to listOf(3f, 2.5f, 2f, 1f, -1f, -1f, 0f, 2f, 2.5f, 3f),
        "Acoustic" to listOf(3f, 3f, 2f, 1f, 1.5f, 1f, 2f, 2.5f, 2f, 1.5f),
        "Late Night" to listOf(3f, 2.5f, 1.5f, 0f, -1f, 0f, 1f, 2f, 2f, 1.5f),
    )

    /** The preset these gains match, or null for a custom curve. */
    fun presetOf(gains: List<Float>): String? = PRESETS.firstOrNull { (_, g) -> g.zip(gains).all { (a, b) -> kotlin.math.abs(a - b) < 0.05f } }?.first

    /** Gain at any frequency, following the curve through the band centres (linear on a log-frequency scale). */
    fun gainAt(gains: List<Float>, hz: Float): Float {
        if (gains.isEmpty()) return 0f
        if (hz <= FREQS.first()) return gains.first()
        if (hz >= FREQS.last()) return gains.last()
        val i = FREQS.indexOfLast { it <= hz }.coerceAtMost(FREQS.size - 2)
        val t = ((ln(hz) - ln(FREQS[i])) / (ln(FREQS[i + 1]) - ln(FREQS[i]))).coerceIn(0f, 1f)
        return gains[i] + (gains[i + 1] - gains[i]) * t
    }

    /** Input gain (dB, <= 0) that keeps boosted bands from clipping. */
    fun preamp(gains: List<Float>): Float = -(gains.maxOrNull() ?: 0f).coerceAtLeast(0f)

    /** Upper edge of each band (geometric midpoint to the next centre; the last runs to 20 kHz). */
    fun bandEdges(): List<Float> = FREQS.indices.map { i -> if (i == FREQS.lastIndex) 20000f else kotlin.math.sqrt(FREQS[i] * FREQS[i + 1]) }
}

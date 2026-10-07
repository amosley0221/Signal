package com.amosley.signal.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.amosley.signal.core.Eq
import com.amosley.signal.core.EqSettings

/**
 * Applies the equalizer to the music player's audio session. Uses Android's DynamicsProcessing (a precise 10-band EQ
 * with a pre-gain against clipping) and falls back to the phone's built-in Equalizer effect if that isn't available.
 */
class EqEngine {
    private var session = 0
    private var dp: DynamicsProcessing? = null
    private var eq: Equalizer? = null

    fun apply(sessionId: Int, s: EqSettings) {
        if (sessionId <= 0) return
        if (sessionId != session) { release(); session = sessionId }
        // Nothing to do while off and never turned on.
        if (!s.enabled && dp == null && eq == null) return
        runCatching { applyDynamics(s) }.onFailure {
            Log.w("Signal", "DynamicsProcessing unavailable, using Equalizer", it)
            dp?.release(); dp = null
            runCatching { applyEqualizer(s) }.onFailure { e -> Log.e("Signal", "equalizer failed", e) }
        }
    }

    private fun applyDynamics(s: EqSettings) {
        if (eq != null) throw IllegalStateException("using Equalizer")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) throw UnsupportedOperationException("needs Android 9")
        val n = Eq.FREQS.size
        val d = dp ?: DynamicsProcessing(
            0, session,
            DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, true, n, false, 0, false, 0, false).build(),
        ).also { dp = it }
        val edges = Eq.bandEdges()
        val bands = DynamicsProcessing.Eq(true, true, n)
        for (i in 0 until n) {
            bands.getBand(i).apply { isEnabled = true; cutoffFrequency = edges[i]; gain = s.gains.getOrElse(i) { 0f } }
        }
        d.setPreEqAllChannelsTo(bands)
        d.setInputGainAllChannelsTo(Eq.preamp(s.gains))
        d.enabled = s.enabled
    }

    private fun applyEqualizer(s: EqSettings) {
        val e = eq ?: Equalizer(0, session).also { eq = it }
        val (lo, hi) = e.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
        for (b in 0 until e.numberOfBands) {
            val hz = e.getCenterFreq(b.toShort()) / 1000f
            val mb = (Eq.gainAt(s.gains, hz) * 100).toInt().coerceIn(lo, hi)
            e.setBandLevel(b.toShort(), mb.toShort())
        }
        e.enabled = s.enabled
    }

    fun release() {
        runCatching { dp?.release() }
        runCatching { eq?.release() }
        dp = null; eq = null
    }
}

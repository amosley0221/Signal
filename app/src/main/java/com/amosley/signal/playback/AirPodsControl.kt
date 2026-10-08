package com.amosley.signal.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * AirPods listening mode (Off / Transparency / Adaptive / Noise Cancellation) through LibrePods, which holds
 * the AirPods connection. Signal asks it with LibrePods' own "set mode" broadcast; nothing is sent to Apple.
 */
object AirPodsControl {
    const val LIBREPODS = "me.kavishdevar.librepods"
    private const val SET_MODE = "me.kavishdevar.librepods.SET_ANC_MODE"

    /** LibrePods' numbers for each mode (its NoiseControlMode order + 1), in the order its app shows them. */
    enum class Mode(val value: Int, val label: String) {
        OFF(1, "Off"), TRANSPARENCY(3, "Transparency"), ADAPTIVE(4, "Adaptive"), NOISE_CANCELLATION(2, "Noise Cancellation"),
    }

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(LIBREPODS, 0); true }.getOrDefault(false)

    /** Name of the Bluetooth headphones audio is going to (e.g. "Tone's AirPods Pro"), or null. */
    fun headphones(context: Context): String? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val bt = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET)
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in bt }?.productName?.toString()
    }

    fun setMode(context: Context, mode: Mode) {
        context.sendBroadcast(Intent(SET_MODE).putExtra("mode", mode.value))
    }
}

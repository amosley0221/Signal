package com.amosley.signal.core

/** Keeps the PC's home-network address current when its router hands it a new one. */
object LanUrl {
    private val URL = Regex("^(https?://)(\\d{1,3}(?:\\.\\d{1,3}){3})(:\\d+)?(/.*)?$")

    /**
     * The stored [lanUrl] rewritten to the PC's current address, or null when it needn't change. Only an address on
     * the phone's own Wi-Fi subnet ([phoneIp], /24) is taken, so virtual adapters and Tailscale are never picked.
     */
    fun refreshed(lanUrl: String, pcAddresses: List<String>, phoneIp: String?): String? {
        val m = URL.find(lanUrl) ?: return null
        val host = m.groupValues[2]
        if (phoneIp == null || pcAddresses.isEmpty() || host in pcAddresses) return null
        val subnet = phoneIp.substringBeforeLast('.') + "."
        val fresh = pcAddresses.firstOrNull { it.startsWith(subnet) && it != phoneIp } ?: return null
        return m.groupValues[1] + fresh + m.groupValues[3] + m.groupValues[4]
    }
}

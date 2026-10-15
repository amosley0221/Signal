package com.amosley.signal.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

data class FoundPc(val name: String, val host: String, val port: Int, val version: String?, val plex: Boolean, val id: String?) {
    val baseUrl: String get() = "http://$host:$port"
}

/**
 * Finds Signal Agents on the LAN: via mDNS (`_signal._tcp`), and by asking every address on the phone's Wi-Fi
 * subnet for the agent's /api/info, since many routers and Windows firewalls drop mDNS.
 */
class Discovery(private val context: Context) {
    private val io = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var sweep: kotlinx.coroutines.Job? = null
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _found = MutableStateFlow<List<FoundPc>>(emptyList())
    val found: StateFlow<List<FoundPc>> = _found
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching
    private var listener: NsdManager.DiscoveryListener? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        if (listener != null) return
        _found.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { _searching.value = true }
            override fun onDiscoveryStopped(serviceType: String) { _searching.value = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { _searching.value = false; listener = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {
                _found.value = _found.value.filterNot { it.name == info.serviceName }
            }
            override fun onServiceFound(info: NsdServiceInfo) { resolve(info) }
        }
        listener = l
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { listener = null }
        sweep?.cancel()
        sweep = io.launch { sweepSubnet() }
    }

    private suspend fun sweepSubnet() {
        val me = com.amosley.signal.playback.wifiAddress(context) ?: return
        val prefix = me.substringBeforeLast('.')
        val limit = kotlinx.coroutines.sync.Semaphore(48)
        kotlinx.coroutines.coroutineScope {
            for (i in 1..254) {
                val host = "$prefix.$i"
                if (host == me) continue
                launch {
                    limit.acquire()
                    try { probe(host, DEFAULT_PORT)?.let { add(it) } } finally { limit.release() }
                }
            }
        }
    }

    private fun probe(host: String, port: Int): FoundPc? = runCatching {
        val conn = java.net.URL("http://$host:$port/api/info").openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 500
        conn.readTimeout = 1500
        try {
            if (conn.responseCode != 200) return null
            val info = SignalJson.decodeFromString(AgentInfo.serializer(), conn.inputStream.bufferedReader().readText())
            FoundPc(info.name, host, port, info.version, info.plex, info.id)
        } finally { conn.disconnect() }
    }.getOrNull()

    private fun add(pc: FoundPc) {
        _found.value = (_found.value.filterNot { it.host == pc.host && it.port == pc.port || (pc.id != null && it.id == pc.id) } + pc).sortedBy { it.name }
    }

    fun stop() {
        sweep?.cancel()
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        _searching.value = false
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        val cb = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(r: NsdServiceInfo) {
                val host = if (Build.VERSION.SDK_INT >= 34) {
                    r.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress ?: r.hostAddresses.firstOrNull()?.hostAddress
                } else r.host?.hostAddress
                host ?: return
                val attrs = r.attributes.mapValues { (_, v) -> v?.let { String(it) } }
                val pc = FoundPc(
                    name = attrs["name"] ?: r.serviceName,
                    host = host,
                    port = r.port,
                    version = attrs["version"],
                    plex = attrs["plex"] == "1",
                    id = attrs["id"],
                )
                add(pc)
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            nsd.registerServiceInfoCallback(info, executor, object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    cb.onServiceResolved(serviceInfo)
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() {}
                override fun onServiceInfoCallbackUnregistered() {}
            })
        } else {
            nsd.resolveService(info, cb)
        }
    }

    companion object {
        const val SERVICE_TYPE = "_signal._tcp."
        const val DEFAULT_PORT = 8765
    }
}

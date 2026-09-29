package com.amosley.signal.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.util.concurrent.Executors

data class FoundPc(val name: String, val host: String, val port: Int, val version: String?, val plex: Boolean, val id: String?) {
    val baseUrl: String get() = "http://$host:$port"
}

/** Finds Signal Agents on the LAN via mDNS (`_signal._tcp`). */
class Discovery(context: Context) {
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
    }

    fun stop() {
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
                _found.value = (_found.value.filterNot { it.host == pc.host && it.port == pc.port } + pc).sortedBy { it.name }
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

    companion object { const val SERVICE_TYPE = "_signal._tcp." }
}

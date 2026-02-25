package com.applenotesync.app.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class ServerState {
    data object Searching : ServerState()
    data class Found(val host: String, val port: Int) : ServerState()
    data class Error(val message: String) : ServerState()
}

class ServerDiscovery(context: Context) {

    companion object {
        private const val SERVICE_TYPE = "_applenotesync._tcp."
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _state = MutableStateFlow<ServerState>(ServerState.Searching)
    val state: StateFlow<ServerState> = _state

    private var multicastLock: WifiManager.MulticastLock? = null
    private var isDiscovering = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            _state.value = ServerState.Error("Discovery start failed (code $errorCode)")
            isDiscovering = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            // Ignored
        }

        override fun onDiscoveryStarted(serviceType: String) {
            isDiscovering = true
        }

        override fun onDiscoveryStopped(serviceType: String) {
            isDiscovering = false
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            nsdManager.resolveService(serviceInfo, resolveListener)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            // Could handle service loss here if needed
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            _state.value = ServerState.Error("Resolve failed (code $errorCode)")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host?.hostAddress ?: return
            val port = serviceInfo.port
            _state.value = ServerState.Found(host, port)
        }
    }

    fun startDiscovery() {
        if (isDiscovering) return
        _state.value = ServerState.Searching
        multicastLock = wifiManager.createMulticastLock("applenotesync_mdns").apply {
            setReferenceCounted(true)
            acquire()
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        if (isDiscovering) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: IllegalArgumentException) {
                // Already stopped
            }
            isDiscovering = false
        }
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }
}

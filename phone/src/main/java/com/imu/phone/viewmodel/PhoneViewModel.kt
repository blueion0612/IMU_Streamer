package com.imu.phone.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.imu.phone.DataSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.LocalDateTime

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PhoneViewModel"
    }

    private val _application = application
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    // WiFi state
    private val _wifiConnected = MutableStateFlow(false)
    val wifiConnected = _wifiConnected.asStateFlow()
    private val _wifiSsid = MutableStateFlow("Not connected")
    val wifiSsid = _wifiSsid.asStateFlow()
    private val _wifiLinkSpeed = MutableStateFlow(0)
    val wifiLinkSpeed = _wifiLinkSpeed.asStateFlow()
    private val _wifiRssi = MutableStateFlow(0)
    val wifiRssi = _wifiRssi.asStateFlow()

    // UI elements
    private var _connectedNodeId: String = "none"
    private val _connectedNodeDisplayName = MutableStateFlow("No device")
    val nodeName = _connectedNodeDisplayName.asStateFlow()
    private val _pingSuccess = MutableStateFlow(false)
    val appActive = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    // Callback for node ID changes
    private var _onNodeIdChanged: ((String) -> Unit)? = null

    fun setOnNodeIdChangedListener(listener: (String) -> Unit) {
        _onNodeIdChanged = listener
        // Immediately notify with current value if already connected
        if (_connectedNodeId != "none") {
            listener(_connectedNodeId)
        }
    }

    // IMU State Flows
    private val _imuStreamState = MutableStateFlow(false)
    val imuStreamState = _imuStreamState.asStateFlow()

    private val _imuInHz = MutableStateFlow(0.0F)
    val imuInHz = _imuInHz.asStateFlow()

    private val _imuOutHz = MutableStateFlow(0.0F)
    val imuOutHz = _imuOutHz.asStateFlow()

    private val _imuQueueSize = MutableStateFlow(0)
    val imuQueueSize = _imuQueueSize.asStateFlow()

    fun getConnectedNodeId(): String {
        return _connectedNodeId
    }

    fun onServiceUpdate(intent: Intent) {
        when (intent.getStringExtra(DataSingleton.BROADCAST_SERVICE_KEY)) {
            DataSingleton.IMU_PATH -> {
                _imuStreamState.value = intent.getBooleanExtra(
                    DataSingleton.BROADCAST_SERVICE_STATE, false
                )
                _imuInHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_IN, 0.0F
                )
                _imuOutHz.value = intent.getFloatExtra(
                    DataSingleton.BROADCAST_SERVICE_HZ_OUT, 0.0F
                )
                _imuQueueSize.value = intent.getIntExtra(
                    DataSingleton.BROADCAST_SERVICE_QUEUE, 0
                )
            }
        }
    }

    fun regularUiUpdates() {
        _scope.launch {
            while (true) {
                requestPing()
                updateWifiStatus()
                delay(2000L)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun updateWifiStatus() {
        try {
            val connectivityManager = _application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiManager = _application.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                _wifiConnected.value = true
                val wifiInfo = wifiManager.connectionInfo
                _wifiSsid.value = wifiInfo.ssid?.replace("\"", "") ?: "Unknown"
                _wifiLinkSpeed.value = wifiInfo.linkSpeed  // Mbps
                _wifiRssi.value = wifiInfo.rssi  // dBm
            } else {
                _wifiConnected.value = false
                _wifiSsid.value = "Not connected"
                _wifiLinkSpeed.value = 0
                _wifiRssi.value = 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get WiFi status: ${e.message}")
        }
    }

    fun queryCapabilities() {
        _scope.launch {
            try {
                val task = _capabilityClient.getAllCapabilities(CapabilityClient.FILTER_REACHABLE)
                val res = Tasks.await(task)
                for ((_, v) in res.iterator()) {
                    onCapabilityChanged(v)
                }
            } catch (exception: Exception) {
                Log.d(TAG, "Querying nodes failed: $exception")
            }
        }
    }

    fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val deviceCap = DataSingleton.WATCH_CAPABILITY
        when (capabilityInfo.name) {
            deviceCap -> {
                val nodes = capabilityInfo.nodes
                if (nodes.count() > 1) {
                    throw Exception("More than one node with $deviceCap detected: $nodes")
                } else if (nodes.isEmpty()) {
                    _connectedNodeDisplayName.value = "No device"
                    _connectedNodeId = "none"
                } else {
                    _connectedNodeDisplayName.value = nodes.first().displayName
                    _connectedNodeId = nodes.first().id
                    // Notify listener about new node ID
                    _onNodeIdChanged?.invoke(_connectedNodeId)
                    Log.d(TAG, "Node ID changed, notifying listener: $_connectedNodeId")
                }
                Log.d(TAG, "Connected watch: $nodes")
            }
        }
        requestPing()
    }

    private fun requestPing() {
        _scope.launch {
            try {
                _messageClient.sendMessage(_connectedNodeId, DataSingleton.PING_REQ, null).await()
                delay(1000L)
                if (Duration.between(_lastPing, LocalDateTime.now()).toMillis() > 1100L) {
                    _pingSuccess.value = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ping message to nodeID: $_connectedNodeId failed")
            }
        }
    }

    fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Received from: ${messageEvent.sourceNodeId} with path ${messageEvent.path}")
        when (messageEvent.path) {
            DataSingleton.PING_REP -> {
                _pingSuccess.value = true
                _lastPing = LocalDateTime.now()
            }

            DataSingleton.PING_REQ -> {
                _scope.launch {
                    _messageClient.sendMessage(_connectedNodeId, DataSingleton.PING_REP, null).await()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _scope.cancel()
        Log.d(TAG, "Cleared")
    }
}

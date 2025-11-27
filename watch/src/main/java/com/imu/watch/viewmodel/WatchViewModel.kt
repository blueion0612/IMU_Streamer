package com.imu.watch.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.imu.watch.DataSingleton
import com.imu.watch.ImuStreamState
import com.imu.watch.service.ImuService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime

class WatchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "WatchViewModel"
    }

    private val _application = application
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    // Vibrator for haptic feedback
    private val _vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _connectedNodeDisplayName = MutableStateFlow("No Device")
    val nodeName = _connectedNodeDisplayName.asStateFlow()
    var connectedNodeId: String = "none"

    private val _pingSuccess = MutableStateFlow(false)
    val pingSuccessState = _pingSuccess.asStateFlow()
    private var _lastPing = LocalDateTime.now()

    private val _imuStreamState = MutableStateFlow(ImuStreamState.Idle)
    val sensorStreamState = _imuStreamState.asStateFlow()

    fun endImu() {
        _imuStreamState.value = ImuStreamState.Idle
        Intent(_application.applicationContext, ImuService::class.java).also { intent ->
            _application.stopService(intent)
        }
    }

    fun resetAllStreamStates() {
        endImu()
    }

    fun onServiceClose(serviceKey: String?) {
        when (serviceKey) {
            DataSingleton.IMU_PATH -> endImu()
        }
    }

    fun onChannelClose(c: ChannelClient.Channel) {
        Log.v(TAG, "channel close ${c.path}")
        when (c.path) {
            DataSingleton.IMU_PATH -> endImu()
        }
    }

    fun imuStreamTrigger(start: Boolean) {
        if (!start) {
            Intent(_application.applicationContext, ImuService::class.java).also { intent ->
                _application.stopService(intent)
            }
        } else {
            startImuStream()
        }
    }

    private fun startImuStream() {
        val intent = Intent(_application.applicationContext, ImuService::class.java)
        intent.putExtra("sourceNodeId", connectedNodeId)
        _application.startService(intent)
        _imuStreamState.value = ImuStreamState.Streaming
    }

    /**
     * Execute haptic feedback
     * @param intensity vibration intensity (0-255)
     * @param count number of vibrations
     * @param duration duration of each vibration in ms
     */
    private fun executeHaptic(intensity: Int, count: Int, duration: Int) {
        _scope.launch(Dispatchers.Main) {
            Log.d(TAG, "Executing haptic: intensity=$intensity, count=$count, duration=$duration")

            val amplitude = intensity.coerceIn(1, 255)

            for (i in 0 until count) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(duration.toLong(), amplitude)
                    _vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    _vibrator.vibrate(duration.toLong())
                }

                if (i < count - 1) {
                    delay(duration.toLong() + 100)  // Gap between vibrations
                }
            }

            DataSingleton.incrementHapticCount()
        }
    }

    fun regularConnectionCheck() {
        _scope.launch {
            while (true) {
                requestPing()
                delay(2500L)
            }
        }
    }

    private fun requestPing() {
        _scope.launch {
            try {
                _messageClient.sendMessage(connectedNodeId, DataSingleton.PING_REQ, null).await()
                delay(1000L)
                if (Duration.between(_lastPing, LocalDateTime.now()).toMillis() > 1100L) {
                    _pingSuccess.value = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ping message to nodeID: $connectedNodeId failed")
            }
        }
    }

    fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            DataSingleton.PING_REP -> {
                _pingSuccess.value = true
                _lastPing = LocalDateTime.now()
            }

            DataSingleton.PING_REQ -> {
                _scope.launch {
                    _messageClient.sendMessage(connectedNodeId, DataSingleton.PING_REP, null).await()
                }
            }

            DataSingleton.HAPTIC_PATH -> {
                Log.d(TAG, "HAPTIC message received! Data size: ${messageEvent.data?.size ?: 0}")
                val data = messageEvent.data
                if (data == null || data.size < 12) {
                    Log.e(TAG, "Invalid haptic data: null or too small")
                    return
                }
                // Parse haptic command (Big Endian - Java default)
                val buffer = ByteBuffer.wrap(data)
                val intensity = buffer.int
                val count = buffer.int
                val duration = buffer.int
                Log.d(TAG, "Parsed haptic: intensity=$intensity, count=$count, duration=$duration")
                executeHaptic(intensity, count, duration)
            }
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
        val deviceCap = DataSingleton.PHONE_CAPABILITY
        when (capabilityInfo.name) {
            deviceCap -> {
                val nodes = capabilityInfo.nodes
                if (nodes.count() > 1) {
                    throw Exception("More than one node with $deviceCap detected: $nodes")
                } else if (nodes.isEmpty()) {
                    _connectedNodeDisplayName.value = "No device"
                    connectedNodeId = "none"
                } else {
                    _connectedNodeDisplayName.value = nodes.first().displayName
                    connectedNodeId = nodes.first().id
                }
                Log.d(TAG, "Connected phone: $nodes")
            }
        }
        requestPing()
    }

    override fun onCleared() {
        super.onCleared()
        _scope.cancel()
        resetAllStreamStates()
        Log.d(TAG, "Cleared")
    }
}

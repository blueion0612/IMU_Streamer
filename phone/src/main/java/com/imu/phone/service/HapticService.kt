package com.imu.phone.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.imu.phone.DataSingleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Service that listens for haptic commands from UDP and forwards them to the watch
 *
 * Haptic Command Format (12 bytes):
 * - intensity: Int (0-255, vibration strength)
 * - count: Int (number of vibrations)
 * - duration: Int (duration of each vibration in ms)
 */
class HapticService : Service() {

    companion object {
        private const val TAG = "Haptic Service"
    }

    private val _messageClient by lazy { Wearable.getMessageClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private var _isRunning = false
    private var _connectedNodeId: String = "none"

    override fun onCreate() {
        Log.v(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nodeId = intent?.getStringExtra("nodeId") ?: "none"
        if (nodeId != "none") {
            _connectedNodeId = nodeId
            Log.d(TAG, "Node ID updated: $_connectedNodeId")
        }

        if (!_isRunning) {
            _isRunning = true
            _scope.launch { listenForHapticCommands() }
            Log.d(TAG, "Haptic listener started")
        }
        Log.v(TAG, "Service started for node: $_connectedNodeId")
        return START_NOT_STICKY
    }

    private suspend fun listenForHapticCommands() {
        try {
            val udpSocket = DatagramSocket(DataSingleton.HAPTIC_PORT)
            udpSocket.soTimeout = 0  // No timeout, blocking receive
            Log.v(TAG, "Listening for haptic commands on port ${DataSingleton.HAPTIC_PORT}")

            val buffer = ByteArray(DataSingleton.HAPTIC_CMD_SIZE)
            val packet = DatagramPacket(buffer, buffer.size)

            while (_isRunning) {
                try {
                    udpSocket.receive(packet)
                    val data = packet.data

                    // Parse haptic command (Little Endian from Python)
                    val byteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val intensity = byteBuffer.int
                    val count = byteBuffer.int
                    val duration = byteBuffer.int

                    Log.d(TAG, "Received haptic: intensity=$intensity, count=$count, duration=$duration")

                    // Forward to watch
                    sendHapticToWatch(intensity, count, duration)
                    DataSingleton.incrementHapticCount()
                } catch (e: Exception) {
                    if (_isRunning) {
                        Log.w(TAG, "Error receiving haptic command: ${e.message}")
                    }
                }
            }
            udpSocket.close()
        } catch (e: Exception) {
            Log.w(TAG, "Haptic service error: ${e.message}")
        }
    }

    private suspend fun sendHapticToWatch(intensity: Int, count: Int, duration: Int) {
        if (_connectedNodeId == "none") {
            Log.w(TAG, "Cannot send haptic: No watch connected")
            return
        }

        try {
            // Use Big Endian (default) for Bluetooth message to Watch
            val buffer = ByteBuffer.allocate(DataSingleton.HAPTIC_CMD_SIZE)
            buffer.putInt(intensity)
            buffer.putInt(count)
            buffer.putInt(duration)

            Log.d(TAG, "Sending haptic to watch node: $_connectedNodeId")
            _messageClient.sendMessage(
                _connectedNodeId,
                DataSingleton.HAPTIC_PATH,
                buffer.array()
            ).await()

            Log.d(TAG, "Haptic command sent to watch successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send haptic to watch: ${e.message}")
            e.printStackTrace()
        }
    }

    fun updateNodeId(nodeId: String) {
        _connectedNodeId = nodeId
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning = false
        _scope.cancel()
        Log.v(TAG, "Haptic Service destroyed")
    }
}

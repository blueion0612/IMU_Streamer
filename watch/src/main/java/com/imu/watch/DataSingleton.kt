package com.imu.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ImuStreamState {
    Idle,
    Streaming
}

object DataSingleton {
    const val VERSION = "1.1.0"

    // communication paths
    const val IMU_PATH = "/imu"
    const val HAPTIC_PATH = "/haptic"
    const val PING_REQ = "/ping_request"
    const val PING_REP = "/ping_reply"
    const val BROADCAST_CLOSE = "imu.broadcast.close"
    const val BROADCAST_SERVICE_KEY = "service.id"

    // capabilities
    const val PHONE_APP_ACTIVE = "phone_app"
    const val WATCH_APP_ACTIVE = "watch_app"
    const val PHONE_CAPABILITY = "phone"

    // streaming parameters
    // Watch message: dT(1) + timestamp(4) + lacc(3) + gyro(3) + rotvec(4) = 15 floats
    const val IMU_MSG_SIZE = 15 * 4  // 60 bytes

    // Haptic command: intensity(1) + count(1) + duration(1) = 3 ints = 12 bytes
    const val HAPTIC_CMD_SIZE = 3 * 4  // 12 bytes

    // Haptic count state
    private val hapticCountSF = MutableStateFlow(0)
    val hapticCount = hapticCountSF.asStateFlow()
    fun incrementHapticCount() {
        hapticCountSF.value += 1
    }
}

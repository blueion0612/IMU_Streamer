package com.imu.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataSingleton {
    const val VERSION = "1.1.0"

    // message paths
    const val IMU_PATH = "/imu"
    const val HAPTIC_PATH = "/haptic"
    const val PING_REQ = "/ping_request"
    const val PING_REP = "/ping_reply"

    // UI broadcasts
    const val BROADCAST_UPDATE = "imu.broadcast.update"
    const val BROADCAST_SERVICE_KEY = "service.id"
    const val BROADCAST_SERVICE_STATE = "service.state"
    const val BROADCAST_SERVICE_HZ_IN = "service.hz.in"
    const val BROADCAST_SERVICE_HZ_OUT = "service.hz.out"
    const val BROADCAST_SERVICE_QUEUE = "service.queue"

    // capabilities
    const val PHONE_APP_ACTIVE = "phone_app"
    const val WATCH_CAPABILITY = "watch"

    // streaming parameters
    // Watch message: dT(1) + timestamp(4) + lacc(3) + gyro(3) + rotvec(4) = 15 floats
    const val WATCH_IMU_MSG_SIZE = 15 * 4  // 60 bytes

    // Combined message: watch(15) + phone(15) = 30 floats
    const val DUAL_IMU_MSG_SIZE = 30 * 4  // 120 bytes

    // Haptic command: intensity(1) + count(1) + duration(1) = 3 ints = 12 bytes
    const val HAPTIC_CMD_SIZE = 3 * 4  // 12 bytes

    const val IMU_PORT = 65000
    const val HAPTIC_PORT = 65010  // Port for receiving haptic commands

    // shared preferences lookup
    const val IP_KEY = "com.imu.phone.ip"
    const val IP_DEFAULT = "192.168.1.138"
    const val PORT_KEY = "com.imu.phone.port"
    const val IMU_PORT_DEFAULT = IMU_PORT

    // as state flow to update UI elements when IP changes
    private val ipStateFlow = MutableStateFlow(IP_DEFAULT)
    val ip = ipStateFlow.asStateFlow()
    fun setIp(st: String) {
        ipStateFlow.value = st
    }

    // as state flow to update UI elements when port changes
    private val portStateFlow = MutableStateFlow(IMU_PORT_DEFAULT)
    val imuPort = portStateFlow.asStateFlow()
    fun setImuPort(p: Int) {
        portStateFlow.value = p
    }

    // Haptic state
    private val hapticCountSF = MutableStateFlow(0)
    val hapticCount = hapticCountSF.asStateFlow()
    fun incrementHapticCount() {
        hapticCountSF.value += 1
    }
    fun resetHapticCount() {
        hapticCountSF.value = 0
    }
}

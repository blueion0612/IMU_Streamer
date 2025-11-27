package com.imu.watch.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.Wearable
import com.imu.watch.DataSingleton
import com.imu.watch.modules.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.lang.Float.min
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime

class ImuService : Service() {

    companion object {
        private const val TAG = "IMU Service"
        private const val NS2S = 1.0f / 1000000000.0f
        const val MSGBREAK = 4L
    }

    private lateinit var _sensorManager: SensorManager
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)

    private var _lastMsg: LocalDateTime? = null
    private var _imuStreamState = false

    // Linear acceleration
    private var _lacc: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var _tsLacc: Long = 0
    private var _tsDLacc: Float = 0f

    // Gyroscope
    private var _gyro: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var _tsGyro: Long = 0
    private var _tsDGyro: Float = 0f

    // Rotation vector as [w,x,y,z] quaternion
    private var _rotvec: FloatArray = floatArrayOf(1f, 0f, 0f, 0f)

    private val _listeners = listOf(
        SensorListener(
            Sensor.TYPE_LINEAR_ACCELERATION
        ) { onLaccReadout(it) },
        SensorListener(
            Sensor.TYPE_ROTATION_VECTOR
        ) { onRotVecReadout(it) },
        SensorListener(
            Sensor.TYPE_GYROSCOPE
        ) { onGyroReadout(it) }
    )

    override fun onCreate() {
        _sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received start: $intent")
        val sourceId = intent?.extras?.getString("sourceNodeId")
        if (sourceId == null) {
            Log.w(TAG, "no Node ID given")
            onDestroy()
        } else if (_imuStreamState) {
            Log.w(TAG, "stream already started")
            onDestroy()
        } else {
            _imuStreamState = true
            _scope.launch { susStreamTrigger(sourceId) }
        }
        return START_NOT_STICKY
    }

    private suspend fun susStreamTrigger(nodeId: String) {
        try {
            val channel = _channelClient.openChannel(
                nodeId,
                DataSingleton.IMU_PATH
            ).await()
            Log.d(TAG, "Opened ${DataSingleton.IMU_PATH} to $nodeId")

            try {
                val outputStream = _channelClient.getOutputStream(channel).await()
                outputStream.use {
                    registerSensorListeners()

                    while (_imuStreamState) {
                        val lastDat = composeImuMessage()
                        if (lastDat != null) {
                            val buffer = ByteBuffer.allocate(DataSingleton.IMU_MSG_SIZE)
                            for (v in lastDat) buffer.putFloat(v)
                            outputStream.write(buffer.array())
                        }
                        delay(MSGBREAK)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, e)
            } finally {
                _channelClient.close(channel)
                stopService()
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
            stopService()
        }
    }

    private fun registerSensorListeners() {
        if (!this::_sensorManager.isInitialized) {
            throw Exception("Sensor manager is not initialized")
        }
        for (l in _listeners) {
            if (_sensorManager.getDefaultSensor(l.code) != null) {
                _sensorManager.registerListener(
                    l,
                    _sensorManager.getDefaultSensor(l.code),
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            } else {
                throw Exception("Sensor code ${l.code} is not present on this device")
            }
        }
    }

    private fun stopService() {
        _lastMsg = null
        _imuStreamState = false
        if (this::_sensorManager.isInitialized) {
            for (l in _listeners) {
                _sensorManager.unregisterListener(l)
            }
        }
        Log.d(TAG, "Service finished")
    }

    /**
     * Compose watch IMU message:
     * [0] dT - delta time
     * [1-4] timestamp (hour, minute, second, nanosecond)
     * [5-7] linear acceleration (x, y, z)
     * [8-10] gyroscope (x, y, z)
     * [11-14] rotation vector quaternion (w, x, y, z)
     */
    private fun composeImuMessage(): FloatArray? {
        if ((_tsDLacc == 0f) || (_tsDGyro == 0f) || _rotvec.contentEquals(floatArrayOf(1f, 0f, 0f, 0f))) {
            return null
        }

        val tsNow = LocalDateTime.now()
        val ts = floatArrayOf(
            tsNow.hour.toFloat(),
            tsNow.minute.toFloat(),
            tsNow.second.toFloat(),
            tsNow.nano.toFloat()
        )

        var dT = 1.0F
        if (_lastMsg == null) {
            _lastMsg = tsNow
        } else {
            dT = min(Duration.between(_lastMsg, tsNow).toNanos() * NS2S, dT)
            _lastMsg = tsNow
        }

        // average gyro velocities
        val avgGyro = floatArrayOf(
            _gyro[0] / _tsDGyro,
            _gyro[1] / _tsDGyro,
            _gyro[2] / _tsDGyro
        )

        // average accelerations
        val avgLacc = floatArrayOf(
            _lacc[0] / _tsDLacc,
            _lacc[1] / _tsDLacc,
            _lacc[2] / _tsDLacc
        )

        val message = floatArrayOf(dT) +  // [0]
                ts +                       // [1,2,3,4]
                avgLacc +                  // [5,6,7]
                avgGyro +                  // [8,9,10]
                _rotvec                    // [11,12,13,14]

        // reset deltas
        _lacc = floatArrayOf(0f, 0f, 0f)
        _tsDLacc = 0f
        _gyro = floatArrayOf(0f, 0f, 0f)
        _tsDGyro = 0f
        _rotvec = floatArrayOf(1f, 0f, 0f, 0f)

        return message
    }

    fun onLaccReadout(newReadout: SensorEvent) {
        if (_tsLacc != 0L) {
            val dT: Float = (newReadout.timestamp - _tsLacc) * NS2S
            if (dT > 1f) {
                _lacc = floatArrayOf(newReadout.values[0], newReadout.values[1], newReadout.values[2])
                _tsDLacc = 1f
            } else {
                _lacc[0] += newReadout.values[0] * dT
                _lacc[1] += newReadout.values[1] * dT
                _lacc[2] += newReadout.values[2] * dT
                _tsDLacc += dT
            }
        }
        _tsLacc = newReadout.timestamp
    }

    fun onGyroReadout(newReadout: SensorEvent) {
        if (_tsGyro != 0L) {
            val dT: Float = (newReadout.timestamp - _tsGyro) * NS2S
            if (dT > 1f) {
                _gyro = floatArrayOf(newReadout.values[0], newReadout.values[1], newReadout.values[2])
                _tsDGyro = 1f
            } else {
                _gyro[0] += newReadout.values[0] * dT
                _gyro[1] += newReadout.values[1] * dT
                _gyro[2] += newReadout.values[2] * dT
                _tsDGyro += dT
            }
        }
        _tsGyro = newReadout.timestamp
    }

    fun onRotVecReadout(newReadout: SensorEvent) {
        // newReadout is [x,y,z,w]
        // our preferred order is [w,x,y,z]
        _rotvec = floatArrayOf(
            newReadout.values[3],
            newReadout.values[0],
            newReadout.values[1],
            newReadout.values[2]
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService()
        val intent = Intent(DataSingleton.BROADCAST_CLOSE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.IMU_PATH)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        _scope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}

package com.imu.phone.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.imu.phone.DataSingleton
import com.imu.phone.modules.PhoneChannelCallback
import com.imu.phone.modules.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Float.min
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.round

class ImuService : Service() {

    companion object {
        private const val TAG = "IMU Service"
        private const val NS2S = 1.0f / 1000000000.0f
        private const val MS2S = 0.001f
        private const val MSGBREAK = 4L
    }

    private val _channelCallback = PhoneChannelCallback(
        openCallback = { onChannelOpen(it) },
        closeCallback = { onChannelClose(it) }
    )

    private lateinit var _sensorManager: SensorManager
    private val _channelClient by lazy { Wearable.getChannelClient(application) }
    private val _scope = CoroutineScope(Job() + Dispatchers.IO)
    private var _lastBroadcast = LocalDateTime.now()

    private var _lastMsg: LocalDateTime? = null

    // service state indicators
    private var _imuStreamState = false
    private var _swInCount: Int = 0
    private var _swOutCount: Int = 0
    private var _swQueue = ConcurrentLinkedQueue<ByteArray>()

    // Sensor values for phone
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

    // store listeners
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
        Log.v(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _channelClient.registerChannelCallback(_channelCallback)
        _scope.launch {
            while (true) {
                broadcastUiUpdate()
                delay(2000L)
            }
        }
        Log.v(TAG, "Service started")
        return START_NOT_STICKY
    }

    private fun broadcastUiUpdate() {
        val now = LocalDateTime.now()
        val diff = Duration.between(_lastBroadcast, now)
        val ds = diff.toMillis() * MS2S
        _lastBroadcast = LocalDateTime.now()

        val intent = Intent(DataSingleton.BROADCAST_UPDATE)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_KEY, DataSingleton.IMU_PATH)
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_STATE, _imuStreamState)

        if (ds <= 0F) {
            intent.putExtra(DataSingleton.BROADCAST_SERVICE_HZ_IN, 0F)
            intent.putExtra(DataSingleton.BROADCAST_SERVICE_HZ_OUT, 0F)
        } else {
            intent.putExtra(DataSingleton.BROADCAST_SERVICE_HZ_IN, round(_swInCount.toFloat() / ds))
            intent.putExtra(DataSingleton.BROADCAST_SERVICE_HZ_OUT, round(_swOutCount.toFloat() / ds))
        }
        intent.putExtra(DataSingleton.BROADCAST_SERVICE_QUEUE, _swQueue.count())
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        _swInCount = 0
        _swOutCount = 0
    }

    private suspend fun sendUdpImuMessages(c: ChannelClient.Channel) {
        try {
            val port = DataSingleton.imuPort.value
            val ip = DataSingleton.ip.value

            withContext(Dispatchers.IO) {
                val udpSocket = DatagramSocket(port)
                udpSocket.broadcast = true
                val socketInetAddress = InetAddress.getByName(ip)
                Log.v(TAG, "Opened UDP socket to $ip:$port")

                udpSocket.use {
                    // register all sensor listeners
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

                    while (_imuStreamState) {
                        // Get data from smartwatch message queue
                        var swData = pollEntireQueue()
                        while (swData == null && _imuStreamState) {
                            delay(1L)
                            swData = pollEntireQueue()
                        }

                        // Get the newest phone IMU reading
                        var phoneData = composeImuMessage()
                        while (phoneData == null && _imuStreamState) {
                            delay(1L)
                            phoneData = composeImuMessage()
                        }

                        if (phoneData != null && swData != null) {
                            // Write watch and phone data to buffer
                            val buffer = ByteBuffer.allocate(DataSingleton.DUAL_IMU_MSG_SIZE)
                            // put smartwatch data (15 floats)
                            for (s in swData) {
                                buffer.putFloat(s)
                            }
                            // append phone data (15 floats)
                            for (v in phoneData) {
                                buffer.putFloat(v)
                            }
                            // create packet and send
                            val dp = DatagramPacket(
                                buffer.array(), buffer.capacity(), socketInetAddress, port
                            )
                            udpSocket.send(dp)
                            _swOutCount += 1
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
            _channelClient.close(c)
            Log.d(TAG, "IMU UDP messages stopped")
        }
    }

    /**
     * Poll entire queue and summarize acceleration and gyro data
     */
    private fun pollEntireQueue(): FloatArray? {
        var lastRow = _swQueue.poll()

        if (lastRow == null) {
            return null
        } else {
            var totalT = 0F
            var totalAcc = floatArrayOf(0F, 0F, 0F)
            var totalGyr = floatArrayOf(0F, 0F, 0F)

            var rowBuf = ByteBuffer.wrap(lastRow)
            while (lastRow != null) {
                val dT = rowBuf.getFloat(0)
                totalT += dT

                // sum gyro speed * delta T (indices 8,9,10 = floats at byte positions 32,36,40)
                totalGyr = floatArrayOf(
                    totalGyr[0] + rowBuf.getFloat(8 * 4) * dT,
                    totalGyr[1] + rowBuf.getFloat(9 * 4) * dT,
                    totalGyr[2] + rowBuf.getFloat(10 * 4) * dT
                )

                // sum acc * delta T (indices 5,6,7 = floats at byte positions 20,24,28)
                totalAcc = floatArrayOf(
                    totalAcc[0] + rowBuf.getFloat(5 * 4) * dT,
                    totalAcc[1] + rowBuf.getFloat(6 * 4) * dT,
                    totalAcc[2] + rowBuf.getFloat(7 * 4) * dT
                )

                lastRow = _swQueue.poll()
                if (lastRow != null) {
                    rowBuf = ByteBuffer.wrap(lastRow)
                }
            }

            // return as float array
            val floats = FloatArray(rowBuf.limit() / 4)
            rowBuf.asFloatBuffer().get(floats)

            // divide by total T to get average
            floats[0] = totalT
            floats[5] = totalAcc[0] / totalT
            floats[6] = totalAcc[1] / totalT
            floats[7] = totalAcc[2] / totalT
            floats[8] = totalGyr[0] / totalT
            floats[9] = totalGyr[1] / totalT
            floats[10] = totalGyr[2] / totalT

            return floats
        }
    }

    private suspend fun swQueueFiller(c: ChannelClient.Channel) {
        try {
            val streamTask = _channelClient.getInputStream(c)
            val stream = Tasks.await(streamTask)
            stream.use {
                while (_imuStreamState) {
                    if (stream.available() > 0) {
                        val buffer = ByteBuffer.allocate(DataSingleton.WATCH_IMU_MSG_SIZE).array()
                        stream.read(buffer)
                        _swQueue.add(buffer)
                        _swInCount += 1
                    }
                    delay(MSGBREAK)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, e)
        } finally {
            _channelClient.close(c)
            Log.d(TAG, "IMU queue filler stopped")
        }
    }

    private fun onChannelOpen(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.IMU_PATH) {
            _imuStreamState = true
            _scope.launch { swQueueFiller(c) }
            _scope.launch { sendUdpImuMessages(c) }
            broadcastUiUpdate()
        }
    }

    private fun onChannelClose(c: ChannelClient.Channel) {
        if (c.path == DataSingleton.IMU_PATH) {
            _imuStreamState = false
            _lastMsg = null
            _swQueue.clear()
            if (this::_sensorManager.isInitialized) {
                for (l in _listeners) {
                    _sensorManager.unregisterListener(l)
                }
            }
            broadcastUiUpdate()
        }
    }

    /**
     * Compose phone IMU message:
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

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        _imuStreamState = false
        _scope.cancel()
        for (l in _listeners) {
            _sensorManager.unregisterListener(l)
        }
        _channelClient.unregisterChannelCallback(_channelCallback)
        Log.v(TAG, "IMU Service destroyed")
    }
}

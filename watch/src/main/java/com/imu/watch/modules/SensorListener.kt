package com.imu.watch.modules

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

/**
 * A simple class to register a listener for sensors.
 */
class SensorListener(val code: Int, val onReadout: (SensorEvent) -> Unit) : SensorEventListener {
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        onReadout(event)
    }
}

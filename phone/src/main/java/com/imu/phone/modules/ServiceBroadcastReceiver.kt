package com.imu.phone.modules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.imu.phone.DataSingleton

class ServiceBroadcastReceiver(
    onServiceUpdate: (Intent) -> Unit
) : BroadcastReceiver() {
    companion object {
        private const val TAG = "ServiceBroadcastReceiver"
    }

    private val _onServiceUpdate = onServiceUpdate

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "received ${intent.action}")
        when (intent.action) {
            DataSingleton.BROADCAST_UPDATE -> {
                _onServiceUpdate(intent)
            }
        }
    }
}

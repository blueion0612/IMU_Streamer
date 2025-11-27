package com.imu.watch.modules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.imu.watch.DataSingleton

class ServiceBroadcastReceiver(
    onServiceClose: (String?) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceBroadcastReceiver"
    }

    private val _onServiceClose = onServiceClose

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "received ${intent.action}")
        val serviceKey = intent.extras?.getString(DataSingleton.BROADCAST_SERVICE_KEY)
        when (intent.action) {
            DataSingleton.BROADCAST_CLOSE -> _onServiceClose(serviceKey)
        }
    }
}

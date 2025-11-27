package com.imu.watch.modules

import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.imu.watch.DataSingleton

/**
 * Channel callback forwarder registered with the channel client.
 */
class WatchChannelCallback(
    closeCallback: (ChannelClient.Channel) -> Unit
) : ChannelClient.ChannelCallback() {

    private val _closeCallback = closeCallback

    companion object {
        private const val TAG = "WatchChannelCallback"
    }

    override fun onChannelClosed(
        channel: ChannelClient.Channel,
        closeReason: Int,
        appSpecificErrorCode: Int
    ) {
        if (channel.path == DataSingleton.IMU_PATH) {
            Log.d(TAG, "Channel closed ${channel.nodeId}")
            _closeCallback(channel)
        }
    }
}

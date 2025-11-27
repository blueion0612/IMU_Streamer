package com.imu.phone.modules

import android.util.Log
import com.google.android.gms.wearable.ChannelClient

/**
 * Channel callback forwarder registered with the channel client.
 */
class PhoneChannelCallback(
    openCallback: (ChannelClient.Channel) -> Unit,
    closeCallback: (ChannelClient.Channel) -> Unit
) : ChannelClient.ChannelCallback() {

    private val _openCallback = openCallback
    private val _closeCallback = closeCallback

    companion object {
        private const val TAG = "ChannelCallback"
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        Log.d(TAG, "${channel.path} opened by ${channel.nodeId}")
        _openCallback(channel)
    }

    override fun onChannelClosed(
        channel: ChannelClient.Channel,
        closeReason: Int,
        appSpecificErrorCode: Int
    ) {
        Log.d(TAG, "${channel.path} closed by ${channel.nodeId}")
        _closeCallback(channel)
    }
}

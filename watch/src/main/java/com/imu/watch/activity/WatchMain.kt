package com.imu.watch.activity

import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.imu.watch.DataSingleton
import com.imu.watch.modules.ServiceBroadcastReceiver
import com.imu.watch.modules.WatchChannelCallback
import com.imu.watch.ui.theme.WatchTheme
import com.imu.watch.ui.view.RenderMain
import com.imu.watch.viewmodel.WatchViewModel

class WatchMain : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "WatchMain"
    }

    private val _channelClient by lazy { Wearable.getChannelClient(this) }
    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _viewModel by viewModels<WatchViewModel>()

    private val _channelCallback = WatchChannelCallback(
        closeCallback = { _viewModel.onChannelClose(it) }
    )

    private val _br = ServiceBroadcastReceiver(
        onServiceClose = { _viewModel.onServiceClose(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            _viewModel.queryCapabilities()
            _viewModel.regularConnectionCheck()
            registerListeners()

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WatchTheme {
                RenderMain(
                    connected = _viewModel.pingSuccessState,
                    connectedNodeName = _viewModel.nodeName,
                    imuStreamStateFlow = _viewModel.sensorStreamState,
                    imuStreamCallback = { _viewModel.imuStreamTrigger(it) },
                    finishCallback = ::finish
                )
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        _viewModel.onMessageReceived(messageEvent)
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _viewModel.onCapabilityChanged(capabilityInfo)
    }

    private fun registerListeners() {
        val filter = IntentFilter()
        filter.addAction(DataSingleton.BROADCAST_CLOSE)
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(_br, filter)

        _messageClient.addListener(this)
        _channelClient.registerChannelCallback(_channelCallback)
        _capabilityClient.addLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
        _capabilityClient.addListener(
            this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        _viewModel.queryCapabilities()
    }

    private fun unregisterListeners() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _viewModel.resetAllStreamStates()
        _messageClient.removeListener(this)
        _channelClient.unregisterChannelCallback(_channelCallback)
        _capabilityClient.removeListener(this)
        _capabilityClient.removeLocalCapability(DataSingleton.WATCH_APP_ACTIVE)
        this.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterListeners()
    }

    override fun onPause() {
        super.onPause()
        unregisterListeners()
    }

    override fun onResume() {
        super.onResume()
        registerListeners()
    }
}

package com.imu.phone.activity

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.imu.phone.DataSingleton
import com.imu.phone.modules.ServiceBroadcastReceiver
import com.imu.phone.service.HapticService
import com.imu.phone.service.ImuService
import com.imu.phone.ui.theme.PhoneTheme
import com.imu.phone.ui.view.RenderHome
import com.imu.phone.viewmodel.PhoneViewModel

class PhoneMain : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "PhoneMainActivity"
    }

    private val _capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private val _messageClient by lazy { Wearable.getMessageClient(this) }
    private val _viewModel by viewModels<PhoneViewModel>()

    private val _br = ServiceBroadcastReceiver(
        onServiceUpdate = { _viewModel.onServiceUpdate(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // retrieve stored values from shared preferences
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
            val storedIp = sharedPref.getString(DataSingleton.IP_KEY, DataSingleton.IP_DEFAULT)
            if (storedIp != null) {
                DataSingleton.setIp(storedIp)
            }

            DataSingleton.setImuPort(
                sharedPref.getInt(DataSingleton.PORT_KEY, DataSingleton.IMU_PORT_DEFAULT)
            )

            PhoneTheme {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                _viewModel.queryCapabilities()
                _viewModel.regularUiUpdates()

                RenderHome(
                    connectedNodeSF = _viewModel.nodeName,
                    appActiveSF = _viewModel.appActive,
                    imuSF = _viewModel.imuStreamState,
                    imuInHzSF = _viewModel.imuInHz,
                    imuOutHzSF = _viewModel.imuOutHz,
                    imuQueueSizeSF = _viewModel.imuQueueSize,
                    wifiConnectedSF = _viewModel.wifiConnected,
                    wifiSsidSF = _viewModel.wifiSsid,
                    wifiLinkSpeedSF = _viewModel.wifiLinkSpeed,
                    wifiRssiSF = _viewModel.wifiRssi,
                    onIpChange = { newIp ->
                        // Update DataSingleton
                        DataSingleton.setIp(newIp)
                        // Save to SharedPreferences
                        sharedPref.edit()
                            .putString(DataSingleton.IP_KEY, newIp)
                            .apply()
                    }
                )
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _viewModel.onCapabilityChanged(capabilityInfo)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        _viewModel.onMessageReceived(messageEvent)
    }

    private fun registerListeners() {
        val filter = IntentFilter()
        filter.addAction(DataSingleton.BROADCAST_UPDATE)
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(_br, filter)

        _messageClient.addListener(this)
        _capabilityClient.addListener(
            this,
            Uri.parse("wear://"),
            CapabilityClient.FILTER_REACHABLE
        )
        _capabilityClient.addLocalCapability(DataSingleton.PHONE_APP_ACTIVE)
        _viewModel.queryCapabilities()

        // Start IMU Service
        val imuIntent = Intent(this, ImuService::class.java)
        this.startService(imuIntent)

        // Start Haptic Service
        val hapticIntent = Intent(this, HapticService::class.java)
        hapticIntent.putExtra("nodeId", _viewModel.getConnectedNodeId())
        this.startService(hapticIntent)

        // Register listener for node ID changes to update HapticService
        _viewModel.setOnNodeIdChangedListener { nodeId ->
            val updateIntent = Intent(this, HapticService::class.java)
            updateIntent.putExtra("nodeId", nodeId)
            this.startService(updateIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        registerListeners()
    }

    override fun onPause() {
        super.onPause()

        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(_br)
        _messageClient.removeListener(this)
        _capabilityClient.removeListener(this)
        _capabilityClient.removeLocalCapability(DataSingleton.PHONE_APP_ACTIVE)

        val imuIntent = Intent(this, ImuService::class.java)
        this.stopService(imuIntent)

        val hapticIntent = Intent(this, HapticService::class.java)
        this.stopService(hapticIntent)
    }
}

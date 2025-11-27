package com.imu.phone.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.imu.phone.DataSingleton
import com.imu.phone.ui.theme.PhoneTheme
import com.imu.phone.ui.view.RenderSettings

class SettingsActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhoneTheme {
                RenderSettings(
                    saveSettingsCallback = this::saveSettings
                )
            }
        }
    }

    private fun saveSettings(ip: String, port: Int) {
        // Validate IP
        var confirmedIp = ip
        val regex = """^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.?\b){4}$""".toRegex()
        if (!regex.containsMatchIn(ip)) {
            confirmedIp = "ERROR - Malformed"
        }

        // update shared preferences
        val sharedPref = getDefaultSharedPreferences(this)
        with(sharedPref.edit()) {
            putInt(DataSingleton.PORT_KEY, port)
            putString(DataSingleton.IP_KEY, confirmedIp)
            apply()
        }

        // update data singleton
        DataSingleton.setImuPort(port)
        DataSingleton.setIp(confirmedIp)

        Log.d(TAG, "Set target IP to $confirmedIp and IMU PORT to $port")
        this.finish()
    }

    override fun onPause() {
        super.onPause()
        this.finish()
    }
}

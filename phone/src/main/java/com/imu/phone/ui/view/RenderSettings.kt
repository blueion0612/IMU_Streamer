package com.imu.phone.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.imu.phone.DataSingleton
import com.imu.phone.ui.DefaultButton
import com.imu.phone.ui.DefaultHeadline
import com.imu.phone.ui.SmallCard

@Composable
fun RenderSettings(saveSettingsCallback: (String, Int) -> Unit) {
    val port by DataSingleton.imuPort.collectAsState()
    var ipText by remember { mutableStateOf(DataSingleton.ip.value) }
    var portText by remember { mutableStateOf(port.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            DefaultHeadline(text = "UDP Settings")
        }
        item {
            SmallCard {
                Text(
                    text = "Broadcast via UDP",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.body2,
                    color = Color.White
                )
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("Target IP Address") },
                    textStyle = TextStyle(color = Color.White),
                    singleLine = true
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("UDP Port") },
                    textStyle = TextStyle(color = Color.White),
                    singleLine = true
                )
            }
        }
        item {
            DefaultButton(
                onClick = {
                    val parsedPort = portText.toIntOrNull() ?: DataSingleton.IMU_PORT
                    saveSettingsCallback(ipText, parsedPort)
                },
                text = "Save"
            )
        }
    }
}

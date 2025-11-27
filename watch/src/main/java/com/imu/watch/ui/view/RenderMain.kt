package com.imu.watch.ui.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.imu.watch.DataSingleton
import com.imu.watch.ImuStreamState
import com.imu.watch.ui.RedButton
import com.imu.watch.ui.StatusIndicator
import com.imu.watch.ui.StreamToggle
import com.imu.watch.ui.theme.SecondaryBlue
import com.imu.watch.ui.theme.TextPrimary
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderMain(
    connected: StateFlow<Boolean>,
    connectedNodeName: StateFlow<String>,
    imuStreamStateFlow: StateFlow<ImuStreamState>,
    imuStreamCallback: (Boolean) -> Unit,
    finishCallback: () -> Unit
) {
    val streamSt by imuStreamStateFlow.collectAsState()
    val nodeName by connectedNodeName.collectAsState()
    val con by connected.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        userScrollEnabled = true
    ) {
        // Title
        item {
            Text(
                text = "IMU",
                color = SecondaryBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.title1,
                fontWeight = FontWeight.Bold
            )
        }

        // Connection status
        item {
            StatusIndicator(
                isConnected = con,
                nodeName = nodeName
            )
        }

        // Stream toggle only
        item {
            StreamToggle(
                enabled = con,
                text = "Stream IMU",
                checked = (streamSt == ImuStreamState.Streaming),
                onChecked = { imuStreamCallback(it) }
            )
        }

        // Version info and Credit
        item {
            Text(
                text = "v${DataSingleton.VERSION} | Made by LYH",
                color = TextPrimary.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        // Exit button
        item {
            Spacer(modifier = Modifier.height(4.dp))
            RedButton(
                onClick = { finishCallback() },
                text = "Exit"
            )
        }
    }
}

package com.imu.phone.ui.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imu.phone.DataSingleton
import com.imu.phone.R
import com.imu.phone.ui.*
import com.imu.phone.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RenderHome(
    connectedNodeSF: StateFlow<String>,
    appActiveSF: StateFlow<Boolean>,
    imuSF: StateFlow<Boolean>,
    imuInHzSF: StateFlow<Float>,
    imuOutHzSF: StateFlow<Float>,
    imuQueueSizeSF: StateFlow<Int>,
    wifiConnectedSF: StateFlow<Boolean>,
    wifiSsidSF: StateFlow<String>,
    wifiLinkSpeedSF: StateFlow<Int>,
    wifiRssiSF: StateFlow<Int>,
    onIpChange: (String) -> Unit
) {
    val ip by DataSingleton.ip.collectAsState()
    val port by DataSingleton.imuPort.collectAsState()

    val nodeName by connectedNodeSF.collectAsState()
    val appState by appActiveSF.collectAsState()

    val imuSt by imuSF.collectAsState()
    val imuInHz by imuInHzSF.collectAsState()
    val imuOutHz by imuOutHzSF.collectAsState()
    val imuQueueSize by imuQueueSizeSF.collectAsState()

    val wifiConnected by wifiConnectedSF.collectAsState()
    val wifiSsid by wifiSsidSF.collectAsState()
    val wifiLinkSpeed by wifiLinkSpeedSF.collectAsState()
    val wifiRssi by wifiRssiSF.collectAsState()

    // Dialog state for IP editing
    var showIpDialog by remember { mutableStateOf(false) }

    // IP Edit Dialog
    if (showIpDialog) {
        IpEditDialog(
            currentIp = ip,
            onDismiss = { showIpDialog = false },
            onConfirm = { newIp ->
                onIpChange(newIp)
                showIpDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, PrimaryBlue.copy(alpha = 0.3f))
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Header with Logo
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // App Logo
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "IMU Streaming",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Real-time Sensor Data",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }

            // Connection Status Card
            item {
                BigCard {
                    SectionTitle(text = "CONNECTION STATUS")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Watch Device",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = nodeName,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        StatusBadge(
                            text = if (appState) "CONNECTED" else "DISCONNECTED",
                            isActive = appState
                        )
                    }
                }
            }

            // Stream Status Card
            item {
                BigCard {
                    SectionTitle(text = "STREAM STATUS")

                    // Status indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (imuSt) StatusGreen else StatusYellow)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (imuSt) "STREAMING" else "WAITING FOR WATCH",
                            color = if (imuSt) StatusGreen else StatusYellow,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "IN", value = "${imuInHz.toInt()} Hz", color = AccentBlue)
                        StatItem(label = "OUT", value = "${imuOutHz.toInt()} Hz", color = SecondaryBlue)
                        StatItem(label = "QUEUE", value = "$imuQueueSize", color = StatusOrange)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Start streaming from watch app",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Network Settings Card
            item {
                BigCard {
                    SectionTitle(text = "NETWORK SETTINGS")

                    SmallCard {
                        EditableInfoRow(
                            label = "Target IP",
                            value = ip,
                            onClick = { showIpDialog = true }
                        )
                        Divider(
                            color = CardBackground,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        InfoRow(label = "UDP Port (IMU)", value = port.toString())
                        Divider(
                            color = CardBackground,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        InfoRow(label = "UDP Port (Haptic)", value = DataSingleton.HAPTIC_PORT.toString())
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tap IP address to edit",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // WiFi Status Card
            item {
                BigCard {
                    SectionTitle(text = "WIFI STATUS")

                    // WiFi Connection indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (wifiConnected) StatusGreen else StatusRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (wifiConnected) wifiSsid else "NOT CONNECTED",
                            color = if (wifiConnected) StatusGreen else StatusRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (wifiConnected) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                label = "SPEED",
                                value = "$wifiLinkSpeed Mbps",
                                color = AccentBlue
                            )
                            StatItem(
                                label = "SIGNAL",
                                value = "$wifiRssi dBm",
                                color = when {
                                    wifiRssi >= -50 -> StatusGreen
                                    wifiRssi >= -70 -> StatusYellow
                                    else -> StatusOrange
                                }
                            )
                        }
                    }
                }
            }

            // Version Footer and Credit
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Version ${DataSingleton.VERSION}",
                        textAlign = TextAlign.Center,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Made by LYH",
                        textAlign = TextAlign.Center,
                        color = SecondaryBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

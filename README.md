# IMU Streaming App

A real-time IMU sensor data streaming application for Android smartwatch and smartphone.

> This project is based on [wearable-motion-capture/sensor-stream-apps](https://github.com/wearable-motion-capture/sensor-stream-apps).

## Overview

| Item | Value |
|------|-------|
| Version | 0.4.1 |
| Platform | Android (Phone + WearOS Watch) |
| Language | Kotlin |
| Build System | Gradle (AGP 8.4.0) |

## Features

- **Real-time IMU Streaming**: Stream accelerometer, gyroscope, and rotation vector data from watch and phone via UDP
- **Haptic Feedback**: Send vibration feedback from server to watch
- **WiFi Status Monitoring**: Real-time display of connection status, speed, and signal strength
- **Easy Configuration**: Modify target IP directly within the app

## System Architecture

```
┌─────────────┐    Bluetooth    ┌─────────────┐      UDP        ┌─────────────┐
│   Watch     │  ───────────>   │   Phone     │  ───────────>   │   Server    │
│  (WearOS)   │    IMU 15f      │  (Android)  │   IMU 30f       │  (Python)   │
│             │                 │             │   Port 65000    │             │
└─────────────┘                 └─────────────┘                 └─────────────┘
      ▲                               ▲                               │
      │                               │           UDP                 │
      │        Bluetooth              │        Port 65010             │
      └───────────────────────────────┴───────────────────────────────┘
                              Haptic Command (12 bytes)
```

## Data Format

### IMU Data (30 floats, 120 bytes, Big Endian)

| Index | Data | Description |
|-------|------|-------------|
| 0-14 | Watch | dT, timestamp(4), lacc(3), gyro(3), rotvec(4) |
| 15-29 | Phone | dT, timestamp(4), lacc(3), gyro(3), rotvec(4) |

**Field Details:**
- `dT`: Delta time since last sample (seconds)
- `timestamp`: Hour, minute, second, nanosecond
- `lacc`: Linear acceleration [x, y, z] (m/s²)
- `gyro`: Gyroscope [x, y, z] (rad/s)
- `rotvec`: Rotation vector quaternion [w, x, y, z]

### Haptic Command (3 integers, 12 bytes, Little Endian)

| Index | Field | Range | Description |
|-------|-------|-------|-------------|
| 0 | intensity | 1-255 | Vibration intensity |
| 1 | count | 1-10 | Number of vibrations |
| 2 | duration | 50-500 | Duration per vibration (ms) |

## Requirements

### Development Environment
- Android Studio (Hedgehog or later)
- Gradle 8.4.0+
- Kotlin 1.8.10
- Java 1.8

### Devices
- **Phone**: Android 10 (API 29) or higher
- **Watch**: WearOS (API 28 or higher), Samsung Galaxy Watch series recommended

## Build & Install

```bash
# Full build
./gradlew build

# Build and install Phone app
./gradlew :phone:installDebug

# Build and install Watch app
./gradlew :watch:installDebug
```

## Usage

### 1. Setup Phone App
- Launch the phone app
- Set the target IP to your server IP (tap to edit)
- Verify WiFi connection status

### 2. Setup Watch App
- Launch the watch app
- Verify connection with phone (shows "Connected")
- Toggle "Stream IMU" to start streaming

### 3. Receive Data on Server

```python
import socket
import struct

# Create UDP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(('0.0.0.0', 65000))

# Receive IMU data
data, addr = sock.recvfrom(120)
values = struct.unpack('>30f', data)  # Big Endian

# Parse watch data
watch_dt = values[0]
watch_timestamp = values[1:5]
watch_lacc = values[5:8]      # Linear acceleration
watch_gyro = values[8:11]     # Gyroscope
watch_rotvec = values[11:15]  # Rotation vector

# Parse phone data
phone_dt = values[15]
phone_timestamp = values[16:20]
phone_lacc = values[20:23]
phone_gyro = values[23:26]
phone_rotvec = values[26:30]
```

### 4. Send Haptic Feedback

```python
import socket
import struct

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
phone_ip = "192.168.1.100"  # Phone IP address

# Send haptic command (Little Endian)
intensity = 200  # 1-255
count = 1        # 1-10
duration = 100   # 50-500 ms

data = struct.pack('<iii', intensity, count, duration)
sock.sendto(data, (phone_ip, 65010))
```

## Project Structure

```
imu-streaming-app/
├── phone/                          # Android Phone App
│   └── src/main/java/com/imu/phone/
│       ├── activity/               # Activities
│       ├── service/                # IMU, Haptic services
│       ├── viewmodel/              # UI state management
│       ├── ui/                     # Jetpack Compose UI
│       └── DataSingleton.kt        # Global constants
│
├── watch/                          # WearOS Watch App
│   └── src/main/java/com/imu/watch/
│       ├── activity/               # Activities
│       ├── service/                # IMU service
│       ├── viewmodel/              # State management
│       └── DataSingleton.kt        # Global constants
│
├── test/                           # Python test code
│   └── imu_test.py                 # Real-time visualization & haptic test
│
├── IMU_APP_SPEC.md                 # Detailed data communication spec
└── README.md
```

## Running Test Code

```bash
cd test
pip install matplotlib numpy
python imu_test.py
```

## Network Configuration

| Data Type | Port | Direction | Format |
|-----------|------|-----------|--------|
| IMU | 65000 | Phone → Server | Big Endian |
| Haptic | 65010 | Server → Phone | Little Endian |

## Troubleshooting

### Watch not connecting to Phone
- Ensure both devices are paired via Bluetooth
- Check that the Phone app is running
- Restart both apps

### No data received on server
- Verify the target IP is correctly set on the phone
- Check firewall settings on the server
- Ensure phone and server are on the same network

### Haptic feedback not working
- Check the phone IP address in your Python code
- Verify port 65010 is not blocked
- Ensure the watch is connected and streaming

## References

- Original Project: [wearable-motion-capture/sensor-stream-apps](https://github.com/wearable-motion-capture/sensor-stream-apps)
- Detailed Data Specification: [IMU_APP_SPEC.md](./IMU_APP_SPEC.md)

## License

MIT License

## Author

Made by LYH - Myongji University Capstone Project

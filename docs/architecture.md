# Architecture - IMU Streaming App

## 프로젝트 개요

스마트워치와 스마트폰에서 IMU 센서 데이터를 실시간으로 스트리밍하는 Android 애플리케이션입니다.
Phone Pocket 모드 전용으로 설계되었으며, Calibration 없이 바로 스트리밍을 시작할 수 있습니다.

- **버전:** 1.0.0
- **플랫폼:** Android (Kotlin)
- **빌드 시스템:** Gradle (AGP 8.4.0)

---

## 프로젝트 구조

```
imu-streaming-app/
├── phone/                          # Android Phone 앱
│   └── src/main/java/com/imu/phone/
│       ├── activity/
│       │   ├── PhoneMain.kt        # 메인 Activity
│       │   └── SettingsActivity.kt # IP/포트 설정
│       ├── service/
│       │   └── ImuService.kt       # IMU 스트리밍 서비스
│       ├── modules/
│       │   ├── SensorListener.kt
│       │   ├── PhoneChannelCallback.kt
│       │   └── ServiceBroadcastReceiver.kt
│       ├── viewmodel/
│       │   └── PhoneViewModel.kt
│       ├── ui/
│       │   ├── UiElements.kt
│       │   ├── view/
│       │   │   ├── RenderHome.kt
│       │   │   └── RenderSettings.kt
│       │   └── theme/
│       └── DataSingleton.kt
│
├── watch/                          # WearOS Watch 앱
│   └── src/main/java/com/imu/watch/
│       ├── activity/
│       │   └── WatchMain.kt        # 메인 Activity
│       ├── service/
│       │   └── ImuService.kt       # IMU 스트리밍 서비스
│       ├── modules/
│       │   ├── SensorListener.kt
│       │   ├── WatchChannelCallback.kt
│       │   └── ServiceBroadcastReceiver.kt
│       ├── viewmodel/
│       │   └── WatchViewModel.kt
│       ├── ui/
│       │   ├── UIElements.kt
│       │   ├── view/
│       │   │   └── RenderMain.kt
│       │   └── theme/
│       └── DataSingleton.kt
│
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## 동작 모드

**Phone Pocket 모드 전용**
- 폰을 주머니에, 워치는 손목에
- 데이터 흐름: `Watch → Bluetooth/Channel → Phone → UDP → Server`
- Calibration 없이 바로 스트리밍 시작 가능

---

## UDP 메시지 포맷

### 전송 데이터 구조 (30 floats = 120 bytes)

```
# Watch Data (15 floats)
sw_dt       [0]     # delta time since last obs
sw_h        [1]     # hour
sw_m        [2]     # minute
sw_s        [3]     # second
sw_ns       [4]     # nanosecond
sw_lacc_x   [5]     # linear acceleration x
sw_lacc_y   [6]     # linear acceleration y
sw_lacc_z   [7]     # linear acceleration z
sw_gyro_x   [8]     # gyroscope x
sw_gyro_y   [9]     # gyroscope y
sw_gyro_z   [10]    # gyroscope z
sw_rotvec_w [11]    # rotation vector w (quaternion)
sw_rotvec_x [12]    # rotation vector x
sw_rotvec_y [13]    # rotation vector y
sw_rotvec_z [14]    # rotation vector z

# Phone Data (15 floats)
ph_dt       [15]    # delta time since last obs
ph_h        [16]    # hour
ph_m        [17]    # minute
ph_s        [18]    # second
ph_ns       [19]    # nanosecond
ph_lacc_x   [20]    # linear acceleration x
ph_lacc_y   [21]    # linear acceleration y
ph_lacc_z   [22]    # linear acceleration z
ph_gyro_x   [23]    # gyroscope x
ph_gyro_y   [24]    # gyroscope y
ph_gyro_z   [25]    # gyroscope z
ph_rotvec_w [26]    # rotation vector w (quaternion)
ph_rotvec_x [27]    # rotation vector x
ph_rotvec_y [28]    # rotation vector y
ph_rotvec_z [29]    # rotation vector z
```

### Python 수신 예제

```python
import socket
import struct

IMU_MSG_LOOKUP = {
    # Watch data
    "sw_dt": 0, "sw_h": 1, "sw_m": 2, "sw_s": 3, "sw_ns": 4,
    "sw_lacc_x": 5, "sw_lacc_y": 6, "sw_lacc_z": 7,
    "sw_gyro_x": 8, "sw_gyro_y": 9, "sw_gyro_z": 10,
    "sw_rotvec_w": 11, "sw_rotvec_x": 12, "sw_rotvec_y": 13, "sw_rotvec_z": 14,
    # Phone data
    "ph_dt": 15, "ph_h": 16, "ph_m": 17, "ph_s": 18, "ph_ns": 19,
    "ph_lacc_x": 20, "ph_lacc_y": 21, "ph_lacc_z": 22,
    "ph_gyro_x": 23, "ph_gyro_y": 24, "ph_gyro_z": 25,
    "ph_rotvec_w": 26, "ph_rotvec_x": 27, "ph_rotvec_y": 28, "ph_rotvec_z": 29,
}

MSG_SIZE = len(IMU_MSG_LOOKUP) * 4  # 120 bytes

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(('0.0.0.0', 65000))

while True:
    data, addr = sock.recvfrom(MSG_SIZE)
    values = struct.unpack(f'>{len(IMU_MSG_LOOKUP)}f', data)
    # 데이터 처리
```

---

## 통신 프로토콜

### 워치-폰 통신 (Wearable Data API)
- **채널:** `/imu`
- **메시지:** `/ping_request`, `/ping_reply`, `/start_imu`
- **Capability:** `"phone"`, `"watch"`

### UDP 스트리밍
- **기본 포트:** 65000
- **기본 IP:** 192.168.1.138 (Settings에서 변경 가능)

---

## 빌드 및 실행

### 요구사항
- Android Studio (Gradle 8.4.0+)
- Kotlin 1.8.10
- Java 1.8

### 빌드 명령
```bash
./gradlew build
./gradlew :phone:assembleDebug
./gradlew :watch:assembleDebug
./gradlew :phone:installDebug
./gradlew :watch:installDebug
```

---

## 핵심 클래스

### Phone App
| 클래스 | 역할 |
|--------|------|
| `PhoneMain.kt` | 메인 Activity, 디바이스 검색 |
| `PhoneViewModel.kt` | 상태 관리, ping/heartbeat |
| `ImuService.kt` | 워치 데이터 수신, 폰 센서 융합, UDP 전송 |
| `DataSingleton.kt` | 전역 설정 및 상수 |

### Watch App
| 클래스 | 역할 |
|--------|------|
| `WatchMain.kt` | 메인 Activity |
| `WatchViewModel.kt` | 상태 관리 |
| `ImuService.kt` | 센서 수집, 블루투스 채널로 전송 |
| `DataSingleton.kt` | 전역 설정 및 상수 |

---

## 데이터 흐름

```
1. Phone 앱 시작 → ImuService 시작 → Channel Callback 등록

2. Watch 앱에서 "Stream IMU" 토글 ON
   → ImuService 시작
   → Phone에 Channel 오픈 (/imu)

3. Watch: 센서 수집 → composeImuMessage() → Channel 전송

4. Phone: Channel 수신 → Queue에 저장
         → 폰 센서 수집 → composeImuMessage()
         → Watch + Phone 데이터 결합
         → UDP 전송

5. 서버: UDP 포트 65000에서 120 bytes 수신
```

---

## 설정 (Settings)

### Phone SharedPreferences
```
- "com.imu.phone.ip" (기본: "192.168.1.138")
- "com.imu.phone.port" (기본: 65000)
```

---

## 권한

### Phone
- `VIBRATE`
- `HIGH_SAMPLING_RATE_SENSORS`
- `INTERNET`
- `ACCESS_NETWORK_STATE`

### Watch
- `WAKE_LOCK`
- `BODY_SENSORS`
- `VIBRATE`
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `HIGH_SAMPLING_RATE_SENSORS`

---

## 기존 버전과의 차이점

1. **Calibration 제거**: "hold watch level to ground and parallel to hip" 메시지 및 calibration 과정 없음
2. **단일 모드**: Phone Pocket 모드만 지원
3. **간소화된 메시지 포맷**:
   - 기존: 55 floats (220 bytes) - calibration 데이터 포함
   - 변경: 30 floats (120 bytes) - 필수 데이터만 포함
4. **제거된 데이터**:
   - `sw_lvel_*`, `ph_lvel_*` (적분 속도)
   - `sw_pres`, `ph_pres` (기압)
   - `sw_grav_*`, `ph_grav_*` (중력)
   - `sw_rotvec_conf`, `ph_rotvec_conf` (회전 벡터 신뢰도)
   - `sw_forward_*`, `ph_forward_*` (calibration 쿼터니언)
   - `sw_init_pres` (초기 기압)

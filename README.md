# IMU Streaming App

스마트워치와 스마트폰에서 IMU 센서 데이터를 실시간으로 스트리밍하는 Android 애플리케이션입니다.

> 본 프로젝트는 [wearable-motion-capture/sensor-stream-apps](https://github.com/wearable-motion-capture/sensor-stream-apps)를 기반으로 개발되었습니다.

## 개요

| 항목 | 값 |
|------|-----|
| 버전 | 0.4.1 |
| 플랫폼 | Android (Phone + WearOS Watch) |
| 언어 | Kotlin |
| 빌드 시스템 | Gradle (AGP 8.4.0) |

## 주요 기능

- **실시간 IMU 스트리밍**: 워치와 폰의 가속도계, 자이로스코프, 회전 벡터 데이터를 UDP로 전송
- **햅틱 피드백**: 서버에서 워치로 진동 피드백 전송 가능
- **WiFi 상태 모니터링**: 연결 상태, 속도, 신호 강도 실시간 표시
- **간편한 설정**: 앱 내에서 Target IP 직접 수정 가능

## 시스템 구조

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

## 데이터 형식

### IMU 데이터 (30 floats, 120 bytes, Big Endian)

| 인덱스 | 데이터 | 설명 |
|--------|--------|------|
| 0-14 | Watch | dT, timestamp(4), lacc(3), gyro(3), rotvec(4) |
| 15-29 | Phone | dT, timestamp(4), lacc(3), gyro(3), rotvec(4) |

### 햅틱 명령 (3 integers, 12 bytes, Little Endian)

| 인덱스 | 필드 | 범위 |
|--------|------|------|
| 0 | intensity | 1-255 |
| 1 | count | 1-10 |
| 2 | duration | 50-500 ms |

## 요구 사항

### 개발 환경
- Android Studio (Hedgehog 이상)
- Gradle 8.4.0+
- Kotlin 1.8.10
- Java 1.8

### 디바이스
- **Phone**: Android 10 (API 29) 이상
- **Watch**: WearOS (API 28 이상), Galaxy Watch 시리즈 권장

## 빌드 및 설치

```bash
# 전체 빌드
./gradlew build

# Phone 앱 빌드 및 설치
./gradlew :phone:installDebug

# Watch 앱 빌드 및 설치
./gradlew :watch:installDebug
```

## 사용 방법

1. **Phone 앱 실행**
   - Target IP를 서버 IP로 설정 (화면에서 직접 수정 가능)
   - WiFi 연결 상태 확인

2. **Watch 앱 실행**
   - Phone과 연결 상태 확인 (Connected 표시)
   - "Stream IMU" 토글을 켜서 스트리밍 시작

3. **서버에서 데이터 수신**
   ```python
   import socket
   import struct

   sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
   sock.bind(('0.0.0.0', 65000))

   data, addr = sock.recvfrom(120)
   values = struct.unpack('>30f', data)  # Big Endian

   watch_acc = values[5:8]    # Watch 가속도
   phone_acc = values[20:23]  # Phone 가속도
   ```

4. **햅틱 피드백 전송**
   ```python
   import struct

   data = struct.pack('<iii', 200, 1, 100)  # intensity, count, duration
   sock.sendto(data, (phone_ip, 65010))
   ```

## 프로젝트 구조

```
imu-streaming-app/
├── phone/                          # Android Phone 앱
│   └── src/main/java/com/imu/phone/
│       ├── activity/               # Activities
│       ├── service/                # IMU, Haptic 서비스
│       ├── viewmodel/              # UI 상태 관리
│       ├── ui/                     # Jetpack Compose UI
│       └── DataSingleton.kt        # 전역 상수
│
├── watch/                          # WearOS Watch 앱
│   └── src/main/java/com/imu/watch/
│       ├── activity/               # Activities
│       ├── service/                # IMU 서비스
│       ├── viewmodel/              # 상태 관리
│       └── DataSingleton.kt        # 전역 상수
│
├── test/                           # Python 테스트 코드
│   └── imu_test.py                 # 실시간 시각화 및 햅틱 테스트
│
├── IMU_APP_SPEC.md                 # 상세 데이터 통신 명세서
└── README.md
```

## 테스트 코드 실행

```bash
cd test
pip install matplotlib numpy
python imu_test.py
```

## 참고 자료

- 원본 프로젝트: [wearable-motion-capture/sensor-stream-apps](https://github.com/wearable-motion-capture/sensor-stream-apps)
- 상세 데이터 명세: [IMU_APP_SPEC.md](./IMU_APP_SPEC.md)

## 라이선스

MIT License

## 제작

Made by LYH - 명지대학교 캡스톤 프로젝트

# Wire protocol

Everything a server needs in order to talk to the phone app. Constants here are
taken from `DataSingleton.kt` in each module; that file is the source of truth.

## Links

| Hop | Transport | Payload | Endianness |
|---|---|---|---|
| Watch to phone | Wearable channel, path `/imu` | 15 floats, 60 B | big |
| Phone to server | UDP, port 65000 | 30 floats, 120 B | big |
| Server to phone | UDP, port 65010 | 3 int32, 12 B | little |
| Phone to watch | Wearable message, path `/haptic` | 3 int32, 12 B | big |

The two UDP endianness values differ because each side uses its platform default:
Java's `ByteBuffer` is big endian, Python's `struct` is little endian. The phone
reads haptic commands little endian so that a plain `struct.pack('<iii', ...)`
works without a format character.

## IMU packet, phone to server

30 floats, 120 bytes. Indices 0 to 14 are the watch, 15 to 29 are the phone, and
the two blocks have the same layout.

| Offset | Field | Meaning | Unit |
|---|---|---|---|
| +0 | `dT` | time since the previous sample | s |
| +1 to +4 | `ts_hour`, `ts_min`, `ts_sec`, `ts_nano` | timestamp | |
| +5 to +7 | `lacc_x/y/z` | linear acceleration, gravity removed | m/s² |
| +8 to +10 | `gyro_x/y/z` | angular rate | rad/s |
| +11 to +14 | `rotvec_w/x/y/z` | rotation vector, quaternion | |

Read it with:

```python
import socket, struct

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(("0.0.0.0", 65000))

data, _ = sock.recvfrom(120)
v = struct.unpack(">30f", data)          # big endian

watch = dict(dT=v[0], ts=v[1:5], lacc=v[5:8], gyro=v[8:11], rotvec=v[11:15])
phone = dict(dT=v[15], ts=v[16:20], lacc=v[20:23], gyro=v[23:26], rotvec=v[26:30])
```

The phone emits a packet only once it holds a watch sample, so a packet always
carries both devices. `dT` is per device and the two are not locked to each other.

## Haptic command, server to phone

3 signed 32-bit integers, 12 bytes, little endian. The phone forwards the command
to the watch over the wearable message path `/haptic`.

| Index | Field | Range | Meaning |
|---|---|---|---|
| 0 | intensity | 1 to 255 | vibration amplitude |
| 1 | count | 1 to 10 | number of pulses |
| 2 | duration | 50 to 500 | milliseconds per pulse |

```python
import socket, struct

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.sendto(struct.pack("<iii", 200, 1, 100), ("192.168.1.138", 65010))
```

## Wearable paths and capabilities

| Constant | Value |
|---|---|
| IMU channel | `/imu` |
| Haptic message | `/haptic` |
| Ping request, reply | `/ping_request`, `/ping_reply` |
| Capabilities | `watch`, `phone` |

## Sensors

Both devices register the same three Android sensors.

| Sensor | Android type |
|---|---|
| Linear acceleration | `TYPE_LINEAR_ACCELERATION` |
| Gyroscope | `TYPE_GYROSCOPE` |
| Rotation vector | `TYPE_ROTATION_VECTOR` |

Sample rate is whatever the platform delivers for the requested delay; the app
does not resample, which is why `dT` is transmitted rather than assumed.

## Settings the phone stores

| Key | Default |
|---|---|
| `com.imu.phone.ip` | `192.168.1.138` |
| `com.imu.phone.port` | `65000` |

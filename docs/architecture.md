# Architecture

Two Gradle modules, `phone` and `watch`, sharing no code. Each has the same shape:
an activity, a foreground service that owns the sensors, a view model holding UI
state, and a `DataSingleton` of constants.

For the wire format, see [protocol.md](protocol.md).

## Modules

```
phone/src/main/java/com/imu/phone/
  activity/PhoneMain.kt            device discovery, permissions, main screen
  activity/SettingsActivity.kt     server address and port
  service/ImuService.kt            receives watch samples, reads phone sensors,
                                   merges the two blocks, sends UDP
  service/HapticService.kt         listens on 65010, forwards to the watch
  modules/SensorListener.kt        Android sensor callbacks
  modules/PhoneChannelCallback.kt  wearable channel lifecycle
  viewmodel/PhoneViewModel.kt      connection state, ping and heartbeat
  DataSingleton.kt                 constants and stored settings

watch/src/main/java/com/imu/watch/
  activity/WatchMain.kt            main screen and the stream toggle
  service/ImuService.kt            reads sensors, writes to the channel
  modules/WatchChannelCallback.kt  wearable channel lifecycle
  viewmodel/WatchViewModel.kt      connection state
  DataSingleton.kt                 constants
```

## Runtime sequence

1. The phone app starts its service and registers the channel callback.
2. The user toggles **Stream IMU** on the watch. The watch service starts and
   opens the `/imu` channel to the phone.
3. The watch samples its sensors, packs 15 floats, and writes them to the channel.
4. The phone queues each watch sample, samples its own sensors, concatenates the
   two blocks into 30 floats, and sends the result to the configured address.
5. A server may reply on port 65010 with a haptic command, which the phone
   forwards to the watch.

The phone is the only device that talks to the network. The watch never needs an
IP address.

## Permissions

| Permission | Phone | Watch | Why |
|---|---|---|---|
| `INTERNET` | yes | yes | UDP |
| `ACCESS_NETWORK_STATE` | yes | yes | connection status display |
| `ACCESS_WIFI_STATE` | yes | yes | link speed and signal display |
| `HIGH_SAMPLING_RATE_SENSORS` | yes | yes | sensor rates above 200 Hz |
| `VIBRATE` | yes | yes | haptic feedback |
| `BODY_SENSORS` | | yes | wrist sensors |
| `WAKE_LOCK` | | yes | keep sampling with the screen off |

## What this fork changes

Derived from [wearable-motion-capture/sensor-stream-apps](https://github.com/wearable-motion-capture/sensor-stream-apps).
The upstream project supports several wearing modes and a calibration step. This
fork keeps one mode and removes the calibration, which is the whole difference.

- **One mode only.** The phone sits in a pocket, the watch on the wrist. The
  upstream mode selector is gone.
- **No calibration.** Upstream asks the wearer to hold the watch level and
  parallel to the hip before streaming. That step and its prompt are removed, so
  streaming starts immediately.
- **Smaller packet, 55 floats to 30.** The fields that only calibration or
  pressure-based estimation needed are not transmitted: integrated velocities
  (`lvel`), barometric pressure (`pres`, `init_pres`), gravity vectors (`grav`),
  rotation-vector confidence (`rotvec_conf`) and the calibration quaternions
  (`forward`).
- **Haptic return path added.** Upstream streams in one direction. This fork
  accepts a command on UDP 65010 and forwards it to the watch.

The removed fields are the ones a calibrated pose estimate needs. Anything that
consumes this stream therefore has to work from raw sensor-frame values.

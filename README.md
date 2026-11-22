# AirMouse HID Test (Native Android / Kotlin)

**AirMouse HID Test** is a genuine native Android application written 100% in Kotlin and Jetpack Compose that uses Android's native `android.bluetooth.BluetoothHidDevice` API (`BluetoothProfile.HID_DEVICE`) to test real Bluetooth HID Mouse registration, host connectivity, and mouse input reporting on **Android 14 (API 34)**, **Android 15 (API 35)**, **Android 16 (API 36)**, and newer releases.

> **Important:** Android 14+ compatibility does **not** guarantee that a device's OEM Bluetooth stack exposes the Bluetooth HID Device profile (`BluetoothProfile.HID_DEVICE`). This app detects actual runtime capability (never confusing `HID_HOST` with `HID_DEVICE` and never simulating Bluetooth status).

---

## Project Structure

```text
.
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/strings.xml
        │   │   ├── values/themes.xml
        │   │   ├── drawable/ic_launcher_background.xml
        │   │   ├── drawable/ic_launcher_foreground.xml
        │   │   └── mipmap-anydpi-v26/
        │   └── java/com/airmouse/hidtest/
        │       ├── MainActivity.kt
        │       ├── hid/
        │       │   ├── HidMouseDescriptor.kt
        │       │   ├── HidState.kt
        │       │   └── BluetoothHidManager.kt
        │       ├── permissions/
        │       │   └── BluetoothPermissionHelper.kt
        │       ├── viewmodel/
        │       │   └── AirMouseViewModel.kt
        │       └── ui/
        │           ├── AirMouseApp.kt
        │           ├── theme/Theme.kt
        │           └── screens/
        │               ├── TouchpadScreen.kt
        │               ├── ConnectionScreen.kt
        │               └── DiagnosticsScreen.kt
        └── test/
            └── java/com/airmouse/hidtest/
                └── HidMouseDescriptorTest.kt
```

---

## SDK & Compatibility Configuration

| Setting | Value | Notes |
| --- | --- | --- |
| Language | Kotlin `2.1.20` | Native Android + Jetpack Compose |
| Android Gradle Plugin | `8.9.1` | Supports API 36 (Android 16) |
| Gradle | `8.11.1` | Configured in `gradle-wrapper.properties` |
| JDK | Java 17 | Required for AGP 8.9+ |
| `minSdk` | `34` | Android 14 |
| `compileSdk` | `36` | Android 16 |
| `targetSdk` | `36` | Android 14, 15, 16+ compatible |

### Permissions (Android 14+ Only)

Declared in `app/src/main/AndroidManifest.xml` and requested at runtime via `BluetoothPermissionHelper`:

- `android.permission.BLUETOOTH_CONNECT` — Required for `BluetoothHidDevice` proxy binding, `registerApp()`, `connect()` / `disconnect()`, `sendReport()`, and reading `bondedDevices`.
- `android.permission.BLUETOOTH_ADVERTISE` — Required for `BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE` so a host computer can discover and pair with the Android device.
- **No location permissions** (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`) are declared or requested.
- `<uses-feature android:name="android.hardware.bluetooth" android:required="false" />` allows installation on hardware without Bluetooth so the Diagnostic Dashboard can report adapter unavailability cleanly.

---

## HID Mouse Report Descriptor & Payload

Defined in `app/src/main/java/com/airmouse/hidtest/hid/HidMouseDescriptor.kt`:

- **Subclass:** `BluetoothHidDevice.SUBCLASS1_MOUSE` (`0x80`)
- **Descriptor Size:** 54 bytes (standard USB/Bluetooth HID 1.11 3-button relative mouse + vertical wheel)
- **Report ID:** `0x01` (`REPORT_ID_MOUSE = 1`)
- **Input Report Payload (4 bytes):**
  - Byte `0`: Button bitmask (`bit 0 = Left`, `bit 1 = Right`, `bit 2 = Middle`, `bits 3..7 = 0` constant padding)
  - Byte `1`: Relative `X` delta (signed 8-bit, `-127..127`)
  - Byte `2`: Relative `Y` delta (signed 8-bit, `-127..127`)
  - Byte `3`: Relative vertical `Wheel` delta (signed 8-bit, `-127..127`)
- Fast swipes exceeding `±127` in a single touch frame are split cleanly into sequential spec-compliant reports via `buildChunkedMouseReports()`.

---

## Build & Debug APK Instructions

### Prerequisites

1. **Android Studio** (Meerkat 2024.3.1 or newer recommended for API 36 support).
2. **JDK 17** (bundled with Android Studio).
3. **Android SDK Platform 36** (`SDK Manager -> SDK Platforms -> Android 16 / API 36`).
4. **Android SDK Build-Tools 35/36**.

### Option A: Build & Run in Android Studio

1. Open Android Studio and select **File -> Open...**, then choose this project root directory (containing `settings.gradle.kts`).
2. Allow Gradle Sync to download dependencies using Android Studio's Gradle distribution (`8.11.1`).
3. Connect a physical Android 14, 15, or 16 device with **USB Debugging** enabled.
4. Click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** (or click **Run**).
5. The generated Debug APK will be located at:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Option B: Build Debug APK from Command Line

If you have Gradle installed locally, bootstrap the wrapper binary once (since `gradle-wrapper.jar` is a binary not committed in plain text) and assemble the Debug APK:

```bash
# 1. Generate Gradle wrapper scripts & jar (one-time if not opened in Android Studio first)
gradle wrapper --gradle-version 8.11.1

# 2. Run JVM unit tests (verifies HID descriptor & report encoding)
./gradlew testDebugUnitTest

# 3. Build the Debug APK
./gradlew assembleDebug

# 4. Install onto a connected Android 14/15/16 device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Testing Guide & Verification Scope

### Compile-Time / Static Verification vs. Physical Hardware Testing

| Verification Layer | What It Validates | Environment Required |
| --- | --- | --- |
| **Static / Unit Tests** (`HidMouseDescriptorTest`) | 54-byte HID descriptor structure, Report ID `0x01`, button bitmasking, `[-127, 127]` clamping, fast-swipe chunking, Kotlin/Compose compilation against API 36. | Local dev machine with JDK 17 + Android SDK 36. *(Note: The cloud sandbox where source files were generated does not include JDK/Android SDK, so run `./gradlew testDebugUnitTest assembleDebug` locally.)* |
| **Android Emulator** | App startup, dark UI responsiveness, Android 14/15/16 runtime permission dialogs, graceful handling of unavailable `BluetoothHidDevice`. | Android Studio Emulator (API 34, 35, 36). Emulators do **not** provide real Bluetooth HID Device radios. |
| **Physical Hardware Testing** | Real `BluetoothProfile.HID_DEVICE` proxy acquisition, `registerApp()` SDP registration, host pairing, `onConnectionStateChanged(STATE_CONNECTED)`, and live cursor movement/clicks/wheel via `sendReport()`. | Physical Android 14 (API 34), Android 15 (API 35), and Android 16 (API 36) devices + a Bluetooth host (Windows 11, macOS, Linux BlueZ, ChromeOS, or another Android tablet). |

### Physical Device Test Procedure

1. Launch **AirMouse HID Test** on a physical Android 14, 15, or 16 phone and grant **Nearby devices** (`BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE`).
2. Open the **Diagnostics** tab and verify:
   - Android Version & API Level (`34`, `35`, or `36`)
   - Bluetooth Adapter: `Available` & `Enabled (On)`
   - HID Device Profile: `Available (HID Device)` — if `Unavailable on This Device`, the device's OEM Bluetooth stack does not expose `BluetoothHidDeviceService`.
3. Open the **Connection** tab and tap **Register HID**.
   - Confirm `HID Registration` transitions to `Registered` (verified by `onAppStatusChanged(registered = true)`).
4. Tap **Discoverable** (or **Pair Settings**) and pair from a Windows/macOS/Linux host — the phone appears as a Bluetooth Mouse (`AirMouse HID`).
5. Tap **Connect** on the paired host (or connect from the host's Bluetooth menu). Confirm the device badge changes from `PAIRED ONLY — NOT CONNECTED` to `CONNECTED VIA HID DEVICE`.
6. Switch to the **Touchpad** tab and test:
   - Single-finger relative X/Y movement & boundary edges
   - Left-click press/release and click-and-drag
   - Right-click press/release
   - Two-finger vertical scroll wheel
   - Sensitivity slider adjustment (`0.40x` – `3.00x`)
7. Toggle Bluetooth **Off** from Quick Settings during an active session and confirm the app resets proxy/registration state cleanly without crashing, then toggle Bluetooth **On** and verify automatic re-probing.

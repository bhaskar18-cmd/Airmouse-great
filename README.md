# AirMouse HID Test (Native Android / Kotlin)

**AirMouse HID Test** is a genuine native Android application written 100% in Kotlin and Jetpack Compose using Android's native `android.bluetooth.BluetoothHidDevice` API (`BluetoothProfile.HID_DEVICE`). It tests real Bluetooth HID Mouse registration, host connection, and mouse input reporting on **Android 14 (API 34)**, **Android 15 (API 35)**, **Android 16 (API 36)**, and newer releases.

> **Important:** Android 14+ compatibility does **not** guarantee that every phone's OEM Bluetooth stack exposes the Bluetooth HID Device profile (`BluetoothProfile.HID_DEVICE`). This app checks actual device capability at runtime and never simulates Bluetooth connections.

---

## Automatic APK Builds From Your Android Phone (No PC Needed)

This project includes a complete Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) and a ready-to-run GitHub Actions workflow (`.github/workflows/build-android.yml`) that compiles the real Debug APK in the cloud.

### 1. Connect This Project to GitHub

**Option A — Directly from AI Studio (Fastest on a Phone):**
1. Tap the **GitHub / Export to GitHub** icon in AI Studio.
2. Sign in to your GitHub account and choose **Create new repository** (or pick an existing empty repository).
3. Make sure the default branch is **`main`** and push/sync all project files (including `.github/workflows/build-android.yml`, `gradlew`, `gradle/wrapper/gradle-wrapper.jar`, and `app/`).

**Option B — Manual Upload via Phone Browser (If Not Using Direct Sync):**
1. Download/export the project ZIP from AI Studio to your phone.
2. Create a new repository on [github.com](https://github.com) with default branch **`main`**.
3. Push or upload the unzipped files (preserving folder paths such as `.github/workflows/build-android.yml` and `gradle/wrapper/gradle-wrapper.jar`).

---

### 2. How Automatic APK Building Works

Once the repository is on GitHub, the workflow at `.github/workflows/build-android.yml` runs automatically:

- **Automatic trigger:** Runs on every push to the **`main`** branch.
- **Manual trigger:** Open your repository on GitHub -> tap **Actions** -> tap **Build AirMouse Android Debug APK** -> tap **Run workflow** -> choose `main` -> tap **Run workflow**.

During each run, GitHub Actions:
1. Sets up **JDK 17** and **Android SDK Platform 36** (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 34`).
2. Runs unit tests (`./gradlew testDebugUnitTest --stacktrace`).
3. Compiles the real Debug APK (`./gradlew assembleDebug --stacktrace`).
4. Stops immediately and shows the exact Gradle error log if tests or compilation fail (no fake APKs are ever created).
5. Verifies `app/build/outputs/apk/debug/app-debug.apk` and uploads it as an artifact named **`AirMouse-Debug-APK`** (retained for **14 days**).

---

### 3. Where to Download the APK

1. Open your GitHub repository in Chrome (or the GitHub mobile app).
2. Tap the **Actions** tab at the top.
3. Tap the latest completed green (**✓**) workflow run titled **Build AirMouse Android Debug APK**.
4. Scroll down to the **Artifacts** section.
5. Tap **`AirMouse-Debug-APK`** to download `AirMouse-Debug-APK.zip` to your phone's **Downloads** folder.

> Note: You must be signed in to GitHub in your mobile browser to download Actions artifacts.

---

### 4. How to Install the APK on Android 14 / 15 / 16

1. Open your phone's **Files** app (Google Files / My Files) and go to **Downloads**.
2. Tap **`AirMouse-Debug-APK.zip`** and select **Extract** — this extracts **`app-debug.apk`**.
3. Tap **`app-debug.apk`**.
4. If Android asks for permission to install unknown apps, tap **Settings**, turn on **Allow from this source** for your Files app, and go back.
5. Tap **Install**, then **Open** **AirMouse HID Test**.
6. When prompted, allow **Nearby devices** (`BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE`) — no location permission is requested.

---

## Testing Real Bluetooth HID on Your Phone

1. Open the **Diagnostics** tab and check:
   - **Android Version & API Level** (`34`, `35`, or `36`)
   - **Bluetooth Adapter:** `Available` and `Enabled (On)`
   - **HID Device Profile:** `Available (HID Device)` *(if `Unavailable on This Device`, the phone's OEM firmware disables `BluetoothHidDeviceService`)*
2. Open the **Connection** tab and tap **Register HID**. Confirm status changes to **Registered**.
3. Tap **Discoverable** (or **Pair Settings**) and pair from a Windows, macOS, Linux, ChromeOS, or tablet host — the phone advertises as **`AirMouse HID`**.
4. Tap **Connect** on the paired host (or connect from the host's Bluetooth menu). Confirm the status changes from `PAIRED ONLY — NOT CONNECTED` to `CONNECTED VIA HID DEVICE`.
5. Open the **Touchpad** tab and test cursor movement, Left/Right press-and-release clicks, two-finger vertical wheel scrolling, and sensitivity adjustment.

---

## Project Configuration & Architecture

| Setting | Value |
| --- | --- |
| Package / Namespace | `com.airmouse.hidtest` |
| Language | Kotlin `2.1.20` + Jetpack Compose |
| Android Gradle Plugin | `8.9.1` |
| Gradle Wrapper | `8.11.1` (bundled) |
| JDK | Java `17` |
| `minSdk` | `34` (Android 14) |
| `compileSdk` / `targetSdk` | `36` (Android 16) |

### Key Files

```text
.
├── .github/workflows/build-android.yml
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
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
        └── test/java/com/airmouse/hidtest/
            └── HidMouseDescriptorTest.kt
```

### HID Mouse Report Descriptor Summary

- **Subclass:** `BluetoothHidDevice.SUBCLASS1_MOUSE` (`0x80`)
- **Descriptor Size:** 54 bytes (USB/Bluetooth HID 1.11 relative mouse)
- **Report ID:** `0x01`
- **4-Byte Input Payload:** `[Buttons (3 bits + 5-bit pad), Delta X (-127..127), Delta Y (-127..127), Wheel (-127..127)]`
- **Permissions:** Only `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE` (no location permissions).

---

## Optional Local Computer Build

If you ever build on a computer with JDK 17 and Android SDK 36 installed:

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

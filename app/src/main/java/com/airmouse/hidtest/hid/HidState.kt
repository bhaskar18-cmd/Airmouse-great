package com.airmouse.hidtest.hid

import android.os.Build

enum class BluetoothAdapterState(val label: String) {
    UNAVAILABLE("Adapter Unavailable"),
    OFF("Disabled (Off)"),
    TURNING_ON("Turning On"),
    ON("Enabled (On)"),
    TURNING_OFF("Turning Off")
}

enum class HidProfileStatus(val label: String) {
    NOT_PROBED("Not Probed"),
    PERMISSION_REQUIRED("Permission Required"),
    BLUETOOTH_OFF("Bluetooth Disabled"),
    PROBING("Acquiring Profile Proxy..."),
    AVAILABLE("Available (HID Device)"),
    UNAVAILABLE("Unavailable on This Device"),
    DISCONNECTED("Service Disconnected")
}

enum class HidRegistrationStatus(val label: String) {
    UNREGISTERED("Unregistered"),
    REGISTERING("Registering App..."),
    REGISTERED("Registered"),
    FAILED("Registration Failed")
}

enum class HidConnectionStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting..."),
    CONNECTED("Connected"),
    DISCONNECTING("Disconnecting...")
}

data class PairedDeviceInfo(
    val name: String,
    val address: String,
    val isConnectedHidHost: Boolean
)

data class DiagnosticLogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

data class DeviceSystemInfo(
    val androidVersion: String = Build.VERSION.RELEASE ?: "Unknown",
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val securityPatch: String = Build.VERSION.SECURITY_PATCH ?: "Unknown",
    val manufacturer: String = Build.MANUFACTURER ?: "Unknown",
    val model: String = Build.MODEL ?: "Unknown",
    val deviceCodeName: String = Build.DEVICE ?: "Unknown"
)

data class HidUiState(
    val systemInfo: DeviceSystemInfo = DeviceSystemInfo(),
    val hasConnectPermission: Boolean = false,
    val hasAdvertisePermission: Boolean = false,
    val permissionRationaleMessage: String? = null,
    val adapterAvailable: Boolean = false,
    val adapterState: BluetoothAdapterState = BluetoothAdapterState.UNAVAILABLE,
    val hidProfileStatus: HidProfileStatus = HidProfileStatus.NOT_PROBED,
    val hidProfileDetail: String? = null,
    val registrationStatus: HidRegistrationStatus = HidRegistrationStatus.UNREGISTERED,
    val registrationError: String? = null,
    val connectionStatus: HidConnectionStatus = HidConnectionStatus.DISCONNECTED,
    val connectedHostName: String? = null,
    val connectedHostAddress: String? = null,
    val pairedDevices: List<PairedDeviceInfo> = emptyList(),
    val protocolMode: String = "Report Protocol (Default)",
    val sensitivity: Float = 1.25f,
    val activeButtonMask: Int = HidMouseDescriptor.BUTTON_NONE,
    val lastReportSummary: String = "No HID report sent yet",
    val lastSendSucceeded: Boolean? = null,
    val reportsSentCount: Long = 0L,
    val lastErrorMessage: String? = null,
    val eventLog: List<DiagnosticLogEntry> = emptyList()
) {
    val isBluetoothEnabled: Boolean
        get() = adapterAvailable && adapterState == BluetoothAdapterState.ON

    val isHidProxyReady: Boolean
        get() = hidProfileStatus == HidProfileStatus.AVAILABLE

    val isRegistered: Boolean
        get() = registrationStatus == HidRegistrationStatus.REGISTERED

    val isHostConnected: Boolean
        get() = isRegistered &&
            connectionStatus == HidConnectionStatus.CONNECTED &&
            connectedHostAddress != null
}

package com.airmouse.hidtest.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.airmouse.hidtest.permissions.BluetoothPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Genuine native Android Bluetooth HID Device controller backed by [BluetoothHidDevice].
 *
 * Key engineering invariants:
 * - Explicitly uses [BluetoothProfile.HID_DEVICE] (NOT [BluetoothProfile.HID_HOST]).
 * - Detects actual runtime HID Device profile support (Android 14+ does not guarantee OEM support).
 * - Never simulates Bluetooth state or reports a paired/bonded device as connected.
 * - Guards against duplicate registration, null proxies, SecurityException, and Bluetooth power-off.
 */
class BluetoothHidManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hidCallbackExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AirMouse-HID-Callback").apply { isDaemon = true }
    }

    private val _state = MutableStateFlow(HidUiState())
    val state: StateFlow<HidUiState> = _state.asStateFlow()

    @Volatile
    private var hidDeviceProxy: BluetoothHidDevice? = null

    @Volatile
    private var connectedDevice: BluetoothDevice? = null

    @Volatile
    private var lastReportPayload: ByteArray = HidMouseDescriptor.buildMouseReport(HidMouseDescriptor.BUTTON_NONE)

    private var proxyWatchdogJob: Job? = null
    private var isReceiverRegistered: Boolean = false
    private var isReleased: Boolean = false

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private val bluetoothBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isReleased || intent == null) return
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    handleAdapterStateChanged(btState)
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                    val bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    val prevBondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    logEvent(
                        LogLevel.INFO,
                        "Bond state changed (${device?.address ?: "unknown"}): $prevBondState -> $bondState"
                    )
                    refreshPairedDevices()
                }
            }
        }
    }

    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (isReleased) {
                if (proxy != null && profile == BluetoothProfile.HID_DEVICE) {
                    runCatching { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy) }
                }
                return
            }
            if (profile != BluetoothProfile.HID_DEVICE) return

            proxyWatchdogJob?.cancel()
            proxyWatchdogJob = null

            val hidProxy = proxy as? BluetoothHidDevice
            if (hidProxy == null) {
                hidDeviceProxy = null
                _state.update {
                    it.copy(
                        hidProfileStatus = HidProfileStatus.UNAVAILABLE,
                        hidProfileDetail = "onServiceConnected returned a null or non-BluetoothHidDevice proxy.",
                        lastErrorMessage = "HID Device proxy was null."
                    )
                }
                logEvent(LogLevel.ERROR, "onServiceConnected(HID_DEVICE) returned null/unexpected proxy")
                return
            }

            hidDeviceProxy = hidProxy
            _state.update {
                it.copy(
                    hidProfileStatus = HidProfileStatus.AVAILABLE,
                    hidProfileDetail = "BluetoothHidDevice service proxy acquired (BluetoothProfile.HID_DEVICE).",
                    lastErrorMessage = null
                )
            }
            logEvent(LogLevel.SUCCESS, "BluetoothHidDevice profile proxy acquired")
            refreshPairedDevices()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            proxyWatchdogJob?.cancel()
            proxyWatchdogJob = null
            hidDeviceProxy = null
            connectedDevice = null

            _state.update {
                it.copy(
                    hidProfileStatus = HidProfileStatus.DISCONNECTED,
                    hidProfileDetail = "BluetoothHidDevice service disconnected by Android Bluetooth stack.",
                    registrationStatus = HidRegistrationStatus.UNREGISTERED,
                    connectionStatus = HidConnectionStatus.DISCONNECTED,
                    connectedHostName = null,
                    connectedHostAddress = null,
                    activeButtonMask = HidMouseDescriptor.BUTTON_NONE
                )
            }
            logEvent(LogLevel.WARN, "BluetoothHidDevice service disconnected")
            refreshPairedDevices()
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (isReleased) return
            val pluggedLabel = safeDeviceLabel(pluggedDevice)
            logEvent(
                if (registered) LogLevel.SUCCESS else LogLevel.INFO,
                "onAppStatusChanged(registered=$registered, pluggedDevice=$pluggedLabel)"
            )

            if (registered) {
                // Only treat pluggedDevice as connected if BluetoothHidDevice confirms STATE_CONNECTED.
                val confirmedHost = resolveConfirmedConnectedHost(pluggedDevice)
                connectedDevice = confirmedHost

                _state.update { current ->
                    current.copy(
                        registrationStatus = HidRegistrationStatus.REGISTERED,
                        registrationError = null,
                        lastErrorMessage = null,
                        connectionStatus = if (confirmedHost != null) {
                            HidConnectionStatus.CONNECTED
                        } else {
                            HidConnectionStatus.DISCONNECTED
                        },
                        connectedHostName = confirmedHost?.let { safeDeviceName(it) },
                        connectedHostAddress = confirmedHost?.address
                    )
                }
            } else {
                val wasRegistering = _state.value.registrationStatus == HidRegistrationStatus.REGISTERING
                connectedDevice = null
                _state.update { current ->
                    current.copy(
                        registrationStatus = if (wasRegistering) {
                            HidRegistrationStatus.FAILED
                        } else {
                            HidRegistrationStatus.UNREGISTERED
                        },
                        registrationError = if (wasRegistering) {
                            "Bluetooth stack rejected HID registration (onAppStatusChanged registered=false)."
                        } else {
                            null
                        },
                        lastErrorMessage = if (wasRegistering) {
                            "HID app registration failed in Bluetooth stack."
                        } else {
                            current.lastErrorMessage
                        },
                        connectionStatus = HidConnectionStatus.DISCONNECTED,
                        connectedHostName = null,
                        connectedHostAddress = null,
                        activeButtonMask = HidMouseDescriptor.BUTTON_NONE
                    )
                }
            }
            refreshPairedDevices()
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (isReleased) return
            val label = safeDeviceLabel(device)
            val mappedStatus = when (state) {
                BluetoothProfile.STATE_CONNECTED -> HidConnectionStatus.CONNECTED
                BluetoothProfile.STATE_CONNECTING -> HidConnectionStatus.CONNECTING
                BluetoothProfile.STATE_DISCONNECTING -> HidConnectionStatus.DISCONNECTING
                else -> HidConnectionStatus.DISCONNECTED
            }

            logEvent(
                when (mappedStatus) {
                    HidConnectionStatus.CONNECTED -> LogLevel.SUCCESS
                    HidConnectionStatus.DISCONNECTED -> LogLevel.WARN
                    else -> LogLevel.INFO
                },
                "onConnectionStateChanged($label -> ${mappedStatus.label})"
            )

            when (mappedStatus) {
                HidConnectionStatus.CONNECTED -> {
                    connectedDevice = device
                    _state.update {
                        it.copy(
                            connectionStatus = HidConnectionStatus.CONNECTED,
                            connectedHostName = device?.let { d -> safeDeviceName(d) },
                            connectedHostAddress = device?.address,
                            lastErrorMessage = null
                        )
                    }
                }

                HidConnectionStatus.CONNECTING -> {
                    _state.update {
                        it.copy(
                            connectionStatus = HidConnectionStatus.CONNECTING,
                            lastErrorMessage = null
                        )
                    }
                }

                HidConnectionStatus.DISCONNECTING -> {
                    _state.update {
                        it.copy(connectionStatus = HidConnectionStatus.DISCONNECTING)
                    }
                }

                HidConnectionStatus.DISCONNECTED -> {
                    if (connectedDevice == null || device == null || connectedDevice?.address == device.address) {
                        connectedDevice = null
                        _state.update {
                            it.copy(
                                connectionStatus = HidConnectionStatus.DISCONNECTED,
                                connectedHostName = null,
                                connectedHostAddress = null,
                                activeButtonMask = HidMouseDescriptor.BUTTON_NONE
                            )
                        }
                    }
                }
            }
            refreshPairedDevices()
        }

        @SuppressLint("MissingPermission")
        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (isReleased || device == null) return
            val proxy = hidDeviceProxy ?: return
            if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) return

            logEvent(LogLevel.INFO, "onGetReport(type=$type, id=$id, bufferSize=$bufferSize)")
            try {
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT &&
                    id.toInt() == HidMouseDescriptor.REPORT_ID_MOUSE
                ) {
                    proxy.replyReport(device, type, id, lastReportPayload)
                } else {
                    proxy.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
                }
            } catch (se: SecurityException) {
                logEvent(LogLevel.ERROR, "SecurityException replying to GET_REPORT: ${se.message}")
            } catch (e: Exception) {
                logEvent(LogLevel.ERROR, "Failed replying to GET_REPORT: ${e.message}")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            if (isReleased || device == null) return
            val proxy = hidDeviceProxy ?: return
            if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) return

            logEvent(LogLevel.INFO, "onSetReport(type=$type, id=$id, bytes=${data?.size ?: 0})")
            try {
                proxy.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
            } catch (se: SecurityException) {
                logEvent(LogLevel.ERROR, "SecurityException replying to SET_REPORT: ${se.message}")
            } catch (e: Exception) {
                logEvent(LogLevel.ERROR, "Failed replying to SET_REPORT: ${e.message}")
            }
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            if (isReleased) return
            val modeLabel = when (protocol) {
                BluetoothHidDevice.PROTOCOL_BOOT_MODE -> "Boot Protocol (0x00)"
                BluetoothHidDevice.PROTOCOL_REPORT_MODE -> "Report Protocol (0x01)"
                else -> "Protocol 0x%02X".format(protocol.toInt() and 0xFF)
            }
            _state.update { it.copy(protocolMode = modeLabel) }
            logEvent(LogLevel.INFO, "Host selected $modeLabel")
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            if (isReleased) return
            logEvent(
                LogLevel.INFO,
                "onInterruptData(reportId=$reportId, bytes=${data?.size ?: 0})"
            )
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            if (isReleased) return
            logEvent(LogLevel.WARN, "onVirtualCableUnplug(${safeDeviceLabel(device)})")
            if (device == null || connectedDevice?.address == device.address) {
                connectedDevice = null
                _state.update {
                    it.copy(
                        connectionStatus = HidConnectionStatus.DISCONNECTED,
                        connectedHostName = null,
                        connectedHostAddress = null,
                        activeButtonMask = HidMouseDescriptor.BUTTON_NONE
                    )
                }
            }
            refreshPairedDevices()
        }
    }

    init {
        registerSystemReceiver()
        refreshEnvironmentAndPermissions(autoProbeHid = true)
    }

    private fun registerSystemReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        try {
            ContextCompat.registerReceiver(
                appContext,
                bluetoothBroadcastReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            logEvent(LogLevel.ERROR, "Failed to register Bluetooth BroadcastReceiver: ${e.message}")
        }
    }

    fun refreshEnvironmentAndPermissions(autoProbeHid: Boolean = true) {
        if (isReleased) return

        val hasConnect = BluetoothPermissionHelper.hasConnectPermission(appContext)
        val hasAdvertise = BluetoothPermissionHelper.hasAdvertisePermission(appContext)
        val adapter = bluetoothAdapter
        val adapterAvailable = adapter != null
        val mappedAdapterState = when {
            adapter == null -> BluetoothAdapterState.UNAVAILABLE
            adapter.isEnabled -> BluetoothAdapterState.ON
            else -> BluetoothAdapterState.OFF
        }

        val rationale = when {
            !hasConnect && !hasAdvertise ->
                "BLUETOOTH_CONNECT and BLUETOOTH_ADVERTISE are required on Android 14+ to acquire the HID Device profile and allow hosts to discover this phone."
            !hasConnect ->
                "BLUETOOTH_CONNECT is required to access BluetoothHidDevice, paired hosts, and mouse HID report transmission."
            !hasAdvertise ->
                "BLUETOOTH_ADVERTISE is denied. HID mouse control still works with already-paired hosts, but making this phone discoverable requires BLUETOOTH_ADVERTISE."
            else -> null
        }

        _state.update { current ->
            val updatedHidStatus = when {
                !adapterAvailable -> HidProfileStatus.UNAVAILABLE
                !hasConnect -> HidProfileStatus.PERMISSION_REQUIRED
                mappedAdapterState != BluetoothAdapterState.ON -> HidProfileStatus.BLUETOOTH_OFF
                hidDeviceProxy != null -> HidProfileStatus.AVAILABLE
                else -> current.hidProfileStatus
            }
            val updatedHidDetail = when {
                !adapterAvailable -> "BluetoothAdapter is null; this device does not expose Bluetooth hardware."
                !hasConnect -> "Grant BLUETOOTH_CONNECT to probe BluetoothProfile.HID_DEVICE."
                mappedAdapterState != BluetoothAdapterState.ON -> "Enable Bluetooth to bind BluetoothHidDevice."
                else -> current.hidProfileDetail
            }
            current.copy(
                hasConnectPermission = hasConnect,
                hasAdvertisePermission = hasAdvertise,
                permissionRationaleMessage = rationale,
                adapterAvailable = adapterAvailable,
                adapterState = mappedAdapterState,
                hidProfileStatus = updatedHidStatus,
                hidProfileDetail = updatedHidDetail
            )
        }

        if (hasConnect && adapterAvailable && mappedAdapterState == BluetoothAdapterState.ON) {
            refreshPairedDevices()
            if (autoProbeHid && hidDeviceProxy == null) {
                probeHidDeviceProfile()
            }
        }
    }

    /**
     * Explicitly acquires [BluetoothProfile.HID_DEVICE] (never HID_HOST) and monitors binding with a watchdog
     * so devices whose OEM firmware disables BluetoothHidDeviceService are accurately reported as UNAVAILABLE.
     */
    fun probeHidDeviceProfile() {
        if (isReleased) return

        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.update {
                it.copy(
                    adapterAvailable = false,
                    adapterState = BluetoothAdapterState.UNAVAILABLE,
                    hidProfileStatus = HidProfileStatus.UNAVAILABLE,
                    hidProfileDetail = "No Bluetooth adapter found on this device.",
                    lastErrorMessage = "Bluetooth hardware unavailable."
                )
            }
            logEvent(LogLevel.ERROR, "probeHidDeviceProfile aborted: BluetoothAdapter is null")
            return
        }

        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            _state.update {
                it.copy(
                    hidProfileStatus = HidProfileStatus.PERMISSION_REQUIRED,
                    hidProfileDetail = "BLUETOOTH_CONNECT permission is required before calling getProfileProxy(HID_DEVICE).",
                    lastErrorMessage = "Grant BLUETOOTH_CONNECT permission first."
                )
            }
            logEvent(LogLevel.WARN, "probeHidDeviceProfile blocked: BLUETOOTH_CONNECT not granted")
            return
        }

        if (!adapter.isEnabled) {
            _state.update {
                it.copy(
                    adapterState = BluetoothAdapterState.OFF,
                    hidProfileStatus = HidProfileStatus.BLUETOOTH_OFF,
                    hidProfileDetail = "Turn on Bluetooth to acquire BluetoothHidDevice.",
                    lastErrorMessage = "Bluetooth is turned off."
                )
            }
            logEvent(LogLevel.WARN, "probeHidDeviceProfile blocked: Bluetooth is disabled")
            return
        }

        if (hidDeviceProxy != null) {
            _state.update {
                it.copy(
                    hidProfileStatus = HidProfileStatus.AVAILABLE,
                    hidProfileDetail = "BluetoothHidDevice profile proxy is already connected."
                )
            }
            return
        }

        proxyWatchdogJob?.cancel()
        _state.update {
            it.copy(
                hidProfileStatus = HidProfileStatus.PROBING,
                hidProfileDetail = "Binding BluetoothProfile.HID_DEVICE via BluetoothAdapter.getProfileProxy()...",
                lastErrorMessage = null
            )
        }
        logEvent(LogLevel.INFO, "Requesting BluetoothProfile.HID_DEVICE proxy...")

        val requested = try {
            adapter.getProfileProxy(appContext, profileServiceListener, BluetoothProfile.HID_DEVICE)
        } catch (se: SecurityException) {
            logEvent(LogLevel.ERROR, "SecurityException calling getProfileProxy(HID_DEVICE): ${se.message}")
            _state.update {
                it.copy(
                    hidProfileStatus = HidProfileStatus.PERMISSION_REQUIRED,
                    hidProfileDetail = "SecurityException: ${se.message}",
                    lastErrorMessage = "Permission denied while acquiring HID Device profile."
                )
            }
            return
        } catch (e: Exception) {
            logEvent(LogLevel.ERROR, "Exception calling getProfileProxy(HID_DEVICE): ${e.message}")
            _state.update {
                it.copy(
                    hidProfileStatus = HidProfileStatus.UNAVAILABLE,
                    hidProfileDetail = "getProfileProxy threw ${e.javaClass.simpleName}: ${e.message}",
                    lastErrorMessage = "Failed to acquire HID Device profile: ${e.message}"
                )
            }
            return
        }

        if (!requested) {
            _state.update {
                it.copy(
                    hidProfileStatus = HidProfileStatus.UNAVAILABLE,
                    hidProfileDetail = "BluetoothAdapter.getProfileProxy(HID_DEVICE) returned false. This device or Android build does not expose the Bluetooth HID Device service.",
                    lastErrorMessage = "Bluetooth HID Device profile is unavailable on this device."
                )
            }
            logEvent(LogLevel.ERROR, "getProfileProxy(HID_DEVICE) returned false")
            return
        }

        proxyWatchdogJob = scope.launch {
            delay(PROFILE_BIND_TIMEOUT_MS)
            if (hidDeviceProxy == null && _state.value.hidProfileStatus == HidProfileStatus.PROBING) {
                _state.update {
                    it.copy(
                        hidProfileStatus = HidProfileStatus.UNAVAILABLE,
                        hidProfileDetail = "Timed out after ${PROFILE_BIND_TIMEOUT_MS / 1000}s waiting for onServiceConnected(HID_DEVICE). The OEM Bluetooth stack may have disabled BluetoothHidDeviceService.",
                        lastErrorMessage = "HID Device service did not respond (possibly disabled by OEM firmware)."
                    )
                }
                logEvent(LogLevel.ERROR, "HID_DEVICE profile proxy timed out after ${PROFILE_BIND_TIMEOUT_MS}ms")
            }
        }
    }

    /**
     * Registers this app as a genuine Bluetooth HID Mouse using [BluetoothHidDevice.registerApp].
     * Prevents duplicate registration when already REGISTERING or REGISTERED.
     */
    @SuppressLint("MissingPermission")
    fun registerHidApp() {
        if (isReleased) return

        val current = _state.value
        if (current.registrationStatus == HidRegistrationStatus.REGISTERED) {
            logEvent(LogLevel.WARN, "Ignored duplicate registerHidApp(): already registered")
            return
        }
        if (current.registrationStatus == HidRegistrationStatus.REGISTERING) {
            logEvent(LogLevel.WARN, "Ignored duplicate registerHidApp(): registration already in progress")
            return
        }

        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            setRegistrationFailure("BLUETOOTH_CONNECT permission is required to register HID app.")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            setRegistrationFailure("Bluetooth must be enabled before registering the HID mouse.")
            return
        }

        val proxy = hidDeviceProxy
        if (proxy == null) {
            setRegistrationFailure("BluetoothHidDevice proxy is null. Acquire HID profile first.")
            probeHidDeviceProfile()
            return
        }

        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "AirMouse HID",
            "AirMouse Bluetooth HID Mouse",
            "AirMouse",
            BluetoothHidDevice.SUBCLASS1_MOUSE,
            HidMouseDescriptor.MOUSE_REPORT_DESCRIPTOR
        )

        _state.update {
            it.copy(
                registrationStatus = HidRegistrationStatus.REGISTERING,
                registrationError = null,
                lastErrorMessage = null
            )
        }
        logEvent(
            LogLevel.INFO,
            "Calling BluetoothHidDevice.registerApp() with ${HidMouseDescriptor.MOUSE_REPORT_DESCRIPTOR.size}-byte mouse descriptor"
        )

        try {
            // Passing null for inQos and outQos uses Android's default QoS settings, maximizing
            // interoperability with Windows, macOS, Linux BlueZ, ChromeOS, and Android hosts.
            val started = proxy.registerApp(
                sdpSettings,
                null,
                null,
                hidCallbackExecutor,
                hidCallback
            )
            if (!started) {
                setRegistrationFailure(
                    "BluetoothHidDevice.registerApp() returned false. Another HID Device application may already be registered, or the Bluetooth stack rejected registration."
                )
            }
        } catch (se: SecurityException) {
            setRegistrationFailure("SecurityException during registerApp(): ${se.message}")
        } catch (e: Exception) {
            setRegistrationFailure("registerApp() threw ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun unregisterHidApp() {
        if (isReleased) return

        val proxy = hidDeviceProxy
        if (proxy == null) {
            _state.update {
                it.copy(
                    registrationStatus = HidRegistrationStatus.UNREGISTERED,
                    connectionStatus = HidConnectionStatus.DISCONNECTED,
                    connectedHostName = null,
                    connectedHostAddress = null,
                    activeButtonMask = HidMouseDescriptor.BUTTON_NONE
                )
            }
            return
        }

        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            _state.update {
                it.copy(lastErrorMessage = "BLUETOOTH_CONNECT permission required to unregister HID app.")
            }
            return
        }

        // Release any held mouse buttons before unregistering so host does not stay stuck.
        if (_state.value.activeButtonMask != HidMouseDescriptor.BUTTON_NONE) {
            releaseAllButtons()
        }

        try {
            val result = proxy.unregisterApp()
            logEvent(LogLevel.INFO, "unregisterApp() returned $result")
            if (!result) {
                _state.update {
                    it.copy(
                        registrationStatus = HidRegistrationStatus.UNREGISTERED,
                        connectionStatus = HidConnectionStatus.DISCONNECTED,
                        connectedHostName = null,
                        connectedHostAddress = null,
                        activeButtonMask = HidMouseDescriptor.BUTTON_NONE
                    )
                }
            }
        } catch (se: SecurityException) {
            logEvent(LogLevel.ERROR, "SecurityException calling unregisterApp(): ${se.message}")
            _state.update { it.copy(lastErrorMessage = "Permission denied while unregistering HID app.") }
        } catch (e: Exception) {
            logEvent(LogLevel.ERROR, "Exception calling unregisterApp(): ${e.message}")
            _state.update { it.copy(lastErrorMessage = "Error unregistering HID app: ${e.message}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToPairedHost(address: String) {
        if (isReleased) return

        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            _state.update { it.copy(lastErrorMessage = "BLUETOOTH_CONNECT permission is required to connect.") }
            return
        }

        val proxy = hidDeviceProxy
        if (proxy == null) {
            _state.update { it.copy(lastErrorMessage = "BluetoothHidDevice proxy is unavailable.") }
            return
        }

        if (!_state.value.isRegistered) {
            _state.update { it.copy(lastErrorMessage = "Register the HID Mouse application before connecting to a host.") }
            return
        }

        val targetDevice = try {
            bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address }
                ?: bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: Exception) {
            logEvent(LogLevel.ERROR, "Failed to resolve BluetoothDevice $address: ${e.message}")
            null
        }

        if (targetDevice == null) {
            _state.update { it.copy(lastErrorMessage = "Could not find paired Bluetooth device $address.") }
            return
        }

        val label = safeDeviceLabel(targetDevice)
        logEvent(LogLevel.INFO, "Calling hidDevice.connect($label)...")
        _state.update {
            it.copy(
                connectionStatus = HidConnectionStatus.CONNECTING,
                lastErrorMessage = null
            )
        }

        try {
            val initiated = proxy.connect(targetDevice)
            if (!initiated) {
                logEvent(LogLevel.ERROR, "hidDevice.connect($label) returned false")
                _state.update {
                    it.copy(
                        connectionStatus = if (connectedDevice != null) {
                            HidConnectionStatus.CONNECTED
                        } else {
                            HidConnectionStatus.DISCONNECTED
                        },
                        lastErrorMessage = "connect() returned false for $label. Ensure the host has Bluetooth enabled and supports HID Mouse input, or initiate connection from the host."
                    )
                }
            }
        } catch (se: SecurityException) {
            logEvent(LogLevel.ERROR, "SecurityException calling connect(): ${se.message}")
            _state.update {
                it.copy(
                    connectionStatus = HidConnectionStatus.DISCONNECTED,
                    lastErrorMessage = "Permission denied while connecting to host."
                )
            }
        } catch (e: Exception) {
            logEvent(LogLevel.ERROR, "Exception calling connect(): ${e.message}")
            _state.update {
                it.copy(
                    connectionStatus = HidConnectionStatus.DISCONNECTED,
                    lastErrorMessage = "Connection failed: ${e.message}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectHost() {
        if (isReleased) return

        val proxy = hidDeviceProxy
        val host = connectedDevice
        if (proxy == null || host == null) {
            _state.update {
                it.copy(
                    connectionStatus = HidConnectionStatus.DISCONNECTED,
                    connectedHostName = null,
                    connectedHostAddress = null,
                    activeButtonMask = HidMouseDescriptor.BUTTON_NONE
                )
            }
            return
        }

        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            _state.update { it.copy(lastErrorMessage = "BLUETOOTH_CONNECT permission required to disconnect.") }
            return
        }

        if (_state.value.activeButtonMask != HidMouseDescriptor.BUTTON_NONE) {
            releaseAllButtons()
        }

        val label = safeDeviceLabel(host)
        logEvent(LogLevel.INFO, "Calling hidDevice.disconnect($label)...")
        try {
            val initiated = proxy.disconnect(host)
            if (initiated) {
                _state.update { it.copy(connectionStatus = HidConnectionStatus.DISCONNECTING) }
            } else {
                logEvent(LogLevel.WARN, "hidDevice.disconnect($label) returned false")
                _state.update { it.copy(lastErrorMessage = "disconnect() returned false for $label.") }
            }
        } catch (se: SecurityException) {
            logEvent(LogLevel.ERROR, "SecurityException calling disconnect(): ${se.message}")
            _state.update { it.copy(lastErrorMessage = "Permission denied while disconnecting.") }
        } catch (e: Exception) {
            logEvent(LogLevel.ERROR, "Exception calling disconnect(): ${e.message}")
            _state.update { it.copy(lastErrorMessage = "Disconnect failed: ${e.message}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (isReleased) return

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled || !BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            _state.update { it.copy(pairedDevices = emptyList()) }
            return
        }

        try {
            val proxy = hidDeviceProxy
            val bonded = adapter.bondedDevices.orEmpty()
            val list = bonded.map { device ->
                val isActuallyConnected = proxy != null &&
                    _state.value.isRegistered &&
                    runCatching {
                        proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
                    }.getOrDefault(false)

                PairedDeviceInfo(
                    name = safeDeviceName(device),
                    address = device.address,
                    isConnectedHidHost = isActuallyConnected
                )
            }.sortedWith(
                compareByDescending<PairedDeviceInfo> { it.isConnectedHidHost }
                    .thenBy { it.name.lowercase() }
            )
            _state.update { it.copy(pairedDevices = list) }
        } catch (se: SecurityException) {
            logEvent(LogLevel.WARN, "Cannot read bondedDevices: ${se.message}")
            _state.update { it.copy(pairedDevices = emptyList()) }
        } catch (e: Exception) {
            logEvent(LogLevel.ERROR, "Error querying bondedDevices: ${e.message}")
        }
    }

    fun setSensitivity(value: Float) {
        val clamped = value.coerceIn(0.4f, 3.0f)
        _state.update { it.copy(sensitivity = clamped) }
    }

    fun sendMouseMovement(deltaX: Int, deltaY: Int, wheel: Int = 0) {
        if (deltaX == 0 && deltaY == 0 && wheel == 0) return
        val buttonMask = _state.value.activeButtonMask
        val packets = HidMouseDescriptor.buildChunkedMouseReports(
            buttonMask = buttonMask,
            totalDeltaX = deltaX,
            totalDeltaY = deltaY,
            totalWheel = wheel
        )
        for (packet in packets) {
            sendRawReport(packet)
        }
    }

    fun setMouseButtonPressed(buttonBit: Int, pressed: Boolean) {
        val currentMask = _state.value.activeButtonMask
        val nextMask = if (pressed) {
            currentMask or (buttonBit and HidMouseDescriptor.BUTTON_VALID_MASK)
        } else {
            currentMask and (buttonBit and HidMouseDescriptor.BUTTON_VALID_MASK).inv()
        }
        if (nextMask == currentMask) return

        _state.update { it.copy(activeButtonMask = nextMask) }
        val report = HidMouseDescriptor.buildMouseReport(buttonMask = nextMask)
        sendRawReport(report)
    }

    fun releaseAllButtons() {
        if (_state.value.activeButtonMask == HidMouseDescriptor.BUTTON_NONE) return
        _state.update { it.copy(activeButtonMask = HidMouseDescriptor.BUTTON_NONE) }
        val report = HidMouseDescriptor.buildMouseReport(buttonMask = HidMouseDescriptor.BUTTON_NONE)
        sendRawReport(report)
    }

    @SuppressLint("MissingPermission")
    private fun sendRawReport(payload: ByteArray) {
        if (isReleased) return
        lastReportPayload = payload

        val summary = HidMouseDescriptor.formatReportHex(payload)
        val proxy = hidDeviceProxy
        val host = connectedDevice

        if (proxy == null || host == null || !_state.value.isHostConnected) {
            _state.update {
                it.copy(
                    lastReportSummary = "$summary (not sent — no connected host)",
                    lastSendSucceeded = false
                )
            }
            return
        }

        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            _state.update {
                it.copy(
                    lastReportSummary = "$summary (permission denied)",
                    lastSendSucceeded = false,
                    lastErrorMessage = "BLUETOOTH_CONNECT permission is required to send HID reports."
                )
            }
            return
        }

        try {
            val sent = proxy.sendReport(host, HidMouseDescriptor.REPORT_ID_MOUSE, payload)
            _state.update { current ->
                current.copy(
                    lastReportSummary = summary,
                    lastSendSucceeded = sent,
                    reportsSentCount = if (sent) current.reportsSentCount + 1 else current.reportsSentCount,
                    lastErrorMessage = if (!sent) {
                        "BluetoothHidDevice.sendReport() returned false."
                    } else {
                        current.lastErrorMessage
                    }
                )
            }
        } catch (se: SecurityException) {
            logEvent(LogLevel.ERROR, "SecurityException in sendReport(): ${se.message}")
            _state.update {
                it.copy(
                    lastReportSummary = summary,
                    lastSendSucceeded = false,
                    lastErrorMessage = "Permission denied calling sendReport()."
                )
            }
        } catch (e: Exception) {
            logEvent(LogLevel.ERROR, "Exception in sendReport(): ${e.message}")
            _state.update {
                it.copy(
                    lastReportSummary = summary,
                    lastSendSucceeded = false,
                    lastErrorMessage = "sendReport() error: ${e.message}"
                )
            }
        }
    }

    fun clearErrorMessage() {
        _state.update { it.copy(lastErrorMessage = null) }
    }

    fun release() {
        if (isReleased) return
        isReleased = true

        proxyWatchdogJob?.cancel()
        proxyWatchdogJob = null

        val proxy = hidDeviceProxy
        val adapter = bluetoothAdapter
        if (proxy != null) {
            try {
                if (BluetoothPermissionHelper.hasConnectPermission(appContext)) {
                    connectedDevice?.let { host ->
                        if (_state.value.activeButtonMask != HidMouseDescriptor.BUTTON_NONE) {
                            runCatching {
                                proxy.sendReport(
                                    host,
                                    HidMouseDescriptor.REPORT_ID_MOUSE,
                                    HidMouseDescriptor.buildMouseReport(HidMouseDescriptor.BUTTON_NONE)
                                )
                            }
                        }
                    }
                    if (_state.value.isRegistered) {
                        runCatching { proxy.unregisterApp() }
                    }
                }
            } catch (_: Exception) {
                // Best-effort cleanup during teardown.
            } finally {
                runCatching {
                    adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
                }
            }
        }

        hidDeviceProxy = null
        connectedDevice = null

        if (isReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(bluetoothBroadcastReceiver) }
            isReceiverRegistered = false
        }

        hidCallbackExecutor.shutdownNow()
        scope.cancel()
    }

    private fun handleAdapterStateChanged(btState: Int) {
        val mapped = when (btState) {
            BluetoothAdapter.STATE_ON -> BluetoothAdapterState.ON
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterState.TURNING_ON
            BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterState.TURNING_OFF
            else -> BluetoothAdapterState.OFF
        }
        logEvent(
            if (mapped == BluetoothAdapterState.ON) LogLevel.SUCCESS else LogLevel.WARN,
            "Bluetooth adapter state -> ${mapped.label}"
        )

        if (mapped == BluetoothAdapterState.OFF || mapped == BluetoothAdapterState.TURNING_OFF) {
            proxyWatchdogJob?.cancel()
            proxyWatchdogJob = null

            val existingProxy = hidDeviceProxy
            hidDeviceProxy = null
            connectedDevice = null
            if (existingProxy != null) {
                runCatching {
                    bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, existingProxy)
                }
            }

            _state.update {
                it.copy(
                    adapterState = mapped,
                    hidProfileStatus = HidProfileStatus.BLUETOOTH_OFF,
                    hidProfileDetail = "Bluetooth was switched off during operation. Proxy and HID registration were reset cleanly.",
                    registrationStatus = HidRegistrationStatus.UNREGISTERED,
                    connectionStatus = HidConnectionStatus.DISCONNECTED,
                    connectedHostName = null,
                    connectedHostAddress = null,
                    pairedDevices = emptyList(),
                    activeButtonMask = HidMouseDescriptor.BUTTON_NONE
                )
            }
        } else {
            _state.update { it.copy(adapterState = mapped) }
            if (mapped == BluetoothAdapterState.ON) {
                refreshEnvironmentAndPermissions(autoProbeHid = true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveConfirmedConnectedHost(candidate: BluetoothDevice?): BluetoothDevice? {
        val proxy = hidDeviceProxy ?: return null
        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) return null
        return try {
            if (candidate != null &&
                proxy.getConnectionState(candidate) == BluetoothProfile.STATE_CONNECTED
            ) {
                candidate
            } else {
                proxy.connectedDevices.firstOrNull {
                    proxy.getConnectionState(it) == BluetoothProfile.STATE_CONNECTED
                }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun setRegistrationFailure(message: String) {
        logEvent(LogLevel.ERROR, message)
        _state.update {
            it.copy(
                registrationStatus = HidRegistrationStatus.FAILED,
                registrationError = message,
                lastErrorMessage = message
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        if (!BluetoothPermissionHelper.hasConnectPermission(appContext)) {
            return device.address
        }
        return try {
            device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Device"
        } catch (_: SecurityException) {
            device.address
        }
    }

    private fun safeDeviceLabel(device: BluetoothDevice?): String {
        if (device == null) return "none"
        return "${safeDeviceName(device)} [${device.address}]"
    }

    private fun logEvent(level: LogLevel, message: String) {
        when (level) {
            LogLevel.ERROR -> Log.e(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            else -> Log.i(TAG, message)
        }
        val entry = DiagnosticLogEntry(
            timestamp = LocalTime.now().format(timeFormatter),
            level = level,
            message = message
        )
        _state.update { current ->
            val updated = (listOf(entry) + current.eventLog).take(MAX_LOG_ENTRIES)
            current.copy(eventLog = updated)
        }
    }

    companion object {
        private const val TAG = "AirMouseHID"
        private const val PROFILE_BIND_TIMEOUT_MS = 5_000L
        private const val MAX_LOG_ENTRIES = 60
    }
}

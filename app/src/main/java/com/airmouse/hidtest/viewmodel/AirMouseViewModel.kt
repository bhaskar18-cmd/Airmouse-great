package com.airmouse.hidtest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.airmouse.hidtest.hid.BluetoothHidManager
import com.airmouse.hidtest.hid.HidMouseDescriptor
import com.airmouse.hidtest.hid.HidUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-aware ViewModel owning [BluetoothHidManager] so HID registration and host connections
 * survive screen rotations and configuration changes, and release resources cleanly in [onCleared].
 */
class AirMouseViewModel(application: Application) : AndroidViewModel(application) {

    private val hidManager = BluetoothHidManager(application)

    val uiState: StateFlow<HidUiState> = hidManager.state

    fun onPermissionsResult() {
        hidManager.refreshEnvironmentAndPermissions(autoProbeHid = true)
    }

    fun onResumeRefresh() {
        hidManager.refreshEnvironmentAndPermissions(autoProbeHid = true)
    }

    fun probeHidProfile() {
        hidManager.probeHidDeviceProfile()
    }

    fun registerHid() {
        hidManager.registerHidApp()
    }

    fun unregisterHid() {
        hidManager.unregisterHidApp()
    }

    fun connectHost(address: String) {
        hidManager.connectToPairedHost(address)
    }

    fun disconnectHost() {
        hidManager.disconnectHost()
    }

    fun refreshPairedDevices() {
        hidManager.refreshPairedDevices()
    }

    fun updateSensitivity(sensitivity: Float) {
        hidManager.setSensitivity(sensitivity)
    }

    fun sendMouseMove(deltaX: Int, deltaY: Int, wheel: Int = 0) {
        hidManager.sendMouseMovement(deltaX = deltaX, deltaY = deltaY, wheel = wheel)
    }

    fun setLeftButtonPressed(pressed: Boolean) {
        hidManager.setMouseButtonPressed(HidMouseDescriptor.BUTTON_LEFT, pressed)
    }

    fun setRightButtonPressed(pressed: Boolean) {
        hidManager.setMouseButtonPressed(HidMouseDescriptor.BUTTON_RIGHT, pressed)
    }

    fun releaseAllButtons() {
        hidManager.releaseAllButtons()
    }

    fun dismissError() {
        hidManager.clearErrorMessage()
    }

    override fun onCleared() {
        hidManager.release()
        super.onCleared()
    }
}

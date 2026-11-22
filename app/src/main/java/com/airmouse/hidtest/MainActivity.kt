package com.airmouse.hidtest

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airmouse.hidtest.permissions.BluetoothPermissionHelper
import com.airmouse.hidtest.ui.AirMouseApp
import com.airmouse.hidtest.ui.theme.AirMouseTheme
import com.airmouse.hidtest.viewmodel.AirMouseViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AirMouseViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.onPermissionsResult()
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.onResumeRefresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val missing = BluetoothPermissionHelper.missingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing)
        }

        setContent {
            AirMouseTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                AirMouseApp(
                    state = state,
                    onRequestPermissions = ::requestBluetoothPermissions,
                    onOpenBluetoothSettings = ::openBluetoothSettings,
                    onOpenAppSettings = ::openAppSettings,
                    onMakeDiscoverable = ::requestDiscoverableMode,
                    onReprobeHid = viewModel::probeHidProfile,
                    onRegisterHid = viewModel::registerHid,
                    onUnregisterHid = viewModel::unregisterHid,
                    onConnectHost = viewModel::connectHost,
                    onDisconnectHost = viewModel::disconnectHost,
                    onRefreshDevices = viewModel::refreshPairedDevices,
                    onSensitivityChange = viewModel::updateSensitivity,
                    onMove = viewModel::sendMouseMove,
                    onLeftButtonChanged = viewModel::setLeftButtonPressed,
                    onRightButtonChanged = viewModel::setRightButtonPressed,
                    onReleaseAllButtons = viewModel::releaseAllButtons,
                    onDismissError = viewModel::dismissError
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumeRefresh()
    }

    private fun requestBluetoothPermissions() {
        val missing = BluetoothPermissionHelper.missingPermissions(this)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing)
        } else {
            viewModel.onPermissionsResult()
        }
    }

    private fun requestDiscoverableMode() {
        if (!BluetoothPermissionHelper.hasAdvertisePermission(this)) {
            requestBluetoothPermissions()
            return
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180)
        }
        runCatching {
            discoverableLauncher.launch(intent)
        }
    }

    private fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        runCatching {
            startActivity(intent)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        runCatching {
            startActivity(intent)
        }
    }
}

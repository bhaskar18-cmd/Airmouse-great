package com.airmouse.hidtest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airmouse.hidtest.hid.HidProfileStatus
import com.airmouse.hidtest.hid.HidUiState
import com.airmouse.hidtest.ui.screens.ConnectionScreen
import com.airmouse.hidtest.ui.screens.DiagnosticsScreen
import com.airmouse.hidtest.ui.screens.TouchpadScreen
import com.airmouse.hidtest.ui.theme.AirMouseColors

private enum class AirMouseTab(val label: String, val icon: ImageVector) {
    TOUCHPAD("Touchpad", Icons.Rounded.TouchApp),
    CONNECTION("Connection", Icons.Rounded.Devices),
    DIAGNOSTICS("Diagnostics", Icons.Rounded.DeveloperBoard)
}

@Composable
fun AirMouseApp(
    state: HidUiState,
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onReprobeHid: () -> Unit,
    onRegisterHid: () -> Unit,
    onUnregisterHid: () -> Unit,
    onConnectHost: (String) -> Unit,
    onDisconnectHost: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onMove: (deltaX: Int, deltaY: Int, wheel: Int) -> Unit,
    onLeftButtonChanged: (Boolean) -> Unit,
    onRightButtonChanged: (Boolean) -> Unit,
    onReleaseAllButtons: () -> Unit,
    onDismissError: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(AirMouseTab.TOUCHPAD) }

    Scaffold(
        containerColor = AirMouseColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(modifier = Modifier.background(AirMouseColors.Background)) {
                AirMouseHeader(state = state)
                state.lastErrorMessage?.let { errorText ->
                    ErrorBanner(
                        message = errorText,
                        onDismiss = onDismissError
                    )
                }
                HorizontalDivider(color = AirMouseColors.Border)
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = AirMouseColors.Border)
                NavigationBar(
                    containerColor = AirMouseColors.Surface,
                    tonalElevation = 0.dp
                ) {
                    AirMouseTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AirMouseColors.Accent,
                                selectedTextColor = AirMouseColors.Accent,
                                indicatorColor = AirMouseColors.AccentDim,
                                unselectedIconColor = AirMouseColors.TextMuted,
                                unselectedTextColor = AirMouseColors.TextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AirMouseTab.TOUCHPAD -> TouchpadScreen(
                    state = state,
                    onMove = onMove,
                    onLeftButtonChanged = onLeftButtonChanged,
                    onRightButtonChanged = onRightButtonChanged,
                    onReleaseAllButtons = onReleaseAllButtons,
                    onSensitivityChange = onSensitivityChange,
                    onRequestPermissions = onRequestPermissions,
                    onRegisterHid = onRegisterHid,
                    onOpenConnectionTab = { selectedTab = AirMouseTab.CONNECTION },
                    onDisconnect = onDisconnectHost
                )

                AirMouseTab.CONNECTION -> ConnectionScreen(
                    state = state,
                    onRequestPermissions = onRequestPermissions,
                    onRegisterHid = onRegisterHid,
                    onUnregisterHid = onUnregisterHid,
                    onMakeDiscoverable = onMakeDiscoverable,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onConnectHost = onConnectHost,
                    onDisconnectHost = onDisconnectHost,
                    onRefreshDevices = onRefreshDevices
                )

                AirMouseTab.DIAGNOSTICS -> DiagnosticsScreen(
                    state = state,
                    onReprobeHid = onReprobeHid,
                    onRequestPermissions = onRequestPermissions,
                    onOpenAppSettings = onOpenAppSettings
                )
            }
        }
    }
}

@Composable
private fun AirMouseHeader(state: HidUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AirMouse",
                style = MaterialTheme.typography.titleLarge,
                color = AirMouseColors.TextPrimary
            )
            Text(
                text = "Bluetooth HID Diagnostic",
                style = MaterialTheme.typography.bodySmall,
                color = AirMouseColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        BluetoothIndicatorBadge(state = state)
    }
}

@Composable
private fun BluetoothIndicatorBadge(state: HidUiState) {
    val (label, color, icon) = when {
        !state.adapterAvailable -> Triple("No BT", AirMouseColors.Error, Icons.Rounded.BluetoothDisabled)
        !state.hasConnectPermission -> Triple("Perm Needed", AirMouseColors.Error, Icons.Rounded.BluetoothDisabled)
        !state.isBluetoothEnabled -> Triple("BT Off", AirMouseColors.Error, Icons.Rounded.BluetoothDisabled)
        state.hidProfileStatus == HidProfileStatus.UNAVAILABLE -> Triple(
            "HID Unavail",
            AirMouseColors.Error,
            Icons.Rounded.BluetoothDisabled
        )
        state.isHostConnected -> Triple(
            "Connected",
            AirMouseColors.Success,
            Icons.Rounded.BluetoothConnected
        )
        state.isRegistered -> Triple("Registered", AirMouseColors.Accent, Icons.Rounded.Bluetooth)
        state.isHidProxyReady -> Triple("HID Ready", AirMouseColors.Warning, Icons.Rounded.Bluetooth)
        else -> Triple("Probing...", AirMouseColors.Warning, Icons.Rounded.Bluetooth)
    }

    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        color = AirMouseColors.ErrorDim,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AirMouseColors.Error.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = AirMouseColors.Error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFECACA),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss error",
                    tint = AirMouseColors.Error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

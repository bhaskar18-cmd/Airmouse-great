package com.airmouse.hidtest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airmouse.hidtest.hid.HidConnectionStatus
import com.airmouse.hidtest.hid.HidProfileStatus
import com.airmouse.hidtest.hid.HidRegistrationStatus
import com.airmouse.hidtest.hid.HidUiState
import com.airmouse.hidtest.hid.PairedDeviceInfo
import com.airmouse.hidtest.ui.theme.AirMouseColors

@Composable
fun ConnectionScreen(
    state: HidUiState,
    onRequestPermissions: () -> Unit,
    onRegisterHid: () -> Unit,
    onUnregisterHid: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onConnectHost: (address: String) -> Unit,
    onDisconnectHost: () -> Unit,
    onRefreshDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Real Bluetooth + HID status summary card
        item {
            Surface(
                color = AirMouseColors.Surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AirMouseColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "HID Connection Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = AirMouseColors.TextPrimary
                    )

                    StatusRow(
                        label = "Bluetooth",
                        value = state.adapterState.label,
                        valueColor = if (state.isBluetoothEnabled) AirMouseColors.Success else AirMouseColors.Error
                    )
                    StatusRow(
                        label = "HID Device Profile",
                        value = state.hidProfileStatus.label,
                        valueColor = when (state.hidProfileStatus) {
                            HidProfileStatus.AVAILABLE -> AirMouseColors.Success
                            HidProfileStatus.PROBING -> AirMouseColors.Warning
                            else -> AirMouseColors.Error
                        }
                    )
                    StatusRow(
                        label = "HID Registration",
                        value = state.registrationStatus.label,
                        valueColor = when (state.registrationStatus) {
                            HidRegistrationStatus.REGISTERED -> AirMouseColors.Success
                            HidRegistrationStatus.REGISTERING -> AirMouseColors.Warning
                            HidRegistrationStatus.FAILED -> AirMouseColors.Error
                            HidRegistrationStatus.UNREGISTERED -> AirMouseColors.TextSecondary
                        }
                    )
                    StatusRow(
                        label = "Host Connection",
                        value = state.connectionStatus.label,
                        valueColor = when (state.connectionStatus) {
                            HidConnectionStatus.CONNECTED -> AirMouseColors.Success
                            HidConnectionStatus.CONNECTING,
                            HidConnectionStatus.DISCONNECTING -> AirMouseColors.Warning
                            HidConnectionStatus.DISCONNECTED -> AirMouseColors.TextSecondary
                        }
                    )

                    HorizontalDivider(color = AirMouseColors.Border)

                    StatusRow(
                        label = "Connected Host",
                        value = if (state.isHostConnected) {
                            "${state.connectedHostName ?: "Host"} (${state.connectedHostAddress})"
                        } else {
                            "None (Paired devices are not automatically connected)"
                        },
                        valueColor = if (state.isHostConnected) AirMouseColors.Accent else AirMouseColors.TextMuted
                    )
                }
            }
        }

        // Primary HID lifecycle & discovery controls
        item {
            Surface(
                color = AirMouseColors.Surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AirMouseColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "HID Device Controls",
                        style = MaterialTheme.typography.titleMedium,
                        color = AirMouseColors.TextPrimary
                    )

                    if (!state.hasConnectPermission) {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = AirMouseColors.Accent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Android 14+ Bluetooth Permissions")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRegisterHid,
                            enabled = state.hasConnectPermission &&
                                state.isBluetoothEnabled &&
                                state.isHidProxyReady &&
                                state.registrationStatus != HidRegistrationStatus.REGISTERED &&
                                state.registrationStatus != HidRegistrationStatus.REGISTERING,
                            colors = ButtonDefaults.buttonColors(containerColor = AirMouseColors.Accent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Register HID")
                        }

                        OutlinedButton(
                            onClick = onUnregisterHid,
                            enabled = state.isRegistered ||
                                state.registrationStatus == HidRegistrationStatus.REGISTERING,
                            border = BorderStroke(1.dp, AirMouseColors.Border),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unregister HID")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onMakeDiscoverable,
                            enabled = state.adapterAvailable,
                            border = BorderStroke(1.dp, AirMouseColors.Border),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Discoverable")
                        }

                        OutlinedButton(
                            onClick = onOpenBluetoothSettings,
                            border = BorderStroke(1.dp, AirMouseColors.Border),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pair Settings")
                        }
                    }

                    if (state.isHostConnected || state.connectionStatus == HidConnectionStatus.CONNECTING) {
                        OutlinedButton(
                            onClick = onDisconnectHost,
                            border = BorderStroke(1.dp, AirMouseColors.Error.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LinkOff,
                                contentDescription = null,
                                tint = AirMouseColors.Error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect Host", color = AirMouseColors.Error)
                        }
                    }
                }
            }
        }

        // Paired (bonded) devices header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Paired Devices (${state.pairedDevices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = AirMouseColors.TextPrimary
                    )
                    Text(
                        text = "Pairing alone does not establish an HID connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AirMouseColors.TextMuted
                    )
                }
                IconButton(onClick = onRefreshDevices) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh paired devices",
                        tint = AirMouseColors.Accent
                    )
                }
            }
        }

        if (state.pairedDevices.isEmpty()) {
            item {
                Surface(
                    color = AirMouseColors.Surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AirMouseColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (!state.hasConnectPermission) {
                                "Grant BLUETOOTH_CONNECT to read paired devices."
                            } else if (!state.isBluetoothEnabled) {
                                "Enable Bluetooth to list paired host devices."
                            } else {
                                "No paired Bluetooth devices found."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = AirMouseColors.TextSecondary
                        )
                        Text(
                            text = "Tap Register HID, then tap Discoverable or open Pair Settings to pair with your PC, Mac, or Linux host.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AirMouseColors.TextMuted
                        )
                    }
                }
            }
        } else {
            items(state.pairedDevices, key = { it.address }) { device ->
                PairedDeviceCard(
                    device = device,
                    canInitiateConnect = state.isRegistered &&
                        state.connectionStatus != HidConnectionStatus.CONNECTING,
                    onConnect = { onConnectHost(device.address) },
                    onDisconnect = onDisconnectHost
                )
            }
        }
    }
}

@Composable
private fun PairedDeviceCard(
    device: PairedDeviceInfo,
    canInitiateConnect: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        color = AirMouseColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (device.isConnectedHidHost) {
                AirMouseColors.Success.copy(alpha = 0.6f)
            } else {
                AirMouseColors.Border
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (device.isConnectedHidHost) {
                        Icons.Rounded.BluetoothConnected
                    } else {
                        Icons.Rounded.Bluetooth
                    },
                    contentDescription = null,
                    tint = if (device.isConnectedHidHost) {
                        AirMouseColors.Success
                    } else {
                        AirMouseColors.TextSecondary
                    },
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = AirMouseColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = AirMouseColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (device.isConnectedHidHost) {
                            "CONNECTED VIA HID DEVICE"
                        } else {
                            "PAIRED ONLY — NOT CONNECTED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (device.isConnectedHidHost) {
                            AirMouseColors.Success
                        } else {
                            AirMouseColors.Warning
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (device.isConnectedHidHost) {
                OutlinedButton(
                    onClick = onDisconnect,
                    border = BorderStroke(1.dp, AirMouseColors.Error.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Disconnect", color = AirMouseColors.Error)
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = canInitiateConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = AirMouseColors.Accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AirMouseColors.TextSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.airmouse.hidtest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmouse.hidtest.hid.DiagnosticLogEntry
import com.airmouse.hidtest.hid.HidMouseDescriptor
import com.airmouse.hidtest.hid.HidProfileStatus
import com.airmouse.hidtest.hid.HidRegistrationStatus
import com.airmouse.hidtest.hid.HidUiState
import com.airmouse.hidtest.hid.LogLevel
import com.airmouse.hidtest.ui.theme.AirMouseColors

@Composable
fun DiagnosticsScreen(
    state: HidUiState,
    onReprobeHid: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val descriptorHex = remember { HidMouseDescriptor.descriptorHexPreview() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Device & OS Environment
        item {
            DiagnosticSectionCard(title = "Android Runtime & Hardware") {
                DiagnosticRow("Android Version", "Android ${state.systemInfo.androidVersion}")
                DiagnosticRow("API Level", "API ${state.systemInfo.apiLevel}")
                DiagnosticRow("Security Patch", state.systemInfo.securityPatch)
                DiagnosticRow("Manufacturer", state.systemInfo.manufacturer)
                DiagnosticRow(
                    "Device Model",
                    "${state.systemInfo.model} (${state.systemInfo.deviceCodeName})"
                )
            }
        }

        // 2. Android 14+ Bluetooth Permissions
        item {
            DiagnosticSectionCard(title = "Android 14+ Bluetooth Permissions") {
                DiagnosticRow(
                    label = "BLUETOOTH_CONNECT",
                    value = if (state.hasConnectPermission) "GRANTED" else "DENIED",
                    valueColor = if (state.hasConnectPermission) AirMouseColors.Success else AirMouseColors.Error
                )
                DiagnosticRow(
                    label = "BLUETOOTH_ADVERTISE",
                    value = if (state.hasAdvertisePermission) "GRANTED" else "DENIED",
                    valueColor = if (state.hasAdvertisePermission) AirMouseColors.Success else AirMouseColors.Warning
                )
                DiagnosticRow(
                    label = "Location Permissions",
                    value = "NOT REQUESTED (Not required on API 34+)",
                    valueColor = AirMouseColors.TextSecondary
                )

                state.permissionRationaleMessage?.let { rationale ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = AirMouseColors.Warning
                    )
                }

                if (!state.hasConnectPermission || !state.hasAdvertisePermission) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = AirMouseColors.Accent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Request")
                        }
                        OutlinedButton(
                            onClick = onOpenAppSettings,
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
                            Text("App Settings")
                        }
                    }
                }
            }
        }

        // 3. Bluetooth & HID Device Stack Telemetry (Real API results)
        item {
            DiagnosticSectionCard(title = "Bluetooth HID Device Capability") {
                DiagnosticRow(
                    label = "Bluetooth Adapter",
                    value = if (state.adapterAvailable) "Available" else "Unavailable",
                    valueColor = if (state.adapterAvailable) AirMouseColors.Success else AirMouseColors.Error
                )
                DiagnosticRow(
                    label = "Bluetooth Enabled",
                    value = state.adapterState.label,
                    valueColor = if (state.isBluetoothEnabled) AirMouseColors.Success else AirMouseColors.Error
                )
                DiagnosticRow(
                    label = "HID Device Profile",
                    value = state.hidProfileStatus.label,
                    valueColor = when (state.hidProfileStatus) {
                        HidProfileStatus.AVAILABLE -> AirMouseColors.Success
                        HidProfileStatus.PROBING -> AirMouseColors.Warning
                        else -> AirMouseColors.Error
                    }
                )
                state.hidProfileDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = AirMouseColors.TextSecondary
                    )
                }

                HorizontalDivider(color = AirMouseColors.Border)

                DiagnosticRow(
                    label = "HID Registration Status",
                    value = state.registrationStatus.label,
                    valueColor = when (state.registrationStatus) {
                        HidRegistrationStatus.REGISTERED -> AirMouseColors.Success
                        HidRegistrationStatus.REGISTERING -> AirMouseColors.Warning
                        HidRegistrationStatus.FAILED -> AirMouseColors.Error
                        HidRegistrationStatus.UNREGISTERED -> AirMouseColors.TextSecondary
                    }
                )
                DiagnosticRow(
                    label = "Registration Error",
                    value = state.registrationError ?: "None",
                    valueColor = if (state.registrationError != null) {
                        AirMouseColors.Error
                    } else {
                        AirMouseColors.TextSecondary
                    }
                )
                DiagnosticRow(
                    label = "HID Protocol Mode",
                    value = state.protocolMode,
                    valueColor = AirMouseColors.TextPrimary
                )
                DiagnosticRow(
                    label = "Last TX Report",
                    value = state.lastReportSummary,
                    valueColor = when (state.lastSendSucceeded) {
                        true -> AirMouseColors.Success
                        false -> AirMouseColors.Warning
                        null -> AirMouseColors.TextSecondary
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onReprobeHid,
                    border = BorderStroke(1.dp, AirMouseColors.Accent.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = AirMouseColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-Probe BluetoothProfile.HID_DEVICE", color = AirMouseColors.Accent)
                }
            }
        }

        // 4. HID Mouse Report Descriptor Inspection
        item {
            DiagnosticSectionCard(title = "HID Mouse Report Descriptor") {
                DiagnosticRow(
                    label = "Report ID / Payload",
                    value = "ID 0x%02X • %d bytes (Btn, X, Y, Wheel)".format(
                        HidMouseDescriptor.REPORT_ID_MOUSE,
                        HidMouseDescriptor.REPORT_PAYLOAD_SIZE
                    )
                )
                DiagnosticRow(
                    label = "Descriptor Length",
                    value = "${HidMouseDescriptor.MOUSE_REPORT_DESCRIPTOR.size} bytes (SUBCLASS1_MOUSE)"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = descriptorHex,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = AirMouseColors.Accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AirMouseColors.Background, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                )
            }
        }

        // 5. Real Callback & API Event Log
        item {
            Text(
                text = "Bluetooth HID Event Log (${state.eventLog.size})",
                style = MaterialTheme.typography.titleMedium,
                color = AirMouseColors.TextPrimary
            )
        }

        if (state.eventLog.isEmpty()) {
            item {
                Text(
                    text = "No Bluetooth HID lifecycle events recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AirMouseColors.TextMuted
                )
            }
        } else {
            items(state.eventLog) { entry ->
                LogEntryRow(entry = entry)
            }
        }
    }
}

@Composable
private fun DiagnosticSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = AirMouseColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AirMouseColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AirMouseColors.TextPrimary
            )
            HorizontalDivider(color = AirMouseColors.Border)
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color = AirMouseColors.TextPrimary
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AirMouseColors.TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

@Composable
private fun LogEntryRow(entry: DiagnosticLogEntry) {
    val accent = when (entry.level) {
        LogLevel.SUCCESS -> AirMouseColors.Success
        LogLevel.WARN -> AirMouseColors.Warning
        LogLevel.ERROR -> AirMouseColors.Error
        LogLevel.INFO -> AirMouseColors.Accent
    }
    Surface(
        color = AirMouseColors.Surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AirMouseColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = entry.timestamp,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = accent
            )
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = AirMouseColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

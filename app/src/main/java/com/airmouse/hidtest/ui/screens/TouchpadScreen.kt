package com.airmouse.hidtest.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airmouse.hidtest.hid.HidMouseDescriptor
import com.airmouse.hidtest.hid.HidUiState
import com.airmouse.hidtest.ui.theme.AirMouseColors
import java.util.Locale
import kotlin.math.truncate

@Composable
fun TouchpadScreen(
    state: HidUiState,
    onMove: (deltaX: Int, deltaY: Int, wheel: Int) -> Unit,
    onLeftButtonChanged: (Boolean) -> Unit,
    onRightButtonChanged: (Boolean) -> Unit,
    onReleaseAllButtons: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onRequestPermissions: () -> Unit,
    onRegisterHid: () -> Unit,
    onOpenConnectionTab: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ensure buttons are released cleanly if the Touchpad leaves composition.
    DisposableEffect(Unit) {
        onDispose { onReleaseAllButtons() }
    }

    val isLeftPressed = (state.activeButtonMask and HidMouseDescriptor.BUTTON_LEFT) != 0
    val isRightPressed = (state.activeButtonMask and HidMouseDescriptor.BUTTON_RIGHT) != 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Contextual readiness strip (real state only — never simulated)
        TouchpadStatusStrip(
            state = state,
            onRequestPermissions = onRequestPermissions,
            onRegisterHid = onRegisterHid,
            onOpenConnectionTab = onOpenConnectionTab,
            onDisconnect = onDisconnect
        )

        // Large precision touchpad surface
        PrecisionTouchpadSurface(
            enabled = state.isHostConnected,
            sensitivity = state.sensitivity,
            lastReportSummary = state.lastReportSummary,
            reportsSentCount = state.reportsSentCount,
            onMove = onMove,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Physical-feel Left & Right mouse buttons (independent press/release for drag support)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MouseButtonPad(
                label = "LEFT CLICK",
                sublabel = "BTN 1",
                isPressed = isLeftPressed,
                isConnected = state.isHostConnected,
                onPressChanged = onLeftButtonChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            MouseButtonPad(
                label = "RIGHT CLICK",
                sublabel = "BTN 2",
                isPressed = isRightPressed,
                isConnected = state.isHostConnected,
                onPressChanged = onRightButtonChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
        }

        // Cursor sensitivity control bar
        Surface(
            color = AirMouseColors.Surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AirMouseColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = AirMouseColors.Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Cursor Sensitivity",
                            style = MaterialTheme.typography.labelMedium,
                            color = AirMouseColors.TextPrimary
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.2fx", state.sensitivity),
                        style = MaterialTheme.typography.labelSmall,
                        color = AirMouseColors.Accent
                    )
                }

                Slider(
                    value = state.sensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0.4f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = AirMouseColors.Accent,
                        activeTrackColor = AirMouseColors.Accent,
                        inactiveTrackColor = AirMouseColors.Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TouchpadStatusStrip(
    state: HidUiState,
    onRequestPermissions: () -> Unit,
    onRegisterHid: () -> Unit,
    onOpenConnectionTab: () -> Unit,
    onDisconnect: () -> Unit
) {
    val borderColor = when {
        state.isHostConnected -> AirMouseColors.Success.copy(alpha = 0.5f)
        !state.hasConnectPermission || !state.isBluetoothEnabled -> AirMouseColors.Error.copy(alpha = 0.45f)
        else -> AirMouseColors.Border
    }

    Surface(
        color = AirMouseColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        !state.hasConnectPermission -> "Bluetooth Permission Required"
                        !state.adapterAvailable -> "Bluetooth Adapter Unavailable"
                        !state.isBluetoothEnabled -> "Bluetooth Is Disabled"
                        !state.isHidProxyReady -> "HID Profile: ${state.hidProfileStatus.label}"
                        !state.isRegistered -> "HID Mouse Not Registered"
                        state.isHostConnected -> "Connected: ${state.connectedHostName ?: state.connectedHostAddress}"
                        else -> "HID Registered — Waiting for Host"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = AirMouseColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        !state.hasConnectPermission -> "Grant BLUETOOTH_CONNECT & BLUETOOTH_ADVERTISE."
                        !state.isBluetoothEnabled -> "Turn on Bluetooth in system settings."
                        !state.isHidProxyReady -> state.hidProfileDetail ?: "Checking BluetoothProfile.HID_DEVICE..."
                        !state.isRegistered -> "Register the HID descriptor before connecting to a host."
                        state.isHostConnected -> "Host ${state.connectedHostAddress} • Live HID reports active"
                        else -> "Connect a paired host from the Connection tab."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AirMouseColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            when {
                !state.hasConnectPermission -> {
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = AirMouseColors.Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant")
                    }
                }

                state.isHidProxyReady && !state.isRegistered -> {
                    Button(
                        onClick = onRegisterHid,
                        colors = ButtonDefaults.buttonColors(containerColor = AirMouseColors.Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Register HID")
                    }
                }

                state.isRegistered && !state.isHostConnected -> {
                    OutlinedButton(
                        onClick = onOpenConnectionTab,
                        border = BorderStroke(1.dp, AirMouseColors.Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Connect", color = AirMouseColors.Accent)
                    }
                }

                state.isHostConnected -> {
                    OutlinedButton(
                        onClick = onDisconnect,
                        border = BorderStroke(1.dp, AirMouseColors.Error.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LinkOff,
                            contentDescription = null,
                            tint = AirMouseColors.Error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrecisionTouchpadSurface(
    enabled: Boolean,
    sensitivity: Float,
    lastReportSummary: String,
    reportsSentCount: Long,
    onMove: (deltaX: Int, deltaY: Int, wheel: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestSensitivity by rememberUpdatedState(sensitivity)
    val latestOnMove by rememberUpdatedState(onMove)

    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var activeTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var activeFingerCount by remember { mutableStateOf(0) }

    val shape = RoundedCornerShape(18.dp)
    val borderColor = when {
        activeTouchPoint != null && enabled -> AirMouseColors.Accent
        activeTouchPoint != null -> AirMouseColors.Warning
        enabled -> AirMouseColors.BorderActive.copy(alpha = 0.35f)
        else -> AirMouseColors.Border
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(AirMouseColors.TouchpadSurface)
            .border(width = 1.5.dp, color = borderColor, shape = shape)
            .onSizeChanged { padSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var trackedPointerId: PointerId = firstDown.id
                    var lastPosition: Offset = clampToBounds(firstDown.position, padSize)
                    var lastTwoFingerY: Float? = null
                    var remX = 0f
                    var remY = 0f
                    var remWheel = 0f

                    activeTouchPoint = lastPosition
                    activeFingerCount = 1

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressedChanges = event.changes.filter { it.pressed }

                        if (pressedChanges.isEmpty()) {
                            activeTouchPoint = null
                            activeFingerCount = 0
                            break
                        }

                        activeFingerCount = pressedChanges.size

                        if (pressedChanges.size >= 2) {
                            // Two-finger vertical scroll -> HID Wheel axis
                            val avgX = (pressedChanges[0].position.x + pressedChanges[1].position.x) * 0.5f
                            val avgY = (pressedChanges[0].position.y + pressedChanges[1].position.y) * 0.5f
                            val clampedAvg = clampToBounds(Offset(avgX, avgY), padSize)
                            activeTouchPoint = clampedAvg

                            val prevY = lastTwoFingerY
                            if (prevY != null) {
                                val rawDy = clampedAvg.y - prevY
                                // Positive HID wheel scrolls up; dragging downward scrolls down (or natural feel).
                                remWheel += (-rawDy / 24f)
                                val wheelStep = truncate(remWheel).toInt()
                                if (wheelStep != 0) {
                                    remWheel -= wheelStep
                                    latestOnMove(0, 0, wheelStep)
                                }
                            }
                            lastTwoFingerY = clampedAvg.y
                            // Reset single-finger anchor when transitioning back to 1 finger.
                            trackedPointerId = pressedChanges[0].id
                            lastPosition = clampToBounds(pressedChanges[0].position, padSize)
                            pressedChanges.forEach { it.consume() }
                        } else {
                            // Single-finger relative cursor movement
                            lastTwoFingerY = null
                            val active = pressedChanges[0]
                            val clampedPos = clampToBounds(active.position, padSize)
                            activeTouchPoint = clampedPos

                            if (active.id != trackedPointerId) {
                                // Hand-off to remaining finger without producing a jump delta.
                                trackedPointerId = active.id
                                lastPosition = clampedPos
                            } else {
                                val dxPx = clampedPos.x - lastPosition.x
                                val dyPx = clampedPos.y - lastPosition.y
                                lastPosition = clampedPos

                                // Boundary safety: ignore out-of-bounds jitter when finger slides off edge.
                                val withinX = active.position.x in 0f..padSize.width.toFloat()
                                val withinY = active.position.y in 0f..padSize.height.toFloat()

                                if (withinX) remX += dxPx * latestSensitivity
                                if (withinY) remY += dyPx * latestSensitivity

                                val sendX = truncate(remX).toInt()
                                val sendY = truncate(remY).toInt()
                                if (sendX != 0 || sendY != 0) {
                                    remX -= sendX
                                    remY -= sendY
                                    latestOnMove(sendX, sendY, 0)
                                }
                            }
                            active.consume()
                        }
                    }
                }
            }
    ) {
        // Subtle tactile dot grid + live touch reticle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacing = 28.dp.toPx()
            val dotRadius = 1.1.dp.toPx()
            val dotColor = Color(0xFF1F2A3C)

            var y = spacing
            while (y < size.height) {
                var x = spacing
                while (x < size.width) {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                    x += spacing
                }
                y += spacing
            }

            activeTouchPoint?.let { point ->
                val ringColor = if (enabled) AirMouseColors.Accent else AirMouseColors.Warning
                drawCircle(
                    color = ringColor.copy(alpha = 0.16f),
                    radius = if (activeFingerCount >= 2) 34.dp.toPx() else 26.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = ringColor,
                    radius = 5.dp.toPx(),
                    center = point
                )
            }
        }

        // Top-left mode watermark
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (activeFingerCount >= 2) "SCROLL WHEEL" else "TOUCHPAD",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) AirMouseColors.Accent else AirMouseColors.TextMuted
            )
            Text(
                text = "• 1-finger move • 2-finger wheel",
                style = MaterialTheme.typography.bodySmall,
                color = AirMouseColors.TextMuted
            )
        }

        // Center guidance when idle
        if (activeTouchPoint == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (enabled) "Slide to Move Host Cursor" else "No Connected HID Host",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) AirMouseColors.TextSecondary else AirMouseColors.TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (enabled) {
                        "Sending real BluetoothHidDevice mouse reports (ID 0x01)"
                    } else {
                        "Register HID and connect a paired host to transmit input"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AirMouseColors.TextMuted
                )
            }
        }

        // Bottom live HID report telemetry bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xB80B0E14))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lastReportSummary,
                style = MaterialTheme.typography.labelSmall,
                color = AirMouseColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TX: $reportsSentCount",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) AirMouseColors.Success else AirMouseColors.TextMuted
            )
        }
    }
}

@Composable
private fun MouseButtonPad(
    label: String,
    sublabel: String,
    isPressed: Boolean,
    isConnected: Boolean,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnPressChanged by rememberUpdatedState(onPressChanged)
    val bgColor = when {
        isPressed && isConnected -> AirMouseColors.AccentDim
        isPressed -> AirMouseColors.WarningDim
        else -> AirMouseColors.Surface
    }
    val borderColor = when {
        isPressed && isConnected -> AirMouseColors.Accent
        isPressed -> AirMouseColors.Warning
        else -> AirMouseColors.Border
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    latestOnPressChanged(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        latestOnPressChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isPressed) AirMouseColors.Accent else AirMouseColors.TextPrimary
            )
            Text(
                text = if (isPressed) "PRESSED" else sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPressed) AirMouseColors.Accent else AirMouseColors.TextMuted
            )
        }
    }
}

private fun clampToBounds(offset: Offset, size: IntSize): Offset {
    if (size.width <= 0 || size.height <= 0) return offset
    return Offset(
        x = offset.x.coerceIn(0f, size.width.toFloat()),
        y = offset.y.coerceIn(0f, size.height.toFloat())
    )
}

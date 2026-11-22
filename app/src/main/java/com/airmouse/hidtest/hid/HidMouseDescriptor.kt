package com.airmouse.hidtest.hid

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Standard USB/Bluetooth HID 1.11 Mouse Report Descriptor and report encoder.
 *
 * Input report payload layout (Report ID = 1, 4 bytes):
 * - Byte 0: Button bitmask (bit 0 = Left, bit 1 = Right, bit 2 = Middle, bits 3..7 = 0 padding)
 * - Byte 1: Relative X displacement (signed 8-bit, -127..127)
 * - Byte 2: Relative Y displacement (signed 8-bit, -127..127)
 * - Byte 3: Relative Vertical Wheel displacement (signed 8-bit, -127..127)
 *
 * Note: Android's BluetoothHidDevice.sendReport(device, REPORT_ID_MOUSE, payload) automatically
 * prepends the Report ID byte matching the descriptor's Report ID (0x85, 0x01) item.
 */
object HidMouseDescriptor {

    const val REPORT_ID_MOUSE: Int = 1

    const val BUTTON_NONE: Int = 0x00
    const val BUTTON_LEFT: Int = 0x01
    const val BUTTON_RIGHT: Int = 0x02
    const val BUTTON_MIDDLE: Int = 0x04
    const val BUTTON_VALID_MASK: Int = BUTTON_LEFT or BUTTON_RIGHT or BUTTON_MIDDLE

    const val AXIS_MIN: Int = -127
    const val AXIS_MAX: Int = 127

    const val REPORT_PAYLOAD_SIZE: Int = 4

    /**
     * 54-byte HID Report Descriptor for a 3-button relative mouse with vertical wheel.
     * Logical minimum is -127 (0x81) instead of -128 per USB HID 1.11 specification best practice.
     */
    val MOUSE_REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop Controls)
        0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), 0x01.toByte(), //   Report ID (1)
        0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
        // Buttons (3 bits)
        0x05.toByte(), 0x09.toByte(), //     Usage Page (Button)
        0x19.toByte(), 0x01.toByte(), //     Usage Minimum (Button 1 - Left)
        0x29.toByte(), 0x03.toByte(), //     Usage Maximum (Button 3 - Middle)
        0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
        0x95.toByte(), 0x03.toByte(), //     Report Count (3)
        0x75.toByte(), 0x01.toByte(), //     Report Size (1 bit)
        0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute)
        // Padding (5 bits -> aligns button field to 1 byte)
        0x95.toByte(), 0x01.toByte(), //     Report Count (1)
        0x75.toByte(), 0x05.toByte(), //     Report Size (5 bits)
        0x81.toByte(), 0x03.toByte(), //     Input (Constant, Variable, Absolute)
        // Relative X, Y, and Vertical Wheel (3 signed bytes, -127..127)
        0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop Controls)
        0x09.toByte(), 0x30.toByte(), //     Usage (X)
        0x09.toByte(), 0x31.toByte(), //     Usage (Y)
        0x09.toByte(), 0x38.toByte(), //     Usage (Wheel)
        0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(), //     Report Size (8 bits)
        0x95.toByte(), 0x03.toByte(), //     Report Count (3)
        0x81.toByte(), 0x06.toByte(), //     Input (Data, Variable, Relative)
        0xC0.toByte(),                //   End Collection (Physical)
        0xC0.toByte()                 // End Collection (Application)
    )

    /**
     * Builds a single valid 4-byte HID mouse input report payload with axis values clamped to [-127, 127].
     */
    fun buildMouseReport(
        buttonMask: Int,
        deltaX: Int = 0,
        deltaY: Int = 0,
        wheel: Int = 0
    ): ByteArray {
        val maskedButtons = (buttonMask and BUTTON_VALID_MASK).toByte()
        val clampedX = clampAxis(deltaX).toByte()
        val clampedY = clampAxis(deltaY).toByte()
        val clampedWheel = clampAxis(wheel).toByte()
        return byteArrayOf(maskedButtons, clampedX, clampedY, clampedWheel)
    }

    /**
     * Splits large relative movements (> 127 units in one frame) into one or more spec-compliant
     * 4-byte HID reports so fast swipes never overflow a signed 8-bit axis field.
     */
    fun buildChunkedMouseReports(
        buttonMask: Int,
        totalDeltaX: Int,
        totalDeltaY: Int,
        totalWheel: Int = 0
    ): List<ByteArray> {
        if (totalDeltaX == 0 && totalDeltaY == 0 && totalWheel == 0) {
            return listOf(buildMouseReport(buttonMask, 0, 0, 0))
        }

        val reports = mutableListOf<ByteArray>()
        var remX = totalDeltaX
        var remY = totalDeltaY
        var remW = totalWheel

        // Safety cap against pathological inputs while supporting fast flings smoothly.
        var iterations = 0
        while ((remX != 0 || remY != 0 || remW != 0) && iterations < 8) {
            val stepX = clampAxis(remX)
            val stepY = clampAxis(remY)
            val stepW = clampAxis(remW)
            reports.add(buildMouseReport(buttonMask, stepX, stepY, stepW))
            remX -= stepX
            remY -= stepY
            remW -= stepW
            iterations++
        }
        return reports
    }

    fun clampAxis(value: Int): Int = max(AXIS_MIN, min(AXIS_MAX, value))

    fun formatReportHex(report: ByteArray): String {
        if (report.size != REPORT_PAYLOAD_SIZE) {
            return report.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        }
        val buttons = report[0].toInt() and 0xFF
        val x = report[1].toInt()
        val y = report[2].toInt()
        val w = report[3].toInt()
        val sx = if (x >= 0) "+%02d".format(x) else "-%02d".format(abs(x))
        val sy = if (y >= 0) "+%02d".format(y) else "-%02d".format(abs(y))
        val sw = if (w >= 0) "+%02d".format(w) else "-%02d".format(abs(w))
        return "BTN:0x%02X X:%s Y:%s W:%s".format(buttons, sx, sy, sw)
    }

    fun descriptorHexPreview(): String =
        MOUSE_REPORT_DESCRIPTOR.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}

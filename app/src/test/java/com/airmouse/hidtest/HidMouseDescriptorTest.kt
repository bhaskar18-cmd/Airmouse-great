package com.airmouse.hidtest

import com.airmouse.hidtest.hid.HidMouseDescriptor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests verifying HID Mouse report descriptor integrity and 4-byte report encoding.
 * Note: These tests validate protocol/descriptor formatting at compile/unit-test time only;
 * real BluetoothHidDevice profile acquisition requires testing on physical Android 14/15/16 hardware.
 */
class HidMouseDescriptorTest {

    @Test
    fun descriptor_hasValidLengthAndReportId() {
        val desc = HidMouseDescriptor.MOUSE_REPORT_DESCRIPTOR
        assertEquals(54, desc.size)
        // Usage Page (Generic Desktop) = 0x05, 0x01
        assertEquals(0x05.toByte(), desc[0])
        assertEquals(0x01.toByte(), desc[1])
        // Usage (Mouse) = 0x09, 0x02
        assertEquals(0x09.toByte(), desc[2])
        assertEquals(0x02.toByte(), desc[3])
        // Report ID (1) = 0x85, 0x01
        assertEquals(0x85.toByte(), desc[6])
        assertEquals(HidMouseDescriptor.REPORT_ID_MOUSE.toByte(), desc[7])
        // Ends with two End Collection (0xC0) tags
        assertEquals(0xC0.toByte(), desc[desc.size - 2])
        assertEquals(0xC0.toByte(), desc[desc.size - 1])
    }

    @Test
    fun buildMouseReport_clampsAxesAndMasksButtons() {
        val report = HidMouseDescriptor.buildMouseReport(
            buttonMask = 0xFF, // Upper 5 bits must be zeroed (padding)
            deltaX = 500,
            deltaY = -500,
            wheel = 200
        )
        assertArrayEquals(
            byteArrayOf(0x07.toByte(), 127.toByte(), (-127).toByte(), 127.toByte()),
            report
        )
    }

    @Test
    fun buildChunkedMouseReports_splitsFastSwipesWithoutOverflow() {
        val reports = HidMouseDescriptor.buildChunkedMouseReports(
            buttonMask = HidMouseDescriptor.BUTTON_LEFT,
            totalDeltaX = 260,
            totalDeltaY = -140,
            totalWheel = 0
        )
        assertEquals(3, reports.size)
        assertEquals(260, reports.sumOf { it[1].toInt() })
        assertEquals(-140, reports.sumOf { it[2].toInt() })
        assertTrue(reports.all { it[0] == HidMouseDescriptor.BUTTON_LEFT.toByte() })
        assertTrue(reports.all { it[1].toInt() in -127..127 && it[2].toInt() in -127..127 })
    }
}

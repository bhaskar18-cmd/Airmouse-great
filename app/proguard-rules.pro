# ProGuard rules for AirMouse HID Test
# Keep BluetoothHidDevice callback classes if minification is enabled in future release builds.
-keep class com.airmouse.hidtest.hid.** { *; }

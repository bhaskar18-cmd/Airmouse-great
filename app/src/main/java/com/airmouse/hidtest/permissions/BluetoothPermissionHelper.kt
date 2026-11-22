package com.airmouse.hidtest.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Runtime permission utilities for Android 14+ (API 34+).
 *
 * Only requests BLUETOOTH_CONNECT and BLUETOOTH_ADVERTISE.
 * Location permissions are intentionally never declared or requested.
 */
object BluetoothPermissionHelper {

    val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    fun hasConnectPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    fun hasAdvertisePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED

    fun hasAllRequiredPermissions(context: Context): Boolean =
        hasConnectPermission(context) && hasAdvertisePermission(context)

    fun missingPermissions(context: Context): Array<String> =
        REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
}

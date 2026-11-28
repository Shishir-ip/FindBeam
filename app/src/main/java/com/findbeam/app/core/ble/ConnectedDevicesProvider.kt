package com.findbeam.app.core.ble

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Devices currently CONNECTED to this phone (watch, earbuds, car kit…).
 * Their names come from the bond record, so they are always available —
 * unlike scan results, where bonded devices usually advertise with rotating
 * private addresses that can't be matched to cached names.
 */
class ConnectedDevicesProvider(private val context: Context) {

    data class ConnectedDevice(
        val address: String,
        val name: String,
        val classMajor: Int?,
        val viaGatt: Boolean,
        val viaClassic: Boolean
    )

    private val manager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var a2dp: BluetoothA2dp? = null
    private var headset: BluetoothHeadset? = null
    private var proxiesRequested = false

    private val hasConnectPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = proxy as BluetoothA2dp
                BluetoothProfile.HEADSET -> headset = proxy as BluetoothHeadset
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dp = null
                BluetoothProfile.HEADSET -> headset = null
            }
        }
    }

    /** Requests the A2DP/headset profile proxies (idempotent). */
    fun ensureProxies() {
        if (proxiesRequested) return
        proxiesRequested = true
        val adapter = manager.adapter ?: return
        if (!hasConnectPermission) return
        try {
            adapter.getProfileProxy(context, proxyListener, BluetoothProfile.A2DP)
        } catch (_: Exception) {
        }
        try {
            adapter.getProfileProxy(context, proxyListener, BluetoothProfile.HEADSET)
        } catch (_: Exception) {
        }
    }

    fun snapshot(): List<ConnectedDevice> {
        if (!hasConnectPermission) return emptyList()
        val map = LinkedHashMap<String, ConnectedDevice>()

        fun add(device: BluetoothDevice, gatt: Boolean, classic: Boolean) {
            val address = device.address ?: return
            val name = try {
                device.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Unnamed device"
            } catch (_: SecurityException) {
                "Unnamed device"
            }
            val cls = try {
                device.bluetoothClass?.majorDeviceClass?.takeIf { it != 0 }
            } catch (_: Exception) {
                null
            }
            val existing = map[address]
            map[address] = ConnectedDevice(
                address = address,
                name = existing?.name ?: name,
                classMajor = cls ?: existing?.classMajor,
                viaGatt = gatt || (existing?.viaGatt ?: false),
                viaClassic = classic || (existing?.viaClassic ?: false)
            )
        }

        try {
            manager.getConnectedDevices(BluetoothProfile.GATT)
                .forEach { add(it, gatt = true, classic = false) }
        } catch (_: Exception) {
        }
        try {
            manager.getConnectedDevices(BluetoothProfile.GATT_SERVER)
                .forEach { add(it, gatt = true, classic = false) }
        } catch (_: Exception) {
        }
        try {
            a2dp?.getConnectedDevices()?.forEach { add(it, gatt = false, classic = true) }
        } catch (_: Exception) {
        }
        try {
            headset?.getConnectedDevices()?.forEach { add(it, gatt = false, classic = true) }
        } catch (_: Exception) {
        }
        return map.values.toList()
    }
}

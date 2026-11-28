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
import java.util.concurrent.ConcurrentHashMap

/**
 * Devices currently CONNECTED to this phone (watch, earbuds, car kit…).
 * Their names come from the bond record, so they are always available —
 * unlike scan results, where bonded devices usually advertise with rotating
 * private addresses that can't be matched to cached names.
 *
 * OEM stacks (ColorOS in particular) transiently drop devices from the
 * profile connection lists for a few seconds at a time. To keep the UI
 * stable:
 *  - resolved names are remembered forever ([nameMemory]);
 *  - a device that drops out of the live lists is still reported for a
 *    [GRACE_MS] window before it disappears from the section.
 */
class ConnectedDevicesProvider(private val context: Context) {

    data class ConnectedDevice(
        val address: String,
        val name: String,
        val classMajor: Int?,
        val viaGatt: Boolean,
        val viaClassic: Boolean,
        val lastSeenMs: Long
    )

    private val manager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var a2dp: BluetoothA2dp? = null
    private var headset: BluetoothHeadset? = null
    private var proxiesRequested = false

    private val nameMemory = ConcurrentHashMap<String, String>()
    private val lastKnown = ConcurrentHashMap<String, ConnectedDevice>()

    private companion object {
        /** How long a device stays listed after dropping from the profile lists. */
        const val GRACE_MS = 90_000L
    }

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
        val now = System.currentTimeMillis()
        val fresh = LinkedHashMap<String, ConnectedDevice>()

        fun add(device: BluetoothDevice, gatt: Boolean, classic: Boolean) {
            val address = device.address ?: return
            val readName = try {
                device.name?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: SecurityException) {
                null
            }
            if (readName != null) nameMemory[address] = readName
            val cls = try {
                device.bluetoothClass?.majorDeviceClass?.takeIf { it != 0 }
            } catch (_: Exception) {
                null
            }
            val existing = fresh[address]
            fresh[address] = ConnectedDevice(
                address = address,
                name = readName ?: nameMemory[address] ?: "Unnamed device",
                classMajor = cls ?: existing?.classMajor,
                viaGatt = gatt || (existing?.viaGatt ?: false),
                viaClassic = classic || (existing?.viaClassic ?: false),
                lastSeenMs = now
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

        // Merge with the last-known store: keep freshly-seen entries and let
        // devices that dropped from the profiles linger for the grace window
        // instead of flickering out of the UI.
        val merged = LinkedHashMap<String, ConnectedDevice>()
        merged.putAll(fresh)
        for (e in lastKnown.values) {
            if (now - e.lastSeenMs <= GRACE_MS && !merged.containsKey(e.address)) {
                merged[e.address] = e
            }
        }
        // Refresh timestamps for everything we still report.
        for (d in merged.values) {
            lastKnown[d.address] = if (d.lastSeenMs == now) d else d.copy(lastSeenMs = now)
        }
        // Forget entries past the grace window.
        for (e in lastKnown.values.toList()) {
            if (now - e.lastSeenMs > GRACE_MS) lastKnown.remove(e.address)
        }

        return merged.values.sortedBy { it.name.lowercase() }
    }
}

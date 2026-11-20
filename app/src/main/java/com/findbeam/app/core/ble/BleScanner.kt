package com.findbeam.app.core.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Thin wrapper over the platform BLE scanner. Emits deduplicated-friendly
 * [Device] snapshots; the view model merges them by MAC address.
 */
class BleScanner(private val context: Context) {

    data class Device(
        val address: String,
        val name: String?,
        val rssi: Int,
        val companyId: Int?,
        val serviceUuids: List<String>,
        val timestampMs: Long
    )

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    val isBluetoothOn: Boolean
        get() = bluetoothManager.adapter?.isEnabled == true

    val hasPermission: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    var lowLatency = false

    private var callback: ScanCallback? = null

    fun start(onDevice: (Device) -> Unit, filterAddress: String? = null): Boolean {
        val adapter = bluetoothManager.adapter ?: return false
        if (!adapter.isEnabled) return false
        if (!hasPermission) return false
        stop()

        val settings = ScanSettings.Builder()
            .setScanMode(
                if (lowLatency) ScanSettings.SCAN_MODE_LOW_LATENCY
                else ScanSettings.SCAN_MODE_BALANCED
            )
            .build()

        val filters = filterAddress?.let { addr ->
            listOf(android.bluetooth.le.ScanFilter.Builder().setDeviceAddress(addr).build())
        }

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onDevice(result.toDevice())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onDevice(it.toDevice()) }
            }

            override fun onScanFailed(errorCode: Int) {
                // Swallow: the UI reflects "not scanning" state.
            }
        }
        callback = cb
        val scanner = try {
            adapter.bluetoothLeScanner
        } catch (_: SecurityException) {
            null
        } ?: return false

        return try {
            if (filters != null) scanner.startScan(filters, settings, cb)
            else scanner.startScan(null, settings, cb)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        try {
            bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
    }

    private fun ScanResult.toDevice(): Device {
        val name = try {
            scanRecord?.deviceName ?: device?.name
        } catch (_: SecurityException) {
            null
        }
        val companyId = try {
            val msd = scanRecord?.manufacturerSpecificData
            if (msd != null && msd.size() > 0) msd.keyAt(0) else null
        } catch (_: Exception) {
            null
        }
        val uuids = try {
            scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return Device(
            address = device?.address ?: "unknown",
            name = name?.trim()?.takeIf { it.isNotEmpty() },
            rssi = rssi,
            companyId = companyId,
            serviceUuids = uuids,
            timestampMs = System.currentTimeMillis()
        )
    }
}

package com.findbeam.app.core.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper over the platform BLE scanner.
 *
 * Many devices omit their name from advertisement packets, so names and
 * Bluetooth classes are additionally resolved from Android's local device
 * cache (BluetoothDevice.name / bluetoothClass) on a background thread —
 * those are blocking binder calls and must never run on the main thread.
 */
class BleScanner(private val context: Context) {

    data class Device(
        val address: String,
        val name: String?,
        val rssi: Int,
        val companyId: Int?,
        val serviceUuids: List<String>,
        val classMajor: Int?,
        val timestampMs: Long
    )

    data class RemoteInfo(val name: String?, val classMajor: Int?)

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

    // Remote-device info resolved so far, keyed by MAC address.
    private val remoteCache = ConcurrentHashMap<String, RemoteInfo>()

    // Blocking binder calls go here, never on main.
    private val resolveExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resolving = ConcurrentHashMap.newKeySet<String>()

    fun cachedRemoteInfo(address: String): RemoteInfo? = remoteCache[address]

    /**
     * Resolves the cached human-readable name and major device class for a
     * MAC address via BluetoothAdapter. Result is cached and delivered on
     * the main thread. Safe to call repeatedly; duplicates are coalesced.
     */
    fun resolveRemoteInfoAsync(address: String, onDone: (RemoteInfo) -> Unit) {
        if (!hasPermission) return
        if (resolving.contains(address)) return
        resolving.add(address)
        resolveExecutor.execute {
            val info = try {
                val adapter = bluetoothManager.adapter
                val device = adapter?.getRemoteDevice(address)
                if (device == null) {
                    RemoteInfo(null, null)
                } else {
                    val name = try {
                        device.name?.trim()?.takeIf { it.isNotEmpty() }
                    } catch (_: SecurityException) {
                        null
                    }
                    val cls = try {
                        device.bluetoothClass?.majorDeviceClass
                    } catch (_: SecurityException) {
                        null
                    } catch (_: Exception) {
                        null
                    }
                    RemoteInfo(name, cls?.takeIf { it != 0 })
                }
            } catch (_: IllegalArgumentException) {
                RemoteInfo(null, null)
            } catch (_: Exception) {
                RemoteInfo(null, null)
            }
            if (info.name != null || info.classMajor != null) {
                remoteCache[address] = info
            }
            resolving.remove(address)
            if (info.name != null || info.classMajor != null) {
                mainHandler.post { onDone(info) }
            }
        }
    }

    /** Pre-warms the cache with bonded (paired) devices, which have known names. */
    fun preloadBondedNames() {
        if (!hasPermission) return
        resolveExecutor.execute {
            try {
                bluetoothManager.adapter?.bondedDevices?.forEach { d ->
                    val name = try {
                        d.name?.trim()?.takeIf { it.isNotEmpty() }
                    } catch (_: SecurityException) {
                        null
                    }
                    val cls = try {
                        d.bluetoothClass?.majorDeviceClass
                    } catch (_: Exception) {
                        null
                    }
                    if (name != null) {
                        remoteCache[d.address] = RemoteInfo(name, cls?.takeIf { it != 0 })
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Classic discovery. This is what the system Bluetooth settings app
    // relies on: during classic discovery the stack fetches remote names
    // (EIR/SDP) for UNBONDED devices too and caches them, so nearby devices
    // show real names. LE-only devices never appear here; their names can
    // only come from GATT (see fetchNameViaGatt).
    // ------------------------------------------------------------------

    private var discoveryReceiver: BroadcastReceiver? = null

    val isDiscovering: Boolean
        get() = try {
            bluetoothManager.adapter?.isDiscovering == true
        } catch (_: SecurityException) {
            false
        }

    fun startClassicDiscovery(onResolved: (address: String, info: RemoteInfo) -> Unit): Boolean {
        val adapter = bluetoothManager.adapter ?: return false
        if (!adapter.isEnabled || !hasPermission) return false

        stopDiscovery()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND,
                    BluetoothDevice.ACTION_NAME_CHANGED -> {
                        val device = IntentCompat.getParcelableExtra(
                            intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                        ) ?: return
                        val name = try {
                            device.name?.trim()?.takeIf { it.isNotEmpty() }
                        } catch (_: SecurityException) {
                            null
                        }
                        val cls = try {
                            device.bluetoothClass?.majorDeviceClass?.takeIf { it != 0 }
                        } catch (_: Exception) {
                            null
                        }
                        val address = device.address ?: return
                        if (name != null) {
                            val cached = remoteCache[address]
                            remoteCache[address] =
                                RemoteInfo(name, cls ?: cached?.classMajor)
                            mainHandler.post { onResolved(address, remoteCache[address]!!) }
                        } else if (cls != null) {
                            remoteCache.merge(address, RemoteInfo(null, cls)) { old, _new ->
                                RemoteInfo(old.name, cls)
                            }
                        }
                    }
                }
            }
        }
        discoveryReceiver = receiver
        try {
            ContextCompat.registerReceiver(
                context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
            discoveryReceiver = null
            return false
        }

        return try {
            if (adapter.isDiscovering) {
                true // someone else is already discovering; we'll get broadcasts
            } else {
                adapter.startDiscovery()
            }
        } catch (_: SecurityException) {
            false
        }
    }

    fun stopDiscovery() {
        discoveryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        discoveryReceiver = null
        try {
            if (isDiscovering) bluetoothManager.adapter?.cancelDiscovery()
        } catch (_: SecurityException) {
        }
    }

    // ------------------------------------------------------------------
    // GATT name fetch: reads the Generic Access "Device Name" (0x2A00)
    // characteristic. Used for the selected tracking device when the name
    // is still unknown — works for LE-only devices that never appear in
    // classic discovery. Bounded by an 8 s timeout, single attempt.
    // ------------------------------------------------------------------

    fun fetchNameViaGatt(address: String, onDone: (String?) -> Unit) {
        if (!hasPermission) {
            mainHandler.post { onDone(null) }
            return
        }
        resolveExecutor.execute {
            var gattRef: BluetoothGatt? = null
            val finished = AtomicBoolean(false)

            fun finish(name: String?) {
                if (finished.compareAndSet(false, true)) {
                    try {
                        gattRef?.disconnect()
                        gattRef?.close()
                    } catch (_: Exception) {
                    }
                    mainHandler.post { onDone(name) }
                }
            }

            mainHandler.postDelayed({ finish(null) }, 8_000)

            try {
                val adapter = bluetoothManager.adapter
                val device = adapter?.getRemoteDevice(address)
                if (device == null) {
                    finish(null)
                    return@execute
                }

                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(
                        g: BluetoothGatt, status: Int, newState: Int
                    ) {
                        gattRef = g
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> try {
                                g.discoverServices()
                            } catch (_: Exception) {
                                finish(null)
                            }
                            BluetoothProfile.STATE_DISCONNECTED -> finish(null)
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            finish(null)
                            return
                        }
                        val service = g.getService(UUID.fromString(
                            "00001800-0000-1000-8000-00805f9b34fb"))
                        val characteristic = service?.getCharacteristic(UUID.fromString(
                            "00002a00-0000-1000-8000-00805f9b34fb"))
                        if (characteristic == null) {
                            finish(null)
                            return
                        }
                        try {
                            @Suppress("DEPRECATION")
                            if (!g.readCharacteristic(characteristic)) finish(null)
                        } catch (_: Exception) {
                            finish(null)
                        }
                    }

                    override fun onCharacteristicRead(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        // Pre-API 33 devices
                        finish(
                            if (status == BluetoothGatt.GATT_SUCCESS)
                                characteristic.getStringValue(0) else null
                        )
                    }

                    override fun onCharacteristicRead(
                        g: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        value: ByteArray,
                        status: Int
                    ) {
                        // API 33+ devices
                        finish(
                            if (status == BluetoothGatt.GATT_SUCCESS)
                                characteristic.getStringValue(0) else null
                        )
                    }
                }

                val gatt = try {
                    device.connectGatt(
                        context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                    )
                } catch (_: SecurityException) {
                    null
                }
                if (gatt == null) finish(null) else gattRef = gatt
            } catch (_: SecurityException) {
                finish(null)
            } catch (_: Exception) {
                finish(null)
            }
        }
    }

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
            listOf(ScanFilter.Builder().setDeviceAddress(addr).build())
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
        val cached = device?.address?.let { remoteCache[it] }
        return Device(
            address = device?.address ?: "unknown",
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: cached?.name,
            rssi = rssi,
            companyId = companyId,
            serviceUuids = uuids,
            classMajor = cached?.classMajor,
            timestampMs = System.currentTimeMillis()
        )
    }
}

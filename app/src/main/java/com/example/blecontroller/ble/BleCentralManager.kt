package com.example.blecontroller.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleCentralManager(
    private val context: Context,
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val scanMap = linkedMapOf<String, ScanDeviceUi>()

    private var bluetoothGatt: BluetoothGatt? = null

    private val _scanResults = MutableStateFlow<List<ScanDeviceUi>>(emptyList())
    val scanResults: StateFlow<List<ScanDeviceUi>> = _scanResults.asStateFlow()

    private val _gattServices = MutableStateFlow<List<GattServiceUi>>(emptyList())
    val gattServices: StateFlow<List<GattServiceUi>> = _gattServices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ScanDeviceUi?>(null)
    val connectedDevice: StateFlow<ScanDeviceUi?> = _connectedDevice.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _statusText = MutableStateFlow("Disconnected")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    fun isBluetoothAvailable(): Boolean = adapter != null

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value || scanner == null) return
        scanMap.clear()
        _scanResults.value = emptyList()
        _isScanning.value = true
        _statusText.value = "Scanning for BLE devices"
        scanner?.startScan(
            null,
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback,
        )
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        _isScanning.value = false
        if (_connectedDevice.value == null) {
            _statusText.value = "Scan stopped"
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        disconnect()

        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }

        if (device == null) {
            _statusText.value = "Invalid BLE address"
            return
        }

        _statusText.value = "Connecting: $address"
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _gattServices.value = emptyList()
        _connectedDevice.value = null
        _statusText.value = "Disconnected"
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(serviceUuid: String, characteristicUuid: String, payload: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.services.firstOrNull { it.uuid.toString().equals(serviceUuid, ignoreCase = true) }
            ?: return false
        val characteristic = service.characteristics.firstOrNull {
            it.uuid.toString().equals(characteristicUuid, ignoreCase = true)
        } ?: return false

        val properties = characteristic.properties
        val writeType = when {
            properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 -> {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(characteristic, payload, writeType)
            val ok = result == BluetoothStatusCodes.SUCCESS
            ok
        } else {
            characteristic.writeType = writeType
            characteristic.value = payload
            val ok = gatt.writeCharacteristic(characteristic)
            ok
        }
    }

    private fun mapServices(services: List<BluetoothGattService>): List<GattServiceUi> {
        return services.map { service ->
            GattServiceUi(
                uuid = service.uuid.toString(),
                characteristics = service.characteristics.map { characteristic ->
                    GattCharacteristicUi(
                        serviceUuid = service.uuid.toString(),
                        characteristicUuid = characteristic.uuid.toString(),
                        canWrite = characteristic.canWrite(),
                    )
                },
            )
        }
    }

    private fun BluetoothGattCharacteristic.canWrite(): Boolean {
        val props = properties
        return props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
            props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
    }

    private fun ScanResult.toUi(): ScanDeviceUi {
        return ScanDeviceUi(
            name = device.name ?: scanRecord?.deviceName,
            address = device.address,
            rssi = rssi,
        )
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val ui = result.toUi()
            scanMap[ui.address] = ui
            _scanResults.value = scanMap.values.sortedByDescending { it.rssi }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach {
                val ui = it.toUi()
                scanMap[ui.address] = ui
            }
            _scanResults.value = scanMap.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _statusText.value = "BLE scan error: $errorCode"
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val current = scanMap[gatt.device.address] ?: ScanDeviceUi(
                        name = gatt.device.name,
                        address = gatt.device.address,
                        rssi = 0,
                    )
                    _connectedDevice.value = current
                    _statusText.value = "Connected, discovering services"
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectedDevice.value = null
                    _gattServices.value = emptyList()
                    _statusText.value = "Disconnected (status=$status)"
                    gatt.close()
                    if (bluetoothGatt == gatt) {
                        bluetoothGatt = null
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _gattServices.value = mapServices(gatt.services)
                _statusText.value = "Discovered GATT services: ${gatt.services.size}"
            } else {
                _statusText.value = "Service discovery error: $status"
            }
        }
    }
}

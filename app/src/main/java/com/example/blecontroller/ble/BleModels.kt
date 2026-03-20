package com.example.blecontroller.ble

data class ScanDeviceUi(
    val name: String?,
    val address: String,
    val rssi: Int,
)

data class GattCharacteristicUi(
    val serviceUuid: String,
    val characteristicUuid: String,
    val canWrite: Boolean,
)

data class GattServiceUi(
    val uuid: String,
    val characteristics: List<GattCharacteristicUi>,
)

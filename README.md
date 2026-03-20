# BleController

A Kotlin + Jetpack Compose based customizable BLE controller app for Android.

## Main features

- Scan and connect to BLE devices
- Automatic GATT service/characteristic discovery
- Only writable characteristics are offered for button mapping
- Create, move, and resize virtual buttons
- Lock and unlock layouts
- Persist layouts and BLE device bindings in a Room database
- Reload and duplicate saved layouts

## Technical choices

- Min SDK: 26
- Target SDK: 35
- UI: Jetpack Compose
- Storage: Room
- BLE: Android BluetoothGatt API (characteristic write)

## Important note

The app currently implements GATT **characteristic** writes. If you also need descriptor writes, you can add a dedicated descriptor-write path next to `BleCentralManager.writeCharacteristic()`.

## Getting started

1. Open the project in Android Studio.
2. Wait for Gradle sync to complete.
3. Run the app on a BLE-capable physical device (Android 12+ recommended).
4. Grant nearby device permissions when prompted.

## Usage

1. On the **BLE** tab, start scanning.
2. Connect to the desired peripheral.
3. On the **Controller** tab, create a layout.
4. In unlocked mode, tap the canvas and use the **Add button** bubble.
5. In edit mode, tap a button and assign a writable characteristic and HEX payload.
6. Drag buttons to reposition and use the bottom-right handle to resize.
7. Lock the layout to use buttons in live mode (BLE characteristic write on tap).

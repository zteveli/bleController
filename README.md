# BleController

Kotlin + Jetpack Compose alapú, testreszabható BLE controller Androidra.

## Fő funkciók

- BLE eszközök keresése és csatlakoztatása
- GATT service / characteristic automatikus felismerése
- Csak írható characteristicok felajánlása gomb-hozzárendeléshez
- Virtuális nyomógombok létrehozása, mozgatása és átméretezése
- Layout zárolása / feloldása
- Layoutok és BLE eszköz-hozzárendelések mentése Room adatbázisba
- Korábbi layoutok visszatöltése és duplikálása

## Technikai döntések

- Min SDK: 26
- Target SDK: 35
- UI: Jetpack Compose
- Adatmentés: Room
- BLE: Android BluetoothGatt API (characteristic write)

## Fontos megjegyzés

Az alkalmazás jelenleg GATT **characteristic** írást valósít meg. Ha descriptor írásra is szükséged van, a `BleCentralManager.writeCharacteristic()` logikája mellé hozzáadható egy külön descriptor-write útvonal is.

## Indítás

1. Nyisd meg Android Studio-ban a projektet.
2. Várd meg a Gradle sync-et.
3. Futtasd Android 12+ BLE-képes fizikai eszközön.
4. Adj jogosultságot a közeli eszközök elérésére.

## Használat

1. **BLE** fülön kérj jogosultságot és indíts szkennelést.
2. Csatlakozz a kívánt perifériához.
3. **Controller** fülön hozz létre layoutot.
4. Adj hozzá gombot.
5. Szerkesztő módban koppints a gombra, rendelj hozzá characteristicot és hex payloadot.
6. Húzással helyezd el, a jobb alsó saroknál méretezd át.
7. Zárold a layoutot, ekkor a gombnyomás BLE characteristic írást küld.

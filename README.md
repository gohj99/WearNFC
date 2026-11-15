# WearNFC

[English](README.md) | [简体中文](README.zh-CN.md)

WearNFC is an NFC card manager and emulator for Wear OS watches. It includes a watch app and an Android companion app: read a supported card with the phone, save it to the watch, and switch the watch between saved cards from the phone, the watch UI, or a Wear OS Tile.

## Required watch module

> [!IMPORTANT]
> The watch must have the [`galaxy_watch_nfc_provide`](https://github.com/gohj99/galaxy_watch_nfc_provide/) module installed and working before WearNFC can emulate a card. Without this module, the app cannot apply an emulated card to the watch NFC stack.

Install the module on the watch through a compatible root manager, such as KernelSU or BakaSU, and grant WearNFC root access when prompted. The module is a separate project and is not bundled with this repository.

The phone app does **not** require root. Root access is required only on the watch because the watch app uses the module's `nfcconf` command and NFC configuration files to apply a card.

## What it does

- Read a card with the phone NFC reader and send its dump to the paired watch.
- Store card metadata and raw dump data on the watch.
- Add, edit, delete, and select cards from the phone companion app.
- Add and manage cards directly on the watch when the watch NFC reader supports the card.
- Switch the active card and restore the watch's original NFC configuration.
- Provide a Wear OS Tile for quickly moving to the next saved card.
- Keep the phone UI synchronized with the watch through the Wear OS Data Layer.

The watch stores the card list and dump files persistently. The phone displays the list received from the currently connected watch and does not keep an offline card database.

## Requirements

- An NFC-capable Wear OS watch supported by the [`galaxy_watch_nfc_provide`](https://github.com/gohj99/galaxy_watch_nfc_provide/) module.
- The module installed and enabled on the watch.
- Root access on the watch, with root granted to WearNFC.
- An NFC-capable Android phone for phone-based card reading.
- The watch and phone paired through the Wear OS companion connection, with Google Play services available for Data Layer communication.
- Android 11 or newer on both app targets (the project `minSdk` is 30).

## Supported cards and limits

The current reader and watch module support NFC-A cards with a 4-byte UID. MIFARE Classic 1K and 4K cards are supported when their memory can be read.

When reading MIFARE Classic, the phone tries the default key, an all-zero key, and a common `A0 A1 A2 A3 A4 A5` key. Encrypted or otherwise unreadable sectors are left out of the copied data; the app does not claim to have cloned data it could not read. If only the UID is available, the app clearly reports that only the UID was read.

The data exchanged between the phone and watch uses the project's flat dump format:

- MIFARE Classic 1K: 1,024 bytes
- MIFARE Classic 4K: 4,096 bytes
- Maximum saved cards: 100

Cards with a UID other than four bytes, unsupported NFC technologies, or unsupported MIFARE capacities are rejected by the reader.

## Getting started

### 1. Prepare the watch

1. Install and enable [`galaxy_watch_nfc_provide`](https://github.com/gohj99/galaxy_watch_nfc_provide/) on the watch.
2. Grant root access to WearNFC through KernelSU, BakaSU, or the root manager used by the watch.
3. Install and open the WearNFC watch APK once.
4. Open the watch app's emulation settings and select the installed module. The app should report that the NFC module is available.

Do this before adding or switching cards. If the module is missing, the phone can still display the connection state and manage already synchronized metadata, but the watch cannot apply or switch an emulated card.

### 2. Install the phone companion

Install the mobile APK on the paired Android phone and open it. The phone app discovers the reachable watch through the Wear OS Data Layer and shows the watch's confirmed card snapshot after connecting.

### 3. Add and use a card

1. In the phone app, tap **Add card** and hold the card near the phone's NFC antenna.
2. Keep the card still while the reader scans it. Enter a name, optional note, and color, then save it to the watch.
3. Select **Set as current card** to apply it to the watch.
4. Use the watch app or the Wear OS Tile to switch between saved cards.
5. Select **Original NFC** to stop emulation and restore the watch's original NFC configuration.

Restore **Original NFC** before removing or updating the required module. The original configuration can only be restored while the module and root access are still available.

## Building from source

The project contains three Gradle modules: `mobile`, `wear`, and `shared`. It uses Kotlin, Jetpack Compose, and the Wear OS Data Layer.

```bash
# macOS / Linux
./gradlew :mobile:assembleDebug :wear:assembleDebug :shared:test

# Windows
gradlew.bat :mobile:assembleDebug :wear:assembleDebug :shared:test
```

The debug APKs are generated at:

- `mobile/build/outputs/apk/debug/mobile-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

Install the mobile APK on the phone and the wear APK on the watch. Both devices must run APKs built with the same application ID and signing key so that the companion communication can work as expected.

## Project structure

```text
mobile/   Android companion app: NFC reading, card editing, and watch communication
wear/     Wear OS app: card storage, NFC module control, card switching, and Tile
shared/   Shared card models, NFC dump reader, and phone/watch protocol
```

The watch-side card manager persists metadata in DataStore and raw dumps in the app's private `files/ic/` directory. Card switching rewrites the module's NFC routing and NXP configuration from the original backups, refreshes the NFC service, and updates the active-card state only after a successful operation.

## Troubleshooting

### The phone says that the NFC module is unavailable

Confirm that the [`galaxy_watch_nfc_provide`](https://github.com/gohj99/galaxy_watch_nfc_provide/) module is installed and enabled on the watch, that WearNFC has root access, and that the selected module is the one installed on the watch. During startup, the watch app runs `su -c "test -d /data/adb/modules/galaxy_watch_nfc_provide"` to check that the module directory exists. The splash screen stays visible until this check finishes, then the app opens and shows a missing-module dialog if needed. `nfcconf status` is only used to read original configuration backup paths; it does not determine whether the module is installed.

### The phone cannot connect to the watch

Make sure both apps are installed on the paired devices, Google Play services is available, and the watch app has been opened at least once. If more than one watch is reachable, choose the intended watch in the phone app.

### A card reads only its UID

The card may not expose MIFARE Classic memory, or its sectors may use keys that the reader does not know. The unreadable data is intentionally not copied.

### Switching a card fails

Keep the watch awake and confirm that root access and the required module are still available. Switch back to **Original NFC** before changing the module configuration.

## License

This project is distributed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

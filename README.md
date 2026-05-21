# LinkBridge

A lightweight, offline, local-WiFi-only link/text bridge between an Android Phone and an Android TV. No Google services, no cloud, no accounts — just your local network.

## How It Works

1. **TV App** runs an HTTP server on port 9090 and listens for incoming links.
2. **Phone App** registers as a "Share" target. Select any text/link → Share → LinkBridge → sends it to the TV.
3. **TV** receives the link, saves it to history, and automatically opens the "Open With" dialog.
4. **Discovery**: The phone can auto-discover TVs on the same WiFi network via UDP broadcast.

## Features

### TV App
- 🔗 Automatically opens received links with the "Open With" dialog
- 📋 Full history with copy, re-open, and delete
- 🏷️ Auto-tagging: [WEB], [MAGNET], [VIDEO], [TORRENT], [TEXT]
- ⭐ Favorite/star items with filter
- 🔍 Search/filter history
- ✅ Bulk select and delete
- 📤 Export history to JSON
- 🎮 D-pad optimized for TV remote navigation
- 🌙 Dark theme, high contrast, large fonts
- 🔄 Auto-starts on boot

### Phone App
- 📤 Share any text/link from any app
- 🔍 Auto-discovers TVs on local network
- ✏️ Manual IP entry with saved history
- ⚡ Quick Settings Tile: "Send Clipboard to TV"
- 📜 Recent sends for quick resend

## Sideload Installation

Since LinkBridge uses no Google services, you need to sideload the APKs:

### Prerequisites
- Both devices must be on the **same WiFi network**
- Enable "Install from unknown sources" on both devices:
  - **Phone**: Settings → Security → Install unknown apps → Enable
  - **TV**: Settings → Security → Unknown sources → Enable

### Install Steps

1. Download the APKs from [GitHub Releases](../../releases)
2. **TV**: Install `linkbridge-tv-armeabi-v7a.apk` on your Android TV
   - Use a USB drive, or `adb install linkbridge-tv-armeabi-v7a.apk`
3. **Phone**: Install `linkbridge-phone-armeabi-v7a.apk` on your phone
   - Open the APK file after downloading
4. Open **LinkBridge TV** on your TV — note the IP address shown on screen
5. On your phone, select any text → Share → **LinkBridge** → the TV should auto-discover

### Using ADB

```bash
# Install TV app
adb connect <TV_IP_ADDRESS>
adb install linkbridge-tv-armeabi-v7a.apk

# Install Phone app
adb install linkbridge-phone-armeabi-v7a.apk
```

## Network Protocol

- **Discovery**: UDP broadcast on port 9091
- **Transport**: HTTP POST on port 9090
- **Payload**: JSON `{"text": "...", "timestamp": ..., "uuid": "...", "senderDevice": "..."}`
- **No internet required** — works entirely on local WiFi

## Building from Source

```bash
git clone https://github.com/<username>/LinkBridge.git
cd LinkBridge
./gradlew :tv:assembleDebug
./gradlew :phone:assembleDebug
```

APKs will be in:
- `tv/build/outputs/apk/debug/`
- `phone/build/outputs/apk/debug/`

## Requirements

- Android 5.0 (API 21) or higher
- Both devices on the same local WiFi network
- ABI: armeabi-v7a (32-bit ARM)

## License

MIT

# DeckVault for iPhone / iPad

Apple requires macOS + Xcode to compile and code-sign a native `.ipa` app for iOS, so an `.ipa` cannot be compiled on Windows. You have **two ways** to use DeckVault on your iPhone right now:

## 1. Zero-Install iPhone Safari Web Companion (Works Immediately)
Every Windows PC running `DeckVaultCompanion.exe` and every Android device running DeckVault hosts a mobile Web Companion on port `48720`:
1. On your iPhone, connect to the same Wi-Fi network.
2. Open **Safari** and go to `http://<PC-or-Tablet-LAN-IP>:48720/`.
3. Enter the 6-digit PIN.
4. Browse all drives, stream videos, listen to audio, open PDFs/text/images, and tap **+ Upload Photos / Files** to pick directly from your iPhone Photo Library or Files app and send them into any folder/drive.

## 2. Native iOS App (Built From `C:\DeckVault\ios\DeckVaultApp`)
This folder contains the full native SwiftUI + Peer Server source:
- `DeckVaultApp.swift`
- `DeckVaultPeerServer.swift` (`NWListener` TCP `:48720` + UDP `:48721` + `PhotoKit` `/Photos Library` + Files `/Files Documents`)
- `DeckVaultBrowserView.swift` (Browse PC/Android, All Drives capacity view, PhotosPicker upload, AVKit video playback)
- `Info.plist` (Photos, Local Network, Files sharing, Local HTTP permissions)

### How to Build on macOS or GitHub Actions
1. Create a new **iOS App (SwiftUI)** project named `DeckVault` in Xcode 15+ on a Mac.
2. Drop the `.swift` files and `Info.plist` from `DeckVaultApp/` into the project target (Deployment Target: iOS 16.0+).
3. Enable **Privacy - Photo Library**, **Local Network**, and **File Sharing** from `Info.plist`.
4. Hit **Run** on your iPhone (free Apple ID personal signing lasts 7 days, or AltStore/Sideloadly for `.ipa` sideloading).

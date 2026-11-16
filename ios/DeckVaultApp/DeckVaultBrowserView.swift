import AVKit
import PhotosUI
import SwiftUI

struct SavedHostIOS: Codable, Identifiable, Hashable {
    var id: String { hostId }
    let hostId: String
    let name: String
    let ip: String
    let port: Int
    let token: String
    let os: String
}

struct DriveSnapshotIOS: Identifiable {
    let id = UUID()
    let hostName: String
    let os: String
    let drives: [[String: Any]]
}

final class DeckVaultClientStore: ObservableObject {
    @Published var savedHosts: [SavedHostIOS] = []
    @Published var activeHost: SavedHostIOS?
    @Published var drives: [[String: Any]] = []
    @Published var folders: [[String: Any]] = []
    @Published var files: [[String: Any]] = []
    @Published var currentPath: String = ""
    @Published var allSnapshots: [DriveSnapshotIOS] = []
    @Published var statusMsg: String = ""

    private let key = "dv_saved_hosts_v1"

    func loadSavedHosts() {
        guard let raw = UserDefaults.standard.data(forKey: key),
              let list = try? JSONDecoder().decode([SavedHostIOS].self, from: raw) else { return }
        savedHosts = list
        if let first = list.first { selectHost(first) }
    }

    func pair(ip: String, port: Int, pin: String) async {
        guard let url = URL(string: "http://\(ip):\(port)/api/pair") else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["pin": pin, "clientName": UIDevice.current.name])
        do {
            let (data, _) = try await URLSession.shared.data(for: req)
            if let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let token = obj["token"] as? String {
                let host = SavedHostIOS(
                    hostId: (obj["hostId"] as? String) ?? UUID().uuidString,
                    name: (obj["deviceName"] as? String) ?? ip,
                    ip: ip,
                    port: port,
                    token: token,
                    os: (obj["os"] as? String) ?? "Windows",
                )
                await MainActor.run {
                    savedHosts.removeAll { $0.hostId == host.hostId }
                    savedHosts.insert(host, at: 0)
                    if let encoded = try? JSONEncoder().encode(savedHosts) {
                        UserDefaults.standard.set(encoded, forKey: key)
                    }
                    selectHost(host)
                }
            }
        } catch {
            await MainActor.run { statusMsg = "Pair failed: \(error.localizedDescription)" }
        }
    }

    func selectHost(_ host: SavedHostIOS) {
        activeHost = host
        Task { await loadRoots(for: host) }
    }

    func loadRoots(for host: SavedHostIOS) async {
        guard let url = URL(string: "http://\(host.ip):\(host.port)/api/roots") else { return }
        var req = URLRequest(url: url)
        req.setValue(host.token, forHTTPHeaderField: "X-DeckVault-Token")
        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        let dr = (obj["drives"] as? [[String: Any]]) ?? []
        let qf = (obj["quickFolders"] as? [[String: Any]]) ?? []
        await MainActor.run { self.drives = dr }
        if let first = (qf.first?["path"] as? String) ?? (dr.first?["path"] as? String) {
            await loadList(host: host, path: first)
        }
    }

    func loadList(host: SavedHostIOS, path: String) async {
        guard let enc = path.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "http://\(host.ip):\(host.port)/api/list?path=\(enc)&sort=size&order=desc&filter=all") else { return }
        var req = URLRequest(url: url)
        req.setValue(host.token, forHTTPHeaderField: "X-DeckVault-Token")
        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
        await MainActor.run {
            self.currentPath = path
            self.folders = (obj["folders"] as? [[String: Any]]) ?? []
            self.files = (obj["files"] as? [[String: Any]]) ?? []
        }
    }

    func refreshAllDrives(localFree: Int64, localTotal: Int64) async {
        var snaps: [DriveSnapshotIOS] = [
            DriveSnapshotIOS(
                hostName: "\(UIDevice.current.name) (This iPhone)",
                os: "iOS",
                drives: [["name": "iPhone Storage", "path": "/Photos Library", "freeBytes": localFree, "totalBytes": localTotal]],
            )
        ]
        for host in savedHosts {
            guard let url = URL(string: "http://\(host.ip):\(host.port)/api/roots") else { continue }
            var req = URLRequest(url: url)
            req.setValue(host.token, forHTTPHeaderField: "X-DeckVault-Token")
            if let (data, _) = try? await URLSession.shared.data(for: req),
               let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let dr = obj["drives"] as? [[String: Any]] {
                snaps.append(DriveSnapshotIOS(hostName: host.name, os: (obj["os"] as? String) ?? host.os, drives: dr))
            }
        }
        await MainActor.run { self.allSnapshots = snaps }
    }

    func uploadPhotoData(_ data: Data, filename: String) async {
        guard let host = activeHost, !currentPath.isEmpty,
              let dEnc = currentPath.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let nEnc = filename.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "http://\(host.ip):\(host.port)/api/file/upload?dir=\(dEnc)&name=\(nEnc)&conflict=rename") else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(host.token, forHTTPHeaderField: "X-DeckVault-Token")
        req.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        req.httpBody = data
        _ = try? await URLSession.shared.data(for: req)
        await loadList(host: host, path: currentPath)
    }
}

struct DeckVaultRootView: View {
    @ObservedObject var peerServer: DeckVaultPeerServer
    @ObservedObject var store: DeckVaultClientStore
    @State private var ipInput = ""
    @State private var pinInput = ""
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var activeStreamURL: URL?

    var body: some View {
        TabView {
            NavigationStack {
                List {
                    Section("Pair PC, Laptop, Fold or Tab") {
                        TextField("Host IP (e.g. 192.168.1.42)", text: $ipInput).keyboardType(.decimalPad)
                        TextField("6-Digit PIN", text: $pinInput).keyboardType(.numberPad)
                        Button("Pair & Connect") {
                            Task { await store.pair(ip: ipInput.trimmingCharacters(in: .whitespaces), port: 48720, pin: pinInput.trimmingCharacters(in: .whitespaces)) }
                        }
                    }

                    if !store.savedHosts.isEmpty {
                        Section("Connected Devices") {
                            ForEach(store.savedHosts) { h in
                                Button(h.name) { store.selectHost(h) }
                            }
                        }
                    }

                    if store.activeHost != nil {
                        Section("Folders & Files (\(store.currentPath))") {
                            PhotosPicker(selection: $pickerItems, matching: .any(of: [.images, .videos])) {
                                Label("Send iPhone Photos & Videos Here", systemImage: "photo.on.rectangle.angled")
                            }
                            ForEach(0..<store.folders.count, id: \.self) { idx in
                                let name = (store.folders[idx]["name"] as? String) ?? "Folder"
                                let path = (store.folders[idx]["path"] as? String) ?? ""
                                Button {
                                    if let h = store.activeHost { Task { await store.loadList(host: h, path: path) } }
                                } label: { Label(name, systemImage: "folder") }
                            }
                            ForEach(0..<store.files.count, id: \.self) { idx in
                                let f = store.files[idx]
                                let name = (f["name"] as? String) ?? ""
                                let path = (f["path"] as? String) ?? ""
                                Button {
                                    if let h = store.activeHost,
                                       let enc = path.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                                       let tEnc = h.token.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
                                        activeStreamURL = URL(string: "http://\(h.ip):\(h.port)/api/stream?path=\(enc)&token=\(tEnc)")
                                    }
                                } label: { Label(name, systemImage: "play.rectangle") }
                            }
                        }
                    }
                }
                .navigationTitle("DeckVault")
                .onChange(of: pickerItems) { _, items in
                    Task {
                        for item in items {
                            if let data = try? await item.loadTransferable(type: Data.self) {
                                await store.uploadPhotoData(data, filename: "IMG_\(Int(Date().timeIntervalSince1970)).jpg")
                            }
                        }
                        pickerItems.removeAll()
                    }
                }
                .sheet(item: Binding(get: { activeStreamURL.map { URLWrapper(url: $0) } }, set: { activeStreamURL = $0?.url })) { w in
                    VideoPlayer(player: AVPlayer(url: w.url)).ignoresSafeArea()
                }
            }
            .tabItem { Label("Browse", systemImage: "folder.fill") }

            NavigationStack {
                List(store.allSnapshots) { snap in
                    Section("\(snap.hostName) (\(snap.os))") {
                        ForEach(0..<snap.drives.count, id: \.self) { idx in
                            let dr = snap.drives[idx]
                            let name = (dr["name"] as? String) ?? "Drive"
                            let free = ByteCountFormatter.string(fromByteCount: (dr["freeBytes"] as? NSNumber)?.int64Value ?? 0, countStyle: .file)
                            let total = ByteCountFormatter.string(fromByteCount: (dr["totalBytes"] as? NSNumber)?.int64Value ?? 0, countStyle: .file)
                            HStack {
                                Text(name)
                                Spacer()
                                Text("\(free) free / \(total)").foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .navigationTitle("All Drives")
                .onAppear {
                    Task { await store.refreshAllDrives(localFree: DeckVaultPeerServer.freeDiskBytes(), localTotal: DeckVaultPeerServer.totalDiskBytes()) }
                }
            }
            .tabItem { Label("All Drives", systemImage: "internaldrive.fill") }

            NavigationStack {
                List {
                    Section("Share This iPhone With PC, Fold & Tab") {
                        LabeledContent("Status", value: peerServer.isRunning ? "Online (:48720)" : "Starting...")
                        LabeledContent("LAN IP", value: peerServer.ips.first ?? "Wi-Fi IP unavailable")
                        LabeledContent("6-Digit PIN", value: peerServer.pin)
                        Button("Generate New PIN") { peerServer.rotatePin() }
                    }
                }
                .navigationTitle("iPhone Server")
            }
            .tabItem { Label("Share", systemImage: "antenna.radiowaves.left.and.right") }
        }
    }
}

private struct URLWrapper: Identifiable {
    let id = UUID()
    let url: URL
}

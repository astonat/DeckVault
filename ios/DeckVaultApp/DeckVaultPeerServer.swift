import Foundation
import Network
import Photos
import UIKit

/// Native iOS HTTP + UDP discovery peer server compatible with DeckVault Android & Windows Companion.
/// Exposes:
/// - `/Photos Library` (iPhone Photos & Videos via PhotoKit)
/// - `/Files Documents` (app Documents folder visible in iOS Files app)
final class DeckVaultPeerServer: ObservableObject {
    static let httpPort: UInt16 = 48720
    static let udpPort: UInt16 = 48721

    @Published var isRunning = false
    @Published var pin: String
    @Published var ips: [String] = []
    @Published var photosAuthorized = false

    let hostId: String
    let deviceName: String

    private var tcpListener: NWListener?
    private var udpListener: NWListener?
    private var tokens: Set<String> = []
    private let queue = DispatchQueue(label: "com.deckvault.ios.peer", qos: .userInitiated)

    init() {
        let defaults = UserDefaults.standard
        if let saved = defaults.string(forKey: "dv_host_id") {
            self.hostId = saved
        } else {
            let fresh = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(16).lowercased()
            defaults.set(fresh, forKey: "dv_host_id")
            self.hostId = String(fresh)
        }
        self.deviceName = UIDevice.current.name
        if let savedPin = defaults.string(forKey: "dv_pin"), savedPin.count == 6 {
            self.pin = savedPin
        } else {
            let freshPin = String(format: "%06d", Int.random(in: 0...999999))
            defaults.set(freshPin, forKey: "dv_pin")
            self.pin = freshPin
        }
        if let arr = defaults.stringArray(forKey: "dv_tokens") {
            self.tokens = Set(arr)
        }
    }

    func rotatePin() {
        let freshPin = String(format: "%06d", Int.random(in: 0...999999))
        UserDefaults.standard.set(freshPin, forKey: "dv_pin")
        DispatchQueue.main.async { self.pin = freshPin }
    }

    func requestPhotosPermission() {
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
            DispatchQueue.main.async {
                self.photosAuthorized = (status == .authorized || status == .limited)
            }
        }
    }

    func start() {
        guard tcpListener == nil else { return }
        requestPhotosPermission()
        ips = Self.localIPv4Addresses()

        do {
            let listener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: Self.httpPort)!)
            listener.newConnectionHandler = { [weak self] conn in
                self?.handleTCP(conn)
            }
            listener.stateUpdateHandler = { [weak self] state in
                DispatchQueue.main.async {
                    self?.isRunning = (state == .ready)
                }
            }
            listener.start(queue: queue)
            tcpListener = listener
        } catch {
            print("DeckVault HTTP listener failed: \(error)")
        }

        do {
            let udp = try NWListener(using: .udp, on: NWEndpoint.Port(rawValue: Self.udpPort)!)
            udp.newConnectionHandler = { [weak self] conn in
                conn.start(queue: self?.queue ?? .global())
                conn.receiveMessage { data, _, _, _ in
                    guard let data = data,
                          let msg = String(data: data, encoding: .utf8),
                          msg.hasPrefix("DECKVAULT_DISCOVER_V1") else { return }
                    let reply: [String: Any] = [
                        "service": "DeckVault",
                        "version": "1.0.0",
                        "hostId": self?.hostId ?? "",
                        "deviceName": self?.deviceName ?? "iPhone",
                        "os": "iOS",
                        "port": Int(Self.httpPort),
                        "ips": Self.localIPv4Addresses(),
                        "freeBytes": Self.freeDiskBytes(),
                    ]
                    if let payload = try? JSONSerialization.data(withJSONObject: reply) {
                        conn.send(content: payload, completion: .contentProcessed { _ in conn.cancel() })
                    }
                }
            }
            udp.start(queue: queue)
            udpListener = udp
        } catch {
            print("DeckVault UDP listener failed: \(error)")
        }
    }

    func stop() {
        tcpListener?.cancel()
        tcpListener = nil
        udpListener?.cancel()
        udpListener = nil
        DispatchQueue.main.async { self.isRunning = false }
    }

    private func handleTCP(_ conn: NWConnection) {
        conn.start(queue: queue)
        receiveHTTPRequest(on: conn, buffer: Data())
    }

    private func receiveHTTPRequest(on conn: NWConnection, buffer: Data) {
        conn.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] chunk, _, isEOF, error in
            guard let self = self else { conn.cancel(); return }
            var acc = buffer
            if let chunk = chunk { acc.append(chunk) }

            if let headerRange = acc.range(of: Data("\r\n\r\n".utf8)) {
                let headerData = acc[..<headerRange.lowerBound]
                let bodyAlreadyRead = acc[headerRange.upperBound...]
                guard let headerStr = String(data: headerData, encoding: .utf8) else {
                    conn.cancel()
                    return
                }
                let lines = headerStr.components(separatedBy: "\r\n")
                let requestParts = lines.first?.components(separatedBy: " ") ?? []
                let method = requestParts.count > 0 ? requestParts[0] : "GET"
                let target = requestParts.count > 1 ? requestParts[1] : "/"

                var headers: [String: String] = [:]
                for line in lines.dropFirst() {
                    if let idx = line.firstIndex(of: ":") {
                        let k = line[..<idx].trimmingCharacters(in: .whitespaces).lowercased()
                        let v = line[line.index(after: idx)...].trimmingCharacters(in: .whitespaces)
                        headers[k] = v
                    }
                }
                let contentLength = Int(headers["content-length"] ?? "0") ?? 0
                if bodyAlreadyRead.count < contentLength && !isEOF && error == nil {
                    self.receiveBody(on: conn, method: method, target: target, headers: headers, body: Data(bodyAlreadyRead), remaining: contentLength - bodyAlreadyRead.count)
                } else {
                    self.dispatch(conn: conn, method: method, target: target, headers: headers, body: Data(bodyAlreadyRead))
                }
            } else if !isEOF && error == nil && acc.count < 65536 {
                self.receiveHTTPRequest(on: conn, buffer: acc)
            } else {
                conn.cancel()
            }
        }
    }

    private func receiveBody(on conn: NWConnection, method: String, target: String, headers: [String: String], body: Data, remaining: Int) {
        conn.receive(minimumIncompleteLength: 1, maximumLength: min(remaining, 131072)) { [weak self] chunk, _, isEOF, error in
            guard let self = self else { conn.cancel(); return }
            var next = body
            var rem = remaining
            if let chunk = chunk {
                next.append(chunk)
                rem = max(0, remaining - chunk.count)
            }
            if rem > 0 && !isEOF && error == nil {
                self.receiveBody(on: conn, method: method, target: target, headers: headers, body: next, remaining: rem)
            } else {
                self.dispatch(conn: conn, method: method, target: target, headers: headers, body: next)
            }
        }
    }

    private func dispatch(conn: NWConnection, method: String, target: String, headers: [String: String], body: Data) {
        let components = URLComponents(string: "http://localhost" + target)
        let route = components?.path ?? "/"
        let qs = Dictionary(
            uniqueKeysWithValues: (components?.queryItems ?? []).map { ($0.name, $0.value ?? "") }
        )

        if route == "/api/ping" {
            sendJSON(conn: conn, status: 200, obj: [
                "service": "DeckVault",
                "version": "1.0.0",
                "hostId": hostId,
                "deviceName": deviceName,
                "os": "iOS",
                "ffmpeg": false,
                "spaceSavedBytes": 0,
                "indexer": ["scanning": false, "scannedFiles": 0, "scannedDirs": 0, "lastScanMs": 0],
            ])
            return
        }

        if route == "/api/pair" && method == "POST" {
            let req = (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:]
            guard (req["pin"] as? String) == pin else {
                sendJSON(conn: conn, status: 401, obj: ["error": "Invalid PIN"])
                return
            }
            let token = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
            tokens.insert(token)
            UserDefaults.standard.set(Array(tokens), forKey: "dv_tokens")
            sendJSON(conn: conn, status: 200, obj: [
                "token": token,
                "hostId": hostId,
                "deviceName": deviceName,
                "os": "iOS",
                "ffmpeg": false,
                "spaceSavedBytes": 0,
            ])
            return
        }

        let authToken = headers["x-deckvault-token"] ?? qs["token"] ?? ""
        guard tokens.contains(authToken) else {
            sendJSON(conn: conn, status: 401, obj: ["error": "Unauthorized"])
            return
        }

        switch route {
        case "/api/roots":
            let docs = Self.documentsURL().path
            let free = Self.freeDiskBytes()
            let total = Self.totalDiskBytes()
            sendJSON(conn: conn, status: 200, obj: [
                "deviceName": deviceName,
                "hostId": hostId,
                "os": "iOS",
                "ffmpeg": false,
                "spaceSavedBytes": 0,
                "indexer": ["scanning": false, "scannedFiles": 0, "scannedDirs": 0, "lastScanMs": 0],
                "drives": [[
                    "name": "iPhone Storage",
                    "path": docs,
                    "totalBytes": total,
                    "usedBytes": max(0, total - free),
                    "freeBytes": free,
                    "indexedSizeBytes": -1,
                    "fileCount": -1,
                    "mediaCount": -1,
                ]],
                "quickFolders": [
                    ["name": "Photos Library", "path": "/Photos Library", "sizeBytes": -1, "fileCount": -1, "mediaCount": -1],
                    ["name": "Files Documents", "path": docs, "sizeBytes": -1, "fileCount": -1, "mediaCount": -1],
                ],
            ])

        case "/api/tree":
            sendJSON(conn: conn, status: 200, obj: ["path": qs["path"] ?? "/Photos Library", "parentSizeBytes": 0, "children": []])

        case "/api/list":
            let reqPath = qs["path"] ?? "/Photos Library"
            if reqPath == "/Photos Library" {
                listPhotosLibrary(conn: conn)
            } else {
                listDocumentsFolder(conn: conn, path: reqPath)
            }

        case "/api/image", "/api/video/thumbnail":
            let assetId = qs["path"] ?? ""
            if assetId.hasPrefix("ph://") {
                servePhotoAssetThumbnail(conn: conn, localIdentifier: String(assetId.dropFirst(5)), width: Int(qs["w"] ?? "480") ?? 480)
            } else {
                serveLocalFile(conn: conn, path: assetId, rangeHeader: headers["range"])
            }

        case "/api/stream":
            let reqPath = qs["path"] ?? ""
            if reqPath.hasPrefix("ph://") {
                servePhotoAssetOriginal(conn: conn, localIdentifier: String(reqPath.dropFirst(5)), rangeHeader: headers["range"])
            } else {
                serveLocalFile(conn: conn, path: reqPath, rangeHeader: headers["range"])
            }

        case "/api/file/upload":
            let fileName = (qs["name"] ?? "upload.bin").replacingOccurrences(of: "/", with: "_")
            let targetURL = Self.documentsURL().appendingPathComponent(fileName)
            do {
                try body.write(to: targetURL)
                sendJSON(conn: conn, status: 200, obj: ["ok": true, "path": targetURL.path, "name": fileName, "sizeBytes": body.count])
            } catch {
                sendJSON(conn: conn, status: 500, obj: ["error": error.localizedDescription])
            }

        default:
            sendJSON(conn: conn, status: 404, obj: ["error": "Unsupported route"])
        }
    }

    private func listPhotosLibrary(conn: NWConnection) {
        let fetchOpts = PHFetchOptions()
        fetchOpts.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        fetchOpts.fetchLimit = 300
        let assets = PHAsset.fetchAssets(with: fetchOpts)
        var items: [[String: Any]] = []
        assets.enumerateObjects { asset, _, _ in
            let isVideo = asset.mediaType == .video
            let resources = PHAssetResource.assetResources(for: asset)
            let name = resources.first?.originalFilename ?? "IMG_\(asset.localIdentifier.prefix(8)).\(isVideo ? "mov" : "jpg")"
            let size = (resources.first?.value(forKey: "fileSize") as? Int64) ?? 0
            items.append([
                "name": name,
                "path": "ph://\(asset.localIdentifier)",
                "mediaType": isVideo ? "video" : "image",
                "extension": isVideo ? "mov" : "jpg",
                "sizeBytes": size,
                "modifiedMs": Int64((asset.modificationDate?.timeIntervalSince1970 ?? 0) * 1000),
                "meta": isVideo ? [
                    "durationMs": Int64(asset.duration * 1000),
                    "width": asset.pixelWidth,
                    "height": asset.pixelHeight,
                ] : NSNull(),
            ])
        }
        sendJSON(conn: conn, status: 200, obj: [
            "path": "/Photos Library",
            "name": "Photos Library",
            "parentPath": "",
            "totalSizeBytes": -1,
            "fileCount": items.count,
            "mediaCount": items.count,
            "diskFreeBytes": Self.freeDiskBytes(),
            "diskTotalBytes": Self.totalDiskBytes(),
            "indexer": ["scanning": false, "scannedFiles": 0, "scannedDirs": 0, "lastScanMs": 0],
            "folders": [],
            "files": items,
        ])
    }

    private func listDocumentsFolder(conn: NWConnection, path: String) {
        let url = URL(fileURLWithPath: path)
        let contents = (try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isDirectoryKey, .fileSizeKey, .contentModificationDateKey])) ?? []
        var folders: [[String: Any]] = []
        var files: [[String: Any]] = []
        for item in contents {
            let vals = try? item.resourceValues(forKeys: [.isDirectoryKey, .fileSizeKey, .contentModificationDateKey])
            if vals?.isDirectory == true {
                folders.append(["name": item.lastPathComponent, "path": item.path, "sizeBytes": -1, "fileCount": -1, "mediaCount": -1])
            } else {
                let ext = "." + item.pathExtension.lowercased()
                let mtype = [".mp4", ".mov", ".m4v"].contains(ext) ? "video" :
                    ([".jpg", ".jpeg", ".png", ".heic", ".webp"].contains(ext) ? "image" :
                    ([".mp3", ".m4a", ".wav", ".aac"].contains(ext) ? "audio" : "other"))
                files.append([
                    "name": item.lastPathComponent,
                    "path": item.path,
                    "mediaType": mtype,
                    "extension": item.pathExtension.lowercased(),
                    "sizeBytes": Int64(vals?.fileSize ?? 0),
                    "modifiedMs": Int64((vals?.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000),
                ])
            }
        }
        sendJSON(conn: conn, status: 200, obj: [
            "path": url.path,
            "name": url.lastPathComponent,
            "parentPath": "",
            "totalSizeBytes": -1,
            "fileCount": files.count,
            "mediaCount": files.count,
            "diskFreeBytes": Self.freeDiskBytes(),
            "diskTotalBytes": Self.totalDiskBytes(),
            "indexer": ["scanning": false, "scannedFiles": 0, "scannedDirs": 0, "lastScanMs": 0],
            "folders": folders,
            "files": files,
        ])
    }

    private func servePhotoAssetThumbnail(conn: NWConnection, localIdentifier: String, width: Int) {
        let fetch = PHAsset.fetchAssets(withLocalIdentifiers: [localIdentifier], options: nil)
        guard let asset = fetch.firstObject else { sendJSON(conn: conn, status: 404, obj: ["error": "Not found"]); return }
        let opts = PHImageRequestOptions()
        opts.isSynchronous = false
        opts.isNetworkAccessAllowed = true
        opts.deliveryMode = .highQualityFormat
        PHImageManager.default().requestImage(for: asset, targetSize: CGSize(width: width, height: width), contentMode: .aspectFit, options: opts) { image, _ in
            guard let jpg = image?.jpegData(compressionQuality: 0.85) else {
                self.sendJSON(conn: conn, status: 404, obj: ["error": "Thumbnail unavailable"])
                return
            }
            self.sendData(conn: conn, status: 200, contentType: "image/jpeg", data: jpg)
        }
    }

    private func servePhotoAssetOriginal(conn: NWConnection, localIdentifier: String, rangeHeader: String?) {
        let fetch = PHAsset.fetchAssets(withLocalIdentifiers: [localIdentifier], options: nil)
        guard let asset = fetch.firstObject, let resource = PHAssetResource.assetResources(for: asset).first else {
            sendJSON(conn: conn, status: 404, obj: ["error": "Not found"])
            return
        }
        let temp = FileManager.default.temporaryDirectory.appendingPathComponent("dv_ph_\(UUID().uuidString)_\(resource.originalFilename)")
        let opts = PHAssetResourceRequestOptions()
        opts.isNetworkAccessAllowed = true
        PHAssetResourceManager.default().writeData(for: resource, toFile: temp, options: opts) { err in
            if let err = err {
                self.sendJSON(conn: conn, status: 500, obj: ["error": err.localizedDescription])
                return
            }
            self.serveLocalFile(conn: conn, path: temp.path, rangeHeader: rangeHeader)
        }
    }

    private func serveLocalFile(conn: NWConnection, path: String, rangeHeader: String?) {
        guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)) else {
            sendJSON(conn: conn, status: 404, obj: ["error": "File not found"])
            return
        }
        let total = data.count
        if let rangeHeader = rangeHeader, rangeHeader.hasPrefix("bytes="), total > 0 {
            let spec = rangeHeader.dropFirst(6).split(separator: "-").map(String.init)
            let start = max(0, Int(spec.first ?? "0") ?? 0)
            let end = min(total - 1, spec.count > 1 && !spec[1].isEmpty ? (Int(spec[1]) ?? total - 1) : total - 1)
            let slice = data[start...end]
            let hdr = "HTTP/1.1 206 Partial Content\r\nContent-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\nContent-Range: bytes \(start)-\(end)/\(total)\r\nContent-Length: \(slice.count)\r\n\r\n"
            var full = Data(hdr.utf8)
            full.append(slice)
            conn.send(content: full, completion: .contentProcessed { _ in conn.cancel() })
        } else {
            sendData(conn: conn, status: 200, contentType: "application/octet-stream", data: data)
        }
    }

    private func sendJSON(conn: NWConnection, status: Int, obj: Any) {
        let data = (try? JSONSerialization.data(withJSONObject: obj)) ?? Data("{}".utf8)
        sendData(conn: conn, status: status, contentType: "application/json; charset=utf-8", data: data)
    }

    private func sendData(conn: NWConnection, status: Int, contentType: String, data: Data) {
        let reason = status == 200 ? "OK" : (status == 401 ? "Unauthorized" : "Error")
        let header = "HTTP/1.1 \(status) \(reason)\r\nContent-Type: \(contentType)\r\nContent-Length: \(data.count)\r\nAccept-Ranges: bytes\r\nCache-Control: no-store\r\n\r\n"
        var full = Data(header.utf8)
        full.append(data)
        conn.send(content: full, completion: .contentProcessed { _ in conn.cancel() })
    }

    static func documentsURL() -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    static func freeDiskBytes() -> Int64 {
        let vals = try? documentsURL().resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        return vals?.volumeAvailableCapacityForImportantUsage ?? 0
    }

    static func totalDiskBytes() -> Int64 {
        let vals = try? documentsURL().resourceValues(forKeys: [.volumeTotalCapacityKey])
        return Int64(vals?.volumeTotalCapacity ?? 0)
    }

    static func localIPv4Addresses() -> [String] {
        var result: [String] = []
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        if getifaddrs(&ifaddr) == 0 {
            var ptr = ifaddr
            while ptr != nil {
                defer { ptr = ptr?.pointee.ifa_next }
                guard let interface = ptr?.pointee,
                      interface.ifa_addr.pointee.sa_family == UInt8(AF_INET) else { continue }
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                if getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len), &hostname, socklen_t(hostname.count), nil, 0, NI_NUMERICHOST) == 0 {
                    let ip = String(cString: hostname)
                    if ip != "127.0.0.1" && !ip.hasPrefix("169.254.") { result.append(ip) }
                }
            }
            freeifaddrs(ifaddr)
        }
        return result
    }
}

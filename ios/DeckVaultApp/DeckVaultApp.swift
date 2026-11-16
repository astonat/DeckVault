import SwiftUI

@main
struct DeckVaultApp: App {
    @StateObject private var peerServer = DeckVaultPeerServer()
    @StateObject private var clientStore = DeckVaultClientStore()

    var body: some Scene {
        WindowGroup {
            DeckVaultRootView(peerServer: peerServer, store: clientStore)
                .preferredColorScheme(.dark)
                .onAppear {
                    peerServer.start()
                    clientStore.loadSavedHosts()
                }
        }
    }
}

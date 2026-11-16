"""Single-instance launcher for DeckVault Desktop & Companion Tray."""
import sys
import deckvault_server

if __name__ == "__main__":
    if len(sys.argv) == 1:
        sys.argv.append("--app")
    deckvault_server.main()

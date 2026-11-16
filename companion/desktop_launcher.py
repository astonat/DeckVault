"""Single-instance launcher for DeckVault Desktop & Companion Tray."""
import os
import sys

_DEV_COMPANION_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "companion")
if os.path.isfile(os.path.join(_DEV_COMPANION_DIR, "deckvault_server.py")):
    sys.path.insert(0, _DEV_COMPANION_DIR)
elif os.path.isfile(r"C:\DeckVault\companion\deckvault_server.py"):
    sys.path.insert(0, r"C:\DeckVault\companion")

import deckvault_server

if __name__ == "__main__":
    if len(sys.argv) == 1:
        sys.argv.append("--app")
    deckvault_server.main()

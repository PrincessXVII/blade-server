#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PACK_PORT:-8080}"

if [[ ! -f "$ROOT/resourcepack/BladePack.zip" ]]; then
  "$ROOT/build-blade-pack.sh"
fi

cd "$ROOT/resourcepack"
echo "Serving BladePack.zip at http://0.0.0.0:${PORT}/BladePack.zip"
python3 -m http.server "$PORT" --bind 0.0.0.0

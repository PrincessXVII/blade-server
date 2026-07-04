#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT/blade.env"
PROPS="$ROOT/server.properties"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

if [[ -z "${RESOURCE_PACK_URL:-}" ]]; then
  echo "RESOURCE_PACK_URL is not set in blade.env" >&2
  exit 1
fi

SHA1=""
if command -v shasum >/dev/null 2>&1 && [[ -f "$ROOT/resourcepack/BladePack.zip" ]]; then
  SHA1=$(shasum -a 1 "$ROOT/resourcepack/BladePack.zip" | awk '{print $1}')
fi

python3 - <<PY
from pathlib import Path

props_path = Path("$PROPS")
lines = props_path.read_text().splitlines()
updates = {
    "resource-pack": "$RESOURCE_PACK_URL",
    "resource-pack-prompt": "Blade Resource Pack",
}
if "$SHA1":
    updates["resource-pack-sha1"] = "$SHA1"

out = []
seen = set()
for line in lines:
    key = line.split("=", 1)[0] if "=" in line else line
    if key in updates:
        out.append(f"{key}={updates[key]}")
        seen.add(key)
    else:
        out.append(line)

for key, value in updates.items():
    if key not in seen:
        out.append(f"{key}={value}")

props_path.write_text("\n".join(out) + "\n")
print(f"Updated resource-pack URL in {props_path}")
if "$SHA1":
    print(f"SHA1: $SHA1")
PY

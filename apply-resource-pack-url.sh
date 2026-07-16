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

if [[ "${LOCAL_RESOURCE_PACK:-false}" == "true" ]]; then
  RESOURCE_PACK_URL="${LOCAL_RESOURCE_PACK_URL:-http://127.0.0.1:8080/BladePack.zip}"
else
  REPO_ROOT="$(git -C "$ROOT" rev-parse --show-toplevel 2>/dev/null || true)"
  if [[ -n "${RESOURCE_PACK_GITHUB_REPO:-}" && -n "${RESOURCE_PACK_GITHUB_PATH:-}" && -n "$REPO_ROOT" ]]; then
    COMMIT="$(git -C "$REPO_ROOT" rev-parse HEAD)"
    # jsDelivr serves the same GitHub file; raw.githubusercontent.com often hangs Minecraft clients.
    RESOURCE_PACK_CDN="${RESOURCE_PACK_CDN:-jsdelivr}"
    case "$RESOURCE_PACK_CDN" in
      raw)
        RESOURCE_PACK_URL="https://raw.githubusercontent.com/${RESOURCE_PACK_GITHUB_REPO}/${COMMIT}/${RESOURCE_PACK_GITHUB_PATH}"
        ;;
      *)
        RESOURCE_PACK_URL="https://cdn.jsdelivr.net/gh/${RESOURCE_PACK_GITHUB_REPO}@${COMMIT}/${RESOURCE_PACK_GITHUB_PATH}"
        ;;
    esac
  elif [[ -z "${RESOURCE_PACK_URL:-}" ]]; then
    echo "Set LOCAL_RESOURCE_PACK=true or RESOURCE_PACK_GITHUB_REPO/PATH or RESOURCE_PACK_URL in blade.env" >&2
    exit 1
  fi
fi

SHA1=""
if command -v shasum >/dev/null 2>&1 && [[ -f "$ROOT/resourcepack/BladePack.zip" ]]; then
  SHA1=$(shasum -a 1 "$ROOT/resourcepack/BladePack.zip" | awk '{print $1}')
fi

python3 - <<PY
import uuid
from pathlib import Path

props_path = Path("$PROPS")
lines = props_path.read_text().splitlines()
prompt = '{"text":"Blade Resource Pack","color":"gold"}'
sha1 = "$SHA1"
pack_id = str(uuid.uuid5(uuid.NAMESPACE_URL, sha1)) if sha1 else ""
updates = {
    "resource-pack": "$RESOURCE_PACK_URL",
    "resource-pack-prompt": prompt,
    "require-resource-pack": "false",
}
if sha1:
    updates["resource-pack-sha1"] = sha1
    updates["resource-pack-id"] = pack_id

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
print(f"URL: {updates['resource-pack']}")
if sha1:
    print(f"SHA1: {sha1}")
    print(f"Pack ID: {pack_id}")
PY

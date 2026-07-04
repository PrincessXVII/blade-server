#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MC_VERSION="${MC_VERSION:-1.21.11}"
USER_AGENT="${USER_AGENT:-local-duels-setup/1.0 (paper updater)}"
BUILDS_JSON_FILE=".paper-builds-${MC_VERSION}.json"

set +e
curl -fsSL -H "User-Agent: ${USER_AGENT}" "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds" -o "${BUILDS_JSON_FILE}"
CURL_STATUS=$?
if [ $CURL_STATUS -ne 0 ]; then
  set -e
  echo "Failed to resolve Paper build for version $MC_VERSION"
  echo "Could not fetch builds list from Paper API"
  echo "Tip: try another version, for example:"
  echo "  MC_VERSION=1.21.10 ./update-paper.sh"
  exit 1
fi

BUILD_INFO="$(python3 - "${BUILDS_JSON_FILE}" <<'PY'
import json
import sys

json_path = sys.argv[1]
try:
    with open(json_path, "r", encoding="utf-8") as f:
        builds = json.load(f)
except Exception as e:
    print(f"ERROR: {e}")
    sys.exit(2)

if not builds:
    print("ERROR: no builds found")
    sys.exit(3)

stable = [b for b in builds if b.get("channel") == "STABLE"]
pool = stable if stable else builds
build = max(pool, key=lambda b: int(b.get("id", 0)))
download = ((build.get("downloads") or {}).get("server:default") or {}).get("url")

if not download:
    print("ERROR: no server:default download URL")
    sys.exit(4)

print(f"{build['id']}|{download}")
PY
)"
PY_STATUS=$?
set -e

if [ $PY_STATUS -ne 0 ]; then
  echo "Failed to resolve Paper build for version $MC_VERSION"
  echo "${LATEST_BUILD:-Unknown error}"
  echo "Tip: try another version, for example:"
  echo "  MC_VERSION=1.21.10 ./update-paper.sh"
  exit 1
fi

if [[ "$BUILD_INFO" == ERROR:* ]]; then
  echo "Failed to resolve Paper build for version $MC_VERSION"
  echo "$BUILD_INFO"
  echo "Tip: try another version, for example:"
  echo "  MC_VERSION=1.21.10 ./update-paper.sh"
  exit 1
fi

LATEST_BUILD="${BUILD_INFO%%|*}"
DOWNLOAD_URL="${BUILD_INFO#*|}"
JAR_NAME="paper-${MC_VERSION}.jar"

echo "Downloading Paper ${MC_VERSION} build ${LATEST_BUILD}..."
curl -fL -H "User-Agent: ${USER_AGENT}" "$DOWNLOAD_URL" -o "$JAR_NAME"
rm -f "${BUILDS_JSON_FILE}"
echo "Saved: $ROOT_DIR/$JAR_NAME"

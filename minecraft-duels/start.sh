#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

JAR_NAME="${JAR_NAME:-paper-1.21.11.jar}"
MIN_RAM="${MIN_RAM:-4G}"
MAX_RAM="${MAX_RAM:-6G}"

if [ ! -f "$JAR_NAME" ]; then
  echo "Paper jar not found: $JAR_NAME"
  echo "Run ./update-paper.sh first."
  exit 1
fi

JAVA_FLAGS=(
  "-Xms${MIN_RAM}"
  "-Xmx${MAX_RAM}"
  "-XX:+UseG1GC"
  "-XX:+ParallelRefProcEnabled"
  "-XX:MaxGCPauseMillis=200"
  "-XX:+UnlockExperimentalVMOptions"
  "-XX:+DisableExplicitGC"
  "-XX:+AlwaysPreTouch"
  "-XX:G1NewSizePercent=30"
  "-XX:G1MaxNewSizePercent=40"
  "-XX:G1HeapRegionSize=8M"
  "-XX:G1ReservePercent=20"
  "-XX:G1HeapWastePercent=5"
  "-XX:G1MixedGCCountTarget=4"
  "-XX:InitiatingHeapOccupancyPercent=15"
  "-XX:G1MixedGCLiveThresholdPercent=90"
  "-XX:G1RSetUpdatingPauseTimePercent=5"
  "-XX:SurvivorRatio=32"
  "-XX:+PerfDisableSharedMem"
  "-XX:MaxTenuringThreshold=1"
  "-Dusing.aikars.flags=https://mcflags.emc.gs"
  "-Daikars.new.flags=true"
)

exec java "${JAVA_FLAGS[@]}" -jar "$JAR_NAME" nogui

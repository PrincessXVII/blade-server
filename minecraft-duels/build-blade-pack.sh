#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACK_DIR="$ROOT/resourcepack-src"
OUT_ZIP="$ROOT/resourcepack/BladePack.zip"
DONATES="${DONATES_DIR:-$ROOT/resourcepack/assets/donates}"
TITLE="${TITLE_IMAGE:-$ROOT/resourcepack/assets/blade_title.png}"

rm -rf "$PACK_DIR"
mkdir -p "$PACK_DIR/assets/blade/textures/font/ranks"
mkdir -p "$PACK_DIR/assets/blade/textures/font"
mkdir -p "$PACK_DIR/assets/minecraft/font"
mkdir -p "$(dirname "$OUT_ZIP")"

if [[ ! -d "$DONATES" ]]; then
  echo "Donate assets not found: $DONATES" >&2
  exit 1
fi
if [[ ! -f "$TITLE" ]]; then
  echo "Blade title image not found: $TITLE" >&2
  exit 1
fi

cat > "$PACK_DIR/pack.mcmeta" <<'EOF'
{
  "pack": {
    "pack_format": 75,
    "description": "Blade Server Resource Pack"
  }
}
EOF

RANKS=(
  trial booster chamber razor winner sponsor stazher helper moder stmoder glmoder
  dizainer tehadmin kurator zamestitel owner
)

CHAR_CODE=0xE000
PROVIDERS='[{"type":"reference","id":"minecraft:include/space"},{"type":"reference","id":"minecraft:include/default","filter":{"uniform":false}},{"type":"reference","id":"minecraft:include/unifont"}'

for rank in "${RANKS[@]}"; do
  src="$DONATES/${rank}.png"
  if [[ ! -f "$src" ]]; then
    echo "Missing rank image: $src" >&2
    exit 1
  fi
  cp "$src" "$PACK_DIR/assets/blade/textures/font/ranks/${rank}.png"
  char=$(printf '\\u%04X' "$CHAR_CODE")
  PROVIDERS+=',{"type":"bitmap","file":"blade:font/ranks/'"$rank"'.png","ascent":8,"height":9,"chars":["'"$char"'"]}'
  CHAR_CODE=$((CHAR_CODE + 1))
done

TITLE_HEIGHT=32
TITLE_WIDTH=128
sips -z "$TITLE_HEIGHT" "$TITLE_WIDTH" "$TITLE" --out "$PACK_DIR/assets/blade/textures/font/blade_title.png" >/dev/null
TITLE_CHAR=$(printf '\\u%04X' "$CHAR_CODE")
PROVIDERS+=',{"type":"bitmap","file":"blade:font/blade_title.png","ascent":28,"height":'"$TITLE_HEIGHT"',"chars":["'"$TITLE_CHAR"'"]}'

PROVIDERS+=']'

python3 - <<PY
import json
providers = json.loads('''$PROVIDERS''')
with open("$PACK_DIR/assets/minecraft/font/default.json", "w") as f:
    json.dump({"providers": providers}, f, indent=2)
PY

MAP_FILE="$ROOT/resourcepack/rank-chars.txt"
: > "$MAP_FILE"
CHAR_CODE=0xE000
for rank in "${RANKS[@]}"; do
  printf '%s=%s\n' "$rank" "$(printf '\u%04X' "$CHAR_CODE")" >> "$MAP_FILE"
  CHAR_CODE=$((CHAR_CODE + 1))
done
printf 'blade_title=%s\n' "$(printf '\u%04X' "$CHAR_CODE")" >> "$MAP_FILE"

rm -f "$OUT_ZIP"
(cd "$PACK_DIR" && zip -qr "$OUT_ZIP" .)

echo "Built $OUT_ZIP"

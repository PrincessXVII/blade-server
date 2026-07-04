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

# Crop padding; tab header only shows glyphs up to ~48px tall reliably.
export TITLE="$TITLE" PACK_DIR="$PACK_DIR"
python3 <<PY
from PIL import Image
import os
src = os.environ["TITLE"]
out = os.path.join(os.environ["PACK_DIR"], "assets/blade/textures/font/blade_title.png")
tab_height = int(os.environ.get("TITLE_TAB_HEIGHT", "48"))
max_width = 512
im = Image.open(src).convert("RGBA")
bbox = im.getbbox()
if not bbox:
    raise SystemExit("Title image is fully transparent")
im = im.crop(bbox)
scale = min(max_width / im.width, tab_height / im.height, 1.0)
size = (max(1, round(im.width * scale)), max(1, round(im.height * scale)))
if size != im.size:
    im = im.resize(size, Image.Resampling.LANCZOS)
im.save(out, optimize=False)
print(f"title texture: {size[0]}x{size[1]}", flush=True)
PY
TITLE_HEIGHT=$(sips -g pixelHeight "$PACK_DIR/assets/blade/textures/font/blade_title.png" | awk '/pixelHeight/ {print $2}')
# Tab logos typically use ~52/33 height/ascent ratio.
TITLE_ASCENT=$(( TITLE_HEIGHT * 33 / 52 ))
(( TITLE_ASCENT < 1 )) && TITLE_ASCENT=1
(( TITLE_ASCENT >= TITLE_HEIGHT )) && TITLE_ASCENT=$(( TITLE_HEIGHT - 1 ))
TITLE_CHAR=$(printf '\\u%04X' "$CHAR_CODE")
PROVIDERS+=',{"type":"bitmap","file":"blade:font/blade_title.png","ascent":'"$TITLE_ASCENT"',"height":'"$TITLE_HEIGHT"',"chars":["'"$TITLE_CHAR"'"]}'

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

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACK_DIR="$ROOT/resourcepack-src"
OUT_ZIP="$ROOT/resourcepack/BladePack.zip"
DONATES="${DONATES_DIR:-$ROOT/resourcepack/assets/donates}"
TITLE="${TITLE_IMAGE:-$ROOT/resourcepack/assets/blade_title.png}"
MEETUPS_TITLE="${MEETUPS_TITLE_IMAGE:-$ROOT/resourcepack/assets/meetups_title.png}"

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
if [[ ! -f "$MEETUPS_TITLE" ]]; then
  echo "Meetups title image not found: $MEETUPS_TITLE" >&2
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

# Crop padding, downscale with 2x supersampling for max sharpness at tab size.
process_title_texture() {
  local src="$1"
  local out_name="$2"
  local tab_height="${3:-48}"
  export TITLE_SRC="$src" TITLE_OUT_NAME="$out_name" PACK_DIR="$PACK_DIR" TITLE_TAB_HEIGHT="$tab_height"
  python3 <<'PY'
from PIL import Image
import os
src = os.environ["TITLE_SRC"]
out = os.path.join(os.environ["PACK_DIR"], "assets/blade/textures/font", os.environ["TITLE_OUT_NAME"])
tab_height = int(os.environ.get("TITLE_TAB_HEIGHT", "48"))
max_width = 512
im = Image.open(src).convert("RGBA")
bbox = im.getbbox()
if not bbox:
    raise SystemExit(f"Title image is fully transparent: {src}")
im = im.crop(bbox)
scale = min(max_width / im.width, tab_height / im.height, 1.0)
size = (max(1, round(im.width * scale)), max(1, round(im.height * scale)))
if size != im.size:
    w, h = size
    im = im.resize((w * 2, h * 2), Image.Resampling.LANCZOS)
    im = im.resize((w, h), Image.Resampling.LANCZOS)
im.save(out, optimize=False, compress_level=1)
print(f"{os.path.basename(out)}: {size[0]}x{size[1]} (from {bbox[2]-bbox[0]}x{bbox[3]-bbox[1]})", flush=True)
PY
}

process_title_texture "$TITLE" "blade_title.png" 48
TITLE_HEIGHT=$(sips -g pixelHeight "$PACK_DIR/assets/blade/textures/font/blade_title.png" | awk '/pixelHeight/ {print $2}')
TITLE_ASCENT=$(( TITLE_HEIGHT * 33 / 52 ))
(( TITLE_ASCENT < 1 )) && TITLE_ASCENT=1
(( TITLE_ASCENT >= TITLE_HEIGHT )) && TITLE_ASCENT=$(( TITLE_HEIGHT - 1 ))
TITLE_CHAR=$(printf '\\u%04X' "$CHAR_CODE")
PROVIDERS+=',{"type":"bitmap","file":"blade:font/blade_title.png","ascent":'"$TITLE_ASCENT"',"height":'"$TITLE_HEIGHT"',"chars":["'"$TITLE_CHAR"'"]}'
CHAR_CODE=$((CHAR_CODE + 1))

process_title_texture "$MEETUPS_TITLE" "meetups_title.png" 22
MEETUPS_HEIGHT=$(sips -g pixelHeight "$PACK_DIR/assets/blade/textures/font/meetups_title.png" | awk '/pixelHeight/ {print $2}')
MEETUPS_ASCENT=$(( MEETUPS_HEIGHT * 33 / 52 ))
(( MEETUPS_ASCENT < 1 )) && MEETUPS_ASCENT=1
(( MEETUPS_ASCENT >= MEETUPS_HEIGHT )) && MEETUPS_ASCENT=$(( MEETUPS_HEIGHT - 1 ))
MEETUPS_CHAR=$(printf '\\u%04X' "$CHAR_CODE")
PROVIDERS+=',{"type":"bitmap","file":"blade:font/meetups_title.png","ascent":'"$MEETUPS_ASCENT"',"height":'"$MEETUPS_HEIGHT"',"chars":["'"$MEETUPS_CHAR"'"]}'

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
CHAR_CODE=$((CHAR_CODE + 1))
printf 'meetups_title=%s\n' "$(printf '\u%04X' "$CHAR_CODE")" >> "$MAP_FILE"

rm -f "$OUT_ZIP"
(cd "$PACK_DIR" && zip -qr "$OUT_ZIP" .)

echo "Built $OUT_ZIP"

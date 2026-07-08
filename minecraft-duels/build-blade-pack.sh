#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACK_DIR="$ROOT/resourcepack-src"
OUT_ZIP="$ROOT/resourcepack/BladePack.zip"
DONATES="${DONATES_DIR:-$ROOT/resourcepack/assets/donates}"
TITLE="${TITLE_IMAGE:-$ROOT/resourcepack/assets/blade_title.png}"
MEETUPS_TITLE="${MEETUPS_TITLE_IMAGE:-$ROOT/resourcepack/assets/meetups_title.png}"
BATTLEROYALE_TITLE="${BATTLEROYALE_TITLE_IMAGE:-$ROOT/resourcepack/assets/battleroyale.png}"
SMP_TITLE="${SMP_TITLE_IMAGE:-$ROOT/resourcepack/assets/smp.png}"
HUB_ASSETS="${HUB_ASSETS_DIR:-$ROOT/resourcepack/assets/hub}"
DEMORA_ZIP="${DEMORA_RP_ZIP:-$ROOT/resourcepack/demora/demoraRP-6.2.zip}"
RANK_CHAR_BASE=0xE100

rm -rf "$PACK_DIR"
mkdir -p "$(dirname "$OUT_ZIP")"

if [[ ! -f "$DEMORA_ZIP" ]]; then
  echo "Demora resource pack not found: $DEMORA_ZIP" >&2
  exit 1
fi
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
if [[ ! -f "$BATTLEROYALE_TITLE" ]]; then
  echo "Battleroyale title image not found: $BATTLEROYALE_TITLE" >&2
  exit 1
fi
if [[ ! -f "$SMP_TITLE" ]]; then
  echo "SMP title image not found: $SMP_TITLE" >&2
  exit 1
fi
unzip -q -o "$DEMORA_ZIP" -d "$PACK_DIR"

export PACK_DIR="$PACK_DIR" ROOT="$ROOT" DONATES="$DONATES" TITLE="$TITLE" MEETUPS_TITLE="$MEETUPS_TITLE"
export BATTLEROYALE_TITLE="$BATTLEROYALE_TITLE" SMP_TITLE="$SMP_TITLE"
export HUB_ASSETS="$HUB_ASSETS"
export RANK_CHAR_BASE="$RANK_CHAR_BASE"
export RANKS="trial booster chamber razor winner sponsor stazher helper moder stmoder glmoder dizainer tehadmin kurator zamestitel owner"

python3 - <<'PY'
import json
import os
from pathlib import Path

from PIL import Image

pack_dir = Path(os.environ["PACK_DIR"])
root = Path(os.environ["ROOT"])
donates = Path(os.environ["DONATES"])
title_src = Path(os.environ["TITLE"])
meetups_src = Path(os.environ["MEETUPS_TITLE"])
battleroyale_src = Path(os.environ["BATTLEROYALE_TITLE"])
smp_src = Path(os.environ["SMP_TITLE"])
ranks = os.environ["RANKS"].split()
char_code = int(os.environ["RANK_CHAR_BASE"], 0)

meta_path = pack_dir / "pack.mcmeta"
meta = json.loads(meta_path.read_text())
meta.setdefault("pack", {})["description"] = "Blade Server Resource Pack"
meta_path.write_text(json.dumps(meta, indent=2) + "\n")

rank_dir = pack_dir / "assets/blade/textures/font/ranks"
font_dir = pack_dir / "assets/blade/textures/font"
rank_dir.mkdir(parents=True, exist_ok=True)
font_dir.mkdir(parents=True, exist_ok=True)


def process_title(src: Path, out_name: str, tab_height: int) -> tuple[int, int]:
    out = font_dir / out_name
    im = Image.open(src).convert("RGBA")
    bbox = im.getbbox()
    if not bbox:
        raise SystemExit(f"Title image is fully transparent: {src}")
    im = im.crop(bbox)
    scale = min(512 / im.width, tab_height / im.height, 1.0)
    size = (max(1, round(im.width * scale)), max(1, round(im.height * scale)))
    if size != im.size:
        w, h = size
        im = im.resize((w * 2, h * 2), Image.Resampling.LANCZOS)
        im = im.resize((w, h), Image.Resampling.LANCZOS)
    im.save(out, optimize=False, compress_level=1)
    ascent = max(1, min(size[1] - 1, size[1] * 33 // 52))
    print(f"{out_name}: {size[0]}x{size[1]}", flush=True)
    return size[1], ascent


providers = []
char_map = {}

for rank in ranks:
    src = donates / f"{rank}.png"
    if not src.is_file():
        raise SystemExit(f"Missing rank image: {src}")
    (rank_dir / f"{rank}.png").write_bytes(src.read_bytes())
    ch = chr(char_code)
    char_map[rank] = ch
    providers.append({
        "type": "bitmap",
        "file": f"blade:font/ranks/{rank}.png",
        "ascent": 8,
        "height": 9,
        "chars": [ch],
    })
    char_code += 1

title_height, title_ascent = process_title(title_src, "blade_title.png", 48)
ch = chr(char_code)
char_map["blade_title"] = ch
providers.append({
    "type": "bitmap",
    "file": "blade:font/blade_title.png",
    "ascent": title_ascent,
    "height": title_height,
    "chars": [ch],
})
char_code += 1

meetups_height, meetups_ascent = process_title(meetups_src, "meetups_title.png", 22)
ch = chr(char_code)
char_map["meetups_title"] = ch
providers.append({
    "type": "bitmap",
    "file": "blade:font/meetups_title.png",
    "ascent": meetups_ascent,
    "height": meetups_height,
    "chars": [ch],
})
char_code += 1

battleroyale_height, battleroyale_ascent = process_title(battleroyale_src, "battleroyale_title.png", 22)
ch = chr(char_code)
char_map["battleroyale_title"] = ch
providers.append({
    "type": "bitmap",
    "file": "blade:font/battleroyale_title.png",
    "ascent": battleroyale_ascent,
    "height": battleroyale_height,
    "chars": [ch],
})
char_code += 1

smp_height, smp_ascent = process_title(smp_src, "smp_title.png", 22)
ch = chr(char_code)
char_map["smp_title"] = ch
providers.append({
    "type": "bitmap",
    "file": "blade:font/smp_title.png",
    "ascent": smp_ascent,
    "height": smp_height,
    "chars": [ch],
})

font_path = pack_dir / "assets/minecraft/font/default.json"
demora = json.loads(font_path.read_text())
default_providers = demora.setdefault("providers", [])
default_providers.extend(providers)
font_path.write_text(json.dumps(demora, indent=4) + "\n")

map_path = root / "resourcepack/rank-chars.txt"
lines = [f"{rank}=\\u{ord(char_map[rank]):04X}" for rank in ranks]
lines.append(f"blade_title=\\u{ord(char_map['blade_title']):04X}")
lines.append(f"meetups_title=\\u{ord(char_map['meetups_title']):04X}")
lines.append(f"battleroyale_title=\\u{ord(char_map['battleroyale_title']):04X}")
lines.append(f"smp_title=\\u{ord(char_map['smp_title']):04X}")
map_path.write_text("\n".join(lines) + "\n")

hub_assets = Path(os.environ["HUB_ASSETS"])
hub_tex_dir = pack_dir / "assets/blade/textures/item/hub"
hub_model_dir = pack_dir / "assets/blade/models/item/hub"
hub_tex_dir.mkdir(parents=True, exist_ok=True)
hub_model_dir.mkdir(parents=True, exist_ok=True)

hub_cmd_base = 7000
hub_cmd = hub_cmd_base
hub_map_lines = []


def write_hub_model(key: str) -> None:
    model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"blade:item/hub/{key}"},
    }
    (hub_model_dir / f"{key}.json").write_text(json.dumps(model, indent=4) + "\n")


def add_hub_icon(filename: str, key: str) -> int:
    global hub_cmd
    src = hub_assets / filename
    if not src.is_file():
        raise SystemExit(f"Missing hub asset: {src}")
    (hub_tex_dir / f"{key}.png").write_bytes(src.read_bytes())
    write_hub_model(key)
    hub_map_lines.append(f"{key}={hub_cmd}")
    current = hub_cmd
    hub_cmd += 1
    return current


def add_hub_tiles(filename: str, prefix: str, cols: int, rows: int) -> list[int]:
    global hub_cmd
    src = hub_assets / filename
    if not src.is_file():
        raise SystemExit(f"Missing hub asset: {src}")
    image = Image.open(src).convert("RGBA")
    tile_w = image.width // cols
    tile_h = image.height // rows
    cmds = []
    for row in range(rows):
        for col in range(cols):
            tile = image.crop((col * tile_w, row * tile_h, (col + 1) * tile_w, (row + 1) * tile_h))
            key = f"{prefix}_{row}_{col}"
            tile.save(hub_tex_dir / f"{key}.png", optimize=False, compress_level=1)
            write_hub_model(key)
            hub_map_lines.append(f"{key}={hub_cmd}")
            cmds.append(hub_cmd)
            hub_cmd += 1
    return cmds


def add_hub_tiles_padded(filename: str, prefix: str, cols_in: int, cols_out: int, rows: int) -> list[int]:
    global hub_cmd
    src = hub_assets / filename
    if not src.is_file():
        raise SystemExit(f"Missing hub asset: {src}")
    image = Image.open(src).convert("RGBA")
    tile_w = image.width // cols_in
    tile_h = image.height // rows
    cmds = []
    for row in range(rows):
        for col in range(cols_out):
            if col < cols_in:
                tile = image.crop((col * tile_w, row * tile_h, (col + 1) * tile_w, (row + 1) * tile_h))
            else:
                tile = Image.new("RGBA", (tile_w, tile_h), (0, 0, 0, 0))
            key = f"{prefix}_{row}_{col}"
            tile.save(hub_tex_dir / f"{key}.png", optimize=False, compress_level=1)
            write_hub_model(key)
            hub_map_lines.append(f"{key}={hub_cmd}")
            cmds.append(hub_cmd)
            hub_cmd += 1
    return cmds

add_hub_icon("ВыбратьСервер.png", "choose_server")
meetups_cmds = add_hub_tiles("КнопкаМитапы.png", "meetups_button", 3, 3)
bkb_cmds = add_hub_tiles("КнопкаБКБ.png", "bkb_button", 4, 3)
smp_cmds = add_hub_tiles_padded("КнопкаСМП.png", "smp_button", 2, 3, 3)
add_hub_icon("ДоступнаяАрена.png", "arena_available")
add_hub_icon("НедоступнаяАрена.png", "arena_unavailable")

paper_items_path = pack_dir / "assets/minecraft/items/paper.json"
paper_entries = []
for line in hub_map_lines:
    key, cmd = line.split("=", 1)
    paper_entries.append({
        "threshold": int(cmd),
        "model": {
            "type": "model",
            "model": f"blade:item/hub/{key}",
        },
    })
paper_items = {
    "model": {
        "type": "range_dispatch",
        "property": "custom_model_data",
        "fallback": {
            "type": "model",
            "model": "minecraft:item/paper",
        },
        "entries": paper_entries,
    },
}
paper_items_path.parent.mkdir(parents=True, exist_ok=True)
paper_items_path.write_text(json.dumps(paper_items, indent=4) + "\n")

hub_map_path = root / "resourcepack/hub-items.txt"
hub_map_path.write_text("\n".join(hub_map_lines) + "\n")
print(f"hub items: {len(hub_map_lines)}", flush=True)

# Remove slot frames in container UIs (global). Minecraft 1.20+ uses GUI sprites.
gui_slot_dir = pack_dir / "assets/minecraft/textures/gui/sprites/container"
gui_slot_dir.mkdir(parents=True, exist_ok=True)
transparent_slot = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
transparent_slot.save(gui_slot_dir / "slot.png", optimize=False, compress_level=1)

# Some menus still bake slot frames into legacy container textures.
gui_container_dir = pack_dir / "assets/minecraft/textures/gui/container"
gui_container_dir.mkdir(parents=True, exist_ok=True)
transparent_container = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
transparent_container.save(gui_container_dir / "generic_54.png", optimize=False, compress_level=1)
transparent_container.save(gui_container_dir / "generic_9.png", optimize=False, compress_level=1)
PY

rm -f "$OUT_ZIP"
(cd "$PACK_DIR" && zip -qr "$OUT_ZIP" .)

echo "Built $OUT_ZIP ($(du -h "$OUT_ZIP" | awk '{print $1}'))"

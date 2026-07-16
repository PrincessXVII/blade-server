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
char_code += 1

# Hub menu custom GUI shown as inventory title glyph (DeluxeMenus menu_title).
hub_gui_src = root / "resourcepack/assets/hub/menu-templates/hub_menu_gui.png"
if not hub_gui_src.is_file():
    hub_gui_src = root / "resourcepack/assets/hub/menu-templates/generic_54_paint_base.png"
if hub_gui_src.is_file():
    hub_gui_cp = 0xE200
    hub_gui_ch = chr(hub_gui_cp)
    char_map["hub_menu_gui"] = hub_gui_ch
    (font_dir / "hub_menu_gui.png").write_bytes(hub_gui_src.read_bytes())
    providers.append({
        "type": "bitmap",
        "file": "blade:font/hub_menu_gui.png",
        "ascent": 13,
        "height": 256,
        "chars": [hub_gui_ch],
    })
    # ResourcePackSpaces: U+F807 = -7px
    hub_shift = "\uF807"
    menu_title = f"&f{hub_shift}{hub_gui_ch}"
    (root / "resourcepack/hub-menu-title.txt").write_text(
        f"char=\\u{hub_gui_cp:04X}\n"
        f"shift=\\uF807 (-7)\n"
        f"literal={hub_gui_ch}\n"
        f"menu_title={menu_title}\n"
    )
    dm_menu = root / "plugins/DeluxeMenus/gui_menus/blade_hub.yml"
    if dm_menu.is_file():
        text = dm_menu.read_text()
        import re
        text = re.sub(r"^menu_title:.*$", f"menu_title: '{menu_title}'", text, count=1, flags=re.M)
        dm_menu.write_text(text)
    print(f"hub_menu_gui glyph: U+{hub_gui_cp:04X} shift=-7", flush=True)

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
if "hub_menu_gui" in char_map:
    lines.append(f"hub_menu_gui=\\u{ord(char_map['hub_menu_gui']):04X}")
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

# Keep only the choose-server hand item for now.
# Main menu GUI will be drawn as a custom generic_54 background (Oraxen + DeluxeMenus),
# so old tiled transparent buttons are no longer packed into the resource pack.
add_hub_icon("ВыбратьСервер.png", "choose_server")
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

# Do NOT override minecraft:textures/gui/container/generic_54.png here.
# Paint on resourcepack/assets/hub/menu-templates/generic_54_paint_base.png and
# then provide the finished texture for Oraxen/DeluxeMenus integration.
PY

MEETUPS_SOUNDS="${MEETUPS_SOUNDS_DIR:-$ROOT/resourcepack/assets/meetups-sounds}"
if [[ -d "$MEETUPS_SOUNDS" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/sounds/custom/meetups"
  cp -f "$MEETUPS_SOUNDS"/*.ogg "$PACK_DIR/assets/minecraft/sounds/custom/meetups/" 2>/dev/null || true
  export PACK_DIR
  python3 - <<'PY'
import json
import os
from pathlib import Path
pack_dir = Path(os.environ["PACK_DIR"])
sounds_path = pack_dir / "assets/minecraft/sounds.json"
data = json.loads(sounds_path.read_text()) if sounds_path.exists() else {}
data["custom.meetups.countdown"] = {"sounds": ["custom/meetups/countdown"]}
data["custom.meetups.go"] = {"sounds": ["custom/meetups/go"]}
data["custom.meetups.victory"] = {"sounds": ["custom/meetups/victory"]}
sounds_path.parent.mkdir(parents=True, exist_ok=True)
sounds_path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print("meetups sounds: countdown/go/victory", flush=True)
PY
fi

# Villager staff explosion sound (guardian hit)
WEAPONS_SOUNDS="${WEAPONS_SOUNDS_DIR:-$ROOT/resourcepack/assets/weapons-sounds}"
if [[ -d "$WEAPONS_SOUNDS" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/sounds/custom/weapons"
  cp -f "$WEAPONS_SOUNDS"/*.ogg "$PACK_DIR/assets/minecraft/sounds/custom/weapons/" 2>/dev/null || true
  export PACK_DIR
  python3 - <<'PY'
import json
import os
from pathlib import Path
pack_dir = Path(os.environ["PACK_DIR"])
sounds_path = pack_dir / "assets/minecraft/sounds.json"
data = json.loads(sounds_path.read_text()) if sounds_path.exists() else {}
# Remap vanilla guardian hurt so staff explosion (ENTITY_GUARDIAN_HURT) uses hit4 and plays in sync.
data["entity.guardian.hurt"] = {
    "replace": True,
    "sounds": ["custom/weapons/guardian_hit4"],
}
data["custom.weapons.villager_staff_explode"] = {"sounds": ["custom/weapons/guardian_hit4"]}
sounds_path.parent.mkdir(parents=True, exist_ok=True)
sounds_path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print("weapons sounds: guardian hurt -> hit4", flush=True)
PY
fi

# Blood Mace legendary texture (CMD 1 on mace)
BLOOD_MACE_TEX="${BLOOD_MACE_TEXTURE:-$ROOT/resourcepack/assets/blood-mace/blood_mace.png}"
if [[ -f "$BLOOD_MACE_TEX" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/textures/item" \
           "$PACK_DIR/assets/minecraft/models/item" \
           "$PACK_DIR/assets/minecraft/items"
  cp -f "$BLOOD_MACE_TEX" "$PACK_DIR/assets/minecraft/textures/item/blood_mace.png"
  cat > "$PACK_DIR/assets/minecraft/models/item/blood_mace.json" <<'EOF'
{
  "parent": "minecraft:item/handheld_mace",
  "textures": {
    "layer0": "minecraft:item/blood_mace"
  }
}
EOF
  cat > "$PACK_DIR/assets/minecraft/items/mace.json" <<'EOF'
{
  "model": {
    "type": "range_dispatch",
    "property": "custom_model_data",
    "fallback": {
      "type": "model",
      "model": "minecraft:item/mace"
    },
    "entries": [
      {
        "threshold": 1,
        "model": {
          "type": "model",
          "model": "minecraft:item/blood_mace"
        }
      }
    ]
  }
}
EOF
  echo "blood mace: texture + CMD 1"
fi

# Meetups custom totems (CMD 1/2/3 on totem_of_undying)
TOTEM_DIR="${TOTEM_TEXTURE_DIR:-$ROOT/resourcepack/assets/meetups-totems}"
if [[ -f "$TOTEM_DIR/totem_agility.png" && -f "$TOTEM_DIR/totem_fortitude.png" && -f "$TOTEM_DIR/totem_tyrant.png" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/textures/item" \
           "$PACK_DIR/assets/minecraft/models/item" \
           "$PACK_DIR/assets/minecraft/items"
  cp -f "$TOTEM_DIR/totem_agility.png" "$PACK_DIR/assets/minecraft/textures/item/totem_agility.png"
  cp -f "$TOTEM_DIR/totem_fortitude.png" "$PACK_DIR/assets/minecraft/textures/item/totem_fortitude.png"
  cp -f "$TOTEM_DIR/totem_tyrant.png" "$PACK_DIR/assets/minecraft/textures/item/totem_tyrant.png"
  for id in totem_agility totem_fortitude totem_tyrant; do
    cat > "$PACK_DIR/assets/minecraft/models/item/${id}.json" <<EOF
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/${id}"
  }
}
EOF
  done
  cat > "$PACK_DIR/assets/minecraft/items/totem_of_undying.json" <<'EOF'
{
  "model": {
    "type": "range_dispatch",
    "property": "custom_model_data",
    "fallback": {
      "type": "model",
      "model": "minecraft:item/totem_of_undying"
    },
    "entries": [
      {
        "threshold": 1,
        "model": {
          "type": "model",
          "model": "minecraft:item/totem_agility"
        }
      },
      {
        "threshold": 2,
        "model": {
          "type": "model",
          "model": "minecraft:item/totem_fortitude"
        }
      },
      {
        "threshold": 3,
        "model": {
          "type": "model",
          "model": "minecraft:item/totem_tyrant"
        }
      }
    ]
  }
}
EOF
  echo "meetups totems: agility/fortitude/tyrant CMD 1-3"
fi

# Meetups leave-queue item icon (CMD 9101 on paper)
LEAVE_GAME_TEX="${LEAVE_GAME_TEXTURE:-$ROOT/resourcepack/assets/meetups-items/leave_game.png}"
if [[ -f "$LEAVE_GAME_TEX" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/textures/item" \
           "$PACK_DIR/assets/minecraft/models/item" \
           "$PACK_DIR/assets/minecraft/items"
  cp -f "$LEAVE_GAME_TEX" "$PACK_DIR/assets/minecraft/textures/item/leave_game.png"
  cat > "$PACK_DIR/assets/minecraft/models/item/leave_game.json" <<'EOF'
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/leave_game"
  }
}
EOF
  export PACK_DIR
  python3 - <<'PY'
import json
from pathlib import Path

pack_dir = Path(__import__("os").environ["PACK_DIR"])
paper_path = pack_dir / "assets/minecraft/items/paper.json"
if not paper_path.is_file():
    raise SystemExit(f"Missing paper item model: {paper_path}")

data = json.loads(paper_path.read_text())
entries = data.setdefault("model", {}).setdefault("entries", [])
entries = [entry for entry in entries if entry.get("threshold") != 9101]
entries.append({
    "threshold": 9101,
    "model": {
        "type": "model",
        "model": "minecraft:item/leave_game",
    },
})
entries.sort(key=lambda entry: entry["threshold"])
data["model"]["entries"] = entries
paper_path.write_text(json.dumps(data, indent=4) + "\n")
PY
  echo "meetups leave item: paper CMD 9101"
fi

rm -f "$OUT_ZIP"
(cd "$PACK_DIR" && zip -qr "$OUT_ZIP" .)

echo "Built $OUT_ZIP ($(du -h "$OUT_ZIP" | awk '{print $1}'))"

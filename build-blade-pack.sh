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
FFA_TITLE="${FFA_TITLE_IMAGE:-$ROOT/resourcepack/assets/ffa_title.png}"
EVENTS_TITLE="${EVENTS_TITLE_IMAGE:-$ROOT/resourcepack/assets/events_title.png}"
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
if [[ ! -f "$FFA_TITLE" ]]; then
  echo "FFA title image not found: $FFA_TITLE" >&2
  exit 1
fi
if [[ ! -f "$EVENTS_TITLE" ]]; then
  echo "Events title image not found: $EVENTS_TITLE" >&2
  exit 1
fi
unzip -q -o "$DEMORA_ZIP" -d "$PACK_DIR"

# Blade pack icon (MOTD logo). Strip ICC/Display P3 — Minecraft can hang on exotic PNG profiles.
PACK_ICON="${PACK_ICON:-$ROOT/plugins/BetterMOTD/icons/logoblademinecarft.png}"
if [[ -f "$PACK_ICON" ]]; then
  PACK_ICON="$PACK_ICON" PACK_DIR="$PACK_DIR" python3 - <<'PY'
import os
from pathlib import Path
from PIL import Image

src = Path(os.environ["PACK_ICON"])
out = Path(os.environ["PACK_DIR"]) / "pack.png"
im = Image.open(src).convert("RGBA")
# Minecraft pack icons are typically 64x64 or 128x128.
if im.size != (64, 64) and im.size != (128, 128):
    im = im.resize((64, 64), Image.Resampling.LANCZOS)
# Save without ICC profile / exotic metadata.
im.save(out, format="PNG", optimize=True, icc_profile=None)
print(f"pack.png: {im.size[0]}x{im.size[1]} sRGB (no ICC)", flush=True)
PY
else
  echo "Warning: pack icon not found: $PACK_ICON" >&2
fi

export PACK_DIR="$PACK_DIR" ROOT="$ROOT" DONATES="$DONATES" TITLE="$TITLE" MEETUPS_TITLE="$MEETUPS_TITLE"
export BATTLEROYALE_TITLE="$BATTLEROYALE_TITLE" SMP_TITLE="$SMP_TITLE"
export FFA_TITLE="$FFA_TITLE" EVENTS_TITLE="$EVENTS_TITLE"
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
ffa_src = Path(os.environ["FFA_TITLE"])
events_src = Path(os.environ["EVENTS_TITLE"])
ranks = os.environ["RANKS"].split()
char_code = int(os.environ["RANK_CHAR_BASE"], 0)

meta_path = pack_dir / "pack.mcmeta"
meta = json.loads(meta_path.read_text())
pack = meta.setdefault("pack", {})
pack["description"] = "Blade Server Resource Pack"
# Paper/client 1.21.11 requires resource pack format 75.
pack["pack_format"] = 75
pack["min_format"] = 34
pack["max_format"] = 99
pack.pop("supported_formats", None)
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

# Media rank icon after titles so existing rank/title codepoints stay stable.
media_src = donates / "media.png"
if media_src.is_file():
    (rank_dir / "media.png").write_bytes(media_src.read_bytes())
    ch = chr(char_code)
    char_map["media"] = ch
    providers.append({
        "type": "bitmap",
        "file": "blade:font/ranks/media.png",
        "ascent": 8,
        "height": 9,
        "chars": [ch],
    })
    print(f"media rank glyph: U+{ord(ch):04X}", flush=True)
    char_code += 1

# NPC hologram titles (same display height 22 as meetups/BR/SMP).
ffa_height, ffa_ascent = process_title(ffa_src, "ffa_title.png", 22)
ch = chr(char_code)
char_map["ffa_title"] = ch
providers.append({
    "type": "bitmap",
    "file": "blade:font/ffa_title.png",
    "ascent": ffa_ascent,
    "height": ffa_height,
    "chars": [ch],
})
print(f"ffa title glyph: U+{ord(ch):04X}", flush=True)
char_code += 1

events_height, events_ascent = process_title(events_src, "events_title.png", 22)
ch = chr(char_code)
char_map["events_title"] = ch
providers.append({
    "type": "bitmap",
    "file": "blade:font/events_title.png",
    "ascent": events_ascent,
    "height": events_height,
    "chars": [ch],
})
print(f"events title glyph: U+{ord(ch):04X}", flush=True)
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
        # Matches ItemsAdder menu_minigames.png alignment (height 256 / ascent 34).
        "ascent": 34,
        "height": 256,
        "chars": [hub_gui_ch],
    })
    # ResourcePackSpaces: U+F808 (-8) + U+F806 (-6) = -14px
    hub_shift = "\uF808\uF806"
    menu_title = f"&f{hub_shift}{hub_gui_ch}"
    (root / "resourcepack/hub-menu-title.txt").write_text(
        f"char=\\u{hub_gui_cp:04X}\n"
        f"shift=\\uF808\\uF806 (-14)\n"
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
lines = []
for rank in ranks:
    lines.append(f"{rank}=\\u{ord(char_map[rank]):04X}")
    if rank == "sponsor" and "media" in char_map:
        lines.append(f"media=\\u{ord(char_map['media']):04X}")
lines.append(f"blade_title=\\u{ord(char_map['blade_title']):04X}")
lines.append(f"meetups_title=\\u{ord(char_map['meetups_title']):04X}")
lines.append(f"battleroyale_title=\\u{ord(char_map['battleroyale_title']):04X}")
lines.append(f"smp_title=\\u{ord(char_map['smp_title']):04X}")
if "ffa_title" in char_map:
    lines.append(f"ffa_title=\\u{ord(char_map['ffa_title']):04X}")
if "events_title" in char_map:
    lines.append(f"events_title=\\u{ord(char_map['events_title']):04X}")
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

BR_SOUNDS="${BR_SOUNDS_DIR:-$ROOT/resourcepack/assets/br-sounds}"
if [[ -d "$BR_SOUNDS" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/sounds/custom/br"
  cp -f "$BR_SOUNDS"/*.ogg "$PACK_DIR/assets/minecraft/sounds/custom/br/" 2>/dev/null || true
  export PACK_DIR
  python3 - <<'PY'
import json
import os
from pathlib import Path
pack_dir = Path(os.environ["PACK_DIR"])
sounds_path = pack_dir / "assets/minecraft/sounds.json"
data = json.loads(sounds_path.read_text()) if sounds_path.exists() else {}
data["custom.br.countdown"] = {"sounds": ["custom/br/countdown"]}
data["custom.br.go"] = {"sounds": ["custom/br/go"]}
data["custom.br.phase"] = {"sounds": ["custom/br/phase"]}
data["custom.br.craft"] = {"sounds": ["custom/br/craft"]}
data["custom.br.craft_available"] = {"sounds": ["custom/br/craft_available"]}
sounds_path.parent.mkdir(parents=True, exist_ok=True)
sounds_path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print("br sounds: countdown/go/phase/craft/craft_available", flush=True)
PY
fi

# Remove guardian_hit4 completely. Silence ONLY entity.guardian.hurt so old
# client remaps / deferred packets cannot play that sample. Do not touch other sounds.
export PACK_DIR
python3 - <<'PY'
import json
import os
from pathlib import Path
pack_dir = Path(os.environ["PACK_DIR"])
sounds_path = pack_dir / "assets/minecraft/sounds.json"
data = json.loads(sounds_path.read_text()) if sounds_path.exists() else {}
data.pop("custom.weapons.villager_staff_explode", None)
# Empty replace = inaudible. Leaves every other legendary/custom sound intact.
data["entity.guardian.hurt"] = {"replace": True, "sounds": []}
sounds_path.parent.mkdir(parents=True, exist_ok=True)
sounds_path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print("weapons sounds: guardian_hit4 gone; entity.guardian.hurt silenced only", flush=True)
PY
rm -rf "$PACK_DIR/assets/minecraft/sounds/custom/weapons"
rm -f "$ROOT/resourcepack/assets/weapons-sounds/guardian_hit4.ogg" 2>/dev/null || true
rmdir "$ROOT/resourcepack/assets/weapons-sounds" 2>/dev/null || true

# Force new pack hash so clients must re-download (bust stale guardian_hit4 cache).
python3 - <<'PY'
import json, os, time
from pathlib import Path
meta = Path(os.environ["PACK_DIR"]) / "pack.mcmeta"
data = json.loads(meta.read_text())
pack = data.setdefault("pack", {})
pack["description"] = f"Blade Server Resource Pack (no-guardian-hit4-{int(time.time())})"
pack["pack_format"] = 75
pack["min_format"] = 34
pack["max_format"] = 99
pack.pop("supported_formats", None)
meta.write_text(json.dumps(data, indent=2) + "\n")
print("pack.mcmeta bust:", pack["description"], "format=", pack["pack_format"], flush=True)
PY

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

# BR class-select item (CMD 9201 on paper) — must be AFTER leave (9101) so range_dispatch picks correctly
BR_CLASS_TEX="${BR_CLASS_TEXTURE:-$ROOT/resourcepack/assets/br-items/choose_class.png}"
if [[ -f "$BR_CLASS_TEX" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/textures/item" \
           "$PACK_DIR/assets/minecraft/models/item" \
           "$PACK_DIR/assets/minecraft/items"
  cp -f "$BR_CLASS_TEX" "$PACK_DIR/assets/minecraft/textures/item/br_choose_class.png"
  cat > "$PACK_DIR/assets/minecraft/models/item/br_choose_class.json" <<'EOF'
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/br_choose_class"
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
entries = [entry for entry in entries if entry.get("threshold") not in (9101, 9201)]
entries.append({
    "threshold": 9101,
    "model": {
        "type": "model",
        "model": "minecraft:item/leave_game",
    },
})
entries.append({
    "threshold": 9201,
    "model": {
        "type": "model",
        "model": "minecraft:item/br_choose_class",
    },
})
entries.sort(key=lambda entry: entry["threshold"])
data["model"]["entries"] = entries
paper_path.write_text(json.dumps(data, indent=4) + "\n")
PY
  echo "BR class select: paper CMD 9201"
fi

# --- Atlantis cosmetics (hats) + GUI glyphs / icons ---
COSMETICS_SRC="${COSMETICS_SRC:-/Users/boris/Downloads/372428ec865d2f8d6f5fce662fbd1ec3035b2ced.zip_Decompiler.com}"
COSMETICS_GUI_MAIN="${COSMETICS_GUI_MAIN:-/Users/boris/Downloads/cosmetics2 2.png}"
COSMETICS_GUI_HATS="${COSMETICS_GUI_HATS:-/Users/boris/Downloads/skins.png}"
export PACK_DIR COSMETICS_SRC COSMETICS_GUI_MAIN COSMETICS_GUI_HATS ROOT

# Keep vanilla Minecraft GUI textures for standard arena menus.
# Custom glyph menus are tinted correctly by white titles now.

python3 - <<'PY'
import json, os, shutil
from pathlib import Path
from PIL import Image

pack = Path(os.environ["PACK_DIR"])
src = Path(os.environ["COSMETICS_SRC"])
root = Path(os.environ["ROOT"])

if not src.is_dir():
    print("cosmetics src missing, skip", src, flush=True)
    raise SystemExit(0)

# Copy namespaces needed for hats
for ns in ("atlantis_cosmetics", "atlantis_ui"):
    s = src / "assets" / ns
    d = pack / "assets" / ns
    if s.is_dir():
        if d.exists():
            shutil.rmtree(d)
        shutil.copytree(s, d, ignore=shutil.ignore_patterns(".DS_Store"))
        print(f"copied assets/{ns}", flush=True)

# Transparent pumpkin blur (avoid overlay when wearing hats)
blur_src = src / "assets/minecraft/textures/misc/pumpkinblur.png"
blur_dst = pack / "assets/minecraft/textures/misc/pumpkinblur.png"
blur_dst.parent.mkdir(parents=True, exist_ok=True)
if blur_src.is_file():
    shutil.copy2(blur_src, blur_dst)

# Merge IA atlas sprite map so ia:N textures resolve
atlas_src = src / "ia_overlay_modern_atlas/assets/minecraft/atlases/items.json"
atlas_dst = pack / "assets/minecraft/atlases/items.json"
atlas_dst.parent.mkdir(parents=True, exist_ok=True)
if atlas_src.is_file():
    incoming = json.loads(atlas_src.read_text())
    if atlas_dst.is_file():
        existing = json.loads(atlas_dst.read_text())
        sources = existing.setdefault("sources", [])
        sources.extend(incoming.get("sources", []))
        atlas_dst.write_text(json.dumps(existing, indent=2) + "\n")
    else:
        shutil.copy2(atlas_src, atlas_dst)
    print("merged ia items atlas", flush=True)

# carved_pumpkin item model (1.21.4+ format preferred)
for rel in (
    "ia_overlay_1_21_6_plus/assets/minecraft/items/carved_pumpkin.json",
    "ia_overlay_1_21_4_to_5/assets/minecraft/items/carved_pumpkin.json",
):
    p = src / rel
    if p.is_file():
        dst = pack / "assets/minecraft/items/carved_pumpkin.json"
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, dst)
        print("carved_pumpkin items model", rel, flush=True)
        break
# legacy overrides fallback
legacy = src / "assets/minecraft/models/item/carved_pumpkin.json"
if legacy.is_file():
    dst = pack / "assets/minecraft/models/item/carved_pumpkin.json"
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(legacy, dst)

# GUI font glyphs
font_dir = pack / "assets/blade/textures/font"
font_dir.mkdir(parents=True, exist_ok=True)
font_path = pack / "assets/minecraft/font/default.json"
font = json.loads(font_path.read_text()) if font_path.is_file() else {"providers": []}
providers = font.setdefault("providers", [])

def add_gui_glyph(path: Path, name: str, codepoint: int, height: int = 256, ascent: int = 34):
    if not path.is_file():
        print("missing gui", path, flush=True)
        return
    # normalize to RGBA without exotic profiles
    im = Image.open(path).convert("RGBA")
    out = font_dir / f"{name}.png"
    im.save(out, format="PNG", optimize=True, icc_profile=None)
    ch = chr(codepoint)
    providers.append({
        "type": "bitmap",
        "file": f"blade:font/{name}.png",
        "ascent": ascent,
        "height": height,
        "chars": [ch],
    })
    print(f"{name} glyph U+{codepoint:04X}", flush=True)

add_gui_glyph(Path(os.environ["COSMETICS_GUI_MAIN"]), "cosmetics_menu_gui", 0xE201, height=256, ascent=25)
add_gui_glyph(Path(os.environ["COSMETICS_GUI_HATS"]), "cosmetics_hats_gui", 0xE202, height=256, ascent=16)
add_gui_glyph(Path(os.environ["COSMETICS_GUI_HATS"]), "cosmetics_swords_gui", 0xE203, height=256, ascent=16)
add_gui_glyph(Path(os.environ["COSMETICS_GUI_HATS"]), "cosmetics_titles_gui", 0xE204, height=256, ascent=16)
add_gui_glyph(Path(os.environ["COSMETICS_GUI_HATS"]), "cosmetics_title_colors_gui", 0xE205, height=256, ascent=16)
add_gui_glyph(Path(os.environ["COSMETICS_GUI_HATS"]), "cosmetics_kill_effects_gui", 0xE206, height=256, ascent=16)
font_path.write_text(json.dumps(font, indent=4) + "\n")

# Opt-in TTF fonts (NOT merged into default.json — use <font:mine|ten|miniten> explicitly)
ttf_src = root / "resourcepack/assets/fonts"
mc_font = pack / "assets/minecraft/font"
mc_font.mkdir(parents=True, exist_ok=True)
for ttf_name, size in (("mine", 10.0), ("ten", 11.5), ("miniten", 8.2)):
    src_ttf = ttf_src / f"{ttf_name}.ttf"
    if not src_ttf.is_file():
        print(f"missing ttf font: {src_ttf}", flush=True)
        continue
    shutil.copy2(src_ttf, mc_font / f"{ttf_name}.ttf")
    (mc_font / f"{ttf_name}.json").write_text(json.dumps({
        "providers": [{
            "type": "ttf",
            "file": f"minecraft:{ttf_name}",
            "shift": [0, 0],
            "size": size,
            "oversample": 4.0,
        }]
    }, indent=2) + "\n")
    print(f"opt-in font minecraft:{ttf_name} (size={size})", flush=True)

# Paper icons: hats hub (7003), prev(7004), next(7005), blank(7006), swords (7007), kill effects (7008)
hub_tex = pack / "assets/blade/textures/item/hub"
hub_model = pack / "assets/blade/models/item/hub"
hub_tex.mkdir(parents=True, exist_ok=True)
hub_model.mkdir(parents=True, exist_ok=True)

def write_icon(key: str, img: Image.Image, cmd: int, paper_entries: list):
    img.save(hub_tex / f"{key}.png", format="PNG", optimize=True, icc_profile=None)
    (hub_model / f"{key}.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"blade:item/hub/{key}"},
    }, indent=4) + "\n")
    paper_entries.append({
        "threshold": cmd,
        "model": {"type": "model", "model": f"blade:item/hub/{key}"},
    })

paper_path = pack / "assets/minecraft/items/paper.json"
paper = json.loads(paper_path.read_text())
entries = paper["model"].get("entries", [])

hats_icon = src / "assets/atlantis_ui/textures/items/hats.png"
prev_icon = src / "assets/atlantis_ui/textures/buttons/select_rounds/prev-page.png"
next_icon = src / "assets/atlantis_ui/textures/buttons/select_rounds/next-page.png"
prev_meta = src / "assets/atlantis_ui/textures/buttons/select_rounds/prev-page.png.mcmeta"
next_meta = src / "assets/atlantis_ui/textures/buttons/select_rounds/next-page.png.mcmeta"
swords_icon = Path(os.environ.get(
    "COSMETICS_SWORDS_ICON",
    "/Users/boris/Downloads/weapons_icons/sword_r06_c03.png",
))
kill_effects_icon = Path(os.environ.get(
    "COSMETICS_KILL_EFFECTS_ICON",
    "/Users/boris/Downloads/Для пака/Донат.png",
))

new_entries = []
if hats_icon.is_file():
    write_icon("cosmetics_hats", Image.open(hats_icon).convert("RGBA"), 7003, new_entries)
if prev_icon.is_file():
    write_icon("cosmetics_prev", Image.open(prev_icon).convert("RGBA"), 7004, new_entries)
    if prev_meta.is_file():
        shutil.copy2(prev_meta, hub_tex / "cosmetics_prev.png.mcmeta")
if next_icon.is_file():
    write_icon("cosmetics_next", Image.open(next_icon).convert("RGBA"), 7005, new_entries)
    if next_meta.is_file():
        shutil.copy2(next_meta, hub_tex / "cosmetics_next.png.mcmeta")
# transparent 16x16 blank
blank = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
write_icon("cosmetics_blank", blank, 7006, new_entries)
if swords_icon.is_file():
    write_icon("cosmetics_swords", Image.open(swords_icon).convert("RGBA"), 7007, new_entries)
if kill_effects_icon.is_file():
    write_icon("cosmetics_kill_effects", Image.open(kill_effects_icon).convert("RGBA"), 7008, new_entries)

# replace/add thresholds
by_thr = {e["threshold"]: e for e in entries}
for e in new_entries:
    by_thr[e["threshold"]] = e
paper["model"]["entries"] = sorted(by_thr.values(), key=lambda e: e["threshold"])
paper_path.write_text(json.dumps(paper, indent=4) + "\n")
print("cosmetics paper CMDs 7003-7008", flush=True)

# --- Villager staff models from legendary tools pack ---
IA_WAND = {
    "69": "civilization:item/tools/villager_wand/normal/default",
    "68": "civilization:item/tools/villager_wand/bifrost/default",
    "67": "civilization:item/tools/villager_wand/bifrost/anim_0",
    "66": "civilization:item/tools/villager_wand/bifrost/anim_1",
}
wand_tex_src = src / "assets/legendary/textures/item/tools/villager_wand"
wand_tex_dst = pack / "assets/civilization/textures/item/tools/villager_wand"
wand_model_src = src / "assets/legendary/models/item/tools/villager_wand"
wand_model_dst = pack / "assets/civilization/models/item/tools/villager_wand"
if wand_tex_src.is_dir() and wand_model_src.is_dir():
    if wand_tex_dst.exists():
        shutil.rmtree(wand_tex_dst)
    shutil.copytree(wand_tex_src, wand_tex_dst, ignore=shutil.ignore_patterns(".DS_Store"))
    if wand_model_dst.exists():
        shutil.rmtree(wand_model_dst)
    wand_model_dst.mkdir(parents=True, exist_ok=True)
    for path in wand_model_src.glob("*.json"):
        data = json.loads(path.read_text())
        textures = data.get("textures")
        if isinstance(textures, dict):
            for k, v in list(textures.items()):
                if isinstance(v, str) and v.startswith("ia:"):
                    num = v.split(":", 1)[1]
                    if num in IA_WAND:
                        textures[k] = IA_WAND[num]
        (wand_model_dst / path.name).write_text(json.dumps(data, indent=2) + "\n")
    # Main iron_axe CMD 3 model points here:
    flat = pack / "assets/civilization/models/item/tools/villager_wand.json"
    normal = wand_model_dst / "normal.json"
    if normal.is_file():
        flat.write_text(normal.read_text())
    print("updated villager staff models from legendary pack", flush=True)

# --- Kill effects (owlsstudio models + sounds on leather_horse_armor) ---
IA_TO_OWL = {
    "482": "owlsstudio:entity/eaten_by_pac_man",
    "483": "owlsstudio:entity/body_grey",
    "484": "owlsstudio:entity/sand_suck",
    "485": "owlsstudio:entity/tentacle_grasp",
    "486": "owlsstudio:entity/tertis_smash",
    "487": "owlsstudio:entity/hellfire_burn",
    "488": "owlsstudio:entity/among_us_stab",
    "489": "owlsstudio:entity/knockout_k_o",
    "490": "owlsstudio:entity/eaten_by_carnivore_plant",
    "491": "owlsstudio:entity/eaten_by_shark",
    "492": "owlsstudio:entity/angel_wings_to_heaven",
    "493": "owlsstudio:entity/angelic_yellowaura",
    "494": "owlsstudio:entity/archangel_kfx",
}
KILL_EFFECTS = {
    "angelic_bless", "arcade_gameover", "hellfire_burn", "imposter_instinct",
    "kfx_divine_execution", "knockout_ko", "plantfood_feasting", "quicksand",
    "shark_attack", "tentacle_grasp", "tertis_smash",
}

owl_src = src / "assets/owlsstudio"
owl_dst = pack / "assets/owlsstudio"
if owl_src.is_dir():
    # textures
    tex_src = owl_src / "textures"
    tex_dst = owl_dst / "textures"
    if tex_src.is_dir():
        if tex_dst.exists():
            shutil.rmtree(tex_dst)
        shutil.copytree(tex_src, tex_dst, ignore=shutil.ignore_patterns(".DS_Store"))
    # models with ia:N remapped
    models_src = owl_src / "models"
    models_dst = owl_dst / "models"
    if models_src.is_dir():
        if models_dst.exists():
            shutil.rmtree(models_dst)
        for path in models_src.rglob("*.json"):
            rel = path.relative_to(models_src)
            out = models_dst / rel
            out.parent.mkdir(parents=True, exist_ok=True)
            data = json.loads(path.read_text())
            textures = data.get("textures")
            if isinstance(textures, dict):
                for k, v in list(textures.items()):
                    if isinstance(v, str) and v.startswith("ia:"):
                        num = v.split(":", 1)[1]
                        if num in IA_TO_OWL:
                            textures[k] = IA_TO_OWL[num]
            out.write_text(json.dumps(data, indent=2) + "\n")
    print("copied owlsstudio kill-effect assets", flush=True)

# Merge kill-effect entries into leather_horse_armor items model
armor_src = src / "ia_overlay_1_21_6_plus/assets/minecraft/items/leather_horse_armor.json"
armor_dst = pack / "assets/minecraft/items/leather_horse_armor.json"
if armor_src.is_file():
    incoming = json.loads(armor_src.read_text())

    def collect_kill_entries(obj, acc):
        if isinstance(obj, dict):
            if "threshold" in obj and "model" in obj:
                model = obj["model"]
                mid = model.get("model") if isinstance(model, dict) else model
                if isinstance(mid, str) and mid.startswith("owlsstudio:"):
                    effect = mid.split(":", 1)[1].split("/", 1)[0]
                    if effect in KILL_EFFECTS:
                        acc.append(obj)
            for v in obj.values():
                collect_kill_entries(v, acc)
        elif isinstance(obj, list):
            for v in obj:
                collect_kill_entries(v, acc)

    kill_entries = []
    collect_kill_entries(incoming, kill_entries)
    if armor_dst.is_file():
        current = json.loads(armor_dst.read_text())
    else:
        current = {
            "model": {
                "type": "range_dispatch",
                "property": "custom_model_data",
                "entries": [],
                "fallback": {"type": "model", "model": "minecraft:item/leather_horse_armor"},
            },
            "oversized_in_gui": True,
        }
    cur_model = current.setdefault("model", {})
    if cur_model.get("type") != "range_dispatch":
        cur_model = {
            "type": "range_dispatch",
            "property": "custom_model_data",
            "entries": [],
            "fallback": cur_model if cur_model else {"type": "model", "model": "minecraft:item/leather_horse_armor"},
        }
        current["model"] = cur_model
    by_thr = {e["threshold"]: e for e in cur_model.get("entries", []) if "threshold" in e}
    for e in kill_entries:
        by_thr[e["threshold"]] = e
    cur_model["entries"] = sorted(by_thr.values(), key=lambda e: e["threshold"])
    current["oversized_in_gui"] = True
    armor_dst.parent.mkdir(parents=True, exist_ok=True)
    armor_dst.write_text(json.dumps(current, indent=2) + "\n")
    print(f"leather_horse_armor kill FX entries: {len(kill_entries)}", flush=True)

# Kill effect sounds
sounds_src_dir = src / "assets/minecraft/sounds/kill_fx"
sounds_dst_dir = pack / "assets/minecraft/sounds/kill_fx"
if sounds_src_dir.is_dir():
    if sounds_dst_dir.exists():
        shutil.rmtree(sounds_dst_dir)
    shutil.copytree(sounds_src_dir, sounds_dst_dir, ignore=shutil.ignore_patterns(".DS_Store"))
    sounds_json_src = src / "assets/minecraft/sounds.json"
    sounds_json_dst = pack / "assets/minecraft/sounds.json"
    incoming_sounds = {}
    if sounds_json_src.is_file():
        incoming_sounds = json.loads(sounds_json_src.read_text())
    existing_sounds = {}
    if sounds_json_dst.is_file():
        existing_sounds = json.loads(sounds_json_dst.read_text())
    for key, value in incoming_sounds.items():
        if key.startswith("kill_fx."):
            existing_sounds[key] = value
    sounds_json_dst.parent.mkdir(parents=True, exist_ok=True)
    sounds_json_dst.write_text(json.dumps(existing_sounds, indent=2) + "\n")
    print(f"merged kill_fx sounds: {sum(1 for k in existing_sounds if k.startswith('kill_fx.'))}", flush=True)

# Merge Atlantis sword skins (CMD 7001-7254) into diamond/netherite swords.
# Keep existing BladeWeapons legendary entries (low CMDs).
ia_diamond = src / "ia_overlay_1_21_6_plus/assets/minecraft/items/diamond_sword.json"
for sword_name in ("diamond_sword", "netherite_sword"):
    sword_path = pack / f"assets/minecraft/items/{sword_name}.json"
    if not sword_path.is_file() or not ia_diamond.is_file():
        continue
    current = json.loads(sword_path.read_text())
    ia = json.loads(ia_diamond.read_text())
    cur_entries = current.setdefault("model", {}).setdefault("entries", [])
    atl = [
        e for e in ia.get("model", {}).get("entries", [])
        if "atlantis_cosmetics:item/" in e.get("model", {}).get("model", "")
    ]
    by_thr = {e["threshold"]: e for e in cur_entries}
    for e in atl:
        by_thr[e["threshold"]] = e
    current["model"]["entries"] = sorted(by_thr.values(), key=lambda e: e["threshold"])
    sword_path.write_text(json.dumps(current, indent=4) + "\n")
    print(f"{sword_name}: merged {len(atl)} atlantis sword skins", flush=True)
PY

# Hide "Inventory" / "Инвентарь" above player slots in chest-style GUIs (DeluxeMenus + cosmetics).
PACK_DIR="$PACK_DIR" python3 - <<'PY'
import json
import os
from pathlib import Path

pack = Path(os.environ["PACK_DIR"])
lang_dir = pack / "assets/minecraft/lang"
lang_dir.mkdir(parents=True, exist_ok=True)

def patch_lang(path: Path, defaults: dict) -> None:
    data = {}
    if path.is_file():
        data = json.loads(path.read_text(encoding="utf-8"))
    data.update(defaults)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

hide = {"container.inventory": ""}
patch_lang(lang_dir / "en_us.json", hide)
patch_lang(lang_dir / "ru_ru.json", hide)
print("lang: container.inventory hidden (en_us + ru_ru)", flush=True)
PY

rm -f "$OUT_ZIP"
(cd "$PACK_DIR" && zip -qr "$OUT_ZIP" .)

echo "Built $OUT_ZIP ($(du -h "$OUT_ZIP" | awk '{print $1}'))"

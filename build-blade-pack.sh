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
HOPLITE_ZIP="${HOPLITE_RP_ZIP:-$ROOT/resourcepack/hoplite/hopliteRP-8.3.2.zip}"
# Legacy alias: DEMORA_RP_ZIP still accepted if HOPLITE_RP_ZIP unset and pointing at Hoplite zip.
if [[ -n "${DEMORA_RP_ZIP:-}" && ! -f "$HOPLITE_ZIP" ]]; then
  HOPLITE_ZIP="$DEMORA_RP_ZIP"
fi
RANK_CHAR_BASE=0xE100

if [[ ! -f "$HOPLITE_ZIP" ]]; then
  echo "Hoplite resource pack not found: $HOPLITE_ZIP" >&2
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

rm -rf "$PACK_DIR"
mkdir -p "$(dirname "$OUT_ZIP")"
echo "Unpacking Hoplite RP base: $HOPLITE_ZIP"
unzip -q -o "$HOPLITE_ZIP" -d "$PACK_DIR"

# Hoplite ships a fully transparent minecart.png — restore vanilla cart body.
MINECART_TEX="${MINECART_TEX:-$ROOT/resourcepack/assets/minecraft/textures/entity/minecart.png}"
if [[ -f "$MINECART_TEX" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/textures/entity"
  cp "$MINECART_TEX" "$PACK_DIR/assets/minecraft/textures/entity/minecart.png"
  echo "Restored vanilla minecart.png"
fi

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
  for sound in countdown go; do
    [[ -f "$MEETUPS_SOUNDS/$sound.ogg" ]] && cp -f "$MEETUPS_SOUNDS/$sound.ogg" "$PACK_DIR/assets/minecraft/sounds/custom/meetups/"
  done
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
sounds_path.parent.mkdir(parents=True, exist_ok=True)
sounds_path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print("meetups sounds: countdown/go", flush=True)
PY
fi

FFA_SOUNDS="${FFA_SOUNDS_DIR:-$ROOT/resourcepack/assets/ffa-sounds}"
if [[ -d "$FFA_SOUNDS" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/sounds/custom/ffa"
  cp -f "$FFA_SOUNDS"/*.ogg "$PACK_DIR/assets/minecraft/sounds/custom/ffa/" 2>/dev/null || true
  export PACK_DIR
  python3 - <<'PY'
import json
import os
from pathlib import Path
pack_dir = Path(os.environ["PACK_DIR"])
sounds_path = pack_dir / "assets/minecraft/sounds.json"
data = json.loads(sounds_path.read_text()) if sounds_path.exists() else {}
data["custom.ffa.countdown"] = {"sounds": ["custom/ffa/countdown"]}
data["custom.ffa.orb"] = {"sounds": ["custom/ffa/orb"]}
data["custom.ffa.legendary"] = {"sounds": ["custom/ffa/legendary"]}
data["custom.ffa.fail"] = {"sounds": ["custom/ffa/fail"]}
sounds_path.parent.mkdir(parents=True, exist_ok=True)
sounds_path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print("ffa sounds: countdown/orb/legendary/fail", flush=True)
PY
fi

UI_SOUNDS="${UI_SOUNDS_DIR:-$ROOT/resourcepack/assets/ui-sounds}"
if [[ -d "$UI_SOUNDS" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/sounds/custom/ui"
  cp -f "$UI_SOUNDS"/*.ogg "$PACK_DIR/assets/minecraft/sounds/custom/ui/" 2>/dev/null || true
  export PACK_DIR
  python3 - <<'PY'
import json
import os
from pathlib import Path
pack_dir = Path(os.environ["PACK_DIR"])
sounds_path = pack_dir / "assets/minecraft/sounds.json"
data = json.loads(sounds_path.read_text()) if sounds_path.exists() else {}
data["custom.ui.click"] = {"sounds": ["custom/ui/click"]}
sounds_path.parent.mkdir(parents=True, exist_ok=True)
sounds_path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print("ui sounds: click", flush=True)
PY
fi

BR_SOUNDS="${BR_SOUNDS_DIR:-$ROOT/resourcepack/assets/br-sounds}"
if [[ -d "$BR_SOUNDS" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/sounds/custom/br"
  for sound in countdown go phase craft craft_available; do
    [[ -f "$BR_SOUNDS/$sound.ogg" ]] && cp -f "$BR_SOUNDS/$sound.ogg" "$PACK_DIR/assets/minecraft/sounds/custom/br/"
  done
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

VICTORY_SOUND="${VICTORY_SOUND_FILE:-$ROOT/resourcepack/assets/br-sounds/victory.ogg}"
if [[ ! -f "$VICTORY_SOUND" ]]; then
  VICTORY_SOUND="$MEETUPS_SOUNDS/victory.ogg"
fi
if [[ -f "$VICTORY_SOUND" ]]; then
  mkdir -p "$PACK_DIR/assets/minecraft/sounds/custom"
  cp -f "$VICTORY_SOUND" "$PACK_DIR/assets/minecraft/sounds/custom/victory.ogg"
  export PACK_DIR
  python3 - <<'PY'
import json
import os
from pathlib import Path
pack_dir = Path(os.environ["PACK_DIR"])
sounds_path = pack_dir / "assets/minecraft/sounds.json"
data = json.loads(sounds_path.read_text()) if sounds_path.exists() else {}
data.pop("custom.br.victory", None)
data.pop("custom.meetups.victory", None)
data["custom.victory"] = {"sounds": ["custom/victory"]}
sounds_path.parent.mkdir(parents=True, exist_ok=True)
sounds_path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
print("shared victory sound", flush=True)
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
# Ensure 1.21.4+ item model overlay is actually applied (Hoplite ships CMD overrides
# in models/item; 1.21.4+ needs assets/minecraft/items or a declared overlay).
overlays = data.setdefault("overlays", {})
entries = overlays.setdefault("entries", [])
if not any(e.get("directory") == "overlay_1_21_4" for e in entries if isinstance(e, dict)):
    entries.append({
        "formats": {"min_inclusive": 46, "max_inclusive": 99},
        "directory": "overlay_1_21_4",
    })
meta.write_text(json.dumps(data, indent=2) + "\n")
print("pack.mcmeta bust:", pack["description"], "format=", pack["pack_format"], "overlays=", len(entries), flush=True)
PY

# Promote legacy custom_model_data overrides → 1.21.4+ items/*.json range_dispatch
# (without this, legendary weapons render as vanilla tools on 1.21.4+).
PACK_DIR="$PACK_DIR" python3 - <<'PY'
import json, os, re
from pathlib import Path

pack = Path(os.environ["PACK_DIR"])
models = pack / "assets/minecraft/models/item"
items_dir = pack / "assets/minecraft/items"
items_dir.mkdir(parents=True, exist_ok=True)

def extract_cmd_overrides(model_json: dict) -> list[tuple[int, str]]:
    out = []
    for entry in model_json.get("overrides") or []:
        pred = entry.get("predicate") or {}
        if "custom_model_data" not in pred:
            continue
        try:
            cmd = int(pred["custom_model_data"])
        except (TypeError, ValueError):
            continue
        model = entry.get("model")
        if isinstance(model, str) and model:
            out.append((cmd, model))
    # stable unique by cmd (first wins)
    seen = set()
    uniq = []
    for cmd, model in sorted(out, key=lambda t: t[0]):
        if cmd in seen:
            continue
        seen.add(cmd)
        uniq.append((cmd, model))
    return uniq

def fallback_model(item_id: str, model_json: dict) -> str:
    # ALWAYS the vanilla item model — parent is often item/generated or item/handheld
    # without textures, which renders as invisible/air for CMD=0 items.
    return f"minecraft:item/{item_id}"

converted = 0
for model_path in sorted(models.glob("*.json")):
    item_id = model_path.stem
    try:
        data = json.loads(model_path.read_text(encoding="utf-8"))
    except Exception as ex:
        print(f"skip bad model {model_path.name}: {ex}", flush=True)
        continue
    overrides = extract_cmd_overrides(data)
    if not overrides:
        continue
    out_path = items_dir / f"{item_id}.json"
    # Keep hand-authored item defs that already have non-empty range_dispatch entries
    # (blood mace / hub paper / totems) — merge CMD thresholds from overrides.
    existing_entries = []
    existing_fallback = {"type": "model", "model": fallback_model(item_id, data)}
    if out_path.is_file():
        try:
            cur = json.loads(out_path.read_text(encoding="utf-8"))
            model = cur.get("model") or {}
            if model.get("type") == "range_dispatch" and model.get("property") == "custom_model_data":
                existing_entries = list(model.get("entries") or [])
                # Prefer vanilla item fallback even if an older broken parent was saved.
                existing_fallback = {"type": "model", "model": f"minecraft:item/{item_id}"}
        except Exception:
            pass
    by_threshold = {}
    for e in existing_entries:
        th = e.get("threshold")
        if th is not None:
            by_threshold[int(th)] = e
    for cmd, model_name in overrides:
        # normalize minecraft-relative paths
        if model_name.startswith("item/") or model_name.startswith("vred/") or model_name.startswith("civilization:"):
            model_ref = model_name if ":" in model_name else f"minecraft:{model_name}"
        else:
            model_ref = model_name if ":" in model_name else f"minecraft:item/{model_name}"
        by_threshold[cmd] = {
            "threshold": cmd,
            "model": {"type": "model", "model": model_ref},
        }
    entries = [by_threshold[k] for k in sorted(by_threshold)]
    payload = {
        "model": {
            "type": "range_dispatch",
            "property": "custom_model_data",
            "fallback": existing_fallback,
            "entries": entries,
        }
    }
    out_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    converted += 1

print(f"items range_dispatch generated/merged for {converted} item(s)", flush=True)

# Force vanilla item fallbacks everywhere (main + overlays) — parent models render as air.
# Do NOT flatten stateful trees (crossbow charge_type, bow pull, fishing_rod cast, elytra broken).
STATEFUL_TYPES = {
    "condition", "select", "range_dispatch",
    "minecraft:condition", "minecraft:select", "minecraft:range_dispatch",
}
HANDHELD_ITEMS = {
    "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword",
    "wooden_axe", "stone_axe", "iron_axe", "golden_axe", "diamond_axe", "netherite_axe",
    "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe",
    "wooden_shovel", "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel", "netherite_shovel",
    "wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe",
    "trident", "mace",
}


def vanilla_crossbow_fallback() -> dict:
    return {
        "type": "condition",
        "property": "using_item",
        "on_true": {
            "type": "range_dispatch",
            "property": "crossbow/pull",
            "fallback": {"type": "model", "model": "minecraft:item/crossbow_pulling_0"},
            "entries": [
                {"threshold": 0.58, "model": {"type": "model", "model": "minecraft:item/crossbow_pulling_1"}},
                {"threshold": 1.0, "model": {"type": "model", "model": "minecraft:item/crossbow_pulling_2"}},
            ],
        },
        "on_false": {
            "type": "select",
            "property": "charge_type",
            "fallback": {"type": "model", "model": "minecraft:item/crossbow"},
            "cases": [
                {"when": "arrow", "model": {"type": "model", "model": "minecraft:item/crossbow_arrow"}},
                {"when": "rocket", "model": {"type": "model", "model": "minecraft:item/crossbow_firework"}},
            ],
        },
    }


def vanilla_bow_fallback() -> dict:
    return {
        "type": "condition",
        "property": "using_item",
        "on_false": {"type": "model", "model": "minecraft:item/bow"},
        "on_true": {
            "type": "range_dispatch",
            "property": "use_duration",
            "scale": 0.05,
            "fallback": {"type": "model", "model": "minecraft:item/bow_pulling_0"},
            "entries": [
                {"threshold": 0.65, "model": {"type": "model", "model": "minecraft:item/bow_pulling_1"}},
                {"threshold": 0.9, "model": {"type": "model", "model": "minecraft:item/bow_pulling_2"}},
            ],
        },
    }


def vanilla_fishing_rod_fallback() -> dict:
    return {
        "type": "condition",
        "property": "fishing_rod/cast",
        "on_true": {"type": "model", "model": "minecraft:item/fishing_rod_cast"},
        "on_false": {"type": "model", "model": "minecraft:item/fishing_rod"},
    }


VANILLA_STATE_FALLBACKS = {
    "crossbow": vanilla_crossbow_fallback,
    "bow": vanilla_bow_fallback,
    "fishing_rod": vanilla_fishing_rod_fallback,
}

# Hoplite override-only models have no parent/textures — 1.21.4+ fallbacks render as missing texture.
restored_models = 0
for model_path in models.glob("*.json"):
    try:
        data = json.loads(model_path.read_text(encoding="utf-8"))
    except Exception:
        continue
    if not isinstance(data, dict):
        continue
    if data.get("parent") or data.get("textures") or data.get("elements"):
        continue
    item_id = model_path.stem
    data["parent"] = "minecraft:item/handheld" if item_id in HANDHELD_ITEMS else "minecraft:item/generated"
    data["textures"] = {"layer0": f"minecraft:item/{item_id}"}
    model_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    restored_models += 1
print(f"restored parent/textures on {restored_models} override-only model(s)", flush=True)

fixed = 0
stateful = 0
for items_json in pack.rglob("items/*.json"):
    if "minecraft/items" not in str(items_json).replace("\\", "/"):
        continue
    try:
        data = json.loads(items_json.read_text(encoding="utf-8"))
    except Exception:
        continue
    model = data.get("model")
    if not isinstance(model, dict) or model.get("type") not in ("range_dispatch", "minecraft:range_dispatch"):
        continue
    item_id = items_json.stem
    if item_id in VANILLA_STATE_FALLBACKS:
        model["fallback"] = VANILLA_STATE_FALLBACKS[item_id]()
        items_json.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        stateful += 1
        continue
    fb = model.get("fallback") or {}
    if isinstance(fb, dict) and fb.get("type") in STATEFUL_TYPES:
        continue
    want = f"minecraft:item/{item_id}"
    cur = fb.get("model") if isinstance(fb, dict) else None
    if cur in (None, "minecraft:item/generated", "minecraft:item/handheld", "item/generated", "item/handheld", f"item/{item_id}"):
        model["fallback"] = {"type": "model", "model": want}
        items_json.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        fixed += 1
print(f"forced vanilla item fallbacks: {fixed}; stateful fallbacks: {stateful}", flush=True)
PY

# Lobby welcome sound (levelup.mp3 → vorbis ogg)
LOBBY_WELCOME_OGG="$ROOT/resourcepack/assets/blade/sounds/lobby_welcome.ogg"
if [[ -f "$LOBBY_WELCOME_OGG" ]]; then
  mkdir -p "$PACK_DIR/assets/blade/sounds"
  cp -f "$LOBBY_WELCOME_OGG" "$PACK_DIR/assets/blade/sounds/lobby_welcome.ogg"
  export PACK_DIR
  python3 - <<'PY'
import json, os
from pathlib import Path
pack = Path(os.environ["PACK_DIR"])
blade_sounds = pack / "assets/blade/sounds.json"
blade = json.loads(blade_sounds.read_text()) if blade_sounds.is_file() else {}
# Event blade:lobby_welcome → assets/blade/sounds.json key "lobby_welcome"
blade["lobby_welcome"] = {"sounds": ["lobby_welcome"]}
blade_sounds.parent.mkdir(parents=True, exist_ok=True)
blade_sounds.write_text(json.dumps(blade, ensure_ascii=False, indent=2) + "\n")
print("blade:lobby_welcome sound registered", flush=True)
PY
fi

# Blood Mace legendary texture (CMD 2000 — outside cosmetic mace range 100-399)
BLOOD_MACE_TEX="${BLOOD_MACE_TEXTURE:-$ROOT/resourcepack/assets/blood-mace/blood_mace.png}"
if [[ ! -f "$BLOOD_MACE_TEX" ]]; then
  echo "Blood mace texture not found: $BLOOD_MACE_TEX" >&2
  exit 1
fi
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
  MACE_SKINS_DIR="${MACE_SKINS_DIR:-$ROOT/resourcepack/assets/mace-skins}"
  MACES_JSON="${MACES_JSON:-$ROOT/custom-plugins/blade-cosmetics/src/main/resources/maces.json}"
  export PACK_DIR MACE_SKINS_DIR MACES_JSON
  python3 - <<'PY'
import json, os, shutil, sys
from pathlib import Path
from PIL import Image

pack = Path(os.environ["PACK_DIR"])
src = Path(os.environ["MACE_SKINS_DIR"])
catalog_path = Path(os.environ["MACES_JSON"])
tex_dir = pack / "assets/blade/textures/item/mace_skin"
model_dir = pack / "assets/blade/models/item/mace_skin"
item_path = pack / "assets/minecraft/items/mace.json"
tex_dir.mkdir(parents=True, exist_ok=True)
model_dir.mkdir(parents=True, exist_ok=True)
item_path.parent.mkdir(parents=True, exist_ok=True)

skip = {"sword", "maceicon"}
catalog = []
if catalog_path.is_file():
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
if not isinstance(catalog, list):
    print(f"ERROR: maces.json must be a list, got {type(catalog)}", flush=True)
    sys.exit(1)

BLOOD_CMD = 2000
entries = [{
    "threshold": BLOOD_CMD,
    "model": {"type": "model", "model": "minecraft:item/blood_mace"},
}]
copied = 0
animated = 0
custom_models = 0
for item in catalog:
    sid = item.get("id")
    cmd = item.get("cmd")
    if not sid or sid in skip or cmd is None:
        continue
    png = src / f"{sid}.png"
    if not png.is_file():
        print(f"skip missing texture {sid}", flush=True)
        continue
    im = Image.open(png).convert("RGBA")
    w, h = im.size
    dest_png = tex_dir / f"{sid}.png"
    if w > h and h > 0 and w % h == 0:
        frame = h
        n = w // h
        stacked = Image.new("RGBA", (frame, frame * n))
        for i in range(n):
            stacked.paste(im.crop((i * frame, 0, (i + 1) * frame, frame)), (0, i * frame))
        stacked.save(dest_png)
    else:
        shutil.copy2(png, dest_png)
    meta = src / f"{sid}.png.mcmeta"
    if meta.is_file():
        shutil.copy2(meta, tex_dir / f"{sid}.png.mcmeta")
        animated += 1
    model_src = src / f"{sid}.json"
    if model_src.is_file():
        shutil.copy2(model_src, model_dir / f"{sid}.json")
        custom_models += 1
    else:
        (model_dir / f"{sid}.json").write_text(json.dumps({
            "parent": "minecraft:item/handheld_mace",
            "textures": {"layer0": f"blade:item/mace_skin/{sid}"},
        }, indent=2) + "\n", encoding="utf-8")
    entries.append({
        "threshold": int(cmd),
        "model": {"type": "model", "model": f"blade:item/mace_skin/{sid}"},
    })
    copied += 1

if copied < 50:
    print(f"ERROR: only {copied} cosmetic mace skins copied — refusing blood-only mace.json", flush=True)
    sys.exit(1)

entries.sort(key=lambda e: e["threshold"])
item_definition = {
    "model": {
        "type": "range_dispatch",
        "property": "custom_model_data",
        "index": 0,
        "fallback": {"type": "model", "model": "minecraft:item/mace"},
        "entries": entries,
    }
}
blood_entries = [
    entry for entry in entries
    if entry.get("model", {}).get("model") == "minecraft:item/blood_mace"
]
cosmetic_entries = [
    entry for entry in entries
    if entry.get("model", {}).get("model", "").startswith("blade:item/mace_skin/")
]
if (
    item_definition["model"].get("index") != 0
    or len(blood_entries) != 1
    or blood_entries[0].get("threshold") != 2000
):
    print("ERROR: blood mace model must use custom_model_data index 0 at threshold 2000", flush=True)
    sys.exit(1)
if len(cosmetic_entries) != copied or any(
    not 100 <= int(entry.get("threshold", -1)) <= 399
    for entry in cosmetic_entries
):
    print("ERROR: cosmetic mace CMD values must remain in range 100-399", flush=True)
    sys.exit(1)
item_path.write_text(json.dumps(item_definition, indent=2) + "\n", encoding="utf-8")
for overlay_item in pack.glob("overlay*/assets/minecraft/items"):
    shutil.copy2(item_path, overlay_item / "mace.json")
print(
    f"blood mace CMD {BLOOD_CMD} + {copied} cosmetic mace skins "
    f"(animated={animated}, custom_models={custom_models})",
    flush=True,
)
PY
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

# BR / Hoplite smelter's pickaxe (iron_pickaxe CMD 1 → civilization model)
SMELTERS_TEX="${SMELTERS_PICKAXE_TEXTURE:-$ROOT/resourcepack/assets/br-items/smelters_pickaxe.png}"
SMELTERS_META="${SMELTERS_TEX}.mcmeta"
if [[ -f "$SMELTERS_TEX" ]]; then
  mkdir -p "$PACK_DIR/assets/civilization/textures/item/tools" \
           "$PACK_DIR/assets/civilization/models/item/tools" \
           "$PACK_DIR/assets/minecraft/items"
  cp -f "$SMELTERS_TEX" "$PACK_DIR/assets/civilization/textures/item/tools/smelters_pickaxe.png"
  if [[ -f "$SMELTERS_META" ]]; then
    cp -f "$SMELTERS_META" "$PACK_DIR/assets/civilization/textures/item/tools/smelters_pickaxe.png.mcmeta"
  fi
  cat > "$PACK_DIR/assets/civilization/models/item/tools/smelters_pickaxe.json" <<'EOF'
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "civilization:item/tools/smelters_pickaxe"
  }
}
EOF
  cat > "$PACK_DIR/assets/minecraft/items/iron_pickaxe.json" <<'EOF'
{
    "model": {
        "type": "range_dispatch",
        "property": "custom_model_data",
        "fallback": {
            "type": "model",
            "model": "minecraft:item/iron_pickaxe"
        },
        "entries": [
            {
                "threshold": 1,
                "model": {
                    "type": "model",
                    "model": "civilization:item/tools/smelters_pickaxe"
                }
            }
        ]
    }
}
EOF
  echo "smelters pickaxe: iron_pickaxe CMD 1"
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

# Wearable hat item-models. Do NOT reuse minecraft:carved_pumpkin as item_model:
# 1.21.11 then uses the pumpkin special renderer and the hat is invisible (MC-305284).
hats_json = root / "custom-plugins/blade-cosmetics/src/main/resources/hats.json"
hat_item_count = 0
if hats_json.is_file():
    hats = json.loads(hats_json.read_text(encoding="utf-8"))
    targets = [pack, pack / "overlay_1_21_4"]
    for hat in hats:
        model = (hat.get("model") or "").strip()
        if ":" not in model:
            continue
        ns, path = model.split(":", 1)
        payload = json.dumps({
            "model": {"type": "minecraft:model", "model": model}
        }, indent=2) + "\n"
        for base in targets:
            out = base / "assets" / ns / "items" / f"{path}.json"
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(payload)
        hat_item_count += 1
    print(f"hat item models: {hat_item_count}", flush=True)

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

# BR crafts / kit GUIs (ascent 21 aligns parchment slots; kits use 16 like cosmetics)
br_ui = root / "resourcepack/assets/br-ui"
# ascent 18 = previous 21 shifted 3px down
add_gui_glyph(br_ui / "crafts_legendary.png", "br_crafts_legendary_gui", 0xE207, height=256, ascent=18)
add_gui_glyph(br_ui / "crafts_basic.png", "br_crafts_basic_gui", 0xE208, height=256, ascent=18)
add_gui_glyph(br_ui / "crafts_recipe.png", "br_crafts_recipe_gui", 0xE209, height=256, ascent=36)
add_gui_glyph(br_ui / "kits_menu.png", "br_kits_menu_gui", 0xE20A, height=256, ascent=16)

# Cosmetics rarity badges (8px tall UI strips). limited is packed for later, unused in plugins.
rarity_src = root / "resourcepack/assets/rarity"
rarity_dir = pack / "assets/blade/textures/font/rarity"
rarity_dir.mkdir(parents=True, exist_ok=True)
for rarity_name, rarity_cp in (
    ("common", 0xE220),
    ("rare", 0xE221),
    ("epic", 0xE222),
    ("legendary", 0xE223),
    ("exclusive", 0xE224),
    ("limited", 0xE225),
):
    rarity_file = rarity_src / f"{rarity_name}.png"
    if not rarity_file.is_file():
        print("missing rarity", rarity_file, flush=True)
        continue
    im = Image.open(rarity_file).convert("RGBA")
    out = rarity_dir / f"{rarity_name}.png"
    im.save(out, format="PNG", optimize=True, icc_profile=None)
    providers.append({
        "type": "bitmap",
        "file": f"blade:font/rarity/{rarity_name}.png",
        "ascent": 7,
        "height": 8,
        "chars": [chr(rarity_cp)],
    })
    print(f"rarity {rarity_name} glyph U+{rarity_cp:04X}", flush=True)

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
            "file": f"minecraft:{ttf_name}.ttf",
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

# Merge BetterModel-generated beam assets (villager wand FX).
BM_BUILD="${BETTERMODEL_BUILD_DIR:-$ROOT/resourcepack/bettermodel-build}"
if [[ -d "$BM_BUILD/assets/bettermodel" ]]; then
  mkdir -p "$PACK_DIR/assets"
  cp -a "$BM_BUILD/assets/bettermodel" "$PACK_DIR/assets/"
  echo "Merged BetterModel assets from $BM_BUILD"
else
  echo "Warning: BetterModel build assets missing: $BM_BUILD" >&2
fi

# Merge Oraxen-generated pack (items use oraxen: namespace / item_model).
# Skip Oraxen lang overrides (they blank vanilla join strings). Merge fonts/atlases/sounds.
ORAXEN_BUILD="${ORAXEN_BUILD_DIR:-$ROOT/resourcepack/oraxen-build}"
if [[ -d "$ORAXEN_BUILD/assets" ]]; then
  PACK_DIR="$PACK_DIR" ORAXEN_BUILD="$ORAXEN_BUILD" python3 - <<'PY'
import json
import os
import shutil
from pathlib import Path

pack = Path(os.environ["PACK_DIR"])
ox = Path(os.environ["ORAXEN_BUILD"]) / "assets"
copied = 0
skipped_lang = 0

def merge_json_list(dst: Path, src: Path, key: str) -> None:
    incoming = json.loads(src.read_text(encoding="utf-8"))
    if not dst.is_file():
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(json.dumps(incoming, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return
    current = json.loads(dst.read_text(encoding="utf-8"))
    cur_list = current.setdefault(key, [])
    # Dedupe by JSON dump of each entry
    seen = {json.dumps(e, sort_keys=True) for e in cur_list}
    added = 0
    for e in incoming.get(key, []):
        sig = json.dumps(e, sort_keys=True)
        if sig in seen:
            continue
        cur_list.append(e)
        seen.add(sig)
        added += 1
    dst.write_text(json.dumps(current, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"merged {dst.relative_to(pack)} +{added} {key}", flush=True)

def merge_sounds(dst: Path, src: Path) -> None:
    incoming = json.loads(src.read_text(encoding="utf-8"))
    if not dst.is_file():
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(json.dumps(incoming, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return
    current = json.loads(dst.read_text(encoding="utf-8"))
    for k, v in incoming.items():
        current[k] = v
    dst.write_text(json.dumps(current, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"merged sounds.json keys={len(incoming)}", flush=True)

skipped_blocks = 0
skipped_font = 0
for src in ox.rglob("*"):
    if not src.is_file():
        continue
    rel = src.relative_to(ox)
    # Never take Oraxen lang — blanks connect.joining / menu strings.
    if rel.parts[:2] == ("minecraft", "lang"):
        skipped_lang += 1
        continue
    # Never take Oraxen noteblock/stringblock blockstates (copper slabs/trapdoors → white mush).
    if len(rel.parts) >= 2 and rel.parts[0] == "minecraft" and rel.parts[1] == "blockstates":
        skipped_blocks += 1
        continue
    # Never merge Oraxen into minecraft/font/default.json (breaks shadows / gradient glyphs).
    if rel.as_posix() == "minecraft/font/default.json":
        skipped_font += 1
        continue
    # Never take custom core text shaders (break shadows / gradient letters).
    if len(rel.parts) >= 2 and rel.parts[0] == "minecraft" and rel.parts[1] == "shaders":
        skipped_blocks += 1
        continue
    dst = pack / "assets" / rel
    # Smart merges
    if rel.as_posix() == "minecraft/atlases/blocks.json":
        merge_json_list(dst, src, "sources")
        continue
    if rel.as_posix() == "minecraft/sounds.json":
        merge_sounds(dst, src)
        continue
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    copied += 1

# Strip any previously merged Oraxen copper/crystalmush blockstate overrides.
bs = pack / "assets/minecraft/blockstates"
removed_bs = 0
if bs.is_dir():
    for path in list(bs.glob("*.json")):
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        if "crystalmush" in text or "oraxen:" in text or "default/caveblock" in text:
            path.unlink(missing_ok=True)
            removed_bs += 1

shaders_dir = pack / "assets/minecraft/shaders"
if shaders_dir.exists():
    shutil.rmtree(shaders_dir)
    print("removed assets/minecraft/shaders", flush=True)

print(
    f"Merged Oraxen assets: copied={copied} skipped_lang={skipped_lang} "
    f"skipped_blockstates={skipped_blocks} skipped_font={skipped_font} removed_bs={removed_bs}",
    flush=True,
)
PY
else
  echo "Warning: Oraxen build assets missing: $ORAXEN_BUILD" >&2
fi

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

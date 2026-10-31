#!/usr/bin/env python3
"""Adds the battle pass art (assets/battlepass) to a built BladePack.zip.

Menu backgrounds are glyphs of the default font, drawn like the other menu glyphs
(menu_guis.py): the plugin puts the shift "\\uF808\\uF806\\uF826" (8 px left, onto the chest's
edge) and the glyph in the white inventory title. A glyph's top lands at gui y = 13 - ascent.

    U+E240..U+E249  page_01..page_10  levels 1-9 ... 82-90, 6-row chest + player inventory
    U+E24A          quests            quests page (daily / weekly / monthly)
    U+E24B          bar_base          one 6 px step of the base header bar (gui y 41)
    U+E24C          bar_premium       one 6 px step of the premium header bar (gui y 113)
    U+E400..U+E464  progress 0..100 % quest progress bar for item lore (102 x 6)

Reward and quest icons are item models: blade:bp/<name>, set with the item_model component.
Every reward has <name>, <name>_locked, <name>_ready (animated) and <name>_claimed.

Usage: battlepass.py path/to/BladePack.zip   (rewrites the zip in place)
"""
import json
import sys
import zipfile
from io import BytesIO
from pathlib import Path

from PIL import Image

SOURCE = Path(__file__).resolve().parent / "assets/battlepass"
FONT = "assets/minecraft/font/default.json"
PREFIX = "blade:font/battlepass/"
ITEM_DIR = "bp"

GLYPHS = [
    # file, first codepoint, ascent, glyphs stacked vertically (one per row)
    *[(f"page_{k:02d}", 0xE240 + k - 1, 19, 1) for k in range(1, 11)],
    ("quests", 0xE24A, 19, 1),
    ("bar_base", 0xE24B, -28, 1),
    ("bar_premium", 0xE24C, -100, 1),
    ("progress", 0xE400, 6, 101),
]


def png_bytes(path):
    out = BytesIO()
    Image.open(path).convert("RGBA").save(out, format="PNG", optimize=True)
    return out.getvalue()


def main(path):
    with zipfile.ZipFile(path) as z:
        files = {info.filename: z.read(info.filename) for info in z.infolist() if not info.is_dir()}

    # Re-running replaces our own entries.
    for name in [n for n in files if f"/{ITEM_DIR}/" in n and n.startswith("assets/blade/")]:
        del files[name]
    font = json.loads(files[FONT]) if FONT in files else {"providers": []}
    providers = [p for p in font.get("providers", []) if not str(p.get("file", "")).startswith(PREFIX)]
    taken = {c for p in providers for row in p.get("chars", []) for c in row}
    taken |= {c for p in providers for c in p.get("advances", {})}

    for name, first, ascent, count in GLYPHS:
        chars = [chr(first + i) for i in range(count)]
        clash = [c for c in chars if c in taken]
        if clash:
            raise SystemExit(f"U+{ord(clash[0]):04X} ({name}) is already used by another glyph")
        image = Image.open(SOURCE / "gui" / f"{name}.png")
        files[f"assets/blade/textures/font/battlepass/{name}.png"] = png_bytes(SOURCE / "gui" / f"{name}.png")
        providers.append({
            "type": "bitmap",
            "file": f"{PREFIX}{name}.png",
            "ascent": ascent,
            "height": image.size[1] // count,
            "chars": chars,
        })
        print(f"{name} glyph U+{first:04X}{f'..U+{first + count - 1:04X}' if count > 1 else ''} ascent {ascent}",
              flush=True)

    font["providers"] = providers
    files[FONT] = json.dumps(font, ensure_ascii=False, indent=2).encode("utf-8")

    icons = sorted(p for p in (SOURCE / "item").glob("*.png"))
    for png in icons:
        name = png.stem
        files[f"assets/blade/textures/item/{ITEM_DIR}/{name}.png"] = png_bytes(png)
        meta = png.with_name(png.name + ".mcmeta")
        if meta.exists():
            files[f"assets/blade/textures/item/{ITEM_DIR}/{name}.png.mcmeta"] = meta.read_bytes()
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"blade:item/{ITEM_DIR}/{name}"}}
        files[f"assets/blade/models/item/{ITEM_DIR}/{name}.json"] = json.dumps(model, indent=2).encode()
        item = {"model": {"type": "minecraft:model", "model": f"blade:item/{ITEM_DIR}/{name}"}}
        files[f"assets/blade/items/{ITEM_DIR}/{name}.json"] = json.dumps(item, indent=2).encode()
    print(f"{len(icons)} item models blade:{ITEM_DIR}/*", flush=True)

    # The pages cover the player inventory too: no "Inventory" label in any language the pack has.
    for name in [n for n in files if n.startswith("assets/minecraft/lang/") and n.endswith(".json")]:
        lang = json.loads(files[name].decode("utf-8-sig"))
        if lang.get("container.inventory") != "":
            lang["container.inventory"] = ""
            files[name] = json.dumps(lang, ensure_ascii=False, indent=2).encode("utf-8")
            print(f"{name}: container.inventory hidden", flush=True)

    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])

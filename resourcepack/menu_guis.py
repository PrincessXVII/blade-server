#!/usr/bin/env python3
"""Adds chest-menu backgrounds to a built BladePack.zip as glyphs of the default font.

A plugin draws one by putting the shift "\\uF808\\uF806\\uF826" (8 px left, onto the chest's
edge) and the glyph in the white inventory title. Ascent places the texture vertically: the
title sits at y = 6 and a glyph's top lands at 13 - ascent, so a texture whose first slot frame
starts at row R needs ascent = R - 4 to put that frame on the chest's (row 17).

    U+E20C  ffa_shop_legendary  FFA shop, legendaries page  (BladeFFA)
    U+E20D  ffa_shop_items      FFA shop, consumables page  (BladeFFA)

Usage: menu_guis.py path/to/BladePack.zip   (rewrites the zip in place)
"""
import json
import sys
import zipfile
from io import BytesIO
from pathlib import Path

from PIL import Image

SOURCE = Path(__file__).resolve().parent / "assets/menu-guis"
FONT = "assets/minecraft/font/default.json"
PREFIX = "blade:font/menu_guis/"

GUIS = [
    # name, codepoint, ascent
    ("ffa_shop_legendary", 0xE20C, 18),
    ("ffa_shop_items", 0xE20D, 18),
]


def main(path):
    with zipfile.ZipFile(path) as z:
        files = {info.filename: z.read(info.filename) for info in z.infolist() if not info.is_dir()}

    font = json.loads(files[FONT]) if FONT in files else {"providers": []}
    providers = [p for p in font.get("providers", []) if not str(p.get("file", "")).startswith(PREFIX)]
    taken = {c for p in providers for row in p.get("chars", []) for c in row}

    for name, codepoint, ascent in GUIS:
        char = chr(codepoint)
        if char in taken:
            raise SystemExit(f"U+{codepoint:04X} ({name}) is already used by another glyph")
        image = Image.open(SOURCE / f"{name}.png").convert("RGBA")
        out = BytesIO()
        image.save(out, format="PNG", optimize=True)
        files[f"assets/blade/textures/font/menu_guis/{name}.png"] = out.getvalue()
        providers.append({
            "type": "bitmap",
            "file": f"{PREFIX}{name}.png",
            "ascent": ascent,
            "height": image.size[1],
            "chars": [char],
        })
        print(f"{name} glyph U+{codepoint:04X} ascent {ascent}", flush=True)

    font["providers"] = providers
    files[FONT] = json.dumps(font, ensure_ascii=False, indent=2).encode("utf-8")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])

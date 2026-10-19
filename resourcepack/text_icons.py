#!/usr/bin/env python3
"""Adds the small text icons to a built BladePack.zip.

Every PNG listed in assets/text-icons/icons.json becomes one glyph of the default font at its
private-use codepoint (U+E600 and up), so chat, scoreboards and menus can show it as a single
character. Bitmap glyphs take the text colour, so put icons inside <white> to keep their colours.

Icons taller than ICON_HEIGHT are scaled down to it; smaller ones keep their pixel size and are
centred on the same line.

Usage: text_icons.py path/to/BladePack.zip   (rewrites the zip in place)
"""
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ICON_DIR = Path(__file__).resolve().parent / "assets/text-icons"
FONT = "assets/minecraft/font/default.json"
TEXTURE_NS = "blade"
TEXTURE_DIR = "font/icons"
ICON_HEIGHT = 9
ICON_ASCENT = 8


def main(path):
    with zipfile.ZipFile(path) as z:
        files = {info.filename: z.read(info.filename) for info in z.infolist() if not info.is_dir()}

    prefix = f"{TEXTURE_NS}:{TEXTURE_DIR}/"
    font = json.loads(files[FONT]) if FONT in files else {"providers": []}
    providers = [p for p in font.get("providers", []) if not str(p.get("file", "")).startswith(prefix)]
    taken = {c for p in providers for row in p.get("chars", []) for c in row}

    manifest = json.loads((ICON_DIR / "icons.json").read_text())
    for icon in manifest:
        char = chr(int(icon["char"], 16))
        if char in taken:
            raise SystemExit(f"U+{icon['char']} ({icon['name']}) is already used by another glyph")
        png = (ICON_DIR / f"{icon['name']}.png").read_bytes()
        files[f"assets/{TEXTURE_NS}/textures/{TEXTURE_DIR}/{icon['name']}.png"] = png
        native = Image.open(ICON_DIR / f"{icon['name']}.png").size[1]
        height = min(ICON_HEIGHT, native)
        providers.append({
            "type": "bitmap",
            "file": f"{prefix}{icon['name']}.png",
            "height": height,
            "ascent": ICON_ASCENT - (ICON_HEIGHT - height) // 2,
            "chars": [char],
        })
    font["providers"] = providers
    files[FONT] = json.dumps(font, ensure_ascii=False, indent=2).encode()

    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])
    print(f"text icons: {len(manifest)} glyphs from U+{manifest[0]['char']}", flush=True)


if __name__ == "__main__":
    main(sys.argv[1])

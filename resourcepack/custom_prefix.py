#!/usr/bin/env python3
"""Personal prefix icons of the "custom" donate rank in BladePack.zip.

  custom_prefix.py <BladePack.zip> placeholder <png>      the shared "custom" icon on U+E120
  custom_prefix.py <BladePack.zip> <player> <png>         a player's own icon, U+E400..U+E4FF

Each player keeps their code point (custom_prefixes.json next to this script), so drawing the icon
again only swaps the picture. Prints the in-game command that binds it:
  /customprefix bind <player> <code>
Then push the pack and update server.properties as usual. Bitmaps are 9 px high, like rank icons.
"""
import json
import sys
import zipfile
from pathlib import Path

FONT = "assets/minecraft/font/default.json"
PLACEHOLDER = 0xE120
FIRST, LAST = 0xE400, 0xE4FF
MAP_FILE = Path(__file__).with_name("custom_prefixes.json")


def main(pack_path, who, png_path):
    png = Path(png_path).read_bytes()
    with zipfile.ZipFile(pack_path) as z:
        files = {i.filename: z.read(i.filename) for i in z.infolist() if not i.is_dir()}
    font = json.loads(files[FONT])
    if who == "placeholder":
        texture, code = "blade:font/ranks/custom.png", PLACEHOLDER
    else:
        owners = json.loads(MAP_FILE.read_text(encoding="utf-8")) if MAP_FILE.exists() else {}
        key = who.lower()
        if key not in owners:
            taken = set(owners.values())
            free = [c for c in range(FIRST, LAST + 1) if c not in taken]
            if not free:
                raise SystemExit("no free code points left in U+E400..U+E4FF")
            owners[key] = free[0]
            MAP_FILE.write_text(json.dumps(owners, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        texture, code = f"blade:font/custom/{key}.png", owners[key]
    namespace, rel = texture.split(":", 1)
    files[f"assets/{namespace}/textures/{rel}"] = png
    char = chr(code)
    font["providers"] = [p for p in font["providers"] if char not in "".join(p.get("chars", []))]
    font["providers"].append({"type": "bitmap", "file": texture, "ascent": 8, "height": 9, "chars": [char]})
    files[FONT] = json.dumps(font, ensure_ascii=False).encode("utf-8")
    with zipfile.ZipFile(pack_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])
    print(f"{who}: U+{code:04X} -> {texture}")
    if who != "placeholder":
        print(f"in game, after the pack is live: /customprefix bind {who} {code:04X}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    main(*sys.argv[1:4])

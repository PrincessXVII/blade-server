#!/usr/bin/env python3
"""Puts the 3D strider logos and the rank icons into a built BladePack.zip.

The logos (assets/logos/strider_*.png) are scaled to the width the old Blade logos took, so the
tab header and the scoreboard keep their width; the bottom edge stays where it was, the logo is
just less tall. The tab logo is two glyphs side by side with U+E119 (advance -1) between them.

    lobby     U+E110 + U+E117 tab (also every server without its own), U+E118 scoreboard
    br        U+E11A + U+E11B tab, U+E11C scoreboard
    meetups   U+E11D + U+E11E tab, U+E11F scoreboard

Rank icons (assets/ranks/<group>.png) replace blade:font/ranks/<group>.png; their glyphs keep
their codepoints, height and ascent.

Usage: strider_logos.py path/to/BladePack.zip   (rewrites the zip in place)
"""
import json
import sys
import zipfile
from io import BytesIO
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
FONT = "assets/minecraft/font/default.json"
TEX = "assets/blade/textures/font/"
SRC_TEX = HERE.parent / "resourcepack-src" / TEX

# Old Blade logo: tab 2 x 256x192 shown 90 high (240 wide), bottom 34 below the baseline;
# scoreboard 256x96 shown 49 high (~131 wide), bottom 14 below the baseline.
# A font glyph wider or taller than 256 pixels does not fit the client's font atlas and shows as a
# missing-glyph box, so each half of the tab logo and the scoreboard logo stay at 256 wide.
TAB_WIDTH, TAB_BOTTOM, TAB_TEX_WIDTH = 240, 34, 512
BOARD_WIDTH, BOARD_BOTTOM, BOARD_TEX_WIDTH = 131, 14, 256

LOGOS = {
    "lobby": ("logo_tab_left", "logo_tab_right", "logo_scoreboard", 0xE110, 0xE117, 0xE118),
    "br": ("logo_br_tab_left", "logo_br_tab_right", "logo_br_scoreboard", 0xE11A, 0xE11B, 0xE11C),
    "meetups": ("logo_meetups_tab_left", "logo_meetups_tab_right", "logo_meetups_scoreboard", 0xE11D, 0xE11E, 0xE11F),
}


def png(image):
    out = BytesIO()
    image.save(out, format="PNG", optimize=True)
    return out.getvalue()


def cropped(path):
    image = Image.open(path).convert("RGBA")
    box = image.getchannel("A").point(lambda a: 255 if a > 24 else 0).getbbox()
    return image.crop(box)


def scaled(image, tex_width, shown_width):
    """Texture tex_width wide and the glyph height that shows it shown_width wide."""
    shown_height = max(1, round(shown_width * image.height / image.width))
    tex_height = round(tex_width * shown_height / shown_width)
    return image.resize((tex_width, tex_height), Image.LANCZOS), shown_height


def main(path):
    with zipfile.ZipFile(path) as z:
        files = {info.filename: z.read(info.filename) for info in z.infolist() if not info.is_dir()}
    font = json.loads(files[FONT])
    ours = {f"blade:font/{name}.png" for spec in LOGOS.values() for name in spec[:3]}
    providers = [p for p in font["providers"] if p.get("file") not in ours]
    taken = {c for p in providers for row in p.get("chars", []) for c in row}

    def put(name, image, char, height, ascent):
        if chr(char) in taken:
            raise SystemExit(f"U+{char:04X} ({name}) is already used by another glyph")
        raw = png(image)
        files[f"{TEX}{name}.png"] = raw
        if SRC_TEX.is_dir():
            (SRC_TEX / f"{name}.png").write_bytes(raw)
        providers.append({"type": "bitmap", "file": f"blade:font/{name}.png", "height": height,
                          "ascent": ascent, "chars": [chr(char)]})

    for key, (left, right, board, c_left, c_right, c_board) in LOGOS.items():
        logo = cropped(HERE / "assets/logos" / f"strider_{key}.png")
        tab, tab_h = scaled(logo, TAB_TEX_WIDTH, TAB_WIDTH)
        half = TAB_TEX_WIDTH // 2
        put(left, tab.crop((0, 0, half, tab.height)), c_left, tab_h, tab_h - TAB_BOTTOM)
        put(right, tab.crop((half, 0, TAB_TEX_WIDTH, tab.height)), c_right, tab_h, tab_h - TAB_BOTTOM)
        sb, sb_h = scaled(logo, BOARD_TEX_WIDTH, BOARD_WIDTH)
        put(board, sb, c_board, sb_h, sb_h - BOARD_BOTTOM)
        print(f"{key}: tab {tab.width}x{tab.height} shown {tab_h} high, scoreboard {sb.width}x{sb.height} "
              f"shown {sb_h} high", flush=True)

    ranks = sorted((HERE / "assets/ranks").glob("*.png"))
    for icon in ranks:
        name = f"{TEX}ranks/{icon.name}"
        if name not in files:
            raise SystemExit(f"no rank glyph {icon.stem} in the pack")
        raw = png(Image.open(icon).convert("RGBA"))
        files[name] = raw
        if (SRC_TEX / "ranks").is_dir():
            (SRC_TEX / "ranks" / icon.name).write_bytes(raw)
    print(f"rank icons: {len(ranks)}", flush=True)

    font["providers"] = providers
    files[FONT] = json.dumps(font, ensure_ascii=False, indent=2).encode("utf-8")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])

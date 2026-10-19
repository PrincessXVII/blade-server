#!/usr/bin/env python3
"""Adds legendary effect assets to a built BladePack.zip.

* The reconfiguring and terrain loading screens of a server switch stay black between the
  BladeOnline fades: their titles are one screen-sized black glyph. The greyed out disconnect
  button is hidden too (marker glyphs for its label skipped by the 1.21.11 text shader, black
  disabled button). The panorama and menu background stay vanilla for every other screen.
* Removes the old home-made villager staff blast parts (the staff uses the ready
  villager_wand_beam model, tipped arrow 1973-1979, now).
* Body-shaped shells with crack stages: Excalibur's gold, Frost Grip's ice (body_shells.py).
* Frost creeping in from the screen corners while frozen (the powder snow overlay).
* Infinity gauntlet stones: six copies of the gauntlet (and its blocking pose) with 0 to 5 of the
  knuckle stones lit; the thumb stone lights with the fifth. The shield item picks them by custom
  model data 1.0, 1.1 ... 1.5, which the plugin sets from the number of stored totems.

Usage: legendary_fx.py path/to/BladePack.zip   (rewrites the zip in place)
"""
import colorsys
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

from body_shells import add_body_shells

ROOT = Path(__file__).resolve().parent
OVERLAY = "overlay_1_21_4/"

GAUNTLET_MODEL = "assets/legendary/models/item/tools/infinity_gauntlet{}.json"
GAUNTLET_TEXTURE = "assets/legendary/textures/item/tools/infinity_gauntlet{}.png"
# Knuckle stones in the order totems light them: red, yellow, blue, orange, green. Thumb: purple.
KNUCKLES = [20, 22, 24, 29, 30]
THUMB = 27
STONE_LEVELS = 6
SHARED_WITH_STONES = {13}


def remove_old_blast(files):
    """The villager staff now uses the ready villager_wand_beam model; drop the earlier home-made parts."""
    for name in list(files):
        if "/blade/items/fx/" in name or "/blade/models/item/fx/" in name or "/blade/textures/item/fx/" in name:
            del files[name]


def vanilla_backgrounds(files):
    """An earlier build blacked out the panorama and menu background, which every screen without a
    world draws (settings from the title screen too). The switch screens are covered by a glyph now."""
    for i in range(6):
        files.pop(f"assets/minecraft/textures/gui/title/background/panorama_{i}.png", None)
    files.pop("assets/minecraft/textures/gui/menu_background.png", None)


SCREEN_COVER = ""
# Titles of the switch screens. Only these screens use them.
COVERED_TITLES = ("connect.reconfiguring", "connect.reconfiging", "multiplayer.downloadingTerrain")
RECONFIGURE_LABEL = ROOT / "assets/reconfigure/disconnect_label.png"
# Rows of disconnect_label.png: the vanilla glyphs of the label, recoloured to blue 254.
DISCONNECT_WORDS = {"en_us": ("Disconnect", 0xE700), "ru_ru": ("Отключиться", 0xE710)}
TEXT_OVERLAY = "overlay_text_75"
TEXT_SHADER = """#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    // Blade: the disconnect label is drawn with marker glyphs (blue 254). Greyed out, as on the
    // reconfiguring screen, it is not drawn; white, as in the pause menu, it stays.
    if (texel.a > 0.0 && texel.r > 0.996 && abs(texel.b - 254.0 / 255.0) < 0.002) {
        float grey = vertexColor.r;
        if ((grey > 0.45 && grey < 0.8) || (grey > 0.1 && grey < 0.2)) {
            discard;
        }
    }
    vec4 color = texel * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
"""


def hide_reconfigure_screen(files):
    """The reconfiguring and terrain loading screens of a server switch draw the title panorama.
    Their title becomes a black glyph 4096 GUI pixels tall around the text line, which covers the
    whole screen at any GUI scale. The disconnect button of the reconfiguring screen is drawn
    after the title: its label shares its key with the pause menu, so it is drawn with marker
    glyphs that the text shader skips while greyed out, and the greyed out button sprite is black."""
    for lang in ("en_us", "ru_ru", "uk_ua", "be_by", "kk_kz"):
        name = f"assets/minecraft/lang/{lang}.json"
        data = json.loads(files[name]) if name in files else {}
        for key in COVERED_TITLES:
            data[key] = SCREEN_COVER
        if lang in DISCONNECT_WORDS:
            word, start = DISCONNECT_WORDS[lang]
            data["menu.disconnect"] = "".join(chr(start + i) for i in range(len(word)))
        files[name] = json.dumps(data, ensure_ascii=False, indent=2).encode()

    files["assets/blade/textures/font/disconnect_label.png"] = RECONFIGURE_LABEL.read_bytes()
    width = max(len(word) for word, _ in DISCONNECT_WORDS.values())
    rows = []
    for word, start in DISCONNECT_WORDS.values():
        rows.append("".join(chr(start + i) for i in range(len(word))).ljust(width, "\0"))
    font_name = "assets/minecraft/font/default.json"
    font = json.loads(files[font_name])
    cover = io.BytesIO()
    Image.new("RGBA", (16, 16), (0, 0, 0, 255)).save(cover, "PNG")
    files["assets/blade/textures/font/screen_cover.png"] = cover.getvalue()
    ours = ("blade:font/disconnect_label.png", "blade:font/screen_cover.png")
    font["providers"] = [p for p in font["providers"] if p.get("file") not in ours]
    font["providers"].append({"type": "bitmap", "file": "blade:font/disconnect_label.png", "ascent": 7, "chars": rows})
    font["providers"].append({"type": "bitmap", "file": "blade:font/screen_cover.png", "height": 4096, "ascent": 2048,
                              "chars": [SCREEN_COVER]})
    files[font_name] = json.dumps(font, ensure_ascii=False, indent=2).encode()

    black = io.BytesIO()
    Image.new("RGBA", (200, 20), (0, 0, 0, 255)).save(black, "PNG")
    sprite = "assets/minecraft/textures/gui/sprites/widget/button_disabled.png"
    files[sprite] = black.getvalue()
    files[sprite + ".mcmeta"] = json.dumps(
        {"gui": {"scaling": {"type": "nine_slice", "width": 200, "height": 20, "border": 1}}}, indent=2).encode()

    # Core shaders change between versions, so the shader only goes to 1.21.11 (pack format 75).
    files[f"{TEXT_OVERLAY}/assets/minecraft/shaders/core/rendertype_text.fsh"] = TEXT_SHADER.encode()
    meta = json.loads(files["pack.mcmeta"])
    entries = [e for e in meta["overlays"]["entries"] if e["directory"] != TEXT_OVERLAY]
    entries.append({"formats": {"min_inclusive": 75, "max_inclusive": 75}, "directory": TEXT_OVERLAY,
                    "min_format": 75, "max_format": 75})
    meta["overlays"]["entries"] = entries
    files["pack.mcmeta"] = json.dumps(meta, indent=2).encode()


def frost_overlay(files):
    """The powder snow overlay doubles as the frozen screen (Frost Grip, freezing chakram): a frosty
    haze along the edges with ice crystals branching in from them, thickest in the corners. The
    plugin raises the freeze over a few ticks, so the frost creeps in."""
    import math
    import random
    from PIL import ImageDraw

    w, h = 640, 360
    image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = image.load()
    for y in range(h):
        for x in range(w):
            dx = (x - w / 2) / (w / 2)
            dy = (y - h / 2) / (h / 2)
            d = math.sqrt(dx * dx * 0.8 + dy * dy * 1.1)
            k = max(0.0, min(1.0, (d - 0.62) / 0.55))
            if k > 0.0:
                px[x, y] = (214, 238, 255, int(150 * k * k))
    draw = ImageDraw.Draw(image)
    rng = random.Random(2026)

    def branch(x, y, angle, length, width, depth):
        steps = max(2, int(length / 6))
        for i in range(steps):
            angle += rng.uniform(-0.18, 0.18)
            nx = x + math.cos(angle) * length / steps
            ny = y + math.sin(angle) * length / steps
            draw.line([(x, y), (nx, ny)], fill=(246, 252, 255, 235), width=width)
            if depth > 0 and rng.random() < 0.45:
                side = angle + rng.choice((-1, 1)) * math.pi / 3
                branch(nx, ny, side, length * rng.uniform(0.25, 0.45), max(1, width - 1), depth - 1)
            x, y = nx, ny

    # Crystals grow inward from the frame, more of them and longer near the corners.
    for _ in range(170):
        side = rng.randrange(4)
        t = rng.random()
        corner = 1 - min(t, 1 - t) * 2
        if rng.random() > 0.35 + corner * 0.65:
            continue
        if side == 0:
            x, y = t * w, 0
        elif side == 1:
            x, y = t * w, h - 1
        elif side == 2:
            x, y = 0, t * h
        else:
            x, y = w - 1, t * h
        angle = math.atan2(h / 2 - y, w / 2 - x) + rng.uniform(-0.5, 0.5)
        branch(x, y, angle, rng.uniform(18, 50 + 80 * corner), 2 if corner > 0.6 else 1, 3)
    # Small six-armed sparkles near the frame.
    for _ in range(90):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        dx, dy = (x - w / 2) / (w / 2), (y - h / 2) / (h / 2)
        if dx * dx + dy * dy < 0.75:
            continue
        r = rng.uniform(2, 4)
        for a in range(6):
            angle = a * math.pi / 3
            draw.line([(x, y), (x + math.cos(angle) * r, y + math.sin(angle) * r)], fill=(255, 255, 255, 220))
    out = io.BytesIO()
    image.save(out, "PNG", optimize=True)
    files["assets/minecraft/textures/misc/powder_snow_outline.png"] = out.getvalue()


def face_texels(face, scale):
    u1, v1, u2, v2 = face["uv"]
    x1, x2 = sorted((int(round(u1 * scale)), int(round(u2 * scale))))
    y1, y2 = sorted((int(round(v1 * scale)), int(round(v2 * scale))))
    return {(x, y) for x in range(x1, max(x2, x1 + 1)) for y in range(y1, max(y2, y1 + 1))}


def dim(pixel):
    r, g, b, a = pixel
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    r, g, b = colorsys.hls_to_rgb(h, l * 0.42, s * 0.25)
    return int(r * 255), int(g * 255), int(b * 255), a


def add_gauntlet_stones(files):
    model = json.loads(files[GAUNTLET_MODEL.format("")])
    blocking = json.loads(files[GAUNTLET_MODEL.format("_blocking")])
    texture = Image.open(io.BytesIO(files[GAUNTLET_TEXTURE.format("")])).convert("RGBA")
    scale = texture.width / 16.0
    owners = {}
    for index, element in enumerate(model["elements"]):
        for face in element["faces"].values():
            if face.get("texture") != "#1":
                continue
            for texel in face_texels(face, scale):
                owners.setdefault(texel, set()).add(index)

    def stone(index):
        # Texels of this stone only. Element 13 (back of the hand) repeats the knuckle stones on its
        # side, so those copies dim with them; texels shared between two stones are left alone.
        return {t for t, who in owners.items() if index in who and who - {index} <= SHARED_WITH_STONES}

    for level in range(STONE_LEVELS):
        lit = set(KNUCKLES[:level]) | ({THUMB} if level >= 5 else set())
        image = texture.copy()
        px = image.load()
        for index in KNUCKLES + [THUMB]:
            if index in lit:
                continue
            for x, y in stone(index):
                if px[x, y][3]:
                    px[x, y] = dim(px[x, y])
        out = io.BytesIO()
        image.save(out, "PNG")
        files[GAUNTLET_TEXTURE.format(f"_s{level}")] = out.getvalue()
        for base, suffix in ((model, ""), (blocking, "_blocking")):
            copy = json.loads(json.dumps(base))
            copy["textures"]["1"] = f"legendary:item/tools/infinity_gauntlet_s{level}"
            files[GAUNTLET_MODEL.format(f"{suffix}_s{level}")] = json.dumps(copy).encode()

    for prefix in ("", OVERLAY):
        name = f"{prefix}assets/minecraft/items/shield.json"
        if name not in files:
            continue
        data = json.loads(files[name])
        entries = [e for e in data["model"]["entries"] if not 1 <= e["threshold"] < 2]
        for level in range(STONE_LEVELS):
            entries.append({
                "threshold": 1 if level == 0 else round(1.05 + (level - 1) * 0.1, 2),
                "model": {
                    "type": "condition",
                    "property": "using_item",
                    "on_false": {"type": "model", "model": f"legendary:item/tools/infinity_gauntlet_s{level}"},
                    "on_true": {"type": "model", "model": f"legendary:item/tools/infinity_gauntlet_blocking_s{level}"},
                },
            })
        data["model"]["entries"] = sorted(entries, key=lambda e: e["threshold"])
        files[name] = json.dumps(data, indent=2).encode()


def main(path):
    with zipfile.ZipFile(path) as z:
        files = {info.filename: z.read(info.filename) for info in z.infolist() if not info.is_dir()}
    remove_old_blast(files)
    vanilla_backgrounds(files)
    hide_reconfigure_screen(files)
    add_gauntlet_stones(files)
    add_body_shells(files)
    frost_overlay(files)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])
    print("legendary fx: gauntlet stones x6, body shells, frost overlay, covered switch screens, vanilla menu backgrounds", flush=True)


if __name__ == "__main__":
    main(sys.argv[1])

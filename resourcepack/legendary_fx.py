#!/usr/bin/env python3
"""Adds legendary effect assets to a built BladePack.zip.

* Black panorama and menu background, so the reconfiguring screen of a server switch stays dark
  between the BladeOnline fades.
* Reconfiguring screen without its title and greyed out disconnect button (empty translation,
  marker glyphs for the button label hidden by the 1.21.11 text shader, black disabled button).
* Removes the old home-made villager staff blast parts (the staff uses the ready
  villager_wand_beam model, tipped arrow 1973-1979, now).
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


def black_transition_screens(files):
    """While on the server, the reconfiguring and terrain loading screens of a server switch show the
    title panorama and menu background. Black versions keep the screen dark between the fades."""
    black = io.BytesIO()
    Image.new("RGBA", (16, 16), (0, 0, 0, 255)).save(black, "PNG")
    for i in range(6):
        files[f"assets/minecraft/textures/gui/title/background/panorama_{i}.png"] = black.getvalue()
    files["assets/minecraft/textures/gui/menu_background.png"] = black.getvalue()


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
    """The reconfiguring screen of a server switch shows its title and a greyed out disconnect
    button over the black background. The title gets an empty translation; the button label
    shares its key with the pause menu, so it is drawn with marker glyphs that the text shader
    skips while greyed out, and the greyed out button sprite is plain black."""
    for lang in ("en_us", "ru_ru", "uk_ua", "be_by", "kk_kz"):
        name = f"assets/minecraft/lang/{lang}.json"
        data = json.loads(files[name]) if name in files else {}
        data["connect.reconfiguring"] = ""
        data["connect.reconfiging"] = ""
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
    font["providers"] = [p for p in font["providers"] if p.get("file") != "blade:font/disconnect_label.png"]
    font["providers"].append({"type": "bitmap", "file": "blade:font/disconnect_label.png", "ascent": 7, "chars": rows})
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
    black_transition_screens(files)
    hide_reconfigure_screen(files)
    add_gauntlet_stones(files)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])
    print("legendary fx: gauntlet stones x6, black transition screens, hidden reconfigure screen", flush=True)


if __name__ == "__main__":
    main(sys.argv[1])

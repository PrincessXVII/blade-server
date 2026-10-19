"""3D arrows for the legendary bows (blade:legend_arrow_<kind>), called from legendary_fx.py.

A shaft, a diamond head of two crossed plates and crossed fletching, pointing along +z, so an
item display turned by the arrow's flight direction shows it nose first. BladeWeapons hides the
vanilla arrow and flies one of these in its place.
"""
import io
import json

from PIL import Image

# kind: (shaft, head, fletching)
KINDS = {
    "artemis": ((196, 206, 228), (176, 226, 255), (245, 248, 255)),
    "toxic": ((46, 96, 44), (150, 255, 96), (96, 206, 84)),
    "flaming": ((84, 48, 34), (255, 168, 56), (236, 78, 44)),
}


def texture(colors):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for index, (r, g, b) in enumerate(colors):
        for y in range(4):
            for x in range(4):
                # A little shading across each swatch so the faces are not flat.
                k = 1.0 - 0.06 * (x + y)
                px[index * 4 + x, y] = (int(r * k), int(g * k), int(b * k), 255)
    out = io.BytesIO()
    image.save(out, "PNG")
    return out.getvalue()


def faces(uv):
    return {face: {"uv": uv, "texture": "#arrow"} for face in ("north", "south", "east", "west", "up", "down")}


def model():
    shaft, head, fletch = [0, 0, 4, 4], [4, 0, 8, 4], [8, 0, 12, 4]
    elements = [
        {"from": [7.5, 7.5, 1], "to": [8.5, 8.5, 14], "faces": faces(shaft)},
    ]
    for angle in (45, -45):
        elements.append({"from": [6.25, 7.75, 13], "to": [9.75, 8.25, 17],
                         "rotation": {"angle": angle, "axis": "z", "origin": [8, 8, 15]}, "faces": faces(head)})
        elements.append({"from": [6.0, 7.85, 0], "to": [10.0, 8.15, 4],
                         "rotation": {"angle": angle, "axis": "z", "origin": [8, 8, 2]}, "faces": faces(fletch)})
    return {"textures": {"arrow": "blade:item/arrow/legend_arrow_artemis", "particle": "blade:item/arrow/legend_arrow_artemis"},
            "elements": elements}


def add_legend_arrows(files):
    files["assets/blade/models/item/arrow/legend_arrow.json"] = json.dumps(model()).encode()
    for kind, colors in KINDS.items():
        name = f"legend_arrow_{kind}"
        files[f"assets/blade/textures/item/arrow/{name}.png"] = texture(colors)
        files[f"assets/blade/models/item/arrow/{name}.json"] = json.dumps({
            "parent": "blade:item/arrow/legend_arrow",
            "textures": {"arrow": f"blade:item/arrow/{name}", "particle": f"blade:item/arrow/{name}"},
        }).encode()
        files[f"assets/blade/items/{name}.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"blade:item/arrow/{name}"}}).encode()

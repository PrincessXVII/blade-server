"""Excalibur shield: a translucent golden shell shaped like the player, with crack stages.

Item models blade:excalibur_shell_0 .. _5 (0 = whole, 5 = about to break). BladeWeapons shows
them on an item display that follows the protected player and swaps the stage on every blocked
hit. Each texture is animated: a bright band sweeps over the gold. Called from legendary_fx.py.
"""
import io
import json
import math
import random

from PIL import Image, ImageDraw

STAGES = 6
FRAMES = 8
SIZE = 128
TEX_PER_PX = 2  # texture pixels per model pixel
NAME = "excalibur_shell"

# Boxes in player model pixels: feet at y 0, x to the right, z forward; then the inflation.
BOXES = [
    ("head", (-4, 24, -4), (4, 32, 4), 1.3),
    ("body", (-4, 12, -2), (4, 24, 2), 1.3),
    ("right_arm", (-8, 12, -2), (-4, 24, 2), 1.2),
    ("left_arm", (4, 12, -2), (8, 24, 2), 1.2),
    ("legs", (-4, 0, -2), (4, 12, 2), 1.1),
]
GOLD = (255, 198, 70)
EDGE = (255, 238, 160)


def face_sizes(lo, hi):
    w, h, d = (hi[i] - lo[i] for i in range(3))
    return {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}


def layout():
    """Shelf-packs every face into the texture. Returns {(box, face): (x, y, w, h)} in texture px."""
    faces = []
    for name, lo, hi, grow in BOXES:
        lo = [c - grow for c in lo]
        hi = [c + grow for c in hi]
        for face, (fw, fh) in face_sizes(lo, hi).items():
            faces.append(((name, face), math.ceil(fw * TEX_PER_PX), math.ceil(fh * TEX_PER_PX)))
    faces.sort(key=lambda f: -f[2])
    rects, x, y, row = {}, 0, 0, 0
    for key, w, h in faces:
        if x + w > SIZE:
            x, y, row = 0, y + row, 0
        rects[key] = (x, y, w, h)
        x += w
        row = max(row, h)
    assert y + row <= SIZE, "shell faces do not fit the texture"
    return rects


def model(rects):
    elements = []
    for name, lo, hi, grow in BOXES:
        start = [8 + lo[0] - grow, lo[1] - 8 - grow, 8 + lo[2] - grow]
        end = [8 + hi[0] + grow, hi[1] - 8 + grow, 8 + hi[2] + grow]
        faces = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            x, y, w, h = rects[(name, face)]
            scale = 16 / SIZE
            faces[face] = {"uv": [round(x * scale, 4), round(y * scale, 4), round((x + w) * scale, 4),
                                  round((y + h) * scale, 4)], "texture": "#shell"}
        elements.append({"name": name, "from": [round(v, 3) for v in start], "to": [round(v, 3) for v in end],
                         "faces": faces})
    return {"textures": {"shell": f"blade:item/shell/{NAME}_0", "particle": f"blade:item/shell/{NAME}_0"},
            "elements": elements}


def crack_plan(rects):
    """Impacts per stage, cumulative: every stage adds one on the front, one on the back and one
    somewhere else, so the damage shows from any side."""
    rng = random.Random(1987)
    others = [k for k in rects if k[1] in ("east", "west", "north", "south") and k[0] != "body"]
    plan = []
    for stage in range(1, STAGES):
        hits = [("body", "north"), ("body", "south"), rng.choice(others)]
        if stage >= 3:
            hits.append(rng.choice(others))
        for key in hits:
            x, y, w, h = rects[key]
            cx = x + rng.uniform(0.25, 0.75) * w
            cy = y + rng.uniform(0.2, 0.8) * h
            plan.append((stage, key, cx, cy, rng.random()))
    return plan


def draw_crack(draw, rect, cx, cy, seed, stage):
    rng = random.Random(seed * 1000 + stage)
    x0, y0, w, h = rect
    reach = max(w, h) * rng.uniform(0.45, 0.75)
    branches = rng.randint(5, 7)
    lines = []
    for b in range(branches):
        angle = (b + rng.uniform(-0.3, 0.3)) * 2 * math.pi / branches
        px, py = cx, cy
        length = reach * rng.uniform(0.55, 1.0)
        steps = rng.randint(3, 5)
        for s in range(steps):
            angle += rng.uniform(-0.5, 0.5)
            nx = px + math.cos(angle) * length / steps
            ny = py + math.sin(angle) * length / steps
            lines.append(((px, py), (nx, ny)))
            if s == 1 and rng.random() < 0.6:
                side = angle + rng.choice((-1, 1)) * rng.uniform(0.6, 1.1)
                lines.append(((nx, ny), (nx + math.cos(side) * length * 0.3, ny + math.sin(side) * length * 0.3)))
            px, py = nx, ny
    # A broken ring around the impact.
    ring = reach * 0.28
    for i in range(10):
        if rng.random() < 0.35:
            continue
        a1, a2 = i * 2 * math.pi / 10, (i + 1) * 2 * math.pi / 10
        lines.append(((cx + math.cos(a1) * ring, cy + math.sin(a1) * ring),
                      (cx + math.cos(a2) * ring, cy + math.sin(a2) * ring)))
    box = (x0, y0, x0 + w - 1, y0 + h - 1)

    def clip(p):
        return (min(max(p[0], box[0]), box[2]), min(max(p[1], box[1]), box[3]))

    for a, b in lines:
        draw.line([clip(a), clip(b)], fill=(150, 90, 10, 210), width=3)
    for a, b in lines:
        draw.line([clip(a), clip(b)], fill=(255, 252, 226, 245), width=1)


def frame(rects, stage, index, plan):
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = image.load()
    dim = 1.0 - 0.06 * stage
    band = index / FRAMES
    for (name, face), (x0, y0, w, h) in rects.items():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                u = (x - x0 + 0.5) / w
                v = (y - y0 + 0.5) / h
                edge = min(u, v, 1 - u, 1 - v) * min(w, h)
                if edge < 1.5:
                    r, g, b = EDGE
                    a = 165
                else:
                    r, g, b = GOLD
                    a = 88 + int(34 * (1 - min(1.0, edge / 8)))
                # The sweeping shine: a diagonal band across every face.
                d = ((u + v) / 2 - band) % 1.0
                d = min(d, 1 - d)
                if d < 0.12:
                    k = 1 - d / 0.12
                    r = min(255, int(r + 40 * k))
                    g = min(255, int(g + 50 * k))
                    b = min(255, int(b + 80 * k))
                    a = min(255, int(a + 70 * k))
                # Later stages lean from gold towards orange.
                px[x, y] = (r, int(g * dim), int(b * dim), a)
    draw = ImageDraw.Draw(image)
    for s, key, cx, cy, seed in plan:
        if s <= stage:
            draw_crack(draw, rects[key], cx, cy, seed, s)
    return image


def add_excalibur_shell(files):
    rects = layout()
    plan = crack_plan(rects)
    base = model(rects)
    files[f"assets/blade/models/item/shell/{NAME}.json"] = json.dumps(base).encode()
    for stage in range(STAGES):
        strip = Image.new("RGBA", (SIZE, SIZE * FRAMES))
        for index in range(FRAMES):
            strip.paste(frame(rects, stage, index, plan), (0, SIZE * index))
        out = io.BytesIO()
        strip.save(out, "PNG", optimize=True)
        texture = f"assets/blade/textures/item/shell/{NAME}_{stage}.png"
        files[texture] = out.getvalue()
        files[texture + ".mcmeta"] = json.dumps({"animation": {"frametime": 2, "interpolate": True}}).encode()
        files[f"assets/blade/models/item/shell/{NAME}_{stage}.json"] = json.dumps({
            "parent": f"blade:item/shell/{NAME}",
            "textures": {"shell": f"blade:item/shell/{NAME}_{stage}", "particle": f"blade:item/shell/{NAME}_{stage}"},
        }).encode()
        files[f"assets/blade/items/{NAME}_{stage}.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"blade:item/shell/{NAME}_{stage}"}}).encode()

"""Shells shaped like the player's body, with crack stages. Called from legendary_fx.py.

* excalibur_shell_0 .. _5: the Excalibur shield, translucent gold. BladeWeapons swaps the stage
  on every blocked hit.
* ice_statue_0 .. _3: Frost Grip's ice around a frozen player, cracking as the freeze runs out.

Each stage is an item model (blade:<name>_<stage>) over one shared box model. BladeWeapons shows
the shell in parts, one item display each (blade:<name>_<part>_<stage>), so the head can turn
with the player's head, the right arm can swing and the body can crouch. A part model has its
pivot (neck, shoulder, hip) at the model centre and is turned half a circle, because an item
display draws its model turned by 180 degrees. Every texture is animated: a bright band sweeps
over it.
"""
import io
import json
import math
import random

from PIL import Image, ImageDraw

FRAMES = 8
SIZE = 128
TEX_PER_PX = 2  # texture pixels per model pixel

# Boxes in player model pixels: feet at y 0, x to the right, z forward; then the inflation.
BOXES = [
    ("head", (-4, 24, -4), (4, 32, 4), 1.3),
    ("body", (-4, 12, -2), (4, 24, 2), 1.3),
    ("right_arm", (-8, 12, -2), (-4, 24, 2), 1.2),
    ("left_arm", (4, 12, -2), (8, 24, 2), 1.2),
    ("legs", (-4, 0, -2), (4, 12, 2), 1.1),
]

# Where each part turns, in the same pixels as BOXES (the player model's joints).
PIVOTS = {
    "head": (0, 24, 0),
    "body": (0, 24, 0),
    "right_arm": (-5, 22, 0),
    "left_arm": (5, 22, 0),
    "legs": (0, 12, 0),
}

LOOKS = {
    "excalibur_shell": {
        "stages": 6, "base": (255, 198, 70), "edge": (255, 238, 160), "alpha": 88, "rim": 34,
        "edge_alpha": 165, "shine": 1.0, "warm": 0.06, "bubbles": 0, "seed": 1987, "frametime": 2,
        "crack": (255, 252, 226, 245), "crack_edge": (150, 90, 10, 210),
    },
    "ice_statue": {
        "stages": 4, "base": (168, 214, 250), "edge": (236, 249, 255), "alpha": 125, "rim": 40,
        "edge_alpha": 210, "shine": 0.55, "warm": 0.0, "bubbles": 70, "seed": 7, "frametime": 3,
        "crack": (255, 255, 255, 250), "crack_edge": (80, 130, 185, 200),
    },
}


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


def model(rects, name):
    elements = []
    for box, lo, hi, grow in BOXES:
        start = [8 + lo[0] - grow, lo[1] - 8 - grow, 8 + lo[2] - grow]
        end = [8 + hi[0] + grow, hi[1] - 8 + grow, 8 + hi[2] + grow]
        faces = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            x, y, w, h = rects[(box, face)]
            scale = 16 / SIZE
            faces[face] = {"uv": [round(x * scale, 4), round(y * scale, 4), round((x + w) * scale, 4),
                                  round((y + h) * scale, 4)], "texture": "#shell"}
        elements.append({"name": box, "from": [round(v, 3) for v in start], "to": [round(v, 3) for v in end],
                         "faces": faces})
    return {"textures": {"shell": f"blade:item/shell/{name}_0", "particle": f"blade:item/shell/{name}_0"},
            "elements": elements}


def part_model(rects, name, part):
    """One part around its pivot, mirrored in x and z for the display's half turn."""
    box = next(b for b in BOXES if b[0] == part)
    _, lo, hi, grow = box
    px, py, pz = PIVOTS[part]
    lo = [lo[0] - grow - px, lo[1] - grow - py, lo[2] - grow - pz]
    hi = [hi[0] + grow - px, hi[1] + grow - py, hi[2] + grow - pz]
    start = [8 - hi[0], 8 + lo[1], 8 - hi[2]]
    end = [8 - lo[0], 8 + hi[1], 8 - lo[2]]
    faces = {}
    scale = 16 / SIZE
    for face in ("north", "south", "east", "west", "up", "down"):
        x, y, w, h = rects[(part, face)]
        faces[face] = {"uv": [round(x * scale, 4), round(y * scale, 4), round((x + w) * scale, 4),
                              round((y + h) * scale, 4)], "texture": "#shell"}
    return {"textures": {"shell": f"blade:item/shell/{name}_0", "particle": f"blade:item/shell/{name}_0"},
            "elements": [{"name": part, "from": [round(v, 3) for v in start], "to": [round(v, 3) for v in end],
                          "faces": faces}]}


def crack_plan(rects, look):
    """Impacts per stage, cumulative: every stage adds one on the front, one on the back and one
    somewhere else, so the damage shows from any side."""
    rng = random.Random(look["seed"])
    others = [k for k in rects if k[1] in ("east", "west", "north", "south") and k[0] != "body"]
    plan = []
    for stage in range(1, look["stages"]):
        hits = [("body", "north"), ("body", "south"), rng.choice(others)]
        if stage >= 3:
            hits.append(rng.choice(others))
        for key in hits:
            x, y, w, h = rects[key]
            cx = x + rng.uniform(0.25, 0.75) * w
            cy = y + rng.uniform(0.2, 0.8) * h
            plan.append((stage, key, cx, cy, rng.random()))
    return plan


def draw_crack(draw, rect, cx, cy, seed, stage, look):
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
        draw.line([clip(a), clip(b)], fill=look["crack_edge"], width=3)
    for a, b in lines:
        draw.line([clip(a), clip(b)], fill=look["crack"], width=1)


def frame(rects, stage, index, plan, look, bubbles):
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = image.load()
    dim = 1.0 - look["warm"] * stage
    band = index / FRAMES
    for (name, face), (x0, y0, w, h) in rects.items():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                u = (x - x0 + 0.5) / w
                v = (y - y0 + 0.5) / h
                edge = min(u, v, 1 - u, 1 - v) * min(w, h)
                if edge < 1.5:
                    r, g, b = look["edge"]
                    a = look["edge_alpha"]
                else:
                    r, g, b = look["base"]
                    a = look["alpha"] + int(look["rim"] * (1 - min(1.0, edge / 8)))
                if (x, y) in bubbles:
                    r, g, b = look["edge"]
                    a = min(255, a + 60)
                # The sweeping shine: a diagonal band across every face.
                d = ((u + v) / 2 - band) % 1.0
                d = min(d, 1 - d)
                if d < 0.12:
                    k = (1 - d / 0.12) * look["shine"]
                    r = min(255, int(r + 40 * k))
                    g = min(255, int(g + 50 * k))
                    b = min(255, int(b + 80 * k))
                    a = min(255, int(a + 70 * k))
                # Later stages of the gold lean towards orange.
                px[x, y] = (r, int(g * dim), int(b * dim), a)
    draw = ImageDraw.Draw(image)
    for s, key, cx, cy, seed in plan:
        if s <= stage:
            draw_crack(draw, rects[key], cx, cy, seed, s, look)
    return image


def add_shell(files, name, look):
    rects = layout()
    plan = crack_plan(rects, look)
    rng = random.Random(look["seed"] + 1)
    bubbles = {(rng.randrange(SIZE), rng.randrange(SIZE)) for _ in range(look["bubbles"] * 10)}
    files[f"assets/blade/models/item/shell/{name}.json"] = json.dumps(model(rects, name)).encode()
    for part in PIVOTS:
        files[f"assets/blade/models/item/shell/{name}_{part}.json"] = json.dumps(part_model(rects, name, part)).encode()
    for stage in range(look["stages"]):
        strip = Image.new("RGBA", (SIZE, SIZE * FRAMES))
        for index in range(FRAMES):
            strip.paste(frame(rects, stage, index, plan, look, bubbles), (0, SIZE * index))
        out = io.BytesIO()
        strip.save(out, "PNG", optimize=True)
        texture = f"assets/blade/textures/item/shell/{name}_{stage}.png"
        files[texture] = out.getvalue()
        files[texture + ".mcmeta"] = json.dumps(
            {"animation": {"frametime": look["frametime"], "interpolate": True}}).encode()
        files[f"assets/blade/models/item/shell/{name}_{stage}.json"] = json.dumps({
            "parent": f"blade:item/shell/{name}",
            "textures": {"shell": f"blade:item/shell/{name}_{stage}", "particle": f"blade:item/shell/{name}_{stage}"},
        }).encode()
        files[f"assets/blade/items/{name}_{stage}.json"] = json.dumps(
            {"model": {"type": "minecraft:model", "model": f"blade:item/shell/{name}_{stage}"}}).encode()
        for part in PIVOTS:
            files[f"assets/blade/models/item/shell/{name}_{part}_{stage}.json"] = json.dumps({
                "parent": f"blade:item/shell/{name}_{part}",
                "textures": {"shell": f"blade:item/shell/{name}_{stage}", "particle": f"blade:item/shell/{name}_{stage}"},
            }).encode()
            files[f"assets/blade/items/{name}_{part}_{stage}.json"] = json.dumps(
                {"model": {"type": "minecraft:model", "model": f"blade:item/shell/{name}_{part}_{stage}"}}).encode()


def add_body_shells(files):
    for name, look in LOOKS.items():
        add_shell(files, name, look)

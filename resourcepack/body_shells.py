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
    add_armor_shell(files)
    add_aura_shells(files)


# Excalibur's shield worn as armour: the client draws it on the player model itself, so it moves
# with every limb and never lags. Armour cannot be half transparent, so the gold is a net of
# diamonds with a sparkle in each cell and the body shows through, and every stage cracks it more
# and knocks pieces out. BladeWeapons sends it as fake equipment (blade:excalibur_shell_<stage>).
ARMOR_STAGES = 6


def armor_shell_texture(stage):
    rng = random.Random(4049)
    image = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = image.load()
    for y in range(32):
        for x in range(64):
            # A net of gold diamonds with a sparkle in each cell: the body shows through.
            if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                shade = 0.86 + 0.14 * ((y % 16) / 15.0)
                px[x, y] = (255, int(214 * shade), int(96 * shade), 255)
            elif (x + y) % 8 == 4 and (x - y) % 8 == 4:
                px[x, y] = (255, 244, 196, 255)
    draw = ImageDraw.Draw(image)
    cracks = []
    for _ in range(ARMOR_STAGES * 3):
        x, y = rng.uniform(2, 62), rng.uniform(2, 30)
        points = [(x, y)]
        angle = rng.uniform(0, 2 * math.pi)
        for _ in range(rng.randint(3, 5)):
            angle += rng.uniform(-0.8, 0.8)
            x += math.cos(angle) * rng.uniform(2, 4)
            y += math.sin(angle) * rng.uniform(2, 4)
            points.append((x, y))
        cracks.append(points)
    shown = cracks[:stage * 3]
    # Later stages lose chunks of glass around the oldest cracks.
    for points in shown[:max(0, stage - 2) * 3]:
        cx, cy = points[len(points) // 2]
        r = rng.uniform(1.6, 2.8)
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if 0 <= x < 64 and 0 <= y < 32 and math.hypot(x - cx, y - cy) <= r:
                    px[x, y] = (0, 0, 0, 0)
    for points in shown:
        draw.line(points, fill=(150, 88, 10, 255), width=2)
        draw.line(points, fill=(255, 252, 226, 255), width=1)
    return image


def add_armor_shell(files):
    for stage in range(ARMOR_STAGES):
        out = io.BytesIO()
        armor_shell_texture(stage).save(out, "PNG", optimize=True)
        name = f"excalibur_shell_{stage}"
        for layer in ("humanoid", "humanoid_leggings"):
            files[f"assets/blade/textures/entity/equipment/{layer}/{name}.png"] = out.getvalue()
        files[f"assets/blade/equipment/{name}.json"] = json.dumps({"layers": {
            "humanoid": [{"texture": f"blade:{name}"}],
            "humanoid_leggings": [{"texture": f"blade:{name}"}],
        }}).encode()


# Shapes that never depend on how a client turns the player's body: Excalibur's golden bubble
# (a disc that always faces the viewer, so it reads as a sphere) and Frost Grip's ice block,
# square to the world. blade:shield_bubble_<stage>, blade:ice_block_<stage>.
BUBBLE_STAGES = 6
ICE_BLOCK_STAGES = 4


def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def crack_lines(rng, count, cx_range, cy_range, reach):
    lines = []
    for _ in range(count):
        cx, cy = rng.uniform(*cx_range), rng.uniform(*cy_range)
        for b in range(rng.randint(4, 6)):
            angle = b * 2 * math.pi / 5 + rng.uniform(-0.4, 0.4)
            px, py = cx, cy
            for _ in range(rng.randint(2, 4)):
                angle += rng.uniform(-0.5, 0.5)
                step = reach * rng.uniform(0.2, 0.35)
                nx, ny = px + math.cos(angle) * step, py + math.sin(angle) * step
                lines.append(((px, py), (nx, ny)))
                px, py = nx, ny
    return lines


def bubble_frame(stage, index, plan):
    size = 128
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = image.load()
    band = index / FRAMES
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size * 2 - 1
            v = (y + 0.5) / size * 2 - 1
            r = math.hypot(u, v)
            if r > 0.99:
                continue
            edge = 1.0 - smooth(0.955, 0.99, r)
            rim = smooth(0.62, 0.96, r)
            # A faint honeycomb inside, like a force field.
            hx = u * 9.0
            hy = v * 9.0 / 0.866
            cell = abs(((hx + (math.floor(hy) % 2) * 0.5) % 1.0) - 0.5) + abs((hy % 1.0) - 0.5)
            comb = 1.0 if cell > 0.62 else 0.0
            d = (((u + v) / 4 + 0.5) - band) % 1.0
            d = min(d, 1 - d)
            shine = max(0.0, 1.0 - d / 0.08)
            a = (22 + 175 * rim + 28 * comb + 70 * shine * (0.3 + rim)) * edge
            light = 0.35 * rim + 0.4 * shine
            red = 255
            green = int(196 + (244 - 196) * light)
            blue = int(80 + (190 - 80) * light)
            px[x, y] = (red, min(255, green), min(255, blue), int(min(255, a)))
    draw = ImageDraw.Draw(image)
    for s, (a, b) in plan:
        if s <= stage:
            pa = ((a[0] + 1) / 2 * size, (a[1] + 1) / 2 * size)
            pb = ((b[0] + 1) / 2 * size, (b[1] + 1) / 2 * size)
            draw.line([pa, pb], fill=(150, 90, 10, 230), width=3)
            draw.line([pa, pb], fill=(255, 252, 226, 250), width=1)
    # Nothing outside the disc, cracks included.
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size * 2 - 1
            v = (y + 0.5) / size * 2 - 1
            if math.hypot(u, v) > 0.985:
                px[x, y] = (0, 0, 0, 0)
    return image


def ice_frame(stage, index, plan, bubbles):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = image.load()
    band = index / FRAMES
    # Sides take the left 32x64, top and bottom the right 32x32.
    regions = [(0, 0, 32, 64), (32, 0, 32, 32), (32, 32, 32, 32)]
    for x0, y0, w, h in regions:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                u = (x - x0 + 0.5) / w
                v = (y - y0 + 0.5) / h
                edge = min(u, v, 1 - u, 1 - v) * min(w, h)
                frost = max(0.0, 1.0 - edge / 3.5)
                d = ((u * 0.6 + v * 0.4) - band) % 1.0
                d = min(d, 1 - d)
                shine = max(0.0, 1.0 - d / 0.1)
                streak = 0.5 + 0.5 * math.sin(u * 19.0 + v * 3.0)
                a = 105 + 110 * frost + 25 * streak + 55 * shine
                r = int(176 + 70 * frost + 40 * shine)
                g = int(218 + 30 * frost + 25 * shine)
                b = 252
                if (x, y) in bubbles:
                    r, g, a = 236, 248, a + 70
                px[x, y] = (min(255, r), min(255, g), b, int(min(240, a)))
    draw = ImageDraw.Draw(image)
    for s, (a, b) in plan:
        if s <= stage:
            draw.line([a, b], fill=(70, 120, 175, 220), width=2)
            draw.line([a, b], fill=(255, 255, 255, 250), width=1)
    return image


def add_aura_shells(files):
    rng = random.Random(77)
    plan = []
    for stage in range(1, BUBBLE_STAGES):
        for line in crack_lines(rng, 1, (-0.6, 0.6), (-0.6, 0.6), 0.9):
            plan.append((stage, line))
    elements = [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False,
                 "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#bubble"} for f in ("north", "south")}}]
    files["assets/blade/models/item/shell/shield_bubble.json"] = json.dumps(
        {"textures": {"bubble": "blade:item/shell/shield_bubble_0", "particle": "blade:item/shell/shield_bubble_0"},
         "elements": elements}).encode()
    for stage in range(BUBBLE_STAGES):
        strip = Image.new("RGBA", (128, 128 * FRAMES))
        for index in range(FRAMES):
            strip.paste(bubble_frame(stage, index, plan), (0, 128 * index))
        add_stage(files, "shield_bubble", stage, strip, "bubble")

    rng = random.Random(23)
    plan = []
    for stage in range(1, ICE_BLOCK_STAGES):
        for line in crack_lines(rng, 2, (4, 28), (6, 58), 16):
            plan.append((stage, line))
    bubbles = {(rng.randrange(64), rng.randrange(64)) for _ in range(120)}
    sides = {"uv": [0, 0, 8, 16], "texture": "#ice"}
    ends = {"uv": [8, 0, 16, 8], "texture": "#ice"}
    faces = {f: dict(sides) for f in ("north", "south", "east", "west")}
    faces["up"] = dict(ends)
    faces["down"] = {"uv": [8, 8, 16, 16], "texture": "#ice"}
    files["assets/blade/models/item/shell/ice_block.json"] = json.dumps(
        {"textures": {"ice": "blade:item/shell/ice_block_0", "particle": "blade:item/shell/ice_block_0"},
         "elements": [{"from": [0, -8.5, 0], "to": [16, 24.5, 16], "faces": faces}]}).encode()
    for stage in range(ICE_BLOCK_STAGES):
        strip = Image.new("RGBA", (64, 64 * FRAMES))
        for index in range(FRAMES):
            strip.paste(ice_frame(stage, index, plan, bubbles), (0, 64 * index))
        add_stage(files, "ice_block", stage, strip, "ice")


def add_stage(files, name, stage, strip, key):
    out = io.BytesIO()
    strip.save(out, "PNG", optimize=True)
    texture = f"assets/blade/textures/item/shell/{name}_{stage}.png"
    files[texture] = out.getvalue()
    files[texture + ".mcmeta"] = json.dumps({"animation": {"frametime": 2, "interpolate": True}}).encode()
    files[f"assets/blade/models/item/shell/{name}_{stage}.json"] = json.dumps({
        "parent": f"blade:item/shell/{name}",
        "textures": {key: f"blade:item/shell/{name}_{stage}", "particle": f"blade:item/shell/{name}_{stage}"},
    }).encode()
    files[f"assets/blade/items/{name}_{stage}.json"] = json.dumps(
        {"model": {"type": "minecraft:model", "model": f"blade:item/shell/{name}_{stage}"}}).encode()

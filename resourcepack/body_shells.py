"""Shells around a player, called from legendary_fx.py.

* Excalibur's golden shell is worn as armour, so the client draws it on the player model itself:
  it moves with every limb and never lags. BladeWeapons (fx/GlassArmor) sends the other players a
  copy of the real helmet, chestplate and boots whose equipment asset is blade:excalibur_<base>_<stage>:
  the vanilla layers of that armour with a layer of golden glass on top, so the armour still shows
  under the gold. Armour cannot blend, so the glass texels carry alpha 0.25..0.75, which vanilla
  armour never uses, and the entity shader draws them as a fine screen door and makes them glow.
  A cosmetic hat stays on: the head glass then comes as the item blade:excalibur_head, which draws
  the hat (custom model data string 0) inside a glass cube (string 1 is the crack stage).
* Frost Grip's ice block, square to the world (blade:ice_block_<stage>).
"""
import io
import json
import math
import random

from PIL import Image, ImageDraw

FRAMES = 8
GLASS_STAGES = 6
ICE_BLOCK_STAGES = 4
TEXT_OVERLAY = "overlay_text_75"

# Outputs of earlier builds: body-shaped display shells, the gold net armour and the bubble.
STALE = (
    "assets/blade/models/item/shell/",
    "assets/blade/textures/item/shell/",
    "assets/blade/items/excalibur_shell",
    "assets/blade/items/ice_statue",
    "assets/blade/items/shield_bubble",
    "assets/blade/equipment/excalibur",
    "assets/blade/textures/entity/equipment/humanoid/excalibur",
    "assets/blade/textures/entity/equipment/humanoid_leggings/excalibur",
)

# Vanilla 1.21.11 equipment layers (assets/minecraft/equipment) the glass goes over.
LEATHER = [{"texture": "minecraft:leather", "dyeable": {"color_when_undyed": -6265536}},
           {"texture": "minecraft:leather_overlay"}]
BASES = {
    "none": {},
    "leather": {"humanoid": LEATHER, "humanoid_leggings": LEATHER},
    "turtle_scute": {"humanoid": [{"texture": "minecraft:turtle_scute"}]},
    "elytra": {"wings": [{"texture": "minecraft:elytra", "use_player_texture": True}]},
}
for _metal in ("chainmail", "iron", "gold", "diamond", "netherite", "copper"):
    BASES[_metal] = {"humanoid": [{"texture": f"minecraft:{_metal}"}],
                     "humanoid_leggings": [{"texture": f"minecraft:{_metal}"}]}

# Glass alpha in the shader band: coverage = (alpha - 0.25) * 2.
def band(coverage):
    return int(round((0.25 + coverage / 2.0) * 255))


GLASS = band(0.42)
RIM = band(0.78)
SPARK = band(0.9)
CRACK = band(0.97)
CRACK_EDGE = band(0.85)

TEX = 2  # texture pixels per model pixel; the layer is 64x32 model pixels
# Boxes of the armour model in its 64x32 texture: (u, v, width, height, depth). The hat is left
# empty so the head is not glazed twice.
BOXES = {
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "arm": (40, 16, 4, 12, 4),
    "leg": (0, 16, 4, 12, 4),
}
ENTITY_SHADER = """#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

// Blade: Excalibur's golden glass is armour whose texels have alpha 0.25..0.75, which vanilla
// armour never uses. Armour cannot blend, so those texels keep a share of the screen pixels in a
// fixed 4x4 pattern (more alpha, more pixels kept), which reads as see-through glass, and glow.
const float BLADE_BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0,
                                        3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    bool bladeGlass = false;
#if defined(ALPHA_CUTOUT) && defined(NO_OVERLAY) && defined(PER_FACE_LIGHTING)
    if (color.a > 0.25 && color.a < 0.75) {
        int cell = int(mod(gl_FragCoord.x, 4.0)) + int(mod(gl_FragCoord.y, 4.0)) * 4;
        if ((color.a - 0.25) * 2.0 <= (BLADE_BAYER[cell] + 0.5) / 16.0) {
            discard;
        }
        bladeGlass = true;
        color.a = 1.0;
    }
#endif
#ifdef PER_FACE_LIGHTING
    color *= (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack) * ColorModulator;
#else
    color *= vertexColor * ColorModulator;
#endif
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    if (!bladeGlass) {
        color *= lightMapColor;
    }
#endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
"""


def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def box_faces(u, v, w, h, d):
    """Face rectangles of a box in the standard box unwrap, in model pixels."""
    return [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h),
            (u + d + w, v + d, d, h), (u + d + w + d, v + d, w, h)]


def glass_faces():
    faces = []
    for name, (u, v, w, h, d) in BOXES.items():
        for x, y, fw, fh in box_faces(u, v, w, h, d):
            faces.append((name, (x * TEX, y * TEX, fw * TEX, fh * TEX)))
    return faces


def crack_plan():
    """Cumulative impacts: each stage adds one on the chest, one on the back and one elsewhere."""
    rng = random.Random(1987)
    faces = glass_faces()
    chest = [f for f in faces if f[0] == "body" and f[1][2] == 8 * TEX and f[1][3] == 12 * TEX]
    others = [f for f in faces if f[1][3] > 4 * TEX]
    plan = []
    for stage in range(1, GLASS_STAGES):
        for _, rect in (chest[0], chest[1], rng.choice(others)):
            x, y, w, h = rect
            plan.append((stage, rect, x + rng.uniform(0.25, 0.75) * w, y + rng.uniform(0.2, 0.8) * h,
                         rng.random()))
    return plan


def crack_lines_at(rect, cx, cy, seed):
    rng = random.Random(int(seed * 1e6))
    w, h = rect[2], rect[3]
    reach = max(w, h) * rng.uniform(0.4, 0.65)
    lines = []
    for b in range(rng.randint(4, 6)):
        angle = (b + rng.uniform(-0.3, 0.3)) * 2 * math.pi / 5
        px, py = cx, cy
        for _ in range(rng.randint(2, 4)):
            angle += rng.uniform(-0.5, 0.5)
            nx, ny = px + math.cos(angle) * reach / 3, py + math.sin(angle) * reach / 3
            lines.append(((px, py), (nx, ny)))
            px, py = nx, ny
    return lines


def glass_texture(stage, plan):
    """The golden glass of one crack stage over the whole humanoid layer, 128x64."""
    image = Image.new("RGBA", (64 * TEX, 32 * TEX), (0, 0, 0, 0))
    px = image.load()
    rng = random.Random(4049)
    for _, (x0, y0, w, h) in glass_faces():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                fy = (y - y0 + 0.5) / h
                edge = min(x - x0, y - y0, x0 + w - 1 - x, y0 + h - 1 - y)
                light = 0.55 - 0.45 * fy
                color = (255, int(186 + 50 * light), int(52 + 100 * light), GLASS)
                if edge == 0:
                    color = (255, 236, 168, RIM)
                elif rng.random() < 0.035:
                    color = (255, 248, 214, SPARK)
                px[x, y] = color
    draw = ImageDraw.Draw(image)
    for s, rect, cx, cy, seed in plan:
        if s > stage:
            continue
        x0, y0, w, h = rect
        # Late stages lose a chip of glass at their oldest impacts.
        if stage - s >= 2:
            r = 1.6 + (stage - s) * 0.4
            for y in range(int(cy - r), int(cy + r) + 1):
                for x in range(int(cx - r), int(cx + r) + 1):
                    if x0 <= x < x0 + w and y0 <= y < y0 + h and math.hypot(x - cx, y - cy) <= r:
                        px[x, y] = (0, 0, 0, 0)
        box = (x0, y0, x0 + w - 1, y0 + h - 1)
        for a, b in crack_lines_at(rect, cx, cy, seed):
            a = (min(max(a[0], box[0]), box[2]), min(max(a[1], box[1]), box[3]))
            b = (min(max(b[0], box[0]), box[2]), min(max(b[1], box[1]), box[3]))
            draw.line([a, b], fill=(196, 120, 16, CRACK_EDGE), width=2)
            draw.line([a, b], fill=(255, 252, 230, CRACK), width=1)
    return image


def head_texture(stage, plan_seed):
    """The glass cube around a hat, 32x32, truly translucent (items blend)."""
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = image.load()
    rng = random.Random(77)
    for y in range(32):
        for x in range(32):
            edge = min(x, y, 31 - x, 31 - y)
            light = 0.55 - 0.45 * (y + 0.5) / 32
            color = (255, int(186 + 50 * light), int(52 + 100 * light), 104)
            if edge == 0:
                color = (255, 236, 168, 190)
            elif rng.random() < 0.03:
                color = (255, 248, 214, 210)
            px[x, y] = color
    draw = ImageDraw.Draw(image)
    crack = random.Random(plan_seed)
    for s in range(1, stage + 1):
        cx, cy = crack.uniform(8, 24), crack.uniform(8, 24)
        for a, b in crack_lines_at((0, 0, 32, 32), cx, cy, crack.random()):
            a = (min(max(a[0], 0), 31), min(max(a[1], 0), 31))
            b = (min(max(b[0], 0), 31), min(max(b[1], 0), 31))
            draw.line([a, b], fill=(196, 120, 16, 220), width=2)
            draw.line([a, b], fill=(255, 252, 230, 245), width=1)
    return image


def png(image):
    out = io.BytesIO()
    image.save(out, "PNG", optimize=True)
    return out.getvalue()


def hat_models(files):
    """Every hat item model of the pack ({ns}:hat/...), as the overlay for 1.21.4+ has it."""
    hats = {}
    for name in sorted(files):
        for prefix in ("assets/", "overlay_1_21_4/assets/"):
            if not name.startswith(prefix) or "/items/hat/" not in name or not name.endswith(".json"):
                continue
            ns, _, path = name[len(prefix):].partition("/items/")
            try:
                model = json.loads(files[name])["model"]
            except (ValueError, KeyError):
                continue
            key = f"{ns}:{path[:-5]}"
            if prefix.startswith("overlay") or key not in hats:
                hats[key] = model
    return hats


def add_glass_armor(files):
    plan = crack_plan()
    for stage in range(GLASS_STAGES):
        files[f"assets/blade/textures/entity/equipment/humanoid/excalibur_glass_{stage}.png"] = png(
            glass_texture(stage, plan))
        glass = {"texture": f"blade:excalibur_glass_{stage}"}
        for base, layers in BASES.items():
            asset = {key: [dict(layer) for layer in value] for key, value in layers.items()}
            asset["humanoid"] = asset.get("humanoid", []) + [glass]
            files[f"assets/blade/equipment/excalibur_{base}_{stage}.json"] = json.dumps({"layers": asset}).encode()

        files[f"assets/blade/textures/item/shell/excalibur_head_{stage}.png"] = png(head_texture(stage, 31))
        faces = {f: {"uv": [0, 0, 16, 16], "texture": "#glass"} for f in ("north", "south", "east", "west", "up", "down")}
        files[f"assets/blade/models/item/shell/excalibur_head_{stage}.json"] = json.dumps({
            "textures": {"glass": f"blade:item/shell/excalibur_head_{stage}",
                         "particle": f"blade:item/shell/excalibur_head_{stage}"},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "light_emission": 15,
                          "faces": faces}],
        }).encode()

    hats = [{"when": key, "model": model} for key, model in hat_models(files).items()]
    stages = [{"when": str(stage), "model": {"type": "minecraft:model", "model": f"blade:item/shell/excalibur_head_{stage}"}}
              for stage in range(GLASS_STAGES)]
    files["assets/blade/items/excalibur_head.json"] = json.dumps({"model": {
        "type": "minecraft:composite",
        "models": [
            {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
             "cases": hats, "fallback": {"type": "minecraft:empty"}},
            {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 1,
             "cases": stages, "fallback": stages[0]["model"]},
        ],
    }}).encode()
    files[f"{TEXT_OVERLAY}/assets/minecraft/shaders/core/entity.fsh"] = ENTITY_SHADER.encode()


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


def ice_frame(stage, index, plan, bubbles):
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = image.load()
    band_at = index / FRAMES
    # Sides take the left 32x64, top and bottom the right 32x32.
    regions = [(0, 0, 32, 64), (32, 0, 32, 32), (32, 32, 32, 32)]
    for x0, y0, w, h in regions:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                u = (x - x0 + 0.5) / w
                v = (y - y0 + 0.5) / h
                edge = min(u, v, 1 - u, 1 - v) * min(w, h)
                frost = max(0.0, 1.0 - edge / 3.5)
                d = ((u * 0.6 + v * 0.4) - band_at) % 1.0
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


def add_ice_block(files):
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
    texture = f"assets/blade/textures/item/shell/{name}_{stage}.png"
    files[texture] = png(strip)
    files[texture + ".mcmeta"] = json.dumps({"animation": {"frametime": 2, "interpolate": True}}).encode()
    files[f"assets/blade/models/item/shell/{name}_{stage}.json"] = json.dumps({
        "parent": f"blade:item/shell/{name}",
        "textures": {key: f"blade:item/shell/{name}_{stage}", "particle": f"blade:item/shell/{name}_{stage}"},
    }).encode()
    files[f"assets/blade/items/{name}_{stage}.json"] = json.dumps(
        {"model": {"type": "minecraft:model", "model": f"blade:item/shell/{name}_{stage}"}}).encode()


def add_body_shells(files):
    for name in [n for n in files if n.startswith(STALE)]:
        del files[name]
    add_glass_armor(files)
    add_ice_block(files)

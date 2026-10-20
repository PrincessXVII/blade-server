"""Smooth light effects as item models, called from legendary_fx.py.

Every effect is a flat plane (or a small cube for smoke) with a soft, anti-aliased texture, so
it has no stepped edges. Every face takes its colour from the custom_model_data colours of the
item (white textures, tinted), so BladeWeapons can shift the colour every tick, for example a
beam going from yellow to red.

* blade:fx_beam       vertical plane, wide at the top and narrowing to a point at the bottom: a
                      soft glow (tint 0) around a bright core (tint 1). Shown with a vertical
                      billboard, so it always faces the viewer.
* blade:fx_streak     the same, straight.
* blade:fx_ring       flat dashed ring lying on the ground (tint 0).
* blade:fx_wave       flat soft ring, a shockwave (tint 0).
* blade:fx_glow       flat round glow (tint 0).
* blade:fx_sigil      flat circle of judgement for Sky Verdict: rings, ticks and runes (tint 0).
* blade:fx_puff       small soft cube of smoke (tint 0).
"""
import io
import json
import math
import random

from PIL import Image, ImageDraw, ImageFilter

WHITE = 0xFFFFFF


def png(image):
    out = io.BytesIO()
    image.save(out, "PNG", optimize=True)
    return out.getvalue()


def smoothstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def beam_texture(width, height, half_top, half_bottom, power, peak, taper_tip):
    """White strip whose alpha falls off softly from the middle; the half width goes from
    half_top to half_bottom down the strip, and the last rows close to a point if taper_tip."""
    image = Image.new("RGBA", (width, height), (255, 255, 255, 0))
    px = image.load()
    for y in range(height):
        v = (y + 0.5) / height
        half = half_top + (half_bottom - half_top) * v
        if taper_tip:
            half *= min(1.0, (1.0 - v) / 0.05)
        fade = smoothstep(0.0, 0.1, v)
        for x in range(width):
            u = (x + 0.5) / width
            d = abs(u - 0.5)
            if half <= 0 or d >= half:
                continue
            a = (1.0 - d / half) ** power * peak * fade
            px[x, y] = (255, 255, 255, int(round(255 * a)))
    return image


def ring_texture(size, radius, thickness, dashes, duty, inner=None):
    """Anti-aliased ring (dashed if dashes) drawn at 4x and scaled down."""
    big = size * 4
    image = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(image)
    c = big / 2
    r = radius * c
    w = max(1, int(thickness * c))
    if dashes:
        step = 360.0 / dashes
        for i in range(dashes):
            start = i * step
            draw.arc((c - r, c - r, c + r, c + r), start, start + step * duty, fill=255, width=w)
    else:
        draw.ellipse((c - r, c - r, c + r, c + r), outline=255, width=w)
    if inner:
        ri, wi, alpha = inner
        ri *= c
        draw.ellipse((c - ri, c - ri, c + ri, c + ri), outline=alpha, width=max(1, int(wi * c)))
    mask = image.resize((size, size), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.6))
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.putalpha(mask)
    return out


def wave_texture(size):
    """A bright soft edge with a faint glow filling the inside, like a shockwave front."""
    image = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    px = image.load()
    for y in range(size):
        for x in range(size):
            r = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) / (size / 2)
            edge = math.exp(-((r - 0.9) / 0.045) ** 2)
            inside = 0.22 * smoothstep(0.3, 0.88, r) if r < 0.9 else 0.0
            a = min(1.0, edge + inside)
            px[x, y] = (255, 255, 255, int(255 * a))
    return image


def glow_texture(size):
    image = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    px = image.load()
    for y in range(size):
        for x in range(size):
            r = math.hypot(x + 0.5 - size / 2, y + 0.5 - size / 2) / (size / 2)
            a = max(0.0, 1.0 - r) ** 2
            px[x, y] = (255, 255, 255, int(255 * a))
    return image


def sigil_texture(size):
    big = size * 4
    image = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(image)
    c = big / 2

    def circle(r, w, alpha=255):
        r *= c
        draw.ellipse((c - r, c - r, c + r, c + r), outline=alpha, width=max(1, int(w * c)))

    circle(0.97, 0.022)
    circle(0.86, 0.012)
    circle(0.40, 0.012, 200)
    circle(0.12, 0.02)
    # Ticks between the two outer rings, a long one every 45 degrees.
    for i in range(72):
        a = math.radians(i * 5)
        r0 = 0.86 if i % 9 else 0.80
        draw.line((c + math.cos(a) * r0 * c, c + math.sin(a) * r0 * c,
                   c + math.cos(a) * 0.95 * c, c + math.sin(a) * 0.95 * c),
                  fill=255 if i % 9 == 0 else 150, width=max(1, int(0.008 * c)))
    # Eight rays from the inner circle towards the rim and small diamonds between them.
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        draw.line((c + math.cos(a) * 0.14 * c, c + math.sin(a) * 0.14 * c,
                   c + math.cos(a) * 0.80 * c, c + math.sin(a) * 0.80 * c), fill=170, width=max(1, int(0.008 * c)))
        b = math.radians(i * 45)
        mx, my = c + math.cos(b) * 0.63 * c, c + math.sin(b) * 0.63 * c
        s = 0.045 * c
        draw.polygon([(mx + math.cos(b) * s, my + math.sin(b) * s), (mx - math.sin(b) * s * 0.6, my + math.cos(b) * s * 0.6),
                      (mx - math.cos(b) * s, my - math.sin(b) * s), (mx + math.sin(b) * s * 0.6, my - math.cos(b) * s * 0.6)],
                     fill=230)
    mask = image.resize((size, size), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.5))
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.putalpha(mask)
    return out


def puff_texture(size, seed):
    rng = random.Random(seed)
    image = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    px = image.load()
    blobs = [(rng.uniform(0, size), rng.uniform(0, size), rng.uniform(2, 5)) for _ in range(10)]
    for y in range(size):
        for x in range(size):
            shade = 0.82
            for bx, by, br in blobs:
                d = math.hypot(x - bx, y - by) / br
                if d < 1:
                    shade = max(shade, 1.0 - 0.12 * d)
            g = int(255 * shade)
            px[x, y] = (g, g, g, 235)
    return image


def plane(texture_key, vertical, tintindex=0, z=8.0):
    if vertical:
        frm, to = [0, 0, z], [16, 16, z]
        faces = {f: {"uv": [0, 0, 16, 16], "texture": texture_key, "tintindex": tintindex} for f in ("north", "south")}
    else:
        frm, to = [0, 8, 0], [16, 8, 16]
        faces = {f: {"uv": [0, 0, 16, 16], "texture": texture_key, "tintindex": tintindex} for f in ("up", "down")}
    return {"from": frm, "to": to, "shade": False, "faces": faces}


def add_model(files, name, textures, elements, tints):
    files[f"assets/blade/models/item/fx/{name}.json"] = json.dumps({
        "textures": dict(textures, particle=next(iter(textures.values()))),
        "elements": elements,
    }).encode()
    files[f"assets/blade/items/fx_{name}.json"] = json.dumps({"model": {
        "type": "minecraft:model",
        "model": f"blade:item/fx/{name}",
        "tints": [{"type": "minecraft:custom_model_data", "index": i, "default": WHITE} for i in range(tints)],
    }}).encode()


def texture(files, name, image):
    files[f"assets/blade/textures/item/fx/{name}.png"] = png(image)
    return f"blade:item/fx/{name}"


def add_light_fx(files):
    glow = texture(files, "beam_glow", beam_texture(64, 256, 0.48, 0.24, 1.4, 0.8, True))
    core = texture(files, "beam_core", beam_texture(64, 256, 0.15, 0.07, 0.9, 1.0, True))
    add_model(files, "beam", {"glow": glow, "core": core},
              [plane("#glow", True, 0), plane("#core", True, 1)], 2)
    glow = texture(files, "streak_glow", beam_texture(64, 256, 0.46, 0.46, 1.8, 0.55, False))
    core = texture(files, "streak_core", beam_texture(64, 256, 0.12, 0.12, 0.9, 1.0, False))
    add_model(files, "streak", {"glow": glow, "core": core},
              [plane("#glow", True, 0), plane("#core", True, 1)], 2)
    add_model(files, "ring", {"ring": texture(files, "ring_dashed", ring_texture(256, 0.9, 0.07, 28, 0.62,
                                                                              inner=(0.8, 0.015, 120)))},
              [plane("#ring", False)], 1)
    add_model(files, "wave", {"wave": texture(files, "wave", wave_texture(256))}, [plane("#wave", False)], 1)
    add_model(files, "glow", {"glow": texture(files, "glow", glow_texture(128))}, [plane("#glow", False)], 1)
    add_model(files, "sigil", {"sigil": texture(files, "sigil", sigil_texture(512))}, [plane("#sigil", False)], 1)
    puff = texture(files, "puff", puff_texture(16, 31))
    faces = {f: {"uv": [0, 0, 16, 16], "texture": "#puff", "tintindex": 0}
             for f in ("north", "south", "east", "west", "up", "down")}
    add_model(files, "puff", {"puff": puff}, [{"from": [4, 4, 4], "to": [12, 12, 12], "faces": faces}], 1)

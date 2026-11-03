#!/usr/bin/env python3
"""Draws the ruby currency art (formerly orbs) at 64x64: the battle pass reward icons
(assets/battlepass/item/orb*.png) and the chat/scoreboard glyph (assets/text-icons/orbs.png).

Every icon is drawn as vector shapes on an 8x canvas and scaled down, so the 64x64 textures
keep the facets and highlights; the game shows them at the usual 16x16 item size (and the
glyph at text height), just sharper. File names stay orb* so the plugins need no changes.

Variants per reward: <name> (normal), _locked (grey stones and a padlock), _claimed (a green
tick), _ready (12-frame shimmer, frametime 3 in the .mcmeta next to it).

Usage: ruby_icons.py   (writes the PNGs; then battlepass.py and text_icons.py put them in the zip)
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ITEM_DIR = HERE / "assets/battlepass/item"
GLYPH = HERE / "assets/text-icons/orbs.png"
SIZE = 64
SS = 8
C = SIZE * SS
FRAMES = 12

RUBY = {"light": (255, 140, 150), "mid": (232, 36, 64), "dark": (150, 8, 32), "deep": (92, 2, 20),
        "edge": (52, 0, 10)}
AMBER = {"light": (255, 226, 140), "mid": (246, 160, 40), "dark": (196, 96, 10), "deep": (128, 54, 4),
         "edge": (70, 28, 0)}
ROYAL = {"light": (255, 150, 200), "mid": (236, 30, 110), "dark": (156, 6, 70), "deep": (96, 2, 44),
         "edge": (54, 0, 24)}


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gem(draw, cx, cy, w, h, pal, grey=False):
    """A brilliant-cut stone seen from the front, lit from the top left."""
    t, g, b = cy - 0.46 * h, cy - 0.08 * h, cy + 0.5 * h

    def p(x, y):
        return (cx + x * w, y)

    tl, tm1, tm2, tr = p(-0.30, t), p(-0.10, t), p(0.10, t), p(0.30, t)
    gl, g1, g0, g2, gr = p(-0.5, g), p(-0.25, g), p(0, g), p(0.25, g), p(0.5, g)
    cu = p(0, b)
    L, M, D, DD = pal["light"], pal["mid"], pal["dark"], pal["deep"]
    facets = [
        ([gl, tl, g1], mix(L, M, 0.35)),
        ([tl, tm1, g0, g1], mix(L, M, 0.15)),
        ([tm1, tm2, g0], L),
        ([tm2, tr, g2, g0], mix(L, M, 0.55)),
        ([tr, gr, g2], mix(M, D, 0.25)),
        ([gl, g1, cu], mix(M, D, 0.15)),
        ([g1, g0, cu], M),
        ([g0, g2, cu], mix(M, D, 0.55)),
        ([g2, gr, cu], mix(D, DD, 0.5)),
    ]
    outline = [gl, tl, tr, gr, cu]
    edge = pal["edge"]
    if grey:
        facets = [(pts, greyed(col)) for pts, col in facets]
        edge = greyed(edge)
    draw.polygon(outline, fill=edge)
    # Shrink the facets a touch so the dark outline shows round the stone and between facets.
    inset = 0.045 * w
    for pts, col in facets:
        draw.polygon(shrink(pts, inset * 0.35), fill=col)
    draw.line([gl, gr], fill=mix(edge, M if not grey else greyed(M), 0.35), width=max(1, int(0.03 * w)))
    # Highlights: a glint on the table and a soft streak on the left crown facet.
    hl = (255, 255, 255) if not grey else (230, 230, 230)
    draw.polygon([p(-0.08, t + 0.03 * h), p(0.02, t + 0.03 * h), p(-0.06, g - 0.05 * h)], fill=hl + (200,))
    draw.polygon([p(-0.38, g - 0.04 * h), p(-0.27, t + 0.08 * h), p(-0.22, g - 0.06 * h)], fill=hl + (110,))


def shrink(pts, d):
    cx = sum(x for x, _ in pts) / len(pts)
    cy = sum(y for _, y in pts) / len(pts)
    out = []
    for x, y in pts:
        dx, dy = cx - x, cy - y
        n = math.hypot(dx, dy) or 1
        out.append((x + dx / n * d, y + dy / n * d))
    return out


def greyed(col):
    v = round(0.3 * col[0] + 0.59 * col[1] + 0.11 * col[2])
    v = round(40 + v * 0.62)
    return (v, v, v)


def sparkle(draw, x, y, r, alpha=255):
    col = (255, 255, 255, alpha)
    draw.polygon([(x, y - r), (x + r * 0.22, y - r * 0.22), (x + r, y), (x + r * 0.22, y + r * 0.22),
                  (x, y + r), (x - r * 0.22, y + r * 0.22), (x - r, y), (x - r * 0.22, y - r * 0.22)], fill=col)


def heap(draw, pals, grey=False):
    """Three stones: one behind on top, two in front."""
    s = SS
    top, left, right = pals
    gem(draw, 32 * s, 21 * s, 30 * s, 30 * s, top, grey)
    gem(draw, 17.5 * s, 41 * s, 30 * s, 30 * s, left, grey)
    gem(draw, 46.5 * s, 41 * s, 30 * s, 30 * s, right, grey)


def padlock(draw):
    s = SS
    body = (226, 140, 32)
    dark = (90, 44, 6)
    x0, y0, x1, y1 = 40 * s, 44 * s, 62 * s, 62 * s
    draw.rounded_rectangle([x0 + 4 * s, 34 * s, x1 - 4 * s, 50 * s], radius=7 * s, outline=dark, width=4 * s)
    draw.rounded_rectangle([x0 + 6 * s, 36 * s, x1 - 6 * s, 50 * s], radius=5 * s, outline=(200, 200, 210), width=2 * s)
    draw.rounded_rectangle([x0, y0, x1, y1], radius=3 * s, fill=dark)
    draw.rounded_rectangle([x0 + 1.5 * s, y0 + 1.5 * s, x1 - 1.5 * s, y1 - 1.5 * s], radius=2 * s, fill=body)
    draw.rectangle([x0 + 1.5 * s, y0 + 1.5 * s, x1 - 1.5 * s, y0 + 5 * s], fill=(250, 196, 90))
    draw.ellipse([49 * s, 50 * s, 53 * s, 54 * s], fill=dark)
    draw.rectangle([50.2 * s, 52 * s, 51.8 * s, 58 * s], fill=dark)


def tick(draw):
    s = SS
    pts = [(40 * s, 50 * s), (47 * s, 57 * s), (61 * s, 41 * s)]
    draw.line(pts, fill=(14, 60, 14), width=9 * s, joint="curve")
    draw.line(pts, fill=(84, 230, 80), width=5 * s, joint="curve")


def plus(draw):
    s = SS
    cx, cy, r, t = 55 * s, 9 * s, 7 * s, 2.2 * s
    for col, grow in (((40, 40, 40), 1.4 * s), ((255, 255, 255), 0)):
        draw.rectangle([cx - r - grow, cy - t - grow, cx + r + grow, cy + t + grow], fill=col)
        draw.rectangle([cx - t - grow, cy - r - grow, cx + t + grow, cy + r + grow], fill=col)


def crown(draw, grey=False):
    s = SS
    gold, dark = ((252, 208, 92), (110, 70, 0)) if not grey else ((170, 170, 170), (60, 60, 60))
    pts = [(21 * s, 9 * s), (24 * s, 1 * s), (28.5 * s, 6 * s), (32 * s, 0), (35.5 * s, 6 * s), (40 * s, 1 * s),
           (43 * s, 9 * s)]
    draw.polygon(pts + [(43 * s, 11 * s), (21 * s, 11 * s)], fill=dark)
    draw.polygon(shrink(pts + [(43 * s, 11 * s), (21 * s, 11 * s)], 1.2 * s), fill=gold)
    for x in (24, 32, 40):
        draw.ellipse([(x - 1.3) * s, 2.5 * s if x != 32 else 1.5 * s, (x + 1.3) * s, 5 * s if x != 32 else 4 * s],
                     fill=(236, 36, 70) if not grey else (120, 120, 120))


def casket(draw, grey=False):
    s = SS
    gold, light, dark, inside = ((236, 172, 52), (255, 222, 120), (110, 66, 6), (60, 20, 10))
    if grey:
        gold, light, dark, inside = greyed(gold), greyed(light), greyed(dark), greyed(inside)
    draw.rounded_rectangle([1 * s, 5 * s, 63 * s, 61 * s], radius=6 * s, fill=dark)
    draw.rounded_rectangle([3 * s, 7 * s, 61 * s, 59 * s], radius=5 * s, fill=gold)
    draw.rounded_rectangle([3 * s, 7 * s, 61 * s, 13 * s], radius=4 * s, fill=light)
    draw.rounded_rectangle([8 * s, 12 * s, 56 * s, 54 * s], radius=3 * s, fill=inside)
    for x, y in ((5, 9), (59, 9), (5, 57), (59, 57)):
        draw.ellipse([(x - 1.5) * s, (y - 1.5) * s, (x + 1.5) * s, (y + 1.5) * s], fill=light)


def render(layers, shimmer=None):
    img = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")
    for layer in layers:
        layer(draw)
    if shimmer is not None:
        img = add_shimmer(img, shimmer)
    small = img.resize((SIZE, SIZE), Image.LANCZOS)
    # Keep the edges crisp: almost-clear pixels go fully clear, the rest fully opaque.
    alpha = small.getchannel("A").point(lambda a: 0 if a < 70 else 255)
    small.putalpha(alpha)
    return small


def add_shimmer(img, phase):
    """A light band sweeping across the stones from the top left, plus twinkling sparkles."""
    band = Image.new("L", (C, C), 0)
    bd = ImageDraw.Draw(band)
    pos = -0.4 * C + phase * 1.8 * C
    width = 0.16 * C
    bd.polygon([(pos, 0), (pos + width, 0), (pos + width - C, C), (pos - C, C)], fill=150)
    band = band.filter(ImageFilter.GaussianBlur(3 * SS))
    mask = Image.composite(band, Image.new("L", (C, C), 0), img.getchannel("A").point(lambda a: 255 if a > 200 else 0))
    white = Image.new("RGBA", (C, C), (255, 255, 255, 255))
    out = img.copy()
    out.paste(Image.composite(white, img, mask), (0, 0), mask.point(lambda v: 255 if v else 0))
    out = Image.blend(img, out, 1.0)
    d = ImageDraw.Draw(out, "RGBA")
    for i, (x, y) in enumerate(((22, 12), (9, 33), (52, 30), (36, 46))):
        k = (phase * FRAMES + i * 3) % FRAMES
        a = max(0.0, 1 - abs(k - 2) / 2.5)
        if a > 0:
            sparkle(d, x * SS, y * SS, (3 + 3 * a) * SS, int(255 * a))
    return out


def sheet(frames):
    out = Image.new("RGBA", (SIZE, SIZE * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (0, i * SIZE))
    return out


def variants(name, base_layers, grey_layers):
    """Writes <name>, _locked, _claimed and _ready."""
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    render(base_layers).save(ITEM_DIR / f"{name}.png")
    render(grey_layers + [padlock]).save(ITEM_DIR / f"{name}_locked.png")
    render(base_layers + [tick]).save(ITEM_DIR / f"{name}_claimed.png")
    sheet([render(base_layers, i / FRAMES) for i in range(FRAMES)]).save(ITEM_DIR / f"{name}_ready.png")
    print(f"{name}: 4 icons", flush=True)


def main():
    rubies = (RUBY, RUBY, RUBY)
    variants("orb", [lambda d: heap(d, rubies)], [lambda d: heap(d, rubies, True)])
    bonus = (RUBY, AMBER, RUBY)
    variants("orb_bonus", [lambda d: heap(d, bonus), plus], [lambda d: heap(d, bonus, True), plus])
    variants("orb_chest", [casket, lambda d: heap(d, rubies)], [lambda d: casket(d, True), lambda d: heap(d, rubies, True)])
    royal = (ROYAL, ROYAL, ROYAL)
    variants("orb_premium", [lambda d: heap(d, royal), crown],
             [lambda d: heap(d, royal, True), lambda d: crown(d, True)])

    # The glyph: one big stone with a sparkle.
    img = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    gem(d, 32 * SS, 34 * SS, 58 * SS, 58 * SS, RUBY)
    sparkle(d, 55 * SS, 9 * SS, 7 * SS)
    small = img.resize((SIZE, SIZE), Image.LANCZOS)
    small.putalpha(small.getchannel("A").point(lambda a: 0 if a < 70 else 255))
    small.save(GLYPH)
    print(f"glyph {GLYPH.name}", flush=True)


if __name__ == "__main__":
    main()

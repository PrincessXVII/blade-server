#!/usr/bin/env python3
"""Adds see-through copies of cosmetic icons to a built BladePack.zip.

The cosmetics menu shows case items the player does not own yet with custom model data
raised by GHOST_OFFSET. For every hat (carved_pumpkin), sword skin (diamond/netherite sword
from 7001) and mace skin this adds an entry at that offset whose model is a copy of the
original with every other texel cut out (checkerboard) and the rest at GHOST_ALPHA opacity.
The checkerboard keeps the icon see-through even where the client draws items without
blending, which alpha alone does not.

Ghost textures live under blade_ghost:item/ghost/..., which the vanilla "item" directory
source already puts on the atlas.

Usage: ghost_icons.py path/to/BladePack.zip   (rewrites the zip in place)
"""
import io
import json
import sys
import zipfile

from PIL import Image

GHOST_OFFSET = 100000
GHOST_ALPHA = 0.55
GHOST_NS = "blade_ghost"
OVERLAY = "overlay_1_21_4/"
TARGETS = {
    "carved_pumpkin": 1,
    "diamond_sword": 7001,
    "netherite_sword": 7001,
    "mace": 1,
}


def main(path):
    with zipfile.ZipFile(path) as z:
        files = {info.filename: z.read(info.filename) for info in z.infolist() if not info.is_dir()}

    sprites = {}
    for atlas in ("items", "blocks"):
        name = f"assets/minecraft/atlases/{atlas}.json"
        if name in files:
            for source in json.loads(files[name]).get("sources", []):
                if source.get("type") == "single" and "sprite" in source:
                    sprites.setdefault(source["sprite"], source["resource"])

    ghost_models = {}
    ghost_textures = {}

    def full(ref):
        return ref if ":" in ref else "minecraft:" + ref

    def read(rel):
        for prefix in (OVERLAY, ""):
            if prefix + rel in files:
                return files[prefix + rel]
        return None

    def ghost_texture(ref):
        ref = full(sprites.get(ref, ref))
        if ref in ghost_textures:
            return ghost_textures[ref]
        ns, rel = ref.split(":", 1)
        png = read(f"assets/{ns}/textures/{rel}.png")
        if png is None:
            ghost_textures[ref] = None
            return None
        image = Image.open(io.BytesIO(png)).convert("RGBA")
        pixels = image.load()
        width, height = image.size
        for y in range(height):
            for x in range(width):
                r, g, b, a = pixels[x, y]
                pixels[x, y] = (r, g, b, 0 if (x + y) % 2 else int(a * GHOST_ALPHA))
        out = io.BytesIO()
        image.save(out, "PNG")
        target = f"item/ghost/{ns}/{rel}"
        files[f"assets/{GHOST_NS}/textures/{target}.png"] = out.getvalue()
        meta = read(f"assets/{ns}/textures/{rel}.png.mcmeta")
        if meta is not None:
            files[f"assets/{GHOST_NS}/textures/{target}.png.mcmeta"] = meta
        ghost_textures[ref] = f"{GHOST_NS}:{target}"
        return ghost_textures[ref]

    def ghost_model(ref):
        ref = full(ref)
        if ref in ghost_models:
            return ghost_models[ref]
        ns, rel = ref.split(":", 1)
        raw = read(f"assets/{ns}/models/{rel}.json")
        if raw is None or ns == "minecraft" and rel.startswith(("item/generated", "item/handheld", "builtin/")):
            ghost_models[ref] = None
            return None
        data = json.loads(raw)
        textures = data.get("textures", {})
        for key, value in list(textures.items()):
            if isinstance(value, str) and not value.startswith("#"):
                ghost = ghost_texture(value)
                if ghost is not None:
                    textures[key] = ghost
        parent = data.get("parent")
        if isinstance(parent, str) and not full(parent).startswith("minecraft:"):
            ghost_parent = ghost_model(parent)
            if ghost_parent is not None:
                data["parent"] = ghost_parent
        target = f"{GHOST_NS}:{ns}/{rel}"
        files[f"assets/{GHOST_NS}/models/{ns}/{rel}.json"] = json.dumps(data).encode()
        ghost_models[ref] = target
        return target

    added = 0
    for item, first in TARGETS.items():
        for prefix in ("", OVERLAY):
            name = f"{prefix}assets/minecraft/items/{item}.json"
            if name not in files:
                continue
            definition = json.loads(files[name])
            model = definition.get("model", {})
            if model.get("type") != "range_dispatch":
                continue
            entries = [e for e in model.get("entries", []) if e.get("threshold", 0) < GHOST_OFFSET]
            extra = []
            for entry in entries:
                inner = entry.get("model", {})
                if entry.get("threshold", 0) < first or inner.get("type") != "model":
                    continue
                ghost = ghost_model(inner["model"])
                if ghost is None:
                    continue
                copy = dict(inner)
                copy["model"] = ghost
                copy.pop("tints", None)
                extra.append({"threshold": entry["threshold"] + GHOST_OFFSET, "model": copy})
            model["entries"] = sorted(entries + extra, key=lambda e: e["threshold"])
            files[name] = json.dumps(definition).encode()
            added += len(extra)

    # Make sure both atlases pick the ghost textures up.
    for atlas in ("items", "blocks"):
        name = f"assets/minecraft/atlases/{atlas}.json"
        data = json.loads(files[name]) if name in files else {"sources": []}
        sources = data.setdefault("sources", [])
        if not any(s.get("type") == "directory" and s.get("source") in ("item", "item/ghost") for s in sources):
            sources.append({"type": "directory", "source": "item/ghost", "prefix": "item/ghost/"})
        files[name] = json.dumps(data).encode()

    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])
    print(f"ghost icons: {added} entries, {len(ghost_models)} models, {len(ghost_textures)} textures", flush=True)


if __name__ == "__main__":
    main(sys.argv[1])

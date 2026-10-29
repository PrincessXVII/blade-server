#!/usr/bin/env python3
"""Adds the hats picked from the Veltrix pack to BladePack.zip and BladeCosmetics hats.json.

Each hat gets a default and (where the pack has one) a shiny variant:
  - item definitions atlantis_cosmetics:hat/veltrix_<name>/<variant> in assets/ and the 1.21.4 overlay;
  - a carved_pumpkin custom_model_data entry (menu icon) from cmd 1001;
  - models/textures copied from the Veltrix pack only where BladePack lacks them (it already
    carries most of the civilization namespace).
Run ghost_icons.py on the zip afterwards so the new pumpkin cmds get see-through copies.

Usage: veltrix_hats.py <BladePack.zip> <veltrix extracted dir> <hats.json>   (both rewritten in place)
Re-running replaces the earlier veltrix_ entries instead of adding them twice.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

from veltrix_hats_data import HATS, SHINY_PREFIX, TIER_PRICES

OVERLAY = "overlay_1_21_4/"
PREFIX = "veltrix_"
# Own cmd block: 777/778 are taken by other pumpkin items above the old hats.
VX_BASE = 1001


def main(pack_path, veltrix_dir, hats_path):
    veltrix = Path(veltrix_dir)
    with zipfile.ZipFile(pack_path) as z:
        files = {i.filename: z.read(i.filename) for i in z.infolist() if not i.is_dir()}
    hats = json.loads(Path(hats_path).read_text(encoding="utf-8"))
    hats = [h for h in hats if not h["id"].startswith(PREFIX)]
    next_cmd = VX_BASE

    copied = 0

    def ensure(rel):
        """Copies assets/<rel> (+ .mcmeta) from Veltrix when the pack lacks it."""
        nonlocal copied
        name = "assets/" + rel
        if name in files:
            return True
        src = veltrix / name
        if not src.exists():
            return False
        files[name] = src.read_bytes()
        meta = Path(str(src) + ".mcmeta")
        if meta.exists():
            files[name + ".mcmeta"] = meta.read_bytes()
        copied += 1
        return True

    def ensure_model(ref):
        ns, rel = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        if ns == "minecraft":
            return True
        if not ensure(f"{ns}/models/{rel}.json"):
            return False
        data = json.loads(files[f"assets/{ns}/models/{rel}.json"])
        for tex in data.get("textures", {}).values():
            if isinstance(tex, str) and not tex.startswith("#"):
                tns, trel = tex.split(":", 1) if ":" in tex else ("minecraft", tex)
                if tns != "minecraft" and not ensure(f"{tns}/textures/{trel}.png"):
                    print("missing texture", tex)
        parent = data.get("parent")
        return parent is None or ensure_model(parent)

    def model_for(key, variant):
        base = "class_hats/" + key.split("/")[1] if key.startswith("classhats/") else key
        candidates = [f"{base}/{variant}"]
        if variant == "normal":
            candidates.append(base)  # hats that only ship a single top-level model
        for c in candidates:
            ref = f"civilization:item/wearable/{c}"
            if (veltrix / f"assets/civilization/models/item/wearable/{c}.json").exists() or \
                    f"assets/civilization/models/item/wearable/{c}.json" in files:
                return ref
        return None

    new_hats = []
    pumpkin = []
    for key, (name, gender, description, color, tier) in HATS.items():
        short = key.split("/")[-1]
        prices = TIER_PRICES[tier]
        for variant in ("normal", "shiny"):
            ref = model_for(key, variant)
            if ref is None or not ensure_model(ref):
                continue
            shiny = variant == "shiny"
            hat_id = f"{PREFIX}{short}/{'shiny' if shiny else 'default'}"
            item_model = f"atlantis_cosmetics:hat/{PREFIX}{short}/{'shiny' if shiny else 'default'}"
            definition = json.dumps({"model": {"type": "minecraft:model", "model": ref}}, indent=2).encode()
            rel = f"assets/atlantis_cosmetics/items/hat/{PREFIX}{short}/{'shiny' if shiny else 'default'}.json"
            files[rel] = definition
            files[OVERLAY + rel] = definition
            if shiny:
                price = TIER_PRICES[min(5, tier + 1)][-1]
                hat_name = SHINY_PREFIX[gender] + " " + name[0].lower() + name[1:]
                hat_desc = description + " Сияющая версия."
            else:
                price = prices[sum(map(ord, short)) % len(prices)]
                hat_name = name
                hat_desc = description
            new_hats.append({"id": hat_id, "cmd": next_cmd, "model": item_model, "name": hat_name,
                             "description": hat_desc, "color": color, "price": price})
            pumpkin.append({"threshold": next_cmd, "model": {"type": "model", "model": ref}})
            next_cmd += 1

    for prefix in ("", OVERLAY):
        name = f"{prefix}assets/minecraft/items/carved_pumpkin.json"
        if name not in files:
            continue
        data = json.loads(files[name])
        model = data["model"]
        kept = [e for e in model["entries"] if not (VX_BASE <= e["threshold"] < 100000)]
        model["entries"] = sorted(kept + pumpkin, key=lambda e: e["threshold"])
        files[name] = json.dumps(data).encode()

    with zipfile.ZipFile(pack_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])
    Path(hats_path).write_text(json.dumps(hats + new_hats, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"hats: {len(new_hats)} added (cmd {new_hats[0]['cmd']}..{new_hats[-1]['cmd']}), {copied} files copied")


if __name__ == "__main__":
    main(*sys.argv[1:4])

#!/usr/bin/env python3
"""Roman enchantment levels 11..30 in BladePack.zip (vanilla lang stops at X, so Sharpness XVII
on the Midas sword or the emerald blade showed the raw enchantment.level.17 key). Missing keys of
any language fall back to en_us; ru_ru gets them too. Re-running rewrites the same keys.

Usage: enchant_levels.py <BladePack.zip>   (rewritten in place)
"""
import json
import sys
import zipfile


def roman(n):
    out = ""
    for value, digits in ((1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"), (90, "XC"),
                          (50, "L"), (40, "XL"), (10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")):
        while n >= value:
            out += digits
            n -= value
    return out


def main(pack_path):
    with zipfile.ZipFile(pack_path) as z:
        infos = z.infolist()
        files = {i.filename: z.read(i.filename) for i in infos if not i.is_dir()}
    levels = {f"enchantment.level.{n}": roman(n) for n in range(11, 31)}
    for lang in ("en_us", "ru_ru"):
        name = f"assets/minecraft/lang/{lang}.json"
        data = json.loads(files[name]) if name in files else {}
        data.update(levels)
        files[name] = json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8")
    with zipfile.ZipFile(pack_path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            z.writestr(name, files[name])
    print(f"enchantment levels 11..30 written to en_us and ru_ru")


if __name__ == "__main__":
    main(sys.argv[1])

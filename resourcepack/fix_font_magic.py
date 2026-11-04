#!/usr/bin/env python3
"""Repairs TrueType fonts that claim to be CFF, in the sources and in a built BladePack.zip.

five.ttf and tenone.ttf start with the sfnt magic OTTO but hold glyf/loca outlines and no CFF
table, so FreeType refuses them (0x8e, table missing) and the client falls back to the default
font. The magic becomes 00 01 00 00 and head.checkSumAdjustment is recomputed; nothing else in
the font changes, the table checksums stay valid.

Every other zip entry is copied as raw bytes, compressed data and headers alike, in its original
place. Running it again changes nothing.

Usage: fix_font_magic.py path/to/BladePack.zip   (rewrites the zip in place)
"""
import hashlib
import os
import struct
import sys
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
FONTS = ("five", "tenone")
SOURCE_DIRS = (HERE / "assets/fonts", HERE.parent / "resourcepack-src/assets/minecraft/font")
PACK_DIR = "assets/minecraft/font"

TRUETYPE = b"\x00\x01\x00\x00"
CD_SIG = b"PK\x01\x02"
EOCD_SIG = b"PK\x05\x06"


def fix_font(data):
    """Returns the repaired font, or the same bytes if it is already fine."""
    if data[:4] != b"OTTO":
        return data
    count = struct.unpack(">H", data[4:6])[0]
    tables = {}
    for i in range(count):
        tag, _, offset, length = struct.unpack(">4sIII", data[12 + i * 16:28 + i * 16])
        tables[tag] = (offset, length)
    if b"CFF " in tables or b"CFF2" in tables or b"glyf" not in tables or b"loca" not in tables:
        return data
    out = bytearray(data)
    out[:4] = TRUETYPE
    adjustment = tables[b"head"][0] + 8
    out[adjustment:adjustment + 4] = b"\0\0\0\0"
    padded = bytes(out) + b"\0" * (-len(out) % 4)
    total = sum(struct.unpack(f">{len(padded) // 4}I", padded)) & 0xFFFFFFFF
    out[adjustment:adjustment + 4] = struct.pack(">I", (0xB1B0AFBA - total) & 0xFFFFFFFF)
    return bytes(out)


def deflate(data):
    packer = zlib.compressobj(9, zlib.DEFLATED, -15)
    return packer.compress(data) + packer.flush()


def write_atomic(path, data):
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_bytes(data)
    os.replace(tmp, path)


def patch_zip(path, targets):
    raw = path.read_bytes()
    eocd = raw.rfind(EOCD_SIG)
    if eocd < 0:
        raise SystemExit("no end of central directory")
    entries, cd_size, cd_offset = struct.unpack("<HII", raw[eocd + 10:eocd + 20])
    if 0xFFFF == entries or 0xFFFFFFFF in (cd_size, cd_offset):
        raise SystemExit("zip64 is not handled")

    # Central directory records, in file order of their local entries.
    records = []
    pos = cd_offset
    for _ in range(entries):
        if raw[pos:pos + 4] != CD_SIG:
            raise SystemExit(f"bad central directory record at {pos}")
        name_len, extra_len, comment_len = struct.unpack("<HHH", raw[pos + 28:pos + 34])
        size = 46 + name_len + extra_len + comment_len
        local = struct.unpack("<I", raw[pos + 42:pos + 46])[0]
        name = raw[pos + 46:pos + 46 + name_len].decode("utf-8")
        records.append((local, name, bytearray(raw[pos:pos + size])))
        pos += size
    order = sorted(records, key=lambda r: r[0])
    ends = [r[0] for r in order[1:]] + [cd_offset]

    out = bytearray(raw[:order[0][0]])
    changed = []
    shifted = {}
    for (local, name, record), end in zip(order, ends):
        chunk = raw[local:end]
        shifted[local] = len(out)
        if name in targets:
            method, csize, usize = (struct.unpack("<H", chunk[8:10])[0],
                                    *struct.unpack("<II", chunk[18:26]))
            flags = struct.unpack("<H", chunk[6:8])[0]
            if method != 8 or flags & 8:
                raise SystemExit(f"{name}: unexpected method {method} or flags {flags:#x}")
            head = 30 + sum(struct.unpack("<HH", chunk[26:30]))
            font = zlib.decompress(chunk[head:head + csize], -15)
            fixed = fix_font(font)
            if fixed != font:
                if len(fixed) != usize:
                    raise SystemExit(f"{name}: size changed")
                packed = deflate(fixed)
                numbers = struct.pack("<III", zlib.crc32(fixed), len(packed), len(fixed))
                chunk = chunk[:14] + numbers + chunk[26:head] + packed + chunk[head + csize:]
                record[16:28] = numbers
                changed.append(name)
        out += chunk

    new_cd = len(out)
    for local, _, record in records:
        out += record[:42] + struct.pack("<I", shifted[local]) + record[46:]
    tail = bytearray(raw[eocd:])
    tail[12:20] = struct.pack("<II", len(out) - new_cd, new_cd)
    out += tail

    if changed:
        write_atomic(path, bytes(out))
    return changed


def main(zip_path):
    path = Path(zip_path)
    for folder in SOURCE_DIRS:
        for font in FONTS:
            source = folder / f"{font}.ttf"
            if not source.is_file():
                print(f"no {source}", flush=True)
                continue
            data = source.read_bytes()
            fixed = fix_font(data)
            if fixed != data:
                write_atomic(source, fixed)
            print(f"{'fixed' if fixed != data else 'ok   '} {source}", flush=True)

    targets = {f"{PACK_DIR}/{font}.ttf" for font in FONTS}
    changed = patch_zip(path, targets)
    for name in sorted(targets):
        print(f"{'fixed' if name in changed else 'ok   '} {path.name}:{name}", flush=True)
    print(f"sha1 {hashlib.sha1(path.read_bytes()).hexdigest()}", flush=True)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])

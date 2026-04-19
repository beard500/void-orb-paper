#!/usr/bin/env python3
"""Generates the void_orb item textures (stdlib-only PNG writer).

Outputs two 16x16 PNGs into src/resourcepack/assets/void_orb/textures/item/:
  - void_orb.png       — deep-black sphere with purple nebula highlights
  - void_orb_ring.png  — layered white/purple glow band for the Saturn ring

Pixel coordinates and colors are taken from the Blockbench paint passes used
to author the original model. CI runs this before zipping the resource pack.
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "resourcepack", "assets", "void_orb", "textures", "item")

BASE_BLACK = (10, 5, 16, 255)        # #0A0510
PURPLE_DEEP = (123, 44, 191, 255)    # #7B2CBF
PURPLE_MID = (157, 78, 221, 255)     # #9D4EDD
PURPLE_LIGHT = (199, 125, 255, 255)  # #C77DFF
LAVENDER = (224, 204, 255, 255)      # #E0CCFF
WHITE = (255, 255, 255, 255)


def png_chunk(tag: bytes, data: bytes) -> bytes:
    length = struct.pack(">I", len(data))
    payload = tag + data
    crc = struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)
    return length + payload + crc


def write_png(path: str, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    h = len(pixels)
    w = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for rgba in row:
            raw.extend(rgba)
    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    png = sig + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", idat) + png_chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print(f"wrote {len(png)} bytes -> {path}")


def make_orb() -> list[list[tuple[int, int, int, int]]]:
    grid = [[BASE_BLACK for _ in range(16)] for _ in range(16)]
    for x, y in [(2, 3), (3, 2), (4, 3), (5, 4), (3, 5), (6, 2), (7, 5),
                 (9, 3), (10, 2), (11, 4), (12, 3), (13, 5),
                 (5, 6), (8, 7), (2, 8)]:
        grid[y][x] = PURPLE_DEEP
    for x, y in [(3, 3), (4, 2), (5, 3), (6, 4), (10, 3), (11, 2), (12, 4),
                 (6, 6), (9, 6), (3, 8)]:
        grid[y][x] = PURPLE_LIGHT
    for x, y in [(3, 10), (6, 11), (11, 10), (13, 12), (8, 13)]:
        grid[y][x] = PURPLE_MID
    return grid


def make_ring() -> list[list[tuple[int, int, int, int]]]:
    grid = [[LAVENDER for _ in range(16)] for _ in range(16)]
    for x in range(16):
        grid[0][x] = WHITE
        grid[15][x] = WHITE
        grid[4][x] = PURPLE_LIGHT
        grid[11][x] = PURPLE_LIGHT
        grid[7][x] = PURPLE_DEEP
        grid[8][x] = PURPLE_DEEP
    return grid


def main() -> None:
    write_png(os.path.join(TEX_DIR, "void_orb.png"), make_orb())
    write_png(os.path.join(TEX_DIR, "void_orb_ring.png"), make_ring())


if __name__ == "__main__":
    main()

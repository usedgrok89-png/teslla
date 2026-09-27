#!/usr/bin/env python3
"""Generates the legacy (pre-API 26) launcher icons as tiny PNGs. No third-party deps."""
import math
import os
import struct
import zlib

TEAL = (0x00, 0x89, 0x7B)
WHITE = (0xFF, 0xFF, 0xFF)
SS = 4  # supersampling factor


def write_png(path, w, h, rows):
    raw = bytearray()
    for row in rows:
        raw.append(0)
        raw.extend(row)

    def chunk(tag, data):
        body = tag + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    out += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    out += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(out)


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / L))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def render(size):
    S = float(size)
    cx, cy = 0.5 * S, 0.53 * S
    ring_r = 0.255 * S
    ring_w = 0.075 * S
    hand_w = 0.070 * S
    corner = 0.20 * S

    h1 = (cx, cy - 0.155 * S)
    h2 = (cx + 0.135 * S, cy + 0.055 * S)
    nub = (0.465 * S, 0.205 * S, 0.535 * S, 0.255 * S)

    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            acc = [0, 0, 0, 0]
            for sy in range(SS):
                for sx in range(SS):
                    fx = x + (sx + 0.5) / SS
                    fy = y + (sy + 0.5) / SS

                    dx = max(corner - fx, fx - (S - corner), 0.0)
                    dy = max(corner - fy, fy - (S - corner), 0.0)
                    inside = (dx == 0.0 and dy == 0.0) and math.hypot(dx, dy) <= corner

                    white = False
                    if inside:
                        d = abs(math.hypot(fx - cx, fy - cy) - ring_r)
                        if d <= ring_w / 2:
                            white = True
                        elif seg_dist(fx, fy, cx, cy, h1[0], h1[1]) <= hand_w / 2:
                            white = True
                        elif seg_dist(fx, fy, cx, cy, h2[0], h2[1]) <= hand_w / 2:
                            white = True
                        elif nub[0] <= fx <= nub[2] and nub[1] <= fy <= nub[3]:
                            white = True

                    c = WHITE if white else TEAL
                    acc[0] += c[0]
                    acc[1] += c[1]
                    acc[2] += c[2]
                    acc[3] += 255
            n = SS * SS
            row.extend(a // n for a in acc)
        rows.append(row)
    return rows


def main():
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for folder, size in (('mipmap-mdpi', 48), ('mipmap-hdpi', 72), ('mipmap-xhdpi', 96),
                         ('mipmap-xxhdpi', 144), ('mipmap-xxxhdpi', 192)):
        out_dir = os.path.join(here, 'res', folder)
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, 'ic_launcher.png')
        write_png(path, size, size, render(size))
        print('%-24s %5d bytes' % (folder + '/ic_launcher.png', os.path.getsize(path)))


if __name__ == '__main__':
    main()

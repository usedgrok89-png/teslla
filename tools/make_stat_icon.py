#!/usr/bin/env python3
"""Generates the notification small icon (white silhouette on transparent)."""
import math
import os
import struct
import zlib

SS = 4


def write_png(path, size, rows):
    raw = bytearray()
    for row in rows:
        raw.append(0)
        raw.extend(row)

    def chunk(tag, data):
        body = tag + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
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
    ring_r, ring_w, hand_w = 0.29 * S, 0.085 * S, 0.080 * S
    h1 = (cx, cy - 0.175 * S)
    h2 = (cx + 0.155 * S, cy + 0.062 * S)
    nub = (0.455 * S, 0.175 * S, 0.545 * S, 0.235 * S)

    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            acc_a = 0
            for sy in range(SS):
                for sx in range(SS):
                    fx = x + (sx + 0.5) / SS
                    fy = y + (sy + 0.5) / SS
                    on = False
                    if abs(math.hypot(fx - cx, fy - cy) - ring_r) <= ring_w / 2:
                        on = True
                    elif seg_dist(fx, fy, cx, cy, h1[0], h1[1]) <= hand_w / 2:
                        on = True
                    elif seg_dist(fx, fy, cx, cy, h2[0], h2[1]) <= hand_w / 2:
                        on = True
                    elif nub[0] <= fx <= nub[2] and nub[1] <= fy <= nub[3]:
                        on = True
                    acc_a += 255 if on else 0
            a = acc_a // (SS * SS)
            row.extend((255, 255, 255, a))
        rows.append(row)
    return rows


def main():
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(here, 'res', 'drawable-nodpi')
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, 'ic_stat.png')
    write_png(path, 48, render(48))
    print('drawable-nodpi/ic_stat.png  %d bytes' % os.path.getsize(path))


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Synthesises the notification chimes as small 16-bit PCM WAV files (stdlib only)."""
import math
import os
import struct
import zlib

RATE = 22050


def write_wav(path, samples):
    data = struct.pack('<%dh' % len(samples), *[max(-32767, min(32767, int(s * 32767))) for s in samples])
    hdr = b'RIFF' + struct.pack('<I', 36 + len(data)) + b'WAVE'
    hdr += b'fmt ' + struct.pack('<IHHIIHH', 16, 1, 1, RATE, RATE * 2, 2, 16)
    hdr += b'data' + struct.pack('<I', len(data))
    with open(path, 'wb') as f:
        f.write(hdr + data)


def note(buf, start, dur, freq, amp=0.5, harmonics=(1.0, 0.35, 0.14), decay=5.0):
    """Adds a struck-bell tone at `start` seconds into float buffer `buf`."""
    n0 = int(start * RATE)
    n = int(dur * RATE)
    for i in range(n):
        idx = n0 + i
        if idx >= len(buf):
            break
        t = i / RATE
        env = amp * math.exp(-decay * t) * min(1.0, t / 0.004)
        v = 0.0
        for k, h in enumerate(harmonics, start=1):
            v += h * math.sin(2 * math.pi * freq * k * t)
        buf[idx] += v * env


def render(duration, spec):
    buf = [0.0] * int(duration * RATE)
    for start, dur, freq, amp, harm, dec in spec:
        note(buf, start, dur, freq, amp, harm, dec)
    # soft saturation + fade-out tail
    n = len(buf)
    fade = int(0.05 * RATE)
    for i in range(n):
        v = math.tanh(buf[i] * 1.2)
        if i > n - fade:
            v *= (n - i) / fade
        buf[i] = v * 0.85
    return buf


def main():
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(here, 'res', 'raw')
    os.makedirs(out_dir, exist_ok=True)

    sounds = {
        # bright two-note chime (B5 -> E6) with a soft octave shimmer
        'ding.wav': (0.80, [
            (0.00, 0.55, 987.77, 0.42, (1.0, 0.30, 0.12, 0.05), 5.5),
            (0.16, 0.62, 1318.51, 0.36, (1.0, 0.26, 0.10, 0.04), 4.6),
            (0.17, 0.60, 1975.53, 0.10, (1.0, 0.20), 6.5),
        ]),
        # calm single low tone (D5) with a fifth above
        'soft.wav': (0.70, [
            (0.00, 0.68, 587.33, 0.44, (1.0, 0.18, 0.06), 4.2),
            (0.05, 0.60, 880.00, 0.14, (1.0, 0.15), 5.0),
        ]),
    }

    for name, (dur, spec) in sounds.items():
        path = os.path.join(out_dir, name)
        write_wav(path, render(dur, spec))
        print('res/raw/%-10s %6.1f KB' % (name, os.path.getsize(path) / 1024))


if __name__ == '__main__':
    main()

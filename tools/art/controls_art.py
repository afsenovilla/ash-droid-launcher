#!/usr/bin/env python3
"""Controls screen key icons with a handheld's buttons (PC port of launcher/ControlsArt.java).

Redraws Graphics/Pictures/Controls help/*.png of a Pokémon Essentials game: the arrow keys
become a D-pad, the USE/BACK/ACTION/SPECIAL keys get the given button names and F1/F8
(not available on a handheld) are hidden. The originals are untouched: images go to
OUT_DIR and their index lines ("any<TAB>graphics/pictures/...<TAB>file") are printed, in
the format of the runtime patch's art index.

  python3 tools/art/controls_art.py GAME_DIR OUT_DIR A,B,X,R3
"""
import os
import sys
from collections import Counter

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from relabel import load_fonts  # noqa: E402

FONT = load_fonts()["5x7"]
GW, GH = 5, 7


def glyph(ch):
    g = FONT["g"].get(ch.upper())
    if not g:
        return ["....."] * GH
    return [r.ljust(GW, ".")[:GW] for r in g[-GH:]]


def text_width(s, scale):
    return 0 if not s else (len(s) * (GW + 2) - 1) * scale


def fill(px, w, h, x, y, s, c):
    for yy in range(y, y + s):
        for xx in range(x, x + s):
            if 0 <= xx < w and 0 <= yy < h:
                px[xx, yy] = c


def draw_text(im, s, x, y, scale, color, shadow):
    px, w, h = im.load(), im.width, im.height
    for pas in ((0, 1) if shadow else (1,)):
        off = scale if pas == 0 else 0
        col = shadow if pas == 0 else color
        cx = x
        for ch in s:
            g = glyph(ch)
            for gy in range(GH):
                for gx in range(GW):
                    if g[gy][gx] == "#":
                        fill(px, w, h, cx + gx * scale + off, y + gy * scale + off, scale, col)
                        fill(px, w, h, cx + (gx + 1) * scale + off, y + gy * scale + off, scale, col)  # bold
            cx += (GW + 2) * scale


def classify(base):
    if "bg" in base or "background" in base:
        return None
    if "arrow" in base or "dir" in base:
        return "DPAD"
    for k in ("special", "action", "back", "use"):
        if k in base:
            return k.upper()
    import re
    if re.search(r"(\bf\d+|_f\d+|^f\d+)", base):
        return "HIDE"
    return None


def dist(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2])


def mode_color(colors, exclude=None, min_dist=0):
    cnt = Counter()
    for c in colors:
        if c[3] < 200:
            continue
        k = (c[0], c[1], c[2], 255)
        if exclude and dist(k, exclude) < min_dist:
            continue
        cnt[k] += 1
    return cnt.most_common(1)[0][0] if cnt else None


def pixel_scale(im):
    w, h = im.size
    if w % 2 or h % 2:
        return 1
    px = im.load()
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            c = px[x, y]
            if px[x + 1, y] != c or px[x, y + 1] != c or px[x + 1, y + 1] != c:
                return 1
    return 2


def main_label(label):
    main = label.split("/")[0].strip()
    return main.split(" ")[0] if " " in main else main


def relabel(full, label):
    """Keeps the game's key cap and replaces its text (see ControlsArt.relabel)."""
    scale = pixel_scale(full)
    orig = full if scale == 1 else full.resize((full.width // scale, full.height // scale), Image.NEAREST)
    w, h = orig.size
    bmp = orig.copy()
    px = bmp.load()
    opaque = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 200]
    if not opaque:
        return None
    minx = min(p[0] for p in opaque); maxx = max(p[0] for p in opaque)
    miny = min(p[1] for p in opaque); maxy = max(p[1] for p in opaque)
    capw, caph = maxx - minx + 1, maxy - miny + 1
    face = mode_color([px[x, y] for y in range(h) for x in range(w)])
    if not face:
        return None
    mx, mytop, mybot = 2, max(3, caph // 7), max(4, caph // 4)
    ix0, ix1, iy0, iy1 = minx + mx, maxx - mx, miny + mytop, maxy - mybot
    if ix1 <= ix0 or iy1 <= iy0:
        return None
    tminy, tmaxy, text_px = h, -1, []
    for y in range(iy0, iy1 + 1):
        for x in range(ix0, ix1 + 1):
            c = px[x, y]
            if c[3] > 200 and dist(c, face) > 60:
                text_px.append(c)
                tminy, tmaxy = min(tminy, y), max(tmaxy, y)
                px[x, y] = face
    text_color = mode_color(text_px) if text_px else (0x60, 0x60, 0x70, 255)
    shadow = mode_color(text_px, text_color, 40) if text_px else None
    text_h = (tmaxy - tminy + 1) if tmaxy >= tminy else (iy1 - iy0) * 2 // 3

    # Single buttons get a square key cap: keep both ends of the cap, drop the middle
    if len(label) <= 2 and capw > caph + 2:
        neww = max(caph, 8)
        half = neww // 2
        dx0 = minx + (capw - neww) // 2
        sq = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        for i in range(neww):
            sx = minx + i if i < half else maxx - (neww - 1 - i)
            sq.paste(bmp.crop((sx, 0, sx + 1, h)), (dx0 + i, 0))
        bmp = sq
        m = ix0 - minx
        ix0, ix1 = dx0 + m, dx0 + neww - 1 - m

    txt = label.upper()
    availw = ix1 - ix0 + 1
    ps = max(1, min((text_h + 1) // (GH + 1), (availw - 4) // max(1, text_width(txt, 1))))
    if text_width(txt, ps) > availw:
        ps = 1
    tw, th = text_width(txt, ps), GH * ps
    cy = (tminy + tmaxy + 1) // 2 if tmaxy >= tminy else (iy0 + iy1 + 1) // 2
    draw_text(bmp, txt, ix0 + (availw - tw) // 2, cy - th // 2, ps, text_color, shadow)
    return bmp if scale == 1 else bmp.resize((w * scale, h * scale), Image.NEAREST)


def rect(px, w, h, x0, y0, x1, y1, c):
    for y in range(max(0, y0), min(h, y1)):
        for x in range(max(0, x0), min(w, x1)):
            px[x, y] = c


def palette(im):
    """face (most common), hi (lighter), mid and ink (the next darker ones) of a key cap."""
    cnt = Counter((c[0], c[1], c[2], 255) for c in im.getdata() if c[3] > 200)
    tot = sum(cnt.values())
    face = cnt.most_common(1)[0][0]
    lum = lambda c: sum(c[:3])
    cols = sorted((c for c, n in cnt.items() if n * 100 >= tot), key=lambda c: (lum(c), c))
    hi = cols[-1] if lum(cols[-1]) > lum(face) else face
    below = [c for c in cols if lum(c) < lum(face)]
    mid = below[-1] if below else face
    ink = below[-2] if len(below) > 1 else mid
    return face, hi, mid, ink


def dpad(orig):
    """Arrow keys -> a D-pad drawn like the game's key caps (light top-left edge, darker
    bottom-right bevel, concave centre, arrows in the arrow colour). Pixel art at the
    art's own scale, then scaled up."""
    if not [c for c in orig.getdata() if c[3] > 200]:
        return None
    sc = pixel_scale(orig)
    small = orig if sc == 1 else orig.resize((orig.width // sc, orig.height // sc), Image.NEAREST)
    face, hi, mid, ink = palette(small)
    w, h = small.size
    s = min(w, h) - 1
    s -= 1 - s % 2
    a = s // 3
    a += 1 - a % 2
    x0, y0, c = (w - s) // 2, (h - s) // 2, s // 2

    def out(x, y):
        u, v = x - x0, y - y0
        if not (0 <= u < s and 0 <= v < s):
            return True
        return not (c - a // 2 <= u <= c + a // 2 or c - a // 2 <= v <= c + a // 2)

    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y in range(h):
        for x in range(w):
            if out(x, y):
                continue
            if (out(x - 1, y) or out(x + 1, y)) and (out(x, y - 1) or out(x, y + 1)):
                continue  # rounded arm ends
            col = face
            if out(x, y - 1) or out(x - 1, y):
                col = hi
            if out(x, y + 1) or out(x, y + 2) or out(x + 1, y) or out(x + 2, y):
                col = mid
            px[x, y] = col
    cx, cy = x0 + c, y0 + c
    r = max(2, a // 2 - 2) + 0.5
    for y in range(cy - 4, cy + 5):
        for x in range(cx - 4, cx + 5):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if (r - 1) ** 2 < d <= r * r:
                px[x, y] = mid if (x - cx) + (y - cy) < 0 else hi
    t = max(2, (a - 1) // 2 - 1)
    pos = c - (c - a // 2) // 2 - 1
    for dn in range(4):
        for i in range(t):
            o = pos + t // 2 - i  # i = 0: the tip, outermost
            for j in range(-i, i + 1):
                x, y = ((cx + j, cy - o), (cx + j, cy + o), (cx - o, cy + j), (cx + o, cy + j))[dn]
                px[x, y] = ink
    return im if sc == 1 else im.resize((w * sc, h * sc), Image.NEAREST)


def find_child(d, name):
    for n in os.listdir(d):
        if n.lower() == name.lower():
            return os.path.join(d, n)
    return None


def run(game, out, labels):
    g = find_child(game, "Graphics")
    pics = g and find_child(g, "Pictures")
    if not pics:
        return []
    ctl = next((os.path.join(pics, n) for n in sorted(os.listdir(pics))
                if "control" in n.lower() and os.path.isdir(os.path.join(pics, n))), None)
    if not ctl:
        return []
    os.makedirs(out, exist_ok=True)
    index = []
    rel_dir = os.path.relpath(ctl, game).replace(os.sep, "/")
    for name in sorted(os.listdir(ctl)):
        if not name.lower().endswith(".png"):
            continue
        base = name[:-4].lower()
        kind = classify(base)
        if not kind:
            continue
        orig = Image.open(os.path.join(ctl, name)).convert("RGBA")
        if kind == "DPAD":
            res = dpad(orig)
        elif kind == "HIDE":
            res = Image.new("RGBA", orig.size, (0, 0, 0, 0))
        else:
            res = relabel(orig, main_label(labels[kind]))
        if res is None:
            continue
        fn = "controls_" + base.replace(" ", "_") + ".png"
        res.save(os.path.join(out, fn))
        index.append("any\t%s\t%s" % ((rel_dir + "/" + name[:-4]).lower(), fn))
    return index


if __name__ == "__main__":
    game, out, b = sys.argv[1:4]
    labels = dict(zip(["USE", "BACK", "ACTION", "SPECIAL"], b.split(",")))
    for line in run(game, out, labels):
        print(line)

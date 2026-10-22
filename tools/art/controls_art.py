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


def dpad(orig):
    """Arrow keys -> D-pad in the key caps' own colours (see ControlsArt.restyleAsDpad)."""
    w, h = orig.size
    opx = orig.load()
    colors = [opx[x, y] for y in range(h) for x in range(w)]
    face = mode_color(colors)
    if not face:
        return None
    arrow = mode_color(colors, face, 60)
    cnt = Counter((c[0], c[1], c[2], 255) for c in colors if c[3] > 200)
    border, darkest = arrow, 765
    for k, n in cnt.items():
        if n > len(colors) // 200 and sum(k[:3]) < darkest:
            darkest, border = sum(k[:3]), k
    arrow = arrow or border
    bmp = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = bmp.load()
    s = min(w, h) - 2
    arm = max(5, (s // 3) | 1)
    half, cx, cy, a2 = s // 2, w // 2, h // 2, arm // 2
    rect(px, w, h, cx - a2 - 1, cy - half, cx + a2 + 2, cy + half + 2, border)
    rect(px, w, h, cx - half, cy - a2 - 1, cx + half + 2, cy + a2 + 2, border)
    rect(px, w, h, cx - a2, cy - half + 1, cx + a2 + 1, cy + half, face)
    rect(px, w, h, cx - half + 1, cy - a2, cx + half, cy + a2 + 1, face)
    t = max(2, arm // 4)
    d = half - arm // 2
    for (x, y, dr) in ((cx, cy - d, 0), (cx, cy + d, 2), (cx - d, cy, 3), (cx + d, cy, 1)):
        for i in range(t):
            if dr == 0:
                rect(px, w, h, x - i, y - t // 2 + i, x + i + 1, y - t // 2 + i + 1, arrow)
            elif dr == 2:
                rect(px, w, h, x - i, y + t // 2 - i, x + i + 1, y + t // 2 - i + 1, arrow)
            elif dr == 3:
                rect(px, w, h, x - t // 2 + i, y - i, x - t // 2 + i + 1, y + i + 1, arrow)
            else:
                rect(px, w, h, x + t // 2 - i, y - i, x + t // 2 - i + 1, y + i + 1, arrow)
    return bmp


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

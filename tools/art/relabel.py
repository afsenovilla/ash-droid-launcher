#!/usr/bin/env python3
"""Reference implementation (and preview tool) of the art relabelling engine.

The Android launcher (launcher/ArtPatcher.java) implements exactly the same
algorithm; this script exists to author and check recipes on a PC:

  python3 tools/art/relabel.py GAME_DIR app/src/main/assets/packs/fire-ash/art.json OUT_DIR [--buttons A,B,Y,X]

writes OUT_DIR/<name>.png for every recipe and OUT_DIR/_preview_<name>.png
(original above, result below, zoomed) so they can be inspected.

Recipe format:

{ "images": [ { "path": "Graphics/Pictures/Battle/overlay_lv", "lang": "es" | "any",
    "grid": 2, "offset": [0, 0], "dot": 0,
    "regions": [ { "rect": [x, y, w, h], "text": "NV" | "bitmap": ["##s", ...],
                   "font": "5x7" | ["type5", "type"], "bold": false, "spacing": 1,
                   "style": "shadow" | "outline" | "plain", "align": "left" | "center" | "right",
                   "fill": "#rrggbb", "corner": "#rrggbb", "fills": ["#..", ...], "shade": "#rrggbb", "height": 7,
                   "erase": "row" | "transparent" | "none", "bgx": x, "bg": "#rrggbb",
                   "dx": 0, "dy": 0 } ] } ] }

- Coordinates are in "art pixels": Essentials art is usually drawn with 2x2
  pixels, so with grid 2 every unit is a 2x2 block at offset + 2*(x, y).
- erase "row": each row of the rect becomes the colour found at column bgx
  (default: the rect's left column) on that row, or "bg" if given;
  "transparent": alpha 0.
- fill/shade default to the two most frequent non-background colours of the
  old lettering (outline style: the most frequent is the outline). "fills"
  colours the letters row by row (top to bottom, stretched), for gradients.
- Text is drawn with the shared pixel fonts (assets/pixelfonts.txt), rows
  stretched to the old letters' height; bold doubles vertical strokes.
  "spacing" is the preferred gap between letters, reduced if the text does
  not fit the rect. {USE} {BACK} {ACTION} {SPECIAL} are replaced by the
  device's button names.
- "bitmap" draws hand-made lettering instead: '#' = fill, 's' = shade.
- "image": a picture the *user* put in the game folder (e.g. official Spanish
  art, "assets_E/Tipo_lucha_E.png"; matched ignoring case and accents). If it
  exists it is pasted over "imageRect" (default: rect) and the text is
  skipped; otherwise the text is drawn as usual ("onlyImage": do nothing).
  Nothing like this is shipped.
- Per-language variants: entries with "lang" "es", "fr", "de"… make that
  language's image (plus the "any" entries, in order); "any" alone makes the image
  used in every other language.
- "anchor": "rect" places text at the rect itself instead of the old letters.
- "sdx"/"sdy": shadow offset (default 1, 1).
- erase "lerp": each row blends from the colour at bgx to the one at "bgx2";
  erase "inpaint" with "bgset": every colour not close to one of those is ink.
- "ttf": smooth lettering with the game's own Fonts/<ttf>.ttf ("cap": capital
  height in art pixels, "fat": extra stroke, "align": "left").
- "tips"/"rin"/"layers"/"blur": star-burst badge drawn from polygons.
- "lines"/"tops": pixel-font lines with a black outline and a soft "halo".
ArtEngine.java (Android) and ashdroid_art.rb (PC) implement the same arithmetic;
only TrueType rasterisation (ttf_raster) is done by each platform.
"""
import json, math, os, sys, unicodedata
from collections import Counter
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "pixelfonts.txt")


def load_fonts(path=FONTS):
    fonts, cur, ch, rows = {}, None, None, []
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.startswith("//") or not line:
            continue
        if line.startswith("@font"):
            _, name, w, h = line.split()
            cur = {"w": int(w), "h": int(h), "g": {}}
            fonts[name] = cur
        elif line.startswith("char:"):
            ch = line[5:]
            cur["g"][ch] = []
        else:
            cur["g"][ch].append(line)
    return fonts


ACCENTS = str.maketrans("ÁÉÍÓÚÜÑáéíóúüñ", "AEIOUUNaeiouun")


def hexcol(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


class Canvas:
    """Image seen in art pixels (grid x grid blocks at an offset)."""

    def __init__(self, im, grid=1, offset=(0, 0), dot=0):
        self.im, self.px, self.g, self.o = im, im.load(), grid, offset
        self.dot = dot  # dot-matrix art: each cell is a dot x dot lamp, rest is gap
        self.W = (im.width - offset[0]) // grid
        self.H = (im.height - offset[1]) // grid

    def get(self, x, y):
        if 0 <= x < self.W and 0 <= y < self.H:
            return self.px[self.o[0] + x * self.g, self.o[1] + y * self.g]
        return (0, 0, 0, 0)

    def put(self, x, y, c):
        if 0 <= x < self.W and 0 <= y < self.H:
            for yy in range(self.g):
                for xx in range(self.g):
                    lamp = not self.dot or (xx < self.dot and yy < self.dot)
                    X, Y = self.o[0] + x * self.g + xx, self.o[1] + y * self.g + yy
                    if X < self.im.width and Y < self.im.height:
                        self.px[X, Y] = c if lamp else self.gap

    gap = (0, 0, 0, 255)


def norm(name):
    return unicodedata.normalize("NFD", name).encode("ascii", "ignore").decode().lower()


def find_user_file(game, rel):
    cur = game
    for part in rel.split("/"):
        try:
            names = os.listdir(cur)
        except OSError:
            return None
        hit = [n for n in names if norm(n) == norm(part)]
        if not hit:
            return None
        cur = os.path.join(cur, hit[0])
    return cur if os.path.isfile(cur) else None


def paste_image(cv, src, rect, replace=False):
    x, y, w, h = rect
    im = Image.open(src).convert("RGBA")
    p = im.load()
    for yy in range(h):
        for xx in range(w):
            cv.put(x + xx, y + yy, p[xx * im.width // w, yy * im.height // h])


def apply_region(cv, r, fonts, buttons, game=None):
    if r.get("tips"):
        starburst(cv, r)
        return
    if r.get("lines"):
        draw_halo(cv, r, fonts, buttons)
        return
    if r.get("image") and game:
        src = find_user_file(game, r["image"])
        if src:
            paste_image(cv, src, r.get("imageRect", r["rect"]))
            return
    if r.get("onlyImage"):
        return
    x, y, w, h = r["rect"]
    style = r.get("style", "shadow")
    erase = r.get("erase", "row")
    bgx = r.get("bgx", x)
    rowbg = {}
    for yy in range(y, y + h):
        if erase == "transparent":
            rowbg[yy] = (0, 0, 0, 0)
        elif r.get("bg"):
            rowbg[yy] = hexcol(r["bg"])
        else:
            rowbg[yy] = cv.get(bgx, yy)
    cnt = Counter()
    tx0 = ty0 = 10 ** 9
    tx1 = ty1 = -1
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            c = cv.get(xx, yy)
            if c != rowbg[yy] and c[3] > 0:
                cnt[c] += 1
                tx0, tx1, ty0, ty1 = min(tx0, xx), max(tx1, xx), min(ty0, yy), max(ty1, yy)
    common = [c for c, _ in cnt.most_common(2)]
    if style == "outline":
        shade = common[0] if common else (64, 64, 64, 255)
        fill = common[1] if len(common) > 1 else (248, 248, 248, 255)
    else:
        fill = common[0] if common else (64, 64, 64, 255)
        shade = common[1] if len(common) > 1 else None
    if r.get("fill"):
        fill = hexcol(r["fill"])
        if style != "outline":
            rest = [c for c, _ in cnt.most_common() if c[:3] != fill[:3]]
            shade = rest[0] if rest else None
    if r.get("shade"):
        shade = hexcol(r["shade"])
    fills = [hexcol(c) for c in r.get("fills", [])]
    if ty1 < 0 or r.get("anchor") == "rect":
        ty0, ty1, tx0 = y, y + h - 1, x
    if erase == "inpaint":
        # Remove only the old lettering ("ink" colours): each such pixel takes the
        # colour of the nearest non-ink pixel on its row (keeps stripes/circles).
        ink = {hexcol(c)[:3] for c in r.get("ink", [])}
        for yy in range(y, y + h):
            row = [cv.get(xx, yy) for xx in range(x - w, x + 2 * w)]
            if r.get("bgset"):
                # Anything not close to one of the background colours is old lettering.
                keep = [hexcol(c) for c in r["bgset"]]
                is_ink = [all(max(abs(c[i] - k[i]) for i in range(3)) > 6 for k in keep) for c in row]
            else:
                is_ink = [c[:3] in ink for c in row]
            for i in range(w, 2 * w):
                if not is_ink[i]:
                    continue
                for dd in range(1, w + 1):
                    if i - dd >= 0 and not is_ink[i - dd]:
                        cv.put(x + i - w, yy, row[i - dd]); break
                    if i + dd < len(row) and not is_ink[i + dd]:
                        cv.put(x + i - w, yy, row[i + dd]); break
    elif erase == "lerp":
        # Gradient backgrounds: each row blends from the colour at bgx to the
        # colour at bgx2 across the rect.
        bx2 = r["bgx2"]
        for yy in range(y, y + h):
            a, b = cv.get(bgx, yy), cv.get(bx2, yy)
            for xx in range(x, x + w):
                t = min(1.0, max(0.0, (xx - bgx) / float(bx2 - bgx)))
                cv.put(xx, yy, tuple(int(math.floor(a[i] + (b[i] - a[i]) * t + 0.5)) for i in range(4)))
    elif erase != "none":
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                cv.put(xx, yy, rowbg[yy])

    if "ttf" in r:
        draw_ttf(cv, r, buttons, game, fill, shade)
        return
    if "bitmap" in r:
        ox, oy = x + r.get("dx", 0), y + r.get("dy", 0)
        for j, row in enumerate(r["bitmap"]):
            for i, ch in enumerate(row):
                if ch == "#":
                    cv.put(ox + i, oy + j, fill)
                elif ch == "s" and shade:
                    cv.put(ox + i, oy + j, shade)
        return

    text = r.get("text", "")
    for k, v in buttons.items():
        text = text.replace("{" + k + "}", v)
    raw = text
    names = r.get("font", "5x7")
    names = names if isinstance(names, list) else [names]
    bold = 1 if r.get("bold") else 0
    extra = 1 if style == "shadow" and shade else (2 if style == "outline" else 0)
    height = r.get("height") or max(1, ty1 - ty0 + 1 - extra)
    # First font (in order) whose glyphs cover the text and fit at the preferred
    # spacing; the last one is squeezed if nothing fits.
    for fi, name in enumerate(names):
        font = fonts[name]
        blank = ["." * font["w"]] * font["h"]
        text = "".join(pick_char(font, ch) for ch in raw)
        glyphs = [font["g"].get(ch) or blank for ch in text]
        widths = [len(g[0]) + bold for g in glyphs]
        spacing = r.get("spacing", 1)
        tw = sum(widths) + spacing * (len(text) - 1) + extra
        covered = all(ch in font["g"] or ch == " " for ch in text)
        if fi == len(names) - 1 or (covered and tw <= w):
            break
    while True:
        tw = sum(widths) + spacing * (len(text) - 1) + extra
        if tw <= w or spacing <= 0:
            break
        spacing -= 1
    if spacing < r.get("spacing", 1):
        print("  note: %r squeezed to spacing %d" % (text, spacing))
    align = r.get("align", "center")
    if align == "left":
        ox = tx0 if tx1 >= 0 else x
    elif align == "right":
        ox = x + w - tw
    else:
        ox = x + (w - tw) // 2
    ox += r.get("dx", 0)
    oy = ty0 + r.get("dy", 0)
    gx0 = ox + (1 if style == "outline" else 0)
    gy0 = oy + (1 if style == "outline" else 0)

    corner = hexcol(r["corner"]) if r.get("corner") else None
    for pass_ in ((-1, 0, 1) if corner and style == "outline" else (0, 1)):
        if pass_ == 0 and (style == "plain" or not shade):
            continue
        cx = gx0
        for g, gw in zip(glyphs, widths):
            top = len(g) - font["h"]  # accent rows drawn above the line, unscaled
            for ty in range(-top, height):
                gy = top + ty if ty < 0 else top + (ty * font["h"] + font["h"] // 2) // height
                col = fills[max(0, ty) * len(fills) // height] if fills else fill
                for gx in range(len(g[gy])):
                    if g[gy][gx] != "#":
                        continue
                    for b in range(bold + 1):
                        X, Y = cx + gx + b, gy0 + ty
                        if pass_ == 1:
                            cv.put(X, Y, col)
                        elif pass_ == -1:  # diagonal-only outline pixels
                            for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                                cv.put(X + dx, Y + dy, corner)
                        elif style == "shadow":
                            cv.put(X + r.get("sdx", 1), Y + r.get("sdy", 1), shade)
                        elif corner:
                            for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
                                cv.put(X + dx, Y + dy, shade)
                        else:
                            for dy in (-1, 0, 1):
                                for dx in (-1, 0, 1):
                                    cv.put(X + dx, Y + dy, shade)
            cx += gw + spacing



# ---------------------------------------------------------------- shared maths
# Everything below is plain pixel arithmetic, ported line by line to
# ArtEngine.java and ashdroid_art.rb, so the three give the same pixels. The only
# platform-specific step is rasterising TrueType text (ttf_raster).

def area_resample(src, sw, sh, dw, dh):
    """Box (area-average) resample of a premultiplied RGBA float list."""
    out = [[0.0, 0.0, 0.0, 0.0] for _ in range(dw * dh)]
    fx, fy = sw / float(dw), sh / float(dh)
    for dy in range(dh):
        y0, y1 = dy * fy, (dy + 1) * fy
        for dx in range(dw):
            x0, x1 = dx * fx, (dx + 1) * fx
            acc = [0.0, 0.0, 0.0, 0.0]
            yy = int(y0)
            while yy < y1 and yy < sh:
                wy = min(y1, yy + 1) - max(y0, yy)
                xx = int(x0)
                while xx < x1 and xx < sw:
                    wgt = wy * (min(x1, xx + 1) - max(x0, xx))
                    p = src[yy * sw + xx]
                    for i in range(4):
                        acc[i] += p[i] * wgt
                    xx += 1
                yy += 1
            area = fx * fy
            out[dy * dw + dx] = [v / area for v in acc]
    return out


def _lanczos(x):
    if x == 0:
        return 1.0
    if -3.0 < x < 3.0:
        px = math.pi * x
        return 3.0 * math.sin(px) * math.sin(px / 3.0) / (px * px)
    return 0.0


def _lanczos_weights(sn, dn):
    """Per output index: (first source index, normalised weights), as Pillow's
    resample with filter LANCZOS (support 3, widened by the scale when shrinking)."""
    scale = sn / float(dn)
    fs = max(scale, 1.0)
    support = 3.0 * fs
    res = []
    for i in range(dn):
        centre = (i + 0.5) * scale
        lo = max(0, int(centre - support + 0.5))
        hi = min(sn, int(centre + support + 0.5))
        ws = [_lanczos((j - centre + 0.5) / fs) for j in range(lo, hi)]
        t = sum(ws)
        res.append((lo, [v / t for v in ws] if t else ws))
    return res


def lanczos_resample(src, sw, sh, dw, dh):
    """Separable Lanczos-3 resample of a premultiplied RGBA float list (horizontal
    pass, then vertical), results clamped to 0..1."""
    tmp = src
    if dw != sw:
        wx = _lanczos_weights(sw, dw)
        tmp = []
        for y in range(sh):
            row = src[y * sw:(y + 1) * sw]
            for lo, ws in wx:
                acc = [0.0, 0.0, 0.0, 0.0]
                for k, wgt in enumerate(ws):
                    p = row[lo + k]
                    for c in range(4):
                        acc[c] += p[c] * wgt
                tmp.append(acc)
    if dh == sh:
        out = tmp
    else:
        wy = _lanczos_weights(sh, dh)
        out = []
        for lo, ws in wy:
            for x in range(dw):
                acc = [0.0, 0.0, 0.0, 0.0]
                for k, wgt in enumerate(ws):
                    p = tmp[(lo + k) * dw + x]
                    for c in range(4):
                        acc[c] += p[c] * wgt
                out.append(acc)
    for p in out:
        for c in range(4):
            p[c] = min(1.0, max(0.0, p[c]))
    return out


def dilate(mask, w, h, rad):
    """Max filter with a square (2*rad+1) window over a 0..1 float mask."""
    if rad <= 0:
        return mask
    tmp = [0.0] * (w * h)
    for y in range(h):
        for x in range(w):
            tmp[y * w + x] = max(mask[y * w + xx] for xx in range(max(0, x - rad), min(w, x + rad + 1)))
    out = [0.0] * (w * h)
    for y in range(h):
        for x in range(w):
            out[y * w + x] = max(tmp[yy * w + x] for yy in range(max(0, y - rad), min(h, y + rad + 1)))
    return out


def dilate_disc(mask, w, h, rad):
    """Max filter with a round window (thickens strokes like a pen of radius rad)."""
    if rad <= 0:
        return mask
    offs = [(dx, dy) for dy in range(-rad, rad + 1) for dx in range(-rad, rad + 1) if dx * dx + dy * dy <= rad * rad]
    out = [0.0] * (w * h)
    for y in range(h):
        for x in range(w):
            m = 0.0
            for dx, dy in offs:
                X, Y = x + dx, y + dy
                if 0 <= X < w and 0 <= Y < h and mask[Y * w + X] > m:
                    m = mask[Y * w + X]
            out[y * w + x] = m
    return out


def gauss_kernel(sigma):
    rad = max(1, int(sigma * 3 + 0.999))
    k = [math.exp(-(i * i) / (2.0 * sigma * sigma)) for i in range(-rad, rad + 1)]
    t = sum(k)
    return [v / t for v in k], rad


def blur(chans, w, h, sigma):
    """Separable gaussian blur of a list of float channels (edges clamped)."""
    if sigma <= 0:
        return chans
    k, rad = gauss_kernel(sigma)
    res = []
    for ch in chans:
        tmp = [0.0] * (w * h)
        for y in range(h):
            for x in range(w):
                a = 0.0
                for i in range(-rad, rad + 1):
                    a += ch[y * w + min(w - 1, max(0, x + i))] * k[i + rad]
                tmp[y * w + x] = a
        out = [0.0] * (w * h)
        for y in range(h):
            for x in range(w):
                a = 0.0
                for i in range(-rad, rad + 1):
                    a += tmp[min(h - 1, max(0, y + i)) * w + x] * k[i + rad]
                out[y * w + x] = a
        res.append(out)
    return res


def blend_over(cv, x, y, p):
    """Draws a premultiplied float RGBA pixel over the canvas."""
    a = p[3]
    if a <= 0.0:
        return
    if a > 1.0:
        a = 1.0
    u = cv.get(x, y)
    ua = u[3] / 255.0
    oa = a + ua * (1 - a)
    if oa <= 0:
        return
    col = [(p[i] * 255.0 + u[i] * ua * (1 - a)) / oa for i in range(3)]
    cv.put(x, y, tuple(int(min(255, max(0, c)) + 0.5) for c in col) + (int(oa * 255 + 0.5),))


def ttf_raster(game, name, size, text):
    """PLATFORM STEP. Alpha mask (0..1 floats) of `text` in font Fonts/<name>.ttf at
    pixel size `size`, drawn from origin (0, 0) on a canvas wide enough for it.
    Returns (w, h, mask). Same origin for every text at a given size, so masks of
    different texts share the baseline."""
    from PIL import ImageDraw, ImageFont
    path = find_user_file(game, "Fonts/" + name + ".ttf") if game else None
    if not path:
        return None
    font = ImageFont.truetype(path, size)
    w = int(size * 0.8 * max(1, len(text))) + size * 2
    h = size * 2
    im = Image.new("L", (w, h), 0)
    ImageDraw.Draw(im).text((size // 2, size // 4), text, font=font, fill=255)
    return w, h, [v / 255.0 for v in im.getdata()]


def ink_box(w, h, mask):
    xs = ys = None
    x0, y0, x1, y1 = w, h, -1, -1
    for y in range(h):
        for x in range(w):
            if mask[y * w + x] > 0.0:
                x0, x1, y0, y1 = min(x0, x), max(x1, x), min(y0, y), max(y1, y)
    return (x0, y0, x1, y1) if x1 >= 0 else None


TTF_K = 4


def draw_ttf(cv, r, buttons, game, fill, shade):
    """Smooth lettering with one of the game's own TrueType fonts (Fonts/<ttf>.ttf).
    Drawn TTF_K times larger (capital height "cap" art pixels), thickened by "fat",
    with a 1 px (art) drop shadow, squeezed horizontally if it does not fit the rect,
    then area-averaged down so the edges are anti-aliased like the original art."""
    K = TTF_K
    text = r.get("text", "")
    for k, v in buttons.items():
        text = text.replace("{" + k + "}", v)
    if not text:
        return
    cap = r.get("cap", r["rect"][3] - 2)
    size, hb = None, None
    for s in range(int(cap * K * 2), 5, -1):
        ras = ttf_raster(game, r["ttf"], s, "H")
        if ras is None:
            return
        b = ink_box(*ras)
        if b and b[3] - b[1] + 1 <= cap * K:
            size, hb = s, b
            break
    if size is None:
        return
    tw_, th_, tm = ttf_raster(game, r["ttf"], size, text)
    tb = ink_box(tw_, th_, tm)
    if tb is None:
        return
    fat = int(r.get("fat", 0) * K)
    x, y, w, h = r["rect"]
    gw = tb[2] - tb[0] + 1 + 2 * fat
    BW, BH = gw + K, h * K + 2 * K
    oy = (h * K - (hb[3] - hb[1] + 1)) // 2 - hb[1] + r.get("dy", 0) * K
    glyph = [0.0] * (BW * BH)
    for yy in range(th_):
        Y = yy + oy
        if not 0 <= Y < BH:
            continue
        for xx in range(tb[0], tb[2] + 1):
            v = tm[yy * tw_ + xx]
            if v > 0:
                glyph[Y * BW + xx - tb[0] + fat] = v
    sh = [0.0] * (BW * BH)
    if shade and style_of(r) != "plain":
        for yy in range(BH - K):
            for xx in range(BW - K):
                sh[(yy + K) * BW + xx + K] = glyph[yy * BW + xx]
    glyph = dilate_disc(glyph, BW, BH, fat)
    big = []
    for i in range(BW * BH):
        fa, sa = glyph[i], sh[i] * (1 - glyph[i])
        big.append([fill[0] / 255.0 * fa + (shade[0] / 255.0 * sa if sa else 0),
                    fill[1] / 255.0 * fa + (shade[1] / 255.0 * sa if sa else 0),
                    fill[2] / 255.0 * fa + (shade[2] / 255.0 * sa if sa else 0), fa + sa])
    maxw = (w - 2) * K
    if BW > maxw:
        big = lanczos_resample(big, BW, BH, maxw, BH)
        BW = maxw
    dw, dh = max(1, BW // K), max(1, BH // K)
    small = lanczos_resample(big, BW, BH, dw, dh)
    ox = (x + 1 if r.get("align") == "left" else x + (w - dw) // 2) + r.get("dx", 0)
    for yy in range(dh):
        for xx in range(dw):
            blend_over(cv, ox + xx, y + yy, small[yy * dw + xx])


# ------------------------------------------------------------ star-burst badge

def starburst(cv, r):
    """Explosion star (Dynamax "hard mode" badge): nested polygons from the tips in
    "tips" (art pixels) and an inner radius "rin", one per layer [scale, colour],
    drawn TTF_K-times larger, colours softened by a gaussian blur ("blur", art px),
    then area-averaged down. Replaces the rect entirely."""
    K = TTF_K
    x, y, w, h = r["rect"]
    cx, cy = r.get("centre", [w / 2.0, h / 2.0])
    tips = sorted(r["tips"], key=lambda t: math.atan2(t[1] - cy, t[0] - cx))
    rin = r["rin"]
    W, H = w * K, h * K
    layers = []
    for sc, col in r["layers"]:
        pts = []
        for i, t in enumerate(tips):
            n = tips[(i + 1) % len(tips)]
            a1, a2 = math.atan2(t[1] - cy, t[0] - cx), math.atan2(n[1] - cy, n[0] - cx)
            if a2 < a1:
                a2 += 2 * math.pi
            am = (a1 + a2) / 2
            pts.append(((cx + (t[0] - cx) * sc) * K, (cy + (t[1] - cy) * sc) * K))
            pts.append(((cx + rin * sc * math.cos(am)) * K, (cy + rin * sc * math.sin(am)) * K))
        layers.append((pts, hexcol(col)))
    R, G, B, A = [0.0] * (W * H), [0.0] * (W * H), [0.0] * (W * H), [0.0] * (W * H)
    base = layers[0][1]
    for i in range(W * H):
        R[i], G[i], B[i] = base[0] / 255.0, base[1] / 255.0, base[2] / 255.0
    for li, (pts, col) in enumerate(layers):
        n = len(pts)
        for yy in range(H):
            py = yy + 0.5
            xs = []
            for i in range(n):
                (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % n]
                if (y1 <= py) != (y2 <= py):
                    xs.append(x1 + (py - y1) * (x2 - x1) / (y2 - y1))
            xs.sort()
            for j in range(0, len(xs) - 1, 2):
                a = max(0, int(math.ceil(xs[j] - 0.5)))
                b = min(W - 1, int(math.ceil(xs[j + 1] - 0.5)) - 1)
                for xx in range(a, b + 1):
                    k = yy * W + xx
                    R[k], G[k], B[k] = col[0] / 255.0, col[1] / 255.0, col[2] / 255.0
                    if li == 0:
                        A[k] = 1.0
    R, G, B = blur([R, G, B], W, H, r.get("blur", 0) * K)
    big = [[R[i] * A[i], G[i] * A[i], B[i] * A[i], A[i]] for i in range(W * H)]
    small = lanczos_resample(big, W, H, w, h)
    for yy in range(h):
        for xx in range(w):
            p = small[yy * w + xx]
            a = p[3]
            if a <= 0:
                cv.put(x + xx, y + yy, (0, 0, 0, 0))
            else:
                cv.put(x + xx, y + yy, tuple(int(min(255, p[i] / a * 255) + 0.5) for i in range(3)) + (int(a * 255 + 0.5),))


def draw_halo(cv, r, fonts, buttons):
    """Pixel-font lettering with a black 1 px outline and a soft dark halo around it
    ("halo" colour, "halo_blur" sigma), each letter's right-most column of a stroke
    in "rcol" (inner shading). Lines in "lines", one per row top ["tops"], centred."""
    x, y, w, h = r["rect"]
    font = fonts[r.get("font", "card")]
    fill, rcol = hexcol(r.get("fill", "#ffffff")), hexcol(r.get("rcol", r.get("fill", "#ffffff")))
    col = {}
    mask = [0.0] * (w * h)
    for line, top in zip(r["lines"], r["tops"]):
        for k, v in buttons.items():
            line = line.replace("{" + k + "}", v)
        glyphs = [font["g"][pick_char(font, ch)] for ch in line]
        widths = [max(len(rw) for rw in g) for g in glyphs]
        gx = (w - (sum(widths) + r.get("spacing", 1) * (len(widths) - 1))) // 2
        for g, gw in zip(glyphs, widths):
            y0 = top - (len(g) - font["h"])
            for dy, row in enumerate(g):
                for dx, c in enumerate(row):
                    if c == "#" and 0 <= gx + dx < w and 0 <= y0 + dy < h:
                        right = dx + 1 < len(row) and row[dx + 1] == "#"
                        col[(gx + dx, y0 + dy)] = fill if right else rcol
                        mask[(y0 + dy) * w + gx + dx] = 1.0
            gx += gw + r.get("spacing", 1)
    halo = blur([dilate(mask, w, h, 2)], w, h, r.get("halo_blur", 0.8))[0]
    edge = dilate(mask, w, h, 1)
    hc = hexcol(r.get("halo", "#191919"))
    for yy in range(h):
        for xx in range(w):
            a = min(1.0, halo[yy * w + xx])
            blend_over(cv, x + xx, y + yy, [hc[0] / 255.0 * a, hc[1] / 255.0 * a, hc[2] / 255.0 * a, a])
            if edge[yy * w + xx] > 0:
                cv.put(x + xx, y + yy, (0, 0, 0, 255))
            if (xx, yy) in col:
                cv.put(x + xx, y + yy, col[(xx, yy)])


def style_of(r):
    return r.get("style", "shadow")


def pick_char(font, ch):
    """The glyph to use for ch: itself, its uppercase, then without accent."""
    for c in (ch, ch.upper(), ch.translate(ACCENTS), ch.translate(ACCENTS).upper()):
        if c in font["g"]:
            return c
    return ch.translate(ACCENTS).upper()


def find_image(game, path):
    cur = game
    parts = path.split("/")
    for i, p in enumerate(parts):
        names = os.listdir(cur)
        want = (p + ".png").lower() if i == len(parts) - 1 else p.lower()
        hit = [n for n in names if n.lower() == want]
        if not hit:
            return None
        cur = os.path.join(cur, hit[0])
    return cur


def out_name(path):
    return path.replace("Graphics/Pictures/", "").replace("/", "_").replace(" ", "_").lower() + ".png"


def variants(recipes):
    """(path, lang, entries) per output image. A Spanish ("es") image gets every
    entry for its path in order; the "any" image (shown in any language) only
    the "any" entries, so e.g. button names change even when English is used."""
    paths = []
    for img in recipes["images"]:
        if img["path"] not in paths:
            paths.append(img["path"])
    for path in paths:
        entries = [i for i in recipes["images"] if i["path"] == path]
        langs = {i.get("lang", "es") for i in entries}
        for lang in [l for l in ("es", "fr", "de", "it") if l in langs] + sorted(langs - {"es", "fr", "de", "it", "any"}):
            yield path, lang, [i for i in entries if i.get("lang", "es") in (lang, "any")]
        if "any" in langs:
            yield path, "any", [i for i in entries if i.get("lang", "es") == "any"]


def out_file(path, lang):
    return ("" if lang == "es" else lang + "_") + out_name(path)


def run(game, recipes, out, buttons):
    fonts = load_fonts()
    os.makedirs(out, exist_ok=True)
    index = []
    for path, lang, entries in variants(recipes):
        src = find_image(game, path)
        if not src:
            print("missing", path)
            continue
        orig = Image.open(src).convert("RGBA")
        res = orig.copy()
        for img in entries:
            cv = Canvas(res, img.get("grid", 1), tuple(img.get("offset", (0, 0))), img.get("dot", 0))
            for r in img["regions"]:
                apply_region(cv, r, fonts, buttons, game)
        name = out_file(path, lang)
        res.save(os.path.join(out, name))
        index.append("%s\t%s\t%s" % (lang, path.lower(), name))
        z = max(1, min(6, 900 // max(1, orig.width)))
        prev = Image.new("RGBA", (orig.width, orig.height * 2 + 4), (255, 0, 255, 255))
        prev.alpha_composite(orig, (0, 0))
        prev.alpha_composite(res, (0, orig.height + 4))
        prev.resize((prev.width * z, prev.height * z), Image.NEAREST).save(os.path.join(out, "_preview_" + name))
        print("ok", lang, path)
    with open(os.path.join(out, "index.txt"), "w") as f:
        f.write("\n".join(index) + "\n")


if __name__ == "__main__":
    game, rec, out = sys.argv[1:4]
    buttons = {"USE": "A", "BACK": "B", "ACTION": "Y", "SPECIAL": "X"}
    if len(sys.argv) > 5 and sys.argv[4] == "--buttons":
        b = sys.argv[5].split(",")
        buttons = dict(zip(["USE", "BACK", "ACTION", "SPECIAL"], b))
    run(game, json.load(open(rec, encoding="utf-8")), out, buttons)

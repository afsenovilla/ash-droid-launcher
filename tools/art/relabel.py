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
"""
import json, os, sys, unicodedata
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


def paste_image(cv, src, rect):
    x, y, w, h = rect
    im = Image.open(src).convert("RGBA")
    p = im.load()
    for yy in range(h):
        for xx in range(w):
            cv.put(x + xx, y + yy, p[xx * im.width // w, yy * im.height // h])


def apply_region(cv, r, fonts, buttons, game=None):
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
    if ty1 < 0:
        ty0, ty1, tx0 = y, y + h - 1, x
    if erase == "inpaint":
        # Remove only the old lettering ("ink" colours): each such pixel takes the
        # colour of the nearest non-ink pixel on its row (keeps stripes/circles).
        ink = {hexcol(c)[:3] for c in r.get("ink", [])}
        for yy in range(y, y + h):
            row = [cv.get(xx, yy) for xx in range(x - w, x + 2 * w)]
            is_ink = [c[:3] in ink for c in row]
            for i in range(w, 2 * w):
                if not is_ink[i]:
                    continue
                for dd in range(1, w + 1):
                    if i - dd >= 0 and not is_ink[i - dd]:
                        cv.put(x + i - w, yy, row[i - dd]); break
                    if i + dd < len(row) and not is_ink[i + dd]:
                        cv.put(x + i - w, yy, row[i + dd]); break
    elif erase != "none":
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                cv.put(xx, yy, rowbg[yy])

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
            for ty in range(height):
                gy = (ty * font["h"] + font["h"] // 2) // height
                col = fills[ty * len(fills) // height] if fills else fill
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
                            cv.put(X + 1, Y + 1, shade)
                        elif corner:
                            for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
                                cv.put(X + dx, Y + dy, shade)
                        else:
                            for dy in (-1, 0, 1):
                                for dx in (-1, 0, 1):
                                    cv.put(X + dx, Y + dy, shade)
            cx += gw + spacing


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
        if "es" in langs:
            yield path, "es", entries
        if "any" in langs:
            yield path, "any", [i for i in entries if i.get("lang", "es") == "any"]


def out_file(path, lang):
    return ("any_" if lang == "any" else "") + out_name(path)


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

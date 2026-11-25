# Second-screen (dual-screen handhelds) battle menu design, approved 2026-11-25: buttons drawn by code in
# Fire Ash's palette and lettering. Usage: ds_mock.py <pixelfonts.txt> <game font .ttf> <out.png>  (also writes out.gif
# with the bounce of the selected option). The Java implementation must match this layout and these rules.
import sys
from PIL import Image, ImageDraw, ImageFont
FONTS_TXT, TTF = sys.argv[1], sys.argv[2]
S, AW, AH = 6, 206, 180

def load_font(name):
    glyphs, cur, on = {}, None, False
    for line in open(FONTS_TXT, encoding='utf-8'):
        line = line.rstrip('\n')
        if line.startswith('@font'): on = line.split()[1] == name; cur = None; continue
        if not on or line.startswith('//'): continue
        if line.startswith('char:'): cur = line[5:]; glyphs[cur] = []; continue
        if cur is not None and line: glyphs[cur].append(line)
    return glyphs
CMD = load_font('cmd')
def glyph(ch): return CMD.get(ch) or CMD.get(ch.upper()) or ['....'] * 9
def text_w(t, k=1): return sum(len(glyph(ch)[0]) + 1 for ch in t) * k - k
def pix_text(img, t, cx, cy, k=1, ink=(255, 255, 255), edge=(48, 48, 64)):
    w, h = text_w(t, k), 9 * k
    layer = Image.new('L', (w + 2, h + 6 * k), 0); d = ImageDraw.Draw(layer); x = 1
    for ch in t:
        g = glyph(ch); top = 3 * k - (len(g) - 9) * k
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == '#': d.rectangle((x + c*k, top + r*k, x + c*k + k - 1, top + r*k + k - 1), fill=255)
        x += (len(g[0]) + 1) * k
    ox, oy = int(cx - layer.width / 2), int(cy - (3 * k + h / 2))
    drop = 1 if k < 3 else 2
    for dx in (-1, 0, 1):                       # soft drop shadow, below and to the right
        for dy in (-1, 0, 1):
            img.paste((0, 0, 0), (ox + dx + drop, oy + dy + drop + 1), layer.point(lambda v: v * 0.35))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if dx or dy: img.paste(edge, (ox + dx, oy + dy), layer)
    img.paste(ink, (ox, oy), layer)
    cut = 3 * k + int(h * 0.56)                 # lower part of each letter slightly shaded
    low = layer.copy(); ImageDraw.Draw(low).rectangle((0, 0, low.width, cut - 1), fill=0)
    img.paste((200, 212, 236), (ox, oy), low)

TYPEF = load_font('type')
def type_text(img, t, cx, cy, px, edge):
    """Type name in the lettering of the game's type icons: white capitals with a dark edge. px = screen pixels per font pixel."""
    gs = [TYPEF.get(ch) or TYPEF.get(ch.upper()) or ['....'] * 7 for ch in t]
    w = (sum(len(g[0]) + 1 for g in gs) - 1) * px
    layer = Image.new('L', (w + 2*px, 10*px), 0); d = ImageDraw.Draw(layer); x = px
    for g in gs:
        top = 2*px - (len(g) - 7) * px
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == '#': d.rectangle((x + c*px, top + r*px, x + c*px + px - 1, top + r*px + px - 1), fill=255)
        x += (len(g[0]) + 1) * px
    ox, oy = int(cx - layer.width / 2), int(cy - 5.5*px)
    for dx in (-px, 0, px):
        for dy in (-px, 0, px, 2*px):
            if dx or dy: img.paste(edge, (ox + dx, oy + dy), layer)
    img.paste((255, 255, 255), (ox, oy), layer)

def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in c)
RED = (240, 48, 48)
SHADOW = (124, 124, 168)
def shadow(d, box, r, lift=0):
    """Shadow straight under a button. As the selected button rises, its shadow narrows and fades, like a real lift."""
    f = lift / 2.0
    col = tuple(int(a + (b - a) * 0.45 * f) for a, b in zip(SHADOW, (203, 203, 230)))
    d.rounded_rectangle((box[0]+1+lift, box[1]+3, box[2]-1-lift, box[3]+3), r, fill=col)

def rounded(size, r):
    m = Image.new('L', size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0]-1, size[1]-1), r, fill=255); return m
def inset(mask, n):
    """Shrinks a shape by n pixels on every side, corners included, so every border keeps an even thickness."""
    from PIL import ImageFilter
    big = Image.new('L', (mask.width + 2, mask.height + 2), 0); big.paste(mask, (1, 1))   # the outside counts as empty
    for _ in range(n): big = big.filter(ImageFilter.MinFilter(3))
    return big.crop((1, 1, mask.width + 1, mask.height + 1))
def rows(mask, y0, y1):
    m = Image.new('L', mask.size, 0); m.paste(mask.crop((0, y0, mask.width, y1)), (0, y0)); return m

def button(d, box, col, r, selected=False, lift=0, rim=2):
    """Dark outline, white rim, bevelled two-tone body. Each layer is the outer shape shrunk evenly.
    rim = thickness of the white rim: 2 on normal buttons, 1 on small ones (tabs) so the lettering keeps its margin."""
    shadow(d, box, r, lift)
    img = d._image; x0, y0 = box[0], box[1]-lift; w, h = box[2]-box[0]+1, box[3]-box[1]+1
    outer = rounded((w, h), r)
    img.paste((56, 56, 72), (x0, y0), outer)
    img.paste((240, 240, 248), (x0, y0), inset(outer, 1))
    body = inset(outer, rim + 1); fill = inset(outer, rim + 2)
    img.paste(shade(col, 0.55), (x0, y0), body)
    img.paste(shade(col, 0.84), (x0, y0), rows(fill, 0, h-5))
    img.paste(col, (x0, y0), rows(fill, 0, int(h*0.56)))
    img.paste(shade(col, 1.22), (x0, y0), rows(inset(outer, rim + 3), rim + 3, rim + 5))

def socket(d, box, r):
    d.rounded_rectangle(box, r, fill=(168, 168, 200))
    d.rounded_rectangle((box[0]+1, box[1]+1, box[2]-1, box[3]-1), r-1, fill=(224, 224, 244))

def background(d):
    for y in range(AH): d.line((0, y, AW, y), fill=(200, 200, 228) if (y // 2) % 2 else (206, 206, 232))

def party(d, y, own, foe):
    cols = {'ok': (96, 208, 96), 'st': (240, 200, 64), 'ko': (128, 128, 144), '-': None}
    def dot(x, s):
        d.ellipse((x, y, x+8, y+8), fill=(56, 56, 72))
        d.ellipse((x+1, y+1, x+7, y+7), fill=cols[s] or (176, 176, 204))
        if cols[s]: d.rectangle((x+3, y+2, x+4, y+3), fill=(255, 255, 255))
    for i, s in enumerate(own): dot(5 + i*11, s)
    for i, s in enumerate(foe): dot(AW - 14 - i*11, s)

def up(img): return img.resize((AW*S, AH*S), Image.NEAREST)
def ttf(d, t, x, y, size, col, sh, anchor='mm'):
    assert size % 13 == 0, 'game font sizes must be multiples of 13 to stay pixel-perfect'
    f = ImageFont.truetype(TTF, size); o = size // 13; d.fontmode = '1'; x, y = int(x), int(y)
    if sh:
        for dx, dy in ((o, 0), (0, o), (o, o)): d.text((x+dx, y+dy), t, font=f, fill=sh, anchor=anchor)
    d.text((x, y), t, font=f, fill=col, anchor=anchor)

def command(dy=0):
    a = Image.new('RGB', (AW, AH)); d = ImageDraw.Draw(a); background(d)
    party(d, 4, ['ok', 'ok', 'st', 'ko', '-', '-'], ['ok'])
    button(d, (10, 19, 195, 112), (248, 144, 200), 11, selected=True, lift=-dy)
    button(d, (4, 122, 70, 174), (240, 168, 48), 9)
    button(d, (77, 134, 128, 174), (56, 136, 232), 8)
    button(d, (135, 122, 201, 174), (88, 200, 88), 9)
    pix_text(a, 'LUCHAR', 103, 65+dy, 3)
    pix_text(a, 'MOCHILA', 37, 147, 1); pix_text(a, 'HUIR', 103, 153, 1); pix_text(a, 'POKéMON', 168, 147, 1)
    return up(a)

TYPES = {'Eléctrico': (248, 208, 48), 'Psíquico': (248, 88, 136), 'Normal': (168, 168, 120)}
TYPE_ROW = {'Normal': 0, 'Eléctrico': 13, 'Psíquico': 14}       # rows of the game's types.png (64x28 each)
MOVES = [('Impactrueno', 'Eléctrico', '30/30', 'Muy eficaz'), ('Rayo', 'Eléctrico', '15/15', 'Muy eficaz'),
         ('Agilidad', 'Psíquico', '30/30', None), ('Ataque Rápido', 'Normal', '30/30', None)]
CELLS = [(4, 5, 101, 71), (105, 5, 202, 71), (4, 75, 101, 141), (105, 75, 202, 141)]
def fight(dy=0):
    """Move buttons: thin frame in the type's colour around a cream panel (same card as the moves screen of the
    summary); name, the game's own type icon at 3x with the PP beside it, and the effectiveness band at the foot
    when it applies. The type icon needs the game folder: it is taken as the folder above the font's."""
    import os
    types = Image.open(os.path.join(os.path.dirname(os.path.dirname(TTF)), 'Graphics', 'Pictures', 'types.png')).convert('RGBA')
    cells = [(b[0], b[1]+dy, b[2], b[3]+dy) if i == 0 else b for i, b in enumerate(CELLS)]
    a = Image.new('RGB', (AW, AH)); d = ImageDraw.Draw(a); background(d)
    for i, ((n, t, pp, e), b) in enumerate(zip(MOVES, cells)):
        col = TYPES[t]; sel = i == 0; x0, y0, x1, y1 = b
        shadow(d, CELLS[i], 8, -dy if sel else 0)
        w, h = x1-x0+1, y1-y0+1; outer = rounded((w, h), 8)
        a.paste((56, 56, 72), (x0, y0), outer)
        a.paste((240, 240, 248), (x0, y0), inset(outer, 1))
        a.paste(shade(col, 0.6), (x0, y0), inset(outer, 3)); a.paste(col, (x0, y0), inset(outer, 4))
        a.paste((248, 248, 236), (x0, y0), inset(outer, 6))
        if e:
            band = (72, 184, 88); cream = inset(outer, 6)
            a.paste(band, (x0, y0), rows(cream, 47, h)); a.paste(shade(band, 0.7), (x0, y0), rows(cream, 47, 48))
            a.paste(shade(band, 1.2), (x0, y0), rows(cream, 48, 49))
    button(d, (56, 147, 149, 175), (56, 136, 232), 9)
    pix_text(a, 'CANCELAR', 103, 161, 1)
    big = up(a).convert('RGBA'); d = ImageDraw.Draw(big)
    for (n, t, pp, e), b in zip(MOVES, cells):
        cx = (b[0] + b[2] + 1) / 2 * S
        size = 78
        while ImageFont.truetype(TTF, size).getlength(n) > (b[2]-b[0]-20)*S: size -= 13
        ny, iy = (14.5, 25) if e else (20, 35)                    # name centre and icon top, in art px from the cell's top
        ttf(d, n, cx, (b[1]+ny)*S, size, (64, 64, 80), (168, 168, 192))
        icon = types.crop((0, TYPE_ROW[t]*28, 64, TYPE_ROW[t]*28 + 28)); icon = icon.resize((192, 84), Image.NEAREST)
        big.alpha_composite(icon, (int(cx) - 201, (b[1]+iy)*S))
        ttf(d, 'PP ' + pp, int(cx) + 197, (b[1]+iy+7)*S, 52, (64, 64, 80), (168, 168, 192), 'rm')
        if e: ttf(d, e, cx, (b[1]+53.5)*S, 52, (255, 255, 255), (40, 112, 56))
    return big.convert('RGB')
if __name__ == '__main__':
    OUT = sys.argv[3]
    def sheet(dy):
        a, b = command(dy), fight(dy)
        out = Image.new('RGB', (a.width*2 + 60, a.height + 40), (0, 0, 0)); out.paste(a, (20, 20)); out.paste(b, (a.width + 40, 20))
        return out.resize((out.width//2, out.height//2), Image.NEAREST)
    sheet(0).save(OUT)
    frames = [sheet(dy).convert('P', palette=Image.ADAPTIVE, colors=128) for dy in (0, -1, -2, -1)]
    frames[0].save(OUT.replace('.png', '.gif'), save_all=True, append_images=frames[1:], duration=[260, 110, 200, 110], loop=0)

# Mock-up of the party screen on the second screen, built from the game's own party images and fonts
# (Graphics/Pictures/Party, the Pokémon icons, Power Green), with the colours used by UI_Party.
# - The panels are widened and made taller by repeating lines of the image (never scaled) and shown at 2x.
# - What goes inside them (ball, icon, name, HP bar and numbers, level, status) is shown at 3x, all of it at
#   the same scale, re-laid out for the panel's new shape. A full 3x copy of the game's layout would need
#   panels 768 px wide, which does not fit two columns on a 1240 px screen.
# - The game's 512x384 background is extended to 620x540 by repeating lines; the panel sockets drawn on it are
#   filled with the frame's own colour. Bottom band: help line and the back button (approved button style).
# Needs the game folder. Usage: ds_mock_party.py <game folder> <pixelfonts.txt> <out.png> [actions]
import sys
from PIL import Image, ImageDraw, ImageFont
GAME, FONTS_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
MODE = sys.argv[4] if len(sys.argv) > 4 else ''
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m
P = GAME + '/Graphics/Pictures/'
def img(p): return Image.open(P + p).convert('RGBA')
U = 3                                                             # screen px per pixel of the panel contents
FONT = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 26 * U)
SMALL = ImageFont.truetype(GAME + '/Fonts/power green small.ttf', 26 * U)
HELP = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 52)
W, H = 620, 540
WHITE, DARK = (248, 248, 248), (40, 40, 40)

def text(d, s, x, y, base, shadow, align=0, font=FONT, k=U):     # x, y in screen px; same offsets as the game, times k
    w = d.textlength(s, font=font); x = x - w if align == 1 else x
    y += 10 * k
    for dx, dy in ((2, 0), (0, 2), (2, 2)): d.text((x + dx * k, y + dy * k), s, font=font, fill=shadow, anchor='la')
    d.text((x, y), s, font=font, fill=base, anchor='la')
def big(im): return im.resize((im.width * U, im.height * U), Image.NEAREST)

def repeat_col(im, x, n):
    out = Image.new('RGBA', (im.width + n, im.height)); out.paste(im.crop((0, 0, x, im.height)), (0, 0))
    out.paste(im.crop((x, 0, x + 1, im.height)).resize((n, im.height)), (x, 0)); out.paste(im.crop((x, 0, im.width, im.height)), (x + n, 0)); return out
def repeat_row(im, y, n):
    out = Image.new('RGBA', (im.width, im.height + n)); out.paste(im.crop((0, 0, im.width, y)), (0, 0))
    out.paste(im.crop((0, y, im.width, y + 1)).resize((im.width, n)), (0, y)); out.paste(im.crop((0, y, im.width, im.height)), (0, y + n)); return out

# name, species, level, hp, total, gender
PARTY = [('Pikachu', 'PIKACHU', 5, 18, 18, 'm'), ('Pidgey', 'PIDGEY', 4, 9, 16, 'f'),
         ('Rattata', 'RATTATA', 3, 0, 13, 'm'), ('Caterpie', 'CATERPIE', 3, 12, 12, 'm')]
SELECTED = 0
SIDE, MID, BOTTOM = 54, 90, 66
WIDE, GROW = 40, 20                                               # panel: 256x98 -> 296x138
def tall(im): return repeat_col(repeat_row(repeat_row(im, 60, GROW), 46, GROW), 128, WIDE)
def slot_xy(i): return 8 + (i % 2) * 308, 6 + 16 * (i % 2) + 142 * (i // 2)
def kind(i, mon):
    if not mon: return 'Party/panel_blank.png'
    return 'Party/panel_%s%s%s.png' % ('round' if i == 0 else 'rect', '_faint' if mon[3] == 0 else '', '_sel' if i == SELECTED else '')

def contents(one, d, i, mon):                                     # positions in content pixels (1 = U screen px)
    x, y = slot_xy(i); x, y = x * 2, y * 2
    def at(cx, cy): return x + cx * U, y + cy * U
    name, species, lv, hp, total, gender = mon
    faint = hp == 0
    one.alpha_composite(big(img('Party/overlay_hp_back%s.png' % ('_faint' if faint else ''))), at(52, 36))
    one.alpha_composite(big(img('Party/icon_ball%s.PNG' % ('_sel' if i == SELECTED else ''))), at(1, 0))
    icon = Image.open(GAME + '/Graphics/Pokemon/Icons/' + species + '.png').convert('RGBA').crop((0, 0, 64, 64))
    one.alpha_composite(big(icon), at(10, 2))
    text(d, name, *at(80, 1), WHITE, DARK)
    g = ('♂', (0, 112, 248), (120, 184, 232)) if gender == 'm' else ('♀', (232, 32, 16), (248, 168, 184))
    text(d, g[0], *at(190, 1), g[1], g[2], 1)
    if hp > 0:
        w = max(1, hp * 96.0 / total); w = int(round(w / 2)) * 2
        zone = 2 if hp <= total // 4 else 1 if hp <= total // 2 else 0
        one.alpha_composite(big(img('Party/overlay_hp.png').crop((0, zone * 8, w, zone * 8 + 8))), at(84, 38))
    else:
        one.alpha_composite(big(img('statuses.png').crop((0, 16 * 5, 44, 16 * 6))), at(50, 66))
    text(d, '%d/%d' % (hp, total), *at(190, 50), WHITE, DARK, 1)
    one.alpha_composite(big(img('Party/overlay_lv.png')), at(6, 68))
    text(d, str(lv), *at(28, 55), WHITE, DARK, font=SMALL)

def backdrop():
    s = repeat_row(repeat_row(repeat_col(repeat_col(img('Party/bg.PNG'), 511, SIDE), 0, SIDE), 370, BOTTOM), 320, MID)
    assert s.size == (W, H), s.size
    edge = s.crop((0, 330, W, 331))                               # one clean line of the frame, sides included
    for y in range(0, 330): s.paste(edge, (0, y))
    return s

def footer(one, d, line):
    text(d, line, 48, H * 2 - 124, WHITE, DARK, font=HELP, k=2)
    m.SHADOW = tuple(int(v * 0.6) for v in one.getpixel((W * 2 - 40, H * 2 - 20))[:3])
    key = Image.new('RGBA', (72, 26), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
    m.button(kd, (1, 1, 70, 22), (56, 136, 232), 9); m.pix_text(key, 'VOLVER', 35, 11, 1)
    key = key.resize((72 * 6, 26 * 6), Image.NEAREST); one.paste(key, (W * 2 - 72 * 6 - 8, H * 2 - 26 * 6 - 2), key)

def screen():
    s = backdrop()
    mons = [PARTY[i] if i < len(PARTY) else None for i in range(6)]
    for i, mon in enumerate(mons): s.alpha_composite(tall(img(kind(i, mon))), slot_xy(i))
    one = s.resize((W * 2, H * 2), Image.NEAREST)
    d = ImageDraw.Draw(one); d.fontmode = '1'
    for i, mon in enumerate(mons):
        if mon: contents(one, d, i, mon)
    footer(one, d, 'Elige un Pokémon.')
    return one.convert('RGB')

# What the game offers for a Pokémon: Summary, its field moves, Switch, Item (or Mail), Cancel.
ACTIONS = [('DATOS', (56, 136, 232)), ('MOVER', (88, 200, 88)), ('OBJETO', (240, 168, 48)), ('DESTELLO', (64, 184, 184))]
def actions():
    """After touching a Pokémon: its panel alone on top, a grid of medium buttons under it, back at the bottom."""
    global slot_xy
    s = backdrop(); keep = slot_xy; slot_xy = lambda i: ((W - 296) // 2, 8)
    s.alpha_composite(tall(img(kind(SELECTED, PARTY[SELECTED]))), slot_xy(SELECTED))
    one = s.resize((W * 2, H * 2), Image.NEAREST); d = ImageDraw.Draw(one); d.fontmode = '1'
    contents(one, d, SELECTED, PARTY[SELECTED]); slot_xy = keep
    m.SHADOW = tuple(int(v * 0.7) for v in one.getpixel((W, 400))[:3])
    rows = (len(ACTIONS) + 1) // 2
    grid = Image.new('RGBA', (160, rows * 30), (0, 0, 0, 0)); gd = ImageDraw.Draw(grid)
    for n, (label, col) in enumerate(ACTIONS):
        bx, by = (n % 2) * 82, (n // 2) * 30
        m.button(gd, (bx + 1, by + 1, bx + 77, by + 25), col, 9, selected=(n == 0), lift=(1 if n == 0 else 0))
        m.pix_text(grid, label, bx + 39, by + 13 - (1 if n == 0 else 0), 1)
    grid = grid.resize((grid.width * 6, grid.height * 6), Image.NEAREST)
    one.paste(grid, ((W * 2 - 160 * 6) // 2 + 6, 330), grid)
    footer(one, d, '¿Qué hacer con %s?' % PARTY[SELECTED][0])
    return one.convert('RGB')

if __name__ == '__main__':
    (actions() if MODE == 'actions' else screen()).save(OUT)   # real size: 1240x1080

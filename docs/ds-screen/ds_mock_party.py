# Mock-up of the party screen on the second screen, built from the game's own party images and fonts
# (Graphics/Pictures/Party, the Pokémon icons, Power Green), with the colours used by UI_Party.
# - The panels are widened and made taller by repeating lines of the image (never scaled) and shown at 2x.
# - Inside them, the Pokémon's icon and name are shown at 3x; ball, gender, HP bar and numbers, level and
#   status stay at 2x like the panel. A full 3x copy of the game's layout would need panels 768 px wide,
#   which does not fit two columns on a 1240 px screen.
# - The game's 512x384 background is extended to 620x540 by repeating lines; the panel sockets drawn on it are
#   filled with the frame's own colour. Bottom band: help line and the back button (approved button style).
# Needs the game folder. Usage: ds_mock_party.py <game folder> <pixelfonts.txt> <out.png>
import sys
from PIL import Image, ImageDraw, ImageFont
GAME, FONTS_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m
P = GAME + '/Graphics/Pictures/'
def img(p): return Image.open(P + p).convert('RGBA')
U = 3                                                             # screen px per pixel of the panel contents
FONT = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 26 * U)
SMALL = ImageFont.truetype(GAME + '/Fonts/power green small.ttf', 26 * U)
HELP = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 52)
SMALL2 = ImageFont.truetype(GAME + '/Fonts/power green small.ttf', 52)
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

def contents(one, d, i, mon):                                     # positions in screen px inside the panel (592x276)
    x, y = slot_xy(i); x, y = x * 2, y * 2
    def at(px, py): return x + px, y + py
    def x2(im): return im.resize((im.width * 2, im.height * 2), Image.NEAREST)
    name, species, lv, hp, total, gender = mon
    faint = hp == 0
    one.alpha_composite(x2(img('Party/overlay_hp_back%s.png' % ('_faint' if faint else ''))), at(292, 150))
    one.alpha_composite(x2(img('Party/icon_ball%s.PNG' % ('_sel' if i == SELECTED else ''))), at(20, 0))
    icon = Image.open(GAME + '/Graphics/Pokemon/Icons/' + species + '.png').convert('RGBA').crop((0, 0, 64, 64))
    one.alpha_composite(big(icon), at(44, 4))                     # 3x
    text(d, name, *at(244, 12), WHITE, DARK)                      # 3x
    g = ('♂', (0, 112, 248), (120, 184, 232)) if gender == 'm' else ('♀', (232, 32, 16), (248, 168, 184))
    text(d, g[0], *at(572, 26), g[1], g[2], 1, font=HELP, k=2)
    if hp > 0:
        w = max(1, hp * 96.0 / total); w = int(round(w / 2)) * 2
        zone = 2 if hp <= total // 4 else 1 if hp <= total // 2 else 0
        one.alpha_composite(x2(img('Party/overlay_hp.png').crop((0, zone * 8, w, zone * 8 + 8))), at(356, 154))
    else:
        one.alpha_composite(x2(img('statuses.png').crop((0, 16 * 5, 44, 16 * 6))), at(140, 214))
    text(d, '%3d /%3d' % (hp, total), *at(568, 158), WHITE, DARK, 1, font=HELP, k=2)
    one.alpha_composite(x2(img('Party/overlay_lv.png')), at(24, 218))
    text(d, str(lv), *at(68, 192), WHITE, DARK, font=SMALL2, k=2)

def screen():
    s = repeat_row(repeat_row(repeat_col(repeat_col(img('Party/bg.PNG'), 511, SIDE), 0, SIDE), 370, BOTTOM), 320, MID)
    assert s.size == (W, H), s.size
    edge = s.crop((0, 330, W, 331))                               # one clean line of the frame, sides included
    for y in range(0, 330): s.paste(edge, (0, y))
    mons = [PARTY[i] if i < len(PARTY) else None for i in range(6)]
    for i, mon in enumerate(mons): s.alpha_composite(tall(img(kind(i, mon))), slot_xy(i))
    one = s.resize((W * 2, H * 2), Image.NEAREST)
    d = ImageDraw.Draw(one); d.fontmode = '1'
    for i, mon in enumerate(mons):
        if mon: contents(one, d, i, mon)
    text(d, 'Elige un Pokémon.', 48, H * 2 - 124, WHITE, DARK, font=HELP, k=2)
    m.SHADOW = tuple(int(v * 0.6) for v in one.getpixel((W * 2 - 40, H * 2 - 20))[:3])
    key = Image.new('RGBA', (72, 26), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
    m.button(kd, (1, 1, 70, 22), (56, 136, 232), 9); m.pix_text(key, 'VOLVER', 35, 11, 1)
    key = key.resize((72 * 6, 26 * 6), Image.NEAREST); one.paste(key, (W * 2 - 72 * 6 - 8, H * 2 - 26 * 6 - 2), key)
    return one.convert('RGB')

if __name__ == '__main__':
    screen().save(OUT)                                            # real size: 1240x1080

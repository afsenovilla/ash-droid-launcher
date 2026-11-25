# Mock-up of the party screen on the second screen: a replica of the game's own party screen (same images,
# fonts, colours and offsets inside each panel as UI_Party in the game's scripts), shown at 2x. The six panels
# keep their size and the right column keeps its 16 px drop, but they are spread out to use the taller screen
# and be easy touch targets. The game's 512x384 background is extended to 620x540 by repeating lines (never
# scaled) and the panel sockets drawn on it are filled with the frame's own colour, since the panels no longer
# sit on them. Bottom band: the help line and the back button (approved button style).
# Needs the game folder. Usage: ds_mock_party.py <game folder> <pixelfonts.txt> <out.png>
import sys
from PIL import Image, ImageDraw, ImageFont
GAME, FONTS_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m
P = GAME + '/Graphics/Pictures/'
def img(p): return Image.open(P + p).convert('RGBA')
FONT = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 26)
SMALL = ImageFont.truetype(GAME + '/Fonts/power green small.ttf', 26)
W, H = 620, 540
WHITE, DARK = (248, 248, 248), (40, 40, 40)

def text(d, s, x, y, base, shadow, align=0, font=FONT):
    w = d.textlength(s, font=font); x = x - w if align == 1 else x
    y += 10                                                       # pbDrawTextPositions (+6) and pbDrawShadowText (+4)
    for dx, dy in ((2, 0), (0, 2), (2, 2)): d.text((x + dx, y + dy), s, font=font, fill=shadow, anchor='la')
    d.text((x, y), s, font=font, fill=base, anchor='la')

# name, species, level, hp, total, gender
PARTY = [('Pikachu', 'PIKACHU', 5, 18, 18, 'm'), ('Pidgey', 'PIDGEY', 4, 9, 16, 'f'),
         ('Rattata', 'RATTATA', 3, 0, 13, 'm'), ('Caterpie', 'CATERPIE', 3, 12, 12, 'm')]
SELECTED = 0
SIDE, MID, BOTTOM = 54, 76, 80
def slot_xy(i): return 42 + (i % 2) * 280, 14 + 16 * (i % 2) + 138 * (i // 2)

def repeat_col(im, x, n):
    out = Image.new('RGBA', (im.width + n, im.height)); out.paste(im.crop((0, 0, x, im.height)), (0, 0))
    out.paste(im.crop((x, 0, x + 1, im.height)).resize((n, im.height)), (x, 0)); out.paste(im.crop((x, 0, im.width, im.height)), (x + n, 0)); return out
def repeat_row(im, y, n):
    out = Image.new('RGBA', (im.width, im.height + n)); out.paste(im.crop((0, 0, im.width, y)), (0, 0))
    out.paste(im.crop((0, y, im.width, y + 1)).resize((im.width, n)), (0, y)); out.paste(im.crop((0, y, im.width, im.height)), (0, y + n)); return out

def panel(s, d, i, mon):
    x, y = slot_xy(i)
    if not mon: s.alpha_composite(img('Party/panel_blank.png'), (x, y)); return
    name, species, lv, hp, total, gender = mon
    kind = 'round' if i == 0 else 'rect'; sel = i == SELECTED; faint = hp == 0
    s.alpha_composite(img('Party/panel_%s%s%s.png' % (kind, '_faint' if faint else '', '_sel' if sel else '')), (x, y))
    s.alpha_composite(img('Party/overlay_hp_back%s.png' % ('_faint' if faint else '')), (x + 96, y + 50))
    s.alpha_composite(img('Party/icon_ball%s.PNG' % ('_sel' if sel else '')), (x + 10, y))
    icon = Image.open(GAME + '/Graphics/Pokemon/Icons/' + species + '.png').convert('RGBA').crop((0, 0, 64, 64))
    s.alpha_composite(icon, (x + 60 - 32, y + 40 - 32))
    text(d, name, x + 96, y + 10, WHITE, DARK)
    text(d, '%3d /%3d' % (hp, total), x + 224, y + 54, WHITE, DARK, 1)
    if hp > 0:
        w = max(1, hp * 96.0 / total); w = int(round(w / 2)) * 2
        zone = 2 if hp <= total // 4 else 1 if hp <= total // 2 else 0
        s.alpha_composite(img('Party/overlay_hp.png').crop((0, zone * 8, w, zone * 8 + 8)), (x + 128, y + 52))
    else:
        s.alpha_composite(img('statuses.png').crop((0, 16 * 5, 44, 16 * 6)), (x + 78, y + 68))
    if gender == 'm': text(d, '♂', x + 224, y + 10, (0, 112, 248), (120, 184, 232))
    else: text(d, '♀', x + 224, y + 10, (232, 32, 16), (248, 168, 184))
    s.alpha_composite(img('Party/overlay_lv.png'), (x + 20, y + 70))
    text(d, str(lv), x + 42, y + 57, WHITE, DARK, font=SMALL)

def screen():
    s = repeat_row(repeat_row(repeat_col(repeat_col(img('Party/bg.PNG'), 511, SIDE), 0, SIDE), 370, BOTTOM), 320, MID)
    assert s.size == (W, H), s.size
    light = s.getpixel((W // 2, 330)); x0 = x1 = W // 2; y1 = 330                 # inside of the frame, below the sockets
    while s.getpixel((x0 - 1, 330)) == light: x0 -= 1
    while s.getpixel((x1 + 1, 330)) == light: x1 += 1
    while s.getpixel((W // 2, y1 + 1)) == light: y1 += 1
    edge = s.crop((0, 330, W, 331))                                              # one clean line of the frame, sides included
    for y in range(0, 330): s.paste(edge, (0, y))
    d = ImageDraw.Draw(s); d.fontmode = '1'
    for i in range(6): panel(s, d, i, PARTY[i] if i < len(PARTY) else None)
    text(d, 'Elige un Pokémon.', 24, 474, WHITE, DARK)
    one = s.resize((W * 2, H * 2), Image.NEAREST)
    m.SHADOW = tuple(int(v * 0.6) for v in one.getpixel((W * 2 - 40, H * 2 - 20))[:3])
    key = Image.new('RGBA', (72, 32), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
    m.button(kd, (1, 5, 70, 28), (56, 136, 232), 9); m.pix_text(key, 'VOLVER', 35, 16, 1)
    key = key.resize((72 * 6, 32 * 6), Image.NEAREST); one.paste(key, (W * 2 - 72 * 6 - 8, H * 2 - 32 * 6 - 2), key)
    return one.convert('RGB')

if __name__ == '__main__':
    screen().save(OUT)                                            # real size: 1240x1080

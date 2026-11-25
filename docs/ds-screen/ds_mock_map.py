# Mock-up of the region map on the second screen, built from the game's own map images and font.
# The map fills a viewport between a top bar (region, place) and a bottom row of buttons, at an integer
# zoom (2x shows the whole region, 3x and 4x are centred on the player and clamped to the map edges).
# The game's 512x384 frame is extended by repeating lines where it is uniform (never scaled).
# Needs the game folder. Usage: ds_mock_map.py <game folder> <pixelfonts.txt> <out.png> [zoom=2]
import sys
from PIL import Image, ImageDraw, ImageFont
GAME, FONTS_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
ZOOM = int(sys.argv[4]) if len(sys.argv) > 4 else 2
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m                                               # approved button style and lettering
G = GAME + '/Graphics/Pictures/'
def img(p): return Image.open(G + p).convert('RGBA')
FONT = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 26)
W, H, SIDE, BOTTOM = 620, 540, 54, 156
SQ = 16

def repeat_col(im, x, n):
    out = Image.new('RGBA', (im.width + n, im.height)); out.paste(im.crop((0, 0, x, im.height)), (0, 0))
    out.paste(im.crop((x, 0, x + 1, im.height)).resize((n, im.height)), (x, 0)); out.paste(im.crop((x, 0, im.width, im.height)), (x + n, 0)); return out
def repeat_row(im, y, n):
    out = Image.new('RGBA', (im.width, im.height + n)); out.paste(im.crop((0, 0, im.width, y)), (0, 0))
    out.paste(im.crop((0, y, im.width, y + 1)).resize((im.width, n)), (0, y)); out.paste(im.crop((0, y, im.width, im.height)), (0, y + n)); return out
def text(d, s, x, y, base, shadow, align=0):
    w = d.textlength(s, font=FONT); x = x - w if align == 1 else x
    y += 10
    for dx, dy in ((2, 0), (0, 2), (2, 2)): d.text((x + dx, y + dy), s, font=FONT, fill=shadow, anchor='la')
    d.text((x, y), s, font=FONT, fill=base, anchor='la')

REGION, PLACE, POS = 'Kanto', 'Ruta 1', (6, 9)                     # example: the player is on Route 1
TOP, ROW = 84, 196                                                # top bar and button row, in screen px
VIEW = (0, TOP, W * 2, H * 2 - ROW)

def sign(d, box, plus):
    cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
    for col, o in (((48, 48, 64), 1), ((255, 255, 255), 0)):
        d.rectangle((cx - 5 + o, cy - 1 + o, cx + 5 + o, cy + 1 + o), fill=col)
        if plus: d.rectangle((cx - 1 + o, cy - 5 + o, cx + 1 + o, cy + 5 + o), fill=col)

def screen(zoom=ZOOM):
    bg = repeat_row(repeat_col(repeat_col(img('mapbg.png'), 505, SIDE), 5, SIDE), 378, BOTTOM)
    assert bg.size == (W, H), bg.size
    one = bg.resize((W * 2, H * 2), Image.NEAREST)
    bar = Image.new('RGBA', (W, TOP // 2), (24, 40, 136)); d = ImageDraw.Draw(bar); d.fontmode = '1'
    d.rectangle((0, 0, W, 3), fill=(72, 96, 240)); d.rectangle((0, 4, W, 7), fill=(48, 72, 208))
    text(d, REGION + '  ·  ' + PLACE, 18, 1, (248, 248, 248), (0, 0, 0))
    text(d, 'Estás aquí', W - 16, 1, (248, 248, 248), (0, 0, 0), 1)
    one.paste(bar.resize((W * 2, TOP), Image.NEAREST), (0, 0))
    # the map with the player's marker, at the chosen zoom
    region = img('mapRegion0.PNG')
    px, py = -SQ // 2 + POS[0] * SQ, -SQ // 2 + POS[1] * SQ
    region.alpha_composite(img('mapPlayerPOKEMONTRAINER_Red.png'), (px, py))
    region.alpha_composite(img('mapCursor.png').crop((0, 0, 32, 32)), (px, py))
    big = region.resize((region.width * zoom, region.height * zoom), Image.NEAREST)
    vw, vh = VIEW[2] - VIEW[0], VIEW[3] - VIEW[1]
    ImageDraw.Draw(one).rectangle((VIEW[0], VIEW[1], VIEW[2] - 1, VIEW[3] - 1), fill=(0, 0, 0))
    def window(size, view, centre):                               # crop offset and paste offset on one axis
        if size <= view: return 0, (view - size) // 2
        return max(0, min(size - view, centre - view // 2)), 0
    cx, ox = window(big.width, vw - 8, (px + 16) * zoom); cy, oy = window(big.height, vh - 8, (py + 16) * zoom)
    part = big.crop((cx, cy, min(big.width, cx + vw - 8), min(big.height, cy + vh - 8)))
    if part.size != (vw - 8, vh - 8):                              # whole map visible: keep the game's frame around it
        ImageDraw.Draw(one).rectangle((VIEW[0], VIEW[1], VIEW[2] - 1, VIEW[3] - 1), fill=(24, 40, 136))
        ImageDraw.Draw(one).rectangle((ox, VIEW[1] + oy, ox + part.width + 7, VIEW[1] + oy + part.height + 7), fill=(0, 0, 0))
    one.paste(part, (VIEW[0] + 4 + ox, VIEW[1] + 4 + oy))
    # Bottom buttons, in the style approved for the battle menus (drawn by code)
    m.SHADOW = (16, 24, 96)
    grey, blue = (136, 136, 160), (56, 136, 232)
    zb = Image.new('RGBA', (24, 50), (0, 0, 0, 0)); zd = ImageDraw.Draw(zb)   # zoom buttons, over the map's corner
    m.button(zd, (1, 1, 22, 22), grey if zoom == 4 else blue, 9); sign(zd, (1, 1, 22, 22), True)
    m.button(zd, (1, 26, 22, 47), grey if zoom == 2 else blue, 9); sign(zd, (1, 26, 22, 47), False)
    key = zb.resize((24 * 6, 50 * 6), Image.NEAREST)
    one.paste(key, (W * 2 - 24 * 6 - 6, VIEW[3] - 50 * 6 - 6), key)
    strip = Image.new('RGB', (206, 32), (32, 48, 152)); sd = ImageDraw.Draw(strip)
    m.button(sd, (2, 2, 128, 28), (88, 200, 88), 9); m.pix_text(strip, 'POKéMON SALVAJES', 65, 15, 1)
    m.button(sd, (134, 2, 203, 28), blue, 9); m.pix_text(strip, 'VOLVER', 168, 15, 1)
    ImageDraw.Draw(one).rectangle((0, H * 2 - ROW, W * 2, H * 2), fill=(32, 48, 152))
    one.paste(strip.resize((206 * 6, 32 * 6), Image.NEAREST), (2, H * 2 - ROW + 2))
    return one.convert('RGB')

if __name__ == '__main__':
    screen().save(OUT)                                            # real size: 1240x1080

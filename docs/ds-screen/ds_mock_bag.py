# Mock-up of the bag on the second screen: the game's own bag screen (same images, font, colours and
# layout rules as UI_Bag in the game's scripts) re-laid to fill the whole second screen.
# The game's 512x384 background is cut along lines where it is uniform and those lines are repeated,
# so nothing is blurred or distorted: +64 px in the left panel, +44 in the item list, +124 of height
# in the upper part and +32 at the top of the description box (so its text is not glued to the edge).
# The strip of small pocket icons becomes a grid of large touch buttons built from the same icons.
# With a third argument, the state after touching an item: the left panel gives way to that item's options
# (the ones the game offers for it) as stacked buttons in the approved style, and CANCELAR to drop the
# selection and get the pockets back. 'item' = out of battle, 'battle' = in battle (the game only offers Use there).
# Needs the game folder. Usage: ds_mock_bag.py <game folder> <out.png> [item|battle <pixelfonts.txt>]
import sys
from PIL import Image, ImageDraw, ImageFont
GAME, OUT = sys.argv[1], sys.argv[2]
MODE = sys.argv[3] if len(sys.argv) > 3 else ''
FONTS_TXT = sys.argv[4] if len(sys.argv) > 4 else ''
G = GAME + '/Graphics/'
def img(p): return Image.open(G + p).convert('RGBA')
FONT = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 26)   # the game asks for size 29; the engine draws it at 26 px
W, H = 620, 540                                                   # x2 = 1240x1080
DX_LEFT, DX_LIST, DY_TOP, DY_DESC = 64, 44, 124, 32
BOX = 88                                                          # item icon box (64 in the game)

def repeat_col(im, x, n):
    out = Image.new('RGBA', (im.width + n, im.height)); out.paste(im.crop((0, 0, x, im.height)), (0, 0))
    out.paste(im.crop((x, 0, x + 1, im.height)).resize((n, im.height)), (x, 0)); out.paste(im.crop((x, 0, im.width, im.height)), (x + n, 0)); return out
def repeat_row(im, y, n):
    out = Image.new('RGBA', (im.width, im.height + n)); out.paste(im.crop((0, 0, im.width, y)), (0, 0))
    out.paste(im.crop((0, y, im.width, y + 1)).resize((im.width, n)), (0, y)); out.paste(im.crop((0, y, im.width, im.height)), (0, y + n)); return out

def text(d, s, x, y, base, shadow, align=0):
    w = d.textlength(s, font=FONT)
    x = x - w if align == 1 else x - w // 2 if align == 2 else x
    y += 10                                                       # pbDrawTextPositions (+6) and pbDrawShadowText (+4)
    for dx, dy in ((2, 0), (0, 2), (2, 2)): d.text((x + dx, y + dy), s, font=FONT, fill=shadow, anchor='la')
    d.text((x, y), s, font=FONT, fill=base, anchor='la')

POCKET, NAME = 2, 'Botiquín'
ITEMS = [('POTION', 'Poción', 5), ('SUPERPOTION', 'Superpoción', 2), ('ANTIDOTE', 'Antídoto', 3),
         ('PARALYZEHEAL', 'Antiparalizador', 1), ('', 'Despertar', 2), ('', 'Antiquemar', 1), ('', 'Antihielo', 1),
         ('', 'Cura Total', 1), ('REVIVE', 'Revivir', 1), ('', 'Éter', 1)]
SEL = 0
ART_POCKET = None                                                 # set to keep the bag art of one pocket while another is selected (animation frames)
PRESSED = 5                                                       # a pocket shown while being touched
# One item of the game per pocket, as its emblem (Items, Medicine, Poké Balls, TMs & HMs, Berries, Badges, Battle Items, Key Items)
POCKET_ICONS = ['REPEL', 'POTION', 'POKEBALL', 'machine_NORMAL', 'ORANBERRY', 'BOULDERBADGE', 'XATTACK', 'BICYCLE']
DESC = ['Medicina en espray que restaura', '20 PS de un Pokémon.']
ORANGE = (232, 128, 80, 255)

EMBLEMS = []
GLYPH = {(0, 112, 248), (144, 208, 248), (248, 248, 248), (184, 184, 184), (216, 216, 216)}   # colours of the drawings on the game's pocket icons
def pocket_button(s, d, x, y, state, light, dark, mid, index):
    """60x60 cell: a round button whose whole face has the pocket's two colours (taken from the game's
    pocket sheet). idle: grey ring; selected: white ring, slightly wider; pressed: sunk two pixels, darker,
    without its shadow."""
    off = 2 if state == 'pressed' else 0
    k = 0.72 if state == 'pressed' else 1.0
    tone = lambda c: tuple(int(v * k) for v in c[:3])
    if state != 'pressed': d.ellipse((x + 2, y + 5, x + 57, y + 60), fill=(176, 88, 48))            # shadow on the orange panel
    d.ellipse((x + 2, y + 2 + off, x + 57, y + 57 + off), fill=(72, 72, 72))                          # outline
    d.ellipse((x + 4, y + 4 + off, x + 55, y + 55 + off), fill=(248, 248, 248) if state == 'selected' else tone((200, 200, 200)))
    i = 8 if state == 'selected' else 6
    box = (x + i, y + i + off, x + 59 - i, y + 59 - i + off)
    d.ellipse(box, fill=tone(dark)); d.chord(box, 180, 360, fill=tone(light))                          # upper half light, lower half dark
    d.rectangle((x + i, y + 28 + off, x + 59 - i, y + 29 + off), fill=tone(mid))                       # the band between them, as on the game's icon
    EMBLEMS.append((index, x + 30, y + 30 + off, state))        # the icon's drawing goes on top at screen size (see main)

def bag():
    src = img('Pictures/Bag/bg_%d.png' % (ART_POCKET or POCKET))
    bg = repeat_row(src, 292, DY_DESC)          # description box: room above its text
    bg = repeat_row(bg, 217, DY_TOP)            # upper part: under the pocket-name bar
    bg = repeat_col(bg, 300, DX_LIST)           # the list of items
    bg = repeat_col(bg, 100, DX_LEFT)           # left panel
    s = bg; d = ImageDraw.Draw(s); d.fontmode = '1'
    pw = 186 + DX_LEFT                          # left panel width
    # bag, arrows and pocket name, centred in the wider panel
    bx = (pw - 128) // 2
    s.alpha_composite(img('Pictures/Bag/bag_%d.PNG' % (ART_POCKET or POCKET)), (bx, 20))
    tmp = Image.new('RGBA', s.size)
    tmp.paste(img('Pictures/leftarrow.png').crop((0, 0, 40, 28)), (bx - 34, 76)); tmp.paste(img('Pictures/rightarrow.png').crop((0, 0, 40, 28)), (bx + 120, 76))
    s.alpha_composite(tmp)
    text(d, NAME, pw // 2, 176, (88, 88, 80), (168, 184, 184), 2)
    # Pocket buttons: 2 rows of 4 round touch buttons, an original design drawn by code with three
    # states (idle, selected, pressed). Colours come from the game's own pocket sheet; the emblem of each
    # pocket is the game's own colour pocket icon, enlarged by a whole number of screen pixels.
    d.rectangle((0, 216, pw - 1, 271 + DY_TOP), fill=ORANGE)
    sheet = img('Pictures/Bag/icon_pocket.png')
    for i in range(8):
        x, y = 3 + (i % 4) * 61, 226 + (i // 4) * 64
        state = 'selected' if i + 1 == POCKET else 'pressed' if i + 1 == PRESSED else 'idle'
        cell = sheet.crop((i * 28, 0, i * 28 + 28, 28))
        pocket_button(s, d, x, y, state, cell.getpixel((4, 6)), cell.getpixel((4, 22)), cell.getpixel((4, 13)), i)
    # item list
    lx = DX_LEFT
    for i, (_, name, qty) in enumerate(ITEMS):
        y = 22 + 32 * i
        text(d, name, 200 + lx, y, (88, 88, 80), (168, 184, 184))
        text(d, 'x%3d' % qty, 450 + lx + DX_LIST, y, (88, 88, 80), (168, 184, 184), 1)
    text(d, 'CERRAR MOCHILA', 200 + lx, 22 + 32 * len(ITEMS), (88, 88, 80), (168, 184, 184))
    s.alpha_composite(repeat_col(img('Pictures/Bag/cursor.png'), 141, DX_LIST), (184 + lx, 10 + 32 * SEL))
    # selected item: icon and description
    # Selected item. Its icon box grows (same frame, cut and extended) and sits centred in the taller
    # description box; the icon itself is drawn 3 screen pixels per pixel instead of 2 (see main).
    top = 288 + DY_TOP
    d.rectangle((16, top, 19, H - 1), fill=(80, 88, 88)); d.rectangle((20, top, 79, H - 1), fill=(144, 144, 152))
    box = repeat_row(repeat_col(src.crop((16, 304, 80, 368)), 32, BOX - 64), 32, BOX - 64)
    by = top + (H - top - BOX) // 2
    s.alpha_composite(box, (16, by))
    for i, line in enumerate(DESC): text(d, line, 16 + BOX + 12, top + 8 + 32 * i, (248, 248, 248), (0, 0, 0))
    assert s.size == (W, H), s.size
    return s, (16 + 4 + (BOX - 8) // 2, by + 4 + (BOX - 8) // 2)

if __name__ == '__main__':
    one, centre = bag()
    one = one.resize((W * 2, H * 2), Image.NEAREST)
    icon = img('Items/%s.png' % ITEMS[SEL][0]); icon = icon.resize((icon.width * 3, icon.height * 3), Image.NEAREST)
    one.alpha_composite(icon, (centre[0] * 2 - icon.width // 2, centre[1] * 2 - icon.height // 2))
    # Pocket emblems: the drawings of the game's own colour pocket icons (28 px, from its icon_pocket sheet) at
    # 3 screen pixels per pixel instead of 2: half as big again and still pixel-perfect. Darkened while pressed.
    sheet = img('Pictures/Bag/icon_pocket.png')
    for index, cx, cy, state in EMBLEMS:
        e = sheet.crop((index * 28, 0, index * 28 + 28, 28))
        px = e.load()                                               # keep the drawing only: the button face replaces the icon's own square
        for yy in range(28):
            for xx in range(28):
                if px[xx, yy][:3] not in GLYPH: px[xx, yy] = (0, 0, 0, 0)
                elif state == 'pressed': px[xx, yy] = tuple(int(v * 0.72) for v in px[xx, yy][:3]) + (255,)
        e = e.resize((84, 84), Image.NEAREST)
        one.alpha_composite(e, (cx * 2 - 42, cy * 2 - 42))
    if MODE:
        sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
        import ds_mock as m
        options = [('USAR', (88, 200, 88))] if MODE == 'battle' else [('USAR', (88, 200, 88)), ('DAR', (160, 112, 232)), ('TIRAR', (232, 96, 96))]
        pw2 = (186 + DX_LEFT) * 2; d = ImageDraw.Draw(one)
        d.rectangle((0, 0, pw2 - 3, (288 + DY_TOP) * 2 - 17), fill=ORANGE[:3])
        m.SHADOW = (176, 88, 48)
        col = Image.new('RGBA', (82, 132), (0, 0, 0, 0)); cd = ImageDraw.Draw(col)
        for n, (label, colour) in enumerate(options):
            m.button(cd, (1, n * 27 + 1, 80, n * 27 + 24), colour, 9, selected=(n == 0), lift=(1 if n == 0 else 0))
            m.pix_text(col, label, 41, n * 27 + 12 - (1 if n == 0 else 0), 1)
        m.button(cd, (1, 108, 80, 128), (56, 136, 232), 9); m.pix_text(col, 'CANCELAR', 41, 118, 1)   # drops the selection; CERRAR MOCHILA leaves the bag
        col = col.resize((col.width * 6, col.height * 6), Image.NEAREST); one.alpha_composite(col, ((pw2 - col.width) // 2, 14))
    one = one.convert('RGB')
    one.save(OUT.replace('.png', '-1to1.png'))                    # the screen pixel for pixel (1240x1080)
    out = Image.new('RGB', (W * 2 + 40, H * 2 + 40), (0, 0, 0)); out.paste(one, (20, 20))
    out.resize((out.width // 2, out.height // 2), Image.NEAREST).save(OUT)

# Mock-up of the bag during battle on the second screen. Same images, font and colours as the bag
# (ds_mock_bag.py), laid out for choosing fast: the pockets in one row at the top, the item list as wide as the
# screen with each item's own icon before its name (2x) and taller rows, and at the foot the description of the
# item under the cursor with USAR and VOLVER beside it. The list's frame and arrows are the game's, cut from its
# background and widened by repeating lines.
# The same layout serves the bag out of battle: 'world' shows the description with VOLVER; 'item' is the state
# after touching an item, where the foot shows that item's options in a row (USAR, DAR, TIRAR...) and CANCELAR.
# Needs the game folder. Usage: ds_mock_bag_battle.py <game folder> <out.png> <pixelfonts.txt> [battle|world|item [es|en|fr|de]]
import sys
from PIL import Image, ImageDraw
GAME, OUT, FONTS_TXT = sys.argv[1], sys.argv[2], sys.argv[3]
STATE = sys.argv[4] if len(sys.argv) > 4 else 'battle'
LANG = sys.argv[5] if len(sys.argv) > 5 else 'es'
sys.argv = sys.argv[:3]                                          # ds_mock_bag reads its own arguments on import
import ds_mock_bag as b
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m
W, H = 620, 540
CUT, TOP, ROW = 180, 92, 43                                       # background cut, room for the pockets, row pitch
GROW_LIST, GROW_DESC = 44, 24
if STATE in ('item', 'battle'): GROW_LIST, GROW_DESC = GROW_LIST - ROW, GROW_DESC + ROW   # the options take the last row's room: two rows of buttons
ITEMS = [('POTION', 'Poción', 5), ('SUPERPOTION', 'Superpoción', 2), ('ANTIDOTE', 'Antídoto', 3), ('PARALYZEHEAL', 'Antiparalizador', 1),
         ('AWAKENING', 'Despertar', 2), ('BURNHEAL', 'Antiquemar', 1), ('ICEHEAL', 'Antihielo', 1)]
DESC = ['Medicina en espray que', 'restaura 20 PS de un', 'Pokémon.']   # wrapped to the room left by the buttons
INK, INK_SH = (88, 88, 80), (168, 184, 184)

def screen():
    src = b.img('Pictures/Bag/bg_%d.png' % b.POCKET)
    part = src.crop((CUT, 4, 512, 384))                           # list, arrows and description box, without the left panel
    part = b.repeat_row(part, 292 - 4, GROW_DESC); part = b.repeat_row(part, 150, GROW_LIST)
    part = b.repeat_col(part, 300 - CUT, W - part.width)
    s = Image.new('RGBA', (W, H), b.ORANGE); s.paste(src.crop((0, 0, 1, 4)).resize((W, 4)), (0, 0)); s.paste(part, (0, TOP))
    assert TOP + part.height == H, (TOP + part.height)
    d = ImageDraw.Draw(s); d.fontmode = '1'
    d.rectangle((0, TOP, 186 - CUT - 1, TOP + 270 + GROW_LIST + 2), fill=b.ORANGE)   # what is left of the left panel beside the list
    sheet = b.img('Pictures/Bag/icon_pocket.png')
    for i in range(8):                                            # pockets: one row of the approved round buttons
        cell = sheet.crop((i * 28, 0, i * 28 + 28, 28))
        b.pocket_button(s, d, 62 + i * 62, 16, 'selected' if i + 1 == b.POCKET else 'idle', cell.getpixel((4, 6)), cell.getpixel((4, 22)), cell.getpixel((4, 13)), i)
    lx, ly, lw = 6, TOP + 10, W - 6 - 52                           # inside of the list frame
    cur = b.img('Pictures/Bag/cursor.png'); cur = b.repeat_col(cur, 141, lw - cur.width + 8)
    s.alpha_composite(cur, (lx - 2, ly - 12 + ROW * b.SEL))   # the image has 12 px of empty margin above its frame
    for i, (icon, name, qty) in enumerate(ITEMS[:6] if STATE in ('item', 'battle') else ITEMS):
        y = ly + ROW * i
        s.alpha_composite(b.img('Items/%s.png' % icon), (lx + 12, y - 3))
        b.text(d, name, lx + 68, y + 3, INK, INK_SH); b.text(d, 'x%3d' % qty, lx + lw - 24, y + 3, INK, INK_SH, 1)
    top = TOP + (288 - 4) + GROW_LIST
    lines = {'battle': ['Medicina en espray que restaura 20 PS de un', 'Pokémon.'], 'world': ['Medicina en espray que restaura 20 PS', 'de un Pokémon.'], 'item': []}[STATE]
    for i, line in enumerate(lines): b.text(d, line, 24, top + 10 + 32 * i, (248, 248, 248), (0, 0, 0))
    one = s.resize((W * 2, H * 2), Image.NEAREST)
    sheet = b.img('Pictures/Bag/icon_pocket.png')                 # pocket emblems at 3x, as on the bag
    for index, cx, cy, state in b.EMBLEMS:
        e = sheet.crop((index * 28, 0, index * 28 + 28, 28)); px = e.load()
        for yy in range(28):
            for xx in range(28):
                if px[xx, yy][:3] not in b.GLYPH: px[xx, yy] = (0, 0, 0, 0)
        one.alpha_composite(e.resize((84, 84), Image.NEAREST), (cx * 2 - 42, cy * 2 - 42))
    m.SHADOW = (72, 76, 84)
    green, blue = (88, 200, 88), (56, 136, 232)
    row = {'battle': [], 'world': [('VOLVER', blue, 48)],
           'item': []}[STATE]
    if STATE == 'battle':                                         # description on top, then one row of two wide buttons
        key = Image.new('RGBA', (202, 28), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
        m.button(kd, (2, 1, 99, 24), green, 9, selected=True); m.pix_text(key, 'USAR', 51, 13, 1)
        m.button(kd, (102, 1, 199, 24), blue, 9); m.pix_text(key, 'VOLVER', 151, 13, 1)
        key = key.resize((202 * 6, 28 * 6), Image.NEAREST); one.alpha_composite(key, (14, top * 2 + 12 + 27 * 6))
    elif STATE == 'item':
        # Options in a 2x2 grid of equal buttons, wide enough for the longest word of the game's four languages
        # (German REGISTRIEREN, French ENREGISTRER), so the layout is the same in all of them.
        words = {'es': ('USAR', 'DAR', 'TIRAR', 'CANCELAR'), 'de': ('BENUTZEN', 'GEBEN', 'WEGWERFEN', 'ABBRECHEN'),
                 'fr': ('UTILISER', 'DONNER', 'JETER', 'ANNULER'), 'en': ('USE', 'GIVE', 'TOSS', 'CANCEL')}[LANG]
        cols = (green, (160, 112, 232), (232, 96, 96), blue)
        key = Image.new('RGBA', (202, 56), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
        for n in range(4):
            x, y = 2 + (n % 2) * 100, 1 + (n // 2) * 27
            m.button(kd, (x, y, x + 97, y + 23), cols[n], 9, selected=(n == 0)); m.pix_text(key, words[n], x + 49, y + 12, 1)
        key = key.resize((202 * 6, 56 * 6), Image.NEAREST); one.alpha_composite(key, (14, top * 2 + 12))
    else:
        width = sum(w for _, _, w in row) + 4 * (len(row) - 1)
        key = Image.new('RGBA', (width + 2, 34), (0, 0, 0, 0)); kd = ImageDraw.Draw(key); x = 1
        for n, (label, colour, w) in enumerate(row):
            m.button(kd, (x, 1, x + w - 1, 29), colour, 9, selected=(n == 0 and STATE != 'world')); m.pix_text(key, label, x + w // 2, 15, 1)
            x += w + 4
        key = key.resize((key.width * 6, 34 * 6), Image.NEAREST); one.alpha_composite(key, (W * 2 - key.width - 56, top * 2 + 34))
    return one.convert('RGB')

if __name__ == '__main__':
    screen().save(OUT)                                            # real size: 1240x1080

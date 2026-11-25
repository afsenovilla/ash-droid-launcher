# Mock-up of the party screen on the second screen, built from the game's own party images and fonts
# (Graphics/Pictures/Party, the Pokémon icons, Power Green), with the colours used by UI_Party.
# - The panels are widened and made taller by repeating lines of the image (never scaled) and shown at 2x.
# - What goes inside them (ball, icon, name, HP bar and numbers, level, status) is shown at 3x, all of it at
#   the same scale, re-laid out for the panel's new shape. A full 3x copy of the game's layout would need
#   panels 768 px wide, which does not fit two columns on a 1240 px screen.
# - The game's 512x384 background is extended to 620x540 by repeating lines; the panel sockets drawn on it are
#   filled with the frame's own colour. Bottom band: help line and the back button (approved button style).
# Needs the game folder. Usage: ds_mock_party.py <game folder> <pixelfonts.txt> <out.png> [actions|item|battle|drag|summary|moves]
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
def tall(im, wide=WIDE): return repeat_col(repeat_row(repeat_row(im, 60, GROW), 46, GROW), 128, wide)
def slot_xy(i): return 8 + (i % 2) * 308, 6 + 16 * (i % 2) + 142 * (i // 2)
def kind(i, mon):
    if not mon: return 'Party/panel_blank.png'
    return 'Party/panel_%s%s%s.png' % ('round' if i == 0 else 'rect', '_faint' if mon[3] == 0 else '', '_sel' if i == SELECTED else '')

def contents(one, d, i, mon, pos=None, swap=False, room=0):     # room: extra content px on the right side               # positions in content pixels (1 = U screen px)
    x, y = pos or tuple(v * 2 for v in slot_xy(i))
    def at(cx, cy): return x + cx * U, y + cy * U
    name, species, lv, hp, total, gender = mon
    faint = hp == 0
    one.alpha_composite(big(img('Party/overlay_hp_back%s.png' % ('_swap' if swap else '_faint' if faint else ''))), at(52 + room, 36))
    one.alpha_composite(big(img('Party/icon_ball%s.PNG' % ('_sel' if i == SELECTED else ''))), at(1, 0))
    icon = Image.open(GAME + '/Graphics/Pokemon/Icons/' + species + '.png').convert('RGBA').crop((0, 0, 64, 64))
    one.alpha_composite(big(icon), at(10, 2))
    text(d, name, *at(80, 1), WHITE, DARK)
    g = ('♂', (0, 112, 248), (120, 184, 232)) if gender == 'm' else ('♀', (232, 32, 16), (248, 168, 184))
    text(d, g[0], *at(190 + room, 1), g[1], g[2], 1)
    if hp > 0:
        w = max(1, hp * 96.0 / total); w = int(round(w / 2)) * 2
        zone = 2 if hp <= total // 4 else 1 if hp <= total // 2 else 0
        one.alpha_composite(big(img('Party/overlay_hp.png').crop((0, zone * 8, w, zone * 8 + 8))), at(84 + room, 38))
    else:
        one.alpha_composite(big(img('statuses.png').crop((0, 16 * 5, 44, 16 * 6))), at(50, 66))
    text(d, '%d/%d' % (hp, total), *at(190 + room, 50), WHITE, DARK, 1)
    one.alpha_composite(big(img('Party/overlay_lv.png')), at(6, 68))
    text(d, str(lv), *at(28, 55), WHITE, DARK, font=SMALL)

def backdrop():
    s = repeat_row(repeat_row(repeat_col(repeat_col(img('Party/bg.PNG'), 511, SIDE), 0, SIDE), 370, BOTTOM), 320, MID)
    assert s.size == (W, H), s.size
    edge = s.crop((0, 330, W, 331))                               # one clean line of the frame, sides included
    for y in range(0, 330): s.paste(edge, (0, y))
    return s

HOUSE = ['.....#.....', '....###....', '...#####...', '..#######..', '.#########.', '###########',
         '.#########.', '.###...###.', '.###...###.', '.###...###.']
def footer(one, d, line, home=False):
    """Help line, the back button and, on screens more than one step away from home, a home button beside it."""
    text(d, line, 48, H * 2 - 124, WHITE, DARK, font=HELP, k=2)
    m.SHADOW = tuple(int(v * 0.6) for v in one.getpixel((W * 2 - 40, H * 2 - 20))[:3])
    key = Image.new('RGBA', (100, 26), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
    m.button(kd, (29, 1, 98, 22), (56, 136, 232), 9); m.pix_text(key, 'VOLVER', 63, 11, 1)
    if home:
        m.button(kd, (1, 1, 24, 22), (88, 200, 88), 9)
        for col, o in (((48, 48, 64), 1), ((255, 255, 255), 0)):
            for r, row in enumerate(HOUSE):
                for c, v in enumerate(row):
                    if v == '#': kd.point((7 + c + o, 6 + r + o), fill=col)
    key = key.resize((100 * 6, 26 * 6), Image.NEAREST); one.paste(key, (W * 2 - 100 * 6 - 8, H * 2 - 26 * 6 - 2), key)

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
# Switch is not a button: the order is changed by dragging on the party screen (see drag()).
ACTIONS = [('DATOS', (56, 136, 232)), ('OBJETO', (240, 168, 48)), ('DESTELLO', (64, 184, 184))]
BATTLE_ACTIONS = [('CAMBIAR', (88, 200, 88)), ('DATOS', (56, 136, 232))]   # what the game offers in battle (Switch In only if it can fight)
ITEM_ACTIONS = [('USAR', (88, 200, 88)), ('DAR', (240, 168, 48)), ('QUITAR', (232, 96, 96))]   # QUITAR only when it holds something
def actions(options=None, line=None, held=False):
    options = options or ACTIONS
    """After touching a Pokémon: its panel alone on top (wider, so the HP bar clears the icon), the options
    stacked under it as wide medium buttons, back at the bottom."""
    CARD = 356; room = (CARD - 296) * 2 // U                       # panel width in logical px; extra content px
    s = backdrop(); x0 = (W - CARD) // 2
    s.alpha_composite(tall(img(kind(SELECTED, PARTY[SELECTED])), CARD - 256), (x0, 8))
    one = s.resize((W * 2, H * 2), Image.NEAREST); d = ImageDraw.Draw(one); d.fontmode = '1'
    contents(one, d, SELECTED, PARTY[SELECTED], pos=(x0 * 2, 16), room=room)
    if held:                                                       # the game's held item mark, by the icon as in the game
        one.alpha_composite(big(img('Party/icon_item.png')), (x0 * 2 + 44 * U, 16 + 44 * U))
    m.SHADOW = tuple(int(v * 0.7) for v in one.getpixel((W, 400))[:3])
    bw = CARD * 2 // 6                                             # as wide as the panel
    grid = Image.new('RGBA', (bw + 2, len(options) * 31), (0, 0, 0, 0)); gd = ImageDraw.Draw(grid)
    for n, (label, col) in enumerate(options):
        by = n * 31
        m.button(gd, (1, by + 1, bw, by + 27), col, 9, selected=(n == 0), lift=0)
        m.pix_text(grid, label, (bw + 1) // 2, by + 14, 1)
    grid = grid.resize((grid.width * 6, grid.height * 6), Image.NEAREST)
    one.paste(grid, ((W * 2 - grid.width) // 2, 314), grid)
    footer(one, d, line or '¿Qué hacer con %s?' % PARTY[SELECTED][0], home=True)
    return one.convert('RGB')

PAGES = ['INFORMACIÓN', 'NOTAS', 'HABILIDADES', 'MOVIMIENTOS', 'CINTAS']   # the game's five summary pages
def summary(page=0):
    """DATOS: the game's own summary screen is shown on the top screen; the second screen is its remote:
    which Pokémon (previous / next), which page, and back."""
    s = backdrop(); one = s.resize((W * 2, H * 2), Image.NEAREST); d = ImageDraw.Draw(one); d.fontmode = '1'
    m.SHADOW = tuple(int(v * 0.7) for v in one.getpixel((W, 400))[:3])
    name, species = PARTY[SELECTED][0], PARTY[SELECTED][1]
    icon = big(Image.open(GAME + '/Graphics/Pokemon/Icons/' + species + '.png').convert('RGBA').crop((0, 0, 64, 64)))
    w = icon.width + int(d.textlength(name, font=FONT)); x = (W * 2 - w) // 2
    one.alpha_composite(icon, (x, -6)); text(d, name, x + icon.width, 30, (64, 64, 80), (176, 200, 200))
    UW, X0 = 145, 13                                               # arrows at the ends, page buttons centred
    ui = Image.new('RGBA', (UW + 2, 30 + 5 * 23), (0, 0, 0, 0)); ud = ImageDraw.Draw(ui)
    for left in (True, False):                                     # previous / next Pokémon
        bx = 1 if left else UW - 25; usable = not (left and SELECTED == 0)
        m.button(ud, (bx, 1, bx + 24, 25), (56, 136, 232) if usable else (168, 168, 196), 9)
        for k in range(5):                                         # arrowhead
            xx = bx + 10 + k if left else bx + 14 - k
            for col, o in (((48, 48, 64), 1), ((255, 255, 255), 0)): ud.line((xx + o, 13 - k + o, xx + o, 13 + k + o), fill=col)
    for n, label in enumerate(PAGES):
        by = 30 + n * 23
        m.button(ud, (X0 + 1, by, X0 + 119, by + 20), (240, 168, 48) if n == page else (168, 168, 196), 9, selected=(n == page), lift=0)
        m.pix_text(ui, label, X0 + 60, by + 10, 1)
    ui = ui.resize((ui.width * 6, ui.height * 6), Image.NEAREST)
    one.paste(ui, ((W * 2 - ui.width) // 2, 8), ui)
    footer(one, d, 'Los datos se ven arriba.', home=True)
    return one.convert('RGB')

# name, type, category, power, accuracy, PP, description (wrapped to the cell)
MOVE_TYPES = {'Eléctrico': (248, 208, 48), 'Normal': (168, 168, 120)}
MOVE_CATS = {'Físico': (232, 96, 56), 'Especial': (104, 112, 200), 'Estado': (144, 144, 160)}
MOVES = [('Impactrueno', 'Eléctrico', 'Especial', '40', '100%', '30/30', 'Una descarga eléctrica cae sobre el objetivo. Puede paralizarlo.'),
         ('Ataque Rápido', 'Normal', 'Físico', '40', '100%', '30/30', 'Ataque de extrema rapidez que golpea siempre primero.'),
         ('Látigo', 'Normal', 'Estado', '---', '100%', '30/30', 'Menea la cola con gracia para bajar la Defensa del rival.'),
         ('Onda Trueno', 'Eléctrico', 'Estado', '---', '90%', '20/20', 'Una ligera descarga que paraliza al objetivo.')]
MOVE_CELLS = [(4, 3, 101, 74), (105, 3, 202, 74), (4, 78, 101, 149), (105, 78, 202, 149)]
def moves():
    """MOVIMIENTOS: the Pokémon's four moves at once in a 2x2 grid: name, the game's type and category icons, power,
    accuracy and PP (no description). Cards are buttons in the approved style with a thin frame in the type's
    colour around a cream panel. Dragging one onto another swaps them."""
    S = m.S; sh = m.shade
    a = Image.new('RGB', (m.AW, m.AH)); d = ImageDraw.Draw(a); m.SHADOW = (124, 124, 168); m.background(d)
    for (name, ty, cat, power, acc, pp, desc), (x0, y0, x1, y1) in zip(MOVES, MOVE_CELLS):
        col = MOVE_TYPES[ty]; m.shadow(d, (x0, y0, x1, y1), 8, 0)
        w, h = x1 - x0 + 1, y1 - y0 + 1; outer = m.rounded((w, h), 8)
        a.paste((56, 56, 72), (x0, y0), outer); a.paste((240, 240, 248), (x0, y0), m.inset(outer, 1))
        a.paste(sh(col, 0.6), (x0, y0), m.inset(outer, 3)); a.paste(col, (x0, y0), m.inset(outer, 4))
        a.paste((248, 248, 236), (x0, y0), m.inset(outer, 6))
    m.button(d, (134, 155, 201, 177), (56, 136, 232), 9); m.pix_text(a, 'VOLVER', 167, 166, 1)
    m.button(d, (106, 155, 129, 177), (88, 200, 88), 9)
    for c2, off in (((48, 48, 64), 1), ((255, 255, 255), 0)):
        for r, row in enumerate(HOUSE):
            for c, v in enumerate(row):
                if v == '#': d.point((112 + c + off, 161 + r + off), fill=c2)
    one = m.up(a).convert('RGBA'); d = ImageDraw.Draw(one)
    ink, shd = (64, 64, 80), (168, 168, 192)
    types, cats = img('types.png'), img('category.png')              # the game's own type and category icons, at 3x
    TYPE_ROW, CAT_ROW = {'Normal': 0, 'Eléctrico': 13}, {'Físico': 0, 'Especial': 1, 'Estado': 2}
    for (name, ty, cat, power, acc, pp, desc), (x0, y0, x1, y1) in zip(MOVES, MOVE_CELLS):
        cx = (x0 + x1 + 1) / 2 * S; size = 78
        while ImageFont.truetype(GAME + '/Fonts/power green.ttf', size).getlength(name) > (x1 - x0 - 20) * S: size -= 13
        m.ttf(d, name, cx, (y0 + 13.5) * S, size, ink, shd)
        tr, cr = TYPE_ROW[ty] * 28, CAT_ROW[cat] * 28
        one.alpha_composite(big(types.crop((0, tr, 64, tr + 28))), (int(cx) - 192 - 9, (y0 + 22) * S))
        one.alpha_composite(big(cats.crop((0, cr, 64, cr + 28))), (int(cx) + 9, (y0 + 22) * S))
        for n, (label, value) in enumerate((('Potencia', power), ('Precisión', acc), ('PP', pp))):
            y = (y0 + 42.5 + 8.5 * n) * S
            m.ttf(d, label, int(cx) - 201, y, 52, ink, shd, 'lm'); m.ttf(d, value, int(cx) + 201 - 4, y, 52, ink, shd, 'rm')   # flush with the icons' outer edges
    name, species = PARTY[SELECTED][0], PARTY[SELECTED][1]         # whose moves these are: icon and name at 3x
    icon = big(Image.open(GAME + '/Graphics/Pokemon/Icons/' + species + '.png').convert('RGBA').crop((0, 0, 64, 64)))
    one.alpha_composite(icon, (12, 1080 - 198))
    m.ttf(d, name, 12 + icon.width + 6, 1080 - 96, 78, ink, (176, 176, 200), 'lm')
    return one.convert('RGB')

# example data for the Pokémon looked at in battle
BATTLE_INFO = {'types': [0, 2], 'ability': 'Vista Lince', 'item': 'Ninguno',
               'stats': [('Ataque', '9'), ('Defensa', '8'), ('Velocidad', '10'), ('At. Esp.', '8'), ('Def. Esp.', '8')],
               'moves': [('Placaje', 0, '35/35'), ('Ataque Arena', 4, '15/15')]}
def battle_info():
    """In battle, touching a Pokémon of the party shows its data right here instead of opening the summary:
    its card, then only what matters to decide a switch: its types and its moves with their PP (the game's
    type icons at 3x). CAMBIAR sits at the bottom beside home and VOLVER; it is greyed out if it cannot fight."""
    CARD = 356; room = (CARD - 296) * 2 // U
    s = backdrop(); x0 = (W - CARD) // 2
    s.alpha_composite(tall(img(kind(SELECTED, PARTY[SELECTED])), CARD - 256), (x0, 8))
    one = s.resize((W * 2, H * 2), Image.NEAREST); d = ImageDraw.Draw(one); d.fontmode = '1'
    contents(one, d, SELECTED, PARTY[SELECTED], pos=(x0 * 2, 16), room=room)
    # cream panel for the data, as on the move cards: dark text with a grey shadow reads heavier on it
    pw, ph = 146, 96; outer = m.rounded((pw, ph), 8); panel = Image.new('RGBA', (pw, ph), (0, 0, 0, 0))
    panel.paste((56, 56, 72), (0, 0), outer); panel.paste((240, 240, 248), (0, 0), m.inset(outer, 1)); panel.paste((248, 248, 236), (0, 0), m.inset(outer, 3))
    panel = panel.resize((pw * 6, ph * 6), Image.NEAREST); one.paste(panel, ((W * 2 - pw * 6) // 2, 300), panel)
    types = img('types.png'); ink, shd = (64, 64, 80), (168, 168, 192)
    L, R = 224, 1016
    def icon(row, x, cy):                                          # the game's type icon at 3x
        one.alpha_composite(big(types.crop((0, row * 28, 64, row * 28 + 28))), (x, cy - 42))
    m.ttf(d, 'Tipo:', L, 372, 65, ink, shd, 'lm')
    for n, row in enumerate(BATTLE_INFO['types']): icon(row, L + 210 + 204 * n, 372)
    d.rectangle((L, 432, R, 437), fill=(208, 208, 196))
    for n in range(4):                                             # its moves: type, name, PP
        y = 498 + 100 * n
        if n < len(BATTLE_INFO['moves']):
            name, row, pp = BATTLE_INFO['moves'][n]
            icon(row, L, y); m.ttf(d, name, L + 210, y, 65, ink, shd, 'lm'); m.ttf(d, pp, R, y, 65, ink, shd, 'rm')
        else: m.ttf(d, '-', L + 210, y, 65, ink, shd, 'lm')
    m.SHADOW = tuple(int(v * 0.6) for v in one.getpixel((W * 2 - 40, H * 2 - 20))[:3])
    key = Image.new('RGBA', (206, 26), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
    m.button(kd, (2, 1, 100, 22), (88, 200, 88), 9, selected=True); m.pix_text(key, 'CAMBIAR', 51, 11, 1)
    m.button(kd, (106, 1, 129, 22), (88, 200, 88), 9)
    for col, o in (((48, 48, 64), 1), ((255, 255, 255), 0)):
        for r, row in enumerate(HOUSE):
            for c, v in enumerate(row):
                if v == '#': kd.point((112 + c + o, 6 + r + o), fill=col)
    m.button(kd, (134, 1, 203, 22), (56, 136, 232), 9); m.pix_text(key, 'VOLVER', 168, 11, 1)
    key = key.resize((206 * 6, 26 * 6), Image.NEAREST); one.paste(key, (2, H * 2 - 26 * 6 - 2), key)
    return one.convert('RGB')

def drag():
    """Reordering by dragging: the held panel follows the finger with a shadow under it, its slot stays empty and
    the panel under it takes the game's own 'switching' colours. Letting go swaps the two."""
    s = backdrop(); held, over = 1, 0
    mons = [PARTY[i] if i < len(PARTY) else None for i in range(6)]
    for i, mon in enumerate(mons):
        name = 'Party/panel_blank.png' if i == held else 'Party/panel_round_swap_sel.png' if i == over else kind(i, mon)
        s.alpha_composite(tall(img(name)), slot_xy(i))
    one = s.resize((W * 2, H * 2), Image.NEAREST).convert('RGBA'); d = ImageDraw.Draw(one); d.fontmode = '1'
    for i, mon in enumerate(mons):
        if mon and i != held: contents(one, d, i, mon, swap=(i == over))
    pos = (300, 150); panel = tall(img('Party/panel_rect_swap_sel2.png')); panel = panel.resize((panel.width * 2, panel.height * 2), Image.NEAREST)
    shade = Image.new('RGBA', panel.size, (0, 40, 48, 110)); shade.putalpha(panel.getchannel('A').point(lambda v: 110 if v else 0))
    one.alpha_composite(shade, (pos[0] + 16, pos[1] + 20)); one.alpha_composite(panel, pos)
    d = ImageDraw.Draw(one); d.fontmode = '1'; contents(one, d, held, mons[held], pos=pos, swap=True)
    footer(one, d, 'Suelta para cambiar.')
    return one.convert('RGB')

if __name__ == '__main__':
    if MODE == 'battle': SELECTED = 1                              # in battle: a Pokémon that is not out
    {'battle': battle_info, 'actions': actions, 'drag': drag, 'summary': summary, 'moves': moves,
     'item': lambda: actions(ITEM_ACTIONS, '¿Qué hacer con el objeto?', True)}.get(MODE, screen)().save(OUT)   # real size: 1240x1080

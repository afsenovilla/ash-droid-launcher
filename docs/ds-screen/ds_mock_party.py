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
        m.button(gd, (1, by + 1, bw, by + 27), col, 9, selected=(n == 0), lift=(1 if n == 0 else 0))
        m.pix_text(grid, label, (bw + 1) // 2, by + 14 - (1 if n == 0 else 0), 1)
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
        m.button(ud, (X0 + 1, by, X0 + 119, by + 20), (240, 168, 48) if n == page else (168, 168, 196), 9, selected=(n == page), lift=(1 if n == page else 0))
        m.pix_text(ui, label, X0 + 60, by + 10 - (1 if n == page else 0), 1)
    ui = ui.resize((ui.width * 6, ui.height * 6), Image.NEAREST)
    one.paste(ui, ((W * 2 - ui.width) // 2, 8), ui)
    footer(one, d, 'Los datos se ven arriba.', home=True)
    return one.convert('RGB')

MOVES = [('IMPACTRUENO', (248, 208, 48)), ('ATAQUE RÁPIDO', (168, 168, 120)), ('LÁTIGO', (168, 168, 120)), ('ONDA TRUENO', (248, 208, 48))]
def moves():
    """MOVIMIENTOS page of the summary: touching the page's button again lists the Pokémon's moves, each in its
    type's colour. Touching one shows its details on the top screen (the game's own move detail view)."""
    s = backdrop(); one = s.resize((W * 2, H * 2), Image.NEAREST); d = ImageDraw.Draw(one); d.fontmode = '1'
    m.SHADOW = tuple(int(v * 0.7) for v in one.getpixel((W, 400))[:3])
    name, species = PARTY[SELECTED][0], PARTY[SELECTED][1]
    icon = big(Image.open(GAME + '/Graphics/Pokemon/Icons/' + species + '.png').convert('RGBA').crop((0, 0, 64, 64)))
    w = icon.width + int(d.textlength(name, font=FONT)); x = (W * 2 - w) // 2
    one.alpha_composite(icon, (x, -6)); text(d, name, x + icon.width, 30, (64, 64, 80), (176, 200, 200))
    ui = Image.new('RGBA', (121, 4 * 29), (0, 0, 0, 0)); ud = ImageDraw.Draw(ui)
    for n, (label, col) in enumerate(MOVES):
        by = n * 29
        m.button(ud, (1, by, 119, by + 25), col, 9, selected=(n == 0), lift=(1 if n == 0 else 0))
        m.pix_text(ui, label, 60, by + 12 - (1 if n == 0 else 0), 1)
    ui = ui.resize((ui.width * 6, ui.height * 6), Image.NEAREST)
    one.paste(ui, ((W * 2 - ui.width) // 2, 178), ui)
    footer(one, d, 'Elige un movimiento.', home=True)
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
    {'battle': lambda: actions(BATTLE_ACTIONS), 'actions': actions, 'drag': drag, 'summary': summary, 'moves': moves,
     'item': lambda: actions(ITEM_ACTIONS, '¿Qué hacer con el objeto?', True)}.get(MODE, screen)().save(OUT)   # real size: 1240x1080

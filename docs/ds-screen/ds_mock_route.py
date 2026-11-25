# Mock-up of the wild Pokémon sheet of a route on the second screen, with the game's real data for Route 1
# (Data/encounters.dat, map 76) and the game's own Pokémon icons. Fire Ash only has Land, Cave and the three
# rods as encounter types (no surfing and no time-of-day tables), so those are the only tabs. Entries of the
# same species are merged (rates added, level range widened). Species not seen yet: silhouette and "???".
# Needs the game folder. Usage: ds_mock_route.py <game folder> <pixelfonts.txt> <out.png>
import sys
from PIL import Image, ImageDraw
GAME, FONTS_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m
import ds_mock_overworld as o
S, AW, AH = m.S, m.AW, m.AH

LAND = [(20, 'PIDGEY', 3, 5), (20, 'SPEAROW', 3, 5), (10, 'MANKEY', 2, 5), (10, 'PIDGEY', 2, 4), (10, 'POLIWAG', 3, 5),
        (10, 'RATTATA', 2, 4), (10, 'SANDSHREW', 2, 5), (4, 'RATTATA', 2, 5), (4, 'SPEAROW', 2, 5), (1, 'HOOH', 50, 50),
        (1, 'SPEAROW', 6, 6)]                                      # the game's table, as is
NAMES = {'HOOH': 'Ho-Oh'}
SEEN = {'PIDGEY', 'SPEAROW', 'RATTATA', 'POLIWAG'}                 # example Pokédex

def merged(table):
    out = {}
    for pct, sp, lo, hi in table:
        e = out.setdefault(sp, [0, lo, hi]); e[0] += pct; e[1] = min(e[1], lo); e[2] = max(e[2], hi)
    return sorted(((sp, *v) for sp, v in out.items()), key=lambda r: -r[1])

def icon(species, seen):
    im = Image.open(GAME + '/Graphics/Pokemon/Icons/' + species + '.png').convert('RGBA').crop((0, 0, 64, 64))
    if not seen:                                                   # silhouette: same shape, one flat colour
        flat = Image.new('RGBA', im.size, (72, 72, 96, 255)); flat.putalpha(im.getchannel('A').point(lambda v: 255 if v else 0)); im = flat
    return im.resize((128, 128), Image.NEAREST)

def screen():
    rowsdata = merged(LAND); shown = rowsdata[:5]
    a = Image.new('RGB', (AW, AH)); d = ImageDraw.Draw(a); m.background(d); o.topbar(d)
    for i, (t, col, on) in enumerate([('HIERBA', (88, 200, 88), True), ('PESCA', (64, 184, 184), False)]):
        b = (4 + i * 100, 17, 4 + i * 100 + 97, 38)
        m.button(d, b, col if on else (168, 168, 196), 9, rim=1); m.pix_text(a, t, (b[0] + b[2]) // 2, (b[1] + b[3]) // 2, 1)
    rows = [(4, 41 + i * 23, 196, 41 + i * 23 + 21) for i in range(5)]
    for (sp, pct, lo, hi), b in zip(shown, rows):
        o.panel(a, d, b, r=9)
        d.rounded_rectangle((b[0] + 112, b[1] + 8, b[0] + 154, b[1] + 14), 2, fill=(56, 56, 72))
        d.rectangle((b[0] + 113, b[1] + 9, b[0] + 113 + max(1, int(40 * pct / 100)), b[1] + 13), fill=(96, 208, 96))
    d.rounded_rectangle((199, 43, 202, 152), 1, fill=(168, 168, 196))                       # scroll bar: 5 of 7 rows
    d.rounded_rectangle((199, 43, 202, 43 + int(109 * 5 / len(rowsdata))), 1, fill=(88, 88, 120))
    m.button(d, (134, 157, 201, 178), (56, 136, 232), 9); m.pix_text(a, 'VOLVER', 167, 167, 1)
    big = m.up(a); d = ImageDraw.Draw(big)
    m.ttf(d, 'RUTA 1', 6 * S, 7 * S, 52, (255, 255, 255), (40, 40, 64), 'lm')
    m.ttf(d, 'Pokémon salvajes', (AW - 6) * S, 7 * S, 52, (255, 255, 255), (40, 40, 64), 'rm')
    for (sp, pct, lo, hi), b in zip(shown, rows):
        seen = sp in SEEN
        ic = icon(sp, seen); big.paste(ic, ((b[0] + 2) * S, b[1] * S - 2), ic)
        name = NAMES.get(sp, sp.capitalize()) if seen else '???'
        lv = 'Nv %d' % lo if lo == hi else 'Nv %d-%d' % (lo, hi)
        m.ttf(d, name, (b[0] + 26) * S, (b[1] + 6.5) * S, 52, (64, 64, 80), (176, 176, 200), 'lm')
        m.ttf(d, lv, (b[0] + 26) * S, (b[1] + 15) * S, 52, (104, 104, 128), (200, 200, 220), 'lm')
        m.ttf(d, str(pct) + '%', (b[2] - 6) * S, (b[1] + 11) * S, 52, (64, 64, 80), (176, 176, 200), 'rm')
    m.ttf(d, '??? = sin ver', 8 * S, 168 * S, 52, (64, 64, 80), (176, 176, 200), 'lm')
    return big

if __name__ == '__main__':
    screen().save(OUT)                                             # real size

# Second-screen overworld mock-ups. Usage: ds_mock_overworld.py <pixelfonts.txt> <game font .ttf> <out.png> [home|map|route]
# 'home' was approved as an MVP on 2026-11-25; 'map' is a proposal.
# Pokémon icons are placeholders here: on the device they are the game's own party icons.
import sys
from PIL import Image, ImageDraw, ImageFont
import ds_mock as m
S, AW, AH = m.S, m.AW, m.AH

def slot(a, d, box, filled):
    x0, y0, x1, y1 = box; w, h = x1-x0+1, y1-y0+1
    outer = m.rounded((w, h), 10)                                   # capsule, like the buttons and the route sheet rows
    d.rounded_rectangle((x0+1, y0+2, x1-1, y1+2), 10, fill=m.SHADOW)
    a.paste((56, 56, 72), (x0, y0), outer)
    a.paste((240, 240, 248), (x0, y0), m.inset(outer, 1))
    a.paste((232, 228, 248) if filled else (196, 196, 222), (x0, y0), m.inset(outer, 2))
    if filled:
        d.rounded_rectangle((x0+27, y0+15, x1-9, y0+20), 2, fill=(56, 56, 72))  # HP bar

PARTY = [('Pikachu', 5, 18, 18, None), ('Pidgey', 4, 9, 16, None), ('Rattata', 3, 3, 13, 'PAR'), ('Caterpie', 3, 0, 12, None)]
# The profile button carries the player's name, as the game's own menu does
ACTIONS = [('POKéMON', (248, 144, 200)), ('MOCHILA', (240, 168, 48)), ('MAPA', (88, 200, 88)), ('ASH', (64, 184, 184)), ('GUARDAR', (56, 136, 232))]

def home():
    """Overworld home. Left: the party as six of the game's own party panels (its panel, ball, HP bar, level and
    status images; the HP bar and the status icon at 3x, the Pokémon's icon at 160 px, the rest at 2x; no HP numbers here), made wider by repeating a column and lower by leaving out lines of their two flat
    bands, so six fit in a column. Right: the five buttons, drawn by code. Needs the game folder (the one above
    the font's)."""
    import os
    from PIL import ImageFont
    game = os.path.dirname(os.path.dirname(m.TTF)); pic = lambda p: Image.open(os.path.join(game, 'Graphics', p)).convert('RGBA')
    font = ImageFont.truetype(m.TTF, 26); small = ImageFont.truetype(os.path.join(game, 'Fonts', 'power green small.ttf'), 26)
    a = Image.new('RGB', (AW, AH)); d = ImageDraw.Draw(a); m.background(d)
    d.rectangle((0, 0, AW, 14), fill=(72, 72, 104)); d.line((0, 15, AW, 15), fill=(240, 240, 248))
    btns = [(118, 20 + i*31, 201, 20 + i*31 + 27) for i in range(5)]   # wide enough for SAUVEGARDER / SPEICHERN
    for (label, col), b in zip(ACTIONS, btns):
        m.button(d, b, col, 9)
        m.pix_text(a, label, (b[0]+b[2])//2, (b[1]+b[3])//2, 1)
    big = m.up(a).convert('RGBA'); d = ImageDraw.Draw(big)
    m.ttf(d, 'RUTA 1', 6*S, 7*S, 52, (255, 255, 255), (40, 40, 64), 'lm')
    x = (AW - 6) * S                                              # the clock, with air on both sides of the colon
    for part in ('34', ':', '12'):
        m.ttf(d, part, x, 7*S, 52, (255, 255, 255), (40, 40, 64), 'rm'); x -= int(ImageFont.truetype(m.TTF, 52).getlength(part)) + 8
    PW, PH, PITCH = 340, 80, 81
    marks, bars = [], []                                          # status icons and HP bars, pasted afterwards at 3x
    def compact(im):                                              # 256x98 -> PW x PH along lines where the image is flat
        top, mid, low = im.crop((0, 0, 256, 30)), im.crop((0, 39, 256, 70)), im.crop((0, 79, 256, 98))
        out = Image.new('RGBA', (256, PH)); out.paste(top, (0, 0)); out.paste(mid, (0, 30)); out.paste(low, (0, 61))
        wide = Image.new('RGBA', (PW, PH)); wide.paste(out.crop((0, 0, 128, PH)), (0, 0))
        wide.paste(out.crop((128, 0, 129, PH)).resize((PW - 256, PH)), (128, 0)); wide.paste(out.crop((128, 0, 256, PH)), (128 + PW - 256, 0)); return wide
    def text(dd, t, x, y, base, shadow, align=0, f=font):
        w = dd.textlength(t, font=f); x = x - w if align == 1 else x; y += 10
        for dx, dy in ((2, 0), (0, 2), (2, 2)): dd.text((x + dx, y + dy), t, font=f, fill=shadow, anchor='la')
        dd.text((x, y), t, font=f, fill=base, anchor='la')
    col = Image.new('RGBA', (PW, 6 * PITCH), (0, 0, 0, 0)); cd = ImageDraw.Draw(col); cd.fontmode = '1'
    over = Image.new('RGBA', col.size, (0, 0, 0, 0)); od = ImageDraw.Draw(over); od.fontmode = '1'   # the level, drawn over the icon
    mons = []
    white, dark = (248, 248, 248), (40, 40, 40)
    for i in range(6):
        y = i * PITCH
        if i >= len(PARTY): col.alpha_composite(compact(pic('Pictures/Party/panel_blank.png')), (0, y)); continue
        n, lv, hp, tot, st = PARTY[i]; faint = hp == 0
        col.alpha_composite(compact(pic('Pictures/Party/panel_rect%s.png' % ('_faint' if faint else ''))), (0, y))
        bars.append((y, faint, hp, tot))
        col.alpha_composite(pic('Pictures/Party/icon_ball.PNG'), (8, y + 2))
        mons.append((n, y))
        text(cd, n, 106, y + 6, white, dark)
        row = 5 if faint else {'PAR': 3}.get(st)                   # row of the game's statuses.png (example: paralysed; 5 = fainted)
        if row is not None: marks.append((row, y))
        over.alpha_composite(pic('Pictures/Party/overlay_lv.png'), (12, y + 60)); text(od, str(lv), 34, y + 47, white, dark, f=small)
    col = col.resize((PW * 2, col.height * 2), Image.NEAREST); big.alpha_composite(col, (10, 102))
    for n, y in mons:
        # The game's icons are 32x32 drawings stored with doubled pixels, so they can be shown at 5 screen px per
        # drawn pixel (160 px): between the file at 2x (128) and at 3x (192), and still pixel-perfect.
        ic = pic('Pokemon/Icons/%s.png' % n.upper()).crop((0, 0, 64, 64)).resize((32, 32), Image.NEAREST).resize((160, 160), Image.NEAREST)
        big.alpha_composite(ic, (10 + 124 - 80, 102 + y * 2 + 72 - 80))
    big.alpha_composite(over.resize((PW * 2, over.height * 2), Image.NEAREST), (10, 102))
    for y, faint, hp, tot in bars:                                # the game's HP bar at 3x
        x0, y0 = 10 + 212, 102 + y * 2 + 90
        back = pic('Pictures/Party/overlay_hp_back%s.png' % ('_faint' if faint else '')); big.alpha_composite(back.resize((back.width * 3, back.height * 3), Image.NEAREST), (x0, y0))
        if hp:
            w = int(round(hp * 96.0 / tot / 2)) * 2; zone = 2 if hp <= tot // 4 else 1 if hp <= tot // 2 else 0
            fill = pic('Pictures/Party/overlay_hp.png').crop((0, zone * 8, w, zone * 8 + 8)); big.alpha_composite(fill.resize((w * 3, 24), Image.NEAREST), (x0 + 96, y0 + 6))
    for row, y in marks:
        mark = pic('Pictures/statuses.png').crop((0, 16 * row, 44, 16 * row + 16)).resize((132, 48), Image.NEAREST)
        big.alpha_composite(mark, (10 + PW * 2 - 132 - 28, 102 + y * 2 + 24))
    return big.convert('RGB')

def topbar(d, AWd=AW):
    d.rectangle((0, 0, AW, 14), fill=(72, 72, 104)); d.line((0, 15, AW, 15), fill=(240, 240, 248))

def region_map():
    """Map view. The map drawn here is an invented placeholder: on the device it is the game's own region map
    image, enlarged by a whole factor, with the marker on the player's position."""
    a = Image.new('RGB', (AW, AH)); d = ImageDraw.Draw(a); m.background(d); topbar(d)
    x0, y0, x1, y1 = 22, 21, 183, 129                       # 162x109 art px: a 480x320 map at 2x on the Thor
    d.rounded_rectangle((x0-2, y0, x1+2, y1+5), 4, fill=m.SHADOW)
    d.rectangle((x0-2, y0-2, x1+2, y1+2), fill=(56, 56, 72)); d.rectangle((x0-1, y0-1, x1+1, y1+1), fill=(240, 240, 248))
    d.rectangle((x0, y0, x1, y1), fill=(104, 168, 232))
    land = [(30, 40), (60, 28), (100, 30), (120, 24), (160, 34), (176, 60), (170, 100), (140, 122), (96, 116), (70, 124), (40, 110), (28, 80)]
    d.polygon(land, fill=(120, 200, 112)); d.polygon([(60, 50), (90, 44), (104, 60), (84, 74), (62, 68)], fill=(96, 176, 96))
    route = (232, 216, 152)
    for p, q in [((48, 100), (48, 60)), ((48, 60), (96, 60)), ((96, 60), (96, 100)), ((96, 100), (150, 100)), ((150, 100), (150, 50)), ((96, 60), (150, 50))]:
        d.line((p, q), fill=route, width=3)
    for tx, ty in [(48, 100), (48, 60), (96, 60), (96, 100), (150, 100), (150, 50)]:
        d.rectangle((tx-3, ty-3, tx+3, ty+3), fill=(56, 56, 72)); d.rectangle((tx-2, ty-2, tx+2, ty+2), fill=(232, 80, 72))
    px, py = 48, 80                                          # player marker (bounces like the battle cursor)
    d.ellipse((px-4, py-9, px+4, py-1), fill=(56, 56, 72)); d.ellipse((px-3, py-8, px+3, py-2), fill=(248, 248, 255)); d.ellipse((px-1, py-6, px+1, py-4), fill=(232, 80, 72))
    d.polygon([(px-2, py-2), (px+2, py-2), (px, py+1)], fill=(56, 56, 72))
    panel = (4, 138, 128, 174); w, h = panel[2]-panel[0]+1, panel[3]-panel[1]+1; outer = m.rounded((w, h), 6)
    d.rounded_rectangle((panel[0]+1, panel[1]+2, panel[2]-1, panel[3]+2), 6, fill=m.SHADOW)
    a.paste((56, 56, 72), panel[:2], outer); a.paste((240, 240, 248), panel[:2], m.inset(outer, 1)); a.paste((232, 228, 248), panel[:2], m.inset(outer, 2))
    m.button(d, (134, 140, 201, 172), (56, 136, 232), 9); m.pix_text(a, 'VOLVER', 167, 156, 1)
    big = m.up(a); d = ImageDraw.Draw(big)
    m.ttf(d, 'MAPA', 6*S, 7*S, 52, (255, 255, 255), (40, 40, 64), 'lm')
    m.ttf(d, 'KANTO', (AW-6)*S, 7*S, 52, (255, 255, 255), (40, 40, 64), 'rm')
    m.ttf(d, 'RUTA 1', 12*S, 149*S, 52, (64, 64, 80), (176, 176, 200), 'lm')
    m.ttf(d, 'Estás aquí · Toca para ver sus Pokémon', 12*S, 164*S, 39, (112, 112, 136), None, 'lm')
    return big

def panel(a, d, box, fill=(232, 228, 248), r=6):
    w, h = box[2]-box[0]+1, box[3]-box[1]+1; outer = m.rounded((w, h), r)
    d.rounded_rectangle((box[0]+1, box[1]+2, box[2]-1, box[3]+2), r, fill=m.SHADOW)
    a.paste((56, 56, 72), box[:2], outer); a.paste((240, 240, 248), box[:2], m.inset(outer, 1)); a.paste(fill, box[:2], m.inset(outer, 2))

def tag(d, box, col):
    d.rounded_rectangle(box, 3, fill=m.shade(col, 0.6)); d.rounded_rectangle((box[0]+1, box[1]+1, box[2]-1, box[3]-1), 2, fill=col)

WILD = [('Pidgey', '2-5', 45, None), ('Rattata', '2-4', 35, None), ('Hoothoot', '3-4', 15, 'NOCHE'), ('???', '4', 5, 'DÍA')]
def route_info():
    """Wild Pokémon of a route: method tabs, then per species its levels, encounter rate and time of day.
    Species the player has not seen yet show as ???."""
    a = Image.new('RGB', (AW, AH)); d = ImageDraw.Draw(a); m.background(d); topbar(d)
    tabs = [('HIERBA', (88, 200, 88), True), ('AGUA', (56, 136, 232), False), ('PESCA', (64, 184, 184), False)]
    for i, (t, col, on) in enumerate(tabs):
        b = (4 + i*67, 19, 4 + i*67 + 63, 40)
        m.button(d, b, col if on else (168, 168, 196), 8, rim=1); m.pix_text(a, t, (b[0]+b[2])//2, (b[1]+b[3])//2, 1)
    rows = [(4, 46 + i*25, 201, 46 + i*25 + 22) for i in range(4)]
    for (n, lv, pct, when), b in zip(WILD, rows):
        panel(a, d, b)
        d.ellipse((b[0]+5, b[1]+3, b[0]+21, b[1]+19), fill=(72, 72, 96) if n == '???' else (200, 196, 232))   # icon placeholder; dark = silhouette of an unseen species
        d.rounded_rectangle((b[0]+118, b[1]+8, b[0]+160, b[1]+14), 2, fill=(56, 56, 72))        # rate bar
        d.rectangle((b[0]+119, b[1]+9, b[0]+119+int(40*pct/100), b[1]+13), fill=(96, 208, 96))
        if when: tag(d, (b[0]+84, b[1]+6, b[0]+112, b[1]+16), (72, 88, 168) if when == 'NOCHE' else (240, 176, 56))
    m.button(d, (134, 148, 201, 175), (56, 136, 232), 9); m.pix_text(a, 'VOLVER', 167, 161, 1)
    big = m.up(a); d = ImageDraw.Draw(big)
    m.ttf(d, 'RUTA 1', 6*S, 7*S, 52, (255, 255, 255), (40, 40, 64), 'lm')
    m.ttf(d, 'Pokémon salvajes', (AW-6)*S, 7*S, 52, (255, 255, 255), (40, 40, 64), 'rm')
    for (n, lv, pct, when), b in zip(WILD, rows):
        m.ttf(d, n, (b[0]+26)*S, (b[1]+7)*S, 52, (64, 64, 80), (176, 176, 200), 'lm')
        m.ttf(d, 'Nv ' + lv, (b[0]+26)*S, (b[1]+16.5)*S, 39, (112, 112, 136), None, 'lm')
        m.ttf(d, str(pct) + '%', (b[2]-6)*S, (b[1]+11)*S, 52, (64, 64, 80), (176, 176, 200), 'rm')
        if when: m.ttf(d, when.capitalize(), (b[0]+98.5)*S, (b[1]+10.5)*S, 39, (255, 255, 255), None, 'mm')
    m.ttf(d, '??? = aún no lo has visto', 8*S, 162*S, 39, (112, 112, 136), None, 'lm')
    return big

if __name__ == '__main__':
    h = {'map': region_map, 'route': route_info}.get((sys.argv + ['home'])[4], home)(); out = Image.new('RGB', (h.width + 40, h.height + 40), (0, 0, 0)); out.paste(h, (20, 20))
    out.resize((out.width//2, out.height//2), Image.NEAREST).save(sys.argv[3])

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

PARTY = [('Pikachu', 5, 18, 18, None), ('Pidgey', 4, 9, 16, None), ('Rattata', 3, 3, 13, 'ENV'), ('Caterpie', 3, 0, 12, None)]
# The profile button carries the player's name, as the game's own menu does
ACTIONS = [('POKéMON', (248, 144, 200)), ('MOCHILA', (240, 168, 48)), ('MAPA', (88, 200, 88)), ('ASH', (64, 184, 184)), ('GUARDAR', (56, 136, 232))]

def home():
    a = Image.new('RGB', (AW, AH)); d = ImageDraw.Draw(a); m.background(d)
    d.rectangle((0, 0, AW, 14), fill=(72, 72, 104)); d.line((0, 15, AW, 15), fill=(240, 240, 248))
    cells = [(4, 20 + i*26, 108, 20 + i*26 + 23) for i in range(6)]
    for i, b in enumerate(cells):
        slot(a, d, b, i < len(PARTY))
        if i < len(PARTY):
            n, lv, hp, tot, st = PARTY[i]; x0, y0, x1, y1 = b; f = hp / tot
            col = (96, 208, 96) if f > 0.5 else (240, 200, 64) if f > 0.2 else (240, 88, 72)
            tag = (160, 96, 200) if st else (200, 72, 72) if hp == 0 else None
            end = x1 - 34 if tag else x1 - 9                       # the status tag sits at the end of the HP bar's row, clear of the name
            if tag: d.rectangle((end + 1, y0+14, x1-8, y0+21), fill=(232, 228, 248))
            full = end - 1 - (x0 + 28)
            if hp: d.rectangle((x0+28, y0+16, x0+28+int(full*f), y0+19), fill=col)
            if tag:
                d.rounded_rectangle((x1-31, y0+13, x1-9, y0+22), 3, fill=m.shade(tag, 0.6))
                d.rounded_rectangle((x1-30, y0+14, x1-10, y0+21), 2, fill=tag)
    btns = [(114, 20 + i*31, 201, 20 + i*31 + 27) for i in range(5)]   # wide enough for SAUVEGARDER / SPEICHERN
    for (label, col), b in zip(ACTIONS, btns):
        m.button(d, b, col, 9)
        m.pix_text(a, label, (b[0]+b[2])//2, (b[1]+b[3])//2, 1)
    big = m.up(a).convert('RGBA'); d = ImageDraw.Draw(big)
    m.ttf(d, 'RUTA 1', 6*S, 7*S, 52, (255, 255, 255), (40, 40, 64), 'lm')
    m.ttf(d, '12:34', (AW-6)*S, 7*S, 52, (255, 255, 255), (40, 40, 64), 'rm')
    import os
    icons = os.path.join(os.path.dirname(os.path.dirname(m.TTF)), 'Graphics', 'Pokemon', 'Icons')   # the game folder is the one above the font's
    for (n, lv, hp, tot, st), b in zip(PARTY, cells):
        ic = Image.open(os.path.join(icons, n.upper() + '.png')).convert('RGBA').crop((0, 0, 64, 64)).resize((128, 128), Image.NEAREST)
        if hp == 0: ic = Image.merge('RGBA', (*ic.convert('L').convert('RGB').split(), ic.getchannel('A')))   # fainted: grey
        big.alpha_composite(ic, (int((b[0] + 14.5) * S) - 64, int((b[1] + 11.5) * S) - 64))
        m.ttf(d, n, (b[0]+27)*S, (b[1]+7)*S, 52, (64, 64, 80), (176, 176, 200), 'lm')
        m.ttf(d, 'Nv' + str(lv), (b[2]-9)*S, (b[1]+7)*S, 52, (64, 64, 80), (176, 176, 200), 'rm')
        if st or hp == 0: m.ttf(d, st or 'DEB', (b[2]-20)*S, (b[1]+17.5)*S, 39, (255, 255, 255), None, 'mm')
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

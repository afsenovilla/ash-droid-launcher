# Mock-up of saving from the second screen. The game's own save screen is only a text window, so this one is
# built from the game's load screen instead: its background (loadbg) and its "Continue" panel (loadPanels)
# with the player's walking sprite, the party's icons and the same texts, colours and positions as UI_Load.
# What you see before saving is the card you will find when you load. The background is shown at 2x from the
# top left and extended to the right by repeating its last column; a bar drawn by code holds the question and
# the buttons (approved style).
# Needs the game folder. Usage: ds_mock_save.py <game folder> <pixelfonts.txt> <out.png> [done]
import sys
from PIL import Image, ImageDraw, ImageFont
GAME, FONTS_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
DONE = len(sys.argv) > 4 and sys.argv[4] == 'done'
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m
def pic(p): return Image.open(GAME + '/Graphics/' + p).convert('RGBA')
FONT = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 26)
W, H, BAR = 620, 540, 384                                         # the bar starts where the game's background ends
PX, PY = (W - 408) // 2, 70                                       # the panel
TEXT, TEXT_SH, MALE, MALE_SH = (232, 232, 232), (136, 136, 136), (56, 160, 248), (56, 104, 168)

def text(d, s, x, y, base, shadow, align=0):
    w = d.textlength(s, font=FONT); x = PX + x - (w if align == 1 else 0); y = PY + y + 10
    for dx, dy in ((2, 0), (0, 2), (2, 2)): d.text((x + dx, y + dy), s, font=FONT, fill=shadow, anchor='la')
    d.text((x, y), s, font=FONT, fill=base, anchor='la')

PLACE, NAME = 'Ruta 1', 'Ash'                                     # example save
ROWS = [('Medallas:', '2'), ('Pokédex:', '9'), ('Tiempo:', '1h 12m')]
PARTY = ['PIKACHU', 'PIDGEY', 'RATTATA', 'CATERPIE']

def screen(done=DONE):
    bg = pic('Pictures/loadbg.png'); s = Image.new('RGBA', (W, H)); s.paste(bg, (0, 0))
    s.paste(bg.crop((511, 0, 512, 384)).resize((W - 512, 384)), (512, 0))
    d = ImageDraw.Draw(s); d.fontmode = '1'
    d.rectangle((0, BAR, W, H), fill=(72, 72, 104)); d.rectangle((0, BAR, W, BAR + 1), fill=(240, 240, 248))
    s.alpha_composite(pic('Pictures/loadPanels.png').crop((0, 222, 408, 444)), (PX, PY))      # the selected "Continue" panel
    ch = pic('Characters/trchar000.png'); cw, chh = ch.width // 4, ch.height // 4
    s.alpha_composite(ch.crop((0, 0, cw, chh)), (PX + 64 - cw // 2, PY + 80 - chh // 2))
    for i, sp in enumerate(PARTY):
        ic = pic('Pokemon/Icons/' + sp + '.png').crop((0, 0, 64, 64))
        s.alpha_composite(ic, (PX + 286 + 66 * (i % 2) - 32, PY + 80 + 50 * (i // 2) - 32))
    text(d, 'Partida guardada' if done else 'Guardar', 32, 4, TEXT, TEXT_SH); text(d, PLACE, 386, 4, TEXT, TEXT_SH, 1)
    text(d, NAME, 112, 58, MALE, MALE_SH)
    for n, (label, value) in enumerate(ROWS):
        text(d, label, 32, 106 + 32 * n, TEXT, TEXT_SH); text(d, value, 206, 106 + 32 * n, TEXT, TEXT_SH, 1)
    one = s.resize((W * 2, H * 2), Image.NEAREST); d = ImageDraw.Draw(one)
    line = '%s guardó la partida.' % NAME if done else '¿Quieres guardar la partida?'
    m.ttf(d, line, W, BAR * 2 + 62, 52, (255, 255, 255), (40, 40, 64), 'mm')
    m.SHADOW = (40, 40, 64)
    key = Image.new('RGBA', (206, 34), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
    if not done:
        m.button(kd, (3, 1, 128, 30), (88, 200, 88), 9, selected=True, lift=1); m.pix_text(key, 'GUARDAR', 65, 14, 1)
    m.button(kd, (134, 8, 203, 30), (56, 136, 232), 9); m.pix_text(key, 'VOLVER', 168, 19, 1)
    key = key.resize((206 * 6, 34 * 6), Image.NEAREST); one.paste(key, (2, H * 2 - 34 * 6 + 8), key)
    return one.convert('RGB')

if __name__ == '__main__':
    screen().save(OUT)                                            # real size: 1240x1080

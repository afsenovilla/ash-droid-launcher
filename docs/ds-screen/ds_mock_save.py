# Mock-up of saving from the second screen. The game's own save screen is only a text window, so this one is
# built from the game's load screen instead: its background (loadbg) and its "Continue" panel (loadPanels)
# with the player's walking sprite, the party's icons and the texts and colours of UI_Load. What you see
# before saving is the card you will find when you load, with more facts on it.
# Background and card are shown at 3x (the card then spans the screen). The card is made 64 px taller by
# repeating one of its lines, which makes room for two more rows (money, start date) and a looser party grid.
# A bar drawn by code holds the buttons (approved style); the question is the card's title.
# Needs the game folder. Usage: ds_mock_save.py <game folder> <pixelfonts.txt> <out.png> [done]
import sys
from PIL import Image, ImageDraw, ImageFont
GAME, FONTS_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
DONE = len(sys.argv) > 4 and sys.argv[4] == 'done'
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m
def pic(p): return Image.open(GAME + '/Graphics/' + p).convert('RGBA')
FONT = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 26)
U = 3                                                             # screen px per pixel of the game's art
LW, LH = 414, 294                                                 # the part of the screen above the bar, in art px
GROW = 64
PX, PY = (LW - 408) // 2, 4                                       # the card
TEXT, TEXT_SH, MALE, MALE_SH = (232, 232, 232), (136, 136, 136), (56, 160, 248), (56, 104, 168)

def text(d, s, x, y, base, shadow, align=0):
    w = d.textlength(s, font=FONT); x = PX + x - (w if align == 1 else 0); y = PY + y + 10
    for dx, dy in ((2, 0), (0, 2), (2, 2)): d.text((x + dx, y + dy), s, font=FONT, fill=shadow, anchor='la')
    d.text((x, y), s, font=FONT, fill=base, anchor='la')

PLACE, NAME, ID = 'Ruta 1', 'Ash', '01234'                        # example save
ROWS = [('Medallas:', '2'), ('Pokédex:', '4/9'), ('Tiempo:', '1h 12m'), ('Dinero:', '$3,000'), ('Inicio:', 'Nov 25, 2026')]
PARTY = ['PIKACHU', 'PIDGEY', 'RATTATA', 'CATERPIE']

def screen(done=DONE):
    s = pic('Pictures/loadbg.png').crop((0, 0, LW, LH))
    d = ImageDraw.Draw(s); d.fontmode = '1'
    card = pic('Pictures/loadPanels.png').crop((0, 222, 408, 444))                           # the selected "Continue" panel
    tall = Image.new('RGBA', (408, 222 + GROW)); tall.paste(card.crop((0, 0, 408, 150)), (0, 0))
    tall.paste(card.crop((0, 150, 408, 151)).resize((408, GROW)), (0, 150)); tall.paste(card.crop((0, 150, 408, 222)), (0, 150 + GROW))
    s.alpha_composite(tall, (PX, PY))
    ch = pic('Characters/trchar000.png'); cw, chh = ch.width // 4, ch.height // 4
    s.alpha_composite(ch.crop((0, 0, cw, chh)), (PX + 64 - cw // 2, PY + 80 - chh // 2))
    for i, sp in enumerate(PARTY):
        ic = pic('Pokemon/Icons/' + sp + '.png').crop((0, 0, 64, 64))
        s.alpha_composite(ic, (PX + 286 + 66 * (i % 2) - 32, PY + 96 + 66 * (i // 2) - 32))
    text(d, 'Partida guardada' if done else '¿Guardar la partida?', 32, 4, TEXT, TEXT_SH); text(d, PLACE, 386, 4, TEXT, TEXT_SH, 1)
    text(d, NAME, 112, 40, MALE, MALE_SH); text(d, 'N.º ID ' + ID, 112, 70, TEXT, TEXT_SH)
    for n, (label, value) in enumerate(ROWS):
        text(d, label, 32, 110 + 32 * n, TEXT, TEXT_SH); text(d, value, 236, 110 + 32 * n, TEXT, TEXT_SH, 1)
    one = Image.new('RGB', (1240, 1080), (72, 72, 104))
    one.paste(s.resize((LW * U, LH * U), Image.NEAREST).convert('RGB'), (-1, 0))
    d = ImageDraw.Draw(one); d.rectangle((0, LH * U - 6, 1240, 1080), fill=(72, 72, 104)); d.rectangle((0, LH * U - 6, 1240, LH * U - 1), fill=(240, 240, 248))
    m.SHADOW = (40, 40, 64)
    key = Image.new('RGBA', (206, 32), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
    if not done:
        m.button(kd, (3, 1, 128, 28), (88, 200, 88), 9, selected=True); m.pix_text(key, 'GUARDAR', 65, 14, 1)
    m.button(kd, (134, 6, 203, 28), (56, 136, 232), 9); m.pix_text(key, 'VOLVER', 168, 17, 1)
    key = key.resize((206 * 6, 32 * 6), Image.NEAREST); one.paste(key, (2, 1080 - 32 * 6 + 2), key)
    return one

if __name__ == '__main__':
    screen().save(OUT)                                            # real size: 1240x1080

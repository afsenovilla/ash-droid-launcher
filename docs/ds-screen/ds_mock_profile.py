# Mock-up of the profile on the second screen: a replica of the game's own trainer card (same images, font,
# colours and positions as UI_TrainerCard in the game's scripts), shown whole at 2x and centred, over the
# card's own background colour. Under it, the back button in the approved style.
# Needs the game folder. Usage: ds_mock_profile.py <game folder> <pixelfonts.txt> <out.png>
import sys
from PIL import Image, ImageDraw, ImageFont
GAME, FONTS_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = [sys.argv[0], FONTS_TXT, GAME + '/Fonts/power green.ttf']
import ds_mock as m
P = GAME + '/Graphics/Pictures/Trainer Card/'
def img(p): return Image.open(P + p).convert('RGBA')
FONT = ImageFont.truetype(GAME + '/Fonts/power green.ttf', 26)
W, H, X0, Y0 = 620, 540, 54, 30                                  # the 512x384 card sits at (X0, Y0)
BASE, SHADOW = (72, 72, 72), (160, 160, 160)

def text(d, s, x, y, align=0):
    w = d.textlength(s, font=FONT); x = X0 + x - (w if align == 1 else 0); y = Y0 + y + 10
    for dx, dy in ((2, 0), (0, 2), (2, 2)): d.text((x + dx, y + dy), s, font=FONT, fill=SHADOW, anchor='la')
    d.text((x, y), s, font=FONT, fill=BASE, anchor='la')

# example save
ROWS = [('Nombre', 'Ash'), ('Dinero', '$3,000'), ('Pokédex', '4/9'), ('Tiempo', '1h 12m'), ('Inicio', 'Nov 25, 2026')]
BADGES = [0, 1]

def screen():
    bg = img('bg.png'); s = Image.new('RGBA', (W, H))
    for x in range(0, W, bg.width):
        for y in range(0, H, bg.height): s.paste(bg, (x, y))        # the game shows it as a tiled plane
    s.alpha_composite(img('card.PNG'), (X0, Y0))
    tr = Image.open(GAME + '/Graphics/Trainers/POKEMONTRAINER_Red.png').convert('RGBA')
    s.alpha_composite(tr, (X0 + 336 - (tr.width - 128) // 2, Y0 + 132 - (tr.height - 128)))
    d = ImageDraw.Draw(s); d.fontmode = '1'
    for n, (label, value) in enumerate(ROWS):
        text(d, label, 34, 58 + 48 * n); text(d, value, 302, 58 + 48 * n, 1)
    text(d, 'N.º ID', 332, 58); text(d, '01234', 468, 58, 1)
    badges = img('icon_badges.png')
    for i in BADGES: s.alpha_composite(badges.crop((i * 48, 0, i * 48 + 48, 48)), (X0 + 29 + 58 * i, Y0 + 302))
    one = s.resize((W * 2, H * 2), Image.NEAREST)
    m.SHADOW = tuple(int(v * 0.6) for v in one.getpixel((20, H * 2 - 20))[:3])
    key = Image.new('RGBA', (72, 26), (0, 0, 0, 0)); kd = ImageDraw.Draw(key)
    m.button(kd, (1, 1, 70, 22), (56, 136, 232), 9); m.pix_text(key, 'VOLVER', 35, 11, 1)
    key = key.resize((72 * 6, 26 * 6), Image.NEAREST); one.paste(key, (W * 2 - 72 * 6 - 8, H * 2 - 26 * 6 - 2), key)
    return one.convert('RGB')

if __name__ == '__main__':
    screen().save(OUT)                                            # real size: 1240x1080

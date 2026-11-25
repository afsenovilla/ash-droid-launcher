# Mock-up of saving from the second screen. The game's save screen has no art of its own (a text window with
# the place, player, time, badges and Pokédex, then a yes/no question), so this one is drawn by code in the
# style of the home and route screens, with the same facts and the game's colours for them.
# Usage: ds_mock_save.py <pixelfonts.txt> <power green.ttf> <out.png> [done]
import sys
from PIL import Image, ImageDraw
OUT = sys.argv[3]; DONE = len(sys.argv) > 4 and sys.argv[4] == 'done'
import ds_mock as m
import ds_mock_overworld as o
S, AW, AH = m.S, m.AW, m.AH
PLACE = 'Ruta 1'
ROWS = [('Jugador', 'Ash'), ('Tiempo', '1h 12m'), ('Medallas', '2'), ('Pokédex', '4/9')]   # example save
BLUE, BLUE_SH, GREEN, GREEN_SH = (0, 112, 248), (120, 184, 232), (32, 152, 8), (144, 240, 144)

def screen(done=DONE):
    a = Image.new('RGB', (AW, AH)); d = ImageDraw.Draw(a); m.background(d); o.topbar(d)
    box = (4, 19, 201, 118); o.panel(a, d, box, r=9)
    for n in range(len(ROWS)): d.line((box[0] + 8, 43 + n * 18, box[2] - 8, 43 + n * 18), fill=(200, 196, 224))
    if done:
        m.button(d, (134, 155, 201, 177), (56, 136, 232), 9); m.pix_text(a, 'VOLVER', 167, 166, 1)
    else:
        m.button(d, (4, 146, 128, 177), (88, 200, 88), 9, selected=True, lift=1); m.pix_text(a, 'GUARDAR', 66, 160, 1)
        m.button(d, (134, 155, 201, 177), (56, 136, 232), 9); m.pix_text(a, 'VOLVER', 167, 166, 1)
    big = m.up(a); d = ImageDraw.Draw(big)
    m.ttf(d, 'GUARDAR', 6 * S, 7 * S, 52, (255, 255, 255), (40, 40, 64), 'lm')
    m.ttf(d, PLACE, AW // 2 * S, 31 * S, 52, GREEN, GREEN_SH, 'mm')
    for n, (label, value) in enumerate(ROWS):
        y = (52.5 + n * 18) * S
        m.ttf(d, label, (box[0] + 10) * S, y, 52, (64, 64, 80), (176, 176, 200), 'lm')
        m.ttf(d, value, (box[2] - 10) * S, y, 52, BLUE, BLUE_SH, 'rm')
    line = 'Ash guardó la partida.' if done else '¿Quieres guardar la partida?'
    m.ttf(d, line, AW // 2 * S, 132 * S, 52, (64, 64, 80), (176, 176, 200), 'mm')
    return big

if __name__ == '__main__':
    screen().save(OUT)                                            # real size

# Screen map of the second screen: which screen leads to which. Usage: as ds_mock.py (out.png).
import sys, glob
from PIL import Image, ImageDraw, ImageFont
import ds_mock as m
import ds_mock_overworld as o

BOLD = (glob.glob('/usr/share/fonts/**/DejaVuSans-Bold.ttf', recursive=True) + [None])[0]
REG = (glob.glob('/usr/share/fonts/**/DejaVuSans.ttf', recursive=True) + [None])[0]
def font(size, bold=True): return ImageFont.truetype(BOLD if bold else REG, size)
W, H = 1500, 2110
INK, LINE, DONE, TODO = (40, 40, 56), (90, 90, 120), (48, 152, 80), (200, 120, 40)
TW, TH = 330, 288                                     # thumbnail size

def thumb(img): return img.resize((TW, TH), Image.LANCZOS)
def pending(title):
    t = Image.new('RGB', (TW, TH), (226, 226, 240)); d = ImageDraw.Draw(t)
    for i in range(-TH, TW, 28): d.line((i, TH, i + TH, 0), fill=(214, 214, 232), width=6)
    d.text((TW/2, TH/2), 'por diseñar', font=font(26, False), fill=(120, 120, 150), anchor='mm')
    return t

def card(base, d, x, y, title, img, done, note=None):
    d.rounded_rectangle((x-8, y-46, x+TW+8, y+TH+8 + (34 if note else 0)), 14, fill=(255, 255, 255), outline=LINE, width=3)
    d.text((x, y-24), title, font=font(26), fill=INK, anchor='lm')
    col = DONE if done else TODO
    d.ellipse((x+TW-22, y-34, x+TW-2, y-14), fill=col)
    base.paste(img, (x, y))
    if note: d.text((x+TW/2, y+TH+22), note, font=font(20, False), fill=(90, 90, 120), anchor='mm')
    return (x-8, y-46, x+TW+8, y+TH+8 + (34 if note else 0))

def arrow(d, p, q, label=None, both=False, lx=0, ly=0):
    d.line((p, q), fill=LINE, width=5)
    def head(a, b):
        import math
        ang = math.atan2(b[1]-a[1], b[0]-a[0]); L = 18
        d.polygon([b, (b[0]-L*math.cos(ang-0.45), b[1]-L*math.sin(ang-0.45)), (b[0]-L*math.cos(ang+0.45), b[1]-L*math.sin(ang+0.45))], fill=LINE)
    head(p, q)
    if both: head(q, p)
    if label:
        mx, my = (p[0]+q[0])/2 + lx, (p[1]+q[1])/2 + ly
        f = font(20, False); w = d.textlength(label, font=f)
        d.rounded_rectangle((mx-w/2-8, my-15, mx+w/2+8, my+15), 8, fill=(246, 246, 252))
        d.text((mx, my), label, font=f, fill=INK, anchor='mm')

def build():
    im = Image.new('RGB', (W, H), (246, 246, 252)); d = ImageDraw.Draw(im)
    d.text((40, 44), 'Pantalla inferior: mapa de pantallas', font=font(40), fill=INK, anchor='lm')
    d.ellipse((42, 84, 62, 104), fill=DONE); d.text((72, 94), 'maqueta hecha', font=font(22, False), fill=INK, anchor='lm')
    d.ellipse((262, 84, 282, 104), fill=TODO); d.text((292, 94), 'pendiente de diseñar', font=font(22, False), fill=INK, anchor='lm')
    d.text((560, 94), '·  flechas: se entra tocando el botón y se regresa con VOLVER', font=font(22, False), fill=INK, anchor='lm')

    # Overworld
    d.text((40, 150), 'FUERA DE COMBATE', font=font(24), fill=LINE, anchor='lm')
    hx, hy = (W-TW)//2, 230
    home = card(im, d, hx, hy, 'INICIO', thumb(o.home()), True, 'al andar por el mundo')
    xs = [40, 40 + 370, 40 + 740, 40 + 1110]; y2 = 720
    kids = [('POKéMON', pending(''), False, 'ficha de cada Pokémon'), ('MOCHILA', pending(''), False, 'objetos por bolsillo'),
            ('MAPA', thumb(o.region_map()), True, 'mapa de la región'), ('PERFIL', pending(''), False, 'tarjeta de entrenador')]
    boxes = [card(im, d, x, y2, t, img, done, note) for x, (t, img, done, note) in zip(xs, kids)]
    for b, lab in zip(boxes, ['POKéMON', 'MOCHILA', 'MAPA', 'botón con tu nombre']):
        cx = (b[0]+b[2])//2
        arrow(d, ((home[0]+home[2])//2 + (cx - (home[0]+home[2])//2)//6, home[3]), (cx, b[1]), both=True)
    # save (dialog over home)
    sx, sy = 40, 230
    save = card(im, d, sx, sy, 'GUARDAR', pending(''), False, '¿Guardar la partida?  Sí / No')
    arrow(d, (home[0], hy+TH//2), (save[2], hy+TH//2), 'GUARDAR', both=True, ly=-28)
    # route sheet under the map
    rx, ry = xs[2], 1190
    route = card(im, d, rx, ry, 'FICHA DE RUTA', thumb(o.route_info()), True, 'salvajes: método, % y horario')
    mb = boxes[2]; cx = (mb[0]+mb[2])//2
    arrow(d, (cx, mb[3]), (cx, route[1]), both=True)
    d.text((cx + 26, (mb[3]+route[1])/2), 'tocar una ruta en el mapa y luego\nel panel del lugar  ·  VOLVER', font=font(20, False), fill=INK, anchor='lm', spacing=6)

    # Battle
    by = 1760
    d.line((40, by-100, W-40, by-100), fill=(214, 214, 232), width=3)
    d.text((40, by-64), 'EN COMBATE (sustituye a INICIO mientras dura)', font=font(24), fill=LINE, anchor='lm')
    cmd, fight = m.command(), m.fight()
    c = card(im, d, 240, by, 'COMANDOS', thumb(cmd), True)
    f = card(im, d, 240 + 620, by, 'MOVIMIENTOS', thumb(fight), True)
    arrow(d, (c[2], by+TH//2 - 30), (f[0], by+TH//2 - 30), 'LUCHAR', ly=-28)
    arrow(d, (f[0], by+TH//2 + 40), (c[2], by+TH//2 + 40), 'CANCELAR', ly=28)
    return im

if __name__ == '__main__':
    build().save(sys.argv[3])

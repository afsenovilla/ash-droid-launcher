"""Genera el icono de MODO DIFÍCIL (Dynamax/raid_hard) por idioma, desde código.

Estrella-explosión de 10 puntas (rojo > naranja > amarillo, difuminada) y encima
el texto en la fuente pixel "card": blanco con la columna derecha de cada trazo
en gris, contorno negro de 1 px y halo oscuro suave alrededor.
Uso: python3 tools/art/raid_hard.py  -> escribe art/raid_hard_<lang>.png
"""
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter
sys.path.insert(0, os.path.dirname(__file__))
from relabel import load_fonts, pick_char

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets")
OUT = os.path.join(ROOT, "packs", "fire-ash", "art")
TEXTS = {"es": ("MODO", "DIFÍCIL"), "fr": ("MODE", "EXPERT"), "de": ("PROFI", "MODUS")}
STAR = dict(rin=15, s2=0.88, s3=0.74, blur=0.45, K=8)
TOP = (13, 26)          # fila superior de cada línea (sin acentos)
WHITE, GRAY = (255, 255, 255), (205, 205, 210)
HALO = (25, 25, 25)

C = 24
TIPS = sorted([(24.5, -0.5), (41, 2.5), (48.5, 16), (48.5, 32), (41, 45.5), (24.5, 48.5),
               (8, 45.5), (-0.5, 32), (-0.5, 16), (8, 2.5)],
              key=lambda t: math.atan2(t[1] - C, t[0] - C))


def star_pts(rin, sc):
    pts = []
    for i, t in enumerate(TIPS):
        n = TIPS[(i + 1) % len(TIPS)]
        a1, a2 = math.atan2(t[1] - C, t[0] - C), math.atan2(n[1] - C, n[0] - C)
        if a2 < a1:
            a2 += 2 * math.pi
        am = (a1 + a2) / 2
        pts += [(C + (t[0] - C) * sc, C + (t[1] - C) * sc),
                (C + rin * sc * math.cos(am), C + rin * sc * math.sin(am))]
    return pts


def star(rin, s2, s3, blur, K):
    a = Image.new("L", (48 * K,) * 2, 0)
    rgb = Image.new("RGB", (48 * K,) * 2, (255, 0, 65))
    da, dr = ImageDraw.Draw(a), ImageDraw.Draw(rgb)
    for sc, col in ((1, (255, 0, 65)), (s2, (255, 90, 0)), (s3, (255, 226, 0))):
        p = [(x * K, y * K) for x, y in star_pts(rin, sc)]
        da.polygon(p, fill=255)
        dr.polygon(p, fill=col)
    rgb = rgb.filter(ImageFilter.GaussianBlur(blur * K))
    return Image.merge("RGBA", (*rgb.split(), a)).resize((48, 48), Image.LANCZOS)


def text_layer(lines, font):
    col = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    mask = Image.new("L", (48, 48), 0)
    for line, top in zip(lines, TOP):
        glyphs = [font["g"][pick_char(font, ch)] for ch in line]
        widths = [max(len(r) for r in g) for g in glyphs]
        x = (48 - (sum(widths) + len(widths) - 1)) // 2
        for g, w in zip(glyphs, widths):
            y0 = top - (len(g) - font["h"])
            for dy, row in enumerate(g):
                for dx, c in enumerate(row):
                    if c == "#":
                        right = dx + 1 < len(row) and row[dx + 1] == "#"
                        col.putpixel((x + dx, y0 + dy), (WHITE if right else GRAY) + (255,))
                        mask.putpixel((x + dx, y0 + dy), 255)
            x += w + 1
    return col, mask


def build(lang, fonts):
    im = star(**STAR)
    col, mask = text_layer(TEXTS[lang], fonts["card"])
    halo = mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(0.8))
    im.alpha_composite(Image.merge("RGBA", (*Image.new("RGB", (48, 48), HALO).split(), halo)))
    edge = mask.filter(ImageFilter.MaxFilter(3))
    im.alpha_composite(Image.merge("RGBA", (*Image.new("RGB", (48, 48), (0, 0, 0)).split(), edge)))
    im.alpha_composite(col)
    return im


if __name__ == "__main__":
    fonts = load_fonts()
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    for lang in TEXTS:
        build(lang, fonts).save(os.path.join(out, "raid_hard_%s.png" % lang))

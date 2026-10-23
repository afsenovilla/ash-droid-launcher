#!/usr/bin/env python3
"""Instalador del parche "Español (España)" de Pokémon Fire Ash para Steam Deck / Linux.

Lo lanza "Instalar en Steam Deck.sh" (junto a las carpetas "parche" y "steamdeck").
- Busca la carpeta del juego (la versión de Windows: Game.exe + mkxp.json).
- Copia el parche y añade una línea a mkxp.json (guarda mkxp.json.bak), como el instalador de Windows.
- Deja el motor nativo de Linux (mkxp-z) en ashdroid_linux/ y un lanzador "Jugar (Linux).sh":
  el juego corre sin Proton.
- Añade el juego a Steam (juego no-Steam) con su imagen, para abrirlo desde el modo juego.
Solo usa la biblioteca estándar de Python (SteamOS trae python3) y kdialog/zenity para las ventanas.

Opciones: --game DIR  --yes (sin preguntas)  --no-steam  --uninstall  --steam-root DIR
"""
import argparse
import binascii
import glob
import os
import re
import shutil
import signal
import struct
import subprocess
import sys
import tarfile
import time
import zipfile
import zlib

TITLE = "Fire Ash · Español (España)"
APP_NAME = "Pokémon Fire Ash"
PATCH = "ashdroid_patches.rb"
MARK = "ashdroid_pc_version.txt"
ENGINE_DIR = "ashdroid_linux"
LAUNCHER = "Jugar (Linux).sh"
HERE = os.path.dirname(os.path.abspath(__file__))
TOP = os.path.dirname(HERE)
PAYLOAD = os.path.join(TOP, "parche")
ENGINE_TGZ = os.path.join(HERE, "mkxp-z-linux-x86_64.tar.gz")

ASSUME_YES = False


# ------------------------------------------------------------------ dialogs

def _has(cmd):
    return shutil.which(cmd) is not None and (os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))


def info(msg, error=False):
    print(msg)
    if ASSUME_YES:
        return
    if _has("kdialog"):
        subprocess.call(["kdialog", "--title", TITLE, "--error" if error else "--msgbox", msg])
    elif _has("zenity"):
        subprocess.call(["zenity", "--title", TITLE, "--error" if error else "--info", "--no-wrap", "--text", msg])


def popup(msg):
    """Non-blocking notice (e.g. while extracting)."""
    print(msg)
    if ASSUME_YES:
        return
    if _has("kdialog"):
        subprocess.Popen(["kdialog", "--title", TITLE, "--passivepopup", msg, "60"])
    elif _has("notify-send"):
        subprocess.Popen(["notify-send", TITLE, msg])


def ask(msg):
    if ASSUME_YES:
        return True
    if _has("kdialog"):
        return subprocess.call(["kdialog", "--title", TITLE, "--yesno", msg]) == 0
    if _has("zenity"):
        return subprocess.call(["zenity", "--title", TITLE, "--question", "--no-wrap", "--text", msg]) == 0
    if sys.stdin.isatty():
        return input(msg + " [S/n] ").strip().lower() in ("", "s", "si", "sí", "y", "yes")
    return True


def choose(msg, options):
    """options: list of (key, label). Returns a key or None."""
    if ASSUME_YES:
        return options[0][0]
    if _has("kdialog"):
        args = ["kdialog", "--title", TITLE, "--menu", msg]
        for k, label in options:
            args += [k, label]
        r = subprocess.run(args, stdout=subprocess.PIPE, text=True)
        return r.stdout.strip() or None
    if _has("zenity"):
        args = ["zenity", "--title", TITLE, "--list", "--text", msg, "--column", "id", "--column", "",
                "--hide-column", "1", "--hide-header"]
        for k, label in options:
            args += [k, label]
        r = subprocess.run(args, stdout=subprocess.PIPE, text=True)
        return r.stdout.strip() or None
    if sys.stdin.isatty():
        for i, (_, label) in enumerate(options, 1):
            print("%d) %s" % (i, label))
        s = input(msg + " ").strip()
        return options[int(s) - 1][0] if s.isdigit() and 0 < int(s) <= len(options) else None
    return options[0][0]


def pick_folder():
    start = os.path.expanduser("~")
    if _has("kdialog"):
        r = subprocess.run(["kdialog", "--title", "Elige la carpeta de Pokémon Fire Ash (donde está Game.exe)",
                            "--getexistingdirectory", start], stdout=subprocess.PIPE, text=True)
        return r.stdout.strip() or None
    if _has("zenity"):
        r = subprocess.run(["zenity", "--title", "Elige la carpeta de Pokémon Fire Ash (donde está Game.exe)",
                            "--file-selection", "--directory", "--filename", start + "/"],
                           stdout=subprocess.PIPE, text=True)
        return r.stdout.strip() or None
    if sys.stdin.isatty():
        return input("Carpeta del juego (donde está Game.exe): ").strip() or None
    return None


# --------------------------------------------------------------- game folder

def is_game(d):
    return bool(d) and os.path.isfile(os.path.join(d, "mkxp.json"))


def search_roots():
    home = os.path.expanduser("~")
    roots = [os.path.join(home, n) for n in ("Downloads", "Descargas", "Desktop", "Escritorio",
                                             "Documents", "Documentos", "Games", "Juegos")]
    return roots + [home] + sorted(glob.glob("/run/media/*")) + sorted(glob.glob("/run/media/*/*"))


def detect_game():
    """Folders with the game already extracted."""
    for d in (TOP, os.path.dirname(TOP)):
        if is_game(d):
            return [d]
    found = []
    for r in search_roots():
        if not os.path.isdir(r):
            continue
        try:
            for d in sorted(glob.glob(os.path.join(r, "*"))):
                if not os.path.isdir(d) or not re.search(r"fire.?ash", os.path.basename(d), re.I):
                    continue
                if is_game(d):
                    found.append(d)
                for d2 in sorted(glob.glob(os.path.join(d, "*"))):
                    if os.path.isdir(d2) and is_game(d2):
                        found.append(d2)
        except OSError:
            pass
    return [f for i, f in enumerate(found) if f not in found[:i] and os.path.realpath(f) != os.path.realpath(TOP)]


# ------------------------------------------------------------- game in a zip

def zip_game_root(path):
    """Folder inside the zip that holds mkxp.json ("" = the zip's root), or None if it isn't the game."""
    try:
        with zipfile.ZipFile(path) as z:
            hits = [n for n in z.namelist() if n.replace("\\", "/").split("/")[-1].lower() == "mkxp.json"]
    except (OSError, zipfile.BadZipFile):
        return None
    if not hits:
        return None
    h = min(hits, key=lambda n: n.count("/"))
    return h.replace("\\", "/").rsplit("/", 1)[0] if "/" in h.replace("\\", "/") else ""


def detect_zips():
    """The game's .zip as downloaded (names like "Pokemon Fire Ash 3.7.zip")."""
    found = []
    for r in search_roots():
        try:
            for f in sorted(glob.glob(os.path.join(r, "*.zip"))):
                if re.search(r"fire.?ash", os.path.basename(f), re.I) and os.path.realpath(f) not in found \
                        and zip_game_root(f) is not None:
                    found.append(os.path.realpath(f))
        except OSError:
            pass
    return found


def extract_dest(zpath):
    """Where to extract: next to the zip if it is on the microSD, else ~/Games."""
    if os.path.realpath(zpath).startswith("/run/media/"):
        return os.path.dirname(os.path.realpath(zpath))
    return os.path.expanduser("~/Games")


def extract_game(zpath):
    """Extracts the game's zip and returns the folder with mkxp.json."""
    root = zip_game_root(zpath)
    base = extract_dest(zpath)
    stem = os.path.splitext(os.path.basename(zpath))[0]
    # Zip with the files at its root: extract into a folder named after the zip
    dest = base if root else os.path.join(base, stem)
    os.makedirs(dest, exist_ok=True)
    popup("Descomprimiendo el juego en %s…\nPuede tardar un minuto." % dest)
    with zipfile.ZipFile(zpath) as z:
        for info_ in z.infolist():
            name = info_.filename.replace("\\", "/")
            if name.startswith("/") or ".." in name.split("/"):
                raise RuntimeError("Archivo no válido en el zip: " + name)
            target = os.path.join(dest, name)
            if name.endswith("/"):
                os.makedirs(target, exist_ok=True)
                continue
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with z.open(info_) as src, open(target, "wb") as out:
                shutil.copyfileobj(src, out, 1 << 20)
    game = os.path.join(dest, root) if root else dest
    if not is_game(game):
        raise RuntimeError("El zip no tiene el juego (falta mkxp.json).")
    return game


def pick_zip():
    start = os.path.expanduser("~/Downloads")
    if _has("kdialog"):
        r = subprocess.run(["kdialog", "--title", "Elige el zip de Pokémon Fire Ash", "--getopenfilename", start,
                            "*.zip"], stdout=subprocess.PIPE, text=True)
        return r.stdout.strip() or None
    if _has("zenity"):
        r = subprocess.run(["zenity", "--title", "Elige el zip de Pokémon Fire Ash", "--file-selection",
                            "--file-filter=*.zip", "--filename", start + "/"], stdout=subprocess.PIPE, text=True)
        return r.stdout.strip() or None
    if sys.stdin.isatty():
        return input("Zip del juego: ").strip() or None
    return None


# ------------------------------------------------------------- patch install

def edit_conf(game, add):
    conf = os.path.join(game, "mkxp.json")
    with open(conf, encoding="utf-8-sig") as f:
        t = f.read()
    if add:
        if PATCH in t:
            return
        if not os.path.exists(conf + ".bak"):
            shutil.copy2(conf, conf + ".bak")
        m = re.search(r'(?m)^(\s*"preloadScript"\s*:\s*\[)', t)
        if m:
            t = t[:m.end()] + '"%s", ' % PATCH + t[m.end():]
        else:
            i = t.find("{")
            if i < 0:
                raise RuntimeError("mkxp.json no tiene el formato esperado.")
            t = t[:i + 1] + '\n  "preloadScript": ["%s"],' % PATCH + t[i + 1:]
    else:
        t = re.sub(r'(?m)^\s*"preloadScript"\s*:\s*\[\s*"%s"\s*\],?\r?\n' % re.escape(PATCH), "", t)
        t = re.sub(r'"%s",\s*' % re.escape(PATCH), "", t)
        t = re.sub(r',\s*"%s"' % re.escape(PATCH), "", t)
    with open(conf, "w", encoding="utf-8") as f:
        f.write(t)


def install_patch(game, music):
    for root, _, files in os.walk(PAYLOAD):
        for fn in files:
            src = os.path.join(root, fn)
            rel = os.path.relpath(src, PAYLOAD)
            if rel == "VERSION.txt":
                continue
            if not music and rel.lower().startswith("ashdroid_audio_"):
                continue
            dst = os.path.join(game, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)
    if not music:
        for l in ("es", "fr", "de"):
            shutil.rmtree(os.path.join(game, "ashdroid_audio_" + l), ignore_errors=True)
    edit_conf(game, True)
    try:
        with open(os.path.join(PAYLOAD, "VERSION.txt")) as f:
            version = f.read().strip()
    except OSError:
        version = "?"
    with open(os.path.join(game, MARK), "w") as f:
        f.write(version)


def install_engine(game):
    dst = os.path.join(game, ENGINE_DIR)
    shutil.rmtree(dst, ignore_errors=True)
    os.makedirs(dst)
    with tarfile.open(ENGINE_TGZ) as tf:
        for m in tf.getmembers():
            if m.name.startswith("/") or ".." in m.name.split("/"):
                raise RuntimeError("Archivo del motor no válido: " + m.name)
        if hasattr(tarfile, "data_filter"):
            tf.extractall(dst, filter="tar")
        else:
            tf.extractall(dst)
    exe = os.path.join(dst, "mkxp-z.x86_64")
    os.chmod(exe, 0o755)
    # Update checker run by the launcher before the game starts
    upd = os.path.join(HERE, "update.py")
    if os.path.isfile(upd):
        shutil.copyfile(upd, os.path.join(dst, "update.py"))
    launcher = os.path.join(game, LAUNCHER)
    # Everything inside a function: an update rewrites this file while it runs, and sh reads
    # scripts as it goes, so it must have parsed the whole body before that.
    with open(launcher, "w", newline="\n") as f:
        f.write('#!/bin/sh\n'
                '# Pokémon Fire Ash con el motor nativo de Linux (mkxp-z), sin Proton.\n'
                '# Antes de abrir el juego mira si hay una versión nueva del parche (ASHDROID_NO_UPDATE=1 lo evita).\n'
                'main() {\n'
                '  cd "$(dirname "$0")" || exit 1\n'
                '  if command -v python3 >/dev/null 2>&1 && [ -f "./%s/update.py" ]; then\n'
                '    python3 "./%s/update.py" "$PWD" || true\n'
                '  fi\n'
                '  export SRCDIR="$PWD"\n'
                '  exec "./%s/mkxp-z.x86_64" "$@"\n'
                '}\n'
                'main "$@"; exit\n' % (ENGINE_DIR, ENGINE_DIR, ENGINE_DIR))
    os.chmod(launcher, 0o755)
    return launcher


def uninstall(game):
    edit_conf(game, False)
    for p in glob.glob(os.path.join(game, "ashdroid_*")):
        if os.path.isdir(p):
            shutil.rmtree(p, ignore_errors=True)
        else:
            os.remove(p)
    for p in (os.path.join(game, LAUNCHER),):
        if os.path.exists(p):
            os.remove(p)


# ------------------------------------------------------------ Steam shortcut
# shortcuts.vdf is Valve's binary KeyValues: 0x00 map, 0x01 string, 0x02 int32, 0x08 end of map.

def _read_str(b, i):
    j = b.index(b"\x00", i)
    return b[i:j].decode("utf-8", "replace"), j + 1


def _read_map(b, i):
    d = {}
    while True:
        t = b[i]
        i += 1
        if t == 0x08:
            return d, i
        k, i = _read_str(b, i)
        if t == 0x00:
            d[k], i = _read_map(b, i)
        elif t == 0x01:
            d[k], i = _read_str(b, i)
        elif t == 0x02:
            d[k] = struct.unpack_from("<i", b, i)[0]
            i += 4
        elif t == 0x07:  # uint64
            d[k] = ("u64", struct.unpack_from("<Q", b, i)[0])
            i += 8
        else:
            raise ValueError("tipo VDF desconocido %d" % t)


def vdf_load(path):
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        return {"shortcuts": {}}
    with open(path, "rb") as f:
        b = f.read()
    d, _ = _read_map(b, 0)
    return d


def _write_map(d):
    out = bytearray()
    for k, v in d.items():
        kb = k.encode("utf-8") + b"\x00"
        if isinstance(v, dict):
            out += b"\x00" + kb + _write_map(v)
        elif isinstance(v, tuple) and v[0] == "u64":
            out += b"\x07" + kb + struct.pack("<Q", v[1])
        elif isinstance(v, bool) or isinstance(v, int):
            out += b"\x02" + kb + struct.pack("<i", int(v))
        else:
            out += b"\x01" + kb + str(v).encode("utf-8") + b"\x00"
    return bytes(out) + b"\x08"


def vdf_save(path, d):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "wb") as f:
        f.write(_write_map(d))
    os.replace(tmp, path)


def shortcut_appid(exe, name):
    """Same id Steam gives a non-Steam game (also used for its grid images)."""
    crc = binascii.crc32((exe + name).encode("utf-8")) & 0xFFFFFFFF
    return crc | 0x80000000


def steam_root(override=None):
    cands = [override] if override else [os.path.expanduser(p) for p in (
        "~/.steam/steam", "~/.local/share/Steam", "~/.var/app/com.valvesoftware.Steam/.local/share/Steam")]
    for c in cands:
        if c and os.path.isdir(os.path.join(c, "userdata")):
            return c
    return None


def steam_users(root):
    users = [d for d in glob.glob(os.path.join(root, "userdata", "*")) if os.path.basename(d).isdigit()
             and os.path.basename(d) != "0"]
    # Most recently used account first
    return sorted(users, key=lambda d: os.path.getmtime(d), reverse=True)


def steam_running():
    try:
        out = subprocess.run(["pgrep", "-x", "steam"], stdout=subprocess.PIPE, text=True).stdout
        return bool(out.strip())
    except OSError:
        return False


def stop_steam():
    subprocess.call(["steam", "-shutdown"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(60):
        if not steam_running():
            return True
        time.sleep(1)
    return not steam_running()


def find_art(game):
    for pat in ("Graphics/Titles/title*.png", "Graphics/Titles/*.png", "Graphics/Pictures/title*.png"):
        hits = sorted(glob.glob(os.path.join(game, pat)), key=lambda p: (len(p), p))
        if hits:
            return hits[0]
    return None


# ------------------------------------------------------------ library art
# Steam's library (game mode) shows a vertical cover (<appid>p.png, 600x900); a wide image alone
# leaves a blank capsule. Built from the game's own title screen with a tiny PNG reader/writer
# (standard library only), so no game files are shipped.

def _png_read(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    i, idat, plte, trns = 8, b"", None, None
    while i < len(data):
        n, t = struct.unpack(">I4s", data[i:i + 8])
        c = data[i + 8:i + 8 + n]
        if t == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", c)
        elif t == b"PLTE":
            plte = c
        elif t == b"tRNS":
            trns = c
        elif t == b"IDAT":
            idat += c
        i += 12 + n
    if depth != 8 or interlace or ctype not in (0, 2, 3, 4, 6):
        raise ValueError("unsupported PNG")
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * bpp
    rows, prev, o = [], bytearray(stride), 0
    for _ in range(h):
        ft = raw[o]; line = bytearray(raw[o + 1:o + 1 + stride]); o += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            cc = prev[x - bpp] if x >= bpp else 0
            if ft == 1:
                line[x] = (line[x] + a) & 255
            elif ft == 2:
                line[x] = (line[x] + b) & 255
            elif ft == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 255
            elif ft == 4:
                pa, pb, pc = abs(b - cc), abs(a - cc), abs(a + b - 2 * cc)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else cc)) & 255
        rows.append(line); prev = line
    px = []  # RGB rows
    for line in rows:
        out = bytearray()
        for x in range(w):
            if ctype == 2:
                out += line[3 * x:3 * x + 3]
            elif ctype == 6:
                r, g, b, al = line[4 * x:4 * x + 4]
                out += bytes((r * al // 255, g * al // 255, b * al // 255))
            elif ctype == 3:
                k = line[x]
                r, g, b = plte[3 * k:3 * k + 3]
                al = trns[k] if trns and k < len(trns) else 255
                out += bytes((r * al // 255, g * al // 255, b * al // 255))
            else:
                v = line[x * bpp]
                out += bytes((v, v, v))
        px.append(out)
    return w, h, px


def _png_write(path, w, h, rows):
    raw = b"".join(b"\x00" + bytes(r) for r in rows)
    def chunk(t, c):
        return struct.pack(">I", len(c)) + t + c + struct.pack(">I", binascii.crc32(t + c) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) +
                chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def _crop(src, box):
    w, h, px = src
    x0, y0, x1, y1 = box
    return x1 - x0, y1 - y0, [r[3 * x0:3 * x1] for r in px[y0:y1]]


def _canvas(src, cw, ch, cover=False, bg=(16, 16, 16)):
    """Scales the image into cw x ch, centred, nearest-neighbour: whole image (borders) or cover (cropped)."""
    w, h, px = src
    k = (max if cover else min)(cw / w, ch / h)
    nw, nh = max(1, int(round(w * k))), max(1, int(round(h * k)))
    ox, oy = (cw - nw) // 2, (ch - nh) // 2
    cols = [(x, min(w - 1, int((x - ox) / k))) for x in range(max(0, ox), min(cw, ox + nw))]
    blank = bytes(bg) * cw
    rows = []
    for y in range(ch):
        if y < oy or y >= oy + nh:
            rows.append(bytearray(blank)); continue
        line = px[min(h - 1, int((y - oy) / k))]
        r = bytearray(blank)
        for x, sx in cols:
            r[3 * x:3 * x + 3] = line[3 * sx:3 * sx + 3]
        rows.append(r)
    return rows


def _paste(rows, img, x0, y0):
    w, h, px = img
    for y in range(h):
        rows[y0 + y][3 * x0:3 * (x0 + w)] = px[y]


def _images(src):
    """{suffix: (w, h, rows)} for the library: vertical cover, wide capsule and banner."""
    w, h, _ = src
    if (w, h) == (512, 384):
        # Fire Ash's title screen: picture between two bars (y 48-310), logo top right
        cover = _canvas(_crop(src, (95, 48, 270, 310)), 600, 900, cover=True)
        logo = _crop(src, (282, 14, 512, 122))  # with the black bar above it, as a header
        lw, lh = 600, int(600 * 108 / 230)
        _paste(cover, (lw, lh, _canvas(logo, lw, lh, cover=True)), 0, 0)
        return {"p": (600, 900, cover),
                "": (920, 430, _canvas(_crop(src, (0, 48, 512, 288)), 920, 430, cover=True)),
                "_hero": (1920, 620, _canvas(_crop(src, (0, 48, 512, 213)), 1920, 620, cover=True))}
    return {sfx: (cw, ch, _canvas(src, cw, ch)) for sfx, cw, ch in
            (("p", 600, 900), ("", 920, 430), ("_hero", 1920, 620))}


def write_art(grid, appid, art):
    """Vertical cover, wide capsule and banner for the Steam library."""
    os.makedirs(grid, exist_ok=True)
    try:
        for suffix, (cw, ch, rows) in _images(_png_read(art)).items():
            _png_write(os.path.join(grid, "%d%s.png" % (appid, suffix)), cw, ch, rows)
    except Exception:  # noqa: BLE001  (unexpected PNG: use it as is)
        for suffix in ("p", "", "_hero"):
            shutil.copyfile(art, os.path.join(grid, "%d%s.png" % (appid, suffix)))


def update_art(root, game, launcher):
    """Library images only (used by updates, which don't touch shortcuts.vdf)."""
    art = find_art(game)
    if not art:
        return
    appid = shortcut_appid('"%s"' % launcher, APP_NAME)
    for user in steam_users(root):
        grid = os.path.join(user, "config", "grid")
        if os.path.isdir(os.path.dirname(grid)):
            write_art(grid, appid, art)


def add_to_steam(root, game, launcher):
    exe = '"%s"' % launcher
    appid = shortcut_appid(exe, APP_NAME)
    signed = struct.unpack("<i", struct.pack("<I", appid))[0]
    art = find_art(game)
    users = steam_users(root)
    for user in users:
        path = os.path.join(user, "config", "shortcuts.vdf")
        data = vdf_load(path)
        sc = data.setdefault("shortcuts", {})
        # Replace an earlier entry for this game (same name or same launcher)
        keep = [v for v in sc.values() if not (isinstance(v, dict) and (
            v.get("AppName", v.get("appname")) == APP_NAME or v.get("Exe", v.get("exe")) == exe))]
        entry = {
            "appid": signed, "AppName": APP_NAME, "Exe": exe, "StartDir": '"%s"' % game,
            "icon": "", "ShortcutPath": "", "LaunchOptions": "", "IsHidden": 0, "AllowDesktopConfig": 1,
            "AllowOverlay": 1, "OpenVR": 0, "Devkit": 0, "DevkitGameID": "", "DevkitOverrideAppID": 0,
            "LastPlayTime": 0, "FlatpakAppID": "", "tags": {},
        }
        keep.append(entry)
        data["shortcuts"] = {str(i): v for i, v in enumerate(keep)}
        if os.path.exists(path) and not os.path.exists(path + ".ashdroid.bak"):
            shutil.copy2(path, path + ".ashdroid.bak")
        vdf_save(path, data)
        if art:
            write_art(os.path.join(user, "config", "grid"), appid, art)
    return len(users)


def remove_from_steam(root, launcher):
    exe = '"%s"' % launcher
    for user in steam_users(root):
        path = os.path.join(user, "config", "shortcuts.vdf")
        if not os.path.exists(path):
            continue
        data = vdf_load(path)
        sc = data.get("shortcuts", {})
        keep = [v for v in sc.values() if not (isinstance(v, dict) and v.get("Exe", v.get("exe")) == exe)]
        if len(keep) != len(sc):
            data["shortcuts"] = {str(i): v for i, v in enumerate(keep)}
            vdf_save(path, data)
        appid = shortcut_appid(exe, APP_NAME)
        for suffix in ("", "p", "_hero"):
            img = os.path.join(user, "config", "grid", "%d%s.png" % (appid, suffix))
            if os.path.exists(img):
                os.remove(img)


# ---------------------------------------------------------------------- main

def main():
    global ASSUME_YES
    ap = argparse.ArgumentParser()
    ap.add_argument("--game")
    ap.add_argument("--yes", action="store_true")
    ap.add_argument("--no-steam", action="store_true")
    ap.add_argument("--no-music", action="store_true")
    ap.add_argument("--uninstall", action="store_true")
    ap.add_argument("--steam-root")
    a = ap.parse_args()
    ASSUME_YES = a.yes

    if not os.path.isdir(PAYLOAD) or not os.path.isfile(ENGINE_TGZ):
        info('Faltan las carpetas "parche" y "steamdeck" junto a este programa. Descomprime el zip entero.', True)
        return 1

    game = a.game
    try:
        if game and game.lower().endswith(".zip"):
            game = extract_game(game)
        elif not game:
            folders, zips = detect_game(), detect_zips()
            options = [(f, f) for f in folders] + \
                      [("zip:" + z, "%s  (comprimido: lo descomprimo en %s)" % (z, extract_dest(z))) for z in zips]
            if len(options) == 1:
                k, label = options[0]
                q = ("He encontrado el juego en:\n%s\n\n¿Uso ese?" % label) if not k.startswith("zip:") else \
                    ("He encontrado el juego comprimido:\n%s\n\n¿Lo descomprimo en %s y lo uso?" % (k[4:], extract_dest(k[4:])))
                choice = k if ask(q) else None
            elif options:
                choice = choose("¿Dónde está Pokémon Fire Ash?", options +
                                [("__dir__", "Otra carpeta…"), ("__zip__", "Otro zip…")])
            else:
                choice = choose("No encuentro Pokémon Fire Ash. ¿Dónde está?",
                                [("__zip__", "Elegir el zip del juego (tal cual se descarga)"),
                                 ("__dir__", "Elegir la carpeta del juego ya descomprimido")])
            if choice == "__zip__":
                z = pick_zip()
                choice = "zip:" + z if z else None
            elif choice == "__dir__":
                choice = pick_folder()
            if choice and choice.startswith("zip:"):
                if zip_game_root(choice[4:]) is None:
                    info("Ese zip no es Pokémon Fire Ash (no tiene mkxp.json).", True)
                    return 1
                game = extract_game(choice[4:])
            else:
                game = choice
    except Exception as e:  # noqa: BLE001
        info("Error al descomprimir el juego: %s" % e, True)
        return 1
    if not game:
        return 1
    if not is_game(game):
        info("En esa carpeta no está el juego (falta mkxp.json).\n"
             "Elige la carpeta de la versión de Windows de Pokémon Fire Ash, la que tiene Game.exe.", True)
        return 1
    game = os.path.abspath(game)
    launcher = os.path.join(game, LAUNCHER)
    root = None if a.no_steam else steam_root(a.steam_root)

    installed = os.path.exists(os.path.join(game, MARK))
    action = "install"
    if a.uninstall:
        action = "uninstall"
    elif installed:
        action = choose("El parche ya está instalado en esta carpeta.", [
            ("install", "Actualizar"), ("uninstall", "Desinstalar")])
        if not action:
            return 1

    steam_was_running = False
    if root:
        if steam_running():
            if not ask("Para añadir el juego a Steam hay que cerrar Steam un momento (se vuelve a abrir solo).\n\n"
                       "¿Continuar?"):
                root = None
            else:
                steam_was_running = True
                if not stop_steam():
                    info("Steam no se ha cerrado; el juego no se añadirá a Steam esta vez.", True)
                    root = None

    try:
        if action == "uninstall":
            if root:
                remove_from_steam(root, launcher)
            uninstall(game)
            msg = "Parche desinstalado. El juego vuelve a estar como antes."
        else:
            # The Spanish intro is switched in the game (Options -> "Música en castellano", off by default)
            install_patch(game, not a.no_music)
            install_engine(game)
            n = add_to_steam(root, game, launcher) if root else 0
            if not root and a.no_steam:
                # Update from the game: the shortcut is already there; just (re)make its library images
                try:
                    sr = steam_root(a.steam_root)
                    if sr:
                        update_art(sr, game, launcher)
                except Exception:  # noqa: BLE001
                    pass
            msg = "¡Listo!\n\n"
            if n:
                msg += ('"%s" ya está en tu biblioteca de Steam (en "No-Steam").\n'
                        "Vuelve al modo juego y ábrelo desde ahí.\n\n" % APP_NAME)
            else:
                msg += ('Para jugar, abre "%s" en la carpeta del juego, o añádelo a Steam como '
                        "juego no-Steam (sin Proton).\n\n" % LAUNCHER)
            msg += 'Si juegas en español ya lo tienes: el idioma "Spanish" pasa a ser "Spanish (Spain)". Si no, elígelo en la pantalla de carga (opción Language).'
    except Exception as e:  # noqa: BLE001
        info("Error: %s" % e, True)
        return 1
    finally:
        if steam_was_running:
            subprocess.Popen(["steam"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                             start_new_session=True)
    info(msg)
    return 0


if __name__ == "__main__":
    signal.signal(signal.SIGINT, signal.SIG_DFL)
    sys.exit(main())

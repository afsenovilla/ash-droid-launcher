#!/usr/bin/env python3
"""Buscador de actualizaciones del parche de Pokémon Fire Ash para Steam Deck / Linux.

El instalador lo deja en <juego>/ashdroid_linux/update.py y "Jugar (Linux).sh" lo ejecuta antes
de abrir el juego. Mira la última versión publicada en GitHub; si es más nueva que la instalada
pregunta si actualizar y, si se acepta, descarga el zip de Steam Deck y ejecuta su instalador
(sin preguntas y sin tocar Steam). Sin conexión, o ante cualquier error, no hace nada y el juego
arranca igual. Si se responde que no, no vuelve a preguntar por esa versión.

Uso: update.py CARPETA_DEL_JUEGO
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

API = "https://api.github.com/repos/afsenovilla/ash-droid-launcher/releases/latest"
ASSET = "SteamDeck-Linux"
TITLE = "Pokémon Fire Ash"
MARK = "ashdroid_pc_version.txt"
SKIP = "ashdroid_update_skip.txt"
UA = {"User-Agent": "ash-droid-launcher-linux", "Accept": "application/vnd.github+json"}


def has(cmd):
    return shutil.which(cmd) is not None and (os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))


def ask(msg):
    if has("zenity"):
        return subprocess.call(["zenity", "--question", "--title", TITLE, "--no-wrap", "--text", msg,
                                "--ok-label", "Actualizar", "--cancel-label", "Ahora no"]) == 0
    if has("kdialog"):
        return subprocess.call(["kdialog", "--title", TITLE, "--yes-label", "Actualizar",
                                "--no-label", "Ahora no", "--yesno", msg]) == 0
    return False


def tell(msg, error=False):
    if has("zenity"):
        subprocess.call(["zenity", "--error" if error else "--info", "--title", TITLE, "--no-wrap",
                         "--timeout", "8", "--text", msg])
    elif has("kdialog"):
        subprocess.call(["kdialog", "--title", TITLE, "--error" if error else "--msgbox", msg])


def version_key(v):
    main, _, pre = v.strip().lstrip("v").partition("-")
    nums = []
    for p in main.split("."):
        digits = "".join(c for c in p if c.isdigit())
        nums.append(int(digits) if digits else 0)
    while len(nums) < 3:
        nums.append(0)
    return nums + [0 if pre else 1]  # 0.4.0-dev.3 < 0.4.0


def read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read().strip()
    except OSError:
        return ""


def latest():
    req = urllib.request.Request(API, headers=UA)
    with urllib.request.urlopen(req, timeout=5) as r:
        o = json.load(r)
    if o.get("draft") or o.get("prerelease"):
        return None
    for a in o.get("assets", []):
        name = a.get("name", "")
        if ASSET in name and name.endswith(".zip"):
            return o.get("tag_name", "").lstrip("v"), a["browser_download_url"], a.get("size", 0)
    return None


def download(url, size, dst):
    """Downloads with a progress bar when zenity is there."""
    bar = None
    if has("zenity"):
        bar = subprocess.Popen(["zenity", "--progress", "--title", TITLE, "--auto-close", "--no-cancel",
                                "--text", "Descargando la actualización…"],
                               stdin=subprocess.PIPE, text=True)
    elif has("kdialog"):
        subprocess.Popen(["kdialog", "--title", TITLE, "--passivepopup", "Descargando la actualización…", "30"])
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA["User-Agent"]})
        with urllib.request.urlopen(req, timeout=30) as r, open(dst, "wb") as f:
            total = int(r.headers.get("Content-Length") or size or 0)
            done, last = 0, -1
            while True:
                b = r.read(1 << 16)
                if not b:
                    break
                f.write(b)
                done += len(b)
                pct = min(99, done * 100 // total) if total else 50
                if bar and pct != last:
                    last = pct
                    bar.stdin.write("%d\n" % pct)
                    bar.stdin.flush()
    finally:
        if bar:
            try:
                bar.stdin.write("100\n")
                bar.stdin.close()
            except OSError:
                pass
            bar.wait()


def main():
    game = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.getcwd())
    if os.environ.get("ASHDROID_NO_UPDATE"):
        return 0
    current = read(os.path.join(game, MARK))
    if not current or current == "?":
        return 0
    try:
        info = latest()
    except Exception:  # noqa: BLE001  (offline, rate limit…: just play)
        return 0
    if not info:
        return 0
    new, url, size = info
    if version_key(new) <= version_key(current) or read(os.path.join(game, SKIP)) == new:
        return 0
    if not ask("Hay una versión nueva del parche: %s (tienes la %s).\n\n¿Actualizar ahora? Tarda un momento;\n"
               "tus partidas no se tocan." % (new, current)):
        with open(os.path.join(game, SKIP), "w") as f:
            f.write(new)
        return 0
    tmp = tempfile.mkdtemp(prefix="fireash-update-")
    try:
        z = os.path.join(tmp, "update.zip")
        download(url, size, z)
        with zipfile.ZipFile(z) as zf:
            for n in zf.namelist():
                if n.startswith("/") or ".." in n.split("/"):
                    raise RuntimeError("zip no válido")
            zf.extractall(tmp)
        inst = [os.path.join(root, "install.py") for root, _, files in os.walk(tmp)
                if "install.py" in files and os.path.basename(root) == "steamdeck"]
        if not inst:
            raise RuntimeError("el zip no trae el instalador")
        r = subprocess.run([sys.executable, inst[0], "--game", game, "--yes", "--no-steam"],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if r.returncode != 0:
            raise RuntimeError((r.stdout or "").strip().splitlines()[-1:] or "error del instalador")
        tell("Parche actualizado a la versión %s." % new)
    except Exception as e:  # noqa: BLE001
        tell("No se pudo actualizar (%s).\nEl juego se abre con la versión que ya tenías." % e, True)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:  # noqa: BLE001  (never keep the game from starting)
        sys.exit(0)

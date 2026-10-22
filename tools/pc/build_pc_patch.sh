#!/usr/bin/env bash
# Builds the PC patch zip: tools/pc/build_pc_patch.sh GAME_DIR OUT.zip
# GAME_DIR is a Fire Ash install (its Graphics are needed to redraw the Spanish images).
set -euo pipefail
GAME="$1"; OUT="$(realpath -m "$2")"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"; P="$TMP/Parche Español (España) - Fire Ash"; mkdir -p "$P"
A="$ROOT/app/src/main/assets"
cp "$A/ashdroid_patches.rb" "$A/packs/fire-ash/ashdroid_es.dat" "$A/packs/fire-ash/ashdroid_es_ES.dat" "$P/"
mkdir -p "$P/ashdroid_audio_es"; cp "$A/packs/fire-ash/audio_es/"* "$P/ashdroid_audio_es/"
# Spanish images; keyboard keys for the button letters (Essentials defaults)
python3 "$ROOT/tools/art/relabel.py" "$GAME" "$A/packs/fire-ash/art.json" "$TMP/art" --buttons ENTER,X,Z,D >/dev/null
mkdir -p "$P/ashdroid_art"
grep -P '^es\t' "$TMP/art/index.txt" > "$P/ashdroid_art/index.txt"
cut -f3 "$P/ashdroid_art/index.txt" | while read -r f; do cp "$TMP/art/$f" "$P/ashdroid_art/"; done
cp "$ROOT/tools/pc/"{INSTALAR.bat,DESINSTALAR.bat,instalar.ps1,desinstalar.ps1,LEEME.txt} "$P/"
sed -i "s/\r\?$/\r/" "$P/"*.ps1 "$P/LEEME.txt"
for f in "$P/"*.ps1; do printf '\xEF\xBB\xBF' | cat - "$f" > "$f.t" && mv "$f.t" "$f"; done
rm -f "$OUT"; (cd "$TMP" && zip -qr "$OUT" "$(basename "$P")")
rm -rf "$TMP"; echo "$OUT"

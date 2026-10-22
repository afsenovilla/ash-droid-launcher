#!/usr/bin/env bash
# Builds the PC patch zip:
#   tools/pc/build_pc_patch.sh GAME_DIR "Instalar parche.exe" OUT.zip [VERSION]
# GAME_DIR is a Fire Ash install (its Graphics are needed to redraw the Spanish images);
# the installer .exe comes from the "PC installer" GitHub workflow.
set -euo pipefail
GAME="$1"; EXE="$2"; OUT="$(realpath -m "$3")"; VER="${4:-dev}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"; TOP="$TMP/Fire Ash - Español (España)"; P="$TOP/parche"; mkdir -p "$P"
A="$ROOT/app/src/main/assets"
cp "$A/ashdroid_patches.rb" "$A/packs/fire-ash/ashdroid_es.dat" "$A/packs/fire-ash/ashdroid_es_ES.dat" "$P/"
mkdir -p "$P/ashdroid_audio_es"; cp "$A/packs/fire-ash/audio_es/"* "$P/ashdroid_audio_es/"
# Spanish images; keyboard keys for the button letters (Essentials defaults)
python3 "$ROOT/tools/art/relabel.py" "$GAME" "$A/packs/fire-ash/art.json" "$TMP/art" --buttons ENTER,X,Z,D >/dev/null
mkdir -p "$P/ashdroid_art"
grep -P '^es\t' "$TMP/art/index.txt" > "$P/ashdroid_art/index.txt"
cut -f3 "$P/ashdroid_art/index.txt" | while read -r f; do cp "$TMP/art/$f" "$P/ashdroid_art/"; done
printf '%s' "$VER" > "$P/VERSION.txt"
cp "$EXE" "$TOP/Instalar parche.exe"
sed "s/\r\?$/\r/" "$ROOT/tools/pc/LEEME.txt" > "$TOP/LEEME.txt"
rm -f "$OUT"; (cd "$TMP" && zip -qr "$OUT" "$(basename "$TOP")")
rm -rf "$TMP"; echo "$OUT"

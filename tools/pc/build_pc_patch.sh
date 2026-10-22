#!/usr/bin/env bash
# Builds the PC / Steam Deck patch zip (attached to every release next to the APK):
#   tools/pc/build_pc_patch.sh OUT.zip VERSION "Instalar parche.exe" mkxp-z-linux-x86_64.tar.gz
# The installer .exe comes from the "PC installer" workflow and the Linux engine from the
# "Linux runtime" one. No game files are needed or shipped: the images with Spanish
# lettering / Steam Deck buttons are redrawn by the patch itself, from the player's own
# copy of the game, while it runs (ashdroid_art.rb + the recipes in ashdroid_art.dat).
set -euo pipefail
OUT="$(realpath -m "$1")"; VER="$2"; EXE="$3"; ENGINE="$4"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"; TOP="$TMP/Fire Ash - Espanol (Espana)"; P="$TOP/parche"; mkdir -p "$P"
A="$ROOT/app/src/main/assets"
cp "$A/ashdroid_patches.rb" "$A/ashdroid_art.rb" "$A/packs/fire-ash/ashdroid_es.dat" "$A/packs/fire-ash/ashdroid_es_ES.dat" "$P/"
ruby "$ROOT/tools/art/make_art_dat.rb" "$A/packs/fire-ash/art.json" "$P/ashdroid_art.dat" >/dev/null
mkdir -p "$P/ashdroid_audio_es"; cp "$A/packs/fire-ash/audio_es/"* "$P/ashdroid_audio_es/"
printf '%s' "$VER" > "$P/VERSION.txt"

# Windows
cp "$EXE" "$TOP/Instalar parche.exe"
sed "s/\r\?$/\r/" "$ROOT/tools/pc/LEEME.txt" > "$TOP/LEEME.txt"

# Steam Deck / Linux: installer + native engine
mkdir -p "$TOP/steamdeck"
cp "$ROOT/tools/linux/install.py" "$TOP/steamdeck/"
cp "$ROOT/tools/linux/Instalar en Steam Deck.sh" "$TOP/"
chmod 755 "$TOP/Instalar en Steam Deck.sh" "$TOP/steamdeck/install.py"
cp "$ENGINE" "$TOP/steamdeck/mkxp-z-linux-x86_64.tar.gz"

rm -f "$OUT"; (cd "$TMP" && zip -qr "$OUT" "$(basename "$TOP")")
rm -rf "$TMP"; echo "$OUT"

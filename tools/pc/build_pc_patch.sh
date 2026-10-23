#!/usr/bin/env bash
# Builds the PC patch packages attached to each release:
#   tools/pc/build_pc_patch.sh windows OUT.zip VERSION "Instalar parche.exe"
#   tools/pc/build_pc_patch.sh linux   OUT.zip VERSION mkxp-z-linux-x86_64.tar.gz
# The .exe comes from the "PC installer" workflow, the Linux engine from "Linux runtime".
# No game files are shipped: the images with Spanish lettering / Steam Deck buttons are
# redrawn by the patch itself, from the player's own copy of the game, while it runs.
set -euo pipefail
TARGET="$1"; OUT="$(realpath -m "$2")"; VER="$3"; EXTRA="$4"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
A="$ROOT/app/src/main/assets"
case "$TARGET" in
  windows) TOP="$TMP/Fire Ash - Espanol (Espana) - Windows" ;;
  linux)   TOP="$TMP/Fire Ash - Espanol (Espana) - Steam Deck" ;;
  *) echo "target: windows | linux" >&2; exit 1 ;;
esac
P="$TOP/parche"; mkdir -p "$P"
cp "$A/ashdroid_patches.rb" "$A/ashdroid_art.rb" "$A/packs/fire-ash/ashdroid_es.dat" "$A/packs/fire-ash/ashdroid_es_ES.dat" "$A/packs/fire-ash/ashdroid_en.dat" "$P/"
ruby "$ROOT/tools/art/make_art_dat.rb" "$A/packs/fire-ash/art.json" "$P/ashdroid_art.dat" >/dev/null
mkdir -p "$P/ashdroid_audio_es"; cp "$A/packs/fire-ash/audio_es/"* "$P/ashdroid_audio_es/"
printf '%s' "$VER" > "$P/VERSION.txt"

if [ "$TARGET" = windows ]; then
  cp "$EXTRA" "$TOP/Instalar parche.exe"
  sed "s/\r\?$/\r/" "$ROOT/tools/pc/LEEME.txt" > "$TOP/LEEME.txt"
else
  mkdir -p "$TOP/steamdeck"
  cp "$ROOT/tools/linux/install.py" "$TOP/steamdeck/"
  cp "$EXTRA" "$TOP/steamdeck/mkxp-z-linux-x86_64.tar.gz"
  cp "$ROOT/tools/linux/Instalar en Steam Deck.sh" "$ROOT/tools/linux/LEEME.txt" "$TOP/"
  chmod 755 "$TOP/Instalar en Steam Deck.sh" "$TOP/steamdeck/install.py"
fi

rm -f "$OUT"; (cd "$TMP" && zip -qr "$OUT" "$(basename "$TOP")")
rm -rf "$TMP"; echo "$OUT"

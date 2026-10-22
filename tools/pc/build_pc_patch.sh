#!/usr/bin/env bash
# Builds the PC / Steam Deck patch zip:
#   tools/pc/build_pc_patch.sh GAME_DIR "Instalar parche.exe" OUT.zip [VERSION] [mkxp-z-linux-x86_64.tar.gz]
# GAME_DIR is a Fire Ash install (its Graphics are needed to redraw the Spanish images);
# the installer .exe comes from the "PC installer" GitHub workflow and the Linux engine from
# the "Linux runtime" one (downloaded from its prerelease when not given).
set -euo pipefail
GAME="$1"; EXE="$2"; OUT="$(realpath -m "$3")"; VER="${4:-dev}"; ENGINE="${5:-}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"; TOP="$TMP/Fire Ash - Espanol (Espana)"; P="$TOP/parche"; mkdir -p "$P"
A="$ROOT/app/src/main/assets"
cp "$A/ashdroid_patches.rb" "$A/packs/fire-ash/ashdroid_es.dat" "$A/packs/fire-ash/ashdroid_es_ES.dat" "$P/"
mkdir -p "$P/ashdroid_audio_es"; cp "$A/packs/fire-ash/audio_es/"* "$P/ashdroid_audio_es/"

# Spanish images; keyboard keys for the button letters (Essentials defaults)
python3 "$ROOT/tools/art/relabel.py" "$GAME" "$A/packs/fire-ash/art.json" "$TMP/art" --buttons ENTER,X,Z,D >/dev/null
mkdir -p "$P/ashdroid_art"
grep -P '^es\t' "$TMP/art/index.txt" > "$P/ashdroid_art/index.txt"
cut -f3 "$P/ashdroid_art/index.txt" | while read -r f; do cp "$TMP/art/$f" "$P/ashdroid_art/"; done

# Steam Deck: the same images with the Deck's buttons (mkxp-z controller defaults:
# A = USE, B = BACK, X = ACTION, R3 = SPECIAL), also in English ("any"), plus the
# controls screen key icons. The runtime patch uses them when it detects a Deck.
python3 "$ROOT/tools/art/relabel.py" "$GAME" "$A/packs/fire-ash/art.json" "$TMP/art_deck" --buttons A,B,X,R3 >/dev/null
mkdir -p "$P/ashdroid_art_deck"
cp "$TMP/art_deck/index.txt" "$P/ashdroid_art_deck/index.txt"
cut -f3 "$P/ashdroid_art_deck/index.txt" | while read -r f; do cp "$TMP/art_deck/$f" "$P/ashdroid_art_deck/"; done
python3 "$ROOT/tools/art/controls_art.py" "$GAME" "$P/ashdroid_art_deck" A,B,X,R3 >> "$P/ashdroid_art_deck/index.txt"
sed -i '/^$/d' "$P/ashdroid_art_deck/index.txt"

printf '%s' "$VER" > "$P/VERSION.txt"
cp "$EXE" "$TOP/Instalar parche.exe"
sed "s/\r\?$/\r/" "$ROOT/tools/pc/LEEME.txt" > "$TOP/LEEME.txt"

# Steam Deck / Linux: installer + native engine
mkdir -p "$TOP/steamdeck"
cp "$ROOT/tools/linux/install.py" "$TOP/steamdeck/"
cp "$ROOT/tools/linux/Instalar en Steam Deck.sh" "$TOP/"
chmod 755 "$TOP/Instalar en Steam Deck.sh" "$TOP/steamdeck/install.py"
if [ -z "$ENGINE" ]; then
  ENGINE="$TMP/mkxp-z-linux-x86_64.tar.gz"
  curl -fsSL -o "$ENGINE" \
    https://github.com/afsenovilla/ash-droid-launcher/releases/download/linux-runtime/mkxp-z-linux-x86_64.tar.gz
fi
cp "$ENGINE" "$TOP/steamdeck/mkxp-z-linux-x86_64.tar.gz"

rm -f "$OUT"; (cd "$TMP" && zip -qr "$OUT" "$(basename "$TOP")")
rm -rf "$TMP"; echo "$OUT"

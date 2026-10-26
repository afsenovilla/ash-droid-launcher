#!/bin/sh
# Parche Ash Droid de Pokémon Fire Ash para consolas H700 (Anbernic RG34XX, RG35XX..., con Knulli).
# Se lanza desde el menú Ports de la consola, o por SSH. No necesita python ni PC:
# solo sh, unzip (o 7z/bsdtar), sed, awk y tar, que traen estos firmwares.
#
# Busca el juego (zip tal como se descarga, o carpeta ya descomprimida), lo deja en ports/FireAsh,
# instala el parche y el motor nativo ARM64 y crea "Fire Ash.sh" en Ports.
# "Fire Ash - Desinstalar.sh" lo deshace.

main() {
  HERE="$(cd "$(dirname "$0")" && pwd)"
  PAYLOAD="$HERE/FireAsh-Patch"
  PORTS="$HERE"
  LOG="$HERE/Fire Ash - instalacion.log"
  ERR="$HERE/.fireash-errors"
  GAME="$PORTS/FireAsh"
  PATCH=ashdroid_patches.rb
  MARK=ashdroid_pc_version.txt
  ENGINE=ashdroid_linux
  FONT_MARK='"fontSub": ["power green>ashdroid power green", "power clear>ashdroid power clear"],'
  VERSION="$(cat "$PAYLOAD/parche/VERSION.txt" 2>/dev/null)"

  : > "$LOG"
  : > "$ERR"
  screen_init

  if [ "$1" = "--uninstall" ]; then TITLE="Desinstalar"; uninstall; return; fi
  TITLE="Instalar"
  echo "== Parche Ash Droid de Fire Ash $VERSION (consolas H700) ==" >> "$LOG"

  step "Comprobando el paquete"
  [ -d "$PAYLOAD/parche" ] || die "Falta la carpeta FireAsh-Patch junto a este script." \
    "Copia TODO el contenido del zip del parche a la carpeta ports."
  [ -f "$PAYLOAD/mkxp-z-linux-aarch64.tar.gz" ] || die "Falta el motor (mkxp-z-linux-aarch64.tar.gz)." \
    "Vuelve a copiar la carpeta FireAsh-Patch del zip del parche."
  ok

  step "Buscando el juego"
  find_game || die "No encuentro el juego." \
    "Copia el zip de Pokemon Fire Ash (version de Windows, sin descomprimir) a la carpeta ports, junto a este script."
  ok

  install_patch
  install_engine
  write_launcher

  finish "TODO LISTO" \
    "1. START > Opciones de juego > Actualizar listas" \
    "2. Abre \"Fire Ash\" en Ports"
}

# ---------------------------------------------------------------- screen
# The Ports menu shows nothing while a script runs: the progress is drawn on the text console
# (the same trick PortMaster ports use), redrawn whole at every change. Everything also goes
# to the log.

C_RST="$(printf '\033[0m')"; C_DIM="$(printf '\033[2m')"; C_B="$(printf '\033[1m')"
C_RED="$(printf '\033[1;31m')"; C_GRN="$(printf '\033[1;32m')"; C_YEL="$(printf '\033[1;33m')"
C_CYA="$(printf '\033[1;36m')"; C_BG="$(printf '\033[44m')"

screen_init() {
  TTY=""; DONE=""; CUR=""; BAR=""
  for t in /dev/tty0 /dev/tty1; do
    if [ -w "$t" ]; then TTY="$t"; break; fi
  done
  [ -n "$TTY" ] || return 0
  export TERM=linux
  # A bigger console font if the firmware has one (the default is tiny at 640x480)
  if command -v setfont >/dev/null 2>&1; then
    for f in /usr/share/consolefonts/Lat7-Terminus24x12.psf.gz /usr/share/consolefonts/ter-v24n.psf.gz \
             /usr/share/consolefonts/Lat7-Terminus20x10.psf.gz /usr/share/consolefonts/Lat7-Terminus16.psf.gz; do
      if [ -f "$f" ]; then setfont "$f" -C "$TTY" 2>/dev/null || setfont "$f" 2>/dev/null; break; fi
    done
  fi
  command -v chvt >/dev/null 2>&1 && [ "$TTY" = /dev/tty1 ] && chvt 1 2>/dev/null
  printf '\033c\033[?25l' > "$TTY" 2>/dev/null   # clear, hide the cursor
}

# Lines end in \r\n and stay under ~50 columns (the console is about 53 x 20 with the big font)
draw() { # [extra lines...]
  [ -n "$TTY" ] || return 0
  {
    printf '\033[H\033[J\r\n'
    printf '  %s%s  POKEMON FIRE ASH  %s\r\n' "$C_BG" "$C_B" "$C_RST"
    printf '  %sParche Ash Droid %s - %s%s\r\n\r\n' "$C_CYA" "$VERSION" "$TITLE" "$C_RST"
    printf '%s' "$DONE"
    [ -n "$CUR" ] && printf '  %s[ .. ]%s %s\r\n' "$C_YEL" "$C_RST" "$CUR"
    [ -n "$BAR" ] && printf '         %s\r\n' "$BAR"
    for l in "$@"; do printf '%s\r\n' "$l"; done
  } > "$TTY" 2>/dev/null
  return 0
}

wrap() { # text -> lines of at most 44 columns
  if command -v fold >/dev/null 2>&1; then printf '%s\n' "$1" | fold -s -w 44
  else printf '%s\n' "$1" | cut -c1-44; fi
}

step() { CUR="$1"; BAR=""; echo "-- $1" >> "$LOG"; echo "$1..."; draw; }

ok() { # [detail]
  DONE="$DONE$(printf '  %s[ OK ]%s %s\r' "$C_GRN" "$C_RST" "$CUR")
"
  [ -n "$1" ] && DONE="$DONE$(printf '         %s%s%s\r' "$C_DIM" "$(printf '%s' "$1" | cut -c1-40)" "$C_RST")
"
  echo "   OK $1" >> "$LOG"; echo "   OK $1"
  CUR=""; BAR=""; draw
}

progress() { # percent, text
  n=$(( $1 / 5 )); i=0; f=""; e=""
  while [ $i -lt 20 ]; do if [ $i -lt $n ]; then f="$f#"; else e="$e-"; fi; i=$((i + 1)); done
  BAR="$C_GRN$f$C_RST$C_DIM$e$C_RST $(printf '%3d' "$1")%  $C_DIM$2$C_RST"
  draw
}

# Error: the step that failed, what to do, and the last lines the tools wrote; stays on screen
die() { # message, [hint]
  { echo "ERROR: $1"; [ -n "$2" ] && echo "  $2"; cat "$ERR"; } >> "$LOG"
  echo "ERROR: $1"; [ -n "$2" ] && echo "  $2"
  [ -n "$CUR" ] && DONE="$DONE$(printf '  %s[FALLO]%s %s\r' "$C_RED" "$C_RST" "$CUR")
"
  CUR=""; BAR=""
  lines="$(wrap "$1")"
  [ -n "$2" ] && lines="$lines
$(wrap "$2")"
  set -- "" "  ${C_RED}ERROR${C_RST}"
  while IFS= read -r l; do set -- "$@" "  $l"; done <<EOF_MSG
$lines
EOF_MSG
  if [ -s "$ERR" ]; then
    set -- "$@" "" "  ${C_DIM}Detalle:${C_RST}"
    while IFS= read -r l; do set -- "$@" "  $C_DIM$l$C_RST"; done <<EOF_ERR
$(tail -n 3 "$ERR" | cut -c1-44)
EOF_ERR
  fi
  set -- "$@" "  ${C_DIM}Log: ports/Fire Ash - instalacion.log${C_RST}"
  draw "$@"
  rm -f "$ERR"
  hold 30
  exit 1
}

finish() { # title, lines...
  t="$1"; shift
  esc="$(printf '\033')"
  printf '%s\n' "$t" "$@" | tee -a "$LOG"
  out=""
  for l in "$@"; do out="$out
  $l"; done
  set -- "" "  ${C_GRN}${C_B}${t}${C_RST}"
  while IFS= read -r l; do [ -n "$l" ] && set -- "$@" "$l"; done <<EOF_FIN
$out
EOF_FIN
  draw "$@"
  rm -f "$ERR"
  hold 12
}

# Back to the menu after a few seconds (the cursor and screen as they were)
hold() { [ -n "$TTY" ] && sleep "$1"; [ -n "$TTY" ] && printf '\033c\033[?25h' > "$TTY" 2>/dev/null; return 0; }

# ----------------------------------------------------------------- game

is_game() { [ -f "$1/mkxp.json" ]; }

# Zip of the game as downloaded: prints the folder inside the zip that holds mkxp.json ("." = root)
zip_root() {
  hit="$(zip_list "$1" | grep -iE '(^|/)mkxp\.json$' | awk '{print gsub("/","/") " " $0}' | sort -n | head -n 1 | cut -d' ' -f2-)"
  [ -n "$hit" ] || return 1
  case "$hit" in */*) echo "${hit%/*}" ;; *) echo "." ;; esac
}

zip_list() {
  if command -v unzip >/dev/null 2>&1; then
    out="$(unzip -Z1 "$1" 2>/dev/null)"
    # unzip without -Z (BusyBox): parse the normal listing
    [ -n "$out" ] || out="$(unzip -l "$1" 2>/dev/null | awk 'NR > 3 && NF >= 4 { sub(/^ *[0-9]+ +[0-9-]+ +[0-9:]+ +/, ""); print }')"
    printf '%s\n' "$out"
  elif command -v 7z >/dev/null 2>&1; then 7z l -slt "$1" 2>/dev/null | sed -n 's/^Path = //p'
  elif command -v bsdtar >/dev/null 2>&1; then bsdtar -tf "$1" 2>/dev/null
  else return 1; fi
}

zip_extract() { # zip dest
  if command -v unzip >/dev/null 2>&1; then unzip -oq "$1" -d "$2"
  elif command -v 7z >/dev/null 2>&1; then 7z x -y -o"$2" "$1" >/dev/null
  elif command -v bsdtar >/dev/null 2>&1; then bsdtar -xf "$1" -C "$2"
  else return 1; fi
}

find_game() {
  # 1) already installed / extracted next to the script
  if is_game "$GAME"; then return 0; fi
  roots="$PORTS /userdata/roms/ports /userdata/roms /userdata/downloads /userdata/system /userdata/saves"
  # 2) a folder with the game already extracted (name with "fire" and "ash")
  for r in $roots; do
    [ -d "$r" ] || continue
    for d in "$r"/*; do
      [ -d "$d" ] || continue
      case "$(basename "$d" | tr 'A-Z' 'a-z')" in *fire*ash*) ;; *) continue ;; esac
      if is_game "$d"; then GAME="$d"; return 0; fi
      for d2 in "$d"/*; do
        if [ -d "$d2" ] && is_game "$d2"; then GAME="$d2"; return 0; fi
      done
    done
  done
  # 3) the zip as downloaded
  for r in $roots; do
    [ -d "$r" ] || continue
    for z in "$r"/*.zip; do
      [ -f "$z" ] || continue
      case "$(basename "$z" | tr 'A-Z' 'a-z')" in *fire*ash*) ;; *) continue ;; esac
      root="$(zip_root "$z")" || continue
      ok "$(basename "$z")"
      step "Descomprimiendo el juego"
      tmp="$PORTS/.fireash-extract"
      rm -rf "$tmp"; mkdir -p "$tmp"
      total="$(zip_list "$z" | grep -c .)"
      zip_extract "$z" "$tmp" >> "$LOG" 2>> "$ERR" &
      pid=$!
      # Progress: files written so far against the files in the zip
      while kill -0 "$pid" 2>/dev/null; do
        done_n="$(find "$tmp" 2>/dev/null | wc -l)"
        pct=$(( total > 0 ? done_n * 100 / total : 0 )); [ "$pct" -gt 99 ] && pct=99
        progress "$pct" "$done_n/$total archivos"
        sleep 2
      done
      wait "$pid" || { rm -rf "$tmp"; die "No he podido descomprimir el zip del juego." \
        "Puede que el zip este incompleto o que no quede espacio en la tarjeta."; }
      progress 100 "$total archivos"
      rm -rf "$GAME"
      if [ "$root" = "." ]; then mv "$tmp" "$GAME"; else mv "$tmp/$root" "$GAME"; rm -rf "$tmp"; fi
      is_game "$GAME" && return 0
    done
  done
  return 1
}

# ---------------------------------------------------------------- patch

# mkxp.json is edited line by line, like the PC installers do (the game's own is kept as mkxp.json.bak)
insert_after_brace() { # file, text (no backslashes)
  BLK="$2" awk 'BEGIN{d=0; blk=ENVIRON["BLK"]} { if (!d && (i = index($0, "{")) > 0) { $0 = substr($0, 1, i) "\n" blk substr($0, i + 1); d = 1 } print }' "$1" > "$1.tmp" && mv "$1.tmp" "$1"
}

install_patch() {
  step "Instalando el parche"
  conf="$GAME/mkxp.json"
  [ -f "$conf.bak" ] || cp "$conf" "$conf.bak"
  # parche + traducciones + audio de la intro (sin VERSION.txt)
  ( cd "$PAYLOAD/parche" && tar cf - . ) | ( cd "$GAME" && tar -xof - ) 2>> "$ERR" \
    || die "No he podido copiar el parche." "Comprueba que queda espacio en la tarjeta."
  cp "$PAYLOAD/parche/VERSION.txt" "$GAME/$MARK"

  if ! grep -q "$PATCH" "$conf"; then
    if grep -q '^[[:space:]]*"preloadScript"[[:space:]]*:[[:space:]]*\[' "$conf"; then
      sed -i "s/^\\([[:space:]]*\"preloadScript\"[[:space:]]*:[[:space:]]*\\[\\)/\\1\"$PATCH\", /" "$conf"
    else
      insert_after_brace "$conf" "  \"preloadScript\": [\"$PATCH\"],"
    fi
  fi
  # Pantalla de la consola: pantalla completa, sin deformar, sin suavizado (el juego es 640x480).
  # Si la clave ya está en el archivo se cambia su valor en su sitio; si no, se añade al principio.
  missing=""
  for kv in fullscreen=true fixedAspectRatio=true smoothScaling=0; do
    k="${kv%%=*}"; v="${kv#*=}"
    if grep -q "^[[:space:]]*\"$k\"[[:space:]]*:" "$conf"; then
      sed -i -E "s|^([[:space:]]*\"$k\"[[:space:]]*:[[:space:]]*)[^,/[:space:]]+|\\1$v|" "$conf"
    else
      missing="$missing  \"$k\": $v,
"
    fi
  done
  [ -z "$missing" ] || insert_after_brace "$conf" "${missing%?}"
  # Fuentes: el parche escribe las copias corregidas al arrancar; aquí solo se activa la sustitución
  if ! grep -q '^[[:space:]]*"fontSub"' "$conf" && ! grep -qF "$FONT_MARK" "$conf"; then
    insert_after_brace "$conf" "  $FONT_MARK"
  fi
  # Marca de que es una consola (el parche la usa para mostrar L2/R2 y los botones de la consola)
  [ -f "$GAME/ashdroid_handheld.txt" ] || printf 'layout=same\n' > "$GAME/ashdroid_handheld.txt"
  ok
}

install_engine() {
  step "Instalando el motor"
  rm -rf "$GAME/$ENGINE"; mkdir -p "$GAME/$ENGINE"
  # -o: don't try to give the files the owner they had on the build machine. The ports folder is
  # usually on the card's exFAT/FAT partition, which has no owners, and tar fails on it otherwise.
  tar -xzof "$PAYLOAD/mkxp-z-linux-aarch64.tar.gz" -C "$GAME/$ENGINE" 2>> "$ERR" \
    || die "No he podido instalar el motor." "Comprueba que queda espacio en la tarjeta."
  [ -f "$GAME/$ENGINE/mkxp-z.aarch64" ] || die "El motor no se ha descomprimido entero." \
    "Comprueba que queda espacio en la tarjeta."
  chmod 755 "$GAME/$ENGINE/mkxp-z.aarch64"
  ok
}

write_launcher() {
  step "Creando \"Fire Ash\" en Ports"
  L="$PORTS/Fire Ash.sh"
  cat > "$L" <<LAUNCH
#!/bin/sh
# Pokémon Fire Ash con el motor nativo de Linux (mkxp-z) y el parche Ash Droid.
# Si algo falla, mira ashdroid_run.log en la carpeta del juego. Para probar otro vídeo/audio
# (SDL_VIDEODRIVER, SDL_AUDIODRIVER...) créate un ashdroid_env.sh en esa carpeta.
main() {
  cd "$GAME" || exit 1
  export SRCDIR="\$PWD"
  export ASHDROID_DEVICE=handheld
  export HOME="\${HOME:-/userdata/system}"
  [ -f ./ashdroid_env.sh ] && . ./ashdroid_env.sh
  for t in /dev/tty0 /dev/tty1; do
    if [ -w "\$t" ]; then printf '\\033c\\n  Cargando Fire Ash...\\n' > "\$t" 2>/dev/null; break; fi
  done
  exec "./$ENGINE/mkxp-z.aarch64" > ./ashdroid_run.log 2>&1
}
main "\$@"; exit
LAUNCH
  chmod 755 "$L"
  [ -f "$L" ] || die "No he podido crear $L."
  ok
}

uninstall() {
  step "Quitando el parche y el motor"
  [ -d "$GAME" ] || { ok "no habia nada instalado"; finish "NADA QUE QUITAR"; return; }
  conf="$GAME/mkxp.json"
  if [ -f "$conf.bak" ]; then cp "$conf.bak" "$conf" && rm -f "$conf.bak"; fi
  rm -rf "$GAME/$ENGINE" "$GAME"/ashdroid_* "$GAME"/Fonts/ashdroid_* "$GAME/ashdroid_pc_version.txt" 2>/dev/null
  rm -f "$PORTS/Fire Ash.sh"
  ok
  finish "DESINSTALADO" "El juego y tus partidas no se han tocado."
}

main "$@"; exit

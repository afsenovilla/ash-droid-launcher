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
  GAME="$PORTS/FireAsh"
  PATCH=ashdroid_patches.rb
  MARK=ashdroid_pc_version.txt
  ENGINE=ashdroid_linux
  FONT_MARK='"fontSub": ["power green>ashdroid power green", "power clear>ashdroid power clear"],'

  : > "$LOG"
  screen_init
  say() { echo "$*"; echo "$*" >> "$LOG"; show "$*"; }
  die() { say ""; say "ERROR: $*"; say "Mira el log: $LOG"; hold 15; exit 1; }

  [ -d "$PAYLOAD/parche" ] || die "Falta la carpeta FireAsh-Patch junto a este script: copia TODO el contenido del zip a la carpeta ports."
  [ -f "$PAYLOAD/mkxp-z-linux-aarch64.tar.gz" ] || die "Falta el motor (mkxp-z-linux-aarch64.tar.gz) en FireAsh-Patch."

  if [ "$1" = "--uninstall" ]; then uninstall; return; fi

  echo "== Parche Ash Droid de Fire Ash (consolas H700) ==" >> "$LOG"
  find_game || die "No encuentro el juego. Copia el zip de Pokémon Fire Ash (versión de Windows, del Discord del juego) a la carpeta ports junto a este script y vuelve a lanzarlo."
  say "Juego: $GAME"

  install_patch
  install_engine
  write_launcher

  say ""
  say "LISTO. En el menu Ports, actualiza las listas de juegos"
  say "(START > Opciones de juego > Actualizar listas)"
  say "y abre \"Fire Ash\"."
  hold 10
}

# ---------------------------------------------------------------- screen
# The Ports menu shows nothing while a script runs: write the progress on the console
# (text terminal) so the screen isn't just black. Same trick PortMaster ports use.

screen_init() {
  TTY=""
  for t in /dev/tty0 /dev/tty1; do
    if [ -w "$t" ]; then TTY="$t"; break; fi
  done
  [ -n "$TTY" ] || return 0
  export TERM=linux
  # A bigger console font if the firmware has one (the default is tiny on 640x480)
  if command -v setfont >/dev/null 2>&1; then
    for f in /usr/share/consolefonts/Lat7-Terminus24x12.psf.gz /usr/share/consolefonts/ter-v24n.psf.gz \
             /usr/share/consolefonts/Lat7-Terminus20x10.psf.gz /usr/share/consolefonts/Lat7-Terminus16.psf.gz; do
      if [ -f "$f" ]; then setfont "$f" -C "$TTY" 2>/dev/null || setfont "$f" 2>/dev/null; break; fi
    done
  fi
  printf '\033c\033[?25l' > "$TTY" 2>/dev/null   # clear the screen, hide the cursor
  # Show the console in front of the menu while the script runs
  command -v chvt >/dev/null 2>&1 && [ "$TTY" = /dev/tty1 ] && chvt 1 2>/dev/null
  show "== Parche Ash Droid de Fire Ash =="
  show ""
}

show() { [ -n "$TTY" ] && printf '%s\n' "$*" > "$TTY" 2>/dev/null; return 0; }

# Leave the last lines on screen for a few seconds before going back to the menu
hold() { [ -n "$TTY" ] && sleep "$1"; printf '\033c\033[?25h' > "$TTY" 2>/dev/null; return 0; }

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
      say "Descomprimiendo $(basename "$z") (puede tardar un par de minutos)..."
      tmp="$PORTS/.fireash-extract"
      rm -rf "$tmp"; mkdir -p "$tmp"
      total="$(zip_list "$z" | grep -c .)"
      zip_extract "$z" "$tmp" >> "$LOG" 2>&1 &
      pid=$!
      # Progress: files written so far against the files in the zip
      while kill -0 "$pid" 2>/dev/null; do
        done_n="$(find "$tmp" 2>/dev/null | wc -l)"
        pct=$(( total > 0 ? done_n * 100 / total : 0 )); [ "$pct" -gt 99 ] && pct=99
        [ -n "$TTY" ] && printf '\r  %3d %%  (%s / %s archivos)   ' "$pct" "$done_n" "$total" > "$TTY" 2>/dev/null
        sleep 2
      done
      wait "$pid" || { rm -rf "$tmp"; die "No he podido descomprimir el zip (¿falta unzip?)."; }
      [ -n "$TTY" ] && printf '\r  100 %%                                  \n' > "$TTY" 2>/dev/null
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
  say "Instalando el parche..."
  conf="$GAME/mkxp.json"
  [ -f "$conf.bak" ] || cp "$conf" "$conf.bak"
  # parche + traducciones + audio de la intro (sin VERSION.txt)
  ( cd "$PAYLOAD/parche" && tar cf - . ) | ( cd "$GAME" && tar xf - ) || die "No he podido copiar el parche."
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
  say "Parche instalado ($(cat "$GAME/$MARK"))."
}

install_engine() {
  say "Instalando el motor..."
  rm -rf "$GAME/$ENGINE"; mkdir -p "$GAME/$ENGINE"
  tar xzf "$PAYLOAD/mkxp-z-linux-aarch64.tar.gz" -C "$GAME/$ENGINE" || die "No he podido instalar el motor."
  chmod 755 "$GAME/$ENGINE/mkxp-z.aarch64"
  say "Motor instalado."
}

write_launcher() {
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
  say "Lanzador creado: $L"
}

uninstall() {
  [ -d "$GAME" ] || { say "No hay nada instalado en $GAME."; return; }
  conf="$GAME/mkxp.json"
  if [ -f "$conf.bak" ]; then cp "$conf.bak" "$conf" && rm -f "$conf.bak"; fi
  rm -rf "$GAME/$ENGINE" "$GAME"/ashdroid_* "$GAME"/Fonts/ashdroid_* "$GAME/ashdroid_pc_version.txt" 2>/dev/null
  rm -f "$PORTS/Fire Ash.sh"
  say "Parche y motor quitados. El juego y tus partidas no se han tocado."
  hold 6
}

main "$@"; exit

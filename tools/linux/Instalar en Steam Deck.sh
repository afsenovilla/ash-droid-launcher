#!/bin/sh
# Parche "Español (España)" de Pokémon Fire Ash para Steam Deck / Linux (modo escritorio).
# Doble clic (o "Ejecutar"). Busca el juego, instala el parche y el motor nativo y lo añade a Steam.
cd "$(dirname "$0")" || exit 1
if ! command -v python3 >/dev/null 2>&1; then
  msg="Falta python3 en este sistema."
  command -v kdialog >/dev/null 2>&1 && kdialog --error "$msg" || echo "$msg"
  exit 1
fi
exec python3 steamdeck/install.py "$@"

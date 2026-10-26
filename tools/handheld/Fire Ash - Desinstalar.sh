#!/bin/sh
# Quita el parche Ash Droid y el motor nativo (el juego y las partidas no se tocan).
cd "$(dirname "$0")" || exit 1
exec sh "./Fire Ash - Instalar.sh" --uninstall

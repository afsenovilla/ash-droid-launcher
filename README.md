# Ash Droid Launcher

Lanzador Android para **Pokémon Fire Ash** (y en principio otros fangames de Pokémon Essentials que ya funcionen
con [mkxp-z](https://github.com/mkxp-z/mkxp-z)), pensado para **consolas portátiles Android con botones físicos**
(Retroid, AYN, Anbernic…).

> *English: generic Android launcher for mkxp-z based Pokémon Essentials fangames. Import the game's original zip,
> it auto-configures screen scaling for your device and asks you to map your physical buttons.*

**Este repositorio no contiene ni distribuye ningún juego.** Necesitas el `.zip` original del juego descargado de
su fuente oficial (en el caso de Fire Ash, su servidor de Discord).

## Cómo funciona

1. Instala el APK (pestaña [Releases](../../releases)).
2. Copia el `.zip` del juego a la consola y pulsa **Importar juego**. Se descomprime en el almacenamiento privado de la app.
3. La app **detecta tu dispositivo**:
   - **Pantalla**: resolución, proporción y frecuencia. Calcula el mejor escalado: si el factor es entero, píxeles
     perfectos; si no (p. ej. ×2,5 en la Retroid Pocket Nova), escala ×2 nítido + un último ajuste suave.
   - **Mando integrado**: nombre y fabricante, y lo cruza con una pequeña [base de datos de consolas](app/src/main/assets/devices.json).
4. **Asistente de botones**: te pide pulsar el botón que quieres para cada acción (Confirmar, Cancelar, Correr,
   Acelerar…). Funciona en cualquier consola, sea cual sea la distribución A/B (Nintendo o Xbox).
5. Genera el `mkxp.json` adaptado (se guarda el original como `mkxp.original.json`) y arranca el juego.

Las partidas guardadas se conservan al actualizar la app o reimportar una versión nueva del juego.

## Modos de imagen

| Modo | Qué hace |
|---|---|
| Automático | Entero exacto si se puede; si no, entero nítido + ajuste final suave |
| Nítido | Solo escalado entero (puede dejar bordes) |
| Suave | Escalado filtrado a todo el alto |
| Estirar | Ocupa toda la pantalla aunque deforme |

## Añadir tu consola

Edita [`devices.json`](app/src/main/assets/devices.json) y abre un PR. La pantalla siempre se detecta en vivo y los
botones siempre se confirman con el asistente, así que la base de datos solo aporta nombre y pistas.

## Compilación

GitHub Actions compila el APK en cada push (`.github/workflows/build.yml`):

- `main` → prerelease **dev** con `ash-droid-launcher-dev.apk`.
- tag `vX.Y.Z` → release estable.

Localmente: NDK r25c, JDK 17, y los mismos pasos del workflow (`app/jni/get_deps.sh`, `make` en `app/jni`,
`make_xxd.sh` en `app/jni/mkxp-z`, `./gradlew assembleRelease`).

El APK se firma con una clave pública fija (ver [`keystore/README.md`](keystore/README.md)) para que cada versión se
instale encima de la anterior sin perder datos.

## Créditos y licencia

- Motor: [mkxp-z](https://github.com/mkxp-z/mkxp-z) (Ancurio, Struma y colaboradores).
- Port Android base: [thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android).
- SDL2, Ruby, OpenAL Soft, PhysicsFS y demás dependencias, cada una con su licencia.

Licencia: GPL-2.0-or-later (como mkxp-z). Pokémon es marca de Nintendo / Game Freak / The Pokémon Company;
este proyecto no está afiliado a ellos.

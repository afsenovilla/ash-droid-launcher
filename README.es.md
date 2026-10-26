# Ash Droid Launcher

[English](README.md) · **Español** · [Français](README.fr.md) · [Deutsch](README.de.md)

Lanzador Android para **Pokémon Fire Ash** (y en principio otros fangames de Pokémon Essentials que ya funcionen
con [mkxp-z](https://github.com/mkxp-z/mkxp-z)), pensado para **consolas portátiles Android con botones físicos**
(Retroid, AYN, Anbernic…).


**Este repositorio no contiene ni distribuye ningún juego.** Necesitas el `.zip` original del juego descargado de
su fuente oficial (en el caso de Fire Ash, su [servidor de Discord](https://discord.com/invite/7EvBgnrpcU)).

## Cómo funciona

1. Instala el APK (pestaña [Releases](https://github.com/afsenovilla/ash-droid-launcher/releases)).
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

## Traducciones

El launcher aplica al juego un parche en tiempo de ejecución (`ashdroid_patches.rb`, no modifica archivos del juego)
con retraducciones completas, hechas desde el inglés al estilo de los juegos oficiales:

| Idioma | En el juego | Qué incluye |
|---|---|---|
| Español (España) | sustituye a "Spanish" como **Spanish (Spain)** | diálogos, menús, combates, objetos, movimientos, habilidades, Pokédex, nombres oficiales y términos del doblaje de España, imágenes con texto redibujadas en español, intro en castellano |
| Francés | French | diálogos, menús, combates, descripciones y nombres oficiales corregidos, intro en francés |
| Alemán | German | diálogos, menús, combates, descripciones y nombres oficiales (los datos alemanes del juego tenían muchos nombres asignados al Pokémon, movimiento, objeto o entrenador equivocado), intro en alemán |
| Inglés | English | solo arreglos: descripciones que no cabían en su caja |

Todas las descripciones se miden con la fuente del juego para que quepan. La intro suena en el idioma del juego y
se puede quitar en las Opciones del juego.

## PC y Steam Deck

El mismo parche (con todos los idiomas de arriba) existe para la versión de Windows del juego (`tools/pc/`): un zip
con un instalador gráfico (`Instalar parche.exe`). Para la **Steam Deck** (LCD y OLED) y otros Linux hay otro zip
con el motor mkxp-z compilado para Linux, así que no hace falta Proton: `Instalar en Steam Deck.sh`
(modo escritorio) busca el juego, instala el parche y lo añade a Steam. El parche detecta la Deck y
muestra sus botones (A, B, X, Y, L1, R1, R3…) en textos e imágenes; ☰ abre el menú.

Actualizaciones: la app de Android busca versiones nuevas y las instala encima; en la Steam Deck el juego lo
comprueba al abrirse y ofrece actualizar el parche. En Windows, descarga el zip nuevo y vuelve a ejecutar el instalador.

## Consolas Anbernic H700 (RG34XX, RG35XX…)

Un cuarto zip, `FireAsh-Patch-Handheld-H700-X.Y.Z.zip`, es para consolas Linux con Allwinner H700 (pensado
para la RG34XX con [Knulli](https://knulli.org); aún sin probar en la consola). Lleva una versión **ARM64**
nativa de mkxp-z y el mismo parche. No hace falta Python ni PC para instalarlo.

1. Descarga el juego (la versión de Windows, del Discord de Fire Ash) y este zip.
2. Descomprime **solo este zip** y copia todo su contenido a la carpeta `ports` de la consola
   (en Knulli: `roms/ports` de la tarjeta, o `share/roms/ports` por red).
3. Copia también a esa misma carpeta `ports` **el zip del juego tal cual se descarga, sin descomprimir**
   (si ya lo tienes descomprimido, vale la carpeta del juego, con un nombre que contenga "Fire Ash").
   Tiene que quedar así:

   ```
   roms/ports/
   ├── Fire Ash - Instalar.sh
   ├── Fire Ash - Desinstalar.sh
   ├── FireAsh-Patch/
   ├── LEEME.txt
   └── Pokemon Fire Ash 3.7.1.zip      ← el juego, comprimido
   ```
4. En la consola, abre **Ports → "Fire Ash - Instalar"**. Descomprime el juego en `ports/FireAsh`
   (tarda unos minutos), instala el parche y el motor y crea la entrada "Fire Ash". Después puedes borrar
   el zip del juego.
5. Actualiza las listas de juegos (START → Opciones de juego → Actualizar listas) y abre **"Fire Ash"**
   desde Ports.

Estas consolas no tienen clic de palancas, así que L2 / R2 hacen lo de L3 / R3 en la Deck, y los textos
muestran los botones de la consola. Si algo falla: `Fire Ash - instalacion.log` (junto al instalador) y
`FireAsh/ashdroid_run.log`. Más detalles en el `LEEME.txt` del zip. Otras consolas Linux más modestas
(p. ej. Miyoo Mini, 128 MB de RAM y sin driver de GPU utilizable) no pueden ejecutar mkxp-z.

## Pendiente

- Imágenes con texto (títulos, menús, medallas…) redibujadas en francés y alemán, como ya está hecho en español.
- Revisión de las traducciones al francés y al alemán por nativos (algunos personajes secundarios no tienen
  nombre oficial confirmado).
- Actualizaciones automáticas del parche de Windows.

## Compilación

GitHub Actions compila el APK en cada push (`.github/workflows/build.yml`):

- `main` → prerelease **dev** con `ash-droid-launcher-dev.apk`.
- tag `vX.Y.Z` → release estable.

Localmente: NDK r25c, JDK 17, y los mismos pasos del workflow (`app/jni/get_deps.sh`, `make` en `app/jni`,
`make_xxd.sh` en `app/jni/mkxp-z`, `./gradlew assembleRelease`).

El APK se firma con una clave pública fija (ver [`keystore/README.md`](keystore/README.md)) para que cada versión se
instale encima de la anterior sin perder datos.

## Versiones y releases

Las versiones siguen [Semantic Versioning](https://semver.org/); los cambios están en [CHANGELOG.md](CHANGELOG.md).
Cada release (tag `vX.Y.Z`) trae cuatro descargas:

| Plataforma | Archivo |
|---|---|
| Android | `ash-droid-launcher-X.Y.Z.apk` |
| Windows | `FireAsh-Patch-Windows-X.Y.Z.zip` |
| Steam Deck / Linux | `FireAsh-Patch-SteamDeck-Linux-X.Y.Z.zip` |
| Consolas Anbernic H700 (RG34XX, RG35XX…, Knulli) | `FireAsh-Patch-Handheld-H700-X.Y.Z.zip` |

Para publicar: añade una sección `## [X.Y.Z]` arriba del changelog y súbelo a `main`; el workflow crea el
tag `vX.Y.Z` y publica la release. El resto de push a `main` solo se compilan, no se publican.

## Créditos y licencia

- Motor: [mkxp-z](https://github.com/mkxp-z/mkxp-z) (Ancurio, Struma y colaboradores).
- Port Android base: [thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android).
- SDL2, Ruby, OpenAL Soft, PhysicsFS y demás dependencias, cada una con su licencia.

Licencia: GPL-2.0-or-later (como mkxp-z). Pokémon es marca de Nintendo / Game Freak / The Pokémon Company;
este proyecto no está afiliado a ellos.

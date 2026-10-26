# Ash Droid Launcher

**English** · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md)

Android launcher for **Pokémon Fire Ash** (and, in principle, other Pokémon Essentials fangames that already run on
[mkxp-z](https://github.com/mkxp-z/mkxp-z)), built for **Android handhelds with physical controls**
(Retroid, AYN, Anbernic…).

**This repository does not contain or distribute any game.** You need the game's original `.zip`, downloaded from
its official source (for Fire Ash, its [Discord server](https://discord.com/invite/7EvBgnrpcU)).

## How it works

1. Install the APK (see [Releases](https://github.com/afsenovilla/ash-droid-launcher/releases)).
2. Copy the game's `.zip` to the device and tap **Import game**. It's extracted into the app's private storage.
3. The app **detects your device**:
   - **Screen**: resolution, aspect ratio and refresh rate. It picks the best scaling: pixel-perfect when the
     factor is an integer; otherwise (e.g. ×2.5 on the Retroid Pocket Nova) a sharp ×2 plus a short smooth final step.
   - **Built-in controller**: name and vendor, matched against a small [device database](app/src/main/assets/devices.json).
4. **Button wizard**: press the button you want for each action (Confirm, Cancel, Run, Speed up…). Works on any
   device regardless of its A/B layout (Nintendo or Xbox style).
5. It writes a device-tuned `mkxp.json` (the game's original is kept as `mkxp.original.json`) and starts the game.

Save files are kept when you update the app or re-import a new version of the game.

## Display modes

| Mode | What it does |
|---|---|
| Automatic | Exact integer scale when possible; otherwise sharp integer scale + smooth final step |
| Sharp | Integer scaling only (may leave borders) |
| Smooth | Filtered scaling to full height |
| Stretch | Fills the whole screen even if it distorts |

## Adding your device

Edit [`devices.json`](app/src/main/assets/devices.json) and open a PR. The screen is always detected live and buttons
are always confirmed in the wizard, so the database only provides a friendly name and hints.

## Translations

The launcher applies a runtime patch to the game (`ashdroid_patches.rb`, no game files are modified) with full
retranslations, written from the English source in the style of the official games:

| Language | In game | What it includes |
|---|---|---|
| Spanish (Spain) | replaces "Spanish" as **Spanish (Spain)** | dialogue, menus, battles, items, moves, abilities, Pokédex, official names and the Spanish dub's terms, images with text redrawn in Spanish, intro song in Spanish |
| French | French | dialogue, menus, battles, descriptions and official names fixed, intro song in French |
| German | German | dialogue, menus, battles, descriptions, and official names (the game's German data had many names shifted onto the wrong Pokémon, moves, items and trainers), intro song in German |
| English | English | only fixes: descriptions that didn't fit their boxes |

Every description is measured with the game's font so it fits its box. The intro song plays in the game's
language and can be switched off from the game's Options.

## PC and Steam Deck

The same patch (all the languages above) exists for the Windows version of the game (`tools/pc/`): a zip with a
graphical installer (`Instalar parche.exe`). For the **Steam Deck** (LCD and OLED) and other Linux machines there is
a separate zip with a native Linux build of mkxp-z, so no Proton is needed: `Instalar en Steam Deck.sh` (desktop
mode) finds the game, installs the patch and adds it to Steam. The patch detects the Deck and shows its
buttons (A, B, X, Y, L1, R1, R3…) in texts and images; ☰ opens the menu.

Updates: the Android app checks for new releases and installs them in place; on the Steam Deck the game checks
when it starts and offers to update the patch. On Windows, download the new zip and run the installer again.

## Anbernic H700 consoles (RG34XX, RG35XX…)

A fourth zip, `FireAsh-Patch-Handheld-H700-X.Y.Z.zip`, is for Linux handhelds with an Allwinner H700 (made for
the RG34XX with [Knulli](https://knulli.org); not yet tested on the console). It carries a native **ARM64**
build of mkxp-z and the same patch. No Python or PC is needed to install it.

1. Download the game (the Windows version, from the Fire Ash Discord) and this zip.
2. Unzip **only this zip** and copy everything in it to the console's `ports` folder
   (on Knulli: `roms/ports` on the card, or `share/roms/ports` over the network).
3. Copy **the game's zip, as downloaded, still zipped** to that same `ports` folder too
   (an already extracted game folder also works, if its name contains "Fire Ash"). It should look like this:

   ```
   roms/ports/
   ├── Fire Ash - Instalar.sh
   ├── Fire Ash - Desinstalar.sh
   ├── FireAsh-Patch/
   ├── LEEME.txt
   └── Pokemon Fire Ash 3.7.1.zip      ← the game, still zipped
   ```
4. On the console, open **Ports → "Fire Ash - Instalar"**. It unpacks the game into `ports/FireAsh`
   (a few minutes), installs the patch and engine and adds a "Fire Ash" entry. You can delete the game's zip
   afterwards.
5. Refresh the game lists (START → Game settings → Update game lists) and open **"Fire Ash"** from Ports.

The consoles have no stick clicks, so L2 / R2 do what L3 / R3 do on the Deck, and the texts show the
console's buttons. If something fails: `Fire Ash - instalacion.log` (next to the installer) and
`FireAsh/ashdroid_run.log`. More in the `LEEME.txt` inside the zip. Other low-end Linux handhelds (e.g. Miyoo
Mini, 128 MB RAM, no usable GPU driver) can't run mkxp-z.

## To do

- Images with text (titles, menus, badges…) redrawn in French and German, as already done for Spanish.
- Review pass of the French and German translations by native speakers (some minor characters have no
  confirmed official name).
- Automatic updates for the Windows patch.

## Building

GitHub Actions builds the APK on every push (`.github/workflows/build.yml`):

- `main` → rolling **dev** prerelease with `ash-droid-launcher-dev.apk`.
- tag `vX.Y.Z` → stable release.

Locally: NDK r25c, JDK 17, and the same steps as the workflow (`app/jni/get_deps.sh`, `make` in `app/jni`,
`make_xxd.sh` in `app/jni/mkxp-z`, `./gradlew assembleRelease`).

The APK is signed with a fixed public key (see [`keystore/README.md`](keystore/README.md)) so each version installs
over the previous one without losing data.

## Versions and releases

Versions follow [Semantic Versioning](https://semver.org/); changes are listed in [CHANGELOG.md](CHANGELOG.md).
Each release (`vX.Y.Z` tag) has four downloads:

| Platform | File |
|---|---|
| Android | `ash-droid-launcher-X.Y.Z.apk` |
| Windows | `FireAsh-Patch-Windows-X.Y.Z.zip` |
| Steam Deck / Linux | `FireAsh-Patch-SteamDeck-Linux-X.Y.Z.zip` |
| Anbernic H700 consoles (RG34XX, RG35XX…, Knulli) | `FireAsh-Patch-Handheld-H700-X.Y.Z.zip` |

To release: add a `## [X.Y.Z]` section at the top of the changelog and push it to `main`; the workflow
tags `vX.Y.Z` and publishes the release. Any other push to `main` is only built, never published.

## Credits and license

- Engine: [mkxp-z](https://github.com/mkxp-z/mkxp-z) (Ancurio, Struma and contributors).
- Android port this is based on: [thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android).
- SDL2, Ruby, OpenAL Soft, PhysicsFS and other dependencies, each under its own license.

License: GPL-2.0-or-later (same as mkxp-z). Pokémon is a trademark of Nintendo / Game Freak / The Pokémon Company;
this project is not affiliated with them.

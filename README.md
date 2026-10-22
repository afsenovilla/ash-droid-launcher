# Ash Droid Launcher

**English** · [Español](README.es.md)

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

## Building

GitHub Actions builds the APK on every push (`.github/workflows/build.yml`):

- `main` → rolling **dev** prerelease with `ash-droid-launcher-dev.apk`.
- tag `vX.Y.Z` → stable release.

Locally: NDK r25c, JDK 17, and the same steps as the workflow (`app/jni/get_deps.sh`, `make` in `app/jni`,
`make_xxd.sh` in `app/jni/mkxp-z`, `./gradlew assembleRelease`).

The APK is signed with a fixed public key (see [`keystore/README.md`](keystore/README.md)) so each version installs
over the previous one without losing data.

## Credits and license

- Engine: [mkxp-z](https://github.com/mkxp-z/mkxp-z) (Ancurio, Struma and contributors).
- Android port this is based on: [thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android).
- SDL2, Ruby, OpenAL Soft, PhysicsFS and other dependencies, each under its own license.

License: GPL-2.0-or-later (same as mkxp-z). Pokémon is a trademark of Nintendo / Game Freak / The Pokémon Company;
this project is not affiliated with them.

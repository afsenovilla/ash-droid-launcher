# Changelog

All notable changes to Ash Droid Launcher are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/) (0.x = early versions, anything may still change).

## [Unreleased]

### Added
- Spanish translation pack for Pokémon Fire Ash: ~2,270 lines the game's own Spanish translation leaves in
  English (NPC dialogue, menus, battle and bag messages), applied at runtime only when the game is set to Spanish.
- Spanish battle command buttons (LUCHAR / MOCHILA / HUIR…), redrawn on the device from the game's own art and
  font. Original files are untouched; the English images are used when the game is in English.
- Translation tooling (`tools/translation`) to extract missing strings from a new game version.

- Launcher "Diagnostics" screen with the runtime patch log (translation pack, language detection, image swaps).

### Changed
- Battle buttons lettered with a pixel font in the game's style (light letters, dark outline).
- Controls screen: single buttons (A, B, X, Y) are drawn as square key caps in the game's style, with a crisp
  pixel font, instead of the long USE/BACK-sized caps.

### Fixed
- Controls screen hint "(Por defecto: X)" now also shows the device button.

## [0.1.0] - 2026-10-22

First version that runs Pokémon Fire Ash on an Android handheld (tested on a Retroid Pocket Nova).

### Added
- Android build of mkxp-z (based on thehatkid/mkxp-z-android, Ruby 3.1, arm64) built by GitHub Actions.
- Launcher: import the game's original `.zip` into private storage (nothing from the game ships in the APK).
- Device detection: screen resolution, aspect ratio and refresh rate; built-in controller; small device database.
- Automatic display configuration (`mkxp.json`): pixel-perfect integer scaling when possible, otherwise sharp
  integer scaling plus a smooth final step. Display modes: Automatic, Sharp, Smooth, Stretch.
- Button setup: automatic mapping on known devices; wizard with a suggested button for each action on others.
- START opens the menu, SELECT uses the registered item.
- Auto-start: the game launches after a 3-second countdown (any button stays in the menu). Can be turned off.
- Big green Play button.
- On-screen touch controls hidden by default on devices with physical controls (toggle in the launcher).
- In-game controls screen: keyboard keys replaced by the device's buttons, both in the text and in the key icons
  (redrawn in the game's own style), and Spanish for the lines the game leaves untranslated.
- App in English, or Spanish when the system language is Spanish.

### Fixed
- `NameError: uninitialized constant PluginManager::Zlib` when starting Pokémon Essentials games.
- Controller face buttons not working in game (only the D-pad did).
- Shifted tile rows and seams on maps (fragment shader precision).
- Android status bar visible over the game.

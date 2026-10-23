# Changelog

All notable changes to Ash Droid Launcher are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/) (0.x = early versions, anything may still change).

## [Unreleased]

### Changed
- The Spain Spanish retranslation now replaces the game's Spanish: the language "Spanish" is renamed
  "Spanish (Spain)" instead of adding a separate "Español (España)". Saves that used "Español (España)"
  switch to it automatically.
- The Spanish intro is off by default and is now switched from inside the game (Options → "Música en
  castellano", only shown while the game is in Spanish). The launcher no longer has a music section.

## [0.3.1] - 2026-10-23

### Changed
- Battle: after switching Pokémon (or sending one out after a faint), the command menu opens on FIGHT
  instead of staying on the POKéMON button.

## [0.3.0] - 2026-10-22

### Added
- Each release now has three downloads: the Android APK, the Windows patch and the Steam Deck / Linux patch.
- Steam Deck (LCD and OLED): the patch detects the Deck (DMI model, or Steam's `SteamDeck=1`; override with
  `ASHDROID_DEVICE=pc|deck|lcd|oled`) and, as on Android, names the Deck's buttons instead of keyboard
  keys, redraws images with them (including the controls screen) and the ☰ button opens the menu like B.
- Steam Deck / Linux package: native Linux build of mkxp-z (no Proton needed) and "Instalar en Steam
  Deck.sh", which finds the game (also on the microSD) — extracted or still in the .zip as downloaded,
  which it extracts itself — installs the patch and adds the game to Steam with its artwork.

### Changed
- Controls screen: new D-pad in the style of the game's key caps — outline, raised edges, thickness,
  engraved arrows and a concave centre (Android too).
- PC patches: the Spanish lettering is redrawn by the patch itself from the player's copy of the game
  while it runs, so the downloads contain no game files.
- Only versioned releases are published (no more rolling "dev" build or build-tool prereleases).

### Fixed
- Windows patch: the Spanish images were not found (their index listed bare file names).

## [0.2.2] - 2026-10-22

### Added
- In-app updates: the launcher checks GitHub for a newer release and offers "Actualizar a vX"; it downloads
  and installs it over the current version (saves and settings are kept).

## [0.2.1] - 2026-10-22

### Added
- "Música en castellano" includes the Spanish intro theme out of the box; every track (bundled or added) can
  be switched on or off from the launcher.
- Español (España): the whole game retranslated and reviewed (maps, system/battle texts, trainers, places,
  phone, ribbons), with names unified against the official Spain games.
- Español (España) now also replaces game data texts: item, move and ability names and descriptions,
  trainer classes and Pokédex entries.

### Fixed
- Item, move and ability descriptions no longer get cut off in the shop, the Bag or the Summary.
- Wrong official names in the game's data (e.g. Caramelo Raro, Rapid Ball, Viraje, Fuerza Bruta).

## [0.2.0] - 2026-10-22

### Added
- "Música en castellano" in the launcher: add songs from the device (e.g. a Spanish Title.ogg); they play
  instead of the game's track with the same name while the game is in Spanish. TeamRocket1 always plays as
  TeamRocket.
- New game language "Español (España)" (added to Fire Ash's language list): a from-scratch Spain Spanish
  retranslation from the English text, with Spain terminology and the Spain anime dub (Team Rocket motto).
  Pilot so far: intro, Pallet Town, Oak's lab, Route 1, Viridian City and the core battle messages; the rest
  falls back to the game's Spanish. Trainer names follow Spanish order and verbs agree in plural ("Jessie y
  James del Team Rocket han sacado a Ekans").
- Title screen "PULSA A" and Spanish ability splash order ("Presión / de Ho-Oh").
- Spanish translation pack for Pokémon Fire Ash: ~2,270 lines the game's own Spanish translation leaves in
  English (NPC dialogue, menus, battle and bag messages), applied at runtime only when the game is set to Spanish.
- Spanish battle command buttons (LUCHAR / MOCHILA / HUIR…), redrawn on the device from the game's own art and
  font. Original files are untouched; the English images are used when the game is in English.
- Spanish lettering in the rest of Fire Ash's images with English text, redrawn on the device with pixel fonts
  taken from the game's own art: type labels (Emerald abbreviations: VOLAD., FANT., ELÉCT., PSÍQ., SINIE.),
  status icons (DOR, ENV, QUE, CON, DEB), "Lv." → "Nv." and "HP" → "PS" (battle, party, storage, summary),
  Pokédex "FORMAS" tab, double-battle "CAMBIO" button, trainer card title ("FICHA ENTREN."), naming screen
  (ABC / abc / Otros, ATRÁS), Voltorb Flip memo (Abrir/Cerrar Notas) and the slot machine
  (FICHA, PARA, GANA, NADA, FICHAS, PREMIO).
- Official Spanish art supplied by the user: put Emerald's type and status icons in `assets_E/` inside the game
  folder (e.g. `Tipo_lucha_E.png`, `Dormido_E.png`) and they are used instead of the redrawn ones.
  Nothing from the games is shipped with the app.
- Key names in game art follow the device buttons in any language: Mega Evolution / Z-Move / Ultra Burst /
  Dynamax buttons, the shift button, the naming screen key bar and Voltorb Flip.
- Recipe-based art engine (`assets/packs/<game>/art.json`, `assets/pixelfonts.txt`) with a PC preview tool
  (`tools/art/relabel.py`) to add more images without touching code.
- Translation tooling (`tools/translation`) to extract missing strings from a new game version.
- Launcher "Diagnostics" screen with the runtime patch log (translation pack, language detection, image swaps).

### Changed
- Launcher redesigned: big Play button and a console-style settings list (rows with values and toggles,
  D-pad focus bar); it opens instantly (config and image generation run in the background and are cached).
- The game always fills the screen (its window-size option is removed); PC-only notices are skipped and PC
  keys in tips (F/F7, Alt) name the device buttons; "Quit Game" shows Salir/Quit instead of Cancel;
  items get the right Spanish article instead of "un/a"; white text on black screens is readable.
- Battle buttons lettered with a pixel font in the game's style (light letters, dark outline).
- Controls screen: single buttons (A, B, X, Y) are drawn as square key caps in the game's style, with a crisp
  pixel font, instead of the long USE/BACK-sized caps.

### Fixed
- Battle buttons came out garbled on some devices: they now use the same recipe engine as the other images,
  with the buttons' own 5x9 lettering.
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

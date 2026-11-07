# AGENTS.md

Guidance for AI agents (and humans) working on this repository.

## Language policy: English for everything development-facing

From 2026-11-07, English is the single language of the project's development-facing side. When you add or change any of the following, write it in English:

- **Code:** identifiers, comments, docstrings, log messages, error messages, CLI help and argument descriptions.
- **Anything a player sees that is not the game's own text:** launcher UI (Android), installer windows and terminal output, pop-ups and dialogs (updater prompts, confirmations, errors), progress screens and status messages.
- **File and folder names** shipped in release packages or created on players' devices: installers, launch scripts, readme files, log files, marker and config files, payload folders inside the zips.
- **Documentation:** `README.md` (the canonical README), tool READMEs, `CHANGELOG.md`, release notes, code review notes, planning and design documents.
- **Git and GitHub:** commit messages, branch names, pull request titles and descriptions, issue text, workflow step names and CI output.

Don't add a Spanish (or any other non-English) variant of a development-facing string next to the English one. The launcher and tools are not localized; if localization is ever reintroduced, it will be a deliberate, multi-language decision, not a per-string choice.

### Out of scope: game translations

The game translations are separate and follow their own rules. Do **not** convert them to English:

- Translation packs and their sources: `packs/<game>/*.json`, `app/src/main/assets/packs/<game>/*.dat`.
- Image recipes and lettering in `art.json` / `pixelfonts.txt`, and the per-language intro songs.
- In-game text the runtime patch shows in the game's current language (e.g. per-language tables in `ashdroid_patches.rb` such as the controls-screen texts, the "Intro en español / en français / (Deutsch)" option and its Yes/No values, the device notices with a Spanish and an English variant, the splash credit line).
- Translation style guides for a target language (e.g. `tools/translation/GUIDE_es_ES.md`). Their instructions should still be written in English, but they will naturally contain target-language examples.

These keep using official Pokémon terminology for each language.

### Renaming shipped files: keep existing installs working

Changing the name of something already on players' devices needs a migration path. Before renaming, check what depends on the old name (Steam shortcut IDs and artwork, updater lookups, uninstallers, old marker files) and either keep a compatibility forwarder or migrate it in the installer. See `ENGLISH_STANDARDIZATION.md` for the known cases.

## Other project rules

- Every change adds an entry under `## [Unreleased]` in `CHANGELOG.md` (English).
- A release is cut by adding a `## [X.Y.Z]` section at the top of the changelog and pushing it to `main`; every release ships all platform packages together. No rolling "dev" builds.
- Don't push to `main` or start release builds without the maintainer's OK. Pushing commits to a working branch as a backup is fine; merging still waits for the go-ahead.
- Show visual changes (redrawn images, UI) to the maintainer before they're released.

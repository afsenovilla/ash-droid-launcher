# AGENTS.md

Guidance for AI agents (and humans) working on this repository.

## Language policy

**All development-facing repository content must be written in English, excluding localization resources.**

Developers may communicate with AI agents in their preferred language (for example, Spanish). The language of a request doesn't change the language of the result: development-facing content produced from it is written in English.

### Development-facing content (English)

- **Code:** identifiers, comments, docstrings, log messages, error messages, CLI help and argument descriptions.
- **Source strings:** the default (base) strings of any localized UI, e.g. the Android launcher's `app/src/main/res/values/strings.xml`.
- **Tool and installer text that isn't a localization resource:** Windows installer, Steam Deck / Linux installer and updater, H700 installer: their windows, pop-ups, dialogs, progress screens and terminal output.
- **File and folder names** shipped in release packages or created on players' devices: installers, launch scripts, readme files, log files, marker and config files, payload folders inside the zips.
- **Documentation:** `README.md` (the canonical README), tool READMEs, `CHANGELOG.md`, release notes, planning and design documents.
- **Git and GitHub:** commit messages, branch names, pull request titles and descriptions, issue text, workflow step names and CI output.

### Localization resources (excluded)

Localization resources hold player-facing text in other languages. They stay in their target language, and the features they support must keep working.

**Launcher localization.** The Android launcher's Spanish UI is a supported feature.
- English is the source language: `app/src/main/res/values/strings.xml`.
- Spanish is a localization: `app/src/main/res/values-es/strings.xml`. Android shows it when the device is in Spanish.
- Every new or changed launcher string goes into the source file and into every existing localization, with the same key. UI text is never hardcoded in Java.
- More languages can be added the same way (`values-fr`, `values-de`, `values-it`…).
- If a tool or installer is ever localized, it follows the same model: English source strings, with translations kept as separate localization resources.

**Game translations.** They are separate and follow their own rules:
- Translation packs and their sources: `packs/<game>/*.json`, `app/src/main/assets/packs/<game>/*.dat`.
- Image recipes and lettering in `art.json` / `pixelfonts.txt`, and the per-language intro songs.
- In-game text the runtime patch shows in the game's current language (e.g. the per-language tables in `ashdroid_patches.rb`: controls-screen texts, the "Intro en español / en français / (Deutsch)" option and its Yes/No values, the device notices with a Spanish and an English variant, the splash credit line).
- Translation style guides for a target language (e.g. `tools/translation/GUIDE_es_ES.md`). Their instructions are written in English, with target-language examples.

Game translations keep using official Pokémon terminology for each language.

## New development baseline: no legacy compatibility

Legacy compatibility is not currently a priority. This application has not yet received its official Fire Ash release, and we are establishing a new development baseline.

- Once the changes outlined in `ENGLISH_STANDARDIZATION.md` are approved and implemented, new installations follow the standardized structure.
- Previous beta implementations, legacy filenames, compatibility forwarders and migration requirements must not dictate the new standard.
- Preserve useful functionality, without requiring backward compatibility with experimental builds.

## Other project rules

- Every change adds an entry under `## [Unreleased]` in `CHANGELOG.md` (English).
- A release is cut by adding a `## [X.Y.Z]` section at the top of the changelog and pushing it to `main`. No rolling "dev" builds.
- Each supported platform (Android APK, Windows, Steam Deck/Linux, and H700) maintains an independent build and release package in its own designated folder, reusing shared core assets where applicable.
- Platform releases are independent. Updating or releasing one platform does not require rebuilding or releasing the others.
- Don't push to `main` or start release builds without the maintainer's OK. Pushing commits to a working branch as a backup is fine; merging still waits for the go-ahead.
- Show visual changes (redrawn images, UI) to the maintainer before they're released.

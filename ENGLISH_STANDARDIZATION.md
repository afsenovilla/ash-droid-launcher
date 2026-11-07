# English standardization: findings and proposed changes

Read-only inspection of the repository at v0.7.0 (`cb01c65`, 2026-11-07). Nothing outside `AGENTS.md` and this
file was changed, and no tests were run. The policy is in [`AGENTS.md`](AGENTS.md).

**Scope:** everything development-facing, plus everything a player sees that is not the game's own text
(launcher UI, installers, pop-ups, file names, logs, readmes).
**Out of scope:** game translations (translation packs, image recipes, intro songs, and in-game text the patch
shows in the game's current language).

## Summary

| Area | State | Work |
|---|---|---|
| Android launcher (Java, resources) | Code and comments in English. UI shipped in English **and Spanish** (`values-es`) | Remove the Spanish UI. One class rename |
| Runtime patch (`ashdroid_patches.rb`, `ashdroid_art.rb`) | Code, comments and logs in English. Spanish strings found are in-game text (out of scope) | None |
| Windows patch (`tools/pc/`) | Installer UI, file names and readme in **Spanish** | Translate, rename |
| Steam Deck / Linux (`tools/linux/`) | Installer, updater pop-ups, launcher name and readme in **Spanish** | Translate, rename with a migration |
| H700 handhelds (`tools/handheld/`) | Installer screen, messages, comments, file names, log and readme in **Spanish** | Translate, rename with a migration |
| CI and build scripts | English, except for Spanish file names they reference | Follow the renames |
| Docs | `README.md`, `CHANGELOG.md`, tool docs in English. `README.es/fr/de.md` translations | Update names. Decide on translated READMEs |

The heavy part is the three PC/handheld packages. The Android app only needs its Spanish resources removed.

---

## 1. Android launcher

### 1.1 Spanish UI (`app/src/main/res/values-es/strings.xml`)
- 175 strings, a full Spanish copy of the 179 in `values/strings.xml`. Android picks it automatically when the
  system language is Spanish, so every launcher screen, dialog and error has a Spanish version. That includes the
  update, signing-key and unverified-zip dialogs.
- **Proposal:** delete `values-es/`. The launcher is then English on every device. Update the CHANGELOG and
  README lines that say "App in English, or Spanish when the system language is Spanish".
- **Decision for the maintainer:** this removes the only non-English launcher UI. If launcher localization is
  wanted later, it should come back as a deliberate multi-language feature (es, fr, de, it), not Spanish only.

### 1.2 Naming
- `launcher/SpanishMusic.java` handles the bundled intro songs for **es, fr and de**, not just Spanish. Proposal:
  rename it to `IntroMusic` (one caller: `ConfigWriter.java:131`).
- Its class comment says the in-game intro option is "off by default", but the CHANGELOG (0.4.0) says the intro
  plays by default. One of them is wrong: check it when renaming.

### 1.3 Already compliant
- Java code, comments and log text are in English. The Spanish-looking literals found are character tables
  (`ArtEngine.ACC_FROM`) and language codes (`"es"`, `"fr"`…) used to choose translated images: they're data,
  not text.
- `ConfigWriter.java:187` passes the device language to the patch as a fallback for the game's language. That's
  game-language logic and should stay.

## 2. Runtime patch (`app/src/main/assets/`)

- Module names, comments, `AshDroidLog` messages and errors are in English.
- The Spanish strings found are all in-game text shown in the game's current language, which is out of scope:
  - controls-screen texts (`AshDroidText`, l. 118–143) and the "Predeterminada → Botón" table (l. 120–122);
  - device notices with a Spanish and an English variant (l. 374–393);
  - the splash credit line "Parche Ash Droid: Alberto (afsenovilla)" (l. 712);
  - the "Intro en español / en français / (Deutsch)" option and its No/Sí values (l. 1295–1297);
  - the accent tables in `ashdroid_art.rb`.
- **Proposal:** no changes. `AGENTS.md` lists these as out of scope so they aren't converted by mistake.

## 3. Windows patch (`tools/pc/`)

### 3.1 Installer UI (`tools/pc/installer/Installer.cs`, WinForms)
All visible text is Spanish: window title "Fire Ash · Parche Ash Droid", subtitle, description, the
"Instalar / Actualizar / Examinar…" buttons, the folder picker prompt, status messages, and the success and
uninstall message boxes (l. 44–219). The assembly product name is "Ash Droid Launcher - parche PC" (l. 14).
- **Proposal:** translate every string. Example: "Fire Ash · Ash Droid Patch", "Install / Update / Browse…",
  "Choose the Pokémon Fire Ash folder (the one with Game.exe and mkxp.json)", "Patch installed!".
- **Stale content to fix at the same time:** the description and hint list only Spanish, French and German.
  Italian is missing.

### 3.2 Shipped file names
| Now | Proposed | Referenced in |
|---|---|---|
| `Instalar parche.exe` | `Install patch.exe` | `pc-installer.yml` (l. 1, 25), `build.yml` (l. 244, 268, 310), `build_pc_patch.sh` (l. 3, 30), READMEs |
| `parche/` (payload folder next to the .exe) | `patch/` | `Installer.cs` (l. 3, 35, 138), `build_pc_patch.sh` (l. 22) |
| `LEEME.txt` | `README.txt` | `build_pc_patch.sh` (l. 31) |
| Zip top folder `Fire Ash - Parche Ash Droid - Windows` | `Fire Ash - Ash Droid Patch - Windows` | `build_pc_patch.sh` (l. 17) |

- **Migration:** none needed. Windows has no updater, and the files installed in the game folder (`ashdroid_*`)
  keep their names.

### 3.3 `tools/pc/LEEME.txt`
Spanish-only readme. Proposal: rewrite it in English as `README.txt` and bring it up to date (Italian, current
version).

## 4. Steam Deck / Linux (`tools/linux/`)

### 4.1 Installer (`install.py`, 857 lines)
- 39 Spanish string literals: zenity/kdialog dialogs, terminal prompts, errors and the final "¡Listo!" message
  (l. 111–843). Examples: "He encontrado el juego en: … ¿Uso ese?", "Para añadir el juego a Steam hay que cerrar
  Steam un momento…", "Parche desinstalado. El juego vuelve a estar como antes."
- Spanish module docstring and option help (l. 2–12). Terminal yes/no prompt is `[S/n]` and accepts "s/si/sí"
  (l. 79).
- **Proposal:** translate every string. Prompt `[Y/n]`, still accepting "s/si/sí" for anyone typing out of habit.

### 4.2 Updater pop-ups (`update.py`)
- 12 Spanish strings, shown every time an update is found: the prompt "Hay una versión nueva del parche…
  ¿Actualizar ahora?", the "Actualizar / Ahora no" buttons, "Descargando la actualización…", and the success
  and failure messages (l. 48–205). Spanish docstring (l. 2–11).
- **Proposal:** translate them ("A new version of the patch is available: %s (you have %s). Update now? It takes
  a moment; your saves aren't touched.", buttons "Update / Not now").
- **Note:** players already on 0.7.0 or earlier will see the old Spanish prompt one more time, because the copy
  of `update.py` on their device is the one that asks. The new English updater arrives with that update.

### 4.3 Shipped file names
| Now | Proposed | Referenced in |
|---|---|---|
| `Instalar en Steam Deck.sh` | `Install on Steam Deck.sh` | `build_pc_patch.sh` (l. 41–42), `build.yml` (l. 245, 311), `install.py` (l. 4), READMEs |
| `parche/` (payload) | `patch/` | `install.py` (l. 37, 746), `build_pc_patch.sh` (l. 22) |
| `LEEME.txt` | `README.txt` | `build_pc_patch.sh` (l. 41) |
| Zip top folder `Fire Ash - Parche Ash Droid - Steam Deck` | `Fire Ash - Ash Droid Patch - Steam Deck` | `build_pc_patch.sh` (l. 18) |
| `Jugar (Linux).sh` (created in the game folder) | `Play (Linux).sh` | `install.py` (l. 7, 34, 392, 420, 841) |

Keep `steamdeck/` and `install.py`: the updater already on players' devices finds the new installer by those
names (`update.py` l. 193–194). Its command-line flags (`--game --yes --no-steam`) must not change either.

### 4.4 Migration risk: the Steam shortcut (important)
- The Steam shortcut's app ID is computed from the launcher's path (`shortcut_appid(exe, APP_NAME)`, l. 487, 676,
  685). Updates run with `--no-steam` and never rewrite `shortcuts.vdf` (l. 828–829).
- If `Jugar (Linux).sh` is simply renamed, every existing Steam shortcut keeps pointing at the old file, and the
  library artwork stops matching.
- **Proposal (pick one):**
  1. **Keep a forwarder:** the installer still writes `Jugar (Linux).sh` as a two-line script that runs
     `Play (Linux).sh`, for existing installs only. New installs create only `Play (Linux).sh`. Lowest risk.
  2. **Migrate the shortcut:** on update, rewrite the existing entry in `shortcuts.vdf` to the new path and
     re-copy the artwork under the new app ID. That needs Steam closed, which updates currently avoid on purpose.
  3. **Keep the old name:** leave `Jugar (Linux).sh` as the one exception to the policy.

### 4.5 Other files
- `Instalar en Steam Deck.sh`: Spanish comments (l. 2–3).
- `tools/linux/LEEME.txt`: Spanish-only readme. Proposal: rewrite it in English as `README.txt`.
- Logs and markers are already English: `ashdroid_update.log`, `ashdroid_update_skip.txt`,
  `ashdroid_pc_version.txt`.

## 5. H700 handhelds (`tools/handheld/`)

### 5.1 Installer (`Fire Ash - Instalar.sh`, 366 lines)
- The on-screen dashboard and log are Spanish:
  - header "Parche Ash Droid … - Instalar/Desinstalar";
  - steps "Buscando el juego", "Descomprimiendo el juego", "Instalando el parche", "Instalando el motor",
    "Quitando el parche y el motor";
  - errors such as "No encuentro el juego.", "No he podido instalar el motor.", "Comprueba que queda espacio en
    la tarjeta.";
  - final screens "TODO LISTO", "DESINSTALADO", "NADA QUE QUITAR", and instructions such as
    "START > Opciones de juego > Actualizar listas" (l. 27–363).
- Spanish comments throughout, including the comments of the generated `Fire Ash.sh` launcher (l. 305–307).
- **Proposal:** translate every message and comment. The Knulli menu path needs the English menu names
  (START > Game settings > Update game lists), as the README already gives them.

### 5.2 Shipped and created file names
| Now | Proposed | Referenced in |
|---|---|---|
| `Fire Ash - Instalar.sh` | `Fire Ash - Install.sh` | `build_pc_patch.sh` (l. 34–35), `Fire Ash - Desinstalar.sh` (l. 4), `build.yml` (l. 246), READMEs |
| `Fire Ash - Desinstalar.sh` | `Fire Ash - Uninstall.sh` | `build_pc_patch.sh` (l. 34–35), READMEs |
| `FireAsh-Patch/parche/` (payload) | `FireAsh-Patch/patch/` | installer (l. 21, 32, 252, 254), `build_pc_patch.sh` (l. 22) |
| `LEEME.txt` | `README.txt` | `build_pc_patch.sh` (l. 35) |
| `Fire Ash - instalacion.log` | `Fire Ash - install.log` | installer (l. 14, 137), READMEs |

`Fire Ash.sh`, `ports/FireAsh/`, `ashdroid_run.log`, `ashdroid_handheld.txt`, `ashdroid_env.sh` and
`ashdroid_restart` are already English or neutral.

### 5.3 Migration
- Players update by copying the new zip's contents into `ports/`. The old Spanish-named files stay next to the
  new ones: two install entries, two uninstall entries, two readmes and a stale `FireAsh-Patch/parche/`.
- **Proposal:** the new installer deletes `Fire Ash - Instalar.sh`, `Fire Ash - Desinstalar.sh`, `LEEME.txt`,
  `FireAsh-Patch/parche/` and `Fire Ash - instalacion.log` from `ports/` when it finds them. The new uninstaller
  removes both old and new names.
- `tools/handheld/LEEME.txt`: Spanish-only readme. Proposal: rewrite it in English as `README.txt`.

## 6. CI and build scripts

- `.github/workflows/build.yml`, `pc-installer.yml`, `ci/*.sh`, `tools/pc/build_pc_patch.sh`: comments, step
  names and release notes are in English. They only need to follow the file renames above.
- `build.yml` l. 339–353 renames the old `FireAsh-Espanol-Espana-*` assets of 0.4.x releases. Those names are
  historical data, not text: keep them.
- `build_pc_patch.sh` l. 9 says "images with Spanish lettering": now es/fr/de/it. Fix the comment.
- `ci/release-notes.sh` still has a `dev` mode for rolling builds, which the project no longer makes. Unrelated
  to language; it can be removed in the same clean-up.

## 7. Documentation

- `README.md`: English. It names the Spanish files (`Instalar parche.exe`, `Instalar en Steam Deck.sh`,
  `Fire Ash - Instalar.sh`, `Fire Ash - Desinstalar.sh`, `LEEME.txt`, `Fire Ash - instalacion.log`; l. 58–93).
  Update it with the renames. It's also behind the code (Italian, private signing key, no dev builds, H700 tested).
- `README.es.md`, `README.fr.md`, `README.de.md`: player-facing translations of the README, all behind
  `README.md`. **Decision for the maintainer:**
  - (a) keep them, marked as translations of the canonical English README and updated after it;
  - (b) remove them and keep a single English README; or
  - (c) move them to `docs/`.
- `CHANGELOG.md`: English. Quoted Spanish UI strings in old entries are history: leave them.
- `tools/translation/GUIDE_es_ES.md`: style guide for the Spanish translation. Instructions are in English, with a
  Spanish title and examples. It belongs to the game translations, so it's out of scope (rename the title if
  wanted).
- `keystore/README.md`, `tools/translation/README.md`, `tools/translation/GUIDE.md`: English.

## 8. Other findings

- `ashdroid_patch.log` is committed at the repository root. It looks like a runtime log added by mistake.
  Proposal: remove it and add it to `.gitignore`.

---

## Proposed order of work

1. **Android:** remove `values-es/`, rename `SpanishMusic` → `IntroMusic`, fix the intro default comment. Low risk.
2. **Windows:** translate `Installer.cs`, rename the .exe, payload folder and readme, update CI. No migration.
3. **H700:** translate the installer, rename its files, add the clean-up of old names to the installer and
   uninstaller.
4. **Steam Deck:** translate `install.py` and `update.py`, rename the files, and handle `Jugar (Linux).sh` with
   the option chosen in 4.4.
5. **Docs:** update `README.md` (and the translated READMEs per the decision in section 7), and remove
   `ashdroid_patch.log`.
6. **CHANGELOG:** one entry explaining the renames and that tools and launcher are now English-only.

Steps 2–4 change what players download, so they should go out together in one release, with test installs on a
Steam Deck and an H700 console (and a Windows PC, still untested).

### Decisions needed
1. Android: remove the Spanish launcher UI (section 1.1).
2. Steam Deck: forwarder, shortcut migration or keeping the old launcher name (section 4.4).
3. Translated READMEs: keep, remove or move (section 7).

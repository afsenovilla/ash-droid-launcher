# Ash Droid Launcher

[English](README.md) · [Español](README.es.md) · [Français](README.fr.md) · **Deutsch**

Android-Launcher für **Pokémon Fire Ash** (und im Prinzip andere Pokémon-Essentials-Fangames, die bereits mit
[mkxp-z](https://github.com/mkxp-z/mkxp-z) laufen), gemacht für **Android-Handhelds mit echten Tasten**
(Retroid, AYN, Anbernic…).

**Dieses Repository enthält und verbreitet kein Spiel.** Du brauchst die originale `.zip` des Spiels aus der
offiziellen Quelle (bei Fire Ash dessen [Discord-Server](https://discord.com/invite/7EvBgnrpcU)).

## So funktioniert's

1. Installiere die APK (unter [Releases](https://github.com/afsenovilla/ash-droid-launcher/releases)).
2. Kopiere die `.zip` des Spiels aufs Gerät und tippe auf **Spiel importieren**. Sie wird in den privaten Speicher
   der App entpackt.
3. Die App **erkennt dein Gerät**:
   - **Bildschirm**: Auflösung, Seitenverhältnis und Bildwiederholrate. Sie wählt die beste Skalierung: pixelgenau,
     wenn der Faktor ganzzahlig ist; sonst (z. B. ×2,5 beim Retroid Pocket Nova) ein scharfes ×2 plus ein kurzer,
     weicher letzter Schritt.
   - **Eingebauter Controller**: Name und Hersteller, abgeglichen mit einer kleinen [Gerätedatenbank](app/src/main/assets/devices.json).
4. **Tasten-Assistent**: Drücke für jede Aktion die gewünschte Taste (Bestätigen, Abbrechen, Rennen, Beschleunigen…).
   Funktioniert auf jedem Gerät, egal ob A/B nach Nintendo- oder Xbox-Art belegt ist.
5. Er schreibt eine passende `mkxp.json` (das Original bleibt als `mkxp.original.json` erhalten) und startet das Spiel.

Spielstände bleiben erhalten, wenn du die App aktualisierst oder eine neue Version des Spiels importierst.

## Anzeigemodi

| Modus | Wirkung |
|---|---|
| Automatisch | Exakt ganzzahlig, wenn möglich; sonst scharf ganzzahlig + weicher letzter Schritt |
| Scharf | Nur ganzzahlige Skalierung (kann Ränder lassen) |
| Weich | Gefilterte Skalierung auf volle Höhe |
| Strecken | Füllt den ganzen Bildschirm, auch wenn es verzerrt |

## Dein Gerät hinzufügen

Bearbeite [`devices.json`](app/src/main/assets/devices.json) und öffne einen PR. Der Bildschirm wird immer live erkannt
und die Tasten immer im Assistenten bestätigt; die Datenbank liefert nur einen Namen und Hinweise.

## Übersetzungen

Der Launcher wendet zur Laufzeit einen Patch auf das Spiel an (`ashdroid_patches.rb`, keine Spieldatei wird
verändert), mit vollständigen Neuübersetzungen aus dem Englischen im Stil der offiziellen Spiele:

| Sprache | Im Spiel | Inhalt |
|---|---|---|
| Spanisch (Spanien) | ersetzt „Spanish“ als **Spanish (Spain)** | Dialoge, Menüs, Kämpfe, Items, Attacken, Fähigkeiten, Pokédex, offizielle Namen und Begriffe der spanischen Synchronisation, Bilder mit Text auf Spanisch neu gezeichnet, Intro-Song auf Spanisch |
| Französisch | French | Dialoge, Menüs, Kämpfe, Beschreibungen und korrigierte offizielle Namen, Intro-Song auf Französisch |
| Deutsch | German | Dialoge, Menüs, Kämpfe, Beschreibungen und offizielle Namen (in den deutschen Spieldaten waren viele Namen dem falschen Pokémon, der falschen Attacke, dem falschen Item oder Trainer zugeordnet), Intro-Song auf Deutsch |
| Englisch | English | nur Korrekturen: Beschreibungen, die nicht in ihr Feld passten |

Jede Beschreibung wird mit der Schrift des Spiels vermessen, damit sie in ihr Feld passt. Der Intro-Song läuft in der
Sprache des Spiels und lässt sich in den Optionen des Spiels abschalten.

## PC und Steam Deck

Derselbe Patch (mit allen Sprachen oben) existiert für die Windows-Version des Spiels (`tools/pc/`): eine zip mit
grafischem Installer (`Instalar parche.exe`). Für das **Steam Deck** (LCD und OLED) und andere Linux-Rechner gibt es
eine eigene zip mit einer nativen Linux-Version von mkxp-z, Proton ist also nicht nötig: `Instalar en Steam Deck.sh`
(Desktop-Modus) findet das Spiel, installiert den Patch und fügt es zu Steam hinzu. Der Patch erkennt das Deck und
zeigt dessen Tasten (A, B, X, Y, L1, R1, R3…) in Texten und Bildern; ☰ öffnet das Menü.

Updates: Die Android-App sucht nach neuen Versionen und installiert sie darüber; auf dem Steam Deck prüft das Spiel
beim Start und bietet an, den Patch zu aktualisieren. Unter Windows lädst du die neue zip herunter und startest den
Installer erneut.

## To-do

- Bilder mit Text (Titel, Menüs, Orden…) auf Französisch und Deutsch neu zeichnen, wie schon auf Spanisch.
- Durchsicht der französischen und deutschen Übersetzung durch Muttersprachler (einige Nebenfiguren haben keinen
  bestätigten offiziellen Namen).
- Automatische Updates für den Windows-Patch.

## Bauen

GitHub Actions baut die APK bei jedem Push (`.github/workflows/build.yml`):

- `main` → fortlaufende **Dev**-Vorabversion mit `ash-droid-launcher-dev.apk`.
- Tag `vX.Y.Z` → stabile Version.

Lokal: NDK r25c, JDK 17 und dieselben Schritte wie im Workflow (`app/jni/get_deps.sh`, `make` in `app/jni`,
`make_xxd.sh` in `app/jni/mkxp-z`, `./gradlew assembleRelease`).

Die APK ist mit einem festen öffentlichen Schlüssel signiert (siehe [`keystore/README.md`](keystore/README.md)), damit
sich jede Version ohne Datenverlust über die vorherige installieren lässt.

## Versionen und Releases

Die Versionen folgen [Semantic Versioning](https://semver.org/); Änderungen stehen in [CHANGELOG.md](CHANGELOG.md).
Jedes Release (Tag `vX.Y.Z`) hat drei Downloads:

| Plattform | Datei |
|---|---|
| Android | `ash-droid-launcher-X.Y.Z.apk` |
| Windows | `FireAsh-Patch-Windows-X.Y.Z.zip` |
| Steam Deck / Linux | `FireAsh-Patch-SteamDeck-Linux-X.Y.Z.zip` |

Zum Veröffentlichen: Füge oben im Changelog einen Abschnitt `## [X.Y.Z]` hinzu und pushe ihn nach `main`; der
Workflow setzt den Tag `vX.Y.Z` und veröffentlicht das Release. Jeder andere Push nach `main` wird nur gebaut, nie
veröffentlicht.

## Credits und Lizenz

- Engine: [mkxp-z](https://github.com/mkxp-z/mkxp-z) (Ancurio, Struma und Mitwirkende).
- Android-Port als Basis: [thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android).
- SDL2, Ruby, OpenAL Soft, PhysicsFS und weitere Abhängigkeiten, jeweils unter eigener Lizenz.

Lizenz: GPL-2.0-or-later (wie mkxp-z). Pokémon ist eine Marke von Nintendo / Game Freak / The Pokémon Company;
dieses Projekt steht in keiner Verbindung zu ihnen.

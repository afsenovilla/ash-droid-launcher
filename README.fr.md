# Ash Droid Launcher

[English](README.md) · [Español](README.es.md) · **Français** · [Deutsch](README.de.md)

Lanceur Android pour **Pokémon Fire Ash** (et, en principe, d'autres fangames Pokémon Essentials qui tournent déjà
sur [mkxp-z](https://github.com/mkxp-z/mkxp-z)), conçu pour les **consoles portables Android à boutons physiques**
(Retroid, AYN, Anbernic…).

**Ce dépôt ne contient ni ne distribue aucun jeu.** Il vous faut le `.zip` original du jeu, téléchargé depuis sa
source officielle (pour Fire Ash, son [serveur Discord](https://discord.com/invite/7EvBgnrpcU)).

## Fonctionnement

1. Installez l'APK (onglet [Releases](https://github.com/afsenovilla/ash-droid-launcher/releases)).
2. Copiez le `.zip` du jeu sur la console et touchez **Importer un jeu**. Il est extrait dans le stockage privé de l'app.
3. L'app **détecte votre appareil** :
   - **Écran** : résolution, format et fréquence. Elle choisit la meilleure mise à l'échelle : pixels parfaits si le
     facteur est entier ; sinon (p. ex. ×2,5 sur la Retroid Pocket Nova), un ×2 net plus une courte étape finale lissée.
   - **Manette intégrée** : nom et fabricant, comparés à une petite [base de données d'appareils](app/src/main/assets/devices.json).
4. **Assistant de boutons** : appuyez sur le bouton voulu pour chaque action (Valider, Annuler, Courir, Accélérer…).
   Fonctionne sur tout appareil, quelle que soit la disposition A/B (style Nintendo ou Xbox).
5. Il écrit un `mkxp.json` adapté à l'appareil (l'original est conservé dans `mkxp.original.json`) et lance le jeu.

Les sauvegardes sont conservées quand vous mettez à jour l'app ou réimportez une nouvelle version du jeu.

## Modes d'affichage

| Mode | Effet |
|---|---|
| Automatique | Échelle entière exacte si possible ; sinon échelle entière nette + étape finale lissée |
| Net | Échelle entière uniquement (peut laisser des bordures) |
| Lisse | Mise à l'échelle filtrée sur toute la hauteur |
| Étirer | Remplit tout l'écran, quitte à déformer |

## Ajouter votre appareil

Modifiez [`devices.json`](app/src/main/assets/devices.json) et ouvrez une PR. L'écran est toujours détecté en direct et
les boutons toujours confirmés dans l'assistant : la base ne fournit qu'un nom et des indications.

## Traductions

Le lanceur applique au jeu un patch à l'exécution (`ashdroid_patches.rb`, aucun fichier du jeu n'est modifié) avec des
retraductions complètes, écrites depuis l'anglais dans le style des jeux officiels :

| Langue | Dans le jeu | Contenu |
|---|---|---|
| Espagnol (Espagne) | remplace « Spanish » sous le nom **Spanish (Spain)** | dialogues, menus, combats, objets, capacités, talents, Pokédex, noms officiels et termes du doublage espagnol, images avec texte redessinées en espagnol, chanson d'intro en espagnol |
| Français | French | dialogues, menus, combats, descriptions et noms officiels corrigés, chanson d'intro en français |
| Allemand | German | dialogues, menus, combats, descriptions et noms officiels (les données allemandes du jeu attribuaient de nombreux noms au mauvais Pokémon, capacité, objet ou Dresseur), chanson d'intro en allemand |
| Anglais | English | corrections uniquement : descriptions qui dépassaient de leur cadre |

Chaque description est mesurée avec la police du jeu pour tenir dans son cadre. La chanson d'intro est jouée dans la
langue du jeu et peut être désactivée dans les Options du jeu.

## PC et Steam Deck

Le même patch (avec toutes les langues ci-dessus) existe pour la version Windows du jeu (`tools/pc/`) : un zip avec un
installateur graphique (`Instalar parche.exe`). Pour le **Steam Deck** (LCD et OLED) et les autres Linux, un zip
séparé contient une version Linux native de mkxp-z, sans besoin de Proton : `Instalar en Steam Deck.sh` (mode bureau)
trouve le jeu, installe le patch et l'ajoute à Steam. Le patch détecte le Deck et affiche ses boutons (A, B, X, Y,
L1, R1, R3…) dans les textes et les images ; ☰ ouvre le menu.

Mises à jour : l'app Android cherche les nouvelles versions et les installe par-dessus ; sur le Steam Deck, le jeu
vérifie au démarrage et propose de mettre à jour le patch. Sous Windows, téléchargez le nouveau zip et relancez
l'installateur.

## À faire

- Images avec texte (titres, menus, badges…) redessinées en français et en allemand, comme c'est déjà fait en espagnol.
- Relecture des traductions française et allemande par des locuteurs natifs (certains personnages secondaires n'ont
  pas de nom officiel confirmé).
- Mises à jour automatiques du patch Windows.

## Compilation

GitHub Actions compile l'APK à chaque push (`.github/workflows/build.yml`) :

- `main` → préversion **dev** continue avec `ash-droid-launcher-dev.apk`.
- tag `vX.Y.Z` → version stable.

En local : NDK r25c, JDK 17 et les mêmes étapes que le workflow (`app/jni/get_deps.sh`, `make` dans `app/jni`,
`make_xxd.sh` dans `app/jni/mkxp-z`, `./gradlew assembleRelease`).

L'APK est signé avec une clé publique fixe (voir [`keystore/README.md`](keystore/README.md)) pour que chaque version
s'installe par-dessus la précédente sans perte de données.

## Versions et publications

Les versions suivent [Semantic Versioning](https://semver.org/) ; les changements sont listés dans
[CHANGELOG.md](CHANGELOG.md). Chaque version (tag `vX.Y.Z`) propose quatre téléchargements :

| Plateforme | Fichier |
|---|---|
| Android | `ash-droid-launcher-X.Y.Z.apk` |
| Windows | `FireAsh-Patch-Windows-X.Y.Z.zip` |
| Steam Deck / Linux | `FireAsh-Patch-SteamDeck-Linux-X.Y.Z.zip` |
| Consoles Anbernic H700 (RG34XX, RG35XX…, Knulli) | `FireAsh-Patch-Handheld-H700-X.Y.Z.zip` |

Pour publier : ajoutez une section `## [X.Y.Z]` en haut du changelog et poussez-la sur `main` ; le workflow crée le
tag `vX.Y.Z` et publie la version. Tout autre push sur `main` est seulement compilé, jamais publié.

## Crédits et licence

- Moteur : [mkxp-z](https://github.com/mkxp-z/mkxp-z) (Ancurio, Struma et contributeurs).
- Portage Android de base : [thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android).
- SDL2, Ruby, OpenAL Soft, PhysicsFS et les autres dépendances, chacune sous sa propre licence.

Licence : GPL-2.0-or-later (comme mkxp-z). Pokémon est une marque de Nintendo / Game Freak / The Pokémon Company ;
ce projet n'y est pas affilié.

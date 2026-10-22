# Translation tooling

Fills the gaps of a game's own Spanish translation. Output: `packs/<id>/es.json`
(English source → Spanish), compiled into `app/src/main/assets/packs/<id>/ashdroid_es.dat`
by `build_pack.rb` (also run by CI). At runtime `ashdroid_patches.rb` swaps in the Spanish text
when the game language is Spanish. The game's files are never modified.

## Updating for a new game version

Run from this folder with Ruby 3 and the game unpacked somewhere:

```sh
export GAME_DIR="/path/to/Pokemon Fire Ash"
ruby extract.rb     # untranslated map/event and data strings  -> ui_missing.json, map_missing.json
ruby scripts.rb     # _INTL strings found in Scripts.rxdata + PluginScripts.rxdata -> script_strings.json
ruby ui2.rb         # which script strings have no Spanish yet -> ui_missing2.json
ruby chunks.rb      # split everything not yet translated into chunks/*.json
```

Translate each `chunks/X.json` into `out/X.es.json` following `GUIDE.md`, check with
`python3 validate.py chunks/X.json out/X.es.json`, merge the results into `packs/<id>/es.json`
and run `ruby build_pack.rb`.

Known gap: choice lists inside `\ch[...]` codes are kept in English for now.

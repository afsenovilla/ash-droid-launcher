# Translating Pokémon Fire Ash strings to Spanish (Spain)

Source: English strings the game's own Spanish translation is missing. Output is loaded at runtime by a
launcher and shown INSTEAD of the English text when the game language is Spanish.

## Output
A JSON object `{ "<exact English source string>": "<Spanish translation>", ... }` with EVERY string of the
chunk as a key, byte-for-byte identical to the source (copy keys exactly, including backslashes, spacing,
punctuation). Write it with Python `json.dump(obj, f, ensure_ascii=False, indent=1)` to avoid escaping mistakes.

## Control codes — keep EXACTLY (same codes, same count), only move them if Spanish word order requires
`\PN` player name · `\n` line break · `\r` `\b` `\l` speaker colour codes at the start · `\c[1]` colours ·
`\wt[5]` `\wtnp[..]` waits · `\m` · `\se[...]` `\me[...]` sounds · `\ts[]` · `\G` money window · `\ch[...]` choices ·
`{1}` `{2}` placeholders · `<icon=...>` `<ac>` `<b>` tags · `\v[1]` variables · `\\` etc.
Run `python3 validate.py chunks/X.json out/X.es.json` — it must print OK.

## Style
- Spanish from Spain, natural and concise, like the official Spanish Pokémon games (tú, not usted, unless the
  character is clearly formal). Keep the tone of each character (kids, Team Rocket, Professor Oak…).
- Official Spanish terminology: Pokémon, Poké Ball(s), Entrenador/a, Centro Pokémon, Tienda Pokémon, Pokédex,
  Gimnasio, Líder de Gimnasio, Medalla, Liga Pokémon, Alto Mando, Campeón, MO/MT (HM/TM), PS (HP), Nv. (Lv.),
  ataque/movimiento, Equipo Rocket, Profesor Oak, Bolsillo (bag pocket), Mochila (Bag), PC, Caja (PC box),
  huevo → Huevo, shiny → variocolor, Mega Evolución, Dinamax/Gigamax, movimiento Z, Frente de Batalla.
- Item, move, ability, type, trainer-class and place names: use `glossary.json` (keys English → Spanish used by
  the game). Look them up with grep, e.g. `grep -i '"Potion"' glossary.json`. The section
  `existing_ui_translations` shows how the game already translates its interface — follow its wording.
- Pokémon species names and character names stay in English (Ash, Misty, Brock, Gary, Pikachu…), as in the
  official games. "Ash" stays "Ash".
- ALL-CAPS source → ALL-CAPS Spanish (with ¡! ¿?). Keep spacing quirks only if they are formatting.
- Very long lines: Spanish can be ~20% longer; the game wraps text automatically, so don't add `\n` unless the
  source has one in a similar place.
- If a string is not really text (a file name, a code, a debug-only line), copy it unchanged.

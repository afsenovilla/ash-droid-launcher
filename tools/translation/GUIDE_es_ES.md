# Pokémon Fire Ash → Español de España (retraducción completa)

Pokémon Fire Ash is a fan game that retells the anime (Ash, Pikachu, Misty, Brock, Team Rocket…). Its own
Spanish translation reads like a word-for-word machine translation. We are writing a NEW Spanish (Spain)
translation from the English source. It must read like an official Nintendo España localization and like
the Spanish (Spain) dub of the anime: natural, idiomatic, concise, never literal.

## Input / output
- `chunks/X.json`: list of English source strings, in game order (map by map).
- `chunks/X.ref.json`: for each source string, the map it belongs to and `es_old` = the OLD Spanish
  translation. `es_old` is only context (and sometimes useful for official names); it is often wrong or
  stilted — never copy it blindly. Translate from the English.
- Write `out/X.json`: a JSON object `{ "<English source, byte-for-byte>": "<Spanish>" }` covering EVERY
  source string. Write it with Python `json.dump(obj, f, ensure_ascii=False, indent=1)`.
- Check with `python3 validate.py chunks/X.json out/X.json` until it prints OK.

## Control codes — keep EXACTLY (same codes, same count); move them only if word order requires
`\PN` player name · `\n` line break · `\r` `\b` `\l` (speaker colour at the start: red/blue/…) · `\c[1]` ·
`\wt[5]` `\wtnp[..]` · `\m` · `\se[...]` `\me[...]` · `\ts[]` · `\G` · `\ch[...]` · `{1}` `{2}` · `<icon=…>`
`<ac>` `<b>` · `\v[1]` · `\\`. Lines ending in a code like `\wtnp[30]` must still end in it.

## Register and voice
- Spain Spanish: `tú` for one person, `vosotros` for several ("¿Estáis listos?", "Rendíos"). Never
  Latin-American forms (ustedes as informal plural, "ahorita", "carro", "computadora", "lindo", "chico" as
  "small"…). Natural Spain colloquialisms are welcome where the character would use them ("¡Vaya!",
  "¡Venga!", "vale", "majo", "¡Anda!", "¡Ni hablar!").
- Keep each character's voice: Ash impulsive and enthusiastic; Misty bossy/hot-tempered; Brock warm,
  girl-crazy; Professor Oak kind and a bit absent-minded; Gary cocky ("Ashy-boy" → keep the mocking tone,
  e.g. "Ashito" is fine or keep "Ashy-boy" if it's a catchphrase); Team Rocket theatrical.
- Prefer short, punchy lines like the games. Don't explain, don't add content, don't drop content.
- Exclamations/questions: ¡…! ¿…? always. ALL-CAPS source → ALL-CAPS Spanish.
- Onomatopoeia/catchphrases: "Pika pika!" stays; "Pikachu, I choose you!" → "¡Pikachu, yo te elijo!".

## Team Rocket motto — Spain dub (use these exact lines when the English is the motto)
- "Prepare for trouble!" → "¿Buscáis problemas?"
- "Make it double!" → "¡Pues escuchad nuestro lema!"
- "To protect the world from devastation!" → "¡Para proteger al mundo de la devastación!"
- "To unite all people within our nation!" → "¡Para unir a todos los pueblos en una sola nación!"
- "To denounce the evils of truth and love!" → "¡Para denunciar a los enemigos de la verdad y el amor!"
- "To extend our reach to the stars above!" → "¡Para extender nuestro poder más allá del espacio exterior!"
- "Jessie!" / "James!" → "¡Jessie!" / "¡James!"
- "Team Rocket, blast off at the speed of light!" → "¡El Team Rocket despega a la velocidad de la luz!"
- "Surrender now, or prepare to fight!" → "¡Rendíos ahora o preparaos para luchar!"
- "Meowth! That's right!" → "¡Meowth! ¡Bien dicho!"
- "Looks like Team Rocket's blasting off again!" → "¡El Team Rocket vuelve a salir volando!"
Variations of the motto: adapt in the same spirit.

## Official terminology (Pokémon games in Spain)
Pokémon, Poké Ball, Entrenador/Entrenadora, Centro Pokémon, Tienda Pokémon, Pokédex, Gimnasio, Líder de
Gimnasio, Medalla, Liga Pokémon / Liga Añil, Alto Mando, Campeón, MO/MT, PS, Nv., movimiento, ataque,
Equipo (party), Mochila, Caja (PC box), Huevo, variocolor (shiny), debilitado (fainted), "¡Es muy eficaz!",
"No es muy eficaz…", "¡Un golpe crítico!", "No afecta a {1}…", "¡{1} usó {2}!". Team Rocket = "Team Rocket"
(as in the Spain games and dub). Officer Jenny = Agente Mara; Nurse Joy = Enfermera Joy.
Pokémon species and the anime characters keep their names (Ash, Misty, Brock, Gary, Jessie, James, Meowth,
Delia → "la madre de Ash"/"mamá" as in context, Professor Oak = Profesor Oak).
Moves, items, abilities, types, trainer classes, places: look them up in `../glossary.json` (English →
Spanish used by the game; e.g. `grep -i '"Viridian City"' ../glossary.json`). Those are mostly the official
Spain names (Pueblo Paleta, Ciudad Verde, Bosque Verde, Ruta 1…). If a glossary entry is clearly not
official Spanish, use the official one and mention it in your notes.

## Battle and system messages
- `{1}` in battle messages is usually a Pokémon ("the wild Pidgey", "the opposing Pidgey", "Pikachu") or a
  trainer's full name (e.g. "Team Rocket Jessie y James", "Entrenador Pokémon Gary"). Write sentences that
  work for all of those.
- When `{1}` is a trainer (e.g. "{1} sent out {2}!", "{1} would like to battle!", "{1} withdrew {2}!",
  "You defeated {1}!"), ALSO add a plural version under the key `"<English source>#plural"` used when the
  trainer is two people (Jessie y James): "¡{1} sacaron a {2}!".
- Keep messages short; the battle box is two lines.

## Notes
At the end, print a short list (max 15 lines) of doubtful choices or glossary entries you had to correct.

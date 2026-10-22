# Signing key

`ashdroid.jks` is a **public** signing key (password `ashdroid`) committed on purpose:
every APK built by CI is signed with the same key, so a new version installs over
the previous one **without uninstalling** (uninstalling would wipe the imported game
and your saves).

It is not a secret and gives no security guarantees. If you fork this project and
want your own key, replace the file and set the `ASHDROID_STORE_PASSWORD`,
`ASHDROID_KEY_ALIAS` and `ASHDROID_KEY_PASSWORD` secrets/env vars.

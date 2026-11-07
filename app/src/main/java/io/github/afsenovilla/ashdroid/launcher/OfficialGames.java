package io.github.afsenovilla.ashdroid.launcher;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * SHA-256 of official Fire Ash release zips, exactly as the team distributes them.
 * Add a line for each new release (lowercase hex). A zip that matches is imported
 * without questions; any other zip needs the player's confirmation, because a
 * repacked game can run arbitrary Ruby code with the app's access.
 */
final class OfficialGames
{
    private static final Map<String, String> KNOWN = new HashMap<>();

    static {
        KNOWN.put("8cf1ff8832fde7a0dcd04f195acd65b0cb440cd3351f3fb263c2b9916022629b", "Pokémon Fire Ash 3.7.1");
    }

    private OfficialGames() {}

    /** Release name for a zip hash, or null if it isn't a known official release. */
    static String lookup(String sha256Hex)
    {
        return sha256Hex == null ? null : KNOWN.get(sha256Hex.toLowerCase(Locale.ROOT));
    }
}

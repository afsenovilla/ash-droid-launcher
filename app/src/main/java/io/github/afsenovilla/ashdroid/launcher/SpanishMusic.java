package io.github.afsenovilla.ashdroid.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Spanish versions of songs ("Música en castellano").
 * Two sources: tracks bundled with the app (assets/packs/<id>/audio_es/) and
 * tracks added by the player (kept in the app's own storage, so they survive
 * re-importing the game). Each one can be switched on or off; the enabled ones
 * are copied to <game>/ashdroid_audio_es/, where the runtime patch plays them
 * instead of the track with the same file name while the game is in Spanish.
 * A player track with the same name as a bundled one replaces it.
 */
final class SpanishMusic
{
    static final String GAME_SUBDIR = "ashdroid_audio_es";
    private static final String STORE = "audio_es";
    private static final String BUNDLED = "packs/fire-ash/audio_es";
    private static final String PREFS = "spanish_music";
    private static final String KEY_OFF = "off";

    static final class Track
    {
        final String name;
        final boolean bundled;
        boolean enabled;

        Track(String name, boolean bundled, boolean enabled)
        {
            this.name = name;
            this.bundled = bundled;
            this.enabled = enabled;
        }
    }

    private SpanishMusic() {}

    static File store(Context ctx)
    {
        return new File(ctx.getFilesDir(), STORE);
    }

    private static String[] bundledNames(Context ctx)
    {
        try {
            String[] l = ctx.getAssets().list(BUNDLED);
            return l == null ? new String[0] : l;
        } catch (IOException e) {
            return new String[0];
        }
    }

    private static Set<String> off(Context ctx)
    {
        return new HashSet<>(prefs(ctx).getStringSet(KEY_OFF, new HashSet<>()));
    }

    private static SharedPreferences prefs(Context ctx)
    {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Every track (bundled first, then the player's), sorted by name inside each group. */
    static List<Track> tracks(Context ctx)
    {
        Set<String> off = off(ctx);
        TreeMap<String, Track> user = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String[] u = store(ctx).list();
        if (u != null) for (String n : u) user.put(n, new Track(n, false, !off.contains(n)));
        List<Track> out = new ArrayList<>();
        TreeMap<String, Track> bundled = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String n : bundledNames(ctx)) if (!user.containsKey(n)) bundled.put(n, new Track(n, true, !off.contains(n)));
        out.addAll(bundled.values());
        out.addAll(user.values());
        return out;
    }

    static int enabledCount(Context ctx)
    {
        int n = 0;
        for (Track t : tracks(ctx)) if (t.enabled) n++;
        return n;
    }

    static void setEnabled(Context ctx, String name, boolean on)
    {
        Set<String> off = off(ctx);
        if (on) off.remove(name);
        else off.add(name);
        prefs(ctx).edit().putStringSet(KEY_OFF, off).apply();
    }

    static boolean hasUserTracks(Context ctx)
    {
        String[] l = store(ctx).list();
        return l != null && l.length > 0;
    }

    /** Copies a picked file into the store under its own name. Returns that name. */
    static String add(Context ctx, Uri uri) throws IOException
    {
        String name = displayName(ctx, uri);
        if (name == null || name.isEmpty()) throw new IOException("?");
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        File dir = store(ctx);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(new File(dir, name))) {
            if (in == null) throw new IOException("?");
            copy(in, os);
        }
        setEnabled(ctx, name, true);
        return name;
    }

    /** Removes the player's tracks (bundled ones stay). */
    static void clearUser(Context ctx)
    {
        File[] l = store(ctx).listFiles();
        if (l != null) for (File f : l) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    /** Mirrors the enabled tracks into the game folder. */
    static void sync(Context ctx, File game)
    {
        File dst = new File(game, GAME_SUBDIR);
        Set<String> want = new HashSet<>();
        List<Track> all = tracks(ctx);
        for (Track t : all) if (t.enabled) want.add(t.name);
        File[] old = dst.listFiles();
        if (old != null) for (File f : old) {
            if (!want.contains(f.getName())) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        if (want.isEmpty()) return;
        //noinspection ResultOfMethodCallIgnored
        dst.mkdirs();
        for (Track t : all) {
            if (!t.enabled) continue;
            File out = new File(dst, t.name);
            try {
                if (t.bundled) {
                    long len = bundledLength(ctx, t.name);
                    if (out.exists() && len >= 0 && out.length() == len) continue;
                    try (InputStream in = ctx.getAssets().open(BUNDLED + "/" + t.name);
                         OutputStream os = new FileOutputStream(out)) {
                        copy(in, os);
                    }
                } else {
                    File src = new File(store(ctx), t.name);
                    if (out.exists() && out.length() == src.length()) continue;
                    Util.copy(src, out);
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static long bundledLength(Context ctx, String name)
    {
        try (android.content.res.AssetFileDescriptor fd = ctx.getAssets().openFd(BUNDLED + "/" + name)) {
            return fd.getLength();
        } catch (IOException e) {
            return -1;
        }
    }

    private static void copy(InputStream in, OutputStream os) throws IOException
    {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
    }

    private static String displayName(Context ctx, Uri uri)
    {
        try (Cursor c = ctx.getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME },
                null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) {
        }
        String p = uri.getLastPathSegment();
        return p == null ? null : p.substring(p.lastIndexOf('/') + 1);
    }
}

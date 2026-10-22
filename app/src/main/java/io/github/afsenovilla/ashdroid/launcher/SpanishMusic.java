package io.github.afsenovilla.ashdroid.launcher;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Spanish versions of songs added by the player ("Música en castellano").
 * Kept in the app's own storage (so they survive re-importing the game) and
 * copied to <game>/ashdroid_audio_es/, where the runtime patch plays them
 * instead of the track with the same file name while the game is in Spanish.
 * Nothing is bundled with the app.
 */
final class SpanishMusic
{
    static final String GAME_SUBDIR = "ashdroid_audio_es";
    private static final String STORE = "audio_es";

    private SpanishMusic() {}

    static File store(Context ctx)
    {
        return new File(ctx.getFilesDir(), STORE);
    }

    static String[] tracks(Context ctx)
    {
        String[] l = store(ctx).list();
        return l == null ? new String[0] : l;
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
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return name;
    }

    static void clear(Context ctx)
    {
        File[] l = store(ctx).listFiles();
        if (l != null) for (File f : l) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    /** Mirrors the store into the game folder. */
    static void sync(Context ctx, File game)
    {
        File dst = new File(game, GAME_SUBDIR);
        File[] old = dst.listFiles();
        if (old != null) for (File f : old) {
            if (!new File(store(ctx), f.getName()).exists()) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        File[] src = store(ctx).listFiles();
        if (src == null || src.length == 0) return;
        //noinspection ResultOfMethodCallIgnored
        dst.mkdirs();
        for (File f : src) {
            File t = new File(dst, f.getName());
            if (t.exists() && t.length() == f.length()) continue;
            try {
                Util.copy(f, t);
            } catch (IOException ignored) {
            }
        }
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

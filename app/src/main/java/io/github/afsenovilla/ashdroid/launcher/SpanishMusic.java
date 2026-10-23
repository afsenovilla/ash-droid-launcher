package io.github.afsenovilla.ashdroid.launcher;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Songs in other languages bundled with the app (assets/packs/<id>/audio_<lang>/,
 * e.g. the Spanish, French and German intro). They are copied to
 * <game>/ashdroid_audio_<lang>/; the runtime patch plays them instead of the track
 * with the same file name only while the game is in that language and its in-game
 * option ("Música en castellano"…) is on (off by default).
 */
final class SpanishMusic
{
    private static final String[] LANGS = { "es", "fr", "de" };

    private SpanishMusic() {}

    private static String[] bundled(Context ctx, String dir)
    {
        try {
            String[] l = ctx.getAssets().list(dir);
            return l == null ? new String[0] : l;
        } catch (IOException e) {
            return new String[0];
        }
    }

    /** Mirrors the bundled tracks of every language into the game folder. */
    static void sync(Context ctx, File game)
    {
        for (String lang : LANGS) sync(ctx, game, "packs/fire-ash/audio_" + lang, "ashdroid_audio_" + lang);
    }

    private static void sync(Context ctx, File game, String bundled, String subdir)
    {
        String[] names = bundled(ctx, bundled);
        Set<String> want = new HashSet<>(Arrays.asList(names));
        File dst = new File(game, subdir);
        File[] old = dst.listFiles();
        if (old != null) for (File f : old) {
            if (!want.contains(f.getName())) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        if (names.length == 0) return;
        //noinspection ResultOfMethodCallIgnored
        dst.mkdirs();
        for (String n : names) {
            File out = new File(dst, n);
            long len = length(ctx, bundled + "/" + n);
            if (out.exists() && len >= 0 && out.length() == len) continue;
            try (InputStream in = ctx.getAssets().open(bundled + "/" + n);
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[1 << 16];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
            } catch (IOException ignored) {
            }
        }
    }

    private static long length(Context ctx, String path)
    {
        try (android.content.res.AssetFileDescriptor fd = ctx.getAssets().openFd(path)) {
            return fd.getLength();
        } catch (IOException e) {
            return -1;
        }
    }
}

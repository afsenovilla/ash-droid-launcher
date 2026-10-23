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
 * Spanish versions of songs bundled with the app (assets/packs/<id>/audio_es/).
 * They are copied to <game>/ashdroid_audio_es/; the runtime patch plays them
 * instead of the track with the same file name only while the game is in
 * Spanish and the in-game option "Música en castellano" is on (off by default).
 */
final class SpanishMusic
{
    static final String GAME_SUBDIR = "ashdroid_audio_es";
    private static final String BUNDLED = "packs/fire-ash/audio_es";

    private SpanishMusic() {}

    private static String[] bundled(Context ctx)
    {
        try {
            String[] l = ctx.getAssets().list(BUNDLED);
            return l == null ? new String[0] : l;
        } catch (IOException e) {
            return new String[0];
        }
    }

    /** Mirrors the bundled tracks into the game folder. */
    static void sync(Context ctx, File game)
    {
        String[] names = bundled(ctx);
        Set<String> want = new HashSet<>(Arrays.asList(names));
        File dst = new File(game, GAME_SUBDIR);
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
            long len = length(ctx, n);
            if (out.exists() && len >= 0 && out.length() == len) continue;
            try (InputStream in = ctx.getAssets().open(BUNDLED + "/" + n);
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[1 << 16];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
            } catch (IOException ignored) {
            }
        }
    }

    private static long length(Context ctx, String name)
    {
        try (android.content.res.AssetFileDescriptor fd = ctx.getAssets().openFd(BUNDLED + "/" + name)) {
            return fd.getLength();
        } catch (IOException e) {
            return -1;
        }
    }
}

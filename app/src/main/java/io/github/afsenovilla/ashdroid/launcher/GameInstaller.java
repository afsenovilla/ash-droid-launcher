package io.github.afsenovilla.ashdroid.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports the game from the original .zip the user downloaded, into the app's
 * private storage. Nothing from the game ships inside the APK.
 *
 * Layout in getFilesDir():
 *   game/          the game root (folder that contains Data/, Graphics/, Game.ini…)
 *   game_import/   temporary extraction folder
 * Saves are written by the game outside game/, so re-importing keeps them.
 */
public final class GameInstaller
{
    public interface Progress
    {
        void onProgress(long done, long total, String currentFile);
    }

    private static final String GAME_DIR = "game";
    private static final String TMP_DIR = "game_import";
    private static final String MARKER = ".ashdroid_installed";

    private GameInstaller() {}

    public static File gameDir(Context ctx)
    {
        return new File(ctx.getFilesDir(), GAME_DIR);
    }

    public static boolean isInstalled(Context ctx)
    {
        return new File(gameDir(ctx), MARKER).exists();
    }

    /** Title shown in the launcher, read from Game.ini when available. */
    public static String gameTitle(Context ctx)
    {
        File ini = findIni(gameDir(ctx));
        if (ini != null) {
            try {
                String text = Util.readText(ini);
                for (String line : text.split("\\r?\\n")) {
                    String l = line.trim();
                    if (l.regionMatches(true, 0, "Title=", 0, 6)) {
                        String t = l.substring(6).trim();
                        if (!t.isEmpty()) return t;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return "Juego importado";
    }

    /**
     * Extracts the zip and installs it. Runs on a background thread.
     * @throws IOException with a user-facing message if the zip isn't a valid RPG Maker XP game.
     */
    public static void importZip(Context ctx, Uri uri, Progress progress) throws IOException
    {
        ContentResolver cr = ctx.getContentResolver();
        long total = querySize(cr, uri);

        File tmp = new File(ctx.getFilesDir(), TMP_DIR);
        Util.deleteRecursive(tmp);
        if (!tmp.mkdirs()) throw new IOException("No se pudo crear la carpeta temporal");
        String tmpCanonical = tmp.getCanonicalPath() + File.separator;

        int entries;
        try {
            entries = extract(cr, uri, tmp, tmpCanonical, StandardCharsets.UTF_8, total, progress);
        } catch (IllegalArgumentException ex) {
            // Names aren't valid UTF-8: zip made with an old Windows/OEM encoding. Retry.
            Util.deleteRecursive(tmp);
            tmp.mkdirs();
            Charset legacy;
            try {
                legacy = Charset.forName("IBM437");
            } catch (Exception ce) {
                legacy = StandardCharsets.ISO_8859_1;
            }
            try {
                entries = extract(cr, uri, tmp, tmpCanonical, legacy, total, progress);
            } catch (IllegalArgumentException ex2) {
                throw new IOException("El zip tiene nombres de archivo con una codificación no compatible. "
                        + "Vuelve a comprimir la carpeta del juego con 7-Zip.");
            }
        }

        if (entries == 0) {
            Util.deleteRecursive(tmp);
            throw new IOException("El archivo no es un .zip válido o está vacío.");
        }

        File root = findGameRoot(tmp, 4);
        if (root == null) {
            Util.deleteRecursive(tmp);
            throw new IOException("No encuentro un juego de RPG Maker XP dentro del zip "
                    + "(falta Game.ini o la carpeta Data). ¿Es el zip correcto?");
        }

        File game = gameDir(ctx);
        Util.deleteRecursive(game);
        if (!root.renameTo(game)) {
            throw new IOException("No se pudo mover el juego a su carpeta final");
        }
        Util.deleteRecursive(tmp);

        if (!new File(game, MARKER).createNewFile()) {
            throw new IOException("No se pudo marcar la instalación");
        }
    }

    public static void uninstall(Context ctx)
    {
        Util.deleteRecursive(gameDir(ctx));
    }

    private static int extract(ContentResolver cr, Uri uri, File tmp, String tmpCanonical, Charset cs,
                               long total, Progress progress) throws IOException
    {
        InputStream raw = cr.openInputStream(uri);
        if (raw == null) throw new IOException("No se pudo abrir el archivo");

        CountingInputStream counting = new CountingInputStream(new BufferedInputStream(raw, 1 << 16));
        byte[] buf = new byte[1 << 16];
        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(counting, cs)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(tmp, e.getName());
                // Zip-slip protection
                if (!out.getCanonicalPath().startsWith(tmpCanonical)) {
                    throw new IOException("Entrada no válida en el zip: " + e.getName());
                }
                if (e.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            os.write(buf, 0, n);
                            if (progress != null) progress.onProgress(counting.count, total, e.getName());
                        }
                    }
                    entries++;
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    // ---- helpers ----

    /** Breadth-first search for the folder containing Game.ini or Data/Scripts.rxdata. */
    static File findGameRoot(File dir, int depth)
    {
        if (looksLikeGameRoot(dir)) return dir;
        if (depth <= 0) return null;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.isDirectory() && !c.getName().startsWith("__MACOSX") && looksLikeGameRoot(c)) return c;
        }
        for (File c : children) {
            if (c.isDirectory() && !c.getName().startsWith("__MACOSX")) {
                File r = findGameRoot(c, depth - 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static boolean looksLikeGameRoot(File dir)
    {
        if (findIni(dir) != null) return true;
        File data = Util.findChildIgnoreCase(dir, "Data");
        if (data != null && data.isDirectory()) {
            return Util.findChildIgnoreCase(data, "Scripts.rxdata") != null;
        }
        return Util.findChildIgnoreCase(dir, "Game.rgssad") != null;
    }

    static File findIni(File dir)
    {
        File ini = Util.findChildIgnoreCase(dir, "Game.ini");
        return (ini != null && ini.isFile()) ? ini : null;
    }

    private static long querySize(ContentResolver cr, Uri uri)
    {
        try (Cursor c = cr.query(uri, new String[] { OpenableColumns.SIZE }, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static final class CountingInputStream extends FilterInputStream
    {
        long count;

        CountingInputStream(InputStream in) { super(in); }

        @Override
        public int read() throws IOException
        {
            int b = super.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }

        @Override
        public long skip(long n) throws IOException
        {
            long s = super.skip(n);
            count += s;
            return s;
        }
    }
}

package io.github.afsenovilla.ashdroid.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import io.github.afsenovilla.ashdroid.R;

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
 *   game_old/      previous game tree, kept until a replacement is fully in place
 *   userdata/      player data (saves, backups) parked here whenever game/ is
 *                  removed or replaced, and moved back afterwards
 *
 * Fire Ash's multi-save plugin writes "Save Files/GameN.rxdata" relative to the
 * working directory, i.e. inside game/. Before game/ is ever deleted, that data
 * is moved to userdata/ (a rename on the same filesystem), so re-import and
 * "Delete game" never destroy saves.
 */
public final class GameInstaller
{
    public interface Progress
    {
        void onProgress(long done, long total, String currentFile);
    }

    /** Asked (on the worker thread, may block) when the zip is not a known official release. */
    public interface Verifier
    {
        boolean allowUnverified(String sha256Hex);
    }

    private static final String VERIFIED = ".ashdroid_verified";

    /** Name of the verified official release installed, or null if unverified/unknown. */
    public static String verifiedRelease(Context ctx)
    {
        File f = new File(gameDir(ctx), VERIFIED);
        try {
            return f.isFile() ? Util.readText(f).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static final String GAME_DIR = "game";
    private static final String TMP_DIR = "game_import";
    private static final String OLD_DIR = "game_old";
    private static final String USER_DIR = "userdata";
    private static final String MARKER = ".ashdroid_installed";

    /** Fire Ash is ~1-2 GB with tens of thousands of files; these are generous safety caps. */
    private static final long MAX_BYTES = 8L << 30;
    private static final int MAX_ENTRIES = 200_000;

    /** Serializes import, delete and launch preparation. */
    private static final Object LOCK = new Object();

    private GameInstaller() {}

    public static File gameDir(Context ctx)
    {
        return new File(ctx.getFilesDir(), GAME_DIR);
    }

    public static boolean isInstalled(Context ctx)
    {
        synchronized (LOCK) {
            recover(ctx);
            return new File(gameDir(ctx), MARKER).isFile();
        }
    }

    /** Lock shared with launch preparation so it never overlaps a swap. */
    static Object lock() { return LOCK; }

    /**
     * Repairs the state left by an interrupted replacement: if the new tree never
     * got its marker, the previous tree is restored; once the new tree is marked,
     * the previous one is discarded (its player data was already moved out).
     */
    private static void recover(Context ctx)
    {
        File game = gameDir(ctx);
        File old = new File(ctx.getFilesDir(), OLD_DIR);
        if (!old.exists()) {
            restoreUserData(ctx, game);
            return;
        }
        if (new File(game, MARKER).isFile()) {
            stashUserData(ctx, old);
            Util.deleteRecursive(old);
        } else {
            stashUserData(ctx, game);
            Util.deleteRecursive(game);
            //noinspection ResultOfMethodCallIgnored
            old.renameTo(game);
        }
        restoreUserData(ctx, game);
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
        return ctx.getString(R.string.imported_game);
    }

    /**
     * Extracts the zip and installs it. Runs on a background thread.
     * @throws IOException with a user-facing message if the zip isn't a valid RPG Maker XP game.
     */
    public static void importZip(Context ctx, Uri uri, Progress progress) throws IOException
    {
        importZip(ctx, uri, progress, null);
    }

    /**
     * @param verifier asked when the zip's SHA-256 is not a known official release;
     *                 null = accept any zip (tests). The hash is computed over the
     *                 very bytes that are extracted.
     */
    public static void importZip(Context ctx, Uri uri, Progress progress, Verifier verifier) throws IOException
    {
        ContentResolver cr = ctx.getContentResolver();
        long total = querySize(cr, uri);

        File tmp = new File(ctx.getFilesDir(), TMP_DIR);
        Util.deleteRecursive(tmp);
        if (!tmp.mkdirs()) throw new IOException(ctx.getString(R.string.err_tmp_dir));
        String tmpCanonical = tmp.getCanonicalPath() + File.separator;

        boolean ok = false;
        try {
            importStaged(ctx, cr, uri, tmp, tmpCanonical, total, progress, verifier);
            ok = true;
        } finally {
            if (!ok) Util.deleteRecursive(tmp);
        }
    }

    private static void importStaged(Context ctx, ContentResolver cr, Uri uri, File tmp, String tmpCanonical,
                                     long total, Progress progress, Verifier verifier) throws IOException
    {
        int entries;
        String[] hash = new String[1];
        try {
            entries = extract(ctx, cr, uri, tmp, tmpCanonical, StandardCharsets.UTF_8, total, progress, hash);
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
                entries = extract(ctx, cr, uri, tmp, tmpCanonical, legacy, total, progress, hash);
            } catch (IllegalArgumentException ex2) {
                throw new IOException(ctx.getString(R.string.err_encoding));
            }
        }

        if (entries == 0) {
            Util.deleteRecursive(tmp);
            throw new IOException(ctx.getString(R.string.err_empty_zip));
        }

        File root = findGameRoot(tmp, 4);
        if (root == null) {
            Util.deleteRecursive(tmp);
            throw new IOException(ctx.getString(R.string.err_no_game));
        }

        String release = OfficialGames.lookup(hash[0]);
        if (release == null && verifier != null && !verifier.allowUnverified(hash[0])) {
            Util.deleteRecursive(tmp);
            throw new IOException(ctx.getString(R.string.import_cancelled_unverified));
        }
        Util.deleteRecursive(new File(root, VERIFIED));
        if (release != null) Util.writeText(new File(root, VERIFIED), release);

        synchronized (LOCK) {
            recover(ctx);
            File game = gameDir(ctx);
            File old = new File(ctx.getFilesDir(), OLD_DIR);
            Util.deleteRecursive(old);
            // A zip must not ship its own marker or player data.
            Util.deleteRecursive(new File(root, MARKER));

            // 1. Player data out of the old tree, 2. old tree aside (not deleted).
            stashUserData(ctx, game);
            if (game.exists() && !game.renameTo(old)) {
                restoreUserData(ctx, game);
                Util.deleteRecursive(tmp);
                throw new IOException(ctx.getString(R.string.err_move));
            }
            // 3. New tree in place, or roll back.
            if (!root.renameTo(game)) {
                //noinspection ResultOfMethodCallIgnored
                old.renameTo(game);
                restoreUserData(ctx, game);
                Util.deleteRecursive(tmp);
                throw new IOException(ctx.getString(R.string.err_move));
            }
            Util.deleteRecursive(tmp);
            // 4. Player data back, 5. marker = commit point, 6. drop old tree.
            restoreUserData(ctx, game);
            if (!new File(game, MARKER).createNewFile()) {
                recover(ctx); // restores the previous tree
                throw new IOException(ctx.getString(R.string.err_mark));
            }
            Util.deleteRecursive(old);
        }
    }

    /** Removes the game files. Saves are parked in userdata/ for the next import. */
    public static void uninstall(Context ctx)
    {
        synchronized (LOCK) {
            recover(ctx);
            File game = gameDir(ctx);
            stashUserData(ctx, game);
            Util.deleteRecursive(game);
        }
    }

    // ---- player data ----

    /** True for entries directly under the game root that belong to the player. */
    private static boolean isUserData(File f)
    {
        return isUserDataName(f.getName(), f.isDirectory());
    }

    private static boolean isUserDataName(String name, boolean dir)
    {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (dir) return n.equals("save files") || n.equals("saves") || n.equals("backups");
        return n.endsWith(".rxdata") || n.endsWith(".rxdata.bak") || n.endsWith(".rvdata")
                || n.endsWith(".bak") && n.startsWith("game");
    }

    /** Moves player data from {@code from} into userdata/ (merging, never overwriting newer parked copies with nothing). */
    private static void stashUserData(Context ctx, File from)
    {
        File[] children = from.listFiles();
        if (children == null) return;
        File user = new File(ctx.getFilesDir(), USER_DIR);
        for (File c : children) {
            if (!isUserData(c)) continue;
            //noinspection ResultOfMethodCallIgnored
            user.mkdirs();
            moveMerge(c, new File(user, c.getName()));
        }
    }

    /** Moves parked player data back into the game root. */
    private static void restoreUserData(Context ctx, File to)
    {
        File user = new File(ctx.getFilesDir(), USER_DIR);
        File[] children = user.listFiles();
        if (children == null || !to.isDirectory()) return;
        for (File c : children) moveMerge(c, new File(to, c.getName()));
        //noinspection ResultOfMethodCallIgnored
        user.delete();
    }

    /** Rename {@code src} to {@code dst}; for directories, merge into an existing one. A file in dst is replaced only by a file from src. */
    private static void moveMerge(File src, File dst)
    {
        if (src.isDirectory()) {
            if (!dst.exists() && src.renameTo(dst)) return;
            //noinspection ResultOfMethodCallIgnored
            dst.mkdirs();
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) moveMerge(k, new File(dst, k.getName()));
            //noinspection ResultOfMethodCallIgnored
            src.delete();
        } else {
            if (dst.isDirectory()) return; // conflict: leave src where it is
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            //noinspection ResultOfMethodCallIgnored
            src.renameTo(dst);
        }
    }

    private static int extract(Context ctx, ContentResolver cr, Uri uri, File tmp, String tmpCanonical, Charset cs,
                               long total, Progress progress, String[] hashOut) throws IOException
    {
        InputStream raw = cr.openInputStream(uri);
        if (raw == null) throw new IOException(ctx.getString(R.string.err_open));

        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex.toString());
        }
        // Digest sits below the zip reader's own buffering, so it sees every byte read from the file.
        java.security.DigestInputStream digest = new java.security.DigestInputStream(
                new BufferedInputStream(raw, 1 << 16), md);
        CountingInputStream counting = new CountingInputStream(digest);
        byte[] buf = new byte[1 << 16];
        int entries = 0;
        long written = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(counting, cs)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (seen.size() >= MAX_ENTRIES) throw new IOException(ctx.getString(R.string.err_zip_limits));
                File out = new File(tmp, e.getName());
                // Zip-slip protection
                String canon = out.getCanonicalPath();
                if (!canon.startsWith(tmpCanonical)) {
                    throw new IOException(ctx.getString(R.string.err_bad_entry, e.getName()));
                }
                // Duplicate or case-colliding paths would silently replace earlier files
                String key = canon.toLowerCase(java.util.Locale.ROOT) + (e.isDirectory() ? "/" : "");
                if (!seen.add(key)) {
                    if (e.isDirectory()) { zis.closeEntry(); continue; }
                    throw new IOException(ctx.getString(R.string.err_bad_entry, e.getName()));
                }
                if (e.isDirectory()) {
                    if (out.isFile()) throw new IOException(ctx.getString(R.string.err_bad_entry, e.getName()));
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    if (out.isDirectory() || (parent != null && !parent.isDirectory())) {
                        throw new IOException(ctx.getString(R.string.err_bad_entry, e.getName()));
                    }
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            written += n;
                            if (written > MAX_BYTES) throw new IOException(ctx.getString(R.string.err_zip_limits));
                            os.write(buf, 0, n);
                            if (progress != null) progress.onProgress(counting.count, total, e.getName());
                        }
                    }
                    entries++;
                }
                zis.closeEntry();
            }
            // Central directory and anything after it: part of the file's hash too.
            byte[] rest = new byte[1 << 16];
            //noinspection StatementWithEmptyBody
            while (digest.read(rest) > 0) { }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : md.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
        hashOut[0] = hex.toString();
        return entries;
    }

    // ---- save export / import ----

    private static final long MAX_SAVE_ZIP_BYTES = 256L << 20;

    /** Writes every save (in the game tree and parked) to a zip. Returns the number of files. */
    public static int exportSaves(Context ctx, Uri dest) throws IOException
    {
        synchronized (LOCK) {
            recover(ctx);
            OutputStream raw = ctx.getContentResolver().openOutputStream(dest);
            if (raw == null) throw new IOException(ctx.getString(R.string.err_open));
            int[] count = { 0 };
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                    new java.io.BufferedOutputStream(raw, 1 << 16))) {
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (File base : new File[] { gameDir(ctx), new File(ctx.getFilesDir(), USER_DIR) }) {
                    File[] children = base.listFiles();
                    if (children == null) continue;
                    for (File c : children) {
                        if (isUserData(c)) zipInto(zos, c, c.getName(), seen, count);
                    }
                }
            }
            if (count[0] == 0) throw new IOException(ctx.getString(R.string.err_no_saves));
            return count[0];
        }
    }

    private static void zipInto(java.util.zip.ZipOutputStream zos, File f, String path,
                                java.util.Set<String> seen, int[] count) throws IOException
    {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) zipInto(zos, k, path + "/" + k.getName(), seen, count);
            return;
        }
        if (!seen.add(path.toLowerCase(java.util.Locale.ROOT))) return;
        zos.putNextEntry(new ZipEntry(path));
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
        }
        zos.closeEntry();
        count[0]++;
    }

    /** Folder the multi-save plugin reads, relative to the game root. */
    private static final String SAVE_DIR = "Save Files";

    /**
     * Restores saves. Accepts either a zip (ours from {@link #exportSaves}, or one
     * made by hand / on PC, with saves at any depth) or a single raw save file
     * (Ruby Marshal, e.g. "Game2.rxdata" copied straight from a PC install).
     * Only save files are accepted (no game files, no scripts). Existing saves
     * with the same name are replaced. Returns the number of files restored.
     */
    public static int importSaves(Context ctx, Uri src) throws IOException
    {
        synchronized (LOCK) {
            recover(ctx);
            File stage = new File(ctx.getFilesDir(), "saves_import");
            Util.deleteRecursive(stage);
            if (!stage.mkdirs()) throw new IOException(ctx.getString(R.string.err_tmp_dir));
            String stageCanonical = stage.getCanonicalPath() + File.separator;
            int count = 0;
            long[] written = { 0 };
            try {
                InputStream raw = ctx.getContentResolver().openInputStream(src);
                if (raw == null) throw new IOException(ctx.getString(R.string.err_open));
                BufferedInputStream in = new BufferedInputStream(raw, 1 << 16);
                try {
                    in.mark(4);
                    int b0 = in.read(), b1 = in.read();
                    in.reset();
                    if (b0 == 'P' && b1 == 'K') {
                        try (ZipInputStream zis = new ZipInputStream(in)) {
                            ZipEntry e;
                            while ((e = zis.getNextEntry()) != null) {
                                if (e.isDirectory()) continue;
                                String rel = saveTargetPath(e.getName());
                                if (rel == null) continue;
                                File out = new File(stage, rel);
                                if (!out.getCanonicalPath().startsWith(stageCanonical)) {
                                    throw new IOException(ctx.getString(R.string.err_bad_entry, e.getName()));
                                }
                                copyLimited(ctx, zis, out, written);
                                count++;
                            }
                        }
                    } else if (b0 == 0x04 && b1 == 0x08) {
                        // Raw Ruby Marshal 4.8 = an RGSS save file on its own.
                        String name = safeSaveName(displayName(ctx, src));
                        copyLimited(ctx, in, new File(stage, SAVE_DIR + "/" + name), written);
                        count++;
                    }
                } finally {
                    in.close();
                }
                if (count == 0) throw new IOException(ctx.getString(R.string.err_no_saves));
                // Into the game if installed, otherwise parked for the next import.
                File target = new File(gameDir(ctx), MARKER).isFile()
                        ? gameDir(ctx) : new File(ctx.getFilesDir(), USER_DIR);
                //noinspection ResultOfMethodCallIgnored
                target.mkdirs();
                File[] kids = stage.listFiles();
                if (kids != null) for (File k : kids) moveMerge(k, new File(target, k.getName()));
                return count;
            } finally {
                Util.deleteRecursive(stage);
            }
        }
    }

    /**
     * Maps a zip entry to its path under the game root, or null to skip it.
     * "Pokemon Fire Ash/Save Files/Game2.rxdata" -> "Save Files/Game2.rxdata";
     * a bare "Game2.rxdata" (at any depth) -> "Save Files/Game2.rxdata".
     */
    static String saveTargetPath(String entry)
    {
        String[] parts = entry.replace('\\', '/').split("/");
        java.util.List<String> segs = new java.util.ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) return null;
            segs.add(p);
        }
        if (segs.isEmpty()) return null;
        for (int i = 0; i < segs.size() - 1; i++) {
            if (isUserDataName(segs.get(i), true)) {
                String top = segs.get(i).equalsIgnoreCase(SAVE_DIR) ? SAVE_DIR : segs.get(i);
                StringBuilder sb = new StringBuilder(top);
                for (int j = i + 1; j < segs.size(); j++) sb.append('/').append(segs.get(j));
                return sb.toString();
            }
        }
        String leaf = segs.get(segs.size() - 1);
        if (isGameSave(leaf)) return SAVE_DIR + "/" + leaf;
        if (segs.size() == 1 && isUserDataName(leaf, false)) return leaf;
        return null;
    }

    /** "Game.rxdata", "Game2.rxdata", "Game3.rxdata.bak"... (what the multi-save plugin writes). */
    private static boolean isGameSave(String name)
    {
        return name.toLowerCase(java.util.Locale.ROOT).matches("game\\d*\\.rxdata(\\.bak)?");
    }

    /**
     * Name for a loose save. Pickers / messengers often rename files
     * ("644337b8-Game2.rxdata", "Game2 (1).rxdata"), so the GameN part is recovered.
     */
    static String safeSaveName(String display)
    {
        if (display != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?i)game\\s*(\\d*)\\s*(?:\\(\\d+\\))?\\.rxdata").matcher(display);
            String last = null;
            while (m.find()) last = m.group(1);
            if (last != null) return "Game" + last + ".rxdata";
        }
        return "Game.rxdata";
    }

    private static String displayName(Context ctx, Uri uri)
    {
        try (Cursor c = ctx.getContentResolver().query(uri,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
            // fall through
        }
        return uri.getLastPathSegment();
    }

    private static void copyLimited(Context ctx, InputStream in, File out, long[] written) throws IOException
    {
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        byte[] buf = new byte[1 << 16];
        try (OutputStream os = new FileOutputStream(out)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                written[0] += n;
                if (written[0] > MAX_SAVE_ZIP_BYTES) throw new IOException(ctx.getString(R.string.err_saves_too_big));
                os.write(buf, 0, n);
            }
        }
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

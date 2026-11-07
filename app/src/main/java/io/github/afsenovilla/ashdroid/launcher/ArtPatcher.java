package io.github.afsenovilla.ashdroid.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Redraws English lettering inside game images, following the recipes in
 * assets/packs/<id>/art.json and the pixel fonts in assets/pixelfonts.txt.
 * The drawing itself is {@link ArtEngine} (same algorithm as tools/art/relabel.py,
 * used to author and preview recipes); this class adds the Android parts: reading
 * and writing PNGs and rasterising the game's TrueType fonts.
 *
 * Results go to ashdroid_art/ in the game folder (originals untouched) plus an
 * index the runtime patch reads: "es"/"fr"/"de" images replace the originals only
 * while the game is in that language; "any" images (button names) always do.
 */
final class ArtPatcher
{
    static final String OUT_DIR = "ashdroid_art";
    static final String INDEX = "ashdroid_art/index.txt";

    private ArtPatcher() {}

    /** Android side of the engine: user pictures and TrueType text from the game folder. */
    private static final class AndroidHost implements ArtEngine.Host
    {
        final File game;
        final Map<String, Typeface> faces = new HashMap<>();

        AndroidHost(File game) { this.game = game; }

        @Override
        public ArtEngine.Img userImage(String rel)
        {
            File src = findUserFile(game, rel);
            return src == null ? null : load(src);
        }

        @Override
        public ArtEngine.Mask ttf(String font, int size, String text)
        {
            Typeface tf;
            if (faces.containsKey(font)) tf = faces.get(font);
            else {
                File f = findUserFile(game, "Fonts/" + font + ".ttf");
                tf = null;
                try {
                    if (f != null) tf = Typeface.createFromFile(f);
                } catch (RuntimeException ignored) {
                }
                faces.put(font, tf);
            }
            if (tf == null) return null;
            int w = (int) (size * 0.8 * Math.max(1, text.length())) + size * 2, h = size * 2;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(tf);
            p.setTextSize(size);
            p.setColor(0xFFFFFFFF);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(text, size / 2f, size / 4f - fm.ascent, p);
            int[] px = new int[w * h];
            Bitmap argb = b.copy(Bitmap.Config.ARGB_8888, false);
            argb.getPixels(px, 0, w, 0, 0, w, h);
            argb.recycle();
            b.recycle();
            double[] a = new double[w * h];
            for (int i = 0; i < px.length; i++) a[i] = (px[i] >>> 24) / 255.0;
            return new ArtEngine.Mask(w, h, a);
        }
    }

    static ArtEngine.Img load(File f)
    {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPremultiplied = false;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        b.recycle();
        return new ArtEngine.Img(w, h, px);
    }

    static void save(ArtEngine.Img im, File f) throws IOException
    {
        Bitmap b = Bitmap.createBitmap(im.w, im.h, Bitmap.Config.ARGB_8888);
        b.setPremultiplied(false);
        b.setPixels(im.px, 0, im.w, 0, 0, im.w, im.h);
        try (OutputStream os = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        b.recycle();
    }

    /** buttons: label for USE, BACK, ACTION, SPECIAL (e.g. "A", "B", "X", "Y"). */
    static void generate(Context ctx, File game, String packId, Map<String, String> buttons)
    {
        File out = new File(game, OUT_DIR);
        StringBuilder index = new StringBuilder();
        try {
            Map<String, ArtEngine.Font> fonts = ArtEngine.parseFonts(readAsset(ctx, "pixelfonts.txt"));
            JSONObject recipes = new JSONObject(readAsset(ctx, "packs/" + packId + "/art.json"));
            JSONArray images = recipes.getJSONArray("images");
            LinkedHashSet<String> paths = new LinkedHashSet<>();
            for (int i = 0; i < images.length(); i++) paths.add(images.getJSONObject(i).getString("path"));
            AndroidHost host = new AndroidHost(game);
            out.mkdirs();
            for (String path : paths) {
                File src = findImage(game, path);
                ArtEngine.Img orig = src == null ? null : load(src);
                if (orig == null) continue;
                for (Map.Entry<String, List<JSONObject>> v : ArtEngine.variants(images, path).entrySet()) {
                    try {
                        ArtEngine.Img im = ArtEngine.relabel(orig, v.getValue(), fonts, buttons, host);
                        String name = "any".equals(v.getKey()) ? "any_" + ArtEngine.outName(path)
                                : ArtEngine.outFile(path, v.getKey());
                        save(im, new File(out, name));
                        index.append(v.getKey()).append('\t').append(path.toLowerCase(Locale.ROOT)).append('\t')
                                .append(OUT_DIR).append('/').append(name).append('\n');
                    } catch (Exception ignored) {
                        // skip this image, keep the rest
                    }
                }
            }
        } catch (Exception ignored) {
            // no recipes for this game
        }
        try {
            out.mkdirs();
            Util.writeText(new File(game, INDEX), index.toString());
        } catch (IOException ignored) {
        }
    }

    private static String stripAccents(String s)
    {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Finds a file the user added to the game folder, ignoring case and accents. */
    private static File findUserFile(File game, String rel)
    {
        File cur = game;
        for (String part : rel.split("/")) {
            File[] list = cur.listFiles();
            if (list == null) return null;
            File hit = null;
            String want = stripAccents(part).toLowerCase(Locale.ROOT);
            for (File f : list) {
                if (stripAccents(f.getName()).toLowerCase(Locale.ROOT).equals(want)) { hit = f; break; }
            }
            if (hit == null) return null;
            cur = hit;
        }
        return cur.isFile() ? cur : null;
    }

    private static File findImage(File game, String path)
    {
        File cur = game;
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            String name = i == parts.length - 1 ? parts[i] + ".png" : parts[i];
            cur = Util.findChildIgnoreCase(cur, name);
            if (cur == null) return null;
        }
        return cur.isFile() ? cur : null;
    }

    private static String readAsset(Context ctx, String name) throws IOException
    {
        try (InputStream in = ctx.getAssets().open(name)) {
            return Util.readText(in);
        }
    }
}

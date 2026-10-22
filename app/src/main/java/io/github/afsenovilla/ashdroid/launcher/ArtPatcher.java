package io.github.afsenovilla.ashdroid.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Redraws English lettering inside game images, following the recipes in
 * assets/packs/<id>/art.json and the pixel fonts in assets/pixelfonts.txt.
 * Same algorithm as tools/art/relabel.py (used to author and preview recipes).
 *
 * Results go to ashdroid_art/ in the game folder (originals untouched) plus an
 * index the runtime patch reads: "es" images replace the originals only while
 * the game is in Spanish; "any" images (button names) always do.
 */
final class ArtPatcher
{
    static final String OUT_DIR = "ashdroid_art";
    static final String INDEX = "ashdroid_art/index.txt";

    private static final class Font
    {
        int w, h;
        final Map<Character, String[]> glyphs = new HashMap<>();
    }

    private ArtPatcher() {}

    /** Image seen in "art pixels": grid x grid blocks starting at an offset. */
    private static final class Canvas
    {
        final Bitmap b;
        final int g, ox, oy, W, H;

        Canvas(Bitmap b, int g, int ox, int oy)
        {
            this.b = b; this.g = g; this.ox = ox; this.oy = oy;
            W = (b.getWidth() - ox) / g;
            H = (b.getHeight() - oy) / g;
        }

        int get(int x, int y)
        {
            return (x >= 0 && y >= 0 && x < W && y < H) ? b.getPixel(ox + x * g, oy + y * g) : 0;
        }

        void put(int x, int y, int c)
        {
            if (x < 0 || y < 0 || x >= W || y >= H) return;
            for (int yy = 0; yy < g; yy++)
                for (int xx = 0; xx < g; xx++) b.setPixel(ox + x * g + xx, oy + y * g + yy, c);
        }
    }

    /** buttons: label for USE, BACK, ACTION, SPECIAL (e.g. "A", "B", "X", "Y"). */
    static void generate(Context ctx, File game, String packId, Map<String, String> buttons)
    {
        File out = new File(game, OUT_DIR);
        StringBuilder index = new StringBuilder();
        Map<String, Bitmap> done = new java.util.LinkedHashMap<>();
        Map<String, String> langs = new HashMap<>();
        try {
            Map<String, Font> fonts = loadFonts(ctx);
            JSONObject recipes = new JSONObject(readAsset(ctx, "packs/" + packId + "/art.json"));
            JSONArray images = recipes.getJSONArray("images");
            for (int i = 0; i < images.length(); i++) {
                JSONObject img = images.getJSONObject(i);
                String path = img.getString("path");
                try {
                    Bitmap im = done.get(path);
                    if (im == null) {
                        File src = findImage(game, path);
                        if (src == null) continue;
                        Bitmap orig = BitmapFactory.decodeFile(src.getAbsolutePath());
                        if (orig == null) continue;
                        im = orig.copy(Bitmap.Config.ARGB_8888, true);
                        orig.recycle();
                    }
                    JSONArray off = img.optJSONArray("offset");
                    Canvas cv = new Canvas(im, img.optInt("grid", 1),
                            off == null ? 0 : off.getInt(0), off == null ? 0 : off.getInt(1));
                    JSONArray regions = img.getJSONArray("regions");
                    for (int r = 0; r < regions.length(); r++) {
                        applyRegion(cv, regions.getJSONObject(r), fonts, buttons, game);
                    }
                    done.put(path, im);
                    langs.put(path, img.optString("lang", "es"));
                } catch (Exception ignored) {
                    // skip this image, keep the rest
                }
            }
            out.mkdirs();
            for (Map.Entry<String, Bitmap> e : done.entrySet()) {
                String name = outName(e.getKey());
                try (OutputStream os = new FileOutputStream(new File(out, name))) {
                    e.getValue().compress(Bitmap.CompressFormat.PNG, 100, os);
                    index.append(langs.get(e.getKey())).append('\t')
                            .append(e.getKey().toLowerCase(Locale.ROOT)).append('\t')
                            .append(OUT_DIR).append('/').append(name).append('\n');
                } catch (IOException ignored) {
                }
                e.getValue().recycle();
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

    // ------------------------------------------------------------------ core

    private static void applyRegion(Canvas cv, JSONObject r, Map<String, Font> fonts, Map<String, String> buttons,
            File game) throws Exception
    {
        // Picture supplied by the user in the game folder (e.g. official Spanish art)
        if (r.has("image")) {
            File src = findUserFile(game, r.getString("image"));
            Bitmap pic = src == null ? null : BitmapFactory.decodeFile(src.getAbsolutePath());
            if (pic != null) {
                JSONArray ir = r.has("imageRect") ? r.getJSONArray("imageRect") : r.getJSONArray("rect");
                int x = ir.getInt(0), y = ir.getInt(1), w = ir.getInt(2), h = ir.getInt(3);
                for (int yy = 0; yy < h; yy++)
                    for (int xx = 0; xx < w; xx++)
                        cv.put(x + xx, y + yy, pic.getPixel(xx * pic.getWidth() / w, yy * pic.getHeight() / h));
                pic.recycle();
                return;
            }
        }
        if (r.optBoolean("onlyImage", false)) return;

        JSONArray rect = r.getJSONArray("rect");
        int x = rect.getInt(0), y = rect.getInt(1), w = rect.getInt(2), h = rect.getInt(3);
        String style = r.optString("style", "shadow");
        String erase = r.optString("erase", "row");
        int bgx = r.optInt("bgx", x);

        int[] rowbg = new int[h];
        for (int yy = 0; yy < h; yy++) {
            if ("transparent".equals(erase)) rowbg[yy] = 0;
            else if (r.has("bg")) rowbg[yy] = parseColor(r.getString("bg"));
            else rowbg[yy] = cv.get(bgx, y + yy);
        }

        // Count colours as in the reference tool: most frequent first, ties by first seen
        java.util.LinkedHashMap<Integer, Integer> cnt = new java.util.LinkedHashMap<>();
        int tx0 = Integer.MAX_VALUE, ty0 = Integer.MAX_VALUE, tx1 = -1, ty1 = -1;
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                int c = cv.get(xx, yy);
                if (c != rowbg[yy - y] && Color.alpha(c) > 0) {
                    Integer n = cnt.get(c);
                    cnt.put(c, n == null ? 1 : n + 1);
                    tx0 = Math.min(tx0, xx); tx1 = Math.max(tx1, xx);
                    ty0 = Math.min(ty0, yy); ty1 = Math.max(ty1, yy);
                }
            }
        int first = 0, second = 0, n1 = -1, n2 = -1;
        for (Map.Entry<Integer, Integer> e : cnt.entrySet()) {
            int c = e.getKey(), n = e.getValue();
            if (n > n1) { second = first; n2 = n1; first = c; n1 = n; }
            else if (n > n2) { second = c; n2 = n; }
        }
        boolean has1 = n1 >= 0, has2 = n2 >= 0;
        int fill, shade;
        boolean hasShade;
        if ("outline".equals(style)) {
            shade = has1 ? first : Color.rgb(64, 64, 64);
            fill = has2 ? second : Color.rgb(248, 248, 248);
            hasShade = true;
        } else {
            fill = has1 ? first : Color.rgb(64, 64, 64);
            shade = second;
            hasShade = has2;
        }
        if (r.has("fill")) {
            fill = parseColor(r.getString("fill"));
            if (!"outline".equals(style)) {
                // shade = most frequent colour that isn't the fill
                int best = -1;
                hasShade = false;
                for (Map.Entry<Integer, Integer> e : cnt.entrySet()) {
                    if ((e.getKey() & 0xFFFFFF) == (fill & 0xFFFFFF)) continue;
                    if (e.getValue() > best) { best = e.getValue(); shade = e.getKey(); hasShade = true; }
                }
            }
        }
        if (r.has("shade")) { shade = parseColor(r.getString("shade")); hasShade = true; }
        JSONArray fa = r.optJSONArray("fills");
        int[] fills = new int[fa == null ? 0 : fa.length()];
        for (int i = 0; i < fills.length; i++) fills[i] = parseColor(fa.getString(i));
        if (ty1 < 0) { ty0 = y; ty1 = y + h - 1; tx0 = x; }

        if (!"none".equals(erase)) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) cv.put(xx, yy, rowbg[yy - y]);
        }

        JSONArray bm = r.optJSONArray("bitmap");
        if (bm != null) {
            int ox = x + r.optInt("dx", 0), oy = y + r.optInt("dy", 0);
            for (int j = 0; j < bm.length(); j++) {
                String row = bm.getString(j);
                for (int i = 0; i < row.length(); i++) {
                    char ch = row.charAt(i);
                    if (ch == '#') cv.put(ox + i, oy + j, fill);
                    else if (ch == 's' && hasShade) cv.put(ox + i, oy + j, shade);
                }
            }
            return;
        }

        String text = r.optString("text", "");
        for (Map.Entry<String, String> b : buttons.entrySet())
            if (b.getValue() != null) text = text.replace("{" + b.getKey() + "}", b.getValue());
        text = stripAccents(text).toUpperCase(Locale.ROOT);
        java.util.List<String> names = new java.util.ArrayList<>();
        JSONArray fl = r.optJSONArray("font");
        if (fl != null) for (int i = 0; i < fl.length(); i++) names.add(fl.getString(i));
        else names.add(r.optString("font", "5x7"));
        int bold = r.optBoolean("bold", false) ? 1 : 0;
        int extra = "shadow".equals(style) && hasShade ? 1 : ("outline".equals(style) ? 2 : 0);
        int height = r.has("height") ? r.getInt("height") : Math.max(1, ty1 - ty0 + 1 - extra);

        // First font (in order) whose glyphs cover the text and fit at the preferred
        // spacing; the last one is squeezed if nothing fits.
        Font font = null;
        String[][] glyphs = null;
        int[] widths = null;
        int sum = 0, spacing = 0, tw;
        for (int fi = 0; fi < names.size(); fi++) {
            font = fonts.get(names.get(fi));
            if (font == null) return;
            glyphs = new String[text.length()][];
            widths = new int[text.length()];
            sum = 0;
            boolean covered = true;
            for (int i = 0; i < text.length(); i++) {
                String[] g = font.glyphs.get(text.charAt(i));
                if (g == null) {
                    if (text.charAt(i) != ' ') covered = false;
                    g = new String[font.h];
                    java.util.Arrays.fill(g, new String(new char[font.w]).replace('\0', '.'));
                }
                glyphs[i] = g;
                widths[i] = g[0].length() + bold;
                sum += widths[i];
            }
            spacing = r.optInt("spacing", 1);
            tw = sum + spacing * (text.length() - 1) + extra;
            if (fi == names.size() - 1 || (covered && tw <= w)) break;
        }
        while (true) {
            tw = sum + spacing * (text.length() - 1) + extra;
            if (tw <= w || spacing <= 0) break;
            spacing--;
        }
        String align = r.optString("align", "center");
        int ox;
        if ("left".equals(align)) ox = tx1 >= 0 ? tx0 : x;
        else if ("right".equals(align)) ox = x + w - tw;
        else ox = x + Math.floorDiv(w - tw, 2);
        ox += r.optInt("dx", 0);
        int oy = ty0 + r.optInt("dy", 0);
        int gx0 = ox + ("outline".equals(style) ? 1 : 0);
        int gy0 = oy + ("outline".equals(style) ? 1 : 0);

        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0 && ("plain".equals(style) || !hasShade)) continue;
            int cx = gx0;
            for (int i = 0; i < text.length(); i++) {
                String[] g = glyphs[i];
                for (int ty = 0; ty < height; ty++) {
                    int gy = (ty * font.h + font.h / 2) / height;
                    int col = fills.length > 0 ? fills[ty * fills.length / height] : fill;
                    String row = g[gy];
                    for (int gx = 0; gx < row.length(); gx++) {
                        if (row.charAt(gx) != '#') continue;
                        for (int b = 0; b <= bold; b++) {
                            int X = cx + gx + b, Y = gy0 + ty;
                            if (pass == 1) cv.put(X, Y, col);
                            else if ("shadow".equals(style)) cv.put(X + 1, Y + 1, shade);
                            else for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) cv.put(X + dx, Y + dy, shade);
                        }
                    }
                }
                cx += widths[i] + spacing;
            }
        }
    }

    private static int parseColor(String s)
    {
        s = s.startsWith("#") ? s.substring(1) : s;
        return Color.rgb(Integer.parseInt(s.substring(0, 2), 16), Integer.parseInt(s.substring(2, 4), 16),
                Integer.parseInt(s.substring(4, 6), 16));
    }

    private static String stripAccents(String s)
    {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    static String outName(String path)
    {
        return path.replace("Graphics/Pictures/", "").replace('/', '_').replace(' ', '_')
                .toLowerCase(Locale.ROOT) + ".png";
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

    // ------------------------------------------------------------------ fonts

    private static Map<String, Font> loadFonts(Context ctx) throws IOException
    {
        Map<String, Font> fonts = new HashMap<>();
        Font cur = null;
        Character ch = null;
        java.util.ArrayList<String> rows = new java.util.ArrayList<>();
        for (String line : readAsset(ctx, "pixelfonts.txt").split("\n")) {
            line = line.replace("\r", "");
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.startsWith("@font")) {
                if (cur != null && ch != null) cur.glyphs.put(ch, rows.toArray(new String[0]));
                String[] p = line.split("\\s+");
                cur = new Font();
                cur.w = Integer.parseInt(p[2]);
                cur.h = Integer.parseInt(p[3]);
                fonts.put(p[1], cur);
                ch = null;
                rows.clear();
            } else if (line.startsWith("char:")) {
                if (cur != null && ch != null) cur.glyphs.put(ch, rows.toArray(new String[0]));
                ch = line.length() > 5 ? line.charAt(5) : ' ';
                rows.clear();
            } else {
                rows.add(line);
            }
        }
        if (cur != null && ch != null) cur.glyphs.put(ch, rows.toArray(new String[0]));
        return fonts;
    }

    private static String readAsset(Context ctx, String name) throws IOException
    {
        try (InputStream in = ctx.getAssets().open(name)) {
            return Util.readText(in);
        }
    }
}

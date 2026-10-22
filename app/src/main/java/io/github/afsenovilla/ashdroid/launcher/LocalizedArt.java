package io.github.afsenovilla.ashdroid.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Spanish versions of game images that have English words drawn in them
 * (e.g. the FIGHT / BAG / RUN battle buttons). They are generated on the
 * device from the game's own image — same button art, colours and pixel font,
 * only the word changes — into ashdroid_es/ in the game folder. The original
 * files are never modified: the runtime patch (ashdroid_patches.rb) loads
 * these instead only while the game language is Spanish.
 *
 * Nothing from the game is shipped in the APK, only these "recipes".
 */
final class LocalizedArt
{
    static final String OUT_DIR = "ashdroid_es";

    /** A sprite sheet of word buttons: one row per word, same word in every column. */
    private static final class GridRecipe
    {
        final String path;      // relative to the game folder, without extension
        final String outName;   // file name in OUT_DIR
        final int cellW, cellH; // cell size in the image's pixels
        final String[] rows;    // Spanish word per row (null = keep)

        GridRecipe(String path, String outName, int cellW, int cellH, String... rows)
        {
            this.path = path; this.outName = outName; this.cellW = cellW; this.cellH = cellH; this.rows = rows;
        }
    }

    // Pokémon Essentials v19 battle command buttons (Fire Ash layout: 2 columns x 12 rows)
    private static final GridRecipe[] RECIPES = {
        new GridRecipe("Graphics/Pictures/Battle/cursor_command", "battle_cursor_command.png", 130, 46,
                "LUCHAR", null, "MOCHILA", "HUIR", "LLAMAR", "BALL", "ROCA", "CEBO", "BALL",
                "CANCELAR", "ÁNIMO", "CAMPO"),
    };

    private LocalizedArt() {}

    static void generate(File game)
    {
        Typeface font = findFont(game, "power clear.ttf");
        File out = new File(game, OUT_DIR);
        for (GridRecipe r : RECIPES) {
            try {
                File src = findImage(game, r.path);
                if (src == null) continue;
                File dst = new File(out, r.outName);
                Bitmap orig = BitmapFactory.decodeFile(src.getAbsolutePath());
                if (orig == null) continue;
                Bitmap res = relabelGrid(orig, r, font);
                orig.recycle();
                if (res == null) continue;
                out.mkdirs();
                try (OutputStream os = new FileOutputStream(dst)) {
                    res.compress(Bitmap.CompressFormat.PNG, 100, os);
                }
                res.recycle();
            } catch (IOException | RuntimeException ignored) {
                // cosmetic only
            }
        }
    }

    // ------------------------------------------------------------------ core

    private static int lum(int c)
    {
        return Color.red(c) + Color.green(c) + Color.blue(c);
    }

    /** 2 if the art is drawn with 2x2 "big pixels" (common in Essentials), else 1. */
    private static int pixelScale(Bitmap b)
    {
        int w = b.getWidth(), h = b.getHeight();
        if (w % 2 != 0 || h % 2 != 0) return 1;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int c = b.getPixel(x, y);
                if (b.getPixel(x + 1, y) != c || b.getPixel(x, y + 1) != c || b.getPixel(x + 1, y + 1) != c) return 1;
            }
        }
        return 2;
    }

    private static Bitmap relabelGrid(Bitmap orig, GridRecipe r, Typeface font)
    {
        int s = pixelScale(orig);
        int W = orig.getWidth() / s, H = orig.getHeight() / s;
        Bitmap im = Bitmap.createScaledBitmap(orig, W, H, false).copy(Bitmap.Config.ARGB_8888, true);
        int cw = r.cellW / s, ch = r.cellH / s;
        int cols = Math.max(1, W / cw);

        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setTypeface(font != null ? font : Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        Canvas c = new Canvas(im);

        for (int row = 0; row < r.rows.length && (row + 1) * ch <= H; row++) {
            String label = r.rows[row];
            if (label == null) continue;
            for (int col = 0; col < cols; col++) {
                int x0 = col * cw, y0 = row * ch;
                // Old text = dark (outline) pixels away from the button border
                int tx0 = Integer.MAX_VALUE, tx1 = -1, ty0 = Integer.MAX_VALUE, ty1 = -1, dark = 0;
                for (int y = y0 + 4; y < y0 + ch - 4; y++) {
                    for (int x = x0 + 4; x < x0 + cw - 4; x++) {
                        int px = im.getPixel(x, y);
                        if (Color.alpha(px) > 200 && lum(px) < 200) {
                            if (dark == 0) dark = px;
                            if (x < tx0) tx0 = x; if (x > tx1) tx1 = x;
                            if (y < ty0) ty0 = y; if (y > ty1) ty1 = y;
                        }
                    }
                }
                if (tx1 < 0) continue;
                int light = 0;
                for (int y = ty0; y <= ty1; y++)
                    for (int x = tx0; x <= tx1; x++) {
                        int px = im.getPixel(x, y);
                        if (light == 0 || lum(px) > lum(light)) light = px;
                    }
                // Erase it with the button's own background, row by row (keeps gradients)
                for (int y = ty0; y <= ty1; y++) {
                    int bg = im.getPixel(x0 + 6, y);
                    for (int x = tx0; x <= tx1; x++) im.setPixel(x, y, bg);
                }

                // Font size: same cap height as the old text (minus 1px outline top+bottom)
                int cap = ty1 - ty0 + 1 - 2;
                Rect b = new Rect();
                float size = 4;
                for (; size < 64; size++) {
                    p.setTextSize(size);
                    p.getTextBounds("H", 0, 1, b);
                    if (b.height() >= cap) break;
                }
                int maxW = cw - 12;
                int spacing = 1;
                while (textWidth(p, label, spacing) + 2 > maxW && (spacing > 0 || size > 4)) {
                    if (spacing > 0) spacing = 0; else { size -= 1; p.setTextSize(size); }
                }
                p.getTextBounds("H", 0, 1, b);
                int tw = textWidth(p, label, spacing);
                int ox = x0 + (cw - tw) / 2;
                int baseline = ty0 + 1 - b.top; // top of caps at ty0+1

                p.setColor(dark != 0 ? dark : Color.rgb(0x40, 0x40, 0x40));
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        if (dx != 0 || dy != 0) drawSpaced(c, p, label, ox + dx, baseline + dy, spacing);
                p.setColor(light != 0 ? light : Color.WHITE);
                drawSpaced(c, p, label, ox, baseline, spacing);
            }
        }
        Bitmap out = Bitmap.createScaledBitmap(im, W * s, H * s, false);
        if (out != im) im.recycle();
        return out;
    }

    private static int textWidth(Paint p, String s, int spacing)
    {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += Math.round(p.measureText(s, i, i + 1));
            if (i < s.length() - 1) w += spacing;
        }
        return w;
    }

    private static void drawSpaced(Canvas c, Paint p, String s, int x, int y, int spacing)
    {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            c.drawText(s, i, i + 1, cx, y, p);
            cx += Math.round(p.measureText(s, i, i + 1)) + spacing;
        }
    }

    // ------------------------------------------------------------------ files

    /** Case-insensitive lookup of "Graphics/Pictures/x" + .png */
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

    private static Typeface findFont(File game, String name)
    {
        File fonts = Util.findChildIgnoreCase(game, "Fonts");
        if (fonts == null) return null;
        File f = Util.findChildIgnoreCase(fonts, name);
        if (f == null) {
            File[] list = fonts.listFiles();
            if (list != null) for (File x : list) {
                String n = x.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".ttf") && n.contains("power")) { f = x; break; }
            }
        }
        try {
            return f == null ? null : Typeface.createFromFile(f);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

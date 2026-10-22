package io.github.afsenovilla.ashdroid.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Redraws the key icons of the Pokémon Essentials controls screen
 * (Graphics/Pictures/Controls help/*.png) as the device's own buttons:
 * the arrow keys become a D-pad, USE/BACK/ACTION/SPECIAL become the mapped
 * face/shoulder buttons, and F1/F8 (not available on Android) are hidden.
 *
 * Originals are backed up once to ashdroid_backup/ inside the game folder and
 * always used as the size reference, so this can run on every launch.
 */
final class ControlsArt
{
    private static final String BACKUP_DIR = "ashdroid_backup";

    // Look of the device buttons (dark buttons with white letters, like most handhelds)
    private static final int BTN_FILL = Color.rgb(0x2A, 0x2A, 0x30);
    private static final int BTN_EDGE = Color.rgb(0x10, 0x10, 0x14);
    private static final int BTN_HILITE = Color.rgb(0x55, 0x55, 0x60);
    private static final int BTN_TEXT = Color.WHITE;

    private ControlsArt() {}

    /** label: text drawn on the button for each action, e.g. "A", "L1". */
    static void generate(File game, String use, String back, String action, String special)
    {
        Typeface font = findGameFont(game);
        File dir = findControlsDir(game);
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        File backupDir = new File(game, BACKUP_DIR + "/" + relative(game, dir));
        for (File f : files) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".png")) continue;
            String base = name.substring(0, name.length() - 4);

            Kind kind = classify(base);
            if (kind == null) continue;

            try {
                File backup = new File(backupDir, f.getName());
                if (!backup.exists()) {
                    backupDir.mkdirs();
                    Util.copy(f, backup);
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(backup.getAbsolutePath(), o);
                if (o.outWidth <= 0 || o.outHeight <= 0) continue;

                // Preferred: reuse the game's own key-cap art (same colours, border, font)
                // and only replace the text. Falls back to a generic drawing.
                Bitmap bmp = null;
                Bitmap orig = BitmapFactory.decodeFile(backup.getAbsolutePath());
                if (orig != null) {
                    String label = kind == Kind.USE ? use : kind == Kind.BACK ? back
                            : kind == Kind.ACTION ? action : kind == Kind.SPECIAL ? special : null;
                    if (kind == Kind.DPAD) bmp = restyleAsDpad(orig);
                    else if (label != null) bmp = relabel(orig, mainLabel(label), font);
                    else if (kind == Kind.HIDE) bmp = null;
                    orig.recycle();
                }
                if (bmp == null) {
                    bmp = Bitmap.createBitmap(o.outWidth, o.outHeight, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bmp);
                    switch (kind) {
                        case DPAD: drawDpad(c, o.outWidth, o.outHeight); break;
                        case USE: drawButton(c, o.outWidth, o.outHeight, use); break;
                        case BACK: drawButton(c, o.outWidth, o.outHeight, back); break;
                        case ACTION: drawButton(c, o.outWidth, o.outHeight, action); break;
                        case SPECIAL: drawButton(c, o.outWidth, o.outHeight, special); break;
                        case HIDE: default: break; // transparent
                    }
                }
                try (OutputStream os = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                }
                bmp.recycle();
            } catch (IOException | RuntimeException ignored) {
                // cosmetic only
            }
        }
    }

    private enum Kind { DPAD, USE, BACK, ACTION, SPECIAL, HIDE }

    private static Kind classify(String base)
    {
        if (base.contains("bg") || base.contains("background")) return null;
        if (base.contains("arrow") || base.contains("dir")) return Kind.DPAD;
        if (base.contains("special")) return Kind.SPECIAL;
        if (base.contains("action")) return Kind.ACTION;
        if (base.contains("back")) return Kind.BACK;
        if (base.contains("use")) return Kind.USE;
        if (base.matches(".*\\bf\\d+.*") || base.matches(".*_f\\d+.*") || base.matches("f\\d+.*")) return Kind.HIDE;
        return null;
    }

    private static File findControlsDir(File game)
    {
        File graphics = Util.findChildIgnoreCase(game, "Graphics");
        if (graphics == null) return null;
        File pictures = Util.findChildIgnoreCase(graphics, "Pictures");
        if (pictures == null) return null;
        File[] children = pictures.listFiles();
        if (children == null) return null;
        for (File c : children) {
            String n = c.getName().toLowerCase(Locale.ROOT);
            if (c.isDirectory() && n.contains("control")) return c;
        }
        return null;
    }

    private static String relative(File root, File f)
    {
        String r = root.getAbsolutePath(), p = f.getAbsolutePath();
        return p.startsWith(r) ? p.substring(r.length()).replaceFirst("^/", "") : f.getName();
    }

    // ------------------------------------------------- game-style key caps

    private static String mainLabel(String label)
    {
        String main = label.split("/")[0].trim();
        int sp = main.indexOf(' ');
        return sp > 0 ? main.substring(0, sp) : main;
    }

    /** First .ttf/.otf in the game's Fonts folder, preferring Pokémon-style fonts. */
    private static Typeface findGameFont(File game)
    {
        File fonts = Util.findChildIgnoreCase(game, "Fonts");
        File[] list = fonts == null ? null : fonts.listFiles();
        if (list == null) return null;
        File best = null;
        for (File f : list) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (!(n.endsWith(".ttf") || n.endsWith(".otf"))) continue;
            boolean preferred = n.contains("power") || n.contains("pokemon") || n.contains("pkmn");
            if (best == null || preferred) best = f;
            if (preferred && n.contains("clear")) { best = f; break; }
        }
        try {
            return best == null ? null : Typeface.createFromFile(best);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int dist(int a, int b)
    {
        return Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b))
                + Math.abs(Color.blue(a) - Color.blue(b));
    }

    /** Most frequent opaque colour among px[] (optionally ignoring colours close to `exclude`). */
    private static int modeColor(int[] px, int exclude, int minDist)
    {
        java.util.HashMap<Integer, Integer> count = new java.util.HashMap<>();
        int best = 0, bestN = -1;
        for (int c : px) {
            if (Color.alpha(c) < 200) continue;
            int key = c | 0xFF000000;
            if (exclude != 0 && dist(key, exclude) < minDist) continue;
            Integer n = count.get(key);
            n = n == null ? 1 : n + 1;
            count.put(key, n);
            if (n > bestN) { bestN = n; best = key; }
        }
        return bestN < 0 ? 0 : best;
    }

    /**
     * Keeps the game's key-cap (shape, colours, shadow) and replaces its text
     * with `label`, drawn in the cap's text colour with the game's font.
     */
    private static Bitmap relabel(Bitmap full, String label, Typeface font)
    {
        // Work at the art's native pixel size (Essentials art is often drawn with 2x2 pixels)
        int scale = pixelScale(full);
        Bitmap orig = scale == 1 ? full
                : Bitmap.createScaledBitmap(full, full.getWidth() / scale, full.getHeight() / scale, false);
        int w = orig.getWidth(), h = orig.getHeight();
        Bitmap bmp = orig.copy(Bitmap.Config.ARGB_8888, true);
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        // Opaque bounding box = the key cap
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (Color.alpha(px[y * w + x]) > 200) {
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) return null;
        int capW = maxX - minX + 1, capH = maxY - minY + 1;

        int face = modeColor(px, 0, 0);
        if (face == 0) return null;

        // Inner area (skip border and bottom shadow)
        // Inside the border (2px) on the sides; skip the top highlight and bottom shadow
        int mx = 2, myTop = Math.max(3, capH / 7), myBot = Math.max(4, capH / 4);
        int ix0 = minX + mx, ix1 = maxX - mx, iy0 = minY + myTop, iy1 = maxY - myBot;
        if (ix1 <= ix0 || iy1 <= iy0) return null;

        // Text pixels = inner pixels that differ from the face colour
        int tMinY = h, tMaxY = -1;
        java.util.ArrayList<Integer> textPx = new java.util.ArrayList<>();
        for (int y = iy0; y <= iy1; y++) for (int x = ix0; x <= ix1; x++) {
            int c = px[y * w + x];
            if (Color.alpha(c) > 200 && dist(c, face) > 60) {
                textPx.add(c);
                if (y < tMinY) tMinY = y; if (y > tMaxY) tMaxY = y;
                px[y * w + x] = face; // erase
            }
        }
        int[] tp = new int[textPx.size()];
        for (int i = 0; i < tp.length; i++) tp[i] = textPx.get(i);
        int textColor = tp.length > 0 ? modeColor(tp, 0, 0) : Color.rgb(0x60, 0x60, 0x70);
        int shadowColor = tp.length > 0 ? modeColor(tp, textColor, 40) : 0;
        int textH = tMaxY >= tMinY ? (tMaxY - tMinY + 1) : (iy1 - iy0) * 2 / 3;
        bmp.setPixels(px, 0, w, 0, 0, w, h);

        // Single buttons (A, B, X, Y…) get a square key cap: keep the cap's left and
        // right ends (rounded corners, border, shadow) and drop the middle.
        if (label.length() <= 2 && capW > capH + 2) {
            int newW = Math.max(capH, 8);
            int half = newW / 2;
            int dstX0 = minX + (capW - newW) / 2;
            Bitmap sq = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int[] col = new int[h];
            for (int i = 0; i < newW; i++) {
                int srcX = i < half ? minX + i : maxX - (newW - 1 - i);
                bmp.getPixels(col, 0, 1, srcX, 0, 1, h);
                sq.setPixels(col, 0, 1, dstX0 + i, 0, 1, h);
            }
            bmp.recycle();
            bmp = sq;
            int m = ix0 - minX;
            ix0 = dstX0 + m;
            ix1 = dstX0 + newW - 1 - m;
        }

        // Draw the new label with the 5x7 pixel font (crisp at this size), centred
        // where the old text was, in the key's own text and shadow colours.
        String txt = label.toUpperCase(Locale.ROOT);
        int availW = ix1 - ix0 + 1;
        int ps = Math.max(1, Math.min((textH + 1) / (PixelFont.H + 1), (availW - 4) / Math.max(1, PixelFont.width(txt, 1))));
        if (PixelFont.width(txt, ps) > availW) ps = 1;
        int tw = PixelFont.width(txt, ps), th = PixelFont.H * ps;
        int centerY = tMaxY >= tMinY ? (tMinY + tMaxY + 1) / 2 : (iy0 + iy1 + 1) / 2;
        int tx = ix0 + (availW - tw) / 2;
        int ty = centerY - th / 2 - (shadowColor != 0 ? 0 : 0);
        PixelFont.draw(bmp, txt, tx, ty, ps, textColor, shadowColor);
        if (scale == 1) return bmp;
        Bitmap up = Bitmap.createScaledBitmap(bmp, w * scale, h * scale, false);
        bmp.recycle();
        if (orig != full) orig.recycle();
        return up;
    }

    /**
     * Turns the arrow-keys cluster into a D-pad using the same palette as the
     * game's key caps (face, border, arrow colour sampled from the original).
     */
    private static Bitmap restyleAsDpad(Bitmap orig)
    {
        int w = orig.getWidth(), h = orig.getHeight();
        int[] px = new int[w * h];
        orig.getPixels(px, 0, w, 0, 0, w, h);
        int face = modeColor(px, 0, 0);
        if (face == 0) return null;
        int arrow = modeColor(px, face, 60);
        // Border: darkest frequent opaque colour
        int border = arrow;
        int darkest = 765;
        java.util.HashMap<Integer, Integer> cnt = new java.util.HashMap<>();
        for (int c : px) if (Color.alpha(c) > 200) {
            int k = c | 0xFF000000;
            Integer n = cnt.get(k);
            cnt.put(k, n == null ? 1 : n + 1);
        }
        for (java.util.Map.Entry<Integer, Integer> e : cnt.entrySet()) {
            int k = e.getKey();
            int lum = Color.red(k) + Color.green(k) + Color.blue(k);
            if (e.getValue() > px.length / 200 && lum < darkest) { darkest = lum; border = k; }
        }
        if (arrow == 0) arrow = border;

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        int s = Math.min(w, h) - 2;
        int arm = Math.max(5, (s / 3) | 1);
        int half = s / 2;
        int cx = w / 2, cy = h / 2;
        int a2 = arm / 2;

        Paint p = new Paint();
        p.setAntiAlias(false);
        // shadow/border
        p.setColor(border);
        c.drawRect(cx - a2 - 1, cy - half, cx + a2 + 2, cy + half + 2, p);
        c.drawRect(cx - half, cy - a2 - 1, cx + half + 2, cy + a2 + 2, p);
        // face
        p.setColor(face);
        c.drawRect(cx - a2, cy - half + 1, cx + a2 + 1, cy + half, p);
        c.drawRect(cx - half + 1, cy - a2, cx + half, cy + a2 + 1, p);
        // arrows
        p.setColor(arrow);
        int t = Math.max(2, arm / 4);
        int d = half - arm / 2;
        pixelTriangle(c, p, cx, cy - d, t, 0);
        pixelTriangle(c, p, cx, cy + d, t, 2);
        pixelTriangle(c, p, cx - d, cy, t, 3);
        pixelTriangle(c, p, cx + d, cy, t, 1);
        return bmp;
    }

    /** Pixel-art triangle made of rectangles. dir: 0 up, 1 right, 2 down, 3 left. */
    private static void pixelTriangle(Canvas c, Paint p, int x, int y, int size, int dir)
    {
        for (int i = 0; i < size; i++) {
            int len = i; // half-width of this row
            switch (dir) {
                case 0: c.drawRect(x - len, y - size / 2 + i, x + len + 1, y - size / 2 + i + 1, p); break;
                case 2: c.drawRect(x - len, y + size / 2 - i, x + len + 1, y + size / 2 - i + 1, p); break;
                case 3: c.drawRect(x - size / 2 + i, y - len, x - size / 2 + i + 1, y + len + 1, p); break;
                case 1: default: c.drawRect(x + size / 2 - i, y - len, x + size / 2 - i + 1, y + len + 1, p); break;
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    private static Paint fill(int color)
    {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    /** Round button for a single letter (A/B/X/Y), pill for longer labels (L1, R2, START…). */
    private static void drawButton(Canvas c, int w, int h, String label)
    {
        if (label == null || label.isEmpty()) return;
        // Combined labels ("B / START"): draw the main button
        String main = label.split("/")[0].trim();
        if (main.contains(" ")) main = main.substring(0, main.indexOf(' '));

        float pad = Math.max(1f, Math.min(w, h) * 0.06f);
        float size = Math.min(h, w) - 2 * pad;
        float cy = h / 2f;
        RectF r;
        if (main.length() <= 1) {
            float cx = w / 2f;
            r = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            float edge = Math.max(1f, size * 0.07f);
            c.drawOval(r, fill(BTN_EDGE));
            RectF in = new RectF(r.left + edge, r.top + edge, r.right - edge, r.bottom - edge * 1.6f);
            c.drawOval(in, fill(BTN_FILL));
            Paint hi = fill(BTN_HILITE);
            hi.setAlpha(110);
            c.drawArc(new RectF(in.left + edge, in.top + edge, in.right - edge, in.bottom - edge),
                    200, 140, false, stroke(hi, Math.max(1f, size * 0.05f)));
        } else {
            float bw = Math.min(w - 2 * pad, size * (0.75f + 0.55f * main.length()));
            float bh = size * 0.8f;
            float cx = w / 2f;
            r = new RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
            float rad = bh * 0.35f;
            c.drawRoundRect(r, rad, rad, fill(BTN_EDGE));
            float edge = Math.max(1f, bh * 0.08f);
            c.drawRoundRect(new RectF(r.left + edge, r.top + edge, r.right - edge, r.bottom - edge * 1.6f),
                    rad, rad, fill(BTN_FILL));
        }

        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(BTN_TEXT);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        t.setTextAlign(Paint.Align.CENTER);
        float ts = main.length() <= 1 ? r.height() * 0.6f : r.height() * 0.55f;
        t.setTextSize(ts);
        while (t.measureText(main) > r.width() * 0.8f && ts > 6) {
            ts -= 1;
            t.setTextSize(ts);
        }
        Paint.FontMetrics fm = t.getFontMetrics();
        float ty = r.centerY() - (fm.ascent + fm.descent) / 2 - r.height() * 0.03f;
        c.drawText(main, r.centerX(), ty, t);
    }

    private static Paint stroke(Paint p, float width)
    {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        return p;
    }

    /** D-pad cross with arrows. */
    private static void drawDpad(Canvas c, int w, int h)
    {
        float s = Math.min(w, h) * 0.94f;
        float cx = w / 2f, cy = h / 2f;
        float arm = s / 3f;
        float half = s / 2f;
        float edge = Math.max(1f, s * 0.035f);

        Path cross = crossPath(cx, cy, half, arm / 2f);
        c.drawPath(cross, fill(BTN_EDGE));
        Path inner = crossPath(cx, cy, half - edge, arm / 2f - edge);
        c.drawPath(inner, fill(BTN_FILL));

        Paint a = fill(BTN_TEXT);
        float t = arm * 0.32f;
        float d = half - arm * 0.45f;
        drawTriangle(c, a, cx, cy - d, t, 0);
        drawTriangle(c, a, cx, cy + d, t, 180);
        drawTriangle(c, a, cx - d, cy, t, 270);
        drawTriangle(c, a, cx + d, cy, t, 90);
    }

    private static Path crossPath(float cx, float cy, float half, float armHalf)
    {
        Path p = new Path();
        p.moveTo(cx - armHalf, cy - half);
        p.lineTo(cx + armHalf, cy - half);
        p.lineTo(cx + armHalf, cy - armHalf);
        p.lineTo(cx + half, cy - armHalf);
        p.lineTo(cx + half, cy + armHalf);
        p.lineTo(cx + armHalf, cy + armHalf);
        p.lineTo(cx + armHalf, cy + half);
        p.lineTo(cx - armHalf, cy + half);
        p.lineTo(cx - armHalf, cy + armHalf);
        p.lineTo(cx - half, cy + armHalf);
        p.lineTo(cx - half, cy - armHalf);
        p.lineTo(cx - armHalf, cy - armHalf);
        p.close();
        return p;
    }

    /** Triangle pointing up rotated by deg (0 = up, 90 = right…). */
    private static void drawTriangle(Canvas c, Paint p, float x, float y, float size, float deg)
    {
        c.save();
        c.rotate(deg, x, y);
        Path t = new Path();
        t.moveTo(x, y - size);
        t.lineTo(x + size, y + size * 0.6f);
        t.lineTo(x - size, y + size * 0.6f);
        t.close();
        c.drawPath(t, p);
        c.restore();
    }

    /** 2 if the art is drawn with 2x2 "big pixels" (common in Essentials), else 1. */
    static int pixelScale(Bitmap b)
    {
        int w = b.getWidth(), h = b.getHeight();
        if (w % 2 != 0 || h % 2 != 0) return 1;
        for (int y = 0; y < h; y += 2)
            for (int x = 0; x < w; x += 2) {
                int c = b.getPixel(x, y);
                if (b.getPixel(x + 1, y) != c || b.getPixel(x, y + 1) != c || b.getPixel(x + 1, y + 1) != c) return 1;
            }
        return 2;
    }
}

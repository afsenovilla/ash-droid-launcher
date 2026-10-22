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

                Bitmap bmp = Bitmap.createBitmap(o.outWidth, o.outHeight, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                switch (kind) {
                    case DPAD: drawDpad(c, o.outWidth, o.outHeight); break;
                    case USE: drawButton(c, o.outWidth, o.outHeight, use); break;
                    case BACK: drawButton(c, o.outWidth, o.outHeight, back); break;
                    case ACTION: drawButton(c, o.outWidth, o.outHeight, action); break;
                    case SPECIAL: drawButton(c, o.outWidth, o.outHeight, special); break;
                    case HIDE: default: break; // transparent
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
}

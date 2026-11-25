package io.github.afsenovilla.ashdroid.companion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pixel art drawn by code for the second screen: the buttons, cards and lettering of the approved design
 * (docs/ds-screen). Everything is drawn on a small canvas of "art pixels" that the view enlarges by a whole
 * factor. No Android classes: the desktop preview uses this same code.
 *
 * A canvas is described by a list of items (see {@link #render}); the game-side patch writes them.
 */
public final class DsArt
{
    /** Corner cut per row (rows 0..r) of a rounded rectangle of radius r, as in the approved mock-ups. */
    private static final int[][] CORNERS = {
        {0},
        {1, 0}, {1, 0, 0}, {2, 1, 0, 0}, {3, 1, 1, 0, 0}, {3, 2, 1, 0, 0, 0}, {4, 3, 2, 1, 0, 0, 0},
        {5, 3, 2, 1, 1, 0, 0, 0}, {6, 4, 3, 2, 1, 1, 0, 0, 0}, {7, 5, 3, 2, 2, 1, 1, 0, 0, 0},
        {7, 5, 4, 3, 2, 1, 1, 0, 0, 0, 0}, {8, 6, 5, 3, 3, 2, 1, 1, 0, 0, 0, 0},
        {9, 7, 5, 4, 3, 2, 2, 1, 1, 0, 0, 0, 0}, {10, 7, 6, 5, 4, 3, 2, 1, 1, 1, 0, 0, 0, 0},
        {11, 8, 7, 5, 4, 3, 3, 2, 1, 1, 1, 0, 0, 0, 0},
    };

    private static final Map<String, Map<String, String[]>> FONTS = new HashMap<>();

    private DsArt() {}

    // ---- lettering (assets/pixelfonts.txt) ----

    public static synchronized void loadFonts(InputStream in) throws IOException
    {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        Map<String, String[]> font = null;
        String ch = null;
        List<String> rows = new ArrayList<>();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("@font")) {
                if (font != null && ch != null) font.put(ch, rows.toArray(new String[0]));
                String[] parts = line.split(" ");
                font = new HashMap<>();
                FONTS.put(parts[1], font);
                ch = null;
                rows = new ArrayList<>();
            } else if (font == null || line.startsWith("//")) {
                continue;
            } else if (line.startsWith("char:")) {
                if (ch != null) font.put(ch, rows.toArray(new String[0]));
                ch = line.substring(5);
                rows = new ArrayList<>();
            } else if (ch != null && !line.isEmpty()) {
                rows.add(line);
            }
        }
        if (font != null && ch != null) font.put(ch, rows.toArray(new String[0]));
    }

    public static boolean fontsLoaded()
    {
        return FONTS.containsKey("cmd");
    }

    private static String[] glyph(Map<String, String[]> font, String ch)
    {
        String[] g = font.get(ch);
        return g != null ? g : font.get(ch.toUpperCase(Locale.ROOT));
    }

    private static List<String[]> glyphs(String fontName, String text)
    {
        Map<String, String[]> font = FONTS.get(fontName);
        if (font == null) return null;
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String[] g = glyph(font, new String(Character.toChars(cp)));
            if (g == null || g.length == 0) return null;
            out.add(g);
            i += Character.charCount(cp);
        }
        return out;
    }

    private static int textWidth(List<String[]> gs, int k)
    {
        int w = 0;
        for (String[] g : gs) w += g[0].length() + 1;
        return w * k - k;
    }

    /**
     * The lettering for a label that must fit in maxWidth art pixels: the normal one, the narrow one if it
     * does not fit, or null when a letter is missing (the caller then writes it with the game's font).
     */
    private static List<String[]> lettering(String text, int k, int maxWidth)
    {
        List<String[]> gs = glyphs("cmd", text);
        if (gs == null) return null;
        if (maxWidth > 0 && textWidth(gs, k) > maxWidth) {
            List<String[]> narrow = glyphs("cmd4", text);
            if (narrow != null) return narrow;
        }
        return gs;
    }

    public static boolean canWrite(String text)
    {
        return glyphs("cmd", text) != null;
    }

    // ---- shapes ----

    static boolean[] rounded(int w, int h, int r)
    {
        boolean[] m = new boolean[w * h];
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int y = 0; y < h; y++) {
            int edge = Math.min(y, h - 1 - y);
            int cut = 0;
            if (edge < r) {
                if (r < CORNERS.length) {
                    cut = CORNERS[r][edge];
                } else {
                    while ((cut - r) * (cut - r) + (edge - r) * (edge - r) > r * r + r - 0.5) cut++;
                }
            }
            for (int x = cut; x < w - cut; x++) m[y * w + x] = true;
        }
        return m;
    }

    /** Shrinks a shape by n pixels on every side, corners included, so borders keep an even thickness. */
    static boolean[] inset(boolean[] m, int w, int h, int n)
    {
        for (int i = 0; i < n; i++) {
            boolean[] out = new boolean[w * h];
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    boolean in = true;
                    for (int dy = -1; dy <= 1 && in; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (!m[(y + dy) * w + x + dx]) { in = false; break; }
                        }
                    }
                    out[y * w + x] = in;
                }
            }
            m = out;
        }
        return m;
    }

    private static int blend(int dst, int src, int a)
    {
        return (src * a + dst * (255 - a) + 127) / 255;
    }

    /** Paints one pixel of colour c with coverage a (0..255) over a non-premultiplied ARGB canvas. */
    private static void put(int[] px, int w, int h, int x, int y, int c, int a)
    {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int d = px[y * w + x];
        int na = blend(d >>> 24, 255, a);
        if (a == 255) { px[y * w + x] = 0xFF000000 | (c & 0xFFFFFF); return; }
        int nr = blend((d >> 16) & 255, (c >> 16) & 255, a);
        int ng = blend((d >> 8) & 255, (c >> 8) & 255, a);
        int nb = blend(d & 255, c & 255, a);
        px[y * w + x] = (na << 24) | (nr << 16) | (ng << 8) | nb;
    }

    /**
     * Label in the button lettering, centred on (cx, cy): white with a dark edge, a soft shadow below and the
     * lower part of each letter slightly shaded. Returns false (drawing nothing) if a letter is missing.
     */
    static boolean pixText(int[] px, int w, int h, String text, int cx, int cy, int k, int maxWidth, int ink, int edge)
    {
        List<String[]> gs = lettering(text, k, maxWidth);
        if (gs == null) return false;
        int tw = textWidth(gs, k), th = 9 * k;
        int lw = tw + 2, lh = th + 6 * k;
        boolean[] layer = new boolean[lw * lh];
        int x = 1;
        for (String[] g : gs) {
            int top = 3 * k - (g.length - 9) * k;
            for (int r = 0; r < g.length; r++) {
                String row = g[r];
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) != '#') continue;
                    for (int yy = 0; yy < k; yy++) {
                        for (int xx = 0; xx < k; xx++) {
                            int lx = x + c * k + xx, ly = top + r * k + yy;
                            if (lx >= 0 && ly >= 0 && lx < lw && ly < lh) layer[ly * lw + lx] = true;
                        }
                    }
                }
            }
            x += (g[0].length() + 1) * k;
        }
        int ox = (int) (cx - lw / 2.0), oy = (int) (cy - (3 * k + th / 2.0));
        int drop = k < 3 ? 1 : 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) stamp(px, w, h, layer, lw, lh, ox + dx + drop, oy + dy + drop + 1, 0, 89, 0, lh);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) stamp(px, w, h, layer, lw, lh, ox + dx, oy + dy, edge, 255, 0, lh);
            }
        }
        stamp(px, w, h, layer, lw, lh, ox, oy, ink, 255, 0, lh);
        int cut = 3 * k + (int) (th * 0.56);
        if (ink == 0xFFFFFFFF) stamp(px, w, h, layer, lw, lh, ox, oy, 0xFFC8D4EC, 255, cut, lh);
        return true;
    }

    private static void stamp(int[] px, int w, int h, boolean[] layer, int lw, int lh, int ox, int oy, int c, int a, int y0, int y1)
    {
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < lw; x++) {
                if (layer[y * lw + x]) put(px, w, h, ox + x, oy + y, c, a);
            }
        }
    }

    // ---- canvas ----

    /** A label the lettering could not write; the view writes it with the game's font instead. */
    public static final class Fallback
    {
        public final String text;
        public final int cx, cy;

        Fallback(String text, int cx, int cy) { this.text = text; this.cx = cx; this.cy = cy; }
    }

    /**
     * Draws the items on a transparent canvas of w x h art pixels and returns it as ARGB (not premultiplied).
     * Items are lists whose first element says what they are:
     *   ["rr", x0, y0, x1, y1, radius, inset, colour, row0, row1]  rounded shape shrunk by inset; rows row0..row1-1
     *                                                               of the box only (row1 = -1: all of it)
     *   ["rc", x0, y0, x1, y1, colour]                             rectangle
     *   ["el", x0, y0, x1, y1, colour, upperHalfOnly]              ellipse
     *   ["px", x, y, colour, "..#..", ...]                         pattern, one string per row
     *   ["pt", text, cx, cy, scale, maxWidth, ink, edge]           label in the button lettering
     */
    public static int[] render(List<Object> items, int w, int h, List<Fallback> fallbacks)
    {
        int[] px = new int[w * h];
        if (items == null) return px;
        for (Object o : items) {
            List<Object> it = DsJson.list(o);
            if (it == null || it.isEmpty() || !(it.get(0) instanceof String)) continue;
            String kind = (String) it.get(0);
            if ("rr".equals(kind)) {
                int x0 = DsJson.at(it, 1, 0), y0 = DsJson.at(it, 2, 0), x1 = DsJson.at(it, 3, 0), y1 = DsJson.at(it, 4, 0);
                int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
                if (bw <= 0 || bh <= 0) continue;
                boolean[] m = inset(rounded(bw, bh, DsJson.at(it, 5, 0)), bw, bh, DsJson.at(it, 6, 0));
                int c = DsJson.color(it.size() > 7 ? it.get(7) : null, 0xFF000000);
                int ra = Math.max(0, DsJson.at(it, 8, 0)), rb = DsJson.at(it, 9, -1);
                if (rb < 0 || rb > bh) rb = bh;
                for (int y = ra; y < rb; y++) {
                    for (int x = 0; x < bw; x++) {
                        if (m[y * bw + x]) put(px, w, h, x0 + x, y0 + y, c, c >>> 24);
                    }
                }
            } else if ("rc".equals(kind)) {
                int c = DsJson.color(it.size() > 5 ? it.get(5) : null, 0xFF000000);
                for (int y = DsJson.at(it, 2, 0); y <= DsJson.at(it, 4, 0); y++) {
                    for (int x = DsJson.at(it, 1, 0); x <= DsJson.at(it, 3, 0); x++) put(px, w, h, x, y, c, c >>> 24);
                }
            } else if ("el".equals(kind)) {
                int x0 = DsJson.at(it, 1, 0), y0 = DsJson.at(it, 2, 0), x1 = DsJson.at(it, 3, 0), y1 = DsJson.at(it, 4, 0);
                int c = DsJson.color(it.size() > 5 ? it.get(5) : null, 0xFF000000);
                boolean upper = DsJson.at(it, 6, 0) != 0;
                double rx = (x1 - x0 + 1) / 2.0, ry = (y1 - y0 + 1) / 2.0;
                for (int y = y0; y <= y1; y++) {
                    if (upper && y - y0 + 0.5 > ry) break;
                    for (int x = x0; x <= x1; x++) {
                        double dx = (x - x0 + 0.5 - rx) / rx, dy = (y - y0 + 0.5 - ry) / ry;
                        if (dx * dx + dy * dy <= 1.0 - 2.0 / (rx * ry)) put(px, w, h, x, y, c, c >>> 24);
                    }
                }
            } else if ("px".equals(kind)) {
                int x0 = DsJson.at(it, 1, 0), y0 = DsJson.at(it, 2, 0);
                int c = DsJson.color(it.size() > 3 ? it.get(3) : null, 0xFFFFFFFF);
                for (int r = 4; r < it.size(); r++) {
                    String row = String.valueOf(it.get(r));
                    for (int x = 0; x < row.length(); x++) {
                        if (row.charAt(x) == '#') put(px, w, h, x0 + x, y0 + r - 4, c, c >>> 24);
                    }
                }
            } else if ("pt".equals(kind)) {
                String text = String.valueOf(it.get(1));
                int cx = DsJson.at(it, 2, 0), cy = DsJson.at(it, 3, 0);
                int ink = DsJson.color(it.size() > 6 ? it.get(6) : null, 0xFFFFFFFF);
                int edge = DsJson.color(it.size() > 7 ? it.get(7) : null, 0xFF303040);
                boolean ok = pixText(px, w, h, text, cx, cy, Math.max(1, DsJson.at(it, 4, 1)), DsJson.at(it, 5, 0), ink, edge);
                if (!ok && fallbacks != null) fallbacks.add(new Fallback(text, cx, cy));
            }
        }
        return px;
    }
}

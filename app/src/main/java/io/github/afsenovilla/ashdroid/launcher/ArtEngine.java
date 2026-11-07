package io.github.afsenovilla.ashdroid.launcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-independent core of the art relabelling engine: a line-by-line port of
 * tools/art/relabel.py (the reference used to author and preview the recipes) working
 * on plain ARGB int arrays, so it gives the same pixels. Android specifics (decoding
 * and encoding PNGs, rasterising TrueType text) are supplied through {@link Host}.
 */
final class ArtEngine
{
    /** An image: w x h ARGB pixels, row by row. */
    static final class Img
    {
        final int w, h;
        final int[] px;

        Img(int w, int h, int[] px) { this.w = w; this.h = h; this.px = px; }
    }

    /** Alpha mask (0..1) of rasterised text. */
    static final class Mask
    {
        final int w, h;
        final double[] a;

        Mask(int w, int h, double[] a) { this.w = w; this.h = h; this.a = a; }
    }

    interface Host
    {
        /** A picture the user put in the game folder (e.g. "assets_E/x.png"), or null. */
        Img userImage(String rel);

        /** Text in the game's Fonts/<font>.ttf at pixel size `size`, drawn with the
         *  font's ascender line at y = size / 4 and its left edge at x = size / 2, on a
         *  canvas of (int)(size * 0.8 * max(1, length)) + 2 * size by 2 * size pixels;
         *  null if the font is missing. */
        Mask ttf(String font, int size, String text);
    }

    static final class Font
    {
        int w, h;
        final Map<String, String[]> glyphs = new HashMap<>();
    }

    private ArtEngine() {}

    // ------------------------------------------------------------------ colours

    static int a(int c) { return c >>> 24; }
    static int r(int c) { return (c >> 16) & 255; }
    static int g(int c) { return (c >> 8) & 255; }
    static int b(int c) { return c & 255; }
    static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }

    static int hexcol(String s)
    {
        if (s.startsWith("#")) s = s.substring(1);
        return argb(255, Integer.parseInt(s.substring(0, 2), 16), Integer.parseInt(s.substring(2, 4), 16),
                Integer.parseInt(s.substring(4, 6), 16));
    }

    private static final String ACC_FROM = "ÁÉÍÓÚÜÑáéíóúüñ";
    private static final String ACC_TO = "AEIOUUNaeiouun";

    static String stripAccents(String s)
    {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int k = ACC_FROM.indexOf(ch);
            sb.append(k >= 0 ? ACC_TO.charAt(k) : ch);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ canvas

    /** Image seen in "art pixels": grid x grid blocks starting at an offset. */
    static final class Canvas
    {
        final Img im;
        final int g, ox, oy, W, H, dot;

        Canvas(Img im, int g, int ox, int oy, int dot)
        {
            this.im = im; this.g = g; this.ox = ox; this.oy = oy; this.dot = dot;
            W = (im.w - ox) / g;
            H = (im.h - oy) / g;
        }

        int get(int x, int y)
        {
            return (x >= 0 && y >= 0 && x < W && y < H) ? im.px[(oy + y * g) * im.w + ox + x * g] : 0;
        }

        void put(int x, int y, int c)
        {
            if (x < 0 || y < 0 || x >= W || y >= H) return;
            for (int yy = 0; yy < g; yy++)
                for (int xx = 0; xx < g; xx++) {
                    int X = ox + x * g + xx, Y = oy + y * g + yy;
                    if (X >= im.w || Y >= im.h) continue;
                    boolean lamp = dot == 0 || (xx < dot && yy < dot);
                    im.px[Y * im.w + X] = lamp ? c : 0xFF000000;
                }
        }
    }

    // ------------------------------------------------------------------ recipes

    /** Output images for one path: lang -> entries applied in order ("es", "fr", …, then "any"). */
    static LinkedHashMap<String, List<JSONObject>> variants(JSONArray images, String path) throws JSONException
    {
        List<JSONObject> entries = new ArrayList<>();
        List<String> langs = new ArrayList<>();
        for (int i = 0; i < images.length(); i++) {
            JSONObject img = images.getJSONObject(i);
            if (!path.equals(img.getString("path"))) continue;
            entries.add(img);
            String l = img.optString("lang", "es");
            if (!langs.contains(l)) langs.add(l);
        }
        List<String> order = new ArrayList<>();
        for (String l : new String[] { "es", "fr", "de", "it" }) if (langs.contains(l)) order.add(l);
        List<String> rest = new ArrayList<>();
        for (String l : langs) if (!order.contains(l) && !"any".equals(l)) rest.add(l);
        java.util.Collections.sort(rest);
        order.addAll(rest);
        LinkedHashMap<String, List<JSONObject>> out = new LinkedHashMap<>();
        for (String l : order) {
            List<JSONObject> e = new ArrayList<>();
            for (JSONObject img : entries) {
                String il = img.optString("lang", "es");
                if (il.equals(l) || "any".equals(il)) e.add(img);
            }
            out.put(l, e);
        }
        if (langs.contains("any")) {
            List<JSONObject> e = new ArrayList<>();
            for (JSONObject img : entries) if ("any".equals(img.optString("lang", "es"))) e.add(img);
            out.put("any", e);
        }
        return out;
    }

    static String outName(String path)
    {
        return path.replace("Graphics/Pictures/", "").replace('/', '_').replace(' ', '_')
                .toLowerCase(java.util.Locale.ROOT) + ".png";
    }

    static String outFile(String path, String lang)
    {
        return ("es".equals(lang) ? "" : lang + "_") + outName(path);
    }

    /** Applies the entries to a copy of `src`. */
    static Img relabel(Img src, List<JSONObject> entries, Map<String, Font> fonts, Map<String, String> buttons,
            Host host) throws JSONException
    {
        Img res = new Img(src.w, src.h, src.px.clone());
        for (JSONObject img : entries) {
            JSONArray off = img.optJSONArray("offset");
            Canvas cv = new Canvas(res, img.optInt("grid", 1), off == null ? 0 : off.getInt(0),
                    off == null ? 0 : off.getInt(1), img.optInt("dot", 0));
            JSONArray regions = img.getJSONArray("regions");
            for (int i = 0; i < regions.length(); i++) applyRegion(cv, regions.getJSONObject(i), fonts, buttons, host);
        }
        return res;
    }

    private static String subst(String text, Map<String, String> buttons)
    {
        for (Map.Entry<String, String> e : buttons.entrySet())
            if (e.getValue() != null) text = text.replace("{" + e.getKey() + "}", e.getValue());
        return text;
    }

    static String pickChar(Font font, String ch)
    {
        String[] tries = { ch, ch.toUpperCase(java.util.Locale.ROOT), stripAccents(ch),
                stripAccents(ch).toUpperCase(java.util.Locale.ROOT) };
        for (String t : tries) if (font.glyphs.containsKey(t)) return t;
        return stripAccents(ch).toUpperCase(java.util.Locale.ROOT);
    }

    private static void pasteImage(Canvas cv, Img im, JSONArray rect) throws JSONException
    {
        int x = rect.getInt(0), y = rect.getInt(1), w = rect.getInt(2), h = rect.getInt(3);
        for (int yy = 0; yy < h; yy++)
            for (int xx = 0; xx < w; xx++)
                cv.put(x + xx, y + yy, im.px[(yy * im.h / h) * im.w + xx * im.w / w]);
    }

    static void applyRegion(Canvas cv, JSONObject r, Map<String, Font> fonts, Map<String, String> buttons, Host host)
            throws JSONException
    {
        if (r.has("tips")) { starburst(cv, r); return; }
        if (r.has("lines")) { drawHalo(cv, r, fonts, buttons); return; }
        if (r.has("image") && host != null) {
            Img pic = host.userImage(r.getString("image"));
            if (pic != null) {
                pasteImage(cv, pic, r.has("imageRect") ? r.getJSONArray("imageRect") : r.getJSONArray("rect"));
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
            else if (r.has("bg") && !r.getString("bg").isEmpty()) rowbg[yy] = hexcol(r.getString("bg"));
            else rowbg[yy] = cv.get(bgx, y + yy);
        }

        // Colour counts, most frequent first, ties in first-seen order (Python's Counter)
        LinkedHashMap<Integer, Integer> cnt = new LinkedHashMap<>();
        int tx0 = Integer.MAX_VALUE, ty0 = Integer.MAX_VALUE, tx1 = -1, ty1 = -1;
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                int c = cv.get(xx, yy);
                if (c != rowbg[yy - y] && a(c) > 0) {
                    Integer n = cnt.get(c);
                    cnt.put(c, n == null ? 1 : n + 1);
                    tx0 = Math.min(tx0, xx); tx1 = Math.max(tx1, xx);
                    ty0 = Math.min(ty0, yy); ty1 = Math.max(ty1, yy);
                }
            }
        List<Integer> common = mostCommon(cnt);
        int fill, shade = 0;
        boolean hasShade;
        if ("outline".equals(style)) {
            shade = common.size() > 0 ? common.get(0) : argb(255, 64, 64, 64);
            fill = common.size() > 1 ? common.get(1) : argb(255, 248, 248, 248);
            hasShade = true;
        } else {
            fill = common.size() > 0 ? common.get(0) : argb(255, 64, 64, 64);
            hasShade = common.size() > 1;
            if (hasShade) shade = common.get(1);
        }
        if (r.has("fill") && !r.getString("fill").isEmpty()) {
            fill = hexcol(r.getString("fill"));
            if (!"outline".equals(style)) {
                hasShade = false;
                for (int c : common) {
                    if ((c & 0xFFFFFF) == (fill & 0xFFFFFF)) continue;
                    shade = c; hasShade = true; break;
                }
            }
        }
        if (r.has("shade") && !r.getString("shade").isEmpty()) { shade = hexcol(r.getString("shade")); hasShade = true; }
        JSONArray fa = r.optJSONArray("fills");
        int[] fills = new int[fa == null ? 0 : fa.length()];
        for (int i = 0; i < fills.length; i++) fills[i] = hexcol(fa.getString(i));
        if (ty1 < 0 || "rect".equals(r.optString("anchor"))) { ty0 = y; ty1 = y + h - 1; tx0 = x; }

        if ("inpaint".equals(erase)) {
            // Only the old lettering ("ink") goes: each such pixel takes the colour of the
            // nearest non-ink pixel on its row (keeps stripes/circles).
            java.util.HashSet<Integer> ink = new java.util.HashSet<>();
            JSONArray il = r.optJSONArray("ink");
            if (il != null) for (int i = 0; i < il.length(); i++) ink.add(hexcol(il.getString(i)) & 0xFFFFFF);
            JSONArray bs = r.optJSONArray("bgset");
            int[] keep = new int[bs == null ? 0 : bs.length()];
            for (int i = 0; i < keep.length; i++) keep[i] = hexcol(bs.getString(i));
            int[] row = new int[3 * w];
            boolean[] isInk = new boolean[3 * w];
            for (int yy = y; yy < y + h; yy++) {
                for (int i = 0; i < 3 * w; i++) {
                    row[i] = cv.get(x - w + i, yy);
                    if (keep.length > 0) {
                        boolean far = true;
                        for (int k : keep) {
                            int d = Math.max(Math.abs(r(row[i]) - r(k)), Math.max(Math.abs(g(row[i]) - g(k)), Math.abs(b(row[i]) - b(k))));
                            if (d <= 6) { far = false; break; }
                        }
                        isInk[i] = far;
                    } else {
                        isInk[i] = ink.contains(row[i] & 0xFFFFFF);
                    }
                }
                for (int i = w; i < 2 * w; i++) {
                    if (!isInk[i]) continue;
                    for (int dd = 1; dd <= w; dd++) {
                        if (i - dd >= 0 && !isInk[i - dd]) { cv.put(x + i - w, yy, row[i - dd]); break; }
                        if (i + dd < 3 * w && !isInk[i + dd]) { cv.put(x + i - w, yy, row[i + dd]); break; }
                    }
                }
            }
        } else if ("lerp".equals(erase)) {
            // Gradient backgrounds: each row blends from the colour at bgx to the one at bgx2
            int bx2 = r.getInt("bgx2");
            for (int yy = y; yy < y + h; yy++) {
                int ca = cv.get(bgx, yy), cb = cv.get(bx2, yy);
                for (int xx = x; xx < x + w; xx++) {
                    double t = Math.min(1.0, Math.max(0.0, (xx - bgx) / (double) (bx2 - bgx)));
                    cv.put(xx, yy, argb(lerp(a(ca), a(cb), t), lerp(r(ca), r(cb), t), lerp(g(ca), g(cb), t), lerp(b(ca), b(cb), t)));
                }
            }
        } else if (!"none".equals(erase)) {
            for (int yy = y; yy < y + h; yy++)
                for (int xx = x; xx < x + w; xx++) cv.put(xx, yy, rowbg[yy - y]);
        }

        if (r.has("ttf")) {
            drawTtf(cv, r, buttons, host, fill, hasShade ? shade : null);
            return;
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

        String raw = subst(r.optString("text", ""), buttons);
        List<String> names = new ArrayList<>();
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
        int n = raw.length(), spacing = 1, tw;
        for (int fi = 0; fi < names.size(); fi++) {
            font = fonts.get(names.get(fi));
            if (font == null) return;
            String[] blank = new String[font.h];
            Arrays.fill(blank, repeat('.', font.w));
            glyphs = new String[n][];
            widths = new int[n];
            boolean covered = true;
            for (int i = 0; i < n; i++) {
                String ch = pickChar(font, String.valueOf(raw.charAt(i)));
                String[] gl = font.glyphs.get(ch);
                if (gl == null && !" ".equals(ch)) covered = false;
                glyphs[i] = gl == null ? blank : gl;
                widths[i] = glyphs[i][0].length() + bold;
            }
            spacing = r.optInt("spacing", 1);
            tw = sum(widths) + spacing * (n - 1) + extra;
            if (fi == names.size() - 1 || (covered && tw <= w)) break;
        }
        while (true) {
            tw = sum(widths) + spacing * (n - 1) + extra;
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
        int sdx = r.optInt("sdx", 1), sdy = r.optInt("sdy", 1);
        boolean hasCorner = r.has("corner") && !r.getString("corner").isEmpty();
        int corner = hasCorner ? hexcol(r.getString("corner")) : 0;

        int[] passes = hasCorner && "outline".equals(style) ? new int[] { -1, 0, 1 } : new int[] { 0, 1 };
        for (int pass : passes) {
            if (pass == 0 && ("plain".equals(style) || !hasShade)) continue;
            int cx = gx0;
            for (int i = 0; i < n; i++) {
                String[] gl = glyphs[i];
                int top = gl.length - font.h; // accent rows drawn above the line, unscaled
                for (int ty = -top; ty < height; ty++) {
                    int gy = ty < 0 ? top + ty : top + (ty * font.h + font.h / 2) / height;
                    int col = fills.length > 0 ? fills[Math.max(0, ty) * fills.length / height] : fill;
                    String row = gl[gy];
                    for (int gx = 0; gx < row.length(); gx++) {
                        if (row.charAt(gx) != '#') continue;
                        for (int bb = 0; bb <= bold; bb++) {
                            int X = cx + gx + bb, Y = gy0 + ty;
                            if (pass == 1) cv.put(X, Y, col);
                            else if (pass == -1) {
                                cv.put(X - 1, Y - 1, corner); cv.put(X + 1, Y - 1, corner);
                                cv.put(X - 1, Y + 1, corner); cv.put(X + 1, Y + 1, corner);
                            } else if ("shadow".equals(style)) cv.put(X + sdx, Y + sdy, shade);
                            else if (hasCorner) {
                                cv.put(X, Y, shade); cv.put(X - 1, Y, shade); cv.put(X + 1, Y, shade);
                                cv.put(X, Y - 1, shade); cv.put(X, Y + 1, shade);
                            } else
                                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) cv.put(X + dx, Y + dy, shade);
                        }
                    }
                }
                cx += widths[i] + spacing;
            }
        }
    }

    private static int lerp(int p, int q, double t) { return (int) Math.round(p + (q - p) * t); }

    private static int sum(int[] v) { int s = 0; for (int x : v) s += x; return s; }

    private static String repeat(char c, int n) { char[] a = new char[n]; Arrays.fill(a, c); return new String(a); }

    private static List<Integer> mostCommon(LinkedHashMap<Integer, Integer> cnt)
    {
        List<Map.Entry<Integer, Integer>> e = new ArrayList<>(cnt.entrySet());
        // stable sort: equal counts keep first-seen order
        java.util.Collections.sort(e, (p, q) -> Integer.compare(q.getValue(), p.getValue()));
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> x : e) out.add(x.getKey());
        return out;
    }

    // ---------------------------------------------------------------- shared maths

    private static double lanczos(double x)
    {
        if (x == 0) return 1.0;
        if (-3.0 < x && x < 3.0) {
            double px = Math.PI * x;
            return 3.0 * Math.sin(px) * Math.sin(px / 3.0) / (px * px);
        }
        return 0.0;
    }

    /** Per output index: first source index and normalised weights (Pillow's LANCZOS). */
    private static double[][] lanczosWeights(int sn, int dn, int[] lo)
    {
        double scale = sn / (double) dn;
        double fs = Math.max(scale, 1.0);
        double support = 3.0 * fs;
        double[][] res = new double[dn][];
        for (int i = 0; i < dn; i++) {
            double centre = (i + 0.5) * scale;
            int l = Math.max(0, (int) (centre - support + 0.5));
            int hi = Math.min(sn, (int) (centre + support + 0.5));
            double[] ws = new double[Math.max(0, hi - l)];
            double t = 0;
            for (int j = l; j < hi; j++) { ws[j - l] = lanczos((j - centre + 0.5) / fs); t += ws[j - l]; }
            if (t != 0) for (int k = 0; k < ws.length; k++) ws[k] /= t;
            lo[i] = l;
            res[i] = ws;
        }
        return res;
    }

    /** Separable Lanczos-3 resample of premultiplied RGBA (4 doubles per pixel). */
    static double[] lanczosResample(double[] src, int sw, int sh, int dw, int dh)
    {
        double[] tmp = src;
        if (dw != sw) {
            int[] lo = new int[dw];
            double[][] wx = lanczosWeights(sw, dw, lo);
            tmp = new double[dw * sh * 4];
            for (int y = 0; y < sh; y++)
                for (int i = 0; i < dw; i++) {
                    double[] ws = wx[i];
                    for (int k = 0; k < ws.length; k++) {
                        int s = (y * sw + lo[i] + k) * 4, d = (y * dw + i) * 4;
                        for (int c = 0; c < 4; c++) tmp[d + c] += src[s + c] * ws[k];
                    }
                }
        }
        double[] out = tmp;
        if (dh != sh) {
            int[] lo = new int[dh];
            double[][] wy = lanczosWeights(sh, dh, lo);
            out = new double[dw * dh * 4];
            for (int j = 0; j < dh; j++)
                for (int x = 0; x < dw; x++) {
                    double[] ws = wy[j];
                    for (int k = 0; k < ws.length; k++) {
                        int s = ((lo[j] + k) * dw + x) * 4, d = (j * dw + x) * 4;
                        for (int c = 0; c < 4; c++) out[d + c] += tmp[s + c] * ws[k];
                    }
                }
        }
        if (out == src) out = src.clone();
        for (int i = 0; i < out.length; i++) out[i] = Math.min(1.0, Math.max(0.0, out[i]));
        return out;
    }

    /** Max filter with a square (2 * rad + 1) window. */
    static double[] dilate(double[] m, int w, int h, int rad)
    {
        if (rad <= 0) return m;
        double[] tmp = new double[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = 0;
                for (int xx = Math.max(0, x - rad); xx < Math.min(w, x + rad + 1); xx++) v = Math.max(v, m[y * w + xx]);
                tmp[y * w + x] = v;
            }
        double[] out = new double[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = 0;
                for (int yy = Math.max(0, y - rad); yy < Math.min(h, y + rad + 1); yy++) v = Math.max(v, tmp[yy * w + x]);
                out[y * w + x] = v;
            }
        return out;
    }

    /** Max filter with a round window (a pen of radius rad). */
    static double[] dilateDisc(double[] m, int w, int h, int rad)
    {
        if (rad <= 0) return m;
        List<int[]> offs = new ArrayList<>();
        for (int dy = -rad; dy <= rad; dy++)
            for (int dx = -rad; dx <= rad; dx++) if (dx * dx + dy * dy <= rad * rad) offs.add(new int[] { dx, dy });
        double[] out = new double[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = 0;
                for (int[] o : offs) {
                    int X = x + o[0], Y = y + o[1];
                    if (X >= 0 && X < w && Y >= 0 && Y < h && m[Y * w + X] > v) v = m[Y * w + X];
                }
                out[y * w + x] = v;
            }
        return out;
    }

    /** Separable gaussian blur, edges clamped. */
    static double[] blur(double[] ch, int w, int h, double sigma)
    {
        if (sigma <= 0) return ch;
        int rad = Math.max(1, (int) (sigma * 3 + 0.999));
        double[] k = new double[2 * rad + 1];
        double t = 0;
        for (int i = -rad; i <= rad; i++) { k[i + rad] = Math.exp(-(i * i) / (2.0 * sigma * sigma)); t += k[i + rad]; }
        for (int i = 0; i < k.length; i++) k[i] /= t;
        double[] tmp = new double[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double s = 0;
                for (int i = -rad; i <= rad; i++) s += ch[y * w + Math.min(w - 1, Math.max(0, x + i))] * k[i + rad];
                tmp[y * w + x] = s;
            }
        double[] out = new double[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double s = 0;
                for (int i = -rad; i <= rad; i++) s += tmp[Math.min(h - 1, Math.max(0, y + i)) * w + x] * k[i + rad];
                out[y * w + x] = s;
            }
        return out;
    }

    private static int clampRound(double v) { return (int) (Math.min(255, Math.max(0, v)) + 0.5); }

    /** Draws a premultiplied RGBA pixel (0..1) over the canvas. */
    static void blendOver(Canvas cv, int x, int y, double pr, double pg, double pb, double pa)
    {
        if (pa <= 0.0) return;
        if (pa > 1.0) pa = 1.0;
        int u = cv.get(x, y);
        double ua = a(u) / 255.0;
        double oa = pa + ua * (1 - pa);
        if (oa <= 0) return;
        cv.put(x, y, argb((int) (oa * 255 + 0.5),
                clampRound((pr * 255.0 + r(u) * ua * (1 - pa)) / oa),
                clampRound((pg * 255.0 + g(u) * ua * (1 - pa)) / oa),
                clampRound((pb * 255.0 + b(u) * ua * (1 - pa)) / oa)));
    }

    // ------------------------------------------------------------------ TrueType

    static final int TTF_K = 4;
    private static final Map<String, int[]> H_BOX = new HashMap<>();

    private static int[] inkBox(Mask m)
    {
        int x0 = m.w, y0 = m.h, x1 = -1, y1 = -1;
        for (int y = 0; y < m.h; y++)
            for (int x = 0; x < m.w; x++)
                if (m.a[y * m.w + x] > 0.0) {
                    x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
                }
        return x1 >= 0 ? new int[] { x0, y0, x1, y1 } : null;
    }

    /** Smooth lettering with one of the game's TrueType fonts (see relabel.py draw_ttf). */
    static void drawTtf(Canvas cv, JSONObject r, Map<String, String> buttons, Host host, int fill, Integer shade)
            throws JSONException
    {
        final int K = TTF_K;
        if (host == null) return;
        String text = subst(r.optString("text", ""), buttons);
        if (text.isEmpty()) return;
        JSONArray rect = r.getJSONArray("rect");
        int x = rect.getInt(0), y = rect.getInt(1), w = rect.getInt(2), h = rect.getInt(3);
        double cap = r.has("cap") ? r.getDouble("cap") : h - 2;
        String fname = r.getString("ttf");
        int size = -1;
        int[] hb = null;
        for (int s = (int) (cap * K * 2); s > 5; s--) {
            String key = fname + "|" + s;
            int[] bx;
            if (H_BOX.containsKey(key)) bx = H_BOX.get(key);
            else {
                Mask m = host.ttf(fname, s, "H");
                if (m == null) return;
                bx = inkBox(m);
                H_BOX.put(key, bx);
            }
            if (bx != null && bx[3] - bx[1] + 1 <= cap * K) { size = s; hb = bx; break; }
        }
        if (size < 0) return;
        Mask tm = host.ttf(fname, size, text);
        if (tm == null) return;
        int[] tb = inkBox(tm);
        if (tb == null) return;
        int fat = (int) (r.optDouble("fat", 0) * K);
        int gw = tb[2] - tb[0] + 1 + 2 * fat;
        int BW = gw + K, BH = h * K + 2 * K;
        int oy = Math.floorDiv(h * K - (hb[3] - hb[1] + 1), 2) - hb[1] + r.optInt("dy", 0) * K;
        double[] glyph = new double[BW * BH];
        for (int yy = 0; yy < tm.h; yy++) {
            int Y = yy + oy;
            if (Y < 0 || Y >= BH) continue;
            for (int xx = tb[0]; xx <= tb[2]; xx++) {
                double v = tm.a[yy * tm.w + xx];
                if (v > 0) glyph[Y * BW + xx - tb[0] + fat] = v;
            }
        }
        double[] sh = new double[BW * BH];
        boolean useShade = shade != null && !"plain".equals(r.optString("style", "shadow"));
        if (useShade)
            for (int yy = 0; yy < BH - K; yy++)
                for (int xx = 0; xx < BW - K; xx++) sh[(yy + K) * BW + xx + K] = glyph[yy * BW + xx];
        glyph = dilateDisc(glyph, BW, BH, fat);
        double fr = r(fill) / 255.0, fg = g(fill) / 255.0, fb = b(fill) / 255.0;
        double sr = useShade ? r(shade) / 255.0 : 0, sg = useShade ? g(shade) / 255.0 : 0, sb = useShade ? b(shade) / 255.0 : 0;
        double[] big = new double[BW * BH * 4];
        for (int i = 0; i < BW * BH; i++) {
            double fa = glyph[i], sa = sh[i] * (1 - glyph[i]);
            big[i * 4] = fr * fa + (sa != 0 ? sr * sa : 0);
            big[i * 4 + 1] = fg * fa + (sa != 0 ? sg * sa : 0);
            big[i * 4 + 2] = fb * fa + (sa != 0 ? sb * sa : 0);
            big[i * 4 + 3] = fa + sa;
        }
        int maxw = (w - 2) * K;
        if (BW > maxw) {
            big = lanczosResample(big, BW, BH, maxw, BH);
            BW = maxw;
        }
        int dw = Math.max(1, BW / K), dh = Math.max(1, BH / K);
        double[] small = lanczosResample(big, BW, BH, dw, dh);
        int ox = ("left".equals(r.optString("align", "center")) ? x + 1 : x + Math.floorDiv(w - dw, 2)) + r.optInt("dx", 0);
        for (int yy = 0; yy < dh; yy++)
            for (int xx = 0; xx < dw; xx++) {
                int i = (yy * dw + xx) * 4;
                blendOver(cv, ox + xx, y + yy, small[i], small[i + 1], small[i + 2], small[i + 3]);
            }
    }

    // ------------------------------------------------------------ star-burst badge

    /** Explosion star (see relabel.py starburst). */
    static void starburst(Canvas cv, JSONObject r) throws JSONException
    {
        final int K = TTF_K;
        JSONArray rect = r.getJSONArray("rect");
        int x = rect.getInt(0), y = rect.getInt(1), w = rect.getInt(2), h = rect.getInt(3);
        JSONArray ce = r.optJSONArray("centre");
        double cx = ce != null ? ce.getDouble(0) : w / 2.0, cy = ce != null ? ce.getDouble(1) : h / 2.0;
        JSONArray tj = r.getJSONArray("tips");
        List<double[]> tips = new ArrayList<>();
        for (int i = 0; i < tj.length(); i++) tips.add(new double[] { tj.getJSONArray(i).getDouble(0), tj.getJSONArray(i).getDouble(1) });
        final double fcx = cx, fcy = cy;
        java.util.Collections.sort(tips, (p, q) -> Double.compare(Math.atan2(p[1] - fcy, p[0] - fcx), Math.atan2(q[1] - fcy, q[0] - fcx)));
        double rin = r.getDouble("rin");
        int W = w * K, H = h * K;
        JSONArray lj = r.getJSONArray("layers");
        double[] R = new double[W * H], G = new double[W * H], B = new double[W * H], A = new double[W * H];
        int base = hexcol(lj.getJSONArray(0).getString(1));
        for (int i = 0; i < W * H; i++) { R[i] = r(base) / 255.0; G[i] = g(base) / 255.0; B[i] = b(base) / 255.0; }
        for (int li = 0; li < lj.length(); li++) {
            double sc = lj.getJSONArray(li).getDouble(0);
            int col = hexcol(lj.getJSONArray(li).getString(1));
            int nt = tips.size();
            double[][] pts = new double[2 * nt][];
            for (int i = 0; i < nt; i++) {
                double[] t = tips.get(i), nx = tips.get((i + 1) % nt);
                double a1 = Math.atan2(t[1] - cy, t[0] - cx), a2 = Math.atan2(nx[1] - cy, nx[0] - cx);
                if (a2 < a1) a2 += 2 * Math.PI;
                double am = (a1 + a2) / 2;
                pts[2 * i] = new double[] { (cx + (t[0] - cx) * sc) * K, (cy + (t[1] - cy) * sc) * K };
                pts[2 * i + 1] = new double[] { (cx + rin * sc * Math.cos(am)) * K, (cy + rin * sc * Math.sin(am)) * K };
            }
            int n = pts.length;
            for (int yy = 0; yy < H; yy++) {
                double py = yy + 0.5;
                List<Double> xs = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    double[] p1 = pts[i], p2 = pts[(i + 1) % n];
                    if ((p1[1] <= py) != (p2[1] <= py)) xs.add(p1[0] + (py - p1[1]) * (p2[0] - p1[0]) / (p2[1] - p1[1]));
                }
                java.util.Collections.sort(xs);
                for (int j = 0; j + 1 < xs.size(); j += 2) {
                    int a0 = Math.max(0, (int) Math.ceil(xs.get(j) - 0.5));
                    int b0 = Math.min(W - 1, (int) Math.ceil(xs.get(j + 1) - 0.5) - 1);
                    for (int xx = a0; xx <= b0; xx++) {
                        int k = yy * W + xx;
                        R[k] = r(col) / 255.0; G[k] = g(col) / 255.0; B[k] = b(col) / 255.0;
                        if (li == 0) A[k] = 1.0;
                    }
                }
            }
        }
        double sig = r.optDouble("blur", 0) * K;
        R = blur(R, W, H, sig); G = blur(G, W, H, sig); B = blur(B, W, H, sig);
        double[] big = new double[W * H * 4];
        for (int i = 0; i < W * H; i++) {
            big[i * 4] = R[i] * A[i]; big[i * 4 + 1] = G[i] * A[i]; big[i * 4 + 2] = B[i] * A[i]; big[i * 4 + 3] = A[i];
        }
        double[] small = lanczosResample(big, W, H, w, h);
        for (int yy = 0; yy < h; yy++)
            for (int xx = 0; xx < w; xx++) {
                int i = (yy * w + xx) * 4;
                double al = small[i + 3];
                if (al <= 0) cv.put(x + xx, y + yy, 0);
                else cv.put(x + xx, y + yy, argb((int) (al * 255 + 0.5), clampRound(small[i] / al * 255),
                        clampRound(small[i + 1] / al * 255), clampRound(small[i + 2] / al * 255)));
            }
    }

    /** Pixel-font lettering with outline and soft halo (see relabel.py draw_halo). */
    static void drawHalo(Canvas cv, JSONObject r, Map<String, Font> fonts, Map<String, String> buttons) throws JSONException
    {
        JSONArray rect = r.getJSONArray("rect");
        int x = rect.getInt(0), y = rect.getInt(1), w = rect.getInt(2), h = rect.getInt(3);
        Font font = fonts.get(r.optString("font", "card"));
        if (font == null) return;
        int fill = hexcol(r.optString("fill", "#ffffff"));
        int rcol = hexcol(r.optString("rcol", r.optString("fill", "#ffffff")));
        int spacing = r.optInt("spacing", 1);
        int[] col = new int[w * h];
        boolean[] has = new boolean[w * h];
        double[] mask = new double[w * h];
        JSONArray lines = r.getJSONArray("lines"), tops = r.getJSONArray("tops");
        for (int li = 0; li < Math.min(lines.length(), tops.length()); li++) {
            String line = subst(lines.getString(li), buttons);
            int top = tops.getInt(li);
            String[][] gs = new String[line.length()][];
            int[] widths = new int[line.length()];
            for (int i = 0; i < line.length(); i++) {
                gs[i] = font.glyphs.get(pickChar(font, String.valueOf(line.charAt(i))));
                if (gs[i] == null) return;
                int mw = 0;
                for (String rw : gs[i]) mw = Math.max(mw, rw.length());
                widths[i] = mw;
            }
            int gx = Math.floorDiv(w - (sum(widths) + spacing * (widths.length - 1)), 2);
            for (int i = 0; i < gs.length; i++) {
                String[] gl = gs[i];
                int y0 = top - (gl.length - font.h);
                for (int dy = 0; dy < gl.length; dy++) {
                    String row = gl[dy];
                    for (int dx = 0; dx < row.length(); dx++) {
                        if (row.charAt(dx) != '#') continue;
                        int X = gx + dx, Y = y0 + dy;
                        if (X < 0 || X >= w || Y < 0 || Y >= h) continue;
                        boolean right = dx + 1 < row.length() && row.charAt(dx + 1) == '#';
                        col[Y * w + X] = right ? fill : rcol;
                        has[Y * w + X] = true;
                        mask[Y * w + X] = 1.0;
                    }
                }
                gx += widths[i] + spacing;
            }
        }
        double[] halo = blur(dilate(mask, w, h, 2), w, h, r.optDouble("halo_blur", 0.8));
        double[] edge = dilate(mask, w, h, 1);
        int hc = hexcol(r.optString("halo", "#191919"));
        for (int yy = 0; yy < h; yy++)
            for (int xx = 0; xx < w; xx++) {
                double al = Math.min(1.0, halo[yy * w + xx]);
                blendOver(cv, x + xx, y + yy, r(hc) / 255.0 * al, g(hc) / 255.0 * al, b(hc) / 255.0 * al, al);
                if (edge[yy * w + xx] > 0) cv.put(x + xx, y + yy, 0xFF000000);
                if (has[yy * w + xx]) cv.put(x + xx, y + yy, col[yy * w + xx]);
            }
    }

    // ------------------------------------------------------------------ fonts

    static Map<String, Font> parseFonts(String txt)
    {
        Map<String, Font> fonts = new HashMap<>();
        Font cur = null;
        String ch = null;
        List<String> rows = new ArrayList<>();
        for (String line : txt.split("\n")) {
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
                ch = line.substring(5);
                rows.clear();
            } else {
                rows.add(line);
            }
        }
        if (cur != null && ch != null) cur.glyphs.put(ch, rows.toArray(new String[0]));
        return fonts;
    }
}

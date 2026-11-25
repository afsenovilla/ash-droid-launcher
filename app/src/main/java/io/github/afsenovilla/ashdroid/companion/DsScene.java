package io.github.afsenovilla.ashdroid.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the second screen shows and how it reacts to touch.
 *
 * The game-side patch (ashdroid_patches.rb, modules AshDroidDS / AshDroidDSUI) describes each screen as a
 * list of drawing operations in a 1240x1080 space: the game's own images (file, source and target
 * rectangles, whole-number enlargements), texts in the game's font, canvases of code-drawn pixel art
 * ({@link DsArt}), touch areas with the command they send back, scrolling lists and draggable groups.
 * This class draws that list through {@link Gfx} and turns touches into commands; it knows nothing about
 * the game. No Android classes, so the desktop preview (docs/ds-screen) runs this same code.
 */
public final class DsScene
{
    public static final int W = 1240, H = 1080;
    private static final int SLOP = 18;
    private static final int[] BOUNCE_MS = { 260, 110, 200, 110 };
    private static final int[] BOUNCE_DY = { 0, -1, -2, -1 };

    /** The platform's drawing calls. Coordinates are in the 1240x1080 space. */
    public interface Gfx
    {
        void fill(int x, int y, int w, int h, int argb);

        /** Part of an image file, enlarged without smoothing. fx: "" or an effect, see {@link #applyFx}. */
        void image(String file, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh, String fx, int alpha);

        /** Width and height of an image file, or null if it cannot be read. */
        int[] imageSize(String file);

        /** Pixels drawn by code (ARGB, not premultiplied), each one scale x scale; key identifies them for caching. */
        void pixels(String key, int[] argb, int w, int h, int dx, int dy, int scale, int alpha);

        float textWidth(String s, int size, int font);

        void text(String s, int x, int baseline, int size, int font, int argb);

        void clip(int x, int y, int w, int h);

        void unclip();
    }

    private Map<String, Object> state;
    private List<Object> ops = new ArrayList<>();
    private final Map<String, Integer> scroll = new HashMap<>();
    private final Map<String, String> seen = new HashMap<>();
    /** Where each draggable view (the map) has been moved to: key -> {x, y} of its content at the view's corner. */
    private final Map<String, int[]> pans = new HashMap<>();
    private Map<String, Object> panOp;
    private boolean panning;
    private final Map<String, int[]> artCache = new LinkedHashMap<String, int[]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, int[]> e) { return size() > 160; }
    };
    private final Map<String, List<DsArt.Fallback>> artFallbacks = new HashMap<>();
    /** Cache keys of the canvases of the current state, by the identity of their item lists. */
    private final Map<List<Object>, String> artKeys = new java.util.IdentityHashMap<>();

    // touch
    private Hit pressed;
    private boolean moved, dragging, scrolling, barDrag;
    private int downX, downY, lastX, lastY;
    private Map<String, Object> scrollOp;
    private long bounceStart = System.nanoTime();

    private static final class Hit
    {
        Map<String, Object> op;
        int x, y, w, h;          // on screen
        Map<String, Object> inScroll;
    }

    public void setState(Map<String, Object> s)
    {
        state = s;
        artKeys.clear();
        List<Object> l = s == null ? null : DsJson.list(s.get("ops"));
        ops = l == null ? new ArrayList<>() : l;
        if (pressed != null || dragging || scrolling) {
            // keep an ongoing scroll; anything else belongs to the previous screen
            if (!scrolling && !barDrag) { pressed = null; dragging = false; }
            else scrollOp = findScroll(DsJson.str(scrollOp, "key", ""));
        }
    }

    public boolean hasState()
    {
        return state != null;
    }

    public String font(int i)
    {
        List<Object> f = state == null ? null : DsJson.list(state.get("fonts"));
        return f != null && i < f.size() && f.get(i) instanceof String ? (String) f.get(i) : null;
    }

    /** True while something on screen moves by itself (the selected button's bounce). */
    public boolean animating()
    {
        return hasBounce(ops);
    }

    private boolean hasBounce(List<Object> list)
    {
        for (Object o : list) {
            Map<String, Object> op = DsJson.map(o);
            if (op == null) continue;
            if (DsJson.flag(op, "bn")) return true;
            List<Object> kids = DsJson.list(op.get("ops"));
            if (kids != null && hasBounce(kids)) return true;
        }
        return false;
    }

    // ---- drawing ----

    public void draw(Gfx g)
    {
        if (state == null) {
            g.fill(0, 0, W, H, 0xFF000000);
            return;
        }
        int dragGroup = dragging && pressed != null ? DsJson.num(pressed.op, "g", -1) : -1;
        drawOps(g, ops, 0, 0, dragGroup, false);
        if (dragGroup >= 0) {
            // the held group, on top, following the finger
            drawOps(g, ops, lastX - downX, lastY - downY, dragGroup, true);
        }
    }

    private void drawOps(Gfx g, List<Object> list, int ox, int oy, int dragGroup, boolean onlyDragged)
    {
        for (Object o : list) {
            Map<String, Object> op = DsJson.map(o);
            if (op == null) continue;
            String kind = DsJson.str(op, "o", "");
            boolean inGroup = dragGroup >= 0 && DsJson.num(op, "g", -1) == dragGroup;
            if (onlyDragged != inGroup) continue;
            // pieces shown only while a touch area is (or is not) under the finger: the pressed look of buttons
            // made of the game's images
            if (op.get("dn_of") != null && !isDown(DsJson.num(op, "dn_of", -1))) continue;
            if (op.get("up_of") != null && isDown(DsJson.num(op, "up_of", -1))) continue;
            switch (kind) {
                case "fill": {
                    List<Object> r = DsJson.list(op.get("r"));
                    g.fill(ox + DsJson.at(r, 0, 0), oy + DsJson.at(r, 1, 0), DsJson.at(r, 2, 0), DsJson.at(r, 3, 0),
                            DsJson.color(op.get("c"), 0xFF000000));
                    break;
                }
                case "stripes": {
                    List<Object> r = DsJson.list(op.get("r"));
                    int x = DsJson.at(r, 0, 0), y = DsJson.at(r, 1, 0), w = DsJson.at(r, 2, W), h = DsJson.at(r, 3, H);
                    int n = DsJson.num(op, "n", 12);
                    int c1 = DsJson.color(op.get("c1"), 0xFFCECEE8), c2 = DsJson.color(op.get("c2"), 0xFFC8C8E4);
                    for (int yy = 0; yy < h; yy += n) {
                        g.fill(ox + x, oy + y + yy, w, Math.min(n, h - yy), ((yy / n) % 2 == 0) ? c1 : c2);
                    }
                    break;
                }
                case "img":
                    drawImage(g, op, ox, oy);
                    break;
                case "tile": {
                    String f = DsJson.str(op, "f", null);
                    int[] size = f == null ? null : g.imageSize(f);
                    List<Object> r = DsJson.list(op.get("r")), at = DsJson.list(op.get("at"));
                    int k = DsJson.num(op, "k", 2);
                    if (size == null || size[0] <= 0 || size[1] <= 0) break;
                    int x0 = DsJson.at(r, 0, 0), y0 = DsJson.at(r, 1, 0), x1 = x0 + DsJson.at(r, 2, W), y1 = y0 + DsJson.at(r, 3, H);
                    g.clip(x0, y0, x1 - x0, y1 - y0);
                    for (int ty = DsJson.at(at, 1, y0); ty < y1; ty += size[1] * k) {
                        for (int tx = DsJson.at(at, 0, x0); tx < x1; tx += size[0] * k) {
                            g.image(f, 0, 0, size[0], size[1], tx, ty, size[0] * k, size[1] * k, "", 255);
                        }
                    }
                    g.unclip();
                    break;
                }
                case "txt":
                    drawText(g, op, ox, oy);
                    break;
                case "art":
                    drawArt(g, op, ox, oy);
                    break;
                case "hb":
                    drawRow(g, op, ox, oy);
                    break;
                case "scroll":
                    if (!onlyDragged) drawScroll(g, op);
                    break;
                case "pan": {
                    List<Object> r = DsJson.list(op.get("r"));
                    List<Object> kids = DsJson.list(op.get("ops"));
                    if (r == null || kids == null) break;
                    int[] at = panAt(op);
                    g.clip(DsJson.at(r, 0, 0), DsJson.at(r, 1, 0), DsJson.at(r, 2, 0), DsJson.at(r, 3, 0));
                    drawOps(g, kids, DsJson.at(r, 0, 0) - at[0], DsJson.at(r, 1, 0) - at[1], -1, false);
                    g.unclip();
                    break;
                }
                case "hit":
                    if (pressed != null && pressed.op == op && !moved && DsJson.flag(op, "dim")) {
                        g.fill(pressed.x, pressed.y, pressed.w, pressed.h, 0x48000000);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private boolean isDown(int id)
    {
        return pressed != null && !moved && !dragging && id == DsJson.num(pressed.op, "art", -2);
    }

    private void drawImage(Gfx g, Map<String, Object> op, int ox, int oy)
    {
        String f = DsJson.str(op, "f", null);
        if (f == null) return;
        List<Object> s = DsJson.list(op.get("s")), d = DsJson.list(op.get("d"));
        int sx, sy, sw, sh;
        if (s == null) {
            int[] size = g.imageSize(f);
            if (size == null) return;
            sx = 0; sy = 0; sw = size[0]; sh = size[1];
        } else {
            sx = DsJson.at(s, 0, 0); sy = DsJson.at(s, 1, 0); sw = DsJson.at(s, 2, 0); sh = DsJson.at(s, 3, 0);
        }
        int k = DsJson.num(op, "k", 1);
        int dw = DsJson.at(d, 2, sw * k), dh = DsJson.at(d, 3, sh * k);
        if (DsJson.flag(op, "bn")) oy += bounce() * 6;
        g.image(f, sx, sy, sw, sh, ox + DsJson.at(d, 0, 0), oy + DsJson.at(d, 1, 0), dw, dh,
                DsJson.str(op, "fx", ""), DsJson.num(op, "a", 255));
    }

    private static int ascent(int size) { return (int) Math.ceil(size * 0.75); }
    private static int descent(int size) { return (int) Math.floor(size * 0.25); }

    private int fitted(Gfx g, Map<String, Object> op, String s)
    {
        int size = DsJson.num(op, "z", 52), font = DsJson.num(op, "f", 0), fit = DsJson.num(op, "fit", 0);
        while (fit > 0 && size > 26 && g.textWidth(s, size, font) > fit) size -= 13;
        return size;
    }

    private void drawText(Gfx g, Map<String, Object> op, int ox, int oy)
    {
        String s = DsJson.str(op, "s", "");
        if (s.isEmpty()) return;
        int font = DsJson.num(op, "f", 0);
        int size = fitted(g, op, s);
        if (DsJson.flag(op, "bn")) oy += bounce() * 6;
        int x = ox + DsJson.num(op, "x", 0), y = oy + DsJson.num(op, "y", 0);
        int wrap = DsJson.num(op, "ww", 0);
        if (wrap > 0) {
            int lh = DsJson.num(op, "lh", size + size / 4), max = DsJson.num(op, "ml", 99), n = 0;
            for (String line : wrapLines(g, s, size, font, wrap)) {
                if (n >= max) break;
                line(g, op, line, x, y + n * lh, size, font);
                n++;
            }
        } else {
            line(g, op, s, x, y, size, font);
        }
    }

    private List<String> wrapLines(Gfx g, String s, int size, int font, int width)
    {
        List<String> out = new ArrayList<>();
        for (String para : s.split("\n")) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (cur.length() > 0 && g.textWidth(trial, size, font) > width) {
                    out.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(trial);
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    private void line(Gfx g, Map<String, Object> op, String s, int x, int y, int size, int font)
    {
        String a = DsJson.str(op, "a", "l");
        float w = g.textWidth(s, size, font);
        if ("r".equals(a)) x = Math.round(x - w);
        else if ("m".equals(a)) x = Math.round(x - w / 2f);
        int base = "m".equals(DsJson.str(op, "v", "a")) ? y + (ascent(size) + descent(size) + 1) / 2 : y + ascent(size);
        Object shadow = op.get("k");
        if (shadow instanceof String) {
            int c = DsJson.color(shadow, 0xFF000000), o = Math.max(1, size / 13);
            g.text(s, x + o, base, size, font, c);
            g.text(s, x, base + o, size, font, c);
            g.text(s, x + o, base + o, size, font, c);
        }
        g.text(s, x, base, size, font, DsJson.color(op.get("c"), 0xFFFFFFFF));
    }

    private void drawArt(Gfx g, Map<String, Object> op, int ox, int oy)
    {
        boolean down = pressed != null && !moved && !dragging && op.get("id") != null
                && DsJson.num(op, "id", -1) == DsJson.num(pressed.op, "art", -2);
        List<Object> items = DsJson.list(op.get(down && op.get("dn") != null ? "dn" : "it"));
        if (items == null) return;
        int w = DsJson.num(op, "w", 0), h = DsJson.num(op, "h", 0), sc = DsJson.num(op, "S", 6);
        if (w <= 0 || h <= 0) return;
        String key = artKeys.get(items);
        if (key == null) {
            key = w + "x" + h + ":" + items;
            artKeys.put(items, key);
        }
        int[] px = artCache.get(key);
        if (px == null) {
            List<DsArt.Fallback> fb = new ArrayList<>();
            px = DsArt.render(items, w, h, fb);
            artCache.put(key, px);
            if (fb.isEmpty()) artFallbacks.remove(key);
            else artFallbacks.put(key, fb);
            if (artFallbacks.size() > 400) artFallbacks.clear();
        }
        int x = ox + DsJson.num(op, "x", 0), y = oy + DsJson.num(op, "y", 0);
        if (DsJson.flag(op, "bn") && !down) y += bounce() * sc;
        g.pixels(key, px, w, h, x, y, sc, DsJson.num(op, "a", 255));
        List<DsArt.Fallback> fb = artFallbacks.get(key);
        if (fb != null) {
            for (DsArt.Fallback f : fb) {
                int size = 52;
                while (size > 26 && g.textWidth(f.text, size, 0) > (w - 8) * sc) size -= 13;
                int tx = Math.round(x + f.cx * sc - g.textWidth(f.text, size, 0) / 2f);
                int base = y + f.cy * sc + Math.round(size * 0.35f) - size / 26;
                int o = size / 13;
                g.text(f.text, tx + o, base, size, 0, 0xFF303040);
                g.text(f.text, tx, base + o, size, 0, 0xFF303040);
                g.text(f.text, tx + o, base + o, size, 0, 0xFF303040);
                g.text(f.text, tx, base, size, 0, 0xFFFFFFFF);
            }
        }
    }

    private int bounce()
    {
        long ms = (System.nanoTime() - bounceStart) / 1_000_000L % 680;
        for (int i = 0; i < BOUNCE_MS.length; i++) {
            if (ms < BOUNCE_MS[i]) return BOUNCE_DY[i];
            ms -= BOUNCE_MS[i];
        }
        return 0;
    }

    /** Children side by side, the whole row centred on cx (an icon and a name, for instance). */
    private void drawRow(Gfx g, Map<String, Object> op, int ox, int oy)
    {
        List<Object> kids = DsJson.list(op.get("ops"));
        if (kids == null) return;
        int total = 0;
        int[] widths = new int[kids.size()];
        for (int i = 0; i < kids.size(); i++) {
            Map<String, Object> k = DsJson.map(kids.get(i));
            if (k == null) continue;
            if ("txt".equals(DsJson.str(k, "o", ""))) {
                String s = DsJson.str(k, "s", "");
                widths[i] = Math.round(g.textWidth(s, fitted(g, k, s), DsJson.num(k, "f", 0)));
            } else {
                widths[i] = DsJson.at(DsJson.list(k.get("d")), 2, 0);
            }
            total += widths[i] + DsJson.num(k, "gap", 0);
        }
        int x = DsJson.num(op, "cx", W / 2) - ("r".equals(DsJson.str(op, "ax", "m")) ? total : total / 2);
        for (int i = 0; i < kids.size(); i++) {
            Map<String, Object> k = DsJson.map(kids.get(i));
            if (k == null) continue;
            x += DsJson.num(k, "gap", 0);
            if ("txt".equals(DsJson.str(k, "o", ""))) drawText(g, k, ox + x, oy);
            else drawImage(g, k, ox + x, oy);
            x += widths[i];
        }
    }

    // ---- scrolling lists ----

    private int maxScroll(Map<String, Object> op)
    {
        List<Object> r = DsJson.list(op.get("r"));
        return Math.max(0, DsJson.num(op, "ch", 0) - DsJson.at(r, 3, 0));
    }

    private int offset(Map<String, Object> op)
    {
        String key = DsJson.str(op, "key", "");
        Integer v = scroll.get(key);
        int off = v == null ? 0 : v;
        // the cursor moved with the buttons: bring it into view (once per position)
        List<Object> see = DsJson.list(op.get("see"));
        if (see != null) {
            String mark = see.toString();
            if (!mark.equals(seen.get(key))) {
                seen.put(key, mark);
                int a = DsJson.at(see, 0, 0), b = DsJson.at(see, 1, 0), view = DsJson.at(DsJson.list(op.get("r")), 3, 0);
                if (a < off) off = a;
                if (b > off + view) off = b - view;
            }
        }
        off = Math.max(0, Math.min(maxScroll(op), off));
        scroll.put(key, off);
        return off;
    }

    private void drawScroll(Gfx g, Map<String, Object> op)
    {
        List<Object> r = DsJson.list(op.get("r"));
        List<Object> kids = DsJson.list(op.get("ops"));
        if (r == null || kids == null) return;
        int x = DsJson.at(r, 0, 0), y = DsJson.at(r, 1, 0), w = DsJson.at(r, 2, 0), h = DsJson.at(r, 3, 0);
        int off = offset(op);
        g.clip(x, y, w, h);
        drawOps(g, kids, x, y - off, -1, false);
        g.unclip();
        List<Object> bar = DsJson.list(op.get("bar"));
        int ch = DsJson.num(op, "ch", 0);
        if (bar != null && ch > h) {
            int bx = DsJson.at(bar, 0, 0), by = DsJson.at(bar, 1, 0), bw = DsJson.at(bar, 2, 0), bh = DsJson.at(bar, 3, 0);
            g.fill(bx, by, bw, bh, DsJson.color(op.get("bc1"), 0xFFA8A8C4));
            int th = Math.max(bw * 2, bh * h / ch), ty = by + (bh - th) * off / Math.max(1, ch - h);
            g.fill(bx, ty, bw, th, DsJson.color(op.get("bc2"), 0xFF585878));
        }
    }

    // ---- views moved with the finger in both directions (the map) ----

    private int[] panAt(Map<String, Object> op)
    {
        String key = DsJson.str(op, "key", "");
        List<Object> r = DsJson.list(op.get("r"));
        int maxX = Math.max(0, DsJson.num(op, "cw", 0) - DsJson.at(r, 2, 0));
        int maxY = Math.max(0, DsJson.num(op, "ch", 0) - DsJson.at(r, 3, 0));
        int[] at = pans.get(key);
        if (at == null) {
            at = new int[] { DsJson.num(op, "x0", 0), DsJson.num(op, "y0", 0) };
            pans.put(key, at);
        }
        at[0] = Math.max(0, Math.min(maxX, at[0]));
        at[1] = Math.max(0, Math.min(maxY, at[1]));
        return at;
    }

    private Map<String, Object> panAtPoint(int x, int y)
    {
        for (Object o : ops) {
            Map<String, Object> op = DsJson.map(o);
            if (op != null && "pan".equals(DsJson.str(op, "o", "")) && inside(DsJson.list(op.get("r")), x, y, 0, 0)) return op;
        }
        return null;
    }

    private Map<String, Object> findScroll(String key)
    {
        for (Object o : ops) {
            Map<String, Object> op = DsJson.map(o);
            if (op != null && "scroll".equals(DsJson.str(op, "o", "")) && key.equals(DsJson.str(op, "key", ""))) return op;
        }
        return null;
    }

    private Map<String, Object> scrollAt(int x, int y, boolean barOnly)
    {
        for (Object o : ops) {
            Map<String, Object> op = DsJson.map(o);
            if (op == null || !"scroll".equals(DsJson.str(op, "o", ""))) continue;
            List<Object> bar = DsJson.list(op.get("bar"));
            if (bar != null && maxScroll(op) > 0) {
                int bx = DsJson.at(bar, 0, 0), by = DsJson.at(bar, 1, 0), bw = DsJson.at(bar, 2, 0), bh = DsJson.at(bar, 3, 0);
                if (x >= bx - 36 && x < bx + bw + 36 && y >= by && y < by + bh) return op;
            }
            if (barOnly) continue;
            List<Object> r = DsJson.list(op.get("r"));
            if (inside(r, x, y, 0, 0)) return op;
        }
        return null;
    }

    private void barTo(Map<String, Object> op, int y)
    {
        List<Object> bar = DsJson.list(op.get("bar"));
        int by = DsJson.at(bar, 1, 0), bh = DsJson.at(bar, 3, 1);
        float f = Math.max(0f, Math.min(1f, (y - by) / (float) bh));
        scroll.put(DsJson.str(op, "key", ""), Math.round(f * maxScroll(op)));
    }

    // ---- touch ----

    private static boolean inside(List<Object> r, int x, int y, int ox, int oy)
    {
        if (r == null) return false;
        int rx = ox + DsJson.at(r, 0, 0), ry = oy + DsJson.at(r, 1, 0);
        return x >= rx && y >= ry && x < rx + DsJson.at(r, 2, 0) && y < ry + DsJson.at(r, 3, 0);
    }

    private Hit hitAt(int x, int y, Hit except)
    {
        return hitIn(ops, x, y, 0, 0, null, except);
    }

    private Hit hitIn(List<Object> list, int x, int y, int ox, int oy, Map<String, Object> scrollParent, Hit except)
    {
        for (int i = list.size() - 1; i >= 0; i--) {
            Map<String, Object> op = DsJson.map(list.get(i));
            if (op == null) continue;
            String kind = DsJson.str(op, "o", "");
            if ("scroll".equals(kind)) {
                List<Object> r = DsJson.list(op.get("r"));
                List<Object> kids = DsJson.list(op.get("ops"));
                if (kids != null && inside(r, x, y, 0, 0)) {
                    Hit h = hitIn(kids, x, y, DsJson.at(r, 0, 0), DsJson.at(r, 1, 0) - offset(op), op, except);
                    if (h != null) return h;
                }
            } else if ("hit".equals(kind)) {
                if (except != null && except.op == op) continue;
                List<Object> r = DsJson.list(op.get("r"));
                if (inside(r, x, y, ox, oy)) {
                    Hit h = new Hit();
                    h.op = op;
                    h.x = ox + DsJson.at(r, 0, 0);
                    h.y = oy + DsJson.at(r, 1, 0);
                    h.w = DsJson.at(r, 2, 0);
                    h.h = DsJson.at(r, 3, 0);
                    h.inScroll = scrollParent;
                    return h;
                }
            }
        }
        return null;
    }

    public void touchDown(int x, int y)
    {
        downX = lastX = x;
        downY = lastY = y;
        moved = dragging = scrolling = barDrag = false;
        scrollOp = scrollAt(x, y, true);
        if (scrollOp != null) {
            barDrag = true;
            pressed = null;
            barTo(scrollOp, y);
            return;
        }
        pressed = hitAt(x, y, null);
        scrollOp = scrollAt(x, y, false);
        panOp = pressed == null ? panAtPoint(x, y) : null;
        panning = false;
    }

    public void touchMove(int x, int y)
    {
        if (barDrag) {
            barTo(scrollOp, y);
            lastX = x; lastY = y;
            return;
        }
        if (!moved && (Math.abs(x - downX) > SLOP || Math.abs(y - downY) > SLOP)) {
            moved = true;
            if (pressed != null && pressed.op.get("drag") != null) dragging = true;
            else if (panOp != null) panning = true;
            else if (scrollOp != null && maxScroll(scrollOp) > 0) scrolling = true;
        }
        if (panning) {
            int[] at = panAt(panOp);
            at[0] -= x - lastX;
            at[1] -= y - lastY;
            panAt(panOp);
        }
        if (scrolling) {
            String key = DsJson.str(scrollOp, "key", "");
            int off = offset(scrollOp) - (y - lastY);
            scroll.put(key, Math.max(0, Math.min(maxScroll(scrollOp), off)));
        }
        lastX = x;
        lastY = y;
    }

    /** The finger left the screen; returns the command to send to the game, or null. */
    public String touchUp(int x, int y)
    {
        lastX = x;
        lastY = y;
        String cmd = null;
        if (dragging && pressed != null) {
            Hit target = hitAt(x, y, pressed);
            if (target != null && String.valueOf(pressed.op.get("drag")).equals(String.valueOf(target.op.get("drag")))) {
                cmd = pressed.op.get("drag") + ":" + DsJson.num(pressed.op, "n", 0) + ":" + DsJson.num(target.op, "n", 0);
            }
        } else if (pressed != null && !moved) {
            cmd = DsJson.str(pressed.op, "cmd", null);
        } else if (pressed == null && !moved && !barDrag && !scrolling && state != null) {
            cmd = DsJson.str(state, "tap", null);
        }
        pressed = null;
        dragging = scrolling = barDrag = moved = panning = false;
        scrollOp = null;
        panOp = null;
        return cmd;
    }

    public void touchCancel()
    {
        pressed = null;
        dragging = scrolling = barDrag = moved = panning = false;
        scrollOp = null;
        panOp = null;
    }

    // ---- image effects shared by both platforms ----

    private static float[] hsv(int c)
    {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0) h += 360f;
        }
        return new float[] { h, max == 0 ? 0 : d / max, max };
    }

    private static int rgb(float h, float s, float v)
    {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c, r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /**
     * Repaints an image in another colour: every pixel of the image's own main hue takes the target's hue, and
     * its saturation and brightness keep their proportion to the image's main colour, so shading survives.
     * Greys (outlines) and other hues (a red selection border) are left alone.
     */
    private static void tint(int[] px, int target)
    {
        Map<Integer, Integer> count = new HashMap<>();
        for (int p : px) {
            if ((p >>> 24) < 200) continue;
            float[] c = hsv(p);
            if (c[1] < 0.25f || c[2] < 0.2f) continue;
            Integer n = count.get(p & 0xFFFFFF);
            count.put(p & 0xFFFFFF, n == null ? 1 : n + 1);
        }
        int main = -1, best = 0;
        for (Map.Entry<Integer, Integer> e : count.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); main = e.getKey(); }
        }
        if (main < 0) return;
        float[] ref = hsv(main), to = hsv(target);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            if ((p >>> 24) == 0) continue;
            float[] c = hsv(p);
            float dh = Math.abs(c[0] - ref[0]);
            if (dh > 180) dh = 360 - dh;
            if (c[1] < 0.15f || dh > 40) continue;
            float s2 = Math.min(1f, c[1] * to[1] / Math.max(0.05f, ref[1]));
            float v2 = Math.min(1f, c[2] * to[2] / Math.max(0.05f, ref[2]));
            px[i] = (p & 0xFF000000) | rgb(to[0], s2, v2);
        }
    }

    /**
     * fx "tint:#rrggbb": the image repainted in that colour (see {@link #tint}).
     * fx "sil:#rrggbb": every visible pixel becomes that colour (silhouette).
     * fx "keep:#rrggbb,#rrggbb...": only pixels of those colours stay (a drawing without its background).
     * fx "dark:NN": colours multiplied by NN percent. Effects can be chained with ';'.
     */
    public static void applyFx(int[] px, String fx)
    {
        if (fx == null || fx.isEmpty()) return;
        for (String one : fx.split(";")) {
            if (one.startsWith("sil:")) {
                int c = DsJson.color(one.substring(4), 0xFF484860) & 0xFFFFFF;
                for (int i = 0; i < px.length; i++) px[i] = (px[i] >>> 24) != 0 ? 0xFF000000 | c : 0;
            } else if (one.startsWith("keep:")) {
                String[] list = one.substring(5).split(",");
                int[] keep = new int[list.length];
                for (int i = 0; i < list.length; i++) keep[i] = DsJson.color(list[i], 0) & 0xFFFFFF;
                for (int i = 0; i < px.length; i++) {
                    boolean ok = false;
                    for (int k : keep) if ((px[i] & 0xFFFFFF) == k && (px[i] >>> 24) != 0) { ok = true; break; }
                    if (!ok) px[i] = 0;
                }
            } else if (one.startsWith("tint:")) {
                tint(px, DsJson.color(one.substring(5), 0xFF2888E0));
            } else if (one.startsWith("dark:")) {
                int pct;
                try { pct = Integer.parseInt(one.substring(5)); } catch (NumberFormatException e) { continue; }
                for (int i = 0; i < px.length; i++) {
                    int p = px[i];
                    px[i] = (p & 0xFF000000) | (((p >> 16) & 255) * pct / 100 << 16) | (((p >> 8) & 255) * pct / 100 << 8)
                            | ((p & 255) * pct / 100);
                }
            }
        }
    }
}

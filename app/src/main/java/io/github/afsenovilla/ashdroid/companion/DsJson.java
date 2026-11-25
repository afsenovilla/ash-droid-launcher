package io.github.afsenovilla.ashdroid.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader for the second-screen state (objects become Map, arrays List, numbers Double).
 * Kept free of Android classes so the same drawing code runs in the desktop preview (docs/ds-screen).
 */
public final class DsJson
{
    private final String s;
    private int p;

    private DsJson(String s) { this.s = s; }

    public static Object parse(String text)
    {
        DsJson j = new DsJson(text);
        Object v = j.value();
        j.space();
        if (j.p != j.s.length()) throw new IllegalArgumentException("trailing data at " + j.p);
        return v;
    }

    private void space()
    {
        while (p < s.length() && s.charAt(p) <= ' ') p++;
    }

    private Object value()
    {
        space();
        if (p >= s.length()) throw new IllegalArgumentException("unexpected end");
        char c = s.charAt(p);
        if (c == '{') {
            Map<String, Object> m = new HashMap<>();
            p++;
            space();
            if (s.charAt(p) == '}') { p++; return m; }
            while (true) {
                space();
                String k = string();
                space();
                expect(':');
                m.put(k, value());
                space();
                if (s.charAt(p) == ',') { p++; continue; }
                expect('}');
                return m;
            }
        }
        if (c == '[') {
            List<Object> l = new ArrayList<>();
            p++;
            space();
            if (s.charAt(p) == ']') { p++; return l; }
            while (true) {
                l.add(value());
                space();
                if (s.charAt(p) == ',') { p++; continue; }
                expect(']');
                return l;
            }
        }
        if (c == '"') return string();
        if (s.startsWith("true", p)) { p += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p)) { p += 5; return Boolean.FALSE; }
        if (s.startsWith("null", p)) { p += 4; return null; }
        int a = p;
        while (p < s.length() && "+-0123456789.eE".indexOf(s.charAt(p)) >= 0) p++;
        if (a == p) throw new IllegalArgumentException("bad value at " + p);
        return Double.valueOf(s.substring(a, p));
    }

    private void expect(char c)
    {
        if (p >= s.length() || s.charAt(p) != c) throw new IllegalArgumentException("expected " + c + " at " + p);
        p++;
    }

    private String string()
    {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(p++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            c = s.charAt(p++);
            switch (c) {
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break;
                default: b.append(c);
            }
        }
    }

    // ---- typed access with defaults ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o)
    {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o)
    {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static int num(Object o, int fallback)
    {
        return o instanceof Number ? (int) Math.round(((Number) o).doubleValue()) : fallback;
    }

    public static int num(Map<String, Object> m, String key, int fallback)
    {
        return m == null ? fallback : num(m.get(key), fallback);
    }

    public static int at(List<Object> l, int i, int fallback)
    {
        return l == null || i >= l.size() ? fallback : num(l.get(i), fallback);
    }

    public static String str(Map<String, Object> m, String key, String fallback)
    {
        Object o = m == null ? null : m.get(key);
        return o instanceof String ? (String) o : fallback;
    }

    public static boolean flag(Map<String, Object> m, String key)
    {
        Object o = m == null ? null : m.get(key);
        return Boolean.TRUE.equals(o) || (o instanceof Number && ((Number) o).intValue() != 0);
    }

    /** "#rrggbb" or "#aarrggbb" to ARGB; fallback when missing or malformed. */
    public static int color(Object o, int fallback)
    {
        if (!(o instanceof String)) return fallback;
        String v = (String) o;
        try {
            if (v.length() == 7) return 0xFF000000 | Integer.parseInt(v.substring(1), 16);
            if (v.length() == 9) return (int) Long.parseLong(v.substring(1), 16);
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }
}

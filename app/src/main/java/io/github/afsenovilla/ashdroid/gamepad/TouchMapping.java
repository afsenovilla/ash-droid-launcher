package io.github.afsenovilla.ashdroid.gamepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import io.github.afsenovilla.ashdroid.R;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Which in-game action each on-screen touch button sends. Stored per button slot
 * (the slot ids match the button_* views in gamepad_layout.xml). A slot without
 * an entry keeps its original key from {@link GamepadConfig}.
 *
 * Each action is sent as a keyboard key with an unambiguous mkxp-z default
 * binding (jni/mkxp-z/src/input/keybindings.cpp).
 */
public final class TouchMapping
{
    private static final String PREFS = "ashdroid";
    private static final String KEY = "touch_mapping";
    private static final String KEY_POS = "touch_positions";

    /** Movable controls: every button slot plus the D-pad. */
    public static final String DPAD = "DPAD";

    /** Button slots in the order shown in the editor. */
    public static final String[] SLOTS = { "A", "B", "C", "X", "Y", "Z", "L", "R", "SHIFT", "CTRL", "ALT" };

    /** An action a touch button can send. */
    public static final class Option
    {
        public final String id;
        public final int keycode;      // 0 = button does nothing
        private final int labelRes;
        private final int shortRes;

        Option(String id, int keycode, int labelRes, int shortRes)
        {
            this.id = id;
            this.keycode = keycode;
            this.labelRes = labelRes;
            this.shortRes = shortRes;
        }

        public String label(Context ctx) { return ctx.getString(labelRes); }

        /** Short text drawn on the button itself. */
        public String shortLabel(Context ctx) { return ctx.getString(shortRes); }
    }

    public static final Option[] OPTIONS = {
        new Option("C", KeyEvent.KEYCODE_ENTER, R.string.action_c, R.string.touch_short_c),
        new Option("B", KeyEvent.KEYCODE_ESCAPE, R.string.action_b, R.string.touch_short_b),
        new Option("A", KeyEvent.KEYCODE_SHIFT_LEFT, R.string.action_a, R.string.touch_short_a),
        new Option("R", KeyEvent.KEYCODE_W, R.string.action_r, R.string.touch_short_r),
        new Option("L", KeyEvent.KEYCODE_Q, R.string.action_l, R.string.touch_short_l),
        new Option("Z", KeyEvent.KEYCODE_D, R.string.action_z, R.string.touch_short_z),
        new Option("X", KeyEvent.KEYCODE_A, R.string.action_x, R.string.touch_short_x),
        new Option("Y", KeyEvent.KEYCODE_S, R.string.action_y, R.string.touch_short_y),
        new Option("CTRL", KeyEvent.KEYCODE_CTRL_LEFT, R.string.touch_opt_ctrl, R.string.touch_short_ctrl),
        new Option("ALT", KeyEvent.KEYCODE_ALT_LEFT, R.string.touch_opt_alt, R.string.touch_short_alt),
        new Option("NONE", 0, R.string.touch_opt_none, R.string.touch_short_none),
    };

    private final JSONObject mMap;
    private final JSONObject mPos;

    private TouchMapping(JSONObject map, JSONObject pos) { mMap = map; mPos = pos; }

    public static TouchMapping load(Context ctx)
    {
        return new TouchMapping(json(prefs(ctx).getString(KEY, null)), json(prefs(ctx).getString(KEY_POS, null)));
    }

    private static JSONObject json(String s)
    {
        try {
            return s != null ? new JSONObject(s) : new JSONObject();
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public void save(Context ctx)
    {
        prefs(ctx).edit().putString(KEY, mMap.toString()).putString(KEY_POS, mPos.toString()).apply();
    }

    /** Back to the original actions. */
    public static void reset(Context ctx)
    {
        prefs(ctx).edit().remove(KEY).apply();
    }

    /** Back to the original positions. */
    public static void resetPositions(Context ctx)
    {
        prefs(ctx).edit().remove(KEY_POS).apply();
    }

    public boolean isDefault() { return mMap.length() == 0; }

    public boolean hasCustomPositions() { return mPos.length() > 0; }

    /**
     * Offset of a control from its layout position, as fractions of the
     * overlay's width and height (so it survives resolution changes).
     */
    public float[] offset(String control)
    {
        org.json.JSONArray a = mPos.optJSONArray(control);
        if (a == null || a.length() != 2) return new float[] { 0f, 0f };
        return new float[] { (float) a.optDouble(0, 0), (float) a.optDouble(1, 0) };
    }

    public void setOffset(String control, float fx, float fy)
    {
        try {
            mPos.put(control, new org.json.JSONArray().put((double) fx).put((double) fy));
        } catch (JSONException ignored) {
        }
    }

    /** View for a control inside an inflated gamepad_layout. */
    public static android.view.View findControl(android.view.View root, String control)
    {
        String name = DPAD.equals(control) ? "dpad" : "button_" + control;
        int id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
        return id != 0 ? root.findViewById(id) : null;
    }

    /** All movable controls, D-pad first. */
    public static String[] controls()
    {
        String[] all = new String[SLOTS.length + 1];
        all[0] = DPAD;
        System.arraycopy(SLOTS, 0, all, 1, SLOTS.length);
        return all;
    }

    /** Moves the controls of an inflated overlay to their saved positions, once it has a size. */
    public void applyPositions(final android.view.View overlay)
    {
        if (!hasCustomPositions()) return;
        overlay.post(() -> {
            if (detachControls((android.view.ViewGroup) overlay)) placeControls(overlay);
        });
    }

    /** Places detached controls at default position + saved offset. */
    public void placeControls(android.view.View overlay)
    {
        int w = overlay.getWidth(), h = overlay.getHeight();
        for (String c : controls()) {
            android.view.View v = findControl(overlay, c);
            if (v == null) continue;
            if (!(v.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams)) continue;
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            float[] o = offset(c);
            v.setTranslationX(clamp(o[0] * w, -lp.leftMargin, w - lp.leftMargin - lp.width));
            v.setTranslationY(clamp(o[1] * h, -lp.topMargin, h - lp.topMargin - lp.height));
        }
    }

    private static float clamp(float v, float min, float max)
    {
        return max < min ? min : Math.max(min, Math.min(max, v));
    }

    /**
     * Moves every control out of its row/container straight into the overlay,
     * at the same on-screen place, so it can be moved anywhere and still
     * receive touches. Returns false if the overlay has no size yet.
     */
    public static boolean detachControls(android.view.ViewGroup overlay)
    {
        int w = overlay.getWidth(), h = overlay.getHeight();
        if (w == 0 || h == 0) return false;
        int[] base = new int[2];
        overlay.getLocationInWindow(base);
        String[] all = controls();
        android.view.View[] views = new android.view.View[all.length];
        int[][] pos = new int[all.length][];
        // Measure everything first: removing a view reflows its siblings.
        for (int i = 0; i < all.length; i++) {
            android.view.View v = findControl(overlay, all[i]);
            if (v == null || v.getParent() == overlay && v.getTag(R.id.gamepad_layout) != null) continue;
            int[] loc = new int[2];
            v.getLocationInWindow(loc);
            views[i] = v;
            pos[i] = new int[] { loc[0] - base[0] - Math.round(v.getTranslationX()),
                    loc[1] - base[1] - Math.round(v.getTranslationY()), v.getWidth(), v.getHeight() };
        }
        for (int i = 0; i < all.length; i++) {
            android.view.View v = views[i];
            if (v == null) continue;
            ((android.view.ViewGroup) v.getParent()).removeView(v);
            android.widget.RelativeLayout.LayoutParams lp =
                    new android.widget.RelativeLayout.LayoutParams(pos[i][2], pos[i][3]);
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
            lp.leftMargin = pos[i][0];
            lp.topMargin = pos[i][1];
            v.setTag(R.id.gamepad_layout, Boolean.TRUE);
            v.setTranslationX(0);
            v.setTranslationY(0);
            overlay.addView(v, lp);
        }
        overlay.requestLayout();
        return true;
    }

    /** The option assigned to a slot, or null if it keeps its original key. */
    public Option get(String slot)
    {
        String id = mMap.optString(slot, null);
        if (id == null) return null;
        for (Option o : OPTIONS) if (o.id.equals(id)) return o;
        return null;
    }

    public void set(String slot, Option o)
    {
        try {
            mMap.put(slot, o.id);
        } catch (JSONException ignored) {
        }
    }

    private static SharedPreferences prefs(Context ctx)
    {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

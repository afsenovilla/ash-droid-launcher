package io.github.afsenovilla.ashdroid.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;

import io.github.afsenovilla.ashdroid.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Maps physical controller keycodes to the "standard" Android gamepad keycodes
 * that mkxp-z binds by default to each RPG Maker input.
 *
 * mkxp-z default controller bindings (jni/mkxp-z/src/input/keybindings.cpp):
 *   A -> C (confirm)   B -> B (cancel)   X -> A (run)   Y -> X
 *   L3 -> Y            R3 -> Z           L1 -> L        R1 -> R
 *
 * So remapping a physical button to e.g. KEYCODE_BUTTON_A makes it "Confirm",
 * whatever the device's face-button layout is.
 */
public final class ButtonMapping
{
    public static final int DROP = -1;

    private static final String PREFS = "ashdroid";
    private static final String KEY_MAPPING = "button_mapping";
    private static final String KEY_AUTO = "button_mapping_auto";

    /** One in-game action the wizard asks for. */
    public static final class Action
    {
        public final String id;
        private final int labelRes;
        private final int hintRes;
        public final int target;
        // Suggested physical button for Nintendo-style, Xbox-style and unknown layouts
        private final int sugN, sugX, sugG;

        Action(String id, int labelRes, int hintRes, int target, int sugN, int sugX, int sugG)
        {
            this.id = id;
            this.labelRes = labelRes;
            this.hintRes = hintRes;
            this.target = target;
            this.sugN = sugN;
            this.sugX = sugX;
            this.sugG = sugG;
        }

        public String label(Context ctx) { return ctx.getString(labelRes); }

        public String hint(Context ctx) { return ctx.getString(hintRes); }

        /** @param faceLayout "nintendo", "xbox" or null (unknown device) */
        public String suggestion(Context ctx, String faceLayout)
        {
            int res = "nintendo".equals(faceLayout) ? sugN : "xbox".equals(faceLayout) ? sugX : sugG;
            return ctx.getString(res);
        }
    }

    /** Actions in the order the wizard asks them. Labels follow Pokémon Essentials v19. */
    public static final Action[] ACTIONS = {
        new Action("C", R.string.action_c, R.string.action_c_hint, KeyEvent.KEYCODE_BUTTON_A,
                R.string.sug_c_n, R.string.sug_c_x, R.string.sug_c_g),
        new Action("B", R.string.action_b, R.string.action_b_hint, KeyEvent.KEYCODE_BUTTON_B,
                R.string.sug_b_n, R.string.sug_b_x, R.string.sug_b_g),
        new Action("A", R.string.action_a, R.string.action_a_hint, KeyEvent.KEYCODE_BUTTON_X,
                R.string.sug_a_n, R.string.sug_a_x, R.string.sug_a_g),
        new Action("R", R.string.action_r, R.string.action_r_hint, KeyEvent.KEYCODE_BUTTON_R1,
                R.string.sug_r, R.string.sug_r, R.string.sug_r),
        new Action("L", R.string.action_l, R.string.action_l_hint, KeyEvent.KEYCODE_BUTTON_L1,
                R.string.sug_l, R.string.sug_l, R.string.sug_l),
        new Action("Z", R.string.action_z, R.string.action_z_hint, KeyEvent.KEYCODE_BUTTON_THUMBR,
                R.string.sug_z_n, R.string.sug_z_x, R.string.sug_z_g),
        new Action("X", R.string.action_x, R.string.action_x_hint, KeyEvent.KEYCODE_BUTTON_Y,
                R.string.sug_x, R.string.sug_x, R.string.sug_x),
        new Action("Y", R.string.action_y, R.string.action_y_hint, KeyEvent.KEYCODE_BUTTON_THUMBL,
                R.string.sug_y, R.string.sug_y, R.string.sug_y),
    };

    private final Map<Integer, Integer> mMap;

    /**
     * Mapping for a device in the database, so the wizard isn't needed.
     * Uses the entry's explicit "mapping" if present; otherwise assumes the
     * device sends the keycode matching each printed label (true for Retroid,
     * AYN, Anbernic… in their default controller mode) and assigns the same
     * buttons the wizard would suggest. Returns null for unknown devices.
     */
    public static ButtonMapping forKnownDevice(DeviceProfile dev)
    {
        if (!dev.isKnown()) return null;
        Map<String, Integer> a = new HashMap<>();
        if (dev.mapping != null) {
            for (Action act : ACTIONS) {
                String name = dev.mapping.optString(act.id, null);
                if (name == null) continue;
                int code = KeyEvent.keyCodeFromString(name.startsWith("KEYCODE_") ? name : "KEYCODE_" + name);
                if (code != KeyEvent.KEYCODE_UNKNOWN) a.put(act.id, code);
            }
            return fromAssignments(a);
        }
        if (dev.faceLayout == null) return null;
        boolean nintendo = "nintendo".equals(dev.faceLayout);
        a.put("C", KeyEvent.KEYCODE_BUTTON_A);
        a.put("B", KeyEvent.KEYCODE_BUTTON_B);
        a.put("A", nintendo ? KeyEvent.KEYCODE_BUTTON_Y : KeyEvent.KEYCODE_BUTTON_X); // left face button
        a.put("Z", nintendo ? KeyEvent.KEYCODE_BUTTON_X : KeyEvent.KEYCODE_BUTTON_Y); // top face button
        a.put("R", KeyEvent.KEYCODE_BUTTON_R1);
        a.put("L", KeyEvent.KEYCODE_BUTTON_L1);
        a.put("X", KeyEvent.KEYCODE_BUTTON_L2);
        a.put("Y", KeyEvent.KEYCODE_BUTTON_R2);
        return fromAssignments(a);
    }

    private ButtonMapping(Map<Integer, Integer> map)
    {
        mMap = map;
    }

    /** Identity mapping: mkxp-z defaults. */
    public static ButtonMapping identity()
    {
        return new ButtonMapping(new HashMap<>());
    }

    /**
     * Builds a mapping from wizard answers: action id -> physical keycode.
     * Physical buttons not assigned to any action but whose keycode is the
     * target of another action are dropped, so nothing triggers twice.
     */
    public static ButtonMapping fromAssignments(Map<String, Integer> assignments)
    {
        Map<Integer, Integer> map = new HashMap<>();
        Set<Integer> targets = new HashSet<>();
        for (Action a : ACTIONS) {
            Integer phys = assignments.get(a.id);
            if (phys == null) continue;
            map.put(phys, a.target);
            targets.add(a.target);
        }
        for (Integer t : targets) {
            if (!map.containsKey(t)) {
                map.put(t, DROP);
            }
        }
        return new ButtonMapping(map);
    }

    public int translate(int keyCode)
    {
        Integer t = mMap.get(keyCode);
        return t == null ? keyCode : t;
    }

    /** Physical keycode currently assigned to an action, or null. */
    public Integer physicalFor(Action action)
    {
        for (Map.Entry<Integer, Integer> e : mMap.entrySet()) {
            if (e.getValue() == action.target) return e.getKey();
        }
        return null;
    }

    public boolean isIdentity()
    {
        return mMap.isEmpty();
    }

    // ---- persistence ----

    public static boolean isConfigured(Context ctx)
    {
        return prefs(ctx).contains(KEY_MAPPING);
    }

    /** True if the saved mapping came from the device profile (not the wizard). */
    public static boolean isAutomatic(Context ctx)
    {
        return prefs(ctx).getBoolean(KEY_AUTO, false);
    }

    /**
     * If no mapping is saved yet and the device is in the database, saves its
     * mapping. Returns true if buttons are configured afterwards.
     */
    public static boolean ensureConfigured(Context ctx, DeviceProfile dev)
    {
        if (isConfigured(ctx)) return true;
        ButtonMapping m = forKnownDevice(dev);
        if (m == null) return false;
        m.save(ctx);
        prefs(ctx).edit().putBoolean(KEY_AUTO, true).commit();
        return true;
    }

    public static ButtonMapping load(Context ctx)
    {
        String json = prefs(ctx).getString(KEY_MAPPING, null);
        Map<Integer, Integer> map = new HashMap<>();
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    map.put(Integer.parseInt(k), o.getInt(k));
                }
            } catch (JSONException | NumberFormatException ignored) {
                map.clear();
            }
        }
        return new ButtonMapping(map);
    }

    public void save(Context ctx)
    {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<Integer, Integer> e : mMap.entrySet()) {
                o.put(String.valueOf(e.getKey()), e.getValue().intValue());
            }
        } catch (JSONException ignored) {
        }
        // commit(): the game runs in a separate process and reads this right away
        prefs(ctx).edit().putString(KEY_MAPPING, o.toString()).putBoolean(KEY_AUTO, false).commit();
    }

    private static SharedPreferences prefs(Context ctx)
    {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- device helpers ----

    public static boolean isGamepadEvent(KeyEvent evt)
    {
        int src = evt.getSource();
        if ((src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) return true;
        if ((src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return true;
        return KeyEvent.isGamepadButton(evt.getKeyCode());
    }

    /** True if a non-virtual gamepad/joystick is connected (built-in controls count). */
    public static boolean hasPhysicalGamepad()
    {
        return firstGamepad() != null;
    }

    public static InputDevice firstGamepad()
    {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev == null || dev.isVirtual()) continue;
            int s = dev.getSources();
            if ((s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                return dev;
            }
        }
        return null;
    }

    /** Human readable name for a keycode, e.g. "BUTTON_L2" -> "L2". */
    public static String keyName(Context ctx, int keyCode)
    {
        String s = KeyEvent.keyCodeToString(keyCode);
        if (s.startsWith("KEYCODE_")) s = s.substring(8);
        if (s.startsWith("BUTTON_")) s = s.substring(7);
        switch (s) {
            case "THUMBL": return ctx.getString(R.string.key_l3);
            case "THUMBR": return ctx.getString(R.string.key_r3);
            case "L1": return "L1";
            case "R1": return "R1";
            default: return s;
        }
    }
}

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

    /** One in-game action the wizard asks for. */
    public static final class Action
    {
        public final String id;
        private final int labelRes;
        private final int hintRes;
        public final int target;

        Action(String id, int labelRes, int hintRes, int target)
        {
            this.id = id;
            this.labelRes = labelRes;
            this.hintRes = hintRes;
            this.target = target;
        }

        public String label(Context ctx) { return ctx.getString(labelRes); }

        public String hint(Context ctx) { return ctx.getString(hintRes); }
    }

    /** Actions in the order the wizard asks them. Labels follow Pokémon Essentials v19. */
    public static final Action[] ACTIONS = {
        new Action("C", R.string.action_c, R.string.action_c_hint, KeyEvent.KEYCODE_BUTTON_A),
        new Action("B", R.string.action_b, R.string.action_b_hint, KeyEvent.KEYCODE_BUTTON_B),
        new Action("A", R.string.action_a, R.string.action_a_hint, KeyEvent.KEYCODE_BUTTON_X),
        new Action("R", R.string.action_r, R.string.action_r_hint, KeyEvent.KEYCODE_BUTTON_R1),
        new Action("L", R.string.action_l, R.string.action_l_hint, KeyEvent.KEYCODE_BUTTON_L1),
        new Action("Z", R.string.action_z, R.string.action_z_hint, KeyEvent.KEYCODE_BUTTON_THUMBR),
        new Action("X", R.string.action_x, R.string.action_x_hint, KeyEvent.KEYCODE_BUTTON_Y),
        new Action("Y", R.string.action_y, R.string.action_y_hint, KeyEvent.KEYCODE_BUTTON_THUMBL),
    };

    private final Map<Integer, Integer> mMap;

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
        prefs(ctx).edit().putString(KEY_MAPPING, o.toString()).commit();
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

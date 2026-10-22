package io.github.afsenovilla.ashdroid.launcher;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Locale;

/**
 * What the launcher knows about the device it's running on: screen (always
 * detected live), built-in controller, and optional extra info from the bundled
 * device database (assets/devices.json), matched by manufacturer/model.
 */
public final class DeviceProfile
{
    // Detected
    public final String manufacturer;
    public final String model;
    public final int screenW;      // landscape width in px
    public final int screenH;      // landscape height in px
    public final float refreshRate;
    public final String controllerName;
    public final int controllerVendor;
    public final int controllerProduct;

    // From database (null/defaults when the device is unknown)
    public final String knownName;
    /** "nintendo" (A on the right) or "xbox" (A at the bottom). */
    public final String faceLayout;
    public final String notes;
    /** Optional per-device mapping override from the database: action id -> keycode name. */
    public final JSONObject mapping;

    private DeviceProfile(Activity act)
    {
        manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        model = Build.MODEL == null ? "" : Build.MODEL;

        Display display = act.getWindowManager().getDefaultDisplay();
        DisplayMetrics m = new DisplayMetrics();
        display.getRealMetrics(m);
        screenW = Math.max(m.widthPixels, m.heightPixels);
        screenH = Math.min(m.widthPixels, m.heightPixels);
        refreshRate = display.getRefreshRate();

        InputDevice pad = ButtonMapping.firstGamepad();
        controllerName = pad == null ? null : pad.getName();
        controllerVendor = pad == null ? 0 : pad.getVendorId();
        controllerProduct = pad == null ? 0 : pad.getProductId();

        JSONObject entry = lookup(act, manufacturer, model, controllerName);
        knownName = entry == null ? null : entry.optString("name", null);
        faceLayout = entry == null ? null : entry.optString("faceLayout", null);
        notes = entry == null ? null : entry.optString("notes", null);
        mapping = entry == null ? null : entry.optJSONObject("mapping");
    }

    public static DeviceProfile detect(Activity act)
    {
        return new DeviceProfile(act);
    }

    public String displayName()
    {
        if (knownName != null) return knownName;
        String m = model;
        if (!m.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT))) {
            m = manufacturer + " " + m;
        }
        return m.trim();
    }

    public boolean isKnown()
    {
        return knownName != null;
    }

    /**
     * Finds the first database entry whose "match" regexes all match. Keys:
     * manufacturer, model, controller (case-insensitive regex, all optional).
     */
    private static JSONObject lookup(Context ctx, String manufacturer, String model, String controller)
    {
        try (InputStream in = ctx.getAssets().open("devices.json")) {
            JSONArray arr = new JSONObject(Util.stripJsonComments(Util.readText(in))).getJSONArray("devices");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject d = arr.getJSONObject(i);
                JSONObject match = d.optJSONObject("match");
                if (match == null) continue;
                if (matches(match.optString("manufacturer", null), manufacturer)
                        && matches(match.optString("model", null), model)
                        && matches(match.optString("controller", null), controller)) {
                    return d;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean matches(String regex, String value)
    {
        if (regex == null || regex.isEmpty()) return true;
        if (value == null) return false;
        return value.toLowerCase(Locale.ROOT).matches("(?i).*(" + regex + ").*");
    }
}

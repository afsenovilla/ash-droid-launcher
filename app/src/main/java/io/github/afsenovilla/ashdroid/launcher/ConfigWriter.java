package io.github.afsenovilla.ashdroid.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.afsenovilla.ashdroid.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * Generates the game's mkxp.json for the current device, starting from the
 * mkxp.json shipped with the game (kept as mkxp.original.json) and overriding
 * only display/performance settings.
 */
public final class ConfigWriter
{
    public enum ScaleMode
    {
        AUTO(R.string.scale_auto),
        SHARP(R.string.scale_sharp),
        SMOOTH(R.string.scale_smooth),
        STRETCH(R.string.scale_stretch);

        private final int labelRes;

        ScaleMode(int labelRes) { this.labelRes = labelRes; }

        public String label(Context ctx) { return ctx.getString(labelRes); }
    }

    private static final String PREFS = "ashdroid";
    private static final String KEY_SCALE = "scale_mode";

    /** Result shown in the launcher. */
    public static final class Result
    {
        public int gameW, gameH;
        public double scale;
        public String description;
    }

    private ConfigWriter() {}

    public static ScaleMode scaleMode(Context ctx)
    {
        String v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCALE, ScaleMode.AUTO.name());
        try {
            return ScaleMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            return ScaleMode.AUTO;
        }
    }

    public static void setScaleMode(Context ctx, ScaleMode mode)
    {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString(KEY_SCALE, mode.name()).apply();
    }

    public static Result write(Context ctx, DeviceProfile dev) throws IOException
    {
        File game = GameInstaller.gameDir(ctx);
        File conf = new File(game, "mkxp.json");
        File original = new File(game, "mkxp.original.json");

        if (conf.exists() && !original.exists()) {
            Util.copy(conf, original);
        }

        JSONObject o;
        try {
            o = original.exists()
                    ? new JSONObject(Util.stripJsonComments(Util.readText(original)))
                    : new JSONObject();
        } catch (JSONException e) {
            o = new JSONObject();
        }

        Result r = new Result();
        try {
            // Game resolution: from the game's own config, else RGSS1 default
            r.gameW = o.optInt("defScreenW", 0);
            r.gameH = o.optInt("defScreenH", 0);
            if (r.gameW <= 0 || r.gameH <= 0) {
                r.gameW = 640;
                r.gameH = 480;
            }

            if (!o.has("rgssVersion") || o.optInt("rgssVersion", 0) == 0) {
                o.put("rgssVersion", 1);
            }

            // These settings are for desktop windows: meaningless or harmful here
            o.remove("winResizable");
            o.remove("gameFolder");
            o.remove("customScript");

            o.put("fullscreen", true);
            o.put("vsync", true);
            // Would run the game at the panel's refresh rate (e.g. 120 Hz = double speed)
            o.put("syncToRefreshrate", false);
            o.put("frameSkip", false);
            o.put("printFPS", false);

            if (o.optInt("SESourceCount", 6) < 16) o.put("SESourceCount", 16);

            applyScaling(ctx, o, r, dev, scaleMode(ctx));

            // org.json escapes '/' as '\/'; keep paths readable for mkxp-z's parser
            Util.writeText(conf, o.toString(2).replace("\\/", "/"));
        } catch (JSONException e) {
            throw new IOException(ctx.getString(R.string.err_mkxp_json, e.getMessage()));
        }
        return r;
    }

    private static void applyScaling(Context ctx, JSONObject o, Result r, DeviceProfile dev, ScaleMode mode) throws JSONException
    {
        double sx = (double) dev.screenW / r.gameW;
        double sy = (double) dev.screenH / r.gameH;
        double s = Math.min(sx, sy);
        r.scale = s;

        boolean integer = Math.abs(s - Math.rint(s)) < 0.01;
        boolean sameAspect = Math.abs(sx - sy) < 0.02;
        String aspect = ctx.getString(sameAspect ? R.string.aspect_same : R.string.aspect_borders);

        switch (mode) {
            case STRETCH:
                o.put("fixedAspectRatio", false);
                o.put("smoothScaling", true);
                o.put("integerScalingActive", false);
                r.description = ctx.getString(R.string.desc_stretch);
                return;

            case SHARP:
                o.put("fixedAspectRatio", true);
                o.put("smoothScaling", false);
                o.put("integerScalingActive", s >= 1.0);
                o.put("integerScalingLastMile", false);
                r.description = ctx.getString(R.string.desc_sharp, Math.max(1, (int) Math.floor(s)));
                return;

            case SMOOTH:
                o.put("fixedAspectRatio", true);
                o.put("smoothScaling", true);
                o.put("integerScalingActive", false);
                r.description = ctx.getString(R.string.desc_smooth, s, aspect);
                return;

            case AUTO:
            default:
                o.put("fixedAspectRatio", true);
                if (integer) {
                    // Perfect integer scale: pixel-perfect nearest neighbour
                    o.put("smoothScaling", false);
                    o.put("integerScalingActive", false);
                    r.description = ctx.getString(R.string.desc_exact, (int) Math.rint(s), aspect);
                } else if (s >= 2.0) {
                    // e.g. x2.5: scale x2 with nearest, then a short smooth "last mile"
                    o.put("smoothScaling", true);
                    o.put("integerScalingActive", true);
                    o.put("integerScalingLastMile", true);
                    r.description = ctx.getString(R.string.desc_sharp_lastmile, (int) Math.floor(s), s, aspect);
                } else {
                    o.put("smoothScaling", true);
                    o.put("integerScalingActive", false);
                    r.description = ctx.getString(R.string.desc_smooth, s, aspect);
                }
        }
    }
}

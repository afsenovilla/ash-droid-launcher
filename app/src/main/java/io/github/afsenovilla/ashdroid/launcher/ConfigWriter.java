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

            // Runtime text patches (controls screen talks about buttons, not keys)
            try {
                writeTextPatches(ctx, game);
                writeControlsArt(ctx, game);
                LocalizedArt.generate(game);
                writeLocalizedArt(ctx, game);
                installTranslationPack(ctx, game);
                org.json.JSONArray pre = o.optJSONArray("preloadScript");
                org.json.JSONArray merged = new org.json.JSONArray();
                if (pre != null) {
                    for (int i = 0; i < pre.length(); i++) {
                        if (!PATCH_FILE.equals(pre.optString(i))) merged.put(pre.optString(i));
                    }
                }
                merged.put(PATCH_FILE);
                o.put("preloadScript", merged);
            } catch (IOException e) {
                // cosmetic: never block the game
            }

            // org.json escapes '/' as '\/'; keep paths readable for mkxp-z's parser
            Util.writeText(conf, o.toString(2).replace("\\/", "/"));
        } catch (JSONException e) {
            throw new IOException(ctx.getString(R.string.err_mkxp_json, e.getMessage()));
        }
        return r;
    }

    static final String PATCH_FILE = "ashdroid_patches.rb";

    /**
     * Copies assets/ashdroid_patches.rb into the game folder, filling in which
     * device button corresponds to each keyboard key the game mentions.
     * Keyboard key -> RGSS input (mkxp-z defaults): C=C X=B Z=A D=Z A=X S=Y Q=L W=R
     */
    private static void writeTextPatches(Context ctx, File game) throws IOException
    {
        String tpl;
        try (java.io.InputStream in = ctx.getAssets().open(PATCH_FILE)) {
            tpl = Util.readText(in);
        }
        ButtonMapping m = ButtonMapping.load(ctx);
        String[][] keys = {
            { "C", "C", "" }, { "X", "B", " / START" }, { "Z", "A", "" }, { "D", "Z", " / SELECT" },
            { "A", "X", "" }, { "S", "Y", "" }, { "Q", "L", "" }, { "W", "R", "" },
        };
        StringBuilder rb = new StringBuilder("ASHDROID_KEYS = {");
        boolean first = true;
        for (String[] k : keys) {
            ButtonMapping.Action act = null;
            for (ButtonMapping.Action a : ButtonMapping.ACTIONS) if (a.id.equals(k[1])) act = a;
            if (act == null) continue;
            Integer phys = m.isIdentity() ? Integer.valueOf(act.target) : m.physicalFor(act);
            if (phys == null) continue;
            String label = ButtonMapping.keyName(ctx, phys) + k[2];
            if (!first) rb.append(", ");
            first = false;
            rb.append('"').append(k[0]).append("\" => \"")
              .append(label.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        rb.append("}");
        String out = tpl.replace("ASHDROID_KEYS = {} # __ASHDROID_KEYS__", rb.toString());
        String lang = java.util.Locale.getDefault().getLanguage().replaceAll("[^a-zA-Z_-]", "");
        out = out.replace("ASHDROID_LANG = \"en\" # __ASHDROID_LANG__", "ASHDROID_LANG = \"" + lang + "\"");
        Util.writeText(new File(game, PATCH_FILE), out);
    }

    /**
     * Copies the bundled Spanish translation pack for this game (if there is one)
     * next to the runtime patch, which uses it while the game language is Spanish.
     * Packs live in assets/packs/<id>/ashdroid_es.dat, matched by game title.
     */
    private static void installTranslationPack(Context ctx, File game)
    {
        String id = packId(ctx);
        File dst = new File(game, "ashdroid_es.dat");
        if (id == null) {
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            return;
        }
        try (java.io.InputStream in = ctx.getAssets().open("packs/" + id + "/ashdroid_es.dat");
             java.io.OutputStream os = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } catch (IOException ignored) {
        }
    }

    /** Id of the bundled pack (assets/packs/<id>/) for the imported game, or null. */
    private static String packId(Context ctx)
    {
        String title = GameInstaller.gameTitle(ctx).toLowerCase(java.util.Locale.ROOT);
        return title.contains("fire ash") ? "fire-ash" : null;
    }

    /** Redraws English lettering in the game's images (assets/packs/<id>/art.json). */
    private static void writeLocalizedArt(Context ctx, File game)
    {
        String id = packId(ctx);
        ButtonMapping m = ButtonMapping.load(ctx);
        java.util.Map<String, String> b = new java.util.HashMap<>();
        b.put("USE", labelFor(ctx, m, "C"));
        b.put("BACK", labelFor(ctx, m, "B"));
        b.put("ACTION", labelFor(ctx, m, "A"));
        b.put("SPECIAL", labelFor(ctx, m, "Z"));
        if (id == null) {
            try {
                Util.writeText(new File(game, ArtPatcher.INDEX), "");
            } catch (IOException ignored) {
            }
            return;
        }
        ArtPatcher.generate(ctx, game, id, b);
    }

    /** Button label currently assigned to an RGSS action (e.g. "A", "L1"), or null. */
    private static String labelFor(Context ctx, ButtonMapping m, String actionId)
    {
        for (ButtonMapping.Action a : ButtonMapping.ACTIONS) {
            if (!a.id.equals(actionId)) continue;
            Integer phys = m.isIdentity() ? Integer.valueOf(a.target) : m.physicalFor(a);
            return phys == null ? null : ButtonMapping.keyName(ctx, phys);
        }
        return null;
    }

    private static void writeControlsArt(Context ctx, File game)
    {
        ButtonMapping m = ButtonMapping.load(ctx);
        ControlsArt.generate(game, labelFor(ctx, m, "C"), labelFor(ctx, m, "B"),
                labelFor(ctx, m, "A"), labelFor(ctx, m, "Z"));
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

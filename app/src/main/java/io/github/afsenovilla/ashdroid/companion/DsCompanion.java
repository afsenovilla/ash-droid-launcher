package io.github.afsenovilla.ashdroid.companion;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Second-screen battle menus (BETA) for dual-screen handhelds such as the AYN Thor.
 *
 * The game runs untouched on its own screen. While a battle menu is open, the runtime patch
 * (ashdroid_patches.rb, module AshDroidDS) reports what the menu shows as JSON and this class
 * draws it on the other screen as touch buttons; a touch goes back as a short command
 * ("sel:2", "back"...) that the patch turns into the same cursor move and button press the
 * player would make. Physical buttons keep working: the cursor is mirrored here.
 *
 * Bridge (called from the game thread through MainActivity, see android-binding.cpp):
 * {@link #active()}, {@link #state(byte[])}, {@link #poll()}.
 */
public final class DsCompanion
{
    private static final String TAG = "AshDroid[DS]";

    /** Devices with a second built-in screen. A TV or a cast target must never get the menus. */
    private static final String DUAL_SCREEN_MODELS = ".*(thor|pocket ?ds|dual ?screen).*";

    private static volatile DsCompanion sInstance;

    private final Activity mActivity;
    private final DisplayManager mDisplays;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ConcurrentLinkedQueue<String> mCommands = new ConcurrentLinkedQueue<>();

    private volatile Screen mScreen;
    private volatile boolean mShowing;
    private boolean mStarted;

    private final DisplayManager.DisplayListener mListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { refresh(); }
        @Override public void onDisplayRemoved(int id) { refresh(); }
        @Override public void onDisplayChanged(int id) { refresh(); }
    };

    public static boolean supportedDevice()
    {
        String model = (Build.MANUFACTURER + " " + Build.MODEL + " " + Build.DEVICE).toLowerCase(Locale.ROOT);
        return model.matches(DUAL_SCREEN_MODELS);
    }

    public DsCompanion(Activity activity)
    {
        mActivity = activity;
        mDisplays = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
    }

    /** Call from Activity.onStart(). */
    public void start()
    {
        if (mStarted) return;
        mStarted = true;
        sInstance = this;
        mDisplays.registerDisplayListener(mListener, mMain);
        refresh();
    }

    /** Call from Activity.onStop(). */
    public void stop()
    {
        if (!mStarted) return;
        mStarted = false;
        mDisplays.unregisterDisplayListener(mListener);
        dismiss();
        if (sInstance == this) sInstance = null;
    }

    /** True while the menus are on the second screen (it may hold the window focus). */
    public boolean isShowing()
    {
        return mShowing;
    }

    private Display otherDisplay()
    {
        int own = mActivity.getWindowManager().getDefaultDisplay().getDisplayId();
        for (Display d : mDisplays.getDisplays()) {
            if (d.getDisplayId() == own) continue;
            if (d.getState() == Display.STATE_OFF) continue;
            if ((d.getFlags() & Display.FLAG_PRIVATE) != 0) continue;
            return d;
        }
        return null;
    }

    private void refresh()
    {
        if (!mStarted) return;
        Display target = otherDisplay();
        if (mScreen != null && (target == null || target.getDisplayId() != mScreen.getDisplay().getDisplayId())) {
            dismiss();
        }
        if (mScreen == null && target != null) {
            try {
                Screen s = new Screen(mActivity, target);
                s.show();
                mScreen = s;
                mShowing = true;
                mCommands.clear();
                mCommands.add("hello");
                // The battle menus live on this screen, so the game hides its own (no switch for it here)
                mCommands.add("hide:1");
                Log.i(TAG, "Battle menus on display " + target.getDisplayId() + " (" + target.getName() + ")");
            } catch (RuntimeException e) {
                // WindowManager.InvalidDisplayException and friends: stay on one screen
                Log.w(TAG, "Second screen not usable: " + e);
                mScreen = null;
                mShowing = false;
            }
        }
    }

    private void dismiss()
    {
        mShowing = false;
        if (mScreen != null) {
            try {
                mScreen.dismiss();
            } catch (RuntimeException ignored) {
            }
            mScreen = null;
        }
        mCommands.clear();
    }

    void send(String command)
    {
        mCommands.add(command);
    }

    // ---- Bridge: called on the game thread ----

    public static boolean active()
    {
        DsCompanion c = sInstance;
        return c != null && c.mShowing;
    }

    public static void state(byte[] utf8)
    {
        final DsCompanion c = sInstance;
        if (c == null || utf8 == null) return;
        final DsState st = DsState.parse(new String(utf8, StandardCharsets.UTF_8));
        if (st == null) return;
        c.mMain.post(() -> {
            Screen screen = c.mScreen;
            if (screen != null && screen.mView != null) screen.mView.setState(st);
        });
    }

    /** A frame of one of the game's own menus (RGBA, top row first) to show on this screen. */
    public static void frame(int width, int height, byte[] rgba)
    {
        DsCompanion c = sInstance;
        Screen screen = c == null ? null : c.mScreen;
        if (screen != null && screen.mView != null && rgba != null) screen.mView.setFrame(width, height, rgba);
    }

    public static byte[] poll()
    {
        DsCompanion c = sInstance;
        if (c == null) return null;
        String cmd = c.mCommands.poll();
        return cmd == null ? null : cmd.getBytes(StandardCharsets.UTF_8);
    }

    // ---- The window on the other screen ----

    private final class Screen extends Presentation
    {
        DsView mView;

        Screen(Context outer, Display display)
        {
            super(outer, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            setCancelable(false);
            mView = new DsView(getContext(), DsCompanion.this);
            setContentView(mView);
            Window w = getWindow();
            if (w != null) {
                w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                w.setBackgroundDrawable(null);
                immersive(w);
            }
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus)
        {
            super.onWindowFocusChanged(hasFocus);
            Window w = getWindow();
            if (hasFocus && w != null) immersive(w);
        }

        @SuppressWarnings("deprecation")
        private void immersive(Window w)
        {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }

        // A touch here can move the input focus to this screen: the console's buttons and
        // sticks must still reach the game.
        @Override
        public boolean dispatchKeyEvent(KeyEvent event)
        {
            return mActivity.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event)
        {
            if ((event.getSource() & android.view.InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
                return mActivity.dispatchGenericMotionEvent(event);
            }
            return super.dispatchGenericMotionEvent(event);
        }
    }

    // ---- What the patch reported ----

    static final class DsState
    {
        static final int IDLE = 0, COMMAND = 1, FIGHT = 2, WORLD = 3, MIRROR = 4;

        /** WORLD: the player can open a menu right now, and the engine can mirror it here. */
        boolean free, canMirror;
        /** MIRROR: average cost of sending a frame, in tenths of a millisecond (0 = not measured yet). */
        int mirrorTenths;

        int screen = IDLE;
        int index;
        String title = "";
        String[] items = new String[0];
        Move[] moves = new Move[0];
        /** 0 = no Mega Evolution this turn, 1 = available, 2 = chosen. */
        int mega;
        String back = "Back";
        String megaLabel = "Mega Evolution";
        String[] eff = { "No effect", "Not very effective", "Super effective" };

        // The game's own images (null/empty when it didn't hand any: drawn plainly instead)
        Art bg;
        /** The bar the game keeps behind its menus, if any: drawn behind them and gives the screen its colour. */
        Art strip;
        Art[] buttons = new Art[0];
        Art[] extras = new Art[0];
        Art backBtn;
        Art typeIcon;
        String ppText = "";
        int base = 0xFF505058, shadow = 0xFFA0A0A8;
        int ppBase = 0xFF505058, ppShadow = 0xFFA0A0A8;

        /** A piece of one of the game's images: file, source rectangle and, for extras, its command. */
        static final class Art
        {
            String img = "";
            int x, y, w, h;
            String cmd = "act";

            static Art parse(JSONObject o)
            {
                if (o == null) return null;
                JSONArray r = o.optJSONArray("r");
                String img = o.optString("img", "");
                if (r == null || r.length() != 4 || img.isEmpty()) return null;
                Art a = new Art();
                a.img = img;
                a.x = r.optInt(0);
                a.y = r.optInt(1);
                a.w = r.optInt(2);
                a.h = r.optInt(3);
                a.cmd = o.optString("cmd", "act");
                return a.w > 0 && a.h > 0 ? a : null;
            }

            static Art[] parseAll(JSONArray list)
            {
                if (list == null) return new Art[0];
                Art[] out = new Art[list.length()];
                for (int i = 0; i < out.length; i++) out[i] = parse(list.optJSONObject(i));
                return out;
            }
        }

        static int color(JSONObject o, String key, int fallback)
        {
            String v = o.isNull(key) ? "" : o.optString(key, "");
            if (v.isEmpty()) return fallback;
            try {
                return android.graphics.Color.parseColor(v);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }

        static final class Move
        {
            String name = "";
            String type = "";
            String typeName = "";
            int pp, totalPp;
            /** 0 no effect, 1 not very effective, 2 super effective, -1 nothing to say. */
            int eff = -1;
            /** The game's button for this move and the colour it gives the name (0 = none). */
            Art art;
            int nameColor;
        }

        static DsState parse(String json)
        {
            try {
                JSONObject o = new JSONObject(json);
                DsState s = new DsState();
                String screen = o.optString("screen", "idle");
                s.screen = "command".equals(screen) ? COMMAND : "fight".equals(screen) ? FIGHT
                        : "world".equals(screen) ? WORLD : "mirror".equals(screen) ? MIRROR : IDLE;
                s.free = o.optBoolean("free", false);
                s.canMirror = o.optBoolean("mirror", false);
                s.mirrorTenths = o.optInt("ms", 0);
                s.index = o.optInt("index", 0);
                s.title = o.optString("title", "");
                s.mega = o.optInt("mega", 0);
                JSONArray items = o.optJSONArray("items");
                if (items != null) {
                    s.items = new String[items.length()];
                    for (int i = 0; i < s.items.length; i++) s.items[i] = items.optString(i, "");
                }
                JSONArray moves = o.optJSONArray("moves");
                if (moves != null) {
                    s.moves = new Move[moves.length()];
                    for (int i = 0; i < s.moves.length; i++) {
                        JSONObject m = moves.optJSONObject(i);
                        Move mv = new Move();
                        if (m != null) {
                            mv.name = m.optString("name", "");
                            mv.type = m.optString("type", "");
                            mv.typeName = m.optString("typeName", "");
                            mv.pp = m.optInt("pp", 0);
                            mv.totalPp = m.optInt("tpp", 0);
                            mv.eff = m.isNull("eff") ? -1 : m.optInt("eff", -1);
                            mv.art = Art.parse(m);
                            mv.nameColor = color(m, "col", 0);
                        }
                        s.moves[i] = mv;
                    }
                }
                s.bg = Art.parse(o.optJSONObject("bg"));
                s.strip = Art.parse(o.optJSONObject("strip"));
                s.buttons = Art.parseAll(o.optJSONArray("buttons"));
                s.extras = Art.parseAll(o.optJSONArray("extras"));
                s.backBtn = Art.parse(o.optJSONObject("backBtn"));
                s.base = color(o, "base", s.base);
                s.shadow = color(o, "shadow", s.shadow);
                s.ppBase = s.base;
                s.ppShadow = s.shadow;
                JSONObject info = o.optJSONObject("info");
                if (info != null) {
                    s.typeIcon = Art.parse(info.optJSONObject("icon"));
                    s.ppText = info.isNull("pp") ? "" : info.optString("pp", "");
                    s.ppBase = color(info, "base", s.base);
                    s.ppShadow = color(info, "shadow", s.shadow);
                }
                JSONObject labels = o.optJSONObject("labels");
                if (labels != null) {
                    s.back = labels.optString("back", s.back);
                    s.megaLabel = labels.optString("mega", s.megaLabel);
                    JSONArray eff = labels.optJSONArray("eff");
                    if (eff != null && eff.length() == 3) {
                        s.eff = new String[] { eff.optString(0), eff.optString(1), eff.optString(2) };
                    }
                }
                return s;
            } catch (Exception e) {
                Log.w(TAG, "Bad state: " + e);
                return null;
            }
        }
    }
}

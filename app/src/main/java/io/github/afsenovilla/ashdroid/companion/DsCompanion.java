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

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Second screen (BETA) for dual-screen handhelds such as the AYN Thor: DS-style touch menus in battle
 * and in the overworld.
 *
 * The game runs untouched on its own screen. The runtime patch (ashdroid_patches.rb, modules AshDroidDS
 * and AshDroidDSUI) describes what the second screen must show as JSON and {@link DsView} draws it; a
 * touch goes back as a short command ("sel:2", "back"...) that the patch turns into the same cursor move
 * and button press the player would make. Physical buttons keep working: the cursor is mirrored here.
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
                // The menus live on this screen, so the game hides its own battle menus (no switch for it here)
                mCommands.add("hide:1");
                Log.i(TAG, "Second screen on display " + target.getDisplayId() + " (" + target.getName() + ")");
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
        final java.util.Map<String, Object> st;
        try {
            st = DsJson.map(DsJson.parse(new String(utf8, StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            Log.w(TAG, "Bad state: " + e);
            return;
        }
        if (st == null) return;
        c.mMain.post(() -> {
            Screen screen = c.mScreen;
            if (screen != null && screen.mView != null) screen.mView.setState(st);
        });
    }

    /** Unused since the mirror prototype was retired; kept because the engine still looks the method up. */
    public static void frame(int width, int height, byte[] rgba)
    {
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
}

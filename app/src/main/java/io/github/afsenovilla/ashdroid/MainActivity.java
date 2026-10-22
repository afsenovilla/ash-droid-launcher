package io.github.afsenovilla.ashdroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import org.libsdl.app.SDLActivity;

import io.github.afsenovilla.ashdroid.gamepad.Gamepad;
import io.github.afsenovilla.ashdroid.gamepad.GamepadConfig;
import io.github.afsenovilla.ashdroid.launcher.ButtonMapping;
import io.github.afsenovilla.ashdroid.launcher.GameInstaller;

/**
 * Runs the game (native mkxp-z through SDL).
 * Started by {@link io.github.afsenovilla.ashdroid.launcher.LauncherActivity}
 * once the game is imported and configured.
 */
public class MainActivity extends SDLActivity
{
    private static final String TAG = "AshDroid[Game]";

    // Read by native mkxp-z through JNI (see jni/mkxp-z/src/main.cpp)
    @SuppressWarnings("unused")
    private static String GAME_PATH = "";
    private static boolean DEBUG = false;

    protected boolean mStarted = false;

    protected static Handler mMainHandler;
    protected static Vibrator mVibrator;
    protected static TextView tvFps;

    // On-screen touch gamepad (hidden while a physical controller is used)
    private final Gamepad mGamepad = new Gamepad();
    private boolean mGamepadInvisible = false;

    // Physical button -> standard button remapping from the setup wizard
    private ButtonMapping mMapping;

    private void runSDLThread()
    {
        if (!mStarted) {
            Log.i(TAG, "Game path: " + GAME_PATH);
        }
        mStarted = true;
        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Must be set before the native thread starts
        GAME_PATH = GameInstaller.gameDir(this).getAbsolutePath();
        mMapping = ButtonMapping.load(this);

        super.onCreate(savedInstanceState);

        mMainHandler = new Handler(getMainLooper());
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        try {
            ActivityInfo actInfo = getPackageManager().getActivityInfo(this.getComponentName(), PackageManager.GET_META_DATA);
            DEBUG = actInfo.metaData != null && actInfo.metaData.getBoolean("mkxp_debug");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to read debug flag: " + e);
        }

        // Hide the touch gamepad from the start on devices with physical controls
        mGamepadInvisible = isAndroidTV() || isChromebook() || ButtonMapping.hasPhysicalGamepad();
        GamepadConfig gpadConfig = new GamepadConfig();
        mGamepad.init(gpadConfig, mGamepadInvisible);
        mGamepad.setOnKeyDownListener(SDLActivity::onNativeKeyDown);
        mGamepad.setOnKeyUpListener(SDLActivity::onNativeKeyUp);

        if (mLayout != null) {
            mGamepad.attachTo(this, mLayout);
        }

        tvFps = new TextView(this);
        tvFps.setTextSize((8 * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT)));
        tvFps.setTextColor(Color.argb(96, 255, 255, 255));
        tvFps.setVisibility(View.GONE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(16, 16, 0, 0);
        tvFps.setLayoutParams(params);
        mLayout.addView(tvFps);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        runSDLThread();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        // Ruby VM can't be re-initialized inside the same process
        System.exit(0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt)
    {
        int code = evt.getKeyCode();
        if (code != KeyEvent.KEYCODE_BACK &&
            code != KeyEvent.KEYCODE_VOLUME_UP &&
            code != KeyEvent.KEYCODE_VOLUME_DOWN &&
            code != KeyEvent.KEYCODE_VOLUME_MUTE &&
            code != KeyEvent.KEYCODE_HEADSETHOOK) {
            if (!mGamepadInvisible) {
                mGamepad.hideView();
                mGamepadInvisible = true;
            }
        }

        // Apply the user's button mapping to physical controller events
        if (mMapping != null && ButtonMapping.isGamepadEvent(evt)) {
            int target = mMapping.translate(code);
            if (target == ButtonMapping.DROP) {
                return true;
            }
            if (target != code) {
                evt = new KeyEvent(evt.getDownTime(), evt.getEventTime(), evt.getAction(), target,
                        evt.getRepeatCount(), evt.getMetaState(), evt.getDeviceId(), evt.getScanCode(),
                        evt.getFlags(), evt.getSource());
            }
        }

        if (mGamepad.processGamepadEvent(evt))
            return true;

        return super.dispatchKeyEvent(evt);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evt)
    {
        if (mGamepadInvisible) {
            mGamepad.showView();
            mGamepadInvisible = false;
        }
        return super.dispatchTouchEvent(evt);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent evt)
    {
        if (mGamepad.processDPadEvent(evt))
            return true;
        return super.onGenericMotionEvent(evt);
    }

    @Override
    protected String[] getArguments()
    {
        return DEBUG ? new String[] { "debug" } : new String[] {};
    }

    // ---- Static methods called from native mkxp-z (see jni/mkxp-z/src) ----

    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unused")
    private static void updateFPSText(int num)
    {
        mMainHandler.post(() -> tvFps.setText(num + " FPS"));
    }

    @SuppressWarnings("unused")
    private static void setFPSVisibility(boolean visible)
    {
        mMainHandler.post(() -> tvFps.setVisibility(visible ? View.VISIBLE : View.INVISIBLE));
    }

    @SuppressWarnings("unused")
    private static String getSystemLanguage()
    {
        return Locale.getDefault().toString();
    }

    @SuppressWarnings("unused")
    private static boolean hasVibrator()
    {
        return mVibrator.hasVibrator();
    }

    @SuppressWarnings("unused")
    private static void vibrate(int duration)
    {
        if (duration >= 10000) duration = 10000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            mVibrator.vibrate(duration);
        }
    }

    @SuppressWarnings("unused")
    private static void vibrateStop()
    {
        mVibrator.cancel();
    }

    @SuppressWarnings("unused")
    private static boolean inMultiWindow(Activity activity)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }
}

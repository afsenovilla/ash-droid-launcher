package io.github.afsenovilla.ashdroid.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.afsenovilla.ashdroid.R;
import io.github.afsenovilla.ashdroid.gamepad.GamepadButton;
import io.github.afsenovilla.ashdroid.gamepad.TouchMapping;

/**
 * Drag the on-screen controls to where you want them. Shows the same overlay
 * the game uses, at full size, and saves each control's offset as a fraction
 * of the screen so it fits any resolution.
 */
public class TouchLayoutActivity extends Activity
{
    private ViewGroup mOverlay;
    private TouchMapping mMapping;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        mMapping = TouchMapping.load(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(24, 26, 32));
        LayoutInflater.from(this).inflate(R.layout.gamepad_layout, root);
        mOverlay = root.findViewById(R.id.gamepad_layout);

        TextView hint = new TextView(this);
        hint.setText(R.string.touch_layout_hint);
        hint.setTextColor(Color.argb(160, 255, 255, 255));
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(hint, hlp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(button(R.string.touch_layout_save, v -> save()));
        bar.addView(button(R.string.touch_layout_reset, v -> resetPositions()));
        bar.addView(button(R.string.cancel, v -> finish()));
        root.addView(bar, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        setContentView(root);
        labelButtons();
        mOverlay.post(() -> {
            TouchMapping.detachControls(mOverlay);
            mOverlay.post(() -> {
                mMapping.placeControls(mOverlay);
                enableDragging();
            });
        });
    }

    private Button button(int text, View.OnClickListener l)
    {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        b.setAllCaps(false);
        return b;
    }

    /** Same labels as in game, so the player knows which button is which. */
    private void labelButtons()
    {
        for (String slot : TouchMapping.SLOTS) {
            View v = TouchMapping.findControl(mOverlay, slot);
            if (!(v instanceof GamepadButton)) continue;
            TouchMapping.Option o = mMapping.get(slot);
            ((GamepadButton) v).setForegroundText(o != null ? o.shortLabel(this) : slot);
            v.setAlpha(o != null && o.keycode == 0 ? 0.25f : 1f);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void enableDragging()
    {
        for (String c : TouchMapping.controls()) {
            View v = TouchMapping.findControl(mOverlay, c);
            if (v == null) continue;
            final float[] start = new float[4];
            v.setOnTouchListener((view, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        start[0] = e.getRawX();
                        start[1] = e.getRawY();
                        start[2] = view.getTranslationX();
                        start[3] = view.getTranslationY();
                        view.setScaleX(1.1f);
                        view.setScaleY(1.1f);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float tx = start[2] + e.getRawX() - start[0];
                        float ty = start[3] + e.getRawY() - start[1];
                        // keep it on screen
                        tx = Math.max(-view.getLeft(), Math.min(mOverlay.getWidth() - view.getRight(), tx));
                        ty = Math.max(-view.getTop(), Math.min(mOverlay.getHeight() - view.getBottom(), ty));
                        view.setTranslationX(tx);
                        view.setTranslationY(ty);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setScaleX(1f);
                        view.setScaleY(1f);
                        return true;
                    default:
                        return true;
                }
            });
        }
    }

    private void save()
    {
        int w = mOverlay.getWidth(), h = mOverlay.getHeight();
        if (w > 0 && h > 0) {
            for (String c : TouchMapping.controls()) {
                View v = TouchMapping.findControl(mOverlay, c);
                if (v != null) mMapping.setOffset(c, v.getTranslationX() / w, v.getTranslationY() / h);
            }
            mMapping.save(this);
        }
        setResult(RESULT_OK);
        finish();
    }

    private void resetPositions()
    {
        for (String c : TouchMapping.controls()) {
            View v = TouchMapping.findControl(mOverlay, c);
            if (v == null) continue;
            v.setTranslationX(0);
            v.setTranslationY(0);
        }
        // Saved only when the player presses Save
    }
}

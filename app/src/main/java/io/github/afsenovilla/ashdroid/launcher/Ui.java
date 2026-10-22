package io.github.afsenovilla.ashdroid.launcher;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Tiny programmatic UI kit: dark, large, controller-friendly (clear focus ring). */
final class Ui
{
    static final int BG = Color.rgb(0x12, 0x14, 0x18);
    static final int FG = Color.rgb(0xEE, 0xEE, 0xEE);
    static final int DIM = Color.rgb(0x9A, 0xA0, 0xA8);
    static final int ACCENT = Color.rgb(0xF2, 0x8C, 0x28);
    static final int SURFACE = Color.rgb(0x22, 0x26, 0x2D);
    static final int GREEN = Color.rgb(0x2E, 0x9E, 0x4F);
    static final int GREEN_FOCUS = Color.rgb(0x3C, 0xC2, 0x63);

    private final Activity mAct;

    Ui(Activity act)
    {
        mAct = act;
    }

    int dp(float v)
    {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, mAct.getResources().getDisplayMetrics());
    }

    ScrollView scroll(View child)
    {
        ScrollView sv = new ScrollView(mAct);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(child);
        return sv;
    }

    LinearLayout column()
    {
        LinearLayout l = new LinearLayout(mAct);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(32), dp(24), dp(32), dp(24));
        l.setBackgroundColor(BG);
        return l;
    }

    LinearLayout row()
    {
        LinearLayout l = new LinearLayout(mAct);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    View spacer(int dpH)
    {
        View v = new View(mAct);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpH)));
        return v;
    }

    private TextView text(String s, float sp, int color, boolean bold)
    {
        TextView t = new TextView(mAct);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    TextView title(String s) { return text(s, 26, FG, true); }
    TextView big(String s) { return text(s, 22, FG, true); }
    TextView body(String s) { return text(s, 16, FG, false); }
    TextView small(String s) { return text(s, 13, DIM, false); }
    TextView accent(String s) { return text(s, 16, ACCENT, true); }
    TextView highlight(String s) { return text(s, 20, ACCENT, true); }

    Button button(String label, View.OnClickListener l)
    {
        Button b = new Button(mAct);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTextColor(FG);
        b.setFocusable(true);
        b.setFocusableInTouchMode(false);
        b.setOnClickListener(l);
        b.setPadding(dp(20), dp(12), dp(20), dp(12));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(SURFACE);
        normal.setCornerRadius(dp(10));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(SURFACE);
        focused.setCornerRadius(dp(10));
        focused.setStroke(dp(3), ACCENT);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[] { android.R.attr.state_focused }, focused);
        sl.addState(new int[] { android.R.attr.state_pressed }, focused);
        sl.addState(new int[] {}, normal);
        b.setBackground(sl);
        b.setBackgroundTintList((ColorStateList) null);
        b.setStateListAnimator(null);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), dp(12), dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    /** Big full-width green button for the main action. */
    Button primaryButton(String label, View.OnClickListener l)
    {
        Button b = button(label, l);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(24), dp(22), dp(24), dp(22));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(GREEN);
        normal.setCornerRadius(dp(14));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(GREEN_FOCUS);
        focused.setCornerRadius(dp(14));
        focused.setStroke(dp(4), Color.WHITE);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[] { android.R.attr.state_focused }, focused);
        sl.addState(new int[] { android.R.attr.state_pressed }, focused);
        sl.addState(new int[] {}, normal);
        b.setBackground(sl);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, dp(16));
        b.setLayoutParams(lp);
        return b;
    }

    @SuppressWarnings("deprecation")
    static void immersive(Window w)
    {
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
}

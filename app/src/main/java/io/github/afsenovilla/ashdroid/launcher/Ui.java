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

    /**
     * Settings tile: title + current value, rounded translucent card with a
     * clear focus ring (works with the D-pad). Lays out with weight 1 in a row.
     */
    LinearLayout tile(String title, String value, View.OnClickListener l)
    {
        LinearLayout t = new LinearLayout(mAct);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(l);
        TextView a = text(title, 17, FG, true);
        a.setSingleLine(true);
        a.setEllipsize(android.text.TextUtils.TruncateAt.END);
        a.setPadding(0, 0, 0, dp(2));
        TextView b = text(value, 14, ACCENT, false);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setPadding(0, 0, 0, 0);
        b.setTag("value");
        t.addView(a);
        t.addView(b);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(SURFACE);
        normal.setCornerRadius(dp(12));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(SURFACE);
        focused.setCornerRadius(dp(12));
        focused.setStroke(dp(3), ACCENT);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[] { android.R.attr.state_focused }, focused);
        sl.addState(new int[] { android.R.attr.state_pressed }, focused);
        sl.addState(new int[] {}, normal);
        t.setBackground(sl);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        t.setLayoutParams(lp);
        return t;
    }

    /** Row of tiles with equal widths. */
    LinearLayout tileRow(View... tiles)
    {
        LinearLayout r = new LinearLayout(mAct);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : tiles) r.addView(v);
        if (tiles.length == 1) {
            View filler = new View(mAct);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.setMargins(dp(6), 0, dp(6), 0);
            filler.setLayoutParams(lp);
            r.addView(filler);
        }
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    static final int LIST_BG = Color.rgb(0x1F, 0x23, 0x29);
    static final int LIST_FOCUS = Color.rgb(0x2C, 0x32, 0x3B);
    static final int LIST_LINE = Color.rgb(0x2A, 0x2F, 0x37);
    static final int DANGER = Color.rgb(0xFF, 0x6B, 0x5E);

    /** Rounded group of settings rows (console-style list). */
    LinearLayout group()
    {
        LinearLayout g = new LinearLayout(mAct);
        g.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(LIST_BG);
        bg.setCornerRadius(dp(12));
        g.setBackground(bg);
        g.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(14));
        g.setLayoutParams(lp);
        return g;
    }

    /** Adds a row to a group (with a divider above it if it isn't the first). */
    LinearLayout item(LinearLayout group, String label, View right, boolean danger, View.OnClickListener l)
    {
        if (group.getChildCount() > 0) {
            View line = new View(mAct);
            line.setBackgroundColor(LIST_LINE);
            group.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        }
        LinearLayout r = new LinearLayout(mAct);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), 0, dp(18), 0);
        r.setMinimumHeight(dp(50));
        r.setFocusable(true);
        r.setClickable(true);
        r.setOnClickListener(l);

        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(LIST_FOCUS);
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(ACCENT);
        android.graphics.drawable.LayerDrawable focused =
                new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] { fbg, bar });
        focused.setLayerWidth(1, dp(4));
        focused.setLayerGravity(1, Gravity.LEFT | Gravity.FILL_VERTICAL);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[] { android.R.attr.state_focused }, focused);
        sl.addState(new int[] { android.R.attr.state_pressed }, focused);
        sl.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        r.setBackground(sl);

        TextView t = text(label, 17, danger ? DANGER : FG, false);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) r.addView(right);
        group.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    TextView value(String s)
    {
        TextView t = text(s, 15, DIM, false);
        t.setSingleLine(true);
        return t;
    }

    /** On/off switch drawn like the console's own toggles (display only; the row toggles it). */
    View toggle(boolean on)
    {
        android.widget.FrameLayout f = new android.widget.FrameLayout(mAct);
        GradientDrawable track = new GradientDrawable();
        track.setColor(on ? GREEN : Color.rgb(0x3A, 0x3F, 0x48));
        track.setCornerRadius(dp(10));
        f.setBackground(track);
        View thumb = new View(mAct);
        GradientDrawable k = new GradientDrawable();
        k.setShape(GradientDrawable.OVAL);
        k.setColor(on ? Color.WHITE : DIM);
        thumb.setBackground(k);
        android.widget.FrameLayout.LayoutParams tl = new android.widget.FrameLayout.LayoutParams(dp(16), dp(16));
        tl.gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.RIGHT : Gravity.LEFT);
        tl.setMargins(dp(2), 0, dp(2), 0);
        f.addView(thumb, tl);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(20)));
        return f;
    }

    TextView caption(String s) { return text(s, 12, DIM, true); }

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

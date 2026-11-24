package io.github.afsenovilla.ashdroid.companion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.afsenovilla.ashdroid.R;
import io.github.afsenovilla.ashdroid.companion.DsCompanion.DsState;
import io.github.afsenovilla.ashdroid.launcher.GameInstaller;

/**
 * Draws the battle menu reported by the game on the second screen and turns touches into
 * commands. Everything is drawn by code from the reported texts (no game images), with the
 * game's own font when it can be found.
 */
@SuppressLint("ViewConstructor")
final class DsView extends View
{
    private static final int BG = 0xFF181C24;
    private static final int PANEL = 0xFF2A303C;
    private static final int[] COMMAND_COLORS = { 0xFFD04038, 0xFFD89028, 0xFF48A048, 0xFF3870C8 };
    private static final int[] EFF_COLORS = { 0xFF9098A0, 0xFFF0A040, 0xFF70D860 };
    private static final Map<String, Integer> TYPE_COLORS = new HashMap<>();

    static {
        String[][] t = {
            { "NORMAL", "A8A878" }, { "FIRE", "F08030" }, { "WATER", "6890F0" }, { "ELECTRIC", "E0B820" },
            { "GRASS", "78C850" }, { "ICE", "78C0C0" }, { "FIGHTING", "C03028" }, { "POISON", "A040A0" },
            { "GROUND", "C8A850" }, { "FLYING", "A890F0" }, { "PSYCHIC", "F85888" }, { "BUG", "A8B820" },
            { "ROCK", "B8A038" }, { "GHOST", "705898" }, { "DRAGON", "7038F8" }, { "DARK", "705848" },
            { "STEEL", "9898B0" }, { "FAIRY", "E890A8" }, { "QMARKS", "68A090" }, { "SHADOW", "403060" },
        };
        for (String[] e : t) TYPE_COLORS.put(e[0], 0xFF000000 | Integer.parseInt(e[1], 16));
    }

    private final DsCompanion mOwner;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mOutline = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private DsState mState = new DsState();

    // Touch targets of the current frame: grid buttons (sel:N), then the footer buttons
    private final RectF[] mGrid = { new RectF(), new RectF(), new RectF(), new RectF() };
    private int mGridCount;
    private final RectF mBack = new RectF();
    private final RectF mMega = new RectF();
    private final RectF mToggle = new RectF();
    private boolean mBackShown, mMegaShown;
    /** What the finger went down on: 0-3 grid, 10 back, 11 mega, 12 toggle, -1 nothing. */
    private int mPressed = -1;

    DsView(Context ctx, DsCompanion owner)
    {
        super(ctx);
        mOwner = owner;
        Typeface font = findGameFont(ctx);
        if (font == null) font = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        mText.setTypeface(font);
        mOutline.setTypeface(font);
        mOutline.setStyle(Paint.Style.STROKE);
        mOutline.setStrokeJoin(Paint.Join.ROUND);
        mOutline.setColor(0xC0000000);
        mStroke.setStyle(Paint.Style.STROKE);
        setBackgroundColor(BG);
        setKeepScreenOn(true);
    }

    void setState(DsState s)
    {
        mState = s;
        mPressed = -1;
        invalidate();
    }

    /** First .ttf/.otf in the game's Fonts folder, preferring Pokémon-style fonts (as ControlsArt does). */
    private static Typeface findGameFont(Context ctx)
    {
        try {
            File[] dirs = GameInstaller.gameDir(ctx).listFiles();
            if (dirs == null) return null;
            File best = null;
            for (File d : dirs) {
                if (!d.isDirectory() || !d.getName().equalsIgnoreCase("Fonts")) continue;
                File[] list = d.listFiles();
                if (list == null) continue;
                for (File f : list) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (!(n.endsWith(".ttf") || n.endsWith(".otf")) || n.startsWith("ashdroid_")) continue;
                    boolean preferred = n.contains("power") || n.contains("pokemon") || n.contains("pkmn");
                    if (best == null || preferred) best = f;
                    if (preferred && n.contains("clear")) return Typeface.createFromFile(f);
                }
            }
            return best == null ? null : Typeface.createFromFile(best);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- Drawing ----

    @Override
    protected void onDraw(Canvas c)
    {
        final float w = getWidth(), h = getHeight();
        final float pad = h * 0.03f;
        final DsState s = mState;
        mGridCount = 0;
        mBackShown = false;
        mMegaShown = false;

        // Footer: [Back] [Mega Evolution] ........ [top-screen menu: shown/hidden]
        final float footTop = h * 0.84f;
        final float footH = h - footTop - pad;
        mToggle.set(w * 0.60f, footTop, w - pad, footTop + footH);
        String toggle = getResources().getString(mOwner.hideTopMenu() ? R.string.ds_top_menu_hidden : R.string.ds_top_menu_shown);
        button(c, mToggle, PANEL, false, mPressed == 12);
        label(c, toggle, mToggle.centerX(), mToggle.centerY(), mToggle.width() - pad * 2, footH * 0.34f, 0xFFB8C0CC);

        if (s.screen == DsState.IDLE) return;

        final float gridTop = s.screen == DsState.COMMAND ? h * 0.22f : pad;
        final float gridBottom = footTop - pad;
        final float cellW = (w - pad * 3) / 2f, cellH = (gridBottom - gridTop - pad) / 2f;

        if (s.screen == DsState.COMMAND) {
            String[] lines = s.title.split("\n");
            float size = h * 0.062f;
            float y = (gridTop - (lines.length - 1) * size * 1.2f) / 2f + pad * 0.5f;
            for (String line : lines) {
                label(c, line.trim(), w / 2f, y, w - pad * 2, size, Color.WHITE);
                y += size * 1.2f;
            }
            mGridCount = Math.min(4, s.items.length);
            for (int i = 0; i < mGridCount; i++) {
                RectF r = cell(i, pad, gridTop, cellW, cellH);
                button(c, r, COMMAND_COLORS[i], s.index == i, mPressed == i);
                label(c, s.items[i], r.centerX(), r.centerY(), r.width() - pad * 2, cellH * 0.26f, Color.WHITE);
            }
            return;
        }

        // Moves
        mGridCount = Math.min(4, s.moves.length);
        for (int i = 0; i < mGridCount; i++) {
            DsState.Move m = s.moves[i];
            RectF r = cell(i, pad, gridTop, cellW, cellH);
            Integer col = TYPE_COLORS.get(m.type.toUpperCase(Locale.ROOT));
            button(c, r, col == null ? 0xFF808890 : col, s.index == i, mPressed == i);
            float inner = r.width() - pad * 2;
            label(c, m.name, r.centerX(), r.top + cellH * 0.30f, inner, cellH * 0.20f, Color.WHITE);
            String info = m.typeName + "   PP " + m.pp + "/" + m.totalPp;
            int ppColor = m.pp == 0 ? 0xFFFF7060 : (m.totalPp > 0 && m.pp * 4 <= m.totalPp) ? 0xFFFFD060 : Color.WHITE;
            label(c, info, r.centerX(), r.top + cellH * 0.56f, inner, cellH * 0.13f, ppColor);
            if (m.eff >= 0 && m.eff <= 2) {
                RectF tag = new RectF(r.left + pad, r.top + cellH * 0.70f, r.right - pad, r.top + cellH * 0.92f);
                mFill.setColor(0x70000000);
                c.drawRoundRect(tag, tag.height() / 2f, tag.height() / 2f, mFill);
                label(c, s.eff[m.eff], tag.centerX(), tag.centerY(), tag.width() - pad, cellH * 0.12f, EFF_COLORS[m.eff]);
            }
        }

        mBackShown = true;
        mBack.set(pad, footTop, w * 0.24f, footTop + footH);
        button(c, mBack, 0xFF505868, false, mPressed == 10);
        label(c, s.back, mBack.centerX(), mBack.centerY(), mBack.width() - pad, footH * 0.36f, Color.WHITE);

        if (s.mega > 0) {
            mMegaShown = true;
            mMega.set(w * 0.24f + pad, footTop, w * 0.60f - pad, footTop + footH);
            button(c, mMega, s.mega == 2 ? 0xFFC850D8 : 0xFF604878, s.mega == 2, mPressed == 11);
            label(c, s.megaLabel, mMega.centerX(), mMega.centerY(), mMega.width() - pad, footH * 0.34f, Color.WHITE);
        }
    }

    private RectF cell(int i, float pad, float top, float cw, float ch)
    {
        RectF r = mGrid[i];
        float x = pad + (i % 2) * (cw + pad), y = top + (i / 2) * (ch + pad);
        r.set(x, y, x + cw, y + ch);
        return r;
    }

    /** Rounded button: flat colour, darker lower edge, white frame on the cursor, dimmed while pressed. */
    private void button(Canvas c, RectF r, int color, boolean selected, boolean pressed)
    {
        float rad = Math.min(r.width(), r.height()) * 0.12f;
        mFill.setColor(shade(color, 0.62f));
        c.drawRoundRect(r, rad, rad, mFill);
        float lip = r.height() * 0.06f;
        mFill.setColor(pressed ? shade(color, 0.78f) : color);
        c.drawRoundRect(r.left, r.top, r.right, r.bottom - lip, rad, rad, mFill);
        if (selected) {
            float sw = Math.max(4f, getHeight() * 0.008f);
            mStroke.setStrokeWidth(sw);
            mStroke.setColor(Color.WHITE);
            c.drawRoundRect(r.left + sw / 2, r.top + sw / 2, r.right - sw / 2, r.bottom - sw / 2, rad, rad, mStroke);
        }
    }

    private static int shade(int color, float f)
    {
        return Color.argb(Color.alpha(color), (int) (Color.red(color) * f), (int) (Color.green(color) * f),
                (int) (Color.blue(color) * f));
    }

    /** Centred text with a dark outline, shrunk to fit maxWidth. */
    private void label(Canvas c, String text, float cx, float cy, float maxWidth, float size, int color)
    {
        if (text == null || text.isEmpty()) return;
        mText.setTextSize(size);
        float tw = mText.measureText(text);
        if (tw > maxWidth && tw > 0) {
            size *= maxWidth / tw;
            mText.setTextSize(size);
            tw = mText.measureText(text);
        }
        mOutline.setTextSize(size);
        mOutline.setStrokeWidth(Math.max(2f, size * 0.14f));
        Paint.FontMetrics fm = mText.getFontMetrics();
        float x = cx - tw / 2f, y = cy - (fm.ascent + fm.descent) / 2f;
        c.drawText(text, x, y, mOutline);
        mText.setColor(color);
        c.drawText(text, x, y, mText);
    }

    // ---- Touch ----

    private int hit(float x, float y)
    {
        for (int i = 0; i < mGridCount; i++) if (mGrid[i].contains(x, y)) return i;
        if (mBackShown && mBack.contains(x, y)) return 10;
        if (mMegaShown && mMega.contains(x, y)) return 11;
        if (mToggle.contains(x, y)) return 12;
        return -1;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e)
    {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mPressed = hit(e.getX(), e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP: {
                int target = hit(e.getX(), e.getY());
                if (target >= 0 && target == mPressed) {
                    if (target < 4) mOwner.send("sel:" + target);
                    else if (target == 10) mOwner.send("back");
                    else if (target == 11) mOwner.send("act");
                    else if (target == 12) mOwner.toggleHideTopMenu();
                }
                mPressed = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mPressed = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}

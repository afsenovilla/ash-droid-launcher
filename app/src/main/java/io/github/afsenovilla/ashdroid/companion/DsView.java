package io.github.afsenovilla.ashdroid.companion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.afsenovilla.ashdroid.R;
import io.github.afsenovilla.ashdroid.companion.DsCompanion.DsState;
import io.github.afsenovilla.ashdroid.companion.DsCompanion.DsState.Art;
import io.github.afsenovilla.ashdroid.launcher.GameInstaller;

/**
 * Draws the battle menu reported by the game on the second screen and turns touches into
 * commands. It is built from the game's own menu images, which the game hands over at run
 * time (nothing of the game ships in the APK), with the game's font when it can be found.
 */
@SuppressLint("ViewConstructor")
final class DsView extends View
{
    private static final int BG = 0xFF000000;
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
    private final RectF mToggle = new RectF();
    private final RectF[] mExtra = { new RectF(), new RectF(), new RectF() };
    private final String[] mExtraCmd = new String[3];
    private int mExtraCount;
    private boolean mBackShown;
    /** What the finger went down on: 0-3 grid, 10 back, 12 toggle, 20+ extra buttons, -1 nothing. */
    private int mPressed = -1;

    /** The game's text size (Essentials) and the smallest gap between two enlarged buttons. */
    private static final float FONT_SIZE = 29f, MIN_GAP = 8f;
    /** Where the game writes "What will X do?" inside its 512x96 menu panel. */
    private static final float TEXT_X = 32f, TEXT_Y = 34f, LINE_H = 32f, TEXT_W = 200f;
    private final Paint mBmp = new Paint();
    private final Rect mSrc = new Rect();
    private final RectF mPanel = new RectF(), mTmp = new RectF();
    private final Map<String, Bitmap> mBitmaps = new HashMap<>();
    private int mBackdrop = BG;

    // Prototype: the game's own menus mirrored here
    private final Object mFrameLock = new Object();
    private Bitmap mFrame;
    private final RectF mBag = new RectF();
    private boolean mBagShown;

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
        mBmp.setFilterBitmap(false);
        mBmp.setAntiAlias(false);
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
    //
    // The menus are built from the game's own images (the pieces its menu is showing right
    // now, already translated), enlarged by a whole factor and without smoothing so they stay
    // pixel-sharp, with the game's font and text colours. Where the game handed no image the
    // element is drawn plainly instead, so the screen always works.

    @Override
    protected void onDraw(Canvas c)
    {
        final float w = getWidth(), h = getHeight();
        final float pad = h * 0.03f;
        final DsState s = mState;
        mGridCount = 0;
        mExtraCount = 0;
        mBackShown = false;
        mBagShown = false;

        // The screen takes the colour of the bar the game has behind its menus, so the buttons
        // sit on the same colour as on the game screen; black (like the game's side bars) otherwise.
        Bitmap bar = bitmap(s.strip);
        if (bar != null) {
            int px = Math.min(bar.getWidth() - 1, Math.max(0, s.strip.x + s.strip.w / 2));
            int py = Math.min(bar.getHeight() - 1, Math.max(0, s.strip.y + s.strip.h / 2));
            int col = bar.getPixel(px, py);
            if (Color.alpha(col) == 255) mBackdrop = col;
        }
        c.drawColor(mBackdrop);

        // Bottom-right corner: the launcher's own switch for the menu on the game screen
        final float stripH = h * 0.07f;
        mToggle.set(w * 0.62f, h - stripH - pad * 0.5f, w - pad * 0.5f, h - pad * 0.5f);
        String toggle = getResources().getString(mOwner.hideTopMenu() ? R.string.ds_top_menu_hidden : R.string.ds_top_menu_shown);
        button(c, mToggle, PANEL, false, mPressed == 12);
        label(c, toggle, mToggle.centerX(), mToggle.centerY(), mToggle.width() - pad, stripH * 0.40f, 0xFFB8C0CC);

        if (s.screen == DsState.IDLE) return;
        if (s.screen == DsState.WORLD || s.screen == DsState.MIRROR) {
            // Prototype screens, not the approved overworld design (docs/ds-screen): one plain
            // button to try the game's own bag mirrored here, and the mirrored frame itself.
            c.drawColor(0xFF000000);
            mToggle.setEmpty();
            if (s.screen == DsState.WORLD) drawWorld(c, s, w, h, pad);
            else drawMirror(c, s, w, h);
            return;
        }
        final float top = pad * 0.5f, bottom = mToggle.top - pad * 0.5f;
        if (s.screen == DsState.COMMAND) drawCommand(c, s, w, h, top, bottom, pad);
        else drawFight(c, s, w, h, top, bottom, pad);
    }

    private void drawWorld(Canvas c, DsState s, float w, float h, float pad)
    {
        mBag.set(w * 0.2f, h * 0.36f, w * 0.8f, h * 0.64f);
        boolean on = s.free && s.canMirror;
        button(c, mBag, on ? 0xFFD89028 : 0xFF505058, false, mPressed == 30);
        label(c, getResources().getString(R.string.ds_bag), mBag.centerX(), mBag.centerY(), mBag.width() - pad * 2,
                mBag.height() * 0.30f, on ? Color.WHITE : 0xFFA0A0A8);
        label(c, getResources().getString(s.canMirror ? R.string.ds_prototype : R.string.ds_no_mirror), w / 2f, h * 0.74f,
                w - pad * 2, h * 0.032f, 0xFFB8C0CC);
        mBagShown = on;
    }

    /** The game's own menu, as rendered by the game, enlarged by a whole factor without smoothing. */
    private void drawMirror(Canvas c, DsState s, float w, float h)
    {
        synchronized (mFrameLock) {
            if (mFrame != null) {
                int fw = mFrame.getWidth(), fh = mFrame.getHeight();
                int sc = Math.max(1, (int) Math.floor(Math.min(w / fw, h / fh)));
                float dw = fw * sc, dh = fh * sc;
                mTmp.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f);
                c.drawBitmap(mFrame, null, mTmp, mBmp);
            }
        }
        if (s.mirrorTenths > 0) {
            String cost = String.format(Locale.ROOT, "%.1f ms", s.mirrorTenths / 10f);
            label(c, cost, w * 0.92f, h * 0.972f, w * 0.14f, h * 0.026f, 0xFF8890A0);
        }
    }

    /** Called on the game thread with a new frame (RGBA, top row first). */
    void setFrame(int width, int height, byte[] rgba)
    {
        if (width <= 0 || height <= 0 || rgba.length < width * height * 4) return;
        synchronized (mFrameLock) {
            if (mFrame == null || mFrame.getWidth() != width || mFrame.getHeight() != height) {
                mFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                mFrame.setHasAlpha(false);
            }
            mFrame.copyPixelsFromBuffer(ByteBuffer.wrap(rgba, 0, width * height * 4));
        }
        postInvalidateOnAnimation();
    }

    /** "What will X do?" in the game's menu box, then its four command buttons, enlarged. */
    private void drawCommand(Canvas c, DsState s, float w, float h, float top, float bottom, float pad)
    {
        float ts;
        boolean box = bitmap(s.bg) != null;
        if (box) {
            int so = Math.max(1, (int) Math.floor(w / s.bg.w));
            float hw = s.bg.w * so, hh = s.bg.h * so;
            mPanel.set((w - hw) / 2f, top, (w + hw) / 2f, top + hh);
            drawStrip(c, s, so);
            draw(c, s.bg, mPanel);
            ts = so;
        } else {
            mPanel.set(pad, top, w - pad, top + h * 0.18f);
            mFill.setColor(0xFFF8F8F8);
            c.drawRoundRect(mPanel, pad * 0.5f, pad * 0.5f, mFill);
            ts = mPanel.height() / 96f;
        }
        // The question goes where the game writes it: left-aligned in the box at the left of the
        // panel, first line near the top (TEXT_X / TEXT_Y / LINE_H are the game's own layout).
        String[] lines = s.title.split("\n");
        for (int i = 0; i < lines.length; i++) {
            float y = mPanel.top + (TEXT_Y + LINE_H * i) * ts;
            if (box) {
                gameText(c, lines[i].trim(), mPanel.left + TEXT_X * ts, y, TEXT_W * ts, FONT_SIZE * ts, s.base, s.shadow, 2f * ts, 0f);
            } else {
                gameText(c, lines[i].trim(), w / 2f, y, mPanel.width() - 32f * ts, FONT_SIZE * ts, s.base, s.shadow, 2f * ts, 0.5f);
            }
        }

        mGridCount = Math.min(4, s.items.length);
        Art first = null;
        for (Art a : s.buttons) if (a != null && first == null) first = a;
        float bw = first != null ? first.w : 130f, bh = first != null ? first.h : 46f;
        float areaTop = mPanel.bottom + pad * 0.5f;
        int sc = Math.max(1, (int) Math.floor((w - 3 * MIN_GAP) / (2 * bw)));
        while (sc > 1 && 2 * bh * sc + pad > bottom - areaTop) sc--;
        float cw = bw * sc, ch = bh * sc;
        float gap = Math.min(pad, (w - 2 * cw) / 3f);
        float gx = (w - 2 * cw - gap) / 2f, gy = areaTop + (bottom - areaTop - 2 * ch - gap) / 2f;
        for (int i = 0; i < mGridCount; i++) {
            RectF r = cell(i, gx, gy, cw, ch, gap, gap);
            Art a = i < s.buttons.length ? s.buttons[i] : null;
            if (!draw(c, a, r)) {
                button(c, r, COMMAND_COLORS[i], s.index == i, false);
                label(c, s.items[i], r.centerX(), r.centerY(), r.width() - pad * 2, ch * 0.30f, Color.WHITE);
            }
            if (mPressed == i) dim(c, r);
        }
    }

    /**
     * The game's move buttons enlarged in a 2x2 grid (effectiveness under each one), and under
     * them the game's own move panel with the type and PP of the highlighted move where the
     * game shows them, plus Back and the game's Mega Evolution / Shift buttons.
     */
    private void drawFight(Canvas c, DsState s, float w, float h, float top, float bottom, float pad)
    {
        float fs;
        if (bitmap(s.bg) != null) {
            int so = Math.max(1, (int) Math.floor(w / s.bg.w));
            float fw = s.bg.w * so, fh = s.bg.h * so;
            mPanel.set((w - fw) / 2f, bottom - fh, (w + fw) / 2f, bottom);
            drawStrip(c, s, so);
            draw(c, s.bg, mPanel);
            fs = so;
        } else {
            mPanel.set(pad, bottom - h * 0.18f, w - pad, bottom);
            mFill.setColor(0xFFF8F8F8);
            c.drawRoundRect(mPanel, pad * 0.5f, pad * 0.5f, mFill);
            fs = mPanel.height() / 96f;
        }

        // Type and PP of the highlighted move, where the game's panel has them (its right end)
        DsState.Move current = s.index >= 0 && s.index < s.moves.length ? s.moves[s.index] : null;
        if (bitmap(s.typeIcon) != null) {
            float ix = mPanel.right - 96f * fs, iy = mPanel.top + 20f * fs;
            mTmp.set(ix, iy, ix + s.typeIcon.w * fs, iy + s.typeIcon.h * fs);
            draw(c, s.typeIcon, mTmp);
        } else if (current != null) {
            gameText(c, current.typeName, mPanel.right - 64f * fs, mPanel.top + 32f * fs, 110f * fs, FONT_SIZE * fs,
                    s.base, s.shadow, 2f * fs, 0.5f);
        }
        String pp = !s.ppText.isEmpty() ? s.ppText : current != null ? "PP: " + current.pp + "/" + current.totalPp : "";
        gameText(c, pp, mPanel.right - 64f * fs, mPanel.top + 62f * fs, 120f * fs, FONT_SIZE * fs, s.ppBase, s.ppShadow, 2f * fs, 0.5f);

        // Back, then the game's toggle buttons, from the left end of the panel
        mBackShown = true;
        float bx = mPanel.left + 10f * fs;
        if (bitmap(s.backBtn) != null) {
            float bwid = s.backBtn.w * fs, bhei = s.backBtn.h * fs;
            mBack.set(bx, mPanel.centerY() - bhei / 2f, bx + bwid, mPanel.centerY() + bhei / 2f);
            draw(c, s.backBtn, mBack);
        } else {
            mBack.set(bx, mPanel.top + 22f * fs, bx + 120f * fs, mPanel.bottom - 22f * fs);
            button(c, mBack, 0xFF505868, false, false);
            label(c, s.back, mBack.centerX(), mBack.centerY(), mBack.width() - 12f * fs, 22f * fs, Color.WHITE);
        }
        if (mPressed == 10) dim(c, mBack);
        bx = mBack.right + 8f * fs;
        float limit = mPanel.right - 110f * fs;
        for (Art a : s.extras) {
            if (mExtraCount >= mExtra.length || bitmap(a) == null) continue;
            float ew = a.w * fs, eh = a.h * fs;
            if (bx + ew > limit) break;
            RectF r = mExtra[mExtraCount];
            r.set(bx, mPanel.centerY() - eh / 2f, bx + ew, mPanel.centerY() + eh / 2f);
            draw(c, a, r);
            if (mPressed == 20 + mExtraCount) dim(c, r);
            mExtraCmd[mExtraCount++] = a.cmd;
            bx = r.right + 8f * fs;
        }
        if (mExtraCount == 0 && s.mega > 0 && bx + 150f * fs <= limit) {
            RectF r = mExtra[0];
            r.set(bx, mPanel.top + 22f * fs, bx + 150f * fs, mPanel.bottom - 22f * fs);
            button(c, r, s.mega == 2 ? 0xFFC850D8 : 0xFF604878, s.mega == 2, mPressed == 20);
            label(c, s.megaLabel, r.centerX(), r.centerY(), r.width() - 12f * fs, 20f * fs, Color.WHITE);
            mExtraCmd[0] = "act";
            mExtraCount = 1;
        }

        // Moves
        mGridCount = Math.min(4, s.moves.length);
        Art first = null;
        for (DsState.Move m : s.moves) if (m.art != null && first == null) first = m.art;
        float bw = first != null ? first.w : 192f, bh = first != null ? first.h : 46f;
        float tagH = h * 0.05f;
        float areaBottom = mPanel.top - pad * 0.5f;
        int sc = Math.max(1, (int) Math.floor((w - 3 * MIN_GAP) / (2 * bw)));
        while (sc > 1 && 2 * (bh * sc + tagH) + pad > areaBottom - top) sc--;
        float cw = bw * sc, ch = bh * sc;
        float gapX = Math.min(pad, (w - 2 * cw) / 3f), gapY = tagH + gapX * 0.5f;
        float gx = (w - 2 * cw - gapX) / 2f;
        float gy = top + (areaBottom - top - (2 * ch + gapY + tagH)) / 2f;
        for (int i = 0; i < mGridCount; i++) {
            DsState.Move m = s.moves[i];
            RectF r = cell(i, gx, gy, cw, ch, gapX, gapY);
            if (draw(c, m.art, r)) {
                int col = m.nameColor != 0 ? m.nameColor : s.base;
                gameText(c, m.name, r.centerX(), r.top + ch * 0.47f, (bw - 16f) * sc, FONT_SIZE * sc, col, s.shadow, 2f * sc, 0.5f);
            } else {
                Integer col = TYPE_COLORS.get(m.type.toUpperCase(Locale.ROOT));
                button(c, r, col == null ? 0xFF808890 : col, s.index == i, false);
                label(c, m.name, r.centerX(), r.centerY(), r.width() - pad, ch * 0.34f, Color.WHITE);
            }
            if (m.eff >= 0 && m.eff <= 2) {
                label(c, s.eff[m.eff], r.centerX(), r.bottom + tagH * 0.5f, cw, tagH * 0.62f, EFF_COLORS[m.eff]);
            }
            if (mPressed == i) dim(c, r);
        }
    }

    private Bitmap bitmap(Art a)
    {
        if (a == null) return null;
        if (mBitmaps.containsKey(a.img)) return mBitmaps.get(a.img);
        if (mBitmaps.size() > 32) mBitmaps.clear();
        Bitmap b = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeFile(a.img, o);
        } catch (RuntimeException | OutOfMemoryError ignored) {
        }
        mBitmaps.put(a.img, b);
        return b;
    }

    /** Draws a piece of a game image into dst, enlarged without smoothing. False if there is none. */
    private boolean draw(Canvas c, Art a, RectF dst)
    {
        Bitmap b = bitmap(a);
        if (b == null) return false;
        mSrc.set(Math.max(0, a.x), Math.max(0, a.y), Math.min(b.getWidth(), a.x + a.w), Math.min(b.getHeight(), a.y + a.h));
        if (mSrc.isEmpty()) return false;
        c.drawBitmap(b, mSrc, dst, mBmp);
        return true;
    }

    /** The game's bar behind the menu panel (same place and size as on the game screen). */
    private void drawStrip(Canvas c, DsState s, int scale)
    {
        if (bitmap(s.strip) == null) return;
        float sw = s.strip.w * scale, sh = s.strip.h * scale;
        mTmp.set(mPanel.centerX() - sw / 2f, mPanel.bottom - sh, mPanel.centerX() + sw / 2f, mPanel.bottom);
        draw(c, s.strip, mTmp);
    }

    private void dim(Canvas c, RectF r)
    {
        mFill.setColor(0x50000000);
        c.drawRect(r, mFill);
    }

    /**
     * Text the way the game draws it: base colour over a shadow to the right and below.
     * align 0 starts it at x, 0.5 centres it on x.
     */
    private void gameText(Canvas c, String text, float ax, float cy, float maxWidth, float size, int base, int shadow, float off, float align)
    {
        if (text == null || text.isEmpty()) return;
        mText.setTextSize(size);
        float tw = mText.measureText(text);
        if (tw > maxWidth && tw > 0) {
            mText.setTextSize(size * maxWidth / tw);
            tw = mText.measureText(text);
        }
        Paint.FontMetrics fm = mText.getFontMetrics();
        float x = ax - tw * align, y = cy - (fm.ascent + fm.descent) / 2f;
        mText.setColor(shadow);
        c.drawText(text, x + off, y, mText);
        c.drawText(text, x, y + off, mText);
        c.drawText(text, x + off, y + off, mText);
        mText.setColor(base);
        c.drawText(text, x, y, mText);
    }

    private RectF cell(int i, float left, float top, float cw, float ch, float gapX, float gapY)
    {
        RectF r = mGrid[i];
        float x = left + (i % 2) * (cw + gapX), y = top + (i / 2) * (ch + gapY);
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
        for (int i = 0; i < mExtraCount; i++) if (mExtra[i].contains(x, y)) return 20 + i;
        if (mBagShown && mBag.contains(x, y)) return 30;
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
                    else if (target == 12) mOwner.toggleHideTopMenu();
                    else if (target == 30) mOwner.send("open:bag");
                    else if (target >= 20 && target - 20 < mExtraCount) mOwner.send(mExtraCmd[target - 20]);
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

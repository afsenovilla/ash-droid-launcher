package io.github.afsenovilla.ashdroid.companion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The second screen. What it shows is decided by the game-side patch and drawn by {@link DsScene}; this
 * view only lends it Android's canvas, loads the game's images and fonts from the player's own copy of the
 * game (nothing of the game ships in the APK) and passes touches on.
 */
@SuppressLint("ViewConstructor")
final class DsView extends View implements DsScene.Gfx
{
    private static final String TAG = "AshDroid[DS]";
    private static final int CACHE = 260;

    private final DsCompanion mOwner;
    private final DsScene mScene = new DsScene();
    private final Paint mBmp = new Paint();
    private final Paint mFill = new Paint();
    private final Paint mText = new Paint();
    private final Rect mSrc = new Rect(), mDst = new Rect();
    private final Map<String, Bitmap> mImages = lru();
    private final Map<String, Bitmap> mArt = lru();
    private final Map<String, Typeface> mFonts = new HashMap<>();
    private Canvas mCanvas;
    private float mScale = 1f, mOffX, mOffY;

    private static Map<String, Bitmap> lru()
    {
        return new LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > CACHE; }
        };
    }

    DsView(Context context, DsCompanion owner)
    {
        super(context);
        mOwner = owner;
        mBmp.setFilterBitmap(false);
        mBmp.setAntiAlias(false);
        mBmp.setDither(false);
        mFill.setStyle(Paint.Style.FILL);
        mFill.setAntiAlias(false);
        mText.setAntiAlias(false);
        mText.setSubpixelText(false);
        mText.setLinearText(false);
        mText.setHinting(Paint.HINTING_OFF);
        if (!DsArt.fontsLoaded()) {
            try (InputStream in = context.getAssets().open("pixelfonts.txt")) {
                DsArt.loadFonts(in);
            } catch (Exception e) {
                Log.w(TAG, "Lettering not loaded: " + e);
            }
        }
    }

    void setState(Map<String, Object> state)
    {
        mScene.setState(state);
        invalidate();
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas canvas)
    {
        canvas.drawColor(0xFF000000);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        mScale = Math.min(w / (float) DsScene.W, h / (float) DsScene.H);
        mOffX = (w - DsScene.W * mScale) / 2f;
        mOffY = (h - DsScene.H * mScale) / 2f;
        canvas.save();
        canvas.translate(mOffX, mOffY);
        canvas.scale(mScale, mScale);
        canvas.clipRect(0, 0, DsScene.W, DsScene.H);
        mCanvas = canvas;
        try {
            mScene.draw(this);
        } catch (RuntimeException e) {
            Log.w(TAG, "Draw failed: " + e);
        }
        mCanvas = null;
        canvas.restore();
        if (mScene.animating()) postInvalidateOnAnimation();
    }

    private Bitmap load(String file, String fx)
    {
        String key = fx.isEmpty() ? file : file + "|" + fx;
        if (mImages.containsKey(key)) return mImages.get(key);
        Bitmap bmp = null;
        try {
            if (new File(file).isFile()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inScaled = false;
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                o.inPremultiplied = true;
                bmp = BitmapFactory.decodeFile(file, o);
                if (bmp != null && !fx.isEmpty()) {
                    int bw = bmp.getWidth(), bh = bmp.getHeight();
                    int[] px = new int[bw * bh];
                    bmp.getPixels(px, 0, bw, 0, 0, bw, bh);
                    DsScene.applyFx(px, fx);
                    bmp = Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888);
                }
            }
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "Image not loaded: " + file + " " + e);
        }
        mImages.put(key, bmp);
        return bmp;
    }

    private Typeface typeface(int which)
    {
        String path = mScene.font(which);
        if (path == null) path = mScene.font(0);
        if (path == null) return Typeface.MONOSPACE;
        Typeface t = mFonts.get(path);
        if (t == null) {
            try {
                t = Typeface.createFromFile(path);
            } catch (RuntimeException e) {
                Log.w(TAG, "Font not loaded: " + path + " " + e);
                t = Typeface.MONOSPACE;
            }
            mFonts.put(path, t);
        }
        return t;
    }

    @Override
    public void fill(int x, int y, int w, int h, int argb)
    {
        if (mCanvas == null) return;
        mFill.setColor(argb);
        mCanvas.drawRect(x, y, x + w, y + h, mFill);
    }

    @Override
    public void image(String file, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh, String fx, int alpha)
    {
        Bitmap bmp = load(file, fx == null ? "" : fx);
        if (bmp == null || mCanvas == null) return;
        mSrc.set(sx, sy, sx + sw, sy + sh);
        mDst.set(dx, dy, dx + dw, dy + dh);
        mBmp.setAlpha(alpha);
        mCanvas.drawBitmap(bmp, mSrc, mDst, mBmp);
    }

    @Override
    public int[] imageSize(String file)
    {
        Bitmap bmp = load(file, "");
        return bmp == null ? null : new int[] { bmp.getWidth(), bmp.getHeight() };
    }

    @Override
    public void pixels(String key, int[] argb, int w, int h, int dx, int dy, int scale, int alpha)
    {
        if (mCanvas == null) return;
        Bitmap bmp = mArt.get(key);
        if (bmp == null) {
            try {
                bmp = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
            } catch (RuntimeException | OutOfMemoryError e) {
                return;
            }
            mArt.put(key, bmp);
        }
        mDst.set(dx, dy, dx + w * scale, dy + h * scale);
        mBmp.setAlpha(alpha);
        mCanvas.drawBitmap(bmp, null, mDst, mBmp);
    }

    @Override
    public float textWidth(String s, int size, int font)
    {
        mText.setTypeface(typeface(font));
        mText.setTextSize(size);
        return mText.measureText(s);
    }

    @Override
    public void text(String s, int x, int baseline, int size, int font, int argb)
    {
        if (mCanvas == null) return;
        mText.setTypeface(typeface(font));
        mText.setTextSize(size);
        mText.setColor(argb);
        mCanvas.drawText(s, x, baseline, mText);
    }

    @Override
    public void clip(int x, int y, int w, int h)
    {
        if (mCanvas == null) return;
        mCanvas.save();
        mCanvas.clipRect(x, y, x + w, y + h);
    }

    @Override
    public void unclip()
    {
        if (mCanvas != null) mCanvas.restore();
    }

    // ---- touch ----

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        int x = Math.round((event.getX() - mOffX) / mScale), y = Math.round((event.getY() - mOffY) / mScale);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mScene.touchDown(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                mScene.touchMove(x, y);
                break;
            case MotionEvent.ACTION_UP: {
                String cmd = mScene.touchUp(x, y);
                if (cmd != null && !cmd.isEmpty()) mOwner.send(cmd);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                mScene.touchCancel();
                break;
            default:
                return true;
        }
        invalidate();
        return true;
    }
}

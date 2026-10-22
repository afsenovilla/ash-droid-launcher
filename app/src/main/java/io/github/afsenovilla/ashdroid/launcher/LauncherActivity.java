package io.github.afsenovilla.ashdroid.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;

import io.github.afsenovilla.ashdroid.MainActivity;
import io.github.afsenovilla.ashdroid.R;

/**
 * Entry point. First run: import the game's original .zip, detect the device
 * and run the button wizard. Afterwards: one-button "Jugar".
 */
public class LauncherActivity extends Activity
{
    private static final int REQ_IMPORT = 1;
    private static final int REQ_WIZARD = 2;
    private static final int REQ_WIZARD_THEN_PLAY = 3;

    private Ui mUi;
    private LinearLayout mRoot;
    private DeviceProfile mDevice;
    private volatile boolean mBusy = false;
    private View mScaleButton;
    private Button mPlayButton;
    private android.widget.FrameLayout mFrame;
    private boolean mStarting = false;

    // Auto-start: on a fresh app launch, start the game after a short countdown.
    // Any button press or touch cancels it and stays in the menu.
    private static final String PREFS = "ashdroid";
    private static final String KEY_AUTOSTART = "autostart";
    private static final int AUTOSTART_SECONDS = 3;
    private boolean mAutostartPending = false;
    private int mCountdown;
    private final android.os.Handler mHandler = new android.os.Handler();
    private final Runnable mTick = new Runnable() {
        @Override
        public void run()
        {
            if (!mAutostartPending || mPlayButton == null) return;
            if (mCountdown <= 0) {
                mAutostartPending = false;
                play();
                return;
            }
            mPlayButton.setText(getString(R.string.btn_play_countdown, mCountdown));
            mCountdown--;
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mUi = new Ui(this);
        mRoot = mUi.column();
        mRoot.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        android.widget.ScrollView sv = mUi.scroll(mRoot);
        sv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        mFrame = new android.widget.FrameLayout(this);
        mFrame.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[] { 0xFF1A1D23, 0xFF101216 }));
        mFrame.addView(sv, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(mFrame);
        // Only on a fresh launch from the home screen, not when returning from the game
        mAutostartPending = savedInstanceState == null && autostartEnabled();
    }

    private boolean autostartEnabled()
    {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTOSTART, true);
    }

    private void cancelAutostart()
    {
        if (!mAutostartPending) return;
        mAutostartPending = false;
        mHandler.removeCallbacks(mTick);
        if (mPlayButton != null) mPlayButton.setText(R.string.btn_play);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent evt)
    {
        if (mAutostartPending && evt.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            cancelAutostart();
            return true; // swallow the press that cancelled
        }
        return super.dispatchKeyEvent(evt);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent evt)
    {
        if (mAutostartPending && evt.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            cancelAutostart();
            return true;
        }
        return super.dispatchTouchEvent(evt);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        cancelAutostart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (!mBusy) render();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(getWindow());
    }

    // ---------------------------------------------------------------- screens

    private void render()
    {
        mDevice = DeviceProfile.detect(this);
        mScaleButton = null;
        mPlayButton = null;
        mRoot.removeAllViews();
        mRoot.setOrientation(LinearLayout.VERTICAL);

        if (!GameInstaller.isInstalled(this)) {
            renderWelcome();
        } else {
            renderMain();
        }
    }

    private void renderWelcome()
    {
        mRoot.addView(mUi.title(getString(R.string.launcher_title)));
        mRoot.addView(mUi.small("v" + io.github.afsenovilla.ashdroid.BuildConfig.VERSION_NAME));
        mRoot.addView(mUi.spacer(12));
        mRoot.addView(mUi.body(getString(R.string.welcome_body)));
        mRoot.addView(mUi.small(getString(R.string.welcome_hint)));
        mRoot.addView(mUi.spacer(16));
        mRoot.addView(deviceInfo());
        mRoot.addView(mUi.spacer(16));
        Button imp = mUi.primaryButton(getString(R.string.btn_import), v -> pickZip());
        mRoot.addView(imp);
        imp.requestFocus();
    }

    private void renderMain()
    {
        mRoot.setOrientation(LinearLayout.HORIZONTAL);

        // ---- left: game + Play
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(android.view.Gravity.CENTER_VERTICAL);
        left.setPadding(0, 0, mUi.dp(24), 0);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));

        TextView title = mUi.title(GameInstaller.gameTitle(this));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32);
        title.setShadowLayer(8, 0, 2, 0xFF000000);
        left.addView(title);
        left.addView(mUi.small(getString(R.string.launcher_title) + "  ·  v"
                + io.github.afsenovilla.ashdroid.BuildConfig.VERSION_NAME));

        mPlayButton = mUi.primaryButton(getString(R.string.btn_play), v -> { cancelAutostart(); play(); });
        left.addView(mPlayButton);
        TextView autoHint = mUi.small(getString(R.string.autostart_hint));
        autoHint.setVisibility(View.GONE);
        left.addView(autoHint);

        final TextView status = mUi.small(deviceLine() + "\n" + controlsInfo());
        left.addView(status);
        mRoot.addView(left);

        // ---- right: console-style settings list
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(android.view.Gravity.CENTER_VERTICAL);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));

        TextView cap = mUi.caption(getString(R.string.settings).toUpperCase(java.util.Locale.getDefault()));
        cap.setPadding(mUi.dp(4), 0, 0, mUi.dp(4));
        right.addView(cap);
        LinearLayout g1 = mUi.group();
        mUi.item(g1, getString(R.string.tile_buttons), mUi.value(buttonsValue() + "  ›"), false, v ->
                startActivityForResult(new Intent(this, ButtonWizardActivity.class), REQ_WIZARD));
        mScaleButton = mUi.item(g1, getString(R.string.tile_display),
                mUi.value("‹  " + ConfigWriter.scaleMode(this).label(this) + "  ›"), false, v -> cycleScaleMode());
        mUi.item(g1, getString(R.string.tile_autostart), mUi.toggle(autostartEnabled()), false, v -> toggleAutostart());
        mUi.item(g1, getString(R.string.tile_touch), mUi.toggle(ButtonMapping.touchControlsEnabled(this)), false, v -> {
            ButtonMapping.setTouchControlsEnabled(this, !ButtonMapping.touchControlsEnabled(this));
            render();
            focusItem(3);
        });
        right.addView(g1);

        TextView cap2 = mUi.caption(getString(R.string.game_section).toUpperCase(java.util.Locale.getDefault()));
        cap2.setPadding(mUi.dp(4), mUi.dp(6), 0, mUi.dp(4));
        right.addView(cap2);
        LinearLayout g2 = mUi.group();
        mUi.item(g2, getString(R.string.tile_reimport), mUi.value(getString(R.string.tile_reimport_value) + "  ›"),
                false, v -> pickZip());
        mUi.item(g2, getString(R.string.tile_delete), mUi.value(getString(R.string.tile_delete_value)), true,
                v -> confirmDelete());
        right.addView(g2);
        mRoot.addView(right);

        mPlayButton.requestFocus();

        // Display details need the generated config: do it off the UI thread
        new Thread(() -> {
            String info;
            try {
                ConfigWriter.Result r = ConfigWriter.write(this, mDevice);
                info = getString(R.string.display_info, r.gameW, r.gameH, mDevice.screenW, mDevice.screenH, r.description);
            } catch (IOException e) {
                info = getString(R.string.config_failed, e.getMessage());
            }
            final String s2 = info;
            runOnUiThread(() -> status.setText(deviceLine() + "\n" + controlsInfo() + "\n" + s2));
        }, "config").start();

        // Auto-start only when everything is ready (no wizard pending)
        boolean ready = ButtonMapping.isConfigured(this) || !ButtonMapping.hasPhysicalGamepad();
        if (mAutostartPending && ready) {
            mCountdown = AUTOSTART_SECONDS;
            mHandler.removeCallbacks(mTick);
            mHandler.post(mTick);
            autoHint.setVisibility(View.VISIBLE);
        } else {
            mAutostartPending = false;
        }
    }

    /** Puts the focus back on a row of the settings list after re-rendering. */
    private void focusItem(int index)
    {
        try {
            LinearLayout right = (LinearLayout) mRoot.getChildAt(1);
            LinearLayout g1 = (LinearLayout) right.getChildAt(1);
            g1.getChildAt(index * 2).requestFocus(); // rows alternate with dividers
        } catch (RuntimeException ignored) {
        }
    }

    private String buttonsValue()
    {
        ButtonMapping.ensureConfigured(this, mDevice);
        if (!ButtonMapping.isConfigured(this)) return getString(R.string.tile_buttons_unset);
        if (ButtonMapping.isAutomatic(this)) return getString(R.string.tile_buttons_auto);
        return getString(ButtonMapping.load(this).isIdentity() ? R.string.tile_buttons_default : R.string.tile_buttons_custom);
    }

    private String deviceLine()
    {
        String c = mDevice.controllerName != null ? mDevice.controllerName : getString(R.string.controller_none);
        return mDevice.displayName() + "  ·  " + mDevice.screenW + "x" + mDevice.screenH
                + String.format(java.util.Locale.ROOT, "  ·  %.0f Hz", mDevice.refreshRate)
                + "\n" + getString(R.string.controller_info, c);
    }

    private View deviceInfo()
    {
        String s = getString(R.string.device_info, mDevice.displayName())
                + (mDevice.isKnown() ? getString(R.string.known_profile) : "")
                + "\n" + getString(R.string.screen_info, mDevice.screenW, mDevice.screenH, mDevice.refreshRate)
                + "\n" + getString(R.string.controller_info, mDevice.controllerName != null
                        ? mDevice.controllerName : getString(R.string.controller_none));
        if (mDevice.notes != null && !mDevice.notes.isEmpty()) s += "\n" + mDevice.notes;
        return mUi.small(s);
    }

    private String controlsInfo()
    {
        ButtonMapping.ensureConfigured(this, mDevice);
        if (!ButtonMapping.isConfigured(this)) return getString(R.string.buttons_unset);
        ButtonMapping m = ButtonMapping.load(this);
        if (m.isIdentity()) return getString(R.string.buttons_default);
        StringBuilder sb = new StringBuilder(getString(ButtonMapping.isAutomatic(this)
                ? R.string.buttons_auto_prefix : R.string.buttons_prefix));
        for (ButtonMapping.Action a : ButtonMapping.ACTIONS) {
            Integer k = m.physicalFor(a);
            if (k != null) sb.append("  ").append(a.label(this)).append("=").append(ButtonMapping.keyName(this, k)).append(" ·");
        }
        if (sb.charAt(sb.length() - 1) == '·') sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    // ---------------------------------------------------------------- actions

    private void play()
    {
        if (mStarting) return;
        if (!ButtonMapping.ensureConfigured(this, DeviceProfile.detect(this))
                && ButtonMapping.hasPhysicalGamepad()) {
            startActivityForResult(new Intent(this, ButtonWizardActivity.class), REQ_WIZARD_THEN_PLAY);
            return;
        }
        mStarting = true;
        if (mPlayButton != null) {
            mPlayButton.setText(R.string.preparing);
            mPlayButton.setEnabled(false);
        }
        new Thread(() -> {
            String err = null;
            try {
                ConfigWriter.write(this, DeviceProfile.detect(this));
            } catch (IOException e) {
                err = e.getMessage();
            }
            final String e2 = err;
            runOnUiThread(() -> {
                mStarting = false;
                if (mPlayButton != null) {
                    mPlayButton.setText(R.string.btn_play);
                    mPlayButton.setEnabled(true);
                }
                if (e2 != null) {
                    showError(getString(R.string.err_prepare_config), e2);
                } else {
                    startActivity(new Intent(this, MainActivity.class));
                }
            });
        }, "prepare").start();
    }

    private void toggleAutostart()
    {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_AUTOSTART, !autostartEnabled()).apply();
        render();
        focusItem(2);
    }

    private void cycleScaleMode()
    {
        ConfigWriter.ScaleMode[] all = ConfigWriter.ScaleMode.values();
        ConfigWriter.ScaleMode cur = ConfigWriter.scaleMode(this);
        ConfigWriter.setScaleMode(this, all[(cur.ordinal() + 1) % all.length]);
        render();
        // keep focus on the same tile for quick cycling with the controller
        if (mScaleButton != null) mScaleButton.requestFocus();
    }

    private void pickZip()
    {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/zip", "application/x-zip-compressed", "application/octet-stream" });
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            showError(getString(R.string.err_no_picker_title), getString(R.string.err_no_picker));
        }
    }

    /** Shows what the runtime patch reported during the last game session. */
    private void showDiagnostics()
    {
        java.io.File game = GameInstaller.gameDir(this);
        StringBuilder sb = new StringBuilder();
        sb.append("v").append(io.github.afsenovilla.ashdroid.BuildConfig.VERSION_NAME).append('\n');
        for (String f : new String[] { "mkxp.json", "ashdroid_patches.rb", "ashdroid_es.dat",
                "ashdroid_art/index.txt" }) {
            java.io.File x = new java.io.File(game, f);
            sb.append(f).append(": ").append(x.exists() ? x.length() + " B" : "—").append('\n');
        }
        sb.append("\n");
        java.io.File log = new java.io.File(game, "ashdroid_patch.log");
        try {
            sb.append(log.exists() ? Util.readText(log) : getString(R.string.diag_no_log));
        } catch (IOException e) {
            sb.append(e.toString());
        }
        TextView tv = mUi.small(sb.toString());
        tv.setTextIsSelectable(true);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setPadding(32, 16, 32, 16);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle(R.string.btn_diagnostics).setView(sv)
                .setPositiveButton(R.string.ok, null).show();
    }

    private void confirmDelete()
    {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_msg)
                .setPositiveButton(R.string.delete, (d, w) -> { GameInstaller.uninstall(this); render(); })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            startImport(data.getData());
        } else if (requestCode == REQ_WIZARD_THEN_PLAY && resultCode == RESULT_OK) {
            play();
        }
    }

    private void startImport(Uri uri)
    {
        mBusy = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mRoot.removeAllViews();
        mRoot.addView(mUi.title(getString(R.string.importing_title)));
        mRoot.addView(mUi.small(getString(R.string.importing_hint)));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(true);
        mRoot.addView(bar);
        TextView status = mUi.small("");
        mRoot.addView(status);

        final long[] lastUi = { 0 };
        new Thread(() -> {
            String error = null;
            try {
                GameInstaller.importZip(this, uri, (done, total, file) -> {
                    long now = System.currentTimeMillis();
                    if (now - lastUi[0] < 150) return;
                    lastUi[0] = now;
                    runOnUiThread(() -> {
                        if (total > 0) {
                            bar.setIndeterminate(false);
                            bar.setProgress((int) (done * 1000 / total));
                            status.setText(getString(R.string.import_progress,
                                    (int) (done >> 20), (int) (total >> 20), file));
                        } else {
                            status.setText(getString(R.string.import_progress_nototal, (int) (done >> 20), file));
                        }
                    });
                });
                ConfigWriter.write(this, DeviceProfile.detect(this));
            } catch (IOException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = e.toString();
            }
            final String err = error;
            runOnUiThread(() -> {
                mBusy = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                render();
                if (err != null) {
                    showError(getString(R.string.err_import_title), err);
                } else if (!ButtonMapping.ensureConfigured(this, DeviceProfile.detect(this))
                        && ButtonMapping.hasPhysicalGamepad()) {
                    startActivityForResult(new Intent(this, ButtonWizardActivity.class), REQ_WIZARD);
                }
            });
        }, "game-import").start();
    }

    private void showError(String title, String msg)
    {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton(R.string.ok, null).show();
    }
}

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
    private Button mScaleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mUi = new Ui(this);
        mRoot = mUi.column();
        setContentView(mUi.scroll(mRoot));
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
        mRoot.removeAllViews();
        mRoot.addView(mUi.title(getString(R.string.launcher_title)));

        if (!GameInstaller.isInstalled(this)) {
            renderWelcome();
        } else {
            renderMain();
        }
    }

    private void renderWelcome()
    {
        mRoot.addView(mUi.body(getString(R.string.welcome_body)));
        mRoot.addView(mUi.small(getString(R.string.welcome_hint)));
        mRoot.addView(mUi.spacer(16));
        mRoot.addView(deviceInfo());
        mRoot.addView(mUi.spacer(16));
        Button imp = mUi.button(getString(R.string.btn_import), v -> pickZip());
        mRoot.addView(imp);
        imp.requestFocus();
    }

    private void renderMain()
    {
        mRoot.addView(mUi.big(GameInstaller.gameTitle(this)));

        String scaleInfo;
        try {
            ConfigWriter.Result r = ConfigWriter.write(this, mDevice);
            scaleInfo = getString(R.string.display_info,
                    r.gameW, r.gameH, mDevice.screenW, mDevice.screenH, r.description);
        } catch (IOException e) {
            scaleInfo = getString(R.string.config_failed, e.getMessage());
        }

        mRoot.addView(deviceInfo());
        mRoot.addView(mUi.small(scaleInfo));
        mRoot.addView(mUi.small(controlsInfo()));
        mRoot.addView(mUi.spacer(16));

        Button play = mUi.button(getString(R.string.btn_play), v -> play());
        mRoot.addView(play);
        mRoot.addView(mUi.button(getString(R.string.btn_buttons), v ->
                startActivityForResult(new Intent(this, ButtonWizardActivity.class), REQ_WIZARD)));
        mScaleButton = mUi.button(getString(R.string.btn_scale_mode, ConfigWriter.scaleMode(this).label(this)), v -> cycleScaleMode());
        mRoot.addView(mScaleButton);
        mRoot.addView(mUi.button(getString(R.string.btn_reimport), v -> pickZip()));
        mRoot.addView(mUi.button(getString(R.string.btn_delete), v -> confirmDelete()));
        mRoot.addView(mUi.spacer(12));
        mRoot.addView(mUi.small(getString(R.string.saves_kept)));
        play.requestFocus();
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
        if (!ButtonMapping.isConfigured(this)) return getString(R.string.buttons_unset);
        ButtonMapping m = ButtonMapping.load(this);
        if (m.isIdentity()) return getString(R.string.buttons_default);
        StringBuilder sb = new StringBuilder(getString(R.string.buttons_prefix));
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
        if (!ButtonMapping.isConfigured(this) && ButtonMapping.hasPhysicalGamepad()) {
            startActivityForResult(new Intent(this, ButtonWizardActivity.class), REQ_WIZARD_THEN_PLAY);
            return;
        }
        try {
            ConfigWriter.write(this, DeviceProfile.detect(this));
        } catch (IOException e) {
            showError(getString(R.string.err_prepare_config), e.getMessage());
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
    }

    private void cycleScaleMode()
    {
        ConfigWriter.ScaleMode[] all = ConfigWriter.ScaleMode.values();
        ConfigWriter.ScaleMode cur = ConfigWriter.scaleMode(this);
        ConfigWriter.setScaleMode(this, all[(cur.ordinal() + 1) % all.length]);
        render();
        // keep focus on the same button for quick cycling with the controller
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
                } else if (!ButtonMapping.isConfigured(this) && ButtonMapping.hasPhysicalGamepad()) {
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

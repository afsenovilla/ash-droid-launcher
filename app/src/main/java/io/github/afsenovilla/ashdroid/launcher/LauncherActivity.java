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
import java.util.Locale;

import io.github.afsenovilla.ashdroid.MainActivity;

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
        mRoot.removeAllViews();
        mRoot.addView(mUi.title("Ash Droid Launcher"));

        if (!GameInstaller.isInstalled(this)) {
            renderWelcome();
        } else {
            renderMain();
        }
    }

    private void renderWelcome()
    {
        mRoot.addView(mUi.body("Este lanzador no incluye ningún juego. Necesitas el .zip original de "
                + "Pokémon Fire Ash (u otro fangame de Pokémon Essentials que ya funcione con mkxp-z), "
                + "descargado de su fuente oficial."));
        mRoot.addView(mUi.small("Copia el .zip a la consola (por ejemplo a Descargas) y pulsa Importar."));
        mRoot.addView(mUi.spacer(16));
        mRoot.addView(deviceInfo());
        mRoot.addView(mUi.spacer(16));
        Button imp = mUi.button("Importar juego (.zip)", v -> pickZip());
        mRoot.addView(imp);
        imp.requestFocus();
    }

    private void renderMain()
    {
        mRoot.addView(mUi.big(GameInstaller.gameTitle(this)));

        String scaleInfo;
        try {
            ConfigWriter.Result r = ConfigWriter.write(this, mDevice);
            scaleInfo = String.format(Locale.ROOT, "Imagen: %dx%d → %dx%d · %s",
                    r.gameW, r.gameH, mDevice.screenW, mDevice.screenH, r.description);
        } catch (IOException e) {
            scaleInfo = "No se pudo generar la configuración: " + e.getMessage();
        }

        mRoot.addView(deviceInfo());
        mRoot.addView(mUi.small(scaleInfo));
        mRoot.addView(mUi.small(controlsInfo()));
        mRoot.addView(mUi.spacer(16));

        Button play = mUi.button("▶  Jugar", v -> play());
        mRoot.addView(play);
        mRoot.addView(mUi.button("Configurar botones", v ->
                startActivityForResult(new Intent(this, ButtonWizardActivity.class), REQ_WIZARD)));
        mRoot.addView(mUi.button("Modo de imagen: " + ConfigWriter.scaleMode(this).label, v -> cycleScaleMode()));
        mRoot.addView(mUi.button("Actualizar / reimportar juego (.zip)", v -> pickZip()));
        mRoot.addView(mUi.button("Borrar juego importado", v -> confirmDelete()));
        mRoot.addView(mUi.spacer(12));
        mRoot.addView(mUi.small("Las partidas guardadas se conservan al reimportar o actualizar el juego."));
        play.requestFocus();
    }

    private View deviceInfo()
    {
        String s = "Dispositivo: " + mDevice.displayName()
                + (mDevice.isKnown() ? "  (perfil conocido)" : "")
                + String.format(Locale.ROOT, "\nPantalla: %dx%d · %.0f Hz", mDevice.screenW, mDevice.screenH, mDevice.refreshRate)
                + "\nMando: " + (mDevice.controllerName != null ? mDevice.controllerName : "no detectado (se usarán los controles táctiles)");
        if (mDevice.notes != null && !mDevice.notes.isEmpty()) s += "\n" + mDevice.notes;
        return mUi.small(s);
    }

    private String controlsInfo()
    {
        if (!ButtonMapping.isConfigured(this)) return "Botones: sin configurar (se pedirán al pulsar Jugar)";
        ButtonMapping m = ButtonMapping.load(this);
        if (m.isIdentity()) return "Botones: mapeo por defecto";
        StringBuilder sb = new StringBuilder("Botones:");
        for (ButtonMapping.Action a : ButtonMapping.ACTIONS) {
            Integer k = m.physicalFor(a);
            if (k != null) sb.append("  ").append(a.label).append("=").append(ButtonMapping.keyName(k)).append(" ·");
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
            showError("No se pudo preparar la configuración", e.getMessage());
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
        for (int i = 0; i < mRoot.getChildCount(); i++) {
            View v = mRoot.getChildAt(i);
            if (v instanceof Button && ((Button) v).getText().toString().startsWith("Modo de imagen")) {
                v.requestFocus();
            }
        }
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
            showError("No hay selector de archivos", "Este dispositivo no tiene un selector de documentos disponible.");
        }
    }

    private void confirmDelete()
    {
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar el juego importado?")
                .setMessage("Se borrarán los archivos del juego (no las partidas guardadas). "
                        + "Tendrás que volver a importar el .zip para jugar.")
                .setPositiveButton("Borrar", (d, w) -> { GameInstaller.uninstall(this); render(); })
                .setNegativeButton("Cancelar", null)
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
        mRoot.addView(mUi.title("Importando juego…"));
        mRoot.addView(mUi.small("Puede tardar un par de minutos. No cierres la app."));
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
                            status.setText(String.format(Locale.ROOT, "%d / %d MB · %s",
                                    done >> 20, total >> 20, file));
                        } else {
                            status.setText(String.format(Locale.ROOT, "%d MB · %s", done >> 20, file));
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
                    showError("No se pudo importar el juego", err);
                } else if (!ButtonMapping.isConfigured(this) && ButtonMapping.hasPhysicalGamepad()) {
                    startActivityForResult(new Intent(this, ButtonWizardActivity.class), REQ_WIZARD);
                }
            });
        }, "game-import").start();
    }

    private void showError(String title, String msg)
    {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Vale", null).show();
    }
}

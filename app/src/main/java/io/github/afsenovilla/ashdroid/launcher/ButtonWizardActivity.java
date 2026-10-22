package io.github.afsenovilla.ashdroid.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Asks the player to press the physical button they want for each in-game
 * action. Works on any device: it records whatever keycode the button sends,
 * so face-button layout (Nintendo/Xbox) and firmware quirks don't matter.
 *
 * START = skip this action (keep default), SELECT = go back one step.
 */
public class ButtonWizardActivity extends Activity
{
    private final Map<String, Integer> mAssignments = new HashMap<>();
    private int mStep = 0;
    private boolean mDone = false;

    private TextView mStepView, mTitleView, mHintView, mFeedbackView, mSummaryView;
    private DeviceProfile mDevice;
    private final Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mDevice = DeviceProfile.detect(this);

        Ui ui = new Ui(this);
        LinearLayout root = ui.column();

        root.addView(ui.title("Configurar botones"));
        String sub = mDevice.displayName();
        if (mDevice.controllerName != null) sub += "  ·  mando: " + mDevice.controllerName;
        root.addView(ui.small(sub));

        mStepView = ui.small("");
        mTitleView = ui.big("");
        mHintView = ui.body("");
        mFeedbackView = ui.accent("");
        mSummaryView = ui.body("");
        root.addView(ui.spacer(24));
        root.addView(mStepView);
        root.addView(mTitleView);
        root.addView(mHintView);
        root.addView(ui.spacer(12));
        root.addView(mFeedbackView);
        root.addView(mSummaryView);
        root.addView(ui.spacer(24));
        root.addView(ui.small("START = omitir este paso (deja el valor por defecto)  ·  SELECT = paso anterior"));

        LinearLayout row = ui.row();
        Button skip = ui.button("Omitir paso", v -> skip());
        Button defaults = ui.button("Usar mapeo por defecto", v -> useDefaults());
        Button cancel = ui.button("Cancelar", v -> { setResult(RESULT_CANCELED); finish(); });
        // Touch-only buttons: controller input is captured by the wizard itself
        for (Button b : new Button[] { skip, defaults, cancel }) b.setFocusable(false);
        row.addView(skip);
        row.addView(defaults);
        row.addView(cancel);
        root.addView(row);

        setContentView(ui.scroll(root));
        showStep();
    }

    private void showStep()
    {
        ButtonMapping.Action a = ButtonMapping.ACTIONS[mStep];
        mStepView.setText("Paso " + (mStep + 1) + " de " + ButtonMapping.ACTIONS.length);
        mTitleView.setText("Pulsa el botón para: " + a.label);
        String hint = a.hint;
        if ("C".equals(a.id) && mDevice.faceLayout != null) {
            hint += "nintendo".equals(mDevice.faceLayout)
                    ? "\nEn tu consola suele ser el botón A (el de la derecha)."
                    : "\nEn tu consola suele ser el botón A (el de abajo).";
        }
        mHintView.setText(hint);
        mSummaryView.setText(summary());
    }

    private String summary()
    {
        StringBuilder sb = new StringBuilder();
        for (ButtonMapping.Action a : ButtonMapping.ACTIONS) {
            Integer k = mAssignments.get(a.id);
            if (k == null) continue;
            sb.append("• ").append(a.label).append(" → ").append(ButtonMapping.keyName(k)).append('\n');
        }
        return sb.toString();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt)
    {
        if (mDone) return true;
        int code = evt.getKeyCode();

        // Let volume keys through
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(evt);
        }
        // Back from a touchscreen/system nav bar (not a controller): normal behaviour
        if (code == KeyEvent.KEYCODE_BACK && !ButtonMapping.isGamepadEvent(evt)) {
            return super.dispatchKeyEvent(evt);
        }
        if (evt.getAction() != KeyEvent.ACTION_DOWN || evt.getRepeatCount() > 0) return true;

        if (code == KeyEvent.KEYCODE_BUTTON_START) { skip(); return true; }
        if (code == KeyEvent.KEYCODE_BUTTON_SELECT) { back(); return true; }

        if (isDirection(code)) {
            mFeedbackView.setText("La cruceta y los sticks ya mueven al personaje: usa otro botón.");
            return true;
        }

        for (Map.Entry<String, Integer> e : mAssignments.entrySet()) {
            if (e.getValue() == code && !e.getKey().equals(ButtonMapping.ACTIONS[mStep].id)) {
                mFeedbackView.setText(ButtonMapping.keyName(code) + " ya está asignado a "
                        + labelOf(e.getKey()) + ". Elige otro (o SELECT para volver).");
                return true;
            }
        }

        mAssignments.put(ButtonMapping.ACTIONS[mStep].id, code);
        mFeedbackView.setText("✓ " + ButtonMapping.keyName(code));
        next();
        return true;
    }

    private void next()
    {
        if (mStep + 1 >= ButtonMapping.ACTIONS.length) {
            finishWizard(ButtonMapping.fromAssignments(mAssignments));
        } else {
            mStep++;
            showStep();
        }
    }

    private void skip()
    {
        mAssignments.remove(ButtonMapping.ACTIONS[mStep].id);
        mFeedbackView.setText("Paso omitido");
        next();
    }

    private void back()
    {
        if (mStep > 0) {
            mStep--;
            mAssignments.remove(ButtonMapping.ACTIONS[mStep].id);
            mFeedbackView.setText("");
            showStep();
        }
    }

    private void useDefaults()
    {
        finishWizard(ButtonMapping.identity());
    }

    private void finishWizard(ButtonMapping mapping)
    {
        mDone = true;
        mapping.save(this);
        mStepView.setText("");
        mTitleView.setText("¡Listo!");
        mHintView.setText(mapping.isIdentity() ? "Se usará el mapeo por defecto de mkxp-z." : summary());
        mFeedbackView.setText("");
        mSummaryView.setText("");
        mHandler.postDelayed(() -> { setResult(RESULT_OK); finish(); }, 1500);
    }

    private static boolean isDirection(int code)
    {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP_LEFT || code == KeyEvent.KEYCODE_DPAD_UP_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_DOWN_LEFT || code == KeyEvent.KEYCODE_DPAD_DOWN_RIGHT;
    }

    private static String labelOf(String id)
    {
        for (ButtonMapping.Action a : ButtonMapping.ACTIONS) if (a.id.equals(id)) return a.label;
        return id;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(getWindow());
    }
}

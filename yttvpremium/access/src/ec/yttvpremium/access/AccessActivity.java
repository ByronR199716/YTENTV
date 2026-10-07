package ec.yttvpremium.access;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Pantalla de acceso de YTTVPremium: no deja abrir la app hasta canjear un código válido
 * del servicio "ytpremium" (se generan en el panel). Pensada para manejarse con el control remoto.
 */
public class AccessActivity extends Activity {
    /** Mensaje que manda {@link AccessGuard} cuando bloquea la app mientras estaba abierta. */
    static final String EXTRA_MESSAGE = "ec.yttvpremium.access.MESSAGE";

    private static final int BG = 0xFF0F0F12;
    private static final int CARD = 0xFF1C1C22;
    private static final int REDEEM = 0xFF7C4DFF;
    private static final int REDEEM_FOCUS = 0xFF9E7BFF;
    private static final int MUTED = 0xFFB3B3BD;
    private static final int ERROR = 0xFFFF8A80;

    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private EditText input;
    private Button button;
    private TextView message;
    private boolean busy;
    private boolean formatting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        String lockedMessage = getIntent() != null ? getIntent().getStringExtra(EXTRA_MESSAGE) : null;
        if (lockedMessage != null) {
            showRedeem(lockedMessage);
        } else {
            showChecking();
            final Context app = getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final AccessController.Decision d = AccessController.check(app);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            if (d.unlocked) openApp(d.session, true);
                            else showRedeem(d.message);
                        }
                    });
                }
            }, "yttv-access-check").start();
        }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void showChecking() {
        root.removeAllViews();
        LinearLayout col = column();
        LogoView logo = new LogoView(this);
        col.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));
        ProgressBar spinner = new ProgressBar(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.topMargin = dp(28);
        col.addView(spinner, lp);
        root.addView(col, centered());
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        return col;
    }

    private FrameLayout.LayoutParams centered() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable rounded(int fill, int strokeColor, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private void showRedeem(String msg) {
        root.removeAllViews();
        LinearLayout col = column();
        col.setPadding(dp(48), dp(36), dp(48), dp(36));
        col.setBackground(rounded(CARD, 0, 0, 20));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new LogoView(this), new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView title = text(AccessConfig.APP_TITLE, 34, Color.WHITE, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(18);
        header.addView(title, tlp);
        col.addView(header);

        TextView subtitle = text("Escribe tu código de acceso para empezar", 18, MUTED, false);
        LinearLayout.LayoutParams slp = wrap();
        slp.topMargin = dp(18);
        col.addView(subtitle, slp);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("XXXX-XXXX-XXXX");
        input.setHintTextColor(0xFF6B6B75);
        input.setTextColor(Color.WHITE);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        input.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(39) });
        input.setPadding(dp(20), dp(14), dp(20), dp(14));
        StateListDrawable inputBg = new StateListDrawable();
        inputBg.addState(new int[] { android.R.attr.state_focused }, rounded(0xFF26262E, Color.WHITE, 3, 12));
        inputBg.addState(new int[] {}, rounded(0xFF26262E, 0xFF3A3A44, 2, 12));
        input.setBackground(inputBg);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (formatting) return;
                String formatted = AccessController.formatCode(s.toString());
                if (!formatted.equals(s.toString())) {
                    formatting = true;
                    s.replace(0, s.length(), formatted);
                    formatting = false;
                }
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                submit();
                return true;
            }
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(460), ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(24);
        col.addView(input, ilp);

        button = new Button(this);
        button.setText("Canjear código");
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        StateListDrawable btnBg = new StateListDrawable();
        btnBg.addState(new int[] { android.R.attr.state_focused }, rounded(REDEEM_FOCUS, Color.WHITE, 3, 12));
        btnBg.addState(new int[] { android.R.attr.state_pressed }, rounded(REDEEM_FOCUS, 0, 0, 12));
        btnBg.addState(new int[] {}, rounded(REDEEM, 0, 0, 12));
        button.setBackground(btnBg);
        button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { submit(); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(460), dp(56));
        blp.topMargin = dp(16);
        col.addView(button, blp);

        message = text(msg == null ? "" : msg, 17, ERROR, false);
        message.setVisibility(msg == null ? View.GONE : View.VISIBLE);
        message.setMaxWidth(dp(520));
        LinearLayout.LayoutParams mlp = wrap();
        mlp.topMargin = dp(18);
        col.addView(message, mlp);

        TextView device = text("Este equipo: " + AccessController.deviceName().replace("TV · ", ""), 14, 0xFF7D7D88, false);
        LinearLayout.LayoutParams dlp = wrap();
        dlp.topMargin = dp(22);
        col.addView(device, dlp);

        root.addView(col, centered());

        TextView footer = text("Basado en TizenTube y Cobalt · software libre", 12, 0xFF5E5E68, false);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        flp.bottomMargin = dp(16);
        root.addView(footer, flp);

        input.setNextFocusDownId(View.NO_ID);
        input.requestFocus();
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void setBusy(boolean value) {
        busy = value;
        if (button != null) {
            button.setEnabled(!value);
            button.setText(value ? "Verificando…" : "Canjear código");
        }
        if (input != null) input.setEnabled(!value);
    }

    private void submit() {
        if (busy || input == null) return;
        final String raw = input.getText().toString();
        setBusy(true);
        message.setVisibility(View.GONE);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final AccessController.Decision d = AccessController.submit(app, raw);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        setBusy(false);
                        if (d.unlocked) {
                            openApp(d.session, true);
                        } else {
                            message.setText(d.message == null ? "No se pudo validar el acceso." : d.message);
                            message.setVisibility(View.VISIBLE);
                            button.requestFocus();
                        }
                    }
                });
            }
        }, "yttv-access-submit").start();
    }

    private void openApp(AccessController.Session s, boolean announce) {
        if (announce && s != null) {
            String text = AccessConfig.APP_TITLE + " · te quedan " + AccessController.formatRemaining(s.remainingMs())
                + " (vence " + AccessController.formatDate(s.expiresMs) + ")";
            Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
        }
        Intent i = new Intent();
        i.setClassName(getPackageName(), AccessConfig.MAIN_ACTIVITY);
        Intent from = getIntent();
        if (from != null && Intent.ACTION_VIEW.equals(from.getAction()) && from.getData() != null) {
            i.setAction(Intent.ACTION_VIEW);
            i.setData(from.getData());
        }
        startActivity(i);
        overridePendingTransition(0, 0);
        finish();
    }
}

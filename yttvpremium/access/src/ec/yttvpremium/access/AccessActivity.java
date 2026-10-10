package ec.yttvpremium.access;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
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

    // Aviso de nueva versión (ver AppUpdate).
    private static final int U_AVAILABLE = 0, U_DOWNLOADING = 1, U_PERMISSION = 2, U_READY = 3, U_FAILED = 4;
    private AppUpdate.Info update;
    private AccessController.Session updateSession;
    private int updateState = -1;
    private TextView updateText;
    private ProgressBar updateBar;
    private Button updatePrimary;
    private Button updateLater;

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
                    final AppUpdate.Info update = d.unlocked ? AppUpdate.check(app) : null;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) return;
                            if (d.unlocked && update != null) showUpdate(update, d.session);
                            else if (d.unlocked) openApp(d.session, true);
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
                final AppUpdate.Info update = d.unlocked ? AppUpdate.check(app) : null;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        setBusy(false);
                        if (d.unlocked && update != null) {
                            showUpdate(update, d.session);
                        } else if (d.unlocked) {
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

    private Button bigButton(String label, int fill, int focusFill) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] { android.R.attr.state_focused }, rounded(focusFill, Color.WHITE, 3, 12));
        bg.addState(new int[] { android.R.attr.state_pressed }, rounded(focusFill, 0, 0, 12));
        bg.addState(new int[] {}, rounded(fill, 0, 0, 12));
        b.setBackground(bg);
        b.setFocusable(true);
        b.setPadding(dp(28), 0, dp(28), 0);
        return b;
    }

    /** Pantalla "Nueva versión disponible" (se maneja con el control remoto). */
    private void showUpdate(AppUpdate.Info info, AccessController.Session session) {
        update = info;
        updateSession = session;
        root.removeAllViews();
        LinearLayout col = column();
        col.setPadding(dp(48), dp(36), dp(48), dp(36));
        col.setBackground(rounded(CARD, 0, 0, 20));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(new LogoView(this), new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView title = text(info.required ? "Actualización obligatoria" : "Nueva versión disponible", 28, Color.WHITE, true);
        LinearLayout.LayoutParams tlp = wrap();
        tlp.leftMargin = dp(18);
        header.addView(title, tlp);
        col.addView(header);

        updateText = text("", 18, MUTED, false);
        updateText.setMaxWidth(dp(560));
        LinearLayout.LayoutParams ulp = wrap();
        ulp.topMargin = dp(20);
        col.addView(updateText, ulp);

        if (info.notes != null && info.notes.trim().length() > 0) {
            TextView notes = text(info.notes.trim(), 15, 0xFF9A9AA6, false);
            notes.setMaxWidth(dp(560));
            notes.setMaxLines(6);
            LinearLayout.LayoutParams nlp = wrap();
            nlp.topMargin = dp(14);
            col.addView(notes, nlp);
        }

        updateBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        updateBar.setMax(100);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(460), dp(10));
        plp.topMargin = dp(22);
        col.addView(updateBar, plp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        updatePrimary = bigButton("Actualizar", REDEEM, REDEEM_FOCUS);
        updatePrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onUpdatePrimary(); }
        });
        updateLater = bigButton("Más tarde", 0xFF33333D, 0xFF4A4A56);
        updateLater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { updateLater(); }
        });
        buttons.addView(updatePrimary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        llp.leftMargin = dp(16);
        buttons.addView(updateLater, llp);
        LinearLayout.LayoutParams blp = wrap();
        blp.topMargin = dp(26);
        col.addView(buttons, blp);

        root.addView(col, centered());
        registerUpdateBack();
        setUpdateState(U_AVAILABLE, null);
    }

    private void setUpdateState(int state, String error) {
        updateState = state;
        boolean required = update != null && update.required;
        updateBar.setVisibility(state == U_DOWNLOADING ? View.VISIBLE : View.GONE);
        updatePrimary.setVisibility(state == U_DOWNLOADING ? View.GONE : View.VISIBLE);
        updateLater.setVisibility(state == U_DOWNLOADING || required ? View.GONE : View.VISIBLE);
        switch (state) {
            case U_AVAILABLE:
                updateText.setText(required
                    ? "Esta versión ya no funciona. Instala la actualización para seguir usando " + AccessConfig.APP_TITLE + "."
                    : "Hay una versión nueva de " + AccessConfig.APP_TITLE + ". Se descarga e instala desde aquí.");
                updatePrimary.setText("Actualizar");
                break;
            case U_DOWNLOADING:
                updateBar.setProgress(0);
                updateText.setText("Descargando…");
                break;
            case U_PERMISSION:
                updateText.setText("Para instalar la actualización, permite a " + AccessConfig.APP_TITLE
                    + " instalar apps (Instalar apps desconocidas > activar) y luego regresa con el botón Atrás.");
                updatePrimary.setText("Dar permiso");
                break;
            case U_READY:
                updateText.setText("Elige \"Instalar\" en la ventana de Android. Si la cerraste, ábrela otra vez.");
                updatePrimary.setText("Instalar");
                break;
            case U_FAILED:
                updateText.setText("No se pudo descargar la actualización. Revisa el internet e inténtalo otra vez."
                    + (error == null ? "" : "\n(" + error + ")"));
                updatePrimary.setText("Reintentar");
                break;
        }
        if (updatePrimary.getVisibility() == View.VISIBLE) updatePrimary.requestFocus();
    }

    private void onUpdatePrimary() {
        switch (updateState) {
            case U_AVAILABLE:
            case U_FAILED:
                startDownload();
                break;
            case U_PERMISSION:
                if (!AppUpdate.openInstallPermission(this)) {
                    updateText.setText("Activa \"Instalar apps desconocidas\" para " + AccessConfig.APP_TITLE
                        + " en los Ajustes del TV (Apps o Seguridad) y vuelve aquí.");
                }
                break;
            case U_READY:
                installUpdate();
                break;
        }
    }

    private void startDownload() {
        setUpdateState(U_DOWNLOADING, null);
        final Context app = getApplicationContext();
        final String url = update.url;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    AppUpdate.download(app, url, new AppUpdate.Progress() {
                        @Override
                        public void onProgress(final int percent) {
                            main.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (updateState != U_DOWNLOADING) return;
                                    updateBar.setProgress(percent);
                                    updateText.setText("Descargando… " + percent + "%");
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                final String failure = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (failure != null) setUpdateState(U_FAILED, failure);
                        else if (!AppUpdate.canInstall(AccessActivity.this)) setUpdateState(U_PERMISSION, null);
                        else installUpdate();
                    }
                });
            }
        }, "yttv-update-download").start();
    }

    private void installUpdate() {
        try {
            AppUpdate.install(this);
            setUpdateState(U_READY, null);
        } catch (Exception e) {
            setUpdateState(U_FAILED, e.getMessage());
        }
    }

    private void updateLater() {
        if (update != null && update.required) return;
        updateState = -1;
        unregisterUpdateBack();
        openApp(updateSession, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Al volver de Ajustes con el permiso ya dado, se instala solo.
        if (updateState == U_PERMISSION && AppUpdate.canInstall(this)) installUpdate();
    }

    /** Atrás en la pantalla de actualización: durante la descarga no hace nada; si es obligatoria cierra la app. */
    private void onUpdateBack() {
        if (updateState == U_DOWNLOADING) return;
        if (update != null && update.required) {
            finish();
            return;
        }
        updateLater();
    }

    @Override
    public void onBackPressed() {
        if (updateState >= 0) {
            onUpdateBack();
            return;
        }
        super.onBackPressed();
    }

    // Android 13+ con "atrás predictivo" (obligatorio al apuntar a Android 16) ya no llama a onBackPressed.
    private Object backCallback;

    private void registerUpdateBack() {
        if (Build.VERSION.SDK_INT < 33 || backCallback != null) return;
        android.window.OnBackInvokedCallback cb = new android.window.OnBackInvokedCallback() {
            @Override
            public void onBackInvoked() {
                if (updateState >= 0) onUpdateBack();
            }
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        backCallback = cb;
    }

    private void unregisterUpdateBack() {
        if (Build.VERSION.SDK_INT < 33 || backCallback == null) return;
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCallback);
        backCallback = null;
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

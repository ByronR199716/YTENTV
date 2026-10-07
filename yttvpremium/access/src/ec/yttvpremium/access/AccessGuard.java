package ec.yttvpremium.access;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Application de YTTVPremium (reemplaza a la de Cobalt en el manifiesto y la extiende).
 *
 * Vigila la pantalla principal de la app:
 * - Si se abre sin un acceso vigente (por ejemplo desde un enlace de YouTube), manda a la pantalla de acceso.
 * - Mientras está abierta revisa el acceso cada minuto y al volver a la app; si venció o el código
 *   fue desactivado, cierra la app y muestra la pantalla de acceso con el motivo.
 */
public class AccessGuard extends dev.cobalt.app.CobaltApplication {
    private static final long CHECK_EVERY_MS = 60000L;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Watcher(this));
    }

    private static boolean isMain(Activity a) {
        return AccessConfig.MAIN_ACTIVITY.equals(a.getClass().getName());
    }

    private static void lock(Activity a, String message) {
        Intent i = new Intent(a, AccessActivity.class);
        // Sin mensaje, la pantalla de acceso hace su propia revisión (puede que solo falte validar en línea).
        if (message != null) i.putExtra(AccessActivity.EXTRA_MESSAGE, message);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    private static final class Watcher implements Application.ActivityLifecycleCallbacks {
        private final Context app;
        private final Handler main = new Handler(Looper.getMainLooper());
        private Activity resumed;
        private boolean checking;

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (resumed == null) return;
                runCheck(resumed);
                main.postDelayed(this, CHECK_EVERY_MS);
            }
        };

        Watcher(Context app) {
            this.app = app.getApplicationContext();
        }

        private void runCheck(final Activity activity) {
            if (checking) return;
            checking = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final AccessController.Decision d = AccessController.check(app);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            checking = false;
                            if (!d.unlocked && !activity.isFinishing()) lock(activity, d.message);
                        }
                    });
                }
            }, "yttv-access-guard").start();
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            // Sin acceso vigente guardado no se deja abrir la pantalla principal.
            if (isMain(activity) && !AccessController.isOpenOffline(app)) lock(activity, null);
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (!isMain(activity) || activity.isFinishing()) return;
            resumed = activity;
            main.removeCallbacks(tick);
            main.post(tick);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (activity == resumed) {
                resumed = null;
                main.removeCallbacks(tick);
            }
        }

        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    }
}

package ec.yttvpremium.access;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * Decide si la app puede abrirse (mismas reglas que PremiumMusic y YTPremium).
 *
 * - Con un acceso guardado y validado hace menos de `revalidate_hours`, abre sin usar internet.
 * - Si toca revalidar y no hay internet, deja usar la app hasta `offline_grace_hours`
 *   desde la última validación, siempre que el acceso no haya vencido.
 * - Si el reloj del equipo se atrasa a propósito, exige validar en línea.
 */
final class AccessController {
    private AccessController() {}

    static final long HOUR_MS = 3600000L;
    private static final long CLOCK_TOLERANCE_MS = 10 * 60000L;

    static final String EXPIRED = "Tu código venció. Pide uno nuevo para seguir usando " + AccessConfig.APP_TITLE + ".";
    static final String TRIAL_USED = "Ya usaste tu prueba gratis en este dispositivo. Para seguir usando "
        + AccessConfig.APP_TITLE + ", pide un código.";

    private static final String PREFS = "yttvpremium-access";
    private static final String K_DEVICE = "device_id";
    private static final String K_KIND = "kind";
    private static final String K_CODE = "code";
    private static final String K_EXPIRES = "expires_ms";
    private static final String K_VALIDATED = "validated_ms";
    private static final String K_OFFSET = "server_offset_ms";
    private static final String K_LAST_SEEN = "last_seen_ms";
    private static final String K_REVALIDATE = "revalidate_h";
    private static final String K_GRACE = "grace_h";

    /** Resultado de una revisión. */
    static final class Decision {
        final boolean unlocked;
        /** Mensaje para la pantalla de acceso (null = sin mensaje). */
        final String message;
        final Session session;

        private Decision(boolean unlocked, String message, Session session) {
            this.unlocked = unlocked;
            this.message = message;
            this.session = session;
        }

        static Decision open(Session s) { return new Decision(true, null, s); }
        static Decision locked(String message) { return new Decision(false, message, null); }
    }

    static final class Session {
        String code;
        long expiresMs;
        long validatedAt;
        long offsetMs;
        long lastSeen;
        double revalidateHours;
        double graceHours;

        long remainingMs() {
            return expiresMs - (System.currentTimeMillis() + offsetMs);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String normalizeCode(String raw) {
        if (raw == null) return "";
        String code = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return code.length() > 32 ? code.substring(0, 32) : code;
    }

    static String formatCode(String raw) {
        String code = normalizeCode(raw);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }

    /** XXXX-XXXX-1234: solo se muestran los últimos 4 caracteres. */
    static String maskCode(String raw) {
        String code = normalizeCode(raw);
        if (code.length() <= 4) return code;
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < code.length() - 4; i++) masked.append('X');
        masked.append(code.substring(code.length() - 4));
        return formatCode(masked.toString());
    }

    static String deviceId(Context c) {
        try {
            String id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
            // Mismo ID que usa YTPremium (misma llave de firma): un mismo equipo cuenta una sola vez.
            if (id != null && id.length() >= 4) return id;
        } catch (Exception ignored) {
        }
        SharedPreferences p = prefs(c);
        String saved = p.getString(K_DEVICE, "");
        if (saved.length() > 0) return saved;
        String hex = "0123456789abcdef";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder("gen-");
        for (int i = 0; i < 16; i++) sb.append(hex.charAt(rnd.nextInt(16)));
        p.edit().putString(K_DEVICE, sb.toString()).apply();
        return sb.toString();
    }

    static String deviceName() {
        String maker = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String name;
        if (model.toLowerCase(Locale.ROOT).startsWith(maker.toLowerCase(Locale.ROOT))) name = model;
        else name = (maker + " " + model).trim();
        if (name.length() == 0) name = "TV";
        name = "TV · " + name;
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    static Session loadSession(Context c) {
        SharedPreferences p = prefs(c);
        if (!"code".equals(p.getString(K_KIND, ""))) return null;
        long expires = p.getLong(K_EXPIRES, 0);
        if (expires == 0) return null;
        Session s = new Session();
        s.code = p.getString(K_CODE, "");
        s.expiresMs = expires;
        s.validatedAt = p.getLong(K_VALIDATED, 0);
        s.offsetMs = p.getLong(K_OFFSET, 0);
        s.lastSeen = p.getLong(K_LAST_SEEN, 0);
        s.revalidateHours = Double.longBitsToDouble(p.getLong(K_REVALIDATE, Double.doubleToLongBits(12)));
        s.graceHours = Double.longBitsToDouble(p.getLong(K_GRACE, Double.doubleToLongBits(72)));
        return s;
    }

    private static void saveSession(Context c, String code, AccessApi.Result r) {
        long t = System.currentTimeMillis();
        prefs(c).edit()
            .putString(K_KIND, "code")
            .putString(K_CODE, code)
            .putLong(K_EXPIRES, r.expiresMs != null ? r.expiresMs : t)
            .putLong(K_VALIDATED, t)
            .putLong(K_OFFSET, (r.serverMs != null ? r.serverMs : t) - t)
            .putLong(K_LAST_SEEN, t)
            .putLong(K_REVALIDATE, Double.doubleToLongBits(r.revalidateHours))
            .putLong(K_GRACE, Double.doubleToLongBits(r.graceHours))
            .commit();
    }

    private static void clearSession(Context c) {
        prefs(c).edit()
            .remove(K_KIND).remove(K_CODE).remove(K_EXPIRES)
            .remove(K_VALIDATED).remove(K_OFFSET).remove(K_LAST_SEEN)
            .commit();
    }

    private static void touch(Context c, Session s) {
        long t = System.currentTimeMillis();
        if (t > s.lastSeen) {
            s.lastSeen = t;
            prefs(c).edit().putLong(K_LAST_SEEN, t).apply();
        }
    }

    static String messageFor(String reason) {
        if ("invalid".equals(reason)) return "Ese código no existe. Revisa que esté bien escrito.";
        if ("disabled".equals(reason)) return "Este código fue desactivado. Contacta al administrador.";
        if ("expired".equals(reason)) return EXPIRED;
        if ("device_limit".equals(reason)) {
            return "Este código ya se usa en el máximo de dispositivos. Pide al administrador que libere uno.";
        }
        if ("bad_request".equals(reason)) return "No se pudo identificar este dispositivo.";
        return "No se pudo validar el acceso.";
    }

    private static Decision apply(Context c, String code, AccessApi.Result r) {
        if (r.ok && r.expiresMs != null) {
            saveSession(c, code, r);
            return Decision.open(loadSession(c));
        }
        clearSession(c);
        // Una segunda prueba en el mismo equipo: el servidor responde "expired" sin fecha
        // (un código vencido de verdad siempre trae su fecha de vencimiento).
        boolean trialUsed = "expired".equals(r.reason) && r.expiresMs == null;
        return Decision.locked(trialUsed ? TRIAL_USED : messageFor(r.reason));
    }

    /** Revisión rápida sin internet: true si el acceso guardado sigue vigente y no toca revalidar. */
    static boolean isOpenOffline(Context c) {
        Session s = loadSession(c);
        if (s == null) return false;
        long t = System.currentTimeMillis();
        boolean clockMovedBack = t + CLOCK_TOLERANCE_MS < s.lastSeen || t < s.validatedAt - CLOCK_TOLERANCE_MS;
        boolean expired = t + s.offsetMs >= s.expiresMs;
        boolean withinGrace = t - s.validatedAt < s.graceHours * HOUR_MS;
        return !clockMovedBack && !expired && withinGrace;
    }

    /** Revisa el acceso guardado. Puede usar internet: no llamar en el hilo principal. */
    static synchronized Decision check(Context c) {
        Session s = loadSession(c);
        if (s == null) return Decision.locked(null);

        long t = System.currentTimeMillis();
        boolean clockMovedBack = t + CLOCK_TOLERANCE_MS < s.lastSeen || t < s.validatedAt - CLOCK_TOLERANCE_MS;
        boolean expired = t + s.offsetMs >= s.expiresMs;
        boolean due = t - s.validatedAt >= s.revalidateHours * HOUR_MS;

        if (!clockMovedBack && !expired && !due) {
            touch(c, s);
            return Decision.open(s);
        }

        try {
            return apply(c, s.code, AccessApi.validate(s.code, deviceId(c), deviceName()));
        } catch (AccessApi.UnavailableException e) {
            boolean withinGrace = !clockMovedBack && t - s.validatedAt < s.graceHours * HOUR_MS;
            if (expired) {
                return Decision.locked(EXPIRED + " Si ya te lo renovaron, conéctate a internet y vuelve a abrir la app.");
            } else if (withinGrace) {
                touch(c, s);
                return Decision.open(s);
            } else {
                return Decision.locked("Conéctate a internet para verificar tu acceso y vuelve a abrir la app.");
            }
        }
    }

    /** El usuario escribió un código. Usa internet: no llamar en el hilo principal. */
    static synchronized Decision submit(Context c, String raw) {
        String code = normalizeCode(raw);
        if (code.length() < 6) return Decision.locked("Escribe el código completo.");
        try {
            return apply(c, code, AccessApi.validate(code, deviceId(c), deviceName()));
        } catch (AccessApi.UnavailableException e) {
            return Decision.locked("No se pudo conectar. Revisa tu internet e inténtalo de nuevo.");
        }
    }

    /** Tiempo restante legible, ej. "12 días", "5 h 20 min". */
    static String formatRemaining(long ms) {
        if (ms <= 0) return "Vencido";
        long minutes = ms / 60000;
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        if (days >= 1) return hours > 0 ? days + " d " + hours + " h" : days + (days == 1 ? " día" : " días");
        if (hours >= 1) return hours + " h " + mins + " min";
        return Math.max(mins, 1) + " min";
    }

    static String formatDate(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return String.format(Locale.ROOT, "%02d/%02d/%04d %02d:%02d",
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR),
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }
}

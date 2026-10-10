package ec.yttvpremium.access;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import org.json.JSONObject;

/**
 * Aviso de nueva versión de YTTVPremium.
 *
 * El admin publica la versión en el panel (Servicios > Actualizaciones de las apps, fila ytpremium / tv).
 * La app la consulta con get_app_update(ytpremium, tv) al abrir, descarga el APK, comprueba que sea
 * esta misma app y más nueva, y abre el instalador de Android.
 *
 * Números: el APK de TV lleva versionCode = 100 + N (release build-N), lo mismo que se pone en el panel.
 */
final class AppUpdate {
    private AppUpdate() {}

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String PLATFORM = "tv";
    private static final String DIR = "updates";
    private static final String FILE = "update.apk";

    /** Una sola vez por inicio de la app: "Más tarde" lo oculta hasta que la app se vuelva a abrir. */
    static volatile boolean checkedThisLaunch;

    static final class Info {
        long versionCode;
        long minVersionCode;
        String url;
        String notes;
        boolean required;
    }

    interface Progress {
        void onProgress(int percent);
    }

    @SuppressWarnings("deprecation")
    static long installedVersionCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    private static long versionOf(PackageInfo p) {
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    /**
     * Pregunta al servidor si hay una versión más nueva que la instalada.
     * Devuelve null si no hay, si está apagado en el panel o si no hay internet.
     */
    static Info check(Context c) {
        if (checkedThisLaunch) return null;
        checkedThisLaunch = true;
        try {
            JSONObject body = new JSONObject();
            body.put("p_service", AccessConfig.SERVICE);
            body.put("p_platform", PLATFORM);
            HttpURLConnection conn = (HttpURLConnection) new URL(AccessConfig.SUPABASE_URL + "/rest/v1/rpc/get_app_update").openConnection();
            String text;
            int status;
            try {
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("apikey", AccessConfig.SUPABASE_ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + AccessConfig.SUPABASE_ANON_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                OutputStream out = conn.getOutputStream();
                out.write(body.toString().getBytes(UTF8));
                out.close();
                status = conn.getResponseCode();
                if (status < 200 || status >= 300) return null;
                text = readAll(conn.getInputStream());
            } finally {
                conn.disconnect();
            }
            JSONObject o = new JSONObject(text);
            Info info = new Info();
            info.versionCode = o.optLong("version_code", 0);
            info.minVersionCode = o.optLong("min_version_code", 0);
            info.url = o.optString("url", "");
            info.notes = o.isNull("notes") ? "" : o.optString("notes", "");
            if (info.versionCode <= 0 || info.url.length() == 0) {
                cleanup(c);
                return null;
            }
            long installed = installedVersionCode(c);
            if (installed <= 0 || info.versionCode <= installed) {
                cleanup(c);
                return null;
            }
            info.required = installed < info.minVersionCode;
            info.url = urlForDevice(c, info.url);
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /** El panel apunta al APK de 32 bits; si este equipo tiene instalado el de 64 bits, se baja ese. */
    private static String urlForDevice(Context c, String url) {
        try {
            ApplicationInfo ai = c.getApplicationInfo();
            String libDir = ai.nativeLibraryDir == null ? "" : ai.nativeLibraryDir;
            boolean is64 = libDir.endsWith("arm64") || libDir.contains("/arm64");
            if (is64 && url.endsWith("YTTVPremium-armeabi-v7a.apk")) {
                return url.substring(0, url.length() - "YTTVPremium-armeabi-v7a.apk".length()) + "YTTVPremium-arm64-v8a.apk";
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    private static File dir(Context c) {
        return new File(c.getFilesDir(), DIR);
    }

    private static File file(Context c) {
        return new File(dir(c), FILE);
    }

    static void cleanup(Context c) {
        File[] files = dir(c).listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    /** Descarga el APK (siguiendo las redirecciones de GitHub) y lo verifica. Lanza Exception con el motivo. */
    static void download(Context c, String url, Progress progress) throws Exception {
        File d = dir(c);
        d.mkdirs();
        File tmp = new File(d, FILE + ".part");
        File target = file(c);
        tmp.delete();
        target.delete();

        URL current = new URL(url);
        HttpURLConnection conn = null;
        for (int i = 0; i < 8; i++) {
            HttpURLConnection h = (HttpURLConnection) current.openConnection();
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            h.setRequestProperty("User-Agent", "YTTVPremium-updater");
            int code = h.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = h.getHeaderField("Location");
                h.disconnect();
                if (location == null) throw new Exception("Redirección sin destino");
                current = new URL(current, location);
                continue;
            }
            if (code < 200 || code >= 300) {
                h.disconnect();
                throw new Exception("HTTP " + code);
            }
            conn = h;
            break;
        }
        if (conn == null) throw new Exception("Demasiadas redirecciones");

        try {
            long total = conn.getContentLength();
            long done = 0;
            int last = -1;
            InputStream in = conn.getInputStream();
            OutputStream out = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        if (pct != last) {
                            last = pct;
                            progress.onProgress(pct);
                        }
                    }
                }
            } finally {
                out.close();
                in.close();
            }
        } finally {
            conn.disconnect();
        }

        PackageInfo p = c.getPackageManager().getPackageArchiveInfo(tmp.getAbsolutePath(), 0);
        if (p == null) {
            tmp.delete();
            throw new Exception("El archivo descargado no es un APK válido");
        }
        if (!c.getPackageName().equals(p.packageName)) {
            tmp.delete();
            throw new Exception("El APK no corresponde a esta app");
        }
        if (versionOf(p) <= installedVersionCode(c)) {
            tmp.delete();
            throw new Exception("El APK descargado no es más nuevo que el instalado");
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new Exception("No se pudo guardar la actualización");
        }
    }

    static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    /** Abre el ajuste "Instalar apps desconocidas" de esta app. Devuelve false si el TV no lo tiene. */
    static boolean openInstallPermission(Context c) {
        Intent[] tries = Build.VERSION.SDK_INT >= 26
            ? new Intent[] {
                new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())),
                new Intent(Settings.ACTION_SECURITY_SETTINGS),
            }
            : new Intent[] { new Intent(Settings.ACTION_SECURITY_SETTINGS) };
        for (Intent i : tries) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return false;
    }

    /**
     * Abre el instalador del sistema. Usa el FileProvider que ya trae la app original
     * (autoridad <package>.fileprovider, carpeta "internal_files" = getFilesDir()).
     */
    static void install(Context c) throws Exception {
        if (!file(c).exists()) throw new Exception("Primero hay que descargar la actualización");
        Uri uri = Uri.parse("content://" + c.getPackageName() + ".fileprovider/internal_files/" + DIR + "/" + FILE);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        in.close();
        return new String(buf.toByteArray(), UTF8);
    }
}

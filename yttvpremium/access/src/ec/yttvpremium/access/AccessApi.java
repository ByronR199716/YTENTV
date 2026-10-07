package ec.yttvpremium.access;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import org.json.JSONObject;

/** Llama a validate_access en el servidor. */
final class AccessApi {
    private AccessApi() {}

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** No se pudo hablar con el servidor (sin internet, servidor pausado, error HTTP). */
    static final class UnavailableException extends Exception {
        UnavailableException(String message) { super(message); }
    }

    /** Respuesta del servidor. */
    static final class Result {
        boolean ok;
        String reason = "invalid";
        /** null si el servidor no mandó fecha (p. ej. segunda prueba en el mismo equipo). */
        Long expiresMs;
        Long serverMs;
        double revalidateHours = 12;
        double graceHours = 72;
    }

    static Result validate(String code, String deviceId, String deviceName) throws UnavailableException {
        JSONObject body = new JSONObject();
        try {
            body.put("p_code", code);
            body.put("p_device_id", deviceId);
            body.put("p_device_name", deviceName);
            body.put("p_service", AccessConfig.SERVICE);
        } catch (Exception e) {
            throw new UnavailableException("json");
        }

        HttpURLConnection conn = null;
        String text;
        int status;
        try {
            conn = (HttpURLConnection) new URL(AccessConfig.SUPABASE_URL + "/rest/v1/rpc/validate_access").openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("apikey", AccessConfig.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + AccessConfig.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] payload = body.toString().getBytes(UTF8);
            OutputStream out = conn.getOutputStream();
            out.write(payload);
            out.close();
            status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            text = in == null ? "" : readAll(in);
        } catch (Exception e) {
            throw new UnavailableException("Sin conexión");
        } finally {
            if (conn != null) conn.disconnect();
        }
        if (status < 200 || status >= 300) {
            throw new UnavailableException("HTTP " + status);
        }

        JSONObject o;
        try {
            o = new JSONObject(text);
        } catch (Exception e) {
            throw new UnavailableException("Respuesta inválida");
        }
        Result r = new Result();
        r.ok = o.optBoolean("ok", false);
        r.reason = o.optString("reason", "invalid");
        r.expiresMs = number(o, "expires_ms");
        r.serverMs = number(o, "server_ms");
        Long revalidate = number(o, "revalidate_hours");
        Long grace = number(o, "offline_grace_hours");
        if (revalidate != null) r.revalidateHours = revalidate;
        if (grace != null) r.graceHours = grace;
        return r;
    }

    private static Long number(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        try {
            return o.getLong(key);
        } catch (Exception e) {
            return null;
        }
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

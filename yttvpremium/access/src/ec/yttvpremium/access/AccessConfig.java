package ec.yttvpremium.access;

/**
 * Acceso por códigos de YTTVPremium.
 *
 * Usa el mismo servidor (Supabase), el mismo panel y el MISMO servicio que YTPremium
 * ("ytpremium"): un código de YTPremium sirve también en la TV y no cuesta créditos extra.
 * Cada TV ocupa uno de los dispositivos del código, igual que un teléfono.
 *
 * La "anon public key" está pensada para ir dentro de apps: no da acceso a las tablas,
 * solo a las funciones públicas como validate_access.
 */
final class AccessConfig {
    private AccessConfig() {}

    static final String SUPABASE_URL = "https://dcgaxwkxqbyvuwgsionf.supabase.co";
    static final String SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRjZ2F4d2t4cWJ5dnV3Z3Npb25mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTEyMzIxODUsImV4cCI6MjEwNjgwODE4NX0.Bql5bv4ZIPASPw558vnkTz9idcYmqqqWleMYrzcCXsc";

    /** Servicio en el panel (tabla services). */
    static final String SERVICE = "ytpremium";

    static final String APP_TITLE = "YTTVPremium";

    /** Actividad original de la app (Cobalt) que se abre después de validar. */
    static final String MAIN_ACTIVITY = "dev.cobalt.app.MainActivity";
}

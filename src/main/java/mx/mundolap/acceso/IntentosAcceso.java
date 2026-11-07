package mx.mundolap.acceso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Medida técnica contra ataques de fuerza bruta (no es un requisito del PRY03): tras varios intentos
 * fallidos seguidos con el mismo nombre de usuario, se bloquea ese nombre durante unos minutos.
 * Se registra por nombre escrito (exista o no la cuenta) para no revelar qué usuarios existen.
 */
public final class IntentosAcceso {
    public static final int MAXIMO_FALLOS = 5;
    public static final Duration BLOQUEO = Duration.ofMinutes(5);

    private record Registro(int fallos, Instant bloqueadoHasta) { }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final Clock reloj;

    public IntentosAcceso() { this(Clock.systemUTC()); }

    public IntentosAcceso(Clock reloj) { this.reloj = reloj; }

    public boolean bloqueado(String nombreUsuario) {
        Registro r = registros.get(clave(nombreUsuario));
        return r != null && r.bloqueadoHasta() != null && reloj.instant().isBefore(r.bloqueadoHasta());
    }

    public void fallo(String nombreUsuario) {
        registros.compute(clave(nombreUsuario), (k, r) -> {
            int fallos = (r == null || r.bloqueadoHasta() != null) ? 1 : r.fallos() + 1;
            Instant hasta = fallos >= MAXIMO_FALLOS ? reloj.instant().plus(BLOQUEO) : null;
            return new Registro(fallos, hasta);
        });
    }

    public void exito(String nombreUsuario) { registros.remove(clave(nombreUsuario)); }

    private static String clave(String nombreUsuario) {
        return nombreUsuario == null ? "" : nombreUsuario.trim().toLowerCase(Locale.ROOT);
    }
}

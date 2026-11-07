package mx.mundolap.compartido.web;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import mx.mundolap.compartido.UsuarioSesion;

/**
 * Sesiones de usuario en memoria (se pierden al reiniciar el servidor).
 * Cada sesión vence tras un periodo sin actividad y tiene su propio token CSRF, que los formularios
 * POST deben reenviar para demostrar que salieron de una página del propio sistema.
 */
public final class Sesiones {
    /** Tiempo sin actividad tras el cual se cierra la sesión. */
    public static final Duration INACTIVIDAD_POR_DEFECTO = Duration.ofHours(2);

    private static final class Activa {
        final UsuarioSesion usuario;
        final String csrf;
        volatile Instant ultimoUso;

        Activa(UsuarioSesion usuario, String csrf, Instant ultimoUso) {
            this.usuario = usuario;
            this.csrf = csrf;
            this.ultimoUso = ultimoUso;
        }
    }

    private final Map<String, Activa> activas = new ConcurrentHashMap<>();
    private final SecureRandom azar = new SecureRandom();
    private final Duration inactividad;
    private final Clock reloj;

    public Sesiones() { this(INACTIVIDAD_POR_DEFECTO, Clock.systemUTC()); }

    /** Constructor para pruebas: permite fijar el tiempo de inactividad y el reloj. */
    public Sesiones(Duration inactividad, Clock reloj) {
        this.inactividad = inactividad;
        this.reloj = reloj;
    }

    public String iniciar(UsuarioSesion usuario) {
        purgarVencidas();
        String token = aleatorio();
        activas.put(token, new Activa(usuario, aleatorio(), reloj.instant()));
        return token;
    }

    /** Usuario de la sesión si sigue vigente; cada consulta renueva el tiempo de inactividad. */
    public Optional<UsuarioSesion> obtener(String token) {
        return vigente(token).map(a -> {
            a.ultimoUso = reloj.instant();
            return a.usuario;
        });
    }

    /** Token CSRF de la sesión (vacío si la sesión no existe o venció). */
    public Optional<String> csrf(String token) { return vigente(token).map(a -> a.csrf); }

    public void cerrar(String token) {
        if (token != null) activas.remove(token);
    }

    int cantidadActivas() { return activas.size(); }

    private Optional<Activa> vigente(String token) {
        if (token == null) return Optional.empty();
        Activa a = activas.get(token);
        if (a == null) return Optional.empty();
        if (vencida(a)) {
            activas.remove(token);
            return Optional.empty();
        }
        return Optional.of(a);
    }

    private boolean vencida(Activa a) { return a.ultimoUso.plus(inactividad).isBefore(reloj.instant()); }

    private void purgarVencidas() { activas.values().removeIf(this::vencida); }

    private String aleatorio() {
        byte[] bytes = new byte[24];
        azar.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}

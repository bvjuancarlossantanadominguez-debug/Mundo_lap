package mx.mundolap.compartido.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import mx.mundolap.RelojPrueba;
import mx.mundolap.compartido.Rol;
import mx.mundolap.compartido.UsuarioSesion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SesionesTest {
    private static final UsuarioSesion ROSA = new UsuarioSesion("USR-1", "Rosa", Rol.RECEPCIONISTA);
    private RelojPrueba reloj;
    private Sesiones sesiones;

    @BeforeEach
    void preparar() {
        reloj = new RelojPrueba();
        sesiones = new Sesiones(Duration.ofMinutes(30), reloj);
    }

    @Test
    void laSesionIniciadaDevuelveAlUsuarioYTieneTokenCsrfPropio() {
        String token = sesiones.iniciar(ROSA);
        assertEquals(ROSA, sesiones.obtener(token).orElseThrow());
        String csrf = sesiones.csrf(token).orElseThrow();
        assertNotEquals(token, csrf);
        assertNotEquals(csrf, sesiones.csrf(sesiones.iniciar(ROSA)).orElseThrow());
    }

    @Test
    void venceTrasElTiempoDeInactividad() {
        String token = sesiones.iniciar(ROSA);
        reloj.avanzar(Duration.ofMinutes(31));
        assertFalse(sesiones.obtener(token).isPresent());
        assertFalse(sesiones.csrf(token).isPresent());
    }

    @Test
    void laActividadRenuevaElTiempo() {
        String token = sesiones.iniciar(ROSA);
        reloj.avanzar(Duration.ofMinutes(20));
        assertTrue(sesiones.obtener(token).isPresent());
        reloj.avanzar(Duration.ofMinutes(20));
        assertTrue(sesiones.obtener(token).isPresent());
    }

    @Test
    void cerrarEliminaLaSesionYLasVencidasSePurgan() {
        String token = sesiones.iniciar(ROSA);
        sesiones.cerrar(token);
        assertFalse(sesiones.obtener(token).isPresent());

        sesiones.iniciar(ROSA);
        reloj.avanzar(Duration.ofHours(1));
        sesiones.iniciar(ROSA);
        assertEquals(1, sesiones.cantidadActivas());
    }

    @Test
    void tokenNuloODesconocidoNoTieneSesion() {
        assertFalse(sesiones.obtener(null).isPresent());
        assertFalse(sesiones.obtener("inventado").isPresent());
    }
}

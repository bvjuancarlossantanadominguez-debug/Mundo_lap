package mx.mundolap.acceso;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mx.mundolap.RelojPrueba;
import mx.mundolap.compartido.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutenticacionServicioTest {
    private RelojPrueba reloj;
    private AutenticacionServicio servicio;

    @BeforeEach
    void preparar() {
        UsuarioRepositorio repo = new UsuarioRepositorioMemoria();
        repo.guardar(new Usuario("USR-1", "Rosa", "recepcion", Rol.RECEPCIONISTA,
                AutenticacionServicio.generarCredencial("recepcion123")));
        reloj = new RelojPrueba();
        servicio = new AutenticacionServicio(repo, new IntentosAcceso(reloj));
    }

    @Test
    void aceptaCredencialesCorrectas() {
        assertTrue(servicio.autenticar("recepcion", "recepcion123").isPresent());
    }

    @Test
    void rechazaContrasenaIncorrectaUsuarioInexistenteYVacios() {
        assertFalse(servicio.autenticar("recepcion", "otra").isPresent());
        assertFalse(servicio.autenticar("nadie", "recepcion123").isPresent());
        assertFalse(servicio.autenticar("", "x").isPresent());
        assertFalse(servicio.autenticar("recepcion", null).isPresent());
    }

    @Test
    void laMismaContrasenaGeneraCredencialesDistintasPorLaSal() {
        String a = AutenticacionServicio.generarCredencial("clave");
        String b = AutenticacionServicio.generarCredencial("clave");
        assertNotEquals(a, b);
        assertTrue(AutenticacionServicio.verificar("clave", a));
        assertTrue(AutenticacionServicio.verificar("clave", b));
        assertFalse(AutenticacionServicio.verificar("clave", "no-es-una-credencial"));
    }

    @Test
    void bloqueaTrasCincoFallosYDesbloqueaAlPasarElTiempo() {
        for (int i = 0; i < IntentosAcceso.MAXIMO_FALLOS; i++) servicio.autenticar("recepcion", "mal");
        assertTrue(servicio.bloqueado("recepcion"));
        assertFalse(servicio.autenticar("recepcion", "recepcion123").isPresent(), "bloqueado aun con la clave correcta");

        reloj.avanzar(IntentosAcceso.BLOQUEO.plusSeconds(1));
        assertFalse(servicio.bloqueado("recepcion"));
        assertTrue(servicio.autenticar("recepcion", "recepcion123").isPresent());
    }

    @Test
    void unAccesoCorrectoReiniciaElConteoDeFallos() {
        for (int i = 0; i < IntentosAcceso.MAXIMO_FALLOS - 1; i++) servicio.autenticar("recepcion", "mal");
        assertTrue(servicio.autenticar("recepcion", "recepcion123").isPresent());
        servicio.autenticar("recepcion", "mal");
        assertFalse(servicio.bloqueado("recepcion"));
    }
}

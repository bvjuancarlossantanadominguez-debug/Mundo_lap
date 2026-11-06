package mx.mundolap.clientes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteServicioTest {
    private ClienteServicio servicio;

    @BeforeEach
    void preparar() { servicio = new ClienteServicio(new ClienteRepositorioMemoria()); }

    private static Cliente particular(String nombre, String telefono, String correo) {
        return new Cliente("", nombre, telefono, correo, TipoCliente.PARTICULAR, "", "");
    }

    @Test
    void alGuardarUnClienteNuevoSeLeAsignaIdConsecutivo() {
        assertEquals("CLI-001", servicio.guardar(particular("Ana", "2281112233", "")).idCliente());
        assertEquals("CLI-002", servicio.guardar(particular("Beto", "2284445566", "")).idCliente());
    }

    @Test
    void clienteValidoNoTieneErrores() {
        assertTrue(servicio.validar(particular("Ana López", "2281112233", "ana@correo.com")).isEmpty());
    }

    @Test
    void rechazaTelefonoQueNoTiene10Digitos() {
        List<String> errores = servicio.validar(particular("Ana", "22811", ""));
        assertEquals(List.of("El teléfono debe tener 10 dígitos."), errores);
    }

    @Test
    void rechazaNombreVacioYCorreoInvalido() {
        List<String> errores = servicio.validar(particular(" ", "2281112233", "sin-arroba"));
        assertEquals(2, errores.size());
    }

    @Test
    void rechazaRfcConFormatoInvalido() {
        Cliente c = new Cliente("", "Escuela", "2281112233", "", TipoCliente.CORPORATIVO, "Escuela A.C.", "XYZ");
        assertTrue(servicio.validar(c).contains("El RFC no tiene un formato válido."));
    }

    @Test
    void normalizarLimpiaTelefonoYQuitaDatosFiscalesAParticulares() {
        Cliente c = new Cliente("", " Ana ", "228-111 2233", "", TipoCliente.PARTICULAR, "Razón", "abc");
        Cliente n = servicio.normalizar(c);
        assertEquals("Ana", n.nombreCompleto());
        assertEquals("2281112233", n.telefonoContacto());
        assertEquals("", n.razonSocial());
        assertEquals("", n.rfc());
    }

    @Test
    void rechazaModificarUnClienteConIdInexistente() {
        Cliente inventado = new Cliente("HACK-1", "Fantasma", "2280000000", "", TipoCliente.PARTICULAR, "", "");
        assertTrue(servicio.validar(inventado).contains("El cliente que intenta modificar no existe."));
        assertThrows(IllegalArgumentException.class, () -> servicio.guardar(inventado));
    }

    @Test
    void permiteActualizarUnClienteExistente() {
        Cliente guardado = servicio.guardar(particular("Ana", "2281112233", ""));
        Cliente cambio = new Cliente(guardado.idCliente(), "Ana María", "2281112233", "", TipoCliente.PARTICULAR, "", "");
        assertTrue(servicio.validar(cambio).isEmpty());
        servicio.guardar(cambio);
        assertEquals("Ana María", servicio.buscarPorId("CLI-001").orElseThrow().nombreCompleto());
    }

    @Test
    void detectaDuplicadoPorTelefonoOCorreoSinContarseASiMismo() {
        Cliente ana = servicio.guardar(particular("Ana", "2281112233", "ana@correo.com"));
        assertEquals(1, servicio.duplicados(particular("Otra", "2281112233", "")).size());
        assertEquals(1, servicio.duplicados(particular("Otra", "2289999999", "ANA@correo.com")).size());
        assertTrue(servicio.duplicados(ana).isEmpty());
    }

    @Test
    void buscaPorNombreOTelefono() {
        servicio.guardar(particular("Juan Pérez", "2281112233", ""));
        servicio.guardar(particular("María López", "2284445566", ""));
        assertEquals(1, servicio.buscar("juan").size());
        assertEquals(1, servicio.buscar("44455").size());
        assertEquals(2, servicio.buscar("").size());
    }
}

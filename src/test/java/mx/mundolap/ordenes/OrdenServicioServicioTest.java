package mx.mundolap.ordenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import mx.mundolap.clientes.Cliente;
import mx.mundolap.clientes.ClienteRepositorioMemoria;
import mx.mundolap.clientes.ClienteServicio;
import mx.mundolap.clientes.TipoCliente;
import mx.mundolap.compartido.EstadoOrden;
import mx.mundolap.equipos.Equipo;
import mx.mundolap.equipos.EquipoRepositorioMemoria;
import mx.mundolap.equipos.EquipoServicio;
import mx.mundolap.equipos.TipoDispositivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pruebas del flujo de recepción: cliente → equipo → orden (RF-16, RF-17, RF-19, RF-20) y de EquipoServicio. */
class OrdenServicioServicioTest {
    private ClienteServicio clientes;
    private EquipoServicio equipos;
    private OrdenServicioServicio ordenes;
    private Cliente ana;
    private Cliente beto;
    private Equipo celularAna;
    private Equipo laptopBeto;

    @BeforeEach
    void preparar() {
        clientes = new ClienteServicio(new ClienteRepositorioMemoria());
        equipos = new EquipoServicio(new EquipoRepositorioMemoria());
        ordenes = new OrdenServicioServicio(new OrdenRepositorioMemoria(), clientes, equipos, new CostoDiagnosticoEjemplo());
        clientes.usarRelacionOrdenes(ordenes);
        equipos.usarRelacionOrdenes(ordenes);

        ana = clientes.guardar(new Cliente("", "Ana", "2281112233", "", TipoCliente.PARTICULAR, "", ""));
        beto = clientes.guardar(new Cliente("", "Beto", "2284445566", "", TipoCliente.PARTICULAR, "", ""));
        celularAna = equipos.guardar(new Equipo("", ana.idCliente(), TipoDispositivo.CELULAR, "Samsung", "A15", "S1"));
        laptopBeto = equipos.guardar(new Equipo("", beto.idCliente(), TipoDispositivo.LAPTOP, "HP", "15", "S2"));
    }

    private static LocalDate manana() { return LocalDate.now().plusDays(1); }

    @Test
    void creaOrdenConFolioEstadoRecibidoYCostoEstimado() {
        OrdenServicio o = ordenes.crear(ana.idCliente(), celularAna.idEquipo(), " No enciende ", Prioridad.ALTA, manana());
        assertEquals("OS-0001", o.folio());
        assertEquals(EstadoOrden.RECIBIDO, o.estadoActual());
        assertEquals("No enciende", o.problemaReportado());
        assertEquals(new BigDecimal("250.00"), o.costoEstimado());
        assertEquals(ana.idCliente(), o.idCliente());
        assertEquals(celularAna.idEquipo(), o.idEquipo());
    }

    @Test
    void losFoliosSonUnicosYConsecutivos() {
        String f1 = ordenes.crear(ana.idCliente(), celularAna.idEquipo(), "a", Prioridad.NORMAL, manana()).folio();
        String f2 = ordenes.crear(ana.idCliente(), celularAna.idEquipo(), "b", Prioridad.NORMAL, manana()).folio();
        assertEquals("OS-0002", f2);
        assertFalse(f1.equals(f2));
    }

    @Test
    void rechazaFechaDeEntregaAnteriorAHoy() {
        List<String> errores = ordenes.validar(ana.idCliente(), celularAna.idEquipo(), "x", LocalDate.now().minusDays(1));
        assertEquals(List.of("La fecha de entrega estimada no puede ser anterior a hoy."), errores);
    }

    @Test
    void rechazaEquipoQueNoEsDelCliente() {
        List<String> errores = ordenes.validar(ana.idCliente(), laptopBeto.idEquipo(), "x", manana());
        assertTrue(errores.contains("Seleccione un equipo registrado de este cliente."));
        assertThrows(IllegalArgumentException.class,
                () -> ordenes.crear(ana.idCliente(), laptopBeto.idEquipo(), "x", Prioridad.NORMAL, manana()));
    }

    @Test
    void rechazaProblemaVacioYFechaFaltante() {
        assertEquals(2, ordenes.validar(ana.idCliente(), celularAna.idEquipo(), "  ", null).size());
    }

    @Test
    void cuentaOrdenesActivasPorClienteYPorEstado() {
        ordenes.crear(ana.idCliente(), celularAna.idEquipo(), "x", Prioridad.NORMAL, manana());
        assertEquals(1, clientes.ordenesActivas(ana.idCliente()));
        assertEquals(0, clientes.ordenesActivas(beto.idCliente()));
        assertEquals(1, equipos.totalOrdenes(celularAna.idEquipo()));
        assertEquals(1, ordenes.cantidadEnEstado(EstadoOrden.RECIBIDO));
    }

    @Test
    void buscaPorFolioSinImportarMayusculasNiEspacios() {
        ordenes.crear(ana.idCliente(), celularAna.idEquipo(), "x", Prioridad.NORMAL, manana());
        assertTrue(ordenes.buscarPorFolio(" os-0001 ").isPresent());
        assertEquals(1, clientes.buscar("OS-0001").size());
    }

    @Test
    void equipoExigeMarcaModeloYSerieUnica() {
        Equipo sinDatos = new Equipo("", ana.idCliente(), TipoDispositivo.CELULAR, "", "", "");
        assertEquals(2, equipos.validar(sinDatos).size());
        Equipo serieRepetida = new Equipo("", ana.idCliente(), TipoDispositivo.TABLET, "Lenovo", "M10", "s1");
        assertEquals(1, equipos.validar(serieRepetida).size());
        Equipo sinTipo = new Equipo("", ana.idCliente(), null, "Lenovo", "M10", "");
        assertTrue(equipos.validar(sinTipo).contains("Seleccione el tipo de dispositivo."));
    }
}

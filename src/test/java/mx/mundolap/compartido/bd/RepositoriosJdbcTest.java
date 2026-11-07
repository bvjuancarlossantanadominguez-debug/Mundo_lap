package mx.mundolap.compartido.bd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import mx.mundolap.acceso.AutenticacionServicio;
import mx.mundolap.acceso.Usuario;
import mx.mundolap.acceso.UsuarioRepositorioJdbc;
import mx.mundolap.clientes.Cliente;
import mx.mundolap.clientes.ClienteRepositorioJdbc;
import mx.mundolap.clientes.ClienteServicio;
import mx.mundolap.clientes.TipoCliente;
import mx.mundolap.compartido.EstadoOrden;
import mx.mundolap.compartido.Rol;
import mx.mundolap.equipos.Equipo;
import mx.mundolap.equipos.EquipoRepositorioJdbc;
import mx.mundolap.equipos.EquipoServicio;
import mx.mundolap.equipos.TipoDispositivo;
import mx.mundolap.ordenes.CostoDiagnosticoEjemplo;
import mx.mundolap.ordenes.OrdenRepositorioJdbc;
import mx.mundolap.ordenes.OrdenServicio;
import mx.mundolap.ordenes.OrdenServicioServicio;
import mx.mundolap.ordenes.Prioridad;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de los repositorios y del script SQL contra un MySQL real, en una base aparte
 * ("Mundo_Lap_prueba") que se crea y se borra en cada prueba, así nunca toca los datos del sistema.
 * Si MySQL no está disponible o no está configurada la contraseña, estas pruebas se omiten.
 */
class RepositoriosJdbcTest {
    private static final String BASE_PRUEBA = "Mundo_Lap_prueba";

    private ClienteServicio clientes;
    private EquipoServicio equipos;
    private OrdenServicioServicio ordenes;

    @BeforeEach
    void preparar() throws SQLException {
        System.setProperty("mundolap.bd.nombre", BASE_PRUEBA);
        assumeTrue(ConexionBD.preparar(), "MySQL no disponible: se omiten las pruebas JDBC");
        borrarBase();
        assumeTrue(ConexionBD.preparar(), "MySQL no disponible: se omiten las pruebas JDBC");
        EsquemaBD.crear();
        clientes = new ClienteServicio(new ClienteRepositorioJdbc());
        equipos = new EquipoServicio(new EquipoRepositorioJdbc());
        ordenes = new OrdenServicioServicio(new OrdenRepositorioJdbc(), clientes, equipos, new CostoDiagnosticoEjemplo());
        clientes.usarRelacionOrdenes(ordenes);
        equipos.usarRelacionOrdenes(ordenes);
    }

    @AfterEach
    void limpiar() throws SQLException {
        if (clientes != null) borrarBase();
        System.clearProperty("mundolap.bd.nombre");
    }

    private static void borrarBase() throws SQLException {
        try (Connection cn = ConexionBD.obtener(); Statement st = cn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS `" + BASE_PRUEBA + "`");
        }
    }

    @Test
    void elFlujoClienteEquipoOrdenQuedaGuardadoEnLaBase() {
        Cliente ana = clientes.guardar(new Cliente("", "Ana", "2281112233", "ana@correo.com", TipoCliente.PARTICULAR, "", ""));
        Equipo cel = equipos.guardar(new Equipo("", ana.idCliente(), TipoDispositivo.CELULAR, "Samsung", "A15", "SN-1"));
        OrdenServicio o = ordenes.crear(ana.idCliente(), cel.idEquipo(), "No enciende", Prioridad.ALTA,
                LocalDate.now().plusDays(2));

        assertEquals("CLI-001", ana.idCliente());
        assertEquals("EQ-001", cel.idEquipo());
        assertEquals("OS-0001", o.folio());

        OrdenServicio leida = new OrdenRepositorioJdbc().buscarPorFolio("OS-0001").orElseThrow();
        assertEquals(EstadoOrden.RECIBIDO, leida.estadoActual());
        assertEquals(Prioridad.ALTA, leida.prioridad());
        assertEquals(new BigDecimal("250.00"), leida.costoEstimado());
        assertEquals(1, clientes.ordenesActivas(ana.idCliente()));
        assertEquals(1, clientes.buscar("os-0001").size());
    }

    @Test
    void actualizarUnClienteNoCreaOtroRegistro() {
        Cliente ana = clientes.guardar(new Cliente("", "Ana", "2281112233", "", TipoCliente.PARTICULAR, "", ""));
        clientes.guardar(new Cliente(ana.idCliente(), "Ana María", "2281112233", "", TipoCliente.PARTICULAR, "", ""));
        assertEquals(1, clientes.buscar("").size());
        assertEquals("Ana María", clientes.buscarPorId(ana.idCliente()).orElseThrow().nombreCompleto());
    }

    @Test
    void elNumeroDeSerieRepetidoSeDetectaSinImportarMayusculas() {
        Cliente ana = clientes.guardar(new Cliente("", "Ana", "2281112233", "", TipoCliente.PARTICULAR, "", ""));
        equipos.guardar(new Equipo("", ana.idCliente(), TipoDispositivo.LAPTOP, "HP", "15", "ABC-1"));
        Equipo repetido = new Equipo("", ana.idCliente(), TipoDispositivo.TABLET, "Lenovo", "M10", "abc-1");
        assertEquals(1, equipos.validar(repetido).size());
        assertThrows(ErrorBD.class, () -> equipos.guardar(repetido), "la base también lo rechaza");
    }

    @Test
    void laBaseRechazaUnaOrdenConEquipoDeOtroCliente() {
        Cliente ana = clientes.guardar(new Cliente("", "Ana", "2281112233", "", TipoCliente.PARTICULAR, "", ""));
        Cliente beto = clientes.guardar(new Cliente("", "Beto", "2284445566", "", TipoCliente.PARTICULAR, "", ""));
        Equipo deBeto = equipos.guardar(new Equipo("", beto.idCliente(), TipoDispositivo.LAPTOP, "HP", "15", ""));
        OrdenServicio invalida = new OrdenServicio("OS-9999", ana.idCliente(), deBeto.idEquipo(), "x", "",
                EstadoOrden.RECIBIDO, java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
                Prioridad.NORMAL, BigDecimal.TEN);
        assertThrows(ErrorBD.class, () -> new OrdenRepositorioJdbc().guardar(invalida));
    }

    @Test
    void losUsuariosSeGuardanConLaContrasenaCifrada() {
        UsuarioRepositorioJdbc repo = new UsuarioRepositorioJdbc();
        assertTrue(repo.vacio());
        repo.guardar(new Usuario("USR-001", "Rosa", "recepcion", Rol.RECEPCIONISTA,
                AutenticacionServicio.generarCredencial("recepcion123")));
        assertFalse(repo.vacio());
        assertTrue(new AutenticacionServicio(repo).autenticar("recepcion", "recepcion123").isPresent());
        assertFalse(repo.buscarPorNombreUsuario("recepcion").orElseThrow().credencialesAcceso().contains("recepcion123"));
        assertFalse(repo.buscarPorNombreUsuario("RECEPCION").isPresent(), "el usuario distingue mayúsculas");
    }

    @Test
    void variosEquiposSinNumeroDeSeriePuedenCoexistir() {
        Cliente ana = clientes.guardar(new Cliente("", "Ana", "2281112233", "", TipoCliente.PARTICULAR, "", ""));
        equipos.guardar(new Equipo("", ana.idCliente(), TipoDispositivo.CELULAR, "A", "1", ""));
        equipos.guardar(new Equipo("", ana.idCliente(), TipoDispositivo.CELULAR, "B", "2", ""));
        assertEquals(2, equipos.deCliente(ana.idCliente()).size());
        assertEquals("", equipos.deCliente(ana.idCliente()).get(0).numeroSerie());
    }

    /** Validación de restricciones de integridad del script (PK, FK, UNIQUE y CHECK) en las 8 tablas. */
    @Test
    void elScriptHaceCumplirLasRestriccionesDeIntegridad() throws SQLException {
        Cliente ana = clientes.guardar(new Cliente("", "Ana", "2281112233", "", TipoCliente.PARTICULAR, "", ""));
        Equipo cel = equipos.guardar(new Equipo("", ana.idCliente(), TipoDispositivo.CELULAR, "Samsung", "A15", ""));
        ordenes.crear(ana.idCliente(), cel.idEquipo(), "No enciende", Prioridad.NORMAL, LocalDate.now().plusDays(1));

        try (Connection cn = ConexionBD.obtener(); Statement st = cn.createStatement()) {
            // Datos válidos en las tablas de pagos y refacciones
            st.executeUpdate("INSERT INTO pago (folio, concepto_cobro, monto, metodo_pago) VALUES ('OS-0001','ANTICIPO',100,'EFECTIVO')");
            st.executeUpdate("INSERT INTO proveedor (nombre) VALUES ('Refacciones Xalapa')");
            st.executeUpdate("INSERT INTO refaccion (nombre_pieza, stock, stock_minimo, id_proveedor) VALUES ('Pantalla A15', 3, 1, 1)");
            st.executeUpdate("INSERT INTO solicitud_refaccion (folio, id_refaccion, id_proveedor, cantidad, tiempo_estimado_dias) "
                    + "VALUES ('OS-0001', 1, 1, 1, 3)");

            String[] invalidas = {
                // Llaves primarias duplicadas
                "INSERT INTO cliente (id_cliente, nombre_completo, telefono_contacto, tipo_cliente) VALUES ('CLI-001','X','2289990000','PARTICULAR')",
                // Llaves foráneas inexistentes
                "INSERT INTO equipo (id_equipo, id_cliente, tipo_dispositivo, marca, modelo) VALUES ('EQ-900','CLI-999','CELULAR','A','B')",
                "INSERT INTO pago (folio, concepto_cobro, monto, metodo_pago) VALUES ('OS-9999','ABONO',50,'EFECTIVO')",
                "INSERT INTO refaccion (nombre_pieza, id_proveedor) VALUES ('Batería', 999)",
                "INSERT INTO solicitud_refaccion (folio, id_refaccion, id_proveedor, cantidad, tiempo_estimado_dias) VALUES ('OS-0001', 999, 1, 1, 1)",
                // Integridad referencial al borrar (RESTRICT)
                "DELETE FROM cliente WHERE id_cliente = 'CLI-001'",
                "DELETE FROM orden_servicio WHERE folio = 'OS-0001'",
                "DELETE FROM proveedor WHERE id_proveedor = 1",
                // CHECK y UNIQUE
                "INSERT INTO cliente (id_cliente, nombre_completo, telefono_contacto, tipo_cliente) VALUES ('CLI-900','X','22811','PARTICULAR')",
                "INSERT INTO pago (folio, concepto_cobro, monto, metodo_pago) VALUES ('OS-0001','ABONO',-5,'EFECTIVO')",
                "INSERT INTO pago (folio, concepto_cobro, monto, metodo_pago) VALUES ('OS-0001','REGALO',5,'EFECTIVO')",
                "INSERT INTO refaccion (nombre_pieza, stock, id_proveedor) VALUES ('Cámara', -1, 1)",
                "INSERT INTO solicitud_refaccion (folio, id_refaccion, id_proveedor, cantidad, tiempo_estimado_dias) VALUES ('OS-0001', 1, 1, 0, 1)",
                "INSERT INTO proveedor (nombre) VALUES ('Refacciones Xalapa')",
                "UPDATE orden_servicio SET estado_actual = 'PERDIDO' WHERE folio = 'OS-0001'",
            };
            for (String sql : invalidas) {
                assertThrows(SQLException.class, () -> st.executeUpdate(sql), "debió rechazarse: " + sql);
            }
        }
    }
}

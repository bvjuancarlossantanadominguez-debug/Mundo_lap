package mx.mundolap.demo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import mx.mundolap.acceso.AutenticacionServicio;
import mx.mundolap.acceso.Usuario;
import mx.mundolap.acceso.UsuarioRepositorio;
import mx.mundolap.clientes.Cliente;
import mx.mundolap.clientes.ClienteServicio;
import mx.mundolap.clientes.TipoCliente;
import mx.mundolap.compartido.EstadoOrden;
import mx.mundolap.compartido.Rol;
import mx.mundolap.equipos.Equipo;
import mx.mundolap.equipos.EquipoServicio;
import mx.mundolap.equipos.TipoDispositivo;
import mx.mundolap.ordenes.OrdenServicio;
import mx.mundolap.ordenes.OrdenServicioServicio;
import mx.mundolap.ordenes.Prioridad;

/**
 * Datos de ejemplo para poder usar y capturar las pantallas mientras no exista la base de datos.
 * Al conectar la BD real, se elimina este paquete y la llamada en App.java.
 */
public final class DatosDemo {
    private DatosDemo() { }

    public static void cargar(UsuarioRepositorio usuarios, ClienteServicio clientes,
                              EquipoServicio equipos, OrdenServicioServicio ordenes) {
        usuarios.guardar(new Usuario("USR-001", "Rosa Hernández", "recepcion", Rol.RECEPCIONISTA,
                AutenticacionServicio.generarCredencial("recepcion123")));
        usuarios.guardar(new Usuario("USR-002", "Luis Mora", "tecnico", Rol.TECNICO,
                AutenticacionServicio.generarCredencial("tecnico123")));
        usuarios.guardar(new Usuario("USR-003", "Carmen Ortiz", "gerente", Rol.GERENTE,
                AutenticacionServicio.generarCredencial("gerente123")));

        Cliente c1 = clientes.guardar(new Cliente("", "Juan Pérez Morales", "2281234567", "", TipoCliente.PARTICULAR, "", ""));
        Cliente c2 = clientes.guardar(new Cliente("", "María Fernanda López", "2289876543", "mfernanda@correo.com", TipoCliente.PARTICULAR, "", ""));
        Cliente c3 = clientes.guardar(new Cliente("", "Escuela Técnica Benito Juárez", "2288001122", "compras@etbj.edu.mx",
                TipoCliente.CORPORATIVO, "Escuela Técnica Benito Juárez A.C.", "ETB850101AB1"));

        Equipo e1 = equipos.guardar(new Equipo("", c1.idCliente(), TipoDispositivo.CELULAR, "Samsung", "A15", "RF8N30XXXX1"));
        Equipo e2 = equipos.guardar(new Equipo("", c2.idCliente(), TipoDispositivo.LAPTOP, "HP", "15-dw1024", "5CD2011XXX2"));
        Equipo e3 = equipos.guardar(new Equipo("", c3.idCliente(), TipoDispositivo.PC_ESCRITORIO, "Dell", "OptiPlex 3080", "DL3080XXX3"));
        Equipo e4 = equipos.guardar(new Equipo("", c3.idCliente(), TipoDispositivo.TABLET, "Lenovo", "Tab M10", "LNM10XXXX4"));

        LocalDateTime ahora = LocalDateTime.now();
        orden(ordenes, c1, e1, "El equipo no enciende.", EstadoOrden.EN_REPARACION, Prioridad.ALTA, ahora.minusDays(2), 2, "250.00");
        orden(ordenes, c2, e2, "Pantalla con líneas y se calienta mucho.", EstadoOrden.ESPERANDO_PIEZAS, Prioridad.NORMAL, ahora.minusDays(4), 5, "350.00");
        orden(ordenes, c3, e3, "Mantenimiento preventivo del laboratorio.", EstadoOrden.RECIBIDO, Prioridad.NORMAL, ahora.minusHours(3), 6, "300.00");
        orden(ordenes, c3, e4, "No carga la batería.", EstadoOrden.TERMINADO, Prioridad.NORMAL, ahora.minusDays(6), 1, "250.00");
    }

    private static void orden(OrdenServicioServicio s, Cliente c, Equipo e, String problema, EstadoOrden estado,
                              Prioridad prioridad, LocalDateTime recepcion, int diasEntrega, String costo) {
        s.registrarExistente(new OrdenServicio(s.siguienteFolio(), c.idCliente(), e.idEquipo(), problema, "", estado,
                recepcion, recepcion.plusDays(diasEntrega).toLocalDate().atStartOfDay(), prioridad, new BigDecimal(costo)));
    }
}

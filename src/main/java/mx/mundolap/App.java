package mx.mundolap;

import java.sql.SQLException;
import java.util.List;
import mx.mundolap.acceso.AccesoModulo;
import mx.mundolap.acceso.AutenticacionServicio;
import mx.mundolap.acceso.UsuarioRepositorio;
import mx.mundolap.acceso.UsuarioRepositorioJdbc;
import mx.mundolap.acceso.UsuarioRepositorioMemoria;
import mx.mundolap.caja.CajaModulo;
import mx.mundolap.clientes.ClienteRepositorio;
import mx.mundolap.clientes.ClienteRepositorioJdbc;
import mx.mundolap.clientes.ClienteRepositorioMemoria;
import mx.mundolap.clientes.ClienteServicio;
import mx.mundolap.clientes.ClientesModulo;
import mx.mundolap.compartido.Modulo;
import mx.mundolap.compartido.bd.ConexionBD;
import mx.mundolap.compartido.bd.EsquemaBD;
import mx.mundolap.compartido.web.Router;
import mx.mundolap.compartido.web.Servidor;
import mx.mundolap.compartido.web.Sesiones;
import mx.mundolap.demo.DatosDemo;
import mx.mundolap.equipos.EquipoRepositorio;
import mx.mundolap.equipos.EquipoRepositorioJdbc;
import mx.mundolap.equipos.EquipoRepositorioMemoria;
import mx.mundolap.equipos.EquipoServicio;
import mx.mundolap.equipos.EquiposModulo;
import mx.mundolap.ordenes.CostoDiagnosticoEjemplo;
import mx.mundolap.ordenes.OrdenRepositorio;
import mx.mundolap.ordenes.OrdenRepositorioJdbc;
import mx.mundolap.ordenes.OrdenRepositorioMemoria;
import mx.mundolap.ordenes.OrdenServicioServicio;
import mx.mundolap.ordenes.OrdenesModulo;
import mx.mundolap.pagos.PagosModulo;
import mx.mundolap.refacciones.RefaccionesModulo;
import mx.mundolap.reportes.ReportesModulo;
import mx.mundolap.tecnico.TecnicoModulo;
import mx.mundolap.usuarios.UsuariosModulo;

/**
 * Punto de arranque. Aquí se "arma" el sistema: se crean las piezas y se conectan.
 *  - Con MySQL disponible, los datos se guardan en la base (repositorios JDBC).
 *  - Si no se puede conectar, el sistema arranca igual con repositorios en memoria y lo avisa en consola.
 *  - Para agregar un módulo: crear su clase que implemente Modulo y añadirla a la lista "modulos".
 */
public final class App {

    public static void main(String[] args) throws Exception {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : Integer.getInteger("puerto", 8080);
        // Por seguridad solo escucha en esta computadora. Para abrirlo en la red local: -Dmundolap.host=0.0.0.0
        String host = System.getProperty("mundolap.host", "127.0.0.1");

        // ---- Datos: MySQL si está disponible; si no, memoria ----
        boolean conBD = prepararBaseDeDatos();
        UsuarioRepositorio usuarios = conBD ? new UsuarioRepositorioJdbc() : new UsuarioRepositorioMemoria();
        ClienteRepositorio repoClientes = conBD ? new ClienteRepositorioJdbc() : new ClienteRepositorioMemoria();
        EquipoRepositorio repoEquipos = conBD ? new EquipoRepositorioJdbc() : new EquipoRepositorioMemoria();
        OrdenRepositorio repoOrdenes = conBD ? new OrdenRepositorioJdbc() : new OrdenRepositorioMemoria();

        ClienteServicio clientes = new ClienteServicio(repoClientes);
        EquipoServicio equipos = new EquipoServicio(repoEquipos);
        OrdenServicioServicio ordenes = new OrdenServicioServicio(repoOrdenes, clientes, equipos,
                new CostoDiagnosticoEjemplo());
        clientes.usarRelacionOrdenes(ordenes);
        equipos.usarRelacionOrdenes(ordenes);

        // Datos iniciales solo la primera vez (base vacía): usuarios para iniciar sesión y ejemplos.
        if (usuarios.vacio()) {
            DatosDemo.cargar(usuarios, clientes, equipos, ordenes);
            System.out.println("[Datos] Se cargaron los datos iniciales de ejemplo.");
        }

        // ---- Módulos del sistema ----
        Sesiones sesiones = new Sesiones();
        List<Modulo> modulos = List.of(
                new AccesoModulo(new AutenticacionServicio(usuarios), sesiones, ordenes),
                new ClientesModulo(clientes),
                new EquiposModulo(clientes, equipos),
                new OrdenesModulo(clientes, equipos, ordenes),
                new PagosModulo(),
                new TecnicoModulo(),
                new RefaccionesModulo(),
                new UsuariosModulo(),
                new CajaModulo(),
                new ReportesModulo()
        );

        Router router = new Router();
        for (Modulo m : modulos) {
            m.registrarRutas(router);
            router.agregarMenu(m.menu());
        }

        new Servidor(router, sesiones).iniciar(host, puerto);
        System.out.println("Mundo Lap en marcha: http://localhost:" + puerto
                + (conBD ? "  (datos en MySQL, base " + ConexionBD.nombreBase() + ")" : "  (SIN BASE DE DATOS: los datos se pierden al cerrar)"));
        System.out.println("Usuarios -> recepcion/recepcion123 | tecnico/tecnico123 | gerente/gerente123");
    }

    /** Conecta, crea la base si falta y crea las tablas si faltan. Devuelve false si algo falla. */
    private static boolean prepararBaseDeDatos() {
        if (!ConexionBD.preparar()) {
            System.err.println("[BD] El sistema arranca SIN base de datos (datos en memoria).");
            return false;
        }
        try {
            EsquemaBD.crear();
            System.out.println("[BD] Tablas verificadas en la base " + ConexionBD.nombreBase() + ".");
            return true;
        } catch (SQLException e) {
            System.err.println("[BD] No se pudieron crear las tablas: " + ConexionBD.describirError(e));
            System.err.println("[BD] El sistema arranca SIN base de datos (datos en memoria).");
            return false;
        }
    }
}

package mx.mundolap;

import java.util.List;
import mx.mundolap.acceso.AccesoModulo;
import mx.mundolap.acceso.AutenticacionServicio;
import mx.mundolap.acceso.UsuarioRepositorio;
import mx.mundolap.acceso.UsuarioRepositorioMemoria;
import mx.mundolap.clientes.ClienteRepositorioMemoria;
import mx.mundolap.clientes.ClienteServicio;
import mx.mundolap.clientes.ClientesModulo;
import mx.mundolap.compartido.Modulo;
import mx.mundolap.compartido.bd.ConexionBD;
import mx.mundolap.compartido.web.Router;
import mx.mundolap.compartido.web.Servidor;
import mx.mundolap.compartido.web.Sesiones;
import mx.mundolap.demo.DatosDemo;
import mx.mundolap.equipos.EquipoRepositorioMemoria;
import mx.mundolap.equipos.EquipoServicio;
import mx.mundolap.equipos.EquiposModulo;
import mx.mundolap.ordenes.CostoDiagnosticoEjemplo;
import mx.mundolap.ordenes.OrdenRepositorioMemoria;
import mx.mundolap.ordenes.OrdenServicioServicio;
import mx.mundolap.ordenes.OrdenesModulo;
import mx.mundolap.caja.CajaModulo;
import mx.mundolap.pagos.PagosModulo;
import mx.mundolap.refacciones.RefaccionesModulo;
import mx.mundolap.reportes.ReportesModulo;
import mx.mundolap.tecnico.TecnicoModulo;
import mx.mundolap.usuarios.UsuariosModulo;

/**
 * Punto de arranque. Aquí se "arma" el sistema: se crean las piezas y se conectan.
 *  - Para conectar la base de datos: cambiar los repositorios *Memoria por versiones JDBC.
 *  - Para agregar un módulo: crear su clase que implemente Modulo y añadirla a la lista "modulos".
 */
public final class App {

    public static void main(String[] args) throws Exception {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : Integer.getInteger("puerto", 8080);
        // Por seguridad solo escucha en esta computadora. Para abrirlo en la red local: -Dmundolap.host=0.0.0.0
        String host = System.getProperty("mundolap.host", "127.0.0.1");

        // ---- Base de datos: solo verifica la conexión y avisa; el sistema sigue con repositorios en memoria ----
        ConexionBD.probar();

        // ---- Datos (hoy en memoria) ----
        UsuarioRepositorio usuarios = new UsuarioRepositorioMemoria();
        ClienteServicio clientes = new ClienteServicio(new ClienteRepositorioMemoria());
        EquipoServicio equipos = new EquipoServicio(new EquipoRepositorioMemoria());
        OrdenServicioServicio ordenes = new OrdenServicioServicio(
                new OrdenRepositorioMemoria(), clientes, equipos, new CostoDiagnosticoEjemplo());
        clientes.usarRelacionOrdenes(ordenes);
        equipos.usarRelacionOrdenes(ordenes);

        DatosDemo.cargar(usuarios, clientes, equipos, ordenes);

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
                // new InventarioModulo(...)  <- futuro
        );

        Router router = new Router();
        for (Modulo m : modulos) {
            m.registrarRutas(router);
            router.agregarMenu(m.menu());
        }

        new Servidor(router, sesiones).iniciar(host, puerto);
        System.out.println("Mundo Lap en marcha: http://localhost:" + puerto);
        System.out.println("Usuarios de ejemplo -> recepcion/recepcion123 | tecnico/tecnico123 | gerente/gerente123");
    }
}

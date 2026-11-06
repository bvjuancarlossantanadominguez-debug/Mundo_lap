package mx.mundolap.acceso;

import java.util.Optional;
import mx.mundolap.compartido.EstadoOrden;
import mx.mundolap.compartido.Html;
import mx.mundolap.compartido.RelacionOrdenes;
import mx.mundolap.compartido.Rol;
import mx.mundolap.compartido.UsuarioSesion;
import mx.mundolap.compartido.Vistas;
import mx.mundolap.compartido.web.Contexto;
import mx.mundolap.compartido.web.Sesiones;

/** P-01 Inicio de sesión del personal y P-05 Panel principal. */
public final class AccesoControlador {
    private final AutenticacionServicio autenticacion;
    private final Sesiones sesiones;
    private final RelacionOrdenes ordenes;

    public AccesoControlador(AutenticacionServicio autenticacion, Sesiones sesiones, RelacionOrdenes ordenes) {
        this.autenticacion = autenticacion;
        this.sesiones = sesiones;
        this.ordenes = ordenes;
    }

    public void inicio(Contexto c) throws Exception {
        c.redirigir(c.usuario() != null ? "/panel" : "/login");
    }

    public void formularioLogin(Contexto c) throws Exception {
        if (c.usuario() != null) {
            c.redirigir("/panel");
            return;
        }
        c.html(login("", ""));
    }

    public void iniciarSesion(Contexto c) throws Exception {
        String nombre = c.param("usuario");
        if (autenticacion.bloqueado(nombre)) {
            c.responder(429, login(nombre, "Demasiados intentos fallidos. Espere unos minutos e intente de nuevo."));
            return;
        }
        Optional<Usuario> u = autenticacion.autenticar(nombre, c.param("clave"));
        if (u.isEmpty()) {
            // Mensaje genérico: no revela si falló el usuario o la contraseña (RF-04).
            c.responder(401, login(nombre, "Usuario o contraseña incorrectos."));
            return;
        }
        Usuario usuario = u.get();
        String token = sesiones.iniciar(new UsuarioSesion(
                usuario.idUsuario(), usuario.nombreCompleto(), usuario.rolAsignado()));
        c.fijarSesion(token);
        c.redirigir("/panel");
    }

    public void cerrarSesion(Contexto c) throws Exception {
        sesiones.cerrar(c.token());
        c.borrarSesion();
        c.redirigir("/login");
    }

    public void panel(Contexto c) throws Exception {
        UsuarioSesion u = c.usuario();
        StringBuilder contadores = new StringBuilder();
        EstadoOrden[] mostrados = {EstadoOrden.RECIBIDO, EstadoOrden.EN_REPARACION,
                EstadoOrden.ESPERANDO_PIEZAS, EstadoOrden.TERMINADO};
        for (EstadoOrden e : mostrados) {
            contadores.append("<div class=\"contador estado-").append(e.name().toLowerCase()).append("\">")
                    .append("<span class=\"numero\">").append(ordenes.cantidadEnEstado(e)).append("</span>")
                    .append("<span class=\"etiqueta\">").append(Html.escapar(e.etiqueta())).append("</span></div>");
        }
        String accionNueva = u.rol() == Rol.RECEPCIONISTA
                ? "<a class=\"btn btn-primario\" href=\"/ordenes/nueva\">+ Nueva orden</a>" : "";
        var v = Vistas.datos("saludo", "Hola, " + u.nombreCompleto());
        v.put("contadoresHtml", contadores.toString());
        v.put("accionNuevaHtml", accionNueva);
        c.html(c.pagina("Panel principal", Vistas.render("acceso/panel", v)));
    }

    private String login(String usuario, String error) {
        var v = Vistas.datos("usuario", usuario);
        v.put("errorHtml", error.isEmpty() ? "" : Html.alerta("error", error));
        return Vistas.render("acceso/login", v);
    }
}

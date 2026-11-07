package mx.mundolap.compartido.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import mx.mundolap.compartido.UsuarioSesion;

/** Servidor web basado en el JDK. Despacha cada petición al Router. */
public final class Servidor {
    private static final Logger LOG = Logger.getLogger(Servidor.class.getName());

    private final Router router;
    private final Sesiones sesiones;

    public Servidor(Router router, Sesiones sesiones) {
        this.router = router;
        this.sesiones = sesiones;
    }

    /**
     * @param host "127.0.0.1" (solo esta computadora, valor por defecto) o "0.0.0.0" para que otros
     *             equipos de la red local puedan entrar (por ejemplo, para mostrarlo desde un celular).
     */
    public void iniciar(String host, int puerto) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(host, puerto), 0);
        servidor.createContext("/", this::atender);
        servidor.setExecutor(Executors.newFixedThreadPool(8));
        servidor.start();
    }

    private void atender(HttpExchange ex) {
        try {
            String ruta = ex.getRequestURI().getPath();
            if (ruta.startsWith("/static/")) {
                estatico(ex, ruta);
                return;
            }
            Contexto c = new Contexto(ex, router, sesiones);
            Router.Ruta r = router.buscar(ex.getRequestMethod(), ruta);
            if (r == null) {
                c.responder(404, c.pagina("Página no encontrada", "<p>La dirección solicitada no existe.</p>"));
                return;
            }
            // Todo POST hecho con una sesión iniciada debe traer el token CSRF de esa sesión.
            if ("POST".equals(c.metodo()) && c.usuario() != null && !c.csrfValido()) {
                LOG.warning("POST rechazado por token CSRF inválido -> " + ruta);
                c.responder(403, c.pagina("Formulario no válido",
                        "<p>El formulario expiró o no se envió desde Mundo Lap. Vuelva a la página y repita la acción.</p>"
                                + "<a class=\"btn btn-secundario\" href=\"/panel\">Ir al panel</a>"));
                return;
            }
            if (r.roles() != null) {
                UsuarioSesion u = c.usuario();
                if (u == null) {
                    c.redirigir("/login");
                    return;
                }
                if (!r.roles().contains(u.rol())) {
                    // RF-05: se bloquea el acceso y se registra el intento.
                    LOG.warning("Acceso no autorizado: " + u.nombreCompleto() + " (" + u.rol() + ") -> " + ruta);
                    c.responder(403, c.pagina("Acceso no autorizado",
                            "<p>Su rol no tiene permiso para usar esta función.</p>"));
                    return;
                }
            }
            r.manejador().atender(c);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error atendiendo " + ex.getRequestURI(), e);
            try {
                byte[] b = "Error interno del servidor.".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(500, b.length);
                ex.getResponseBody().write(b);
            } catch (Exception ignorada) { /* la respuesta ya había comenzado */ }
        } finally {
            ex.close();
        }
    }

    private void estatico(HttpExchange ex, String ruta) throws IOException {
        if (ruta.contains("..")) {
            ex.sendResponseHeaders(400, -1);
            return;
        }
        try (InputStream in = Servidor.class.getResourceAsStream(ruta)) {
            if (in == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = in.readAllBytes();
            String tipo = ruta.endsWith(".css") ? "text/css; charset=utf-8"
                    : ruta.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : ruta.endsWith(".svg") ? "image/svg+xml"
                    : ruta.endsWith(".png") ? "image/png" : "application/octet-stream";
            ex.getResponseHeaders().set("Content-Type", tipo);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
        }
    }
}

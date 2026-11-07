package mx.mundolap.compartido.bd;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Única clase que sabe cómo conectarse a MySQL. Los repositorios JDBC piden una conexión con
 * {@link #obtener()} y la cierran con try-with-resources.
 *
 * Configuración (de mayor a menor prioridad):
 *   1. Propiedades de la JVM: -Dmundolap.bd.host, -Dmundolap.bd.clave, etc.
 *   2. Variables de entorno: MUNDOLAP_BD_HOST, MUNDOLAP_BD_PUERTO, MUNDOLAP_BD_NOMBRE,
 *      MUNDOLAP_BD_USUARIO y MUNDOLAP_BD_CLAVE.
 *   3. Archivo src/main/resources/bd.local.properties (opcional, ignorado por Git: aquí va la contraseña).
 *   4. Archivo src/main/resources/bd.properties.
 *   5. Valores por defecto: localhost:3306, base "Mundo_Lap", usuario "root".
 */
public final class ConexionBD {
    private static final String ARCHIVO = "/bd.properties";
    private static final String ARCHIVO_LOCAL = "/bd.local.properties";
    private static final int MILISEGUNDOS_ESPERA = 5000;
    /** Nombres de base permitidos: evita inyectar SQL al crearla. */
    private static final Pattern IDENTIFICADOR = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Set<String> LOCALES = Set.of("localhost", "127.0.0.1", "::1");
    private static final Properties CONFIG = cargarArchivo();

    private ConexionBD() { }

    /** Abre una conexión nueva a la base del sistema. Quien la pide es responsable de cerrarla. */
    public static Connection obtener() throws SQLException {
        return conectar(identificador(nombreBase()));
    }

    public static String nombreBase() { return valor("nombre", "Mundo_Lap"); }

    /** URL JDBC que se está usando (sin usuario ni contraseña, segura para mostrar en pantalla). */
    public static String url() { return urlBase() + nombreBase(); }

    /**
     * Prepara la base de datos para usarse: crea la base si no existe y verifica la conexión.
     * Nunca lanza excepciones; devuelve false (e imprime qué revisar) si no se pudo.
     */
    public static boolean preparar() {
        try {
            String base = identificador(nombreBase());
            try (Connection cn = conectar(""); Statement st = cn.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS `" + base + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            }
            try (Connection cn = obtener()) {
                if (!cn.isValid(MILISEGUNDOS_ESPERA / 1000)) return false;
                System.out.println("[BD] Conectado a " + url() + " como " + valor("usuario", "root"));
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[BD] No se pudo conectar a " + url());
            System.err.println("[BD] " + describirError(e));
            return false;
        }
    }

    /** Traduce los errores más comunes de MySQL a un mensaje que diga qué revisar. */
    public static String describirError(SQLException e) {
        String mensaje = e.getMessage() == null ? "" : e.getMessage();
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        if (mensaje.contains("No suitable driver")) {
            return "No se encontró el driver de MySQL. Recarga el proyecto Maven (clic derecho > Reload POM).";
        }
        switch (e.getErrorCode()) {
            case 1045:
                return "Usuario o contraseña incorrectos. Escribe tu contraseña de MySQL en "
                        + "src/main/resources/bd.local.properties con la línea  bd.clave=tu_contraseña";
            case 1044:
            case 1142:
                return "El usuario no tiene permisos suficientes sobre la base '" + nombreBase() + "'. Usa el usuario root.";
            case 1049:
                return "La base de datos '" + nombreBase() + "' no existe y no se pudo crear.";
            default:
                break;
        }
        if (estado.startsWith("08")) {
            return "No hay respuesta del servidor MySQL. Verifica que el servicio MySQL esté iniciado y que "
                    + "bd.host y bd.puerto sean correctos. Detalle: " + primeraLinea(mensaje);
        }
        return "Error de base de datos (" + (estado.isEmpty() ? "sin código" : estado) + "): " + primeraLinea(mensaje);
    }

    // ---------- internos ----------

    private static Connection conectar(String base) throws SQLException {
        DriverManager.setLoginTimeout(MILISEGUNDOS_ESPERA / 1000);
        Properties p = new Properties();
        p.setProperty("user", valor("usuario", "root"));
        p.setProperty("password", valor("clave", ""));
        p.setProperty("connectTimeout", String.valueOf(MILISEGUNDOS_ESPERA));
        p.setProperty("characterEncoding", "UTF-8");
        // UPDATE devuelve las filas encontradas (no solo las modificadas); Guardado depende de esto.
        p.setProperty("useAffectedRows", "false");
        // MySQL 8 sin SSL necesita pedir la llave pública del servidor; solo se permite en esta computadora.
        if (LOCALES.contains(valor("host", "localhost"))) p.setProperty("allowPublicKeyRetrieval", "true");
        return DriverManager.getConnection(urlBase() + base, p);
    }

    private static String urlBase() {
        return "jdbc:mysql://" + valor("host", "localhost") + ":" + valor("puerto", "3306") + "/";
    }

    static String identificador(String nombre) throws SQLException {
        if (nombre == null || !IDENTIFICADOR.matcher(nombre).matches()) {
            throw new SQLException("Nombre de base no válido: '" + nombre
                    + "'. Use solo letras, números y guion bajo.", "42000");
        }
        return nombre;
    }

    private static String primeraLinea(String texto) {
        int i = texto.indexOf('\n');
        return i < 0 ? texto : texto.substring(0, i);
    }

    private static String valor(String clave, String porDefecto) {
        String jvm = System.getProperty("mundolap.bd." + clave);
        if (jvm != null && !jvm.isBlank()) return jvm.trim();
        String entorno = System.getenv("MUNDOLAP_BD_" + clave.toUpperCase());
        if (entorno != null && !entorno.isBlank()) return entorno.trim();
        String archivo = CONFIG.getProperty("bd." + clave);
        if (archivo != null && !archivo.isBlank()) return archivo.trim();
        return porDefecto;
    }

    /** Lee bd.properties y, encima, bd.local.properties (copia personal ignorada por Git). */
    private static Properties cargarArchivo() {
        Properties p = new Properties();
        leer(p, ARCHIVO);
        leer(p, ARCHIVO_LOCAL);
        return p;
    }

    private static void leer(Properties destino, String recurso) {
        try (InputStream in = ConexionBD.class.getResourceAsStream(recurso)) {
            if (in != null) destino.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[BD] No se pudo leer " + recurso + ": " + e.getMessage());
        }
    }
}

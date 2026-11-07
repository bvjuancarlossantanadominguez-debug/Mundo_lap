package mx.mundolap.compartido.bd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Crea (si no existen) las tablas del sistema ejecutando resources/bd/esquema.sql sobre la base
 * configurada. Las líneas CREATE DATABASE y USE del script se omiten aquí porque la base ya la
 * eligió {@link ConexionBD}; están en el script para poder ejecutarlo también a mano en Workbench.
 */
public final class EsquemaBD {
    private static final String SCRIPT = "/bd/esquema.sql";

    private EsquemaBD() { }

    public static void crear() throws SQLException {
        try (Connection cn = ConexionBD.obtener(); Statement st = cn.createStatement()) {
            for (String sentencia : sentencias()) st.execute(sentencia);
        }
    }

    /** Sentencias del script sin comentarios ni CREATE DATABASE / USE, separadas por punto y coma. */
    static List<String> sentencias() {
        StringBuilder sinComentarios = new StringBuilder();
        for (String linea : leerScript().split("\\R")) {
            if (!linea.trim().startsWith("--")) sinComentarios.append(linea).append('\n');
        }
        List<String> lista = new ArrayList<>();
        for (String s : sinComentarios.toString().split(";")) {
            String sentencia = s.trim();
            String inicio = sentencia.toUpperCase(Locale.ROOT);
            if (sentencia.isEmpty() || inicio.startsWith("CREATE DATABASE") || inicio.startsWith("USE ")) continue;
            lista.add(sentencia);
        }
        return lista;
    }

    private static String leerScript() {
        try (InputStream in = EsquemaBD.class.getResourceAsStream(SCRIPT)) {
            if (in == null) throw new IllegalStateException("No se encontró el script " + SCRIPT);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + SCRIPT, e);
        }
    }
}

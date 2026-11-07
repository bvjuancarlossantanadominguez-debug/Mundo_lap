package mx.mundolap.compartido.bd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Herramienta para verificar la base de datos sin levantar el sistema web.
 * En NetBeans: clic derecho sobre este archivo > Run File (Shift+F6).
 * Crea la base y las tablas si faltan, y muestra servidor, base, usuario y las tablas con sus registros.
 */
public final class ProbarConexion {
    private ProbarConexion() { }

    public static void main(String[] args) {
        System.out.println("Probando conexión a " + ConexionBD.url() + " ...");
        if (!ConexionBD.preparar()) System.exit(1);
        try {
            EsquemaBD.crear();
            try (Connection cn = ConexionBD.obtener(); Statement st = cn.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT VERSION(), DATABASE(), CURRENT_USER()")) {
                    rs.next();
                    System.out.println("CONEXIÓN EXITOSA");
                    System.out.println("  Servidor : MySQL " + rs.getString(1));
                    System.out.println("  Base     : " + rs.getString(2));
                    System.out.println("  Usuario  : " + rs.getString(3));
                }
                List<String> tablas = new ArrayList<>();
                try (PreparedStatement ps = cn.prepareStatement("SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) tablas.add(rs.getString(1));
                }
                System.out.println("Tablas (" + tablas.size() + "):");
                for (String tabla : tablas) {
                    try (ResultSet c = st.executeQuery("SELECT COUNT(*) FROM `" + tabla.replace("`", "") + "`")) {
                        c.next();
                        System.out.println("  - " + tabla + " (" + c.getLong(1) + " registros)");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR: " + ConexionBD.describirError(e));
            System.exit(1);
        }
    }
}

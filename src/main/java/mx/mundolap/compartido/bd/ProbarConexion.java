package mx.mundolap.compartido.bd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Herramienta para verificar la conexión sin levantar el sistema web.
 * En NetBeans: clic derecho sobre este archivo > Run File (Shift+F6).
 * En VS Code: presionar "Run" sobre el método main. Por terminal:mvn compile exec:java -Dexec.mainClass=mx.mundolap.compartido.bd.ProbarConexion.
 * Muestra la versión de PostgreSQL, la base y el usuario, y las tablas que ya existen en el esquema public.
 */
public final class ProbarConexion {
    private ProbarConexion() { }

    public static void main(String[] args) {
        System.out.println("Probando conexión a " + ConexionBD.url() + " ...");
        try (Connection cn = ConexionBD.obtener(); Statement st = cn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT version(), current_database(), current_user")) {
                rs.next();
                System.out.println("CONEXIÓN EXITOSA");
                System.out.println("  Servidor : " + rs.getString(1));
                System.out.println("  Base     : " + rs.getString(2));
                System.out.println("  Usuario  : " + rs.getString(3));
            }
            System.out.println("Tablas en el esquema public:");
            try (ResultSet rs = st.executeQuery(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name")) {
                boolean hay = false;
                while (rs.next()) {
                    hay = true;
                    System.out.println("  - " + rs.getString(1));
                }
                if (!hay) System.out.println("  (ninguna todavía)");
            }
        } catch (SQLException e) {
            System.err.println("NO SE PUDO CONECTAR");
            System.err.println(ConexionBD.describirError(e));
            System.exit(1);
        }
    }
}

package mx.mundolap.compartido.bd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Obtiene el siguiente número de la tabla secuencia. Usa UPDATE ... LAST_INSERT_ID(valor + 1), que en
 * MySQL es atómico: dos usuarios a la vez nunca reciben el mismo número.
 */
public final class Secuencias {
    private Secuencias() { }

    public static long siguiente(String nombre) {
        try (Connection cn = ConexionBD.obtener();
             PreparedStatement ps = cn.prepareStatement(
                     "UPDATE secuencia SET valor = LAST_INSERT_ID(valor + 1) WHERE nombre = ?")) {
            ps.setString(1, nombre);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("No existe la secuencia " + nombre);
            try (Statement st = cn.createStatement(); ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new ErrorBD("generar el identificador", e);
        }
    }
}

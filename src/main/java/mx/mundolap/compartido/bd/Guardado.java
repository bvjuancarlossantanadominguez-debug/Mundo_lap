package mx.mundolap.compartido.bd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * "Guardar" = actualizar el registro si ya existe su llave primaria y, si no, insertarlo.
 * No se usa INSERT ... ON DUPLICATE KEY UPDATE porque en MySQL también se dispara con otras llaves
 * únicas (p. ej. un número de serie repetido) y sobrescribiría otro registro en lugar de rechazarlo.
 */
public final class Guardado {
    /** Pone los parámetros de una sentencia preparada. */
    @FunctionalInterface
    public interface Parametros {
        void poner(PreparedStatement ps) throws SQLException;
    }

    private Guardado() { }

    public static void actualizarOInsertar(String operacion, String sqlUpdate, Parametros paramsUpdate,
                                           String sqlInsert, Parametros paramsInsert) {
        try (Connection cn = ConexionBD.obtener()) {
            cn.setAutoCommit(false);
            try {
                int filas;
                try (PreparedStatement ps = cn.prepareStatement(sqlUpdate)) {
                    paramsUpdate.poner(ps);
                    filas = ps.executeUpdate();
                }
                if (filas == 0) {
                    try (PreparedStatement ps = cn.prepareStatement(sqlInsert)) {
                        paramsInsert.poner(ps);
                        ps.executeUpdate();
                    }
                }
                cn.commit();
            } catch (SQLException e) {
                cn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ErrorBD(operacion, e);
        }
    }
}

package mx.mundolap.compartido.bd;

import java.sql.SQLException;

/** Error de acceso a datos. Envuelve la SQLException para que los servicios no dependan de JDBC. */
public final class ErrorBD extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ErrorBD(String operacion, SQLException causa) {
        super("Error de base de datos al " + operacion + ": " + ConexionBD.describirError(causa), causa);
    }
}

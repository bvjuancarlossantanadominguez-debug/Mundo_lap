package mx.mundolap.equipos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mx.mundolap.compartido.bd.ConexionBD;
import mx.mundolap.compartido.bd.ErrorBD;
import mx.mundolap.compartido.bd.Guardado;
import mx.mundolap.compartido.bd.Secuencias;

/**
 * Equipos guardados en la tabla equipo de MySQL. Un número de serie vacío se guarda como NULL, para
 * que la llave única permita varios equipos sin serie y rechace series repetidas.
 */
public final class EquipoRepositorioJdbc implements EquipoRepositorio {
    private static final String COLUMNAS = "id_equipo, id_cliente, tipo_dispositivo, marca, modelo, numero_serie";

    @Override
    public List<Equipo> listarPorCliente(String idCliente) {
        String sql = "SELECT " + COLUMNAS + " FROM equipo WHERE id_cliente = ? ORDER BY id_equipo";
        try (Connection cn = ConexionBD.obtener(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                List<Equipo> lista = new ArrayList<>();
                while (rs.next()) lista.add(leer(rs));
                return lista;
            }
        } catch (SQLException e) {
            throw new ErrorBD("listar los equipos", e);
        }
    }

    @Override
    public Optional<Equipo> buscarPorId(String idEquipo) {
        return buscarUno("SELECT " + COLUMNAS + " FROM equipo WHERE id_equipo = ?", idEquipo);
    }

    /** La comparación de MySQL (utf8mb4_0900_ai_ci) no distingue mayúsculas. */
    @Override
    public Optional<Equipo> buscarPorSerie(String numeroSerie) {
        if (numeroSerie == null || numeroSerie.isBlank()) return Optional.empty();
        return buscarUno("SELECT " + COLUMNAS + " FROM equipo WHERE numero_serie = ? LIMIT 1", numeroSerie);
    }

    @Override
    public void guardar(Equipo e) {
        String serie = e.numeroSerie().isBlank() ? null : e.numeroSerie();
        Guardado.actualizarOInsertar("guardar el equipo",
                "UPDATE equipo SET id_cliente = ?, tipo_dispositivo = ?, marca = ?, modelo = ?, numero_serie = ? "
                        + "WHERE id_equipo = ?",
                ps -> {
                    ps.setString(1, e.idCliente());
                    ps.setString(2, e.tipoDispositivo().name());
                    ps.setString(3, e.marca());
                    ps.setString(4, e.modelo());
                    ps.setString(5, serie);
                    ps.setString(6, e.idEquipo());
                },
                "INSERT INTO equipo (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, e.idEquipo());
                    ps.setString(2, e.idCliente());
                    ps.setString(3, e.tipoDispositivo().name());
                    ps.setString(4, e.marca());
                    ps.setString(5, e.modelo());
                    ps.setString(6, serie);
                });
    }

    @Override
    public String nuevoId() { return String.format("EQ-%03d", Secuencias.siguiente("equipo")); }

    private Optional<Equipo> buscarUno(String sql, String valor) {
        try (Connection cn = ConexionBD.obtener(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorBD("buscar el equipo", e);
        }
    }

    private static Equipo leer(ResultSet rs) throws SQLException {
        String serie = rs.getString(6);
        return new Equipo(rs.getString(1), rs.getString(2), TipoDispositivo.desde(rs.getString(3)),
                rs.getString(4), rs.getString(5), serie == null ? "" : serie);
    }
}

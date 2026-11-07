package mx.mundolap.ordenes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mx.mundolap.compartido.EstadoOrden;
import mx.mundolap.compartido.bd.ConexionBD;
import mx.mundolap.compartido.bd.ErrorBD;
import mx.mundolap.compartido.bd.Guardado;
import mx.mundolap.compartido.bd.Secuencias;

/** Órdenes de servicio guardadas en la tabla orden_servicio de MySQL. */
public final class OrdenRepositorioJdbc implements OrdenRepositorio {
    private static final String COLUMNAS = "folio, id_cliente, id_equipo, problema_reportado, diagnostico_tecnico, "
            + "estado_actual, fecha_recepcion, fecha_entrega_estimada, prioridad, costo_estimado";

    @Override
    public List<OrdenServicio> listar() {
        try (Connection cn = ConexionBD.obtener();
             PreparedStatement ps = cn.prepareStatement("SELECT " + COLUMNAS + " FROM orden_servicio ORDER BY folio");
             ResultSet rs = ps.executeQuery()) {
            List<OrdenServicio> lista = new ArrayList<>();
            while (rs.next()) lista.add(leer(rs));
            return lista;
        } catch (SQLException e) {
            throw new ErrorBD("listar las órdenes", e);
        }
    }

    @Override
    public Optional<OrdenServicio> buscarPorFolio(String folio) {
        try (Connection cn = ConexionBD.obtener();
             PreparedStatement ps = cn.prepareStatement("SELECT " + COLUMNAS + " FROM orden_servicio WHERE folio = ?")) {
            ps.setString(1, folio);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorBD("buscar la orden", e);
        }
    }

    @Override
    public void guardar(OrdenServicio o) {
        Guardado.actualizarOInsertar("guardar la orden",
                "UPDATE orden_servicio SET id_cliente = ?, id_equipo = ?, problema_reportado = ?, "
                        + "diagnostico_tecnico = ?, estado_actual = ?, fecha_recepcion = ?, fecha_entrega_estimada = ?, "
                        + "prioridad = ?, costo_estimado = ? WHERE folio = ?",
                ps -> {
                    ps.setString(1, o.idCliente());
                    ps.setString(2, o.idEquipo());
                    ps.setString(3, o.problemaReportado());
                    ps.setString(4, o.diagnosticoTecnico());
                    ps.setString(5, o.estadoActual().name());
                    ps.setObject(6, o.fechaRecepcion());
                    ps.setObject(7, o.fechaEntregaEstimada());
                    ps.setString(8, o.prioridad().name());
                    ps.setBigDecimal(9, o.costoEstimado());
                    ps.setString(10, o.folio());
                },
                "INSERT INTO orden_servicio (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, o.folio());
                    ps.setString(2, o.idCliente());
                    ps.setString(3, o.idEquipo());
                    ps.setString(4, o.problemaReportado());
                    ps.setString(5, o.diagnosticoTecnico());
                    ps.setString(6, o.estadoActual().name());
                    ps.setObject(7, o.fechaRecepcion());
                    ps.setObject(8, o.fechaEntregaEstimada());
                    ps.setString(9, o.prioridad().name());
                    ps.setBigDecimal(10, o.costoEstimado());
                });
    }

    @Override
    public String nuevoFolio() { return String.format("OS-%04d", Secuencias.siguiente("orden_servicio")); }

    private static OrdenServicio leer(ResultSet rs) throws SQLException {
        return new OrdenServicio(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                EstadoOrden.valueOf(rs.getString(6)), rs.getObject(7, LocalDateTime.class),
                rs.getObject(8, LocalDateTime.class), Prioridad.desde(rs.getString(9)), rs.getBigDecimal(10));
    }
}

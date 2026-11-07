package mx.mundolap.clientes;

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

/** Clientes guardados en la tabla cliente de MySQL. */
public final class ClienteRepositorioJdbc implements ClienteRepositorio {
    private static final String COLUMNAS = "id_cliente, nombre_completo, telefono_contacto, correo_electronico, "
            + "tipo_cliente, razon_social, rfc";

    @Override
    public List<Cliente> listar() {
        try (Connection cn = ConexionBD.obtener();
             PreparedStatement ps = cn.prepareStatement("SELECT " + COLUMNAS + " FROM cliente ORDER BY id_cliente");
             ResultSet rs = ps.executeQuery()) {
            List<Cliente> lista = new ArrayList<>();
            while (rs.next()) lista.add(leer(rs));
            return lista;
        } catch (SQLException e) {
            throw new ErrorBD("listar los clientes", e);
        }
    }

    @Override
    public Optional<Cliente> buscarPorId(String idCliente) {
        try (Connection cn = ConexionBD.obtener();
             PreparedStatement ps = cn.prepareStatement("SELECT " + COLUMNAS + " FROM cliente WHERE id_cliente = ?")) {
            ps.setString(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(leer(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorBD("buscar el cliente", e);
        }
    }

    /** RF-10 (alta) y RF-12 (actualización): actualiza si ya existe el id; si no, inserta. */
    @Override
    public void guardar(Cliente c) {
        Guardado.actualizarOInsertar("guardar el cliente",
                "UPDATE cliente SET nombre_completo = ?, telefono_contacto = ?, correo_electronico = ?, "
                        + "tipo_cliente = ?, razon_social = ?, rfc = ? WHERE id_cliente = ?",
                ps -> {
                    ps.setString(1, c.nombreCompleto());
                    ps.setString(2, c.telefonoContacto());
                    ps.setString(3, c.correoElectronico());
                    ps.setString(4, c.tipoCliente().name());
                    ps.setString(5, c.razonSocial());
                    ps.setString(6, c.rfc());
                    ps.setString(7, c.idCliente());
                },
                "INSERT INTO cliente (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, c.idCliente());
                    ps.setString(2, c.nombreCompleto());
                    ps.setString(3, c.telefonoContacto());
                    ps.setString(4, c.correoElectronico());
                    ps.setString(5, c.tipoCliente().name());
                    ps.setString(6, c.razonSocial());
                    ps.setString(7, c.rfc());
                });
    }

    @Override
    public String nuevoId() { return String.format("CLI-%03d", Secuencias.siguiente("cliente")); }

    private static Cliente leer(ResultSet rs) throws SQLException {
        return new Cliente(rs.getString(1), rs.getString(2), rs.getString(3).trim(), rs.getString(4),
                TipoCliente.desde(rs.getString(5)), rs.getString(6), rs.getString(7));
    }
}

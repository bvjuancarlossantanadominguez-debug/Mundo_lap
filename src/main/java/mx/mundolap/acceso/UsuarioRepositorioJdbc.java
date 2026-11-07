package mx.mundolap.acceso;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import mx.mundolap.compartido.Rol;
import mx.mundolap.compartido.bd.ConexionBD;
import mx.mundolap.compartido.bd.ErrorBD;
import mx.mundolap.compartido.bd.Guardado;

/** Usuarios guardados en la tabla usuario de MySQL. */
public final class UsuarioRepositorioJdbc implements UsuarioRepositorio {

    @Override
    public Optional<Usuario> buscarPorNombreUsuario(String nombreUsuario) {
        String sql = "SELECT id_usuario, nombre_completo, nombre_usuario, rol_asignado, credenciales_acceso "
                + "FROM usuario WHERE nombre_usuario = ?";
        try (Connection cn = ConexionBD.obtener(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, nombreUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Usuario u = new Usuario(rs.getString(1), rs.getString(2), rs.getString(3),
                        Rol.valueOf(rs.getString(4)), rs.getString(5));
                // La comparación de MySQL no distingue mayúsculas; el inicio de sesión sí debe distinguirlas.
                return u.nombreUsuario().equals(nombreUsuario) ? Optional.of(u) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorBD("buscar el usuario", e);
        }
    }

    @Override
    public void guardar(Usuario u) {
        Guardado.actualizarOInsertar("guardar el usuario",
                "UPDATE usuario SET nombre_completo = ?, nombre_usuario = ?, rol_asignado = ?, "
                        + "credenciales_acceso = ? WHERE id_usuario = ?",
                ps -> {
                    ps.setString(1, u.nombreCompleto());
                    ps.setString(2, u.nombreUsuario());
                    ps.setString(3, u.rolAsignado().name());
                    ps.setString(4, u.credencialesAcceso());
                    ps.setString(5, u.idUsuario());
                },
                "INSERT INTO usuario (id_usuario, nombre_completo, nombre_usuario, rol_asignado, credenciales_acceso) "
                        + "VALUES (?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, u.idUsuario());
                    ps.setString(2, u.nombreCompleto());
                    ps.setString(3, u.nombreUsuario());
                    ps.setString(4, u.rolAsignado().name());
                    ps.setString(5, u.credencialesAcceso());
                });
    }

    @Override
    public boolean vacio() {
        try (Connection cn = ConexionBD.obtener();
             PreparedStatement ps = cn.prepareStatement("SELECT NOT EXISTS (SELECT 1 FROM usuario)");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBoolean(1);
        } catch (SQLException e) {
            throw new ErrorBD("consultar los usuarios", e);
        }
    }
}

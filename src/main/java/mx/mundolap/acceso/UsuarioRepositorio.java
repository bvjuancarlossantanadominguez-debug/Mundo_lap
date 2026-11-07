package mx.mundolap.acceso;

import java.util.Optional;

/** Acceso a datos de usuarios. Implementaciones: JDBC (MySQL) y en memoria (sin BD). */
public interface UsuarioRepositorio {
    Optional<Usuario> buscarPorNombreUsuario(String nombreUsuario);

    void guardar(Usuario usuario);

    /** true si todavía no hay ningún usuario registrado (para cargar los datos iniciales una sola vez). */
    boolean vacio();
}

package mx.mundolap.acceso;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class UsuarioRepositorioMemoria implements UsuarioRepositorio {
    private final Map<String, Usuario> datos = new ConcurrentHashMap<>();

    @Override
    public Optional<Usuario> buscarPorNombreUsuario(String nombreUsuario) {
        return Optional.ofNullable(datos.get(nombreUsuario));
    }

    @Override
    public void guardar(Usuario usuario) { datos.put(usuario.nombreUsuario(), usuario); }

    @Override
    public boolean vacio() { return datos.isEmpty(); }
}

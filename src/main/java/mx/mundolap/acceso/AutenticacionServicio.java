package mx.mundolap.acceso;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Valida credenciales (RF-02, RF-04).
 * Las contraseñas se guardan con PBKDF2-HMAC-SHA256 y una sal aleatoria por usuario (incluido en el JDK,
 * sin librerías externas). Formato de credencialesAcceso: pbkdf2-sha256$iteraciones$sal$hash (Base64).
 */
public final class AutenticacionServicio {
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIJO = "pbkdf2-sha256";
    private static final int ITERACIONES = 120_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom AZAR = new SecureRandom();
    /** Se compara contra esta credencial cuando el usuario no existe, para que el tiempo de respuesta no lo delate. */
    private static final String CREDENCIAL_SENUELO = generarCredencial("señuelo-sin-uso");

    private final UsuarioRepositorio repositorio;
    private final IntentosAcceso intentos;

    public AutenticacionServicio(UsuarioRepositorio repositorio) { this(repositorio, new IntentosAcceso()); }

    public AutenticacionServicio(UsuarioRepositorio repositorio, IntentosAcceso intentos) {
        this.repositorio = repositorio;
        this.intentos = intentos;
    }

    /** true si ese nombre de usuario está bloqueado temporalmente por demasiados intentos fallidos. */
    public boolean bloqueado(String nombreUsuario) { return intentos.bloqueado(nombreUsuario); }

    public Optional<Usuario> autenticar(String nombreUsuario, String clave) {
        if (nombreUsuario == null || nombreUsuario.isBlank() || clave == null) return Optional.empty();
        if (intentos.bloqueado(nombreUsuario)) return Optional.empty();
        Optional<Usuario> usuario = repositorio.buscarPorNombreUsuario(nombreUsuario.trim());
        boolean correcta = verificar(clave, usuario.map(Usuario::credencialesAcceso).orElse(CREDENCIAL_SENUELO));
        if (usuario.isPresent() && correcta) {
            intentos.exito(nombreUsuario);
            return usuario;
        }
        intentos.fallo(nombreUsuario);
        return Optional.empty();
    }

    /** Genera la credencial a guardar en la BD para una contraseña nueva (nunca se guarda la contraseña). */
    public static String generarCredencial(String clave) {
        byte[] sal = new byte[BYTES_SAL];
        AZAR.nextBytes(sal);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIJO + "$" + ITERACIONES + "$" + b64.encodeToString(sal) + "$"
                + b64.encodeToString(derivar(clave, sal, ITERACIONES));
    }

    /** Compara una contraseña contra una credencial guardada. Devuelve false si la credencial está mal formada. */
    public static boolean verificar(String clave, String credencial) {
        if (clave == null || credencial == null) return false;
        String[] partes = credencial.split("\\$");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) return false;
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(esperado, derivar(clave, sal, iteraciones));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String clave, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(clave.toCharArray(), sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("El JDK no soporta " + ALGORITMO, e);
        } finally {
            spec.clearPassword();
        }
    }
}

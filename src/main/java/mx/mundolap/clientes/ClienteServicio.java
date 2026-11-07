package mx.mundolap.clientes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import mx.mundolap.compartido.RelacionOrdenes;

/** Reglas de negocio de clientes (RF-08, RF-10, RF-12, RF-13, RF-15). No sabe nada de HTTP ni de la BD. */
public final class ClienteServicio {
    private static final Pattern TELEFONO = Pattern.compile("\\d{10}");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern RFC = Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$");

    private final ClienteRepositorio repositorio;
    private RelacionOrdenes ordenes = RelacionOrdenes.VACIA;

    public ClienteServicio(ClienteRepositorio repositorio) { this.repositorio = repositorio; }

    public void usarRelacionOrdenes(RelacionOrdenes ordenes) { this.ordenes = ordenes; }

    /** RF-08: busca por nombre, teléfono o folio asociado. Sin texto devuelve todos. */
    public List<Cliente> buscar(String texto) {
        String t = texto == null ? "" : texto.trim();
        if (t.isEmpty()) return repositorio.listar();
        String minus = t.toLowerCase(Locale.ROOT);
        List<Cliente> resultado = new ArrayList<>();
        for (Cliente c : repositorio.listar()) {
            if (c.nombreCompleto().toLowerCase(Locale.ROOT).contains(minus) || c.telefonoContacto().contains(t)) {
                resultado.add(c);
            }
        }
        ordenes.clienteDeFolio(t.toUpperCase(Locale.ROOT)).flatMap(repositorio::buscarPorId)
                .filter(c -> !resultado.contains(c)).ifPresent(resultado::add);
        return resultado;
    }

    public Optional<Cliente> buscarPorId(String id) { return repositorio.buscarPorId(id); }

    public int ordenesActivas(String idCliente) { return ordenes.activasDeCliente(idCliente); }

    /** Limpia espacios y deja el RFC en mayúsculas; los datos fiscales solo aplican a corporativos. */
    public Cliente normalizar(Cliente c) {
        boolean corp = c.tipoCliente() == TipoCliente.CORPORATIVO;
        return new Cliente(c.idCliente().trim(), c.nombreCompleto().trim(),
                c.telefonoContacto().replaceAll("[\\s-]", ""), c.correoElectronico().trim(),
                c.tipoCliente(), corp ? c.razonSocial().trim() : "",
                corp ? c.rfc().trim().toUpperCase(Locale.ROOT) : "");
    }

    public List<String> validar(Cliente c) {
        List<String> errores = new ArrayList<>();
        // Un ID solo llega del formulario al editar un cliente existente; los nuevos los asigna el sistema.
        if (!c.idCliente().isEmpty() && repositorio.buscarPorId(c.idCliente()).isEmpty()) {
            errores.add("El cliente que intenta modificar no existe.");
        }
        if (c.nombreCompleto().isBlank()) errores.add("El nombre completo es obligatorio.");
        if (!TELEFONO.matcher(c.telefonoContacto()).matches()) errores.add("El teléfono debe tener 10 dígitos.");
        if (!c.correoElectronico().isBlank() && !CORREO.matcher(c.correoElectronico()).matches()) {
            errores.add("El correo electrónico no tiene un formato válido.");
        }
        if (!c.rfc().isBlank() && !RFC.matcher(c.rfc()).matches()) errores.add("El RFC no tiene un formato válido.");
        return errores;
    }

    /** RF-13: otros clientes con el mismo teléfono o correo. */
    public List<Cliente> duplicados(Cliente c) {
        List<Cliente> repetidos = new ArrayList<>();
        for (Cliente otro : repositorio.listar()) {
            if (otro.idCliente().equals(c.idCliente())) continue;
            boolean mismoTelefono = otro.telefonoContacto().equals(c.telefonoContacto());
            boolean mismoCorreo = !c.correoElectronico().isBlank()
                    && otro.correoElectronico().equalsIgnoreCase(c.correoElectronico());
            if (mismoTelefono || mismoCorreo) repetidos.add(otro);
        }
        return repetidos;
    }

    /** RF-10 (alta) y RF-12 (actualización de contacto). Los datos deben venir normalizados y validados. */
    public Cliente guardar(Cliente c) {
        if (!c.idCliente().isEmpty() && repositorio.buscarPorId(c.idCliente()).isEmpty()) {
            throw new IllegalArgumentException("No existe el cliente " + c.idCliente());
        }
        Cliente aGuardar = c.idCliente().isEmpty() ? c.conId(repositorio.nuevoId()) : c;
        repositorio.guardar(aGuardar);
        return aGuardar;
    }
}

# Mundo Lap – Sistema de punto de venta y servicios de reparación (Equipo Verde)

Proyecto semestral de Ingeniería de Software · Instituto Tecnológico Superior de Xalapa · Grupo 5B.

Java puro (**JDK 17 o superior**). Sin frameworks: el servidor web es el que trae el JDK. Única dependencia
de ejecución: el driver JDBC de PostgreSQL. JUnit 5 se usa solo para las pruebas.

## Abrir y ejecutar en NetBeans
1. File > Open Project > seleccionar la carpeta `mundolap` (NetBeans la reconoce como proyecto Maven).
2. Clic derecho en el proyecto > Run (o F6). La clase principal es `mx.mundolap.App`.
3. Abrir en el navegador: http://localhost:8080
   (Si el puerto está ocupado, pasar otro puerto como argumento, p. ej. `8090`).

Usuarios de ejemplo: `recepcion / recepcion123`, `tecnico / tecnico123`, `gerente / gerente123`.

Por seguridad el servidor solo acepta conexiones desde la misma computadora. Para abrirlo desde otro
dispositivo de la red (por ejemplo, un celular durante una demostración) agregar la opción de VM
`-Dmundolap.host=0.0.0.0`.

## Ejecutar las pruebas
- NetBeans: clic derecho en el proyecto > Test (Alt+F6).
- Terminal: `mvn test`

Las pruebas están en `src/test/java` y cubren las reglas de negocio de clientes, equipos, órdenes,
autenticación y sesiones, además del escape de HTML.

## Estado actual de cada módulo
| Módulo | Pantallas | Estado |
|---|---|---|
| Acceso | P-01 Inicio de sesión, P-05 Panel | Funcional (datos en memoria). Las alertas del panel son texto fijo. |
| Clientes | P-06 Clientes | Funcional (datos en memoria). |
| Equipos | P-07 Registrar equipo | Funcional (datos en memoria). |
| Órdenes | P-08 Nueva orden con folio | Funcional (datos en memoria). |
| Órdenes | P-10 Entrega | Solo la vista. |
| Pagos | P-09 Pagos | Solo la vista. |
| Técnico | P-11 Mis órdenes, P-12 Detalle | Solo la vista. |
| Refacciones | P-13 Solicitar refacción | Solo la vista. |
| Usuarios | P-14 Administración de usuarios | Solo la vista. |
| Caja | P-15 Cierre de caja | Solo la vista. |
| Reportes | P-16 Reportes, P-17 Metas | Solo la vista. |
| Portal del cliente | P-02 a P-04 | No existe. |

"Solo la vista" significa que la pantalla se muestra pero sus botones están deshabilitados y no guarda nada.

## Cómo está organizado (para poder crecer sin reprogramar)
```
mx.mundolap
├── App.java            Arma el sistema: crea piezas, las conecta y registra los módulos.
├── compartido/         Lo común: roles, estados, menú, plantillas, utilidades.
│   ├── bd/             Conexión a PostgreSQL (ConexionBD) y herramienta ProbarConexion.
│   └── web/            Servidor, rutas (Router), sesiones. Única parte que "sabe" de HTTP.
├── acceso/             P-01 Inicio de sesión, P-05 Panel
├── clientes/           P-06 Clientes
├── equipos/            P-07 Registrar equipo
├── ordenes/            P-08 Nueva orden (con folio) y P-10 Entrega (solo vista)
├── pagos/              P-09 Pagos (solo vista)
├── tecnico/            P-11 Mis órdenes y P-12 Detalle de orden (solo vista)
├── refacciones/        P-13 Solicitar refacción (solo vista)
├── usuarios/           P-14 Administración de usuarios (solo vista)
├── caja/               P-15 Cierre de caja (solo vista)
├── reportes/           P-16 Reportes y P-17 Metas (solo vista)
└── demo/               Datos de ejemplo (se borra al conectar la BD)
src/main/resources/vistas/   Las pantallas (HTML) de cada módulo
src/main/resources/static/   CSS y JS
src/test/java/               Pruebas automatizadas (JUnit 5)
```
Cada módulo separa: **Controlador** (recibe la petición) → **Servicio** (reglas de negocio) → **Repositorio** (datos).
El servicio no usa HTTP ni SQL, por eso se puede reutilizar tal cual.

## Base de datos (PostgreSQL)
**Estado actual:** al arrancar, el sistema solo *verifica* que puede conectarse a PostgreSQL y muestra el
resultado en la consola. Todos los módulos siguen guardando sus datos **en memoria**: ningún dato llega a la
base todavía y todo se pierde al detener el programa.

Configurar la conexión:
1. Los datos generales están en `src/main/resources/bd.properties` (base `Mundo_Lap`, usuario `postgres`).
2. **No escribir la contraseña en ese archivo**, porque se sube a GitHub. Crear junto a él un archivo
   `bd.local.properties` con la línea `bd.clave=tu_contraseña`. Git lo ignora y tiene prioridad.
   (También se pueden usar variables de entorno `MUNDOLAP_BD_*`.)
3. Para probar solo la conexión: clic derecho en `compartido/bd/ProbarConexion.java` > Run File.

Para conectar los módulos de verdad: cada `XRepositorio` es una interfaz y hoy tiene una versión
`XRepositorioMemoria`. Se crea `XRepositorioJdbc implements XRepositorio` usando `ConexionBD.obtener()` y
consultas con `PreparedStatement`, y se cambia **una línea** en `App.java`. Controladores, servicios y vistas
no cambian. Depende del modelo relacional aprobado (sección 3.3 del documento).

## Seguridad implementada
- Contraseñas guardadas con PBKDF2-HMAC-SHA256 y sal aleatoria por usuario (nunca en texto plano).
- Mensaje genérico ante usuario o contraseña incorrectos (RF-04).
- Tras 5 intentos fallidos seguidos, ese nombre de usuario queda bloqueado 5 minutos (medida técnica).
- Sesiones con identificador aleatorio en cookie `HttpOnly` y `SameSite=Lax`; vencen tras 2 horas sin actividad.
- Token CSRF por sesión: todo formulario POST lo lleva y el servidor rechaza los que no lo traen.
- Control de acceso por rol en cada ruta (RF-01, RF-05) y registro de intentos no autorizados.
- Escape de todo dato del usuario al generar HTML (evita XSS).
- El servidor solo escucha en `localhost` salvo que se indique lo contrario.

## Pendientes conocidos
- **Persistencia:** los datos y las sesiones viven en memoria (ver "Base de datos").
- **Datos de ejemplo:** el paquete `demo` carga usuarios, clientes, equipos y órdenes de prueba; se elimina al conectar la BD.
- **Costo estimado:** `CostoDiagnosticoEjemplo` usa tarifas de ejemplo (Celular y Tablet $250, Laptop $350,
  PC de escritorio y Otro $300). El PRY03 no define cómo se calcula; falta el tarifario real del taller.
- **Lógica de P-09 a P-17:** depende del modelo relacional (entidades Pago, Refacción, Proveedor, solicitudes,
  metas, desglose de costo, técnico asignado, entre otras).
- **Panel (P-05):** las alertas y el aviso de entrega son texto fijo; los contadores muestran 4 de los 6 estados
  (falta "Pendiente"), por confirmar contra el PRY03.
- **Exportar a PDF (P-15 y P-16):** pendiente de decidir cómo hacerlo.

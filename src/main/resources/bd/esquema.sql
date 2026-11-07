-- =====================================================================================
-- Mundo Lap - Script de creación de la base de datos (MySQL 8.0.16 o superior)
--
-- El sistema lo ejecuta automáticamente al arrancar. Es idempotente: se puede ejecutar varias
-- veces sin perder datos (CREATE ... IF NOT EXISTS, INSERT IGNORE).
-- Para ejecutarlo a mano en MySQL Workbench: abrir este archivo y ejecutar todo (rayo).
--
-- Tablas clave (8): usuario, cliente, equipo, orden_servicio, pago, proveedor, refaccion,
--                   solicitud_refaccion.
-- Tabla auxiliar (1): secuencia (genera los identificadores con formato CLI-001, OS-0001...).
-- =====================================================================================

CREATE DATABASE IF NOT EXISTS Mundo_Lap CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE Mundo_Lap;

-- -------------------------------------------------------------------------------------
-- secuencia (auxiliar): último número usado para cada tipo de identificador.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS secuencia (
    nombre  VARCHAR(30)     NOT NULL,
    valor   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT pk_secuencia PRIMARY KEY (nombre)
) ENGINE = InnoDB;

INSERT IGNORE INTO secuencia (nombre, valor) VALUES
    ('usuario', 0), ('cliente', 0), ('equipo', 0), ('orden_servicio', 0);

-- -------------------------------------------------------------------------------------
-- usuario: personal del taller que inicia sesión (P-01). Rol según el control de acceso.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuario (
    id_usuario          VARCHAR(10)  NOT NULL,
    nombre_completo     VARCHAR(120) NOT NULL,
    nombre_usuario      VARCHAR(40)  NOT NULL,
    rol_asignado        VARCHAR(20)  NOT NULL,
    credenciales_acceso VARCHAR(200) NOT NULL COMMENT 'Hash PBKDF2 con sal, nunca la contraseña',
    CONSTRAINT pk_usuario PRIMARY KEY (id_usuario),
    CONSTRAINT uq_usuario_nombre UNIQUE (nombre_usuario),
    CONSTRAINT ck_usuario_rol CHECK (rol_asignado IN ('RECEPCIONISTA', 'TECNICO', 'GERENTE')),
    CONSTRAINT ck_usuario_datos CHECK (CHAR_LENGTH(TRIM(nombre_completo)) > 0 AND CHAR_LENGTH(TRIM(nombre_usuario)) > 0)
) ENGINE = InnoDB;

-- -------------------------------------------------------------------------------------
-- cliente: persona o empresa que trae equipos al taller (P-06).
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cliente (
    id_cliente          VARCHAR(10)  NOT NULL,
    nombre_completo     VARCHAR(120) NOT NULL,
    telefono_contacto   CHAR(10)     NOT NULL,
    correo_electronico  VARCHAR(120) NOT NULL DEFAULT '',
    tipo_cliente        VARCHAR(15)  NOT NULL,
    razon_social        VARCHAR(150) NOT NULL DEFAULT '',
    rfc                 VARCHAR(13)  NOT NULL DEFAULT '',
    CONSTRAINT pk_cliente PRIMARY KEY (id_cliente),
    CONSTRAINT ck_cliente_tipo CHECK (tipo_cliente IN ('PARTICULAR', 'CORPORATIVO')),
    CONSTRAINT ck_cliente_telefono CHECK (telefono_contacto REGEXP '^[0-9]{10}$'),
    CONSTRAINT ck_cliente_nombre CHECK (CHAR_LENGTH(TRIM(nombre_completo)) > 0),
    -- Los datos fiscales solo aplican a clientes corporativos
    CONSTRAINT ck_cliente_fiscal CHECK (tipo_cliente = 'CORPORATIVO' OR (razon_social = '' AND rfc = '')),
    INDEX ix_cliente_telefono (telefono_contacto)
) ENGINE = InnoDB;

-- -------------------------------------------------------------------------------------
-- equipo: dispositivo de un cliente (P-07). Relación cliente 1 — * equipo.
-- numero_serie es NULL cuando no se captura; si se captura, no puede repetirse.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS equipo (
    id_equipo           VARCHAR(10)  NOT NULL,
    id_cliente          VARCHAR(10)  NOT NULL,
    tipo_dispositivo    VARCHAR(20)  NOT NULL,
    marca               VARCHAR(60)  NOT NULL,
    modelo              VARCHAR(60)  NOT NULL,
    numero_serie        VARCHAR(60)  NULL,
    CONSTRAINT pk_equipo PRIMARY KEY (id_equipo),
    CONSTRAINT uq_equipo_numero_serie UNIQUE (numero_serie),
    -- Permite que la orden verifique que el equipo pertenece al mismo cliente
    CONSTRAINT uq_equipo_cliente UNIQUE (id_equipo, id_cliente),
    CONSTRAINT fk_equipo_cliente FOREIGN KEY (id_cliente)
        REFERENCES cliente (id_cliente) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_equipo_tipo CHECK (tipo_dispositivo IN ('CELULAR', 'LAPTOP', 'PC_ESCRITORIO', 'TABLET', 'OTRO')),
    CONSTRAINT ck_equipo_datos CHECK (CHAR_LENGTH(TRIM(marca)) > 0 AND CHAR_LENGTH(TRIM(modelo)) > 0),
    CONSTRAINT ck_equipo_serie CHECK (numero_serie IS NULL OR CHAR_LENGTH(TRIM(numero_serie)) > 0)
) ENGINE = InnoDB;

-- -------------------------------------------------------------------------------------
-- orden_servicio: orden generada al recibir un equipo (P-08), identificada por su folio.
-- Relaciones cliente 1 — * orden_servicio y equipo 1 — * orden_servicio.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orden_servicio (
    folio                   VARCHAR(12)   NOT NULL,
    id_cliente              VARCHAR(10)   NOT NULL,
    id_equipo               VARCHAR(10)   NOT NULL,
    problema_reportado      VARCHAR(1000) NOT NULL,
    diagnostico_tecnico     VARCHAR(2000) NOT NULL DEFAULT '',
    estado_actual           VARCHAR(20)   NOT NULL DEFAULT 'RECIBIDO',
    fecha_recepcion         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_entrega_estimada  DATETIME      NOT NULL,
    prioridad               VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',
    costo_estimado          DECIMAL(10,2) NOT NULL,
    CONSTRAINT pk_orden_servicio PRIMARY KEY (folio),
    CONSTRAINT fk_orden_cliente FOREIGN KEY (id_cliente)
        REFERENCES cliente (id_cliente) ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- El equipo de la orden debe pertenecer al mismo cliente de la orden
    CONSTRAINT fk_orden_equipo_cliente FOREIGN KEY (id_equipo, id_cliente)
        REFERENCES equipo (id_equipo, id_cliente) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_orden_estado CHECK (estado_actual IN
        ('RECIBIDO', 'PENDIENTE', 'EN_REPARACION', 'ESPERANDO_PIEZAS', 'TERMINADO', 'ENTREGADO')),
    CONSTRAINT ck_orden_prioridad CHECK (prioridad IN ('NORMAL', 'ALTA')),
    CONSTRAINT ck_orden_costo CHECK (costo_estimado >= 0),
    CONSTRAINT ck_orden_problema CHECK (CHAR_LENGTH(TRIM(problema_reportado)) > 0),
    INDEX ix_orden_estado (estado_actual)
) ENGINE = InnoDB;

-- -------------------------------------------------------------------------------------
-- pago: anticipo, abono o liquidación de una orden (P-09). Relación orden_servicio 1 — * pago.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pago (
    id_pago         INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    folio           VARCHAR(12)   NOT NULL,
    concepto_cobro  VARCHAR(15)   NOT NULL,
    monto           DECIMAL(10,2) NOT NULL,
    metodo_pago     VARCHAR(15)   NOT NULL,
    fecha_pago      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_pago PRIMARY KEY (id_pago),
    CONSTRAINT fk_pago_orden FOREIGN KEY (folio)
        REFERENCES orden_servicio (folio) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_pago_concepto CHECK (concepto_cobro IN ('ANTICIPO', 'ABONO', 'LIQUIDACION')),
    CONSTRAINT ck_pago_metodo CHECK (metodo_pago IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA')),
    CONSTRAINT ck_pago_monto CHECK (monto > 0)
) ENGINE = InnoDB;

-- -------------------------------------------------------------------------------------
-- proveedor: quien surte refacciones al taller (P-13).
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proveedor (
    id_proveedor    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    nombre          VARCHAR(120)  NOT NULL,
    CONSTRAINT pk_proveedor PRIMARY KEY (id_proveedor),
    CONSTRAINT uq_proveedor_nombre UNIQUE (nombre),
    CONSTRAINT ck_proveedor_nombre CHECK (CHAR_LENGTH(TRIM(nombre)) > 0)
) ENGINE = InnoDB;

-- -------------------------------------------------------------------------------------
-- refaccion: catálogo de piezas con stock (P-13). Relación proveedor 1 — * refaccion.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refaccion (
    id_refaccion    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    nombre_pieza    VARCHAR(120)  NOT NULL,
    stock           INT           NOT NULL DEFAULT 0,
    stock_minimo    INT           NOT NULL DEFAULT 0,
    id_proveedor    INT UNSIGNED  NOT NULL,
    CONSTRAINT pk_refaccion PRIMARY KEY (id_refaccion),
    CONSTRAINT uq_refaccion_nombre UNIQUE (nombre_pieza),
    CONSTRAINT fk_refaccion_proveedor FOREIGN KEY (id_proveedor)
        REFERENCES proveedor (id_proveedor) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_refaccion_stock CHECK (stock >= 0 AND stock_minimo >= 0),
    CONSTRAINT ck_refaccion_nombre CHECK (CHAR_LENGTH(TRIM(nombre_pieza)) > 0)
) ENGINE = InnoDB;

-- -------------------------------------------------------------------------------------
-- solicitud_refaccion: pieza pedida para una orden (P-13). Une orden_servicio, refaccion y proveedor.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solicitud_refaccion (
    id_solicitud            INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    folio                   VARCHAR(12)   NOT NULL,
    id_refaccion            INT UNSIGNED  NOT NULL,
    id_proveedor            INT UNSIGNED  NOT NULL,
    cantidad                INT           NOT NULL,
    tiempo_estimado_dias    INT           NOT NULL,
    estado                  VARCHAR(12)   NOT NULL DEFAULT 'SOLICITADA',
    fecha_solicitud         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_solicitud_refaccion PRIMARY KEY (id_solicitud),
    CONSTRAINT fk_solicitud_orden FOREIGN KEY (folio)
        REFERENCES orden_servicio (folio) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_solicitud_refaccion FOREIGN KEY (id_refaccion)
        REFERENCES refaccion (id_refaccion) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_solicitud_proveedor FOREIGN KEY (id_proveedor)
        REFERENCES proveedor (id_proveedor) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_solicitud_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_solicitud_tiempo CHECK (tiempo_estimado_dias >= 0),
    CONSTRAINT ck_solicitud_estado CHECK (estado IN ('SOLICITADA', 'RECIBIDA'))
) ENGINE = InnoDB;

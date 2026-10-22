-- =====================================================================
-- CAMPUSDESK - Script de Inicialización de Base de Datos PostgreSQL
-- TechNova Solutions
-- Base de datos: sistema_gestion_usuarios
-- =====================================================================

-- Conexión a la base de datos activa (compatible con Docker y consolas SQL de Neon/Supabase/Render)

-- 1. Tabla de usuarios del sistema (RF-01)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- 2. Tabla principal de incidencias/tickets (RF-03, RF-04)
CREATE TABLE IF NOT EXISTS tickets (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    solicitante_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    tecnico_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(30) NOT NULL,
    category VARCHAR(30) NOT NULL,
    reaperturas_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);
CREATE INDEX IF NOT EXISTS idx_tickets_priority ON tickets(priority);
CREATE INDEX IF NOT EXISTS idx_tickets_category ON tickets(category);
CREATE INDEX IF NOT EXISTS idx_tickets_solicitante ON tickets(solicitante_id);
CREATE INDEX IF NOT EXISTS idx_tickets_tecnico ON tickets(tecnico_id);

-- 3. Tabla de comentarios e hilo de incidencias (RF-05 y Ejercicio 8)
CREATE TABLE IF NOT EXISTS ticket_comments (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    es_interno BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_comments_ticket_id ON ticket_comments(ticket_id);
CREATE INDEX IF NOT EXISTS idx_comments_es_interno ON ticket_comments(ticket_id, es_interno);

-- 4. Tabla de historial inmutable de auditoría (RF-06)
CREATE TABLE IF NOT EXISTS ticket_history (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    estado_anterior VARCHAR(30),
    estado_nuevo VARCHAR(30) NOT NULL,
    autor_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    fecha_cambio TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comentario_transicion VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_history_ticket_id ON ticket_history(ticket_id);

-- 5. Tabla de etiquetas y categorización dinámica (Ejercicio 1)
CREATE TABLE IF NOT EXISTS tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);

CREATE TABLE IF NOT EXISTS ticket_tags (
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (ticket_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_ticket_tags_tag_id ON ticket_tags(tag_id);

-- 5b. Ejercicios 2-5 (reapertura, transferencia, worklogs y plantillas rápidas)
-- Idempotente: también cubre bases de datos creadas antes de estos ejercicios.
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS reaperturas_count INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_tickets_reaperturas ON tickets(reaperturas_count);

CREATE TABLE IF NOT EXISTS ticket_worklogs (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    tecnico_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    minutos_dedicados INTEGER NOT NULL CHECK (minutos_dedicados BETWEEN 5 AND 480),
    descripcion_actividad VARCHAR(500) NOT NULL,
    fecha_registro TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_worklogs_ticket_id ON ticket_worklogs(ticket_id);

CREATE TABLE IF NOT EXISTS quick_responses (
    id BIGSERIAL PRIMARY KEY,
    tecnico_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    titulo VARCHAR(100) NOT NULL,
    contenido TEXT NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    CONSTRAINT uk_quick_response_tecnico_titulo UNIQUE (tecnico_id, titulo)
);

CREATE INDEX IF NOT EXISTS idx_quick_responses_tecnico ON quick_responses(tecnico_id);

-- 6. Población inicial (Seed Data)
-- Contraseñas encriptadas con algoritmo BCrypt (cost factor 10):
-- 'SuperAdmin123!' -> $2a$10$bJ7x/ldrua0IHEmIlB9ww.WTFtXLIbDkaCT1TrzWp79lhDEKfTQiK
-- 'Password123!'   -> $2a$10$GCKwsUFT/VsLrdYILcp8YeIaKcc2mQ4aQE/.5Ytw1bMZpakq8qPvG

INSERT INTO users (id, name, email, password, role)
VALUES
    (
        1,
        'Super Administrador CAMPUSDESK',
        'superadmin@campusdesk.com',
        '$2a$10$bJ7x/ldrua0IHEmIlB9ww.WTFtXLIbDkaCT1TrzWp79lhDEKfTQiK',
        'ADMIN'
    ),
    (
        2,
        'Técnico de Soporte Campus',
        'tecnico@campusdesk.com',
        '$2a$10$GCKwsUFT/VsLrdYILcp8YeIaKcc2mQ4aQE/.5Ytw1bMZpakq8qPvG',
        'TECHNICIAN'
    ),
    (
        3,
        'Carlos Gómez',
        'carlos@campusdesk.com',
        '$2a$10$GCKwsUFT/VsLrdYILcp8YeIaKcc2mQ4aQE/.5Ytw1bMZpakq8qPvG',
        'USER'
    )
ON CONFLICT (id) DO NOTHING;

-- Ajustar la secuencia de users
SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));

-- Seed de Tickets de prueba cubriendo todos los estados y categorías
INSERT INTO tickets (id, title, description, solicitante_id, tecnico_id, status, priority, category, created_at, updated_at)
VALUES
    (
        1,
        'Fallo de conectividad en Laboratorio 3',
        'Los equipos del bloque norte no obtienen dirección IP por DHCP tras reinicio del switch.',
        3,
        NULL,
        'ABIERTA',
        'ALTA',
        'REDES',
        NOW() - INTERVAL '3 hours',
        NOW() - INTERVAL '3 hours'
    ),
    (
        2,
        'Impresora departamental sin tóner',
        'La impresora multifuncional de la sala de profesores requiere reemplazo urgente de tóner negro.',
        3,
        2,
        'ASIGNADA',
        'MEDIA',
        'HARDWARE',
        NOW() - INTERVAL '5 hours',
        NOW() - INTERVAL '2 hours'
    ),
    (
        3,
        'Error de licencia en suite de diseño AutoCAD',
        'Aparece mensaje de licencia expirada al abrir el software en las estaciones de arquitectura.',
        3,
        2,
        'EN_PROCESO',
        'CRITICA',
        'SOFTWARE',
        NOW() - INTERVAL '1 day',
        NOW() - INTERVAL '1 hour'
    ),
    (
        4,
        'Creación de cuenta para docente invitado',
        'Se requiere habilitar usuario temporal y credenciales VPN para cátedra de robótica.',
        3,
        2,
        'RESUELTA',
        'BAJA',
        'ACCESOS',
        NOW() - INTERVAL '2 days',
        NOW() - INTERVAL '30 minutes'
    ),
    (
        5,
        'Mantenimiento preventivo videoproyector Auditorio',
        'Revisión semestral de lámpara y limpieza de filtros en el auditorio principal.',
        3,
        2,
        'CERRADA',
        'MEDIA',
        'OTROS',
        NOW() - INTERVAL '3 days',
        NOW() - INTERVAL '1 day'
    )
ON CONFLICT (id) DO NOTHING;

-- Ajustar la secuencia de tickets
SELECT setval('tickets_id_seq', (SELECT COALESCE(MAX(id), 1) FROM tickets));

-- Seed de Comentarios en las incidencias
INSERT INTO ticket_comments (ticket_id, author_id, content, created_at)
VALUES
    (2, 1, 'Ticket asignado al técnico de turno para gestión de suministros.', NOW() - INTERVAL '2 hours'),
    (3, 2, 'Iniciando diagnóstico remoto con el servidor de licencias Autodesk.', NOW() - INTERVAL '1 hour'),
    (4, 2, 'Credenciales generadas y enviadas al correo institucional del docente.', NOW() - INTERVAL '30 minutes'),
    (5, 3, 'Confirmado el correcto funcionamiento del proyector. Procedo a cerrar la incidencia.', NOW() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

-- Seed de Historial de Auditoría
INSERT INTO ticket_history (ticket_id, estado_anterior, estado_nuevo, autor_id, fecha_cambio, comentario_transicion)
VALUES
    (1, NULL, 'ABIERTA', 3, NOW() - INTERVAL '3 hours', 'Creación de incidencia por el solicitante'),
    (2, NULL, 'ABIERTA', 3, NOW() - INTERVAL '5 hours', 'Creación de incidencia por el solicitante'),
    (2, 'ABIERTA', 'ASIGNADA', 1, NOW() - INTERVAL '2 hours', 'Asignación de técnico responsable por el administrador'),
    (3, NULL, 'ABIERTA', 3, NOW() - INTERVAL '1 day', 'Creación de incidencia'),
    (3, 'ABIERTA', 'ASIGNADA', 1, NOW() - INTERVAL '10 hours', 'Asignado a Soporte Técnico'),
    (3, 'ASIGNADA', 'EN_PROCESO', 2, NOW() - INTERVAL '1 hour', 'Técnico inicia labores de diagnóstico'),
    (4, 'EN_PROCESO', 'RESUELTA', 2, NOW() - INTERVAL '30 minutes', 'Técnico marca como resuelta tras configuración'),
    (5, 'RESUELTA', 'CERRADA', 3, NOW() - INTERVAL '1 day', 'Solicitante confirma satisfacción y cierra el ticket')
ON CONFLICT DO NOTHING;

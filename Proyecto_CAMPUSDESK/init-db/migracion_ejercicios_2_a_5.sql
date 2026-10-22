-- MIGRACIÓN EJERCICIOS 2-5. Ejercicios 2-5 (reapertura, transferencia, worklogs y plantillas rápidas)
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


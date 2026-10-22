-- MIGRACIÓN EJERCICIOS 6-10: Soporte para notas internas (Ejercicio 8)
-- Idempotente: seguro para bases de datos existentes sin afectar datos previos.
ALTER TABLE ticket_comments ADD COLUMN IF NOT EXISTS es_interno BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_ticket_comments_es_interno ON ticket_comments(ticket_id, es_interno);

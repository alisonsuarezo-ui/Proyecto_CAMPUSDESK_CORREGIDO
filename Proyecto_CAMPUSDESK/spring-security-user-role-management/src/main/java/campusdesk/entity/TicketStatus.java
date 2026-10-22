package campusdesk.entity;

/**
 * Estados permitidos en el ciclo de vida de una incidencia (RF-04).
 * Flujo: ABIERTA -> ASIGNADA -> EN_PROCESO -> RESUELTA -> CERRADA.
 */
public enum TicketStatus {
    ABIERTA,
    ASIGNADA,
    EN_PROCESO,
    RESUELTA,
    CERRADA
}

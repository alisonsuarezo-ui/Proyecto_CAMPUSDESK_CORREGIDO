package campusdesk.exception;

/**
 * Excepción lanzada cuando una transición en la máquina de estados viola el flujo permitido (HTTP 409).
 * Flujo estricto: ABIERTA -> ASIGNADA -> EN_PROCESO -> RESUELTA -> CERRADA.
 */
public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}

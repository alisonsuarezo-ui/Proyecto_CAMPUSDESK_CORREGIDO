package campusdesk.exception;

/**
 * Excepción lanzada cuando un usuario intenta realizar una acción sobre un ticket sin los permisos
 * o sin ser el solicitante o técnico asignado (HTTP 403).
 */
public class UnauthorizedTicketActionException extends RuntimeException {
    public UnauthorizedTicketActionException(String message) {
        super(message);
    }
}

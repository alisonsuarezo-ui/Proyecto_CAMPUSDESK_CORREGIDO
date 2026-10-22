package campusdesk.exception;

/**
 * Excepción lanzada cuando se intenta modificar o comentar un ticket en estado CERRADA (HTTP 409).
 * Un ticket cerrado es completamente inmutable (RF-04).
 */
public class TicketClosedException extends RuntimeException {
    public TicketClosedException(String message) {
        super(message);
    }
}

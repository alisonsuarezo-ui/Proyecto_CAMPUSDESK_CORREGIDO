package campusdesk.exception;

/**
 * Excepción lanzada cuando una incidencia o ticket no existe en el sistema (HTTP 404).
 */
public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String message) {
        super(message);
    }
}

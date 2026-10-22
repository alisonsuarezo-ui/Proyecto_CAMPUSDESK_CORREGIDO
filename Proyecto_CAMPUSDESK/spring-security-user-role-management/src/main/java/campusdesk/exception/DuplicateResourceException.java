package campusdesk.exception;

/**
 * Excepción lanzada cuando se intenta crear un recurso que viola una regla de unicidad (HTTP 409).
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}

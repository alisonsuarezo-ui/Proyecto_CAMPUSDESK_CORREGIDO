package campusdesk.service;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;

import java.util.List;

/**
 * Contrato de servicio para el hilo de comentarios de incidencias (RF-05).
 */
public interface TicketCommentService {

    CommentResponseDTO addComment(Long ticketId, CreateCommentDTO dto, String userEmail);

    /** Ejercicio 8: Agrega una nota interna visible exclusivamente para ADMIN y técnicos asignados. */
    CommentResponseDTO addInternalComment(Long ticketId, CreateCommentDTO dto, String userEmail);

    List<CommentResponseDTO> getCommentsByTicketId(Long ticketId, String userEmail);
}

package campusdesk.controller;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;
import campusdesk.service.TicketCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Controlador REST para el hilo de comentarios y notas de incidencias (RF-05).
 */
@RestController
@RequestMapping({"/api/tickets/{ticketId}/comments", "/api/ticket/{ticketId}/comments"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Comments", description = "Gestión del hilo de comentarios y notas de incidencias")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {

    private final TicketCommentService ticketCommentService;

    @Operation(summary = "Agregar comentario a una incidencia", description = "Permitido para solicitante, técnico asignado y admin si la incidencia no está CERRADA")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponseDTO> addComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentDTO dto,
            Principal principal
    ) {
        log.info("Agregando comentario a ticket #{} por [{}]", ticketId, principal.getName());
        CommentResponseDTO response = ticketCommentService.addComment(ticketId, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Listar comentarios de una incidencia en orden cronológico")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentResponseDTO>> getComments(
            @PathVariable Long ticketId,
            Principal principal
    ) {
        List<CommentResponseDTO> comments = ticketCommentService.getCommentsByTicketId(ticketId, principal.getName());
        return ResponseEntity.ok(comments);
    }
}

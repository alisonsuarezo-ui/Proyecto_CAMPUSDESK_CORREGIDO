package campusdesk.service;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketComment;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.TicketClosedException;
import campusdesk.exception.TicketNotFoundException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketCommentRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación de la gestión de comentarios para incidencias (RF-05).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCommentServiceImpl implements TicketCommentService {

    private final TicketCommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CommentResponseDTO addComment(Long ticketId, CreateCommentDTO dto, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        // 1. Inmutabilidad de tickets cerrados
        if (ticket.getStatus() == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se pueden agregar comentarios a una incidencia en estado CERRADA");
        }

        // 2. Control de acceso
        validateUserAccess(ticket, user, "comentar en");

        TicketComment comment = TicketComment.builder()
                .ticket(ticket)
                .author(user)
                .content(dto.getContent().trim())
                .esInterno(false)
                .build();

        TicketComment savedComment = commentRepository.save(comment);
        log.info("Comentario #{} agregado al ticket #{} por [{}]", savedComment.getId(), ticketId, userEmail);

        return mapToDTO(savedComment);
    }

    @Override
    @Transactional
    public CommentResponseDTO addInternalComment(Long ticketId, CreateCommentDTO dto, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        // 1. Inmutabilidad de tickets cerrados
        if (ticket.getStatus() == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se pueden agregar comentarios a una incidencia en estado CERRADA");
        }

        // 2. Control de acceso: solo ADMIN o TECHNICIAN asignado pueden crear notas internas
        if (user.getRole() != Role.ADMIN) {
            if (user.getRole() != Role.TECHNICIAN || ticket.getTecnico() == null || !ticket.getTecnico().getId().equals(user.getId())) {
                throw new UnauthorizedTicketActionException("Solo un administrador o el técnico asignado pueden registrar notas internas");
            }
        }

        TicketComment comment = TicketComment.builder()
                .ticket(ticket)
                .author(user)
                .content(dto.getContent().trim())
                .esInterno(true)
                .build();

        TicketComment savedComment = commentRepository.save(comment);
        log.info("Nota interna #{} agregada al ticket #{} por [{}]", savedComment.getId(), ticketId, userEmail);

        return mapToDTO(savedComment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponseDTO> getCommentsByTicketId(Long ticketId, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        validateUserAccess(ticket, user, "consultar los comentarios de");

        // Ejercicio 8: Si es usuario solicitante (USER), se filtran estrictamente las notas internas en el backend
        List<TicketComment> comments;
        if (user.getRole() == Role.USER) {
            comments = commentRepository.findPublicByTicketIdOrderByCreatedAtAsc(ticketId);
        } else {
            comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        }
        return comments.stream().map(this::mapToDTO).toList();
    }

    private void validateUserAccess(Ticket ticket, User user, String actionDescription) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        if (user.getRole() == Role.USER) {
            if (ticket.getSolicitante() == null || !ticket.getSolicitante().getId().equals(user.getId())) {
                throw new UnauthorizedTicketActionException("No tiene permisos para " + actionDescription + " esta incidencia");
            }
            return;
        }

        if (user.getRole() == Role.TECHNICIAN) {
            if (ticket.getTecnico() == null || !ticket.getTecnico().getId().equals(user.getId())) {
                throw new UnauthorizedTicketActionException("Solo el técnico asignado puede " + actionDescription + " esta incidencia");
            }
        }
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con correo: " + email));
    }

    private Ticket findTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Incidencia no encontrada con ID: " + id));
    }

    private CommentResponseDTO mapToDTO(TicketComment comment) {
        User author = comment.getAuthor();
        UserResponseDTO authorDto = author != null ? UserResponseDTO.builder()
                .id(author.getId())
                .name(author.getName())
                .email(author.getEmail())
                .role(author.getRole())
                .build() : null;

        return CommentResponseDTO.builder()
                .id(comment.getId())
                .ticketId(comment.getTicket().getId())
                .content(comment.getContent())
                .author(authorDto)
                .createdAt(comment.getCreatedAt())
                .esInterno(comment.getEsInterno() != null ? comment.getEsInterno() : false)
                .build();
    }
}

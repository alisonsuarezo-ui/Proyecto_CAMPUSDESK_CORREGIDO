package campusdesk.service;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.QuickResponseRequestDTO;
import campusdesk.dto.QuickResponseResponseDTO;
import campusdesk.entity.QuickResponse;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketComment;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.DuplicateResourceException;
import campusdesk.exception.InvalidStateTransitionException;
import campusdesk.exception.TicketNotFoundException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.QuickResponseRepository;
import campusdesk.repository.TicketCommentRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación del catálogo de plantillas de solución rápida (Ejercicio 5).
 * Cada técnico solo puede gestionar y visualizar sus propias plantillas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuickResponseServiceImpl implements QuickResponseService {

    private final QuickResponseRepository quickResponseRepository;
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public QuickResponseResponseDTO create(QuickResponseRequestDTO dto, String userEmail) {
        User tecnico = requireTechnician(userEmail);
        String titulo = dto.getTitulo().trim();

        if (quickResponseRepository.existsByTecnicoIdAndTituloIgnoreCase(tecnico.getId(), titulo)) {
            throw new DuplicateResourceException("Ya existe una plantilla con el título '" + titulo + "' para este técnico");
        }

        QuickResponse plantilla = QuickResponse.builder()
                .titulo(titulo)
                .contenido(dto.getContenido().trim())
                .categoria(dto.getCategoria())
                .tecnico(tecnico)
                .build();

        try {
            QuickResponse saved = quickResponseRepository.saveAndFlush(plantilla);
            log.info("Plantilla #{} creada por técnico [{}]", saved.getId(), userEmail);
            return toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Ya existe una plantilla con el título '" + titulo + "' para este técnico");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuickResponseResponseDTO> listMine(String userEmail) {
        User tecnico = requireTechnician(userEmail);
        return quickResponseRepository.findByTecnicoIdOrderByTituloAsc(tecnico.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public QuickResponseResponseDTO getById(Long id, String userEmail) {
        User tecnico = requireTechnician(userEmail);
        return toDto(findOwnedTemplate(id, tecnico));
    }

    @Override
    @Transactional
    public QuickResponseResponseDTO update(Long id, QuickResponseRequestDTO dto, String userEmail) {
        User tecnico = requireTechnician(userEmail);
        QuickResponse plantilla = findOwnedTemplate(id, tecnico);
        String titulo = dto.getTitulo().trim();

        if (quickResponseRepository.existsByTecnicoIdAndTituloIgnoreCaseAndIdNot(tecnico.getId(), titulo, id)) {
            throw new DuplicateResourceException("Ya existe otra plantilla con el título '" + titulo + "' para este técnico");
        }

        plantilla.setTitulo(titulo);
        plantilla.setContenido(dto.getContenido().trim());
        plantilla.setCategoria(dto.getCategoria());

        try {
            return toDto(quickResponseRepository.saveAndFlush(plantilla));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Ya existe otra plantilla con el título '" + titulo + "' para este técnico");
        }
    }

    @Override
    @Transactional
    public void delete(Long id, String userEmail) {
        User tecnico = requireTechnician(userEmail);
        QuickResponse plantilla = findOwnedTemplate(id, tecnico);
        quickResponseRepository.delete(plantilla);
        log.info("Plantilla #{} eliminada por técnico [{}]", id, userEmail);
    }

    @Override
    @Transactional
    public CommentResponseDTO applyTemplate(Long ticketId, Long plantillaId, String userEmail) {
        User tecnico = requireTechnician(userEmail);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Incidencia no encontrada con ID: " + ticketId));

        // 1. La plantilla debe pertenecer al técnico autenticado (403)
        QuickResponse plantilla = findOwnedTemplate(plantillaId, tecnico);

        // 2. El técnico debe tener asignado el ticket (403)
        if (ticket.getTecnico() == null || !ticket.getTecnico().getId().equals(tecnico.getId())) {
            throw new UnauthorizedTicketActionException("El técnico no tiene asignada esta incidencia");
        }

        // 3. El ticket debe estar EN_PROCESO (409)
        if (ticket.getStatus() != TicketStatus.EN_PROCESO) {
            throw new InvalidStateTransitionException("Solo se pueden aplicar plantillas a incidencias en estado EN_PROCESO");
        }

        TicketComment comment = TicketComment.builder()
                .ticket(ticket)
                .author(tecnico)
                .content(plantilla.getContenido())
                .build();
        TicketComment saved = ticketCommentRepository.save(comment);

        log.info("Plantilla #{} aplicada al ticket #{} por [{}]", plantillaId, ticketId, userEmail);
        return CommentResponseDTO.builder()
                .id(saved.getId())
                .ticketId(ticket.getId())
                .content(saved.getContent())
                .author(TicketMapper.toUserDto(tecnico))
                .createdAt(saved.getCreatedAt())
                .build();
    }

    // ---------------------------------------------------------------- Helpers

    private User requireTechnician(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con correo: " + email));
        if (user.getRole() != Role.TECHNICIAN) {
            throw new UnauthorizedTicketActionException("Solo los técnicos pueden gestionar plantillas de solución rápida");
        }
        return user;
    }

    private QuickResponse findOwnedTemplate(Long id, User tecnico) {
        QuickResponse plantilla = quickResponseRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Plantilla no encontrada con ID: " + id));
        if (plantilla.getTecnico() == null || !plantilla.getTecnico().getId().equals(tecnico.getId())) {
            throw new UnauthorizedTicketActionException("La plantilla no pertenece al técnico autenticado");
        }
        return plantilla;
    }

    private QuickResponseResponseDTO toDto(QuickResponse q) {
        return QuickResponseResponseDTO.builder()
                .id(q.getId())
                .titulo(q.getTitulo())
                .contenido(q.getContenido())
                .categoria(q.getCategoria())
                .tecnicoId(q.getTecnico() != null ? q.getTecnico().getId() : null)
                .build();
    }
}

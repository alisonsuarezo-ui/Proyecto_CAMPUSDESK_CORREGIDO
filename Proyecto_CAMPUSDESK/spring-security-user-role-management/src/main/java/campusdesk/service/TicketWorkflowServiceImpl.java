package campusdesk.service;

import campusdesk.dto.ReopenTicketDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.TransferTicketDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketComment;
import campusdesk.entity.TicketHistory;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.InvalidStateTransitionException;
import campusdesk.exception.TicketClosedException;
import campusdesk.exception.TicketNotFoundException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketCommentRepository;
import campusdesk.repository.TicketHistoryRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implementación de la reapertura (Ejercicio 2) y transferencia entre áreas (Ejercicio 3).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketWorkflowServiceImpl implements TicketWorkflowService {

    /** Ventana máxima (en horas) para reabrir un ticket desde que pasó a RESUELTA. */
    public static final long REOPEN_WINDOW_HOURS = 48;

    private static final int HISTORY_MAX_LENGTH = 255;

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final UserRepository userRepository;

    // ---------------------------------------------------------------- Ejercicio 2

    @Override
    @Transactional
    public TicketResponseDTO reopenTicket(Long ticketId, ReopenTicketDTO dto, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        // Solo el USER creador del ticket puede reabrirlo (403)
        if (user.getRole() != Role.USER || !isRequester(ticket, user)) {
            throw new UnauthorizedTicketActionException("Solo el usuario solicitante que creó la incidencia puede reabrirla");
        }

        // Solo desde RESUELTA (409)
        if (ticket.getStatus() != TicketStatus.RESUELTA) {
            throw new InvalidStateTransitionException("Solo se puede reabrir una incidencia que se encuentre en estado RESUELTA");
        }

        // Restricción temporal: máximo 48 horas desde que pasó a RESUELTA (400)
        Instant resolvedAt = ticketHistoryRepository
                .findFirstByTicketIdAndEstadoNuevoOrderByFechaCambioDesc(ticketId, TicketStatus.RESUELTA)
                .map(TicketHistory::getFechaCambio)
                .orElse(ticket.getUpdatedAt());

        if (resolvedAt != null) {
            long hoursElapsed = ChronoUnit.HOURS.between(resolvedAt, Instant.now());
            if (hoursElapsed >= REOPEN_WINDOW_HOURS) {
                throw new IllegalArgumentException(
                        "El plazo de " + REOPEN_WINDOW_HOURS + " horas para reabrir esta incidencia ha vencido. Debe crear un nuevo ticket");
            }
        }

        String motivo = dto.getMotivo().trim();
        int reaperturasPrevias = ticket.getReaperturasCount() != null ? ticket.getReaperturasCount() : 0;

        ticket.setStatus(TicketStatus.EN_PROCESO);
        ticket.setReaperturasCount(reaperturasPrevias + 1);
        Ticket saved = ticketRepository.save(ticket);

        TicketHistory history = TicketHistory.builder()
                .ticket(saved)
                .estadoAnterior(TicketStatus.RESUELTA)
                .estadoNuevo(TicketStatus.EN_PROCESO)
                .autor(user)
                .comentarioTransicion(truncate("Reapertura: " + motivo))
                .build();
        ticketHistoryRepository.save(history);

        log.info("Ticket #{} reabierto por [{}] (reaperturas: {})", ticketId, userEmail, reaperturasPrevias + 1);
        return TicketMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getReopenedTickets(String userEmail) {
        User user = findUserByEmail(userEmail);
        if (user.getRole() != Role.ADMIN) {
            throw new UnauthorizedTicketActionException("Solo un administrador puede consultar los tickets reabiertos");
        }
        return ticketRepository.findByReaperturasCountGreaterThanOrderByUpdatedAtDesc(0)
                .stream()
                .map(TicketMapper::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------- Ejercicio 3

    @Override
    @Transactional
    public TicketResponseDTO transferTicket(Long ticketId, TransferTicketDTO dto, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        // Un ticket cerrado es inmutable (409)
        if (ticket.getStatus() == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se puede modificar una incidencia en estado CERRADA");
        }

        // Solo ADMIN o el TECHNICIAN asignado actualmente (403)
        if (user.getRole() != Role.ADMIN && !isAssignedTechnician(ticket, user)) {
            throw new UnauthorizedTicketActionException("Solo un administrador o el técnico asignado pueden transferir la incidencia");
        }

        TicketCategory categoriaActual = ticket.getCategory();
        TicketCategory nuevaCategoria = dto.getNuevaCategoria();

        // La nueva categoría no puede ser idéntica a la actual (400)
        if (nuevaCategoria == categoriaActual) {
            throw new IllegalArgumentException("La nueva categoría debe ser distinta a la categoría actual (" + categoriaActual + ")");
        }

        String justificacion = dto.getJustificacion().trim();
        TicketStatus estadoAnterior = ticket.getStatus();
        User tecnicoAnterior = ticket.getTecnico();

        // Desvincular técnico, retroceder a ABIERTA y cambiar de categoría
        ticket.setCategory(nuevaCategoria);
        ticket.setTecnico(null);
        ticket.setStatus(TicketStatus.ABIERTA);
        Ticket saved = ticketRepository.save(ticket);

        // Auditoría en historial
        TicketHistory history = TicketHistory.builder()
                .ticket(saved)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(TicketStatus.ABIERTA)
                .autor(user)
                .comentarioTransicion(truncate("Transferencia de área " + categoriaActual + " -> " + nuevaCategoria
                        + ". Motivo: " + justificacion))
                .build();
        ticketHistoryRepository.save(history);

        // Nota automática del sistema en los comentarios
        String tecnicoTexto = tecnicoAnterior != null
                ? "Técnico desvinculado: " + tecnicoAnterior.getName() + ". "
                : "";
        TicketComment nota = TicketComment.builder()
                .ticket(saved)
                .author(user)
                .content("[SISTEMA] Incidencia transferida de la categoría " + categoriaActual + " a " + nuevaCategoria
                        + " por " + user.getName() + " (" + user.getRole() + "). "
                        + tecnicoTexto + "El estado retrocede a ABIERTA. Motivo: " + justificacion)
                .build();
        ticketCommentRepository.save(nota);

        log.info("Ticket #{} transferido de {} a {} por [{}]", ticketId, categoriaActual, nuevaCategoria, userEmail);
        return TicketMapper.toResponse(saved);
    }

    // ---------------------------------------------------------------- Ejercicio 7

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.ABIERTA,
            TicketStatus.ASIGNADA,
            TicketStatus.EN_PROCESO
    );

    @Override
    @Transactional
    public TicketResponseDTO autoAssignTechnician(Long ticketId, String adminEmail) {
        User admin = findUserByEmail(adminEmail);
        if (admin.getRole() != Role.ADMIN) {
            throw new UnauthorizedTicketActionException("Solo un administrador puede ejecutar la asignación automática de técnicos");
        }

        Ticket ticket = findTicketById(ticketId);

        // No permitir asignar tickets cerrados o resueltos
        if (ticket.getStatus() == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se puede modificar una incidencia en estado CERRADA");
        }
        if (ticket.getStatus() == TicketStatus.RESUELTA) {
            throw new InvalidStateTransitionException("No se puede asignar automáticamente una incidencia en estado RESUELTA");
        }

        List<User> technicians = userRepository.findByRole(Role.TECHNICIAN);
        if (technicians == null || technicians.isEmpty()) {
            throw new IllegalStateException("No hay técnicos disponibles en el sistema para la asignación automática");
        }

        // Ordenar candidatos por ID ascendente para garantizar desempate determinista
        List<User> sortedTechnicians = technicians.stream()
                .sorted(java.util.Comparator.comparing(User::getId))
                .toList();

        User bestTech = null;
        long minLoad = Long.MAX_VALUE;

        for (User tech : sortedTechnicians) {
            long load = ticketRepository.countActiveTicketsByTecnicoIdExcludingTicket(
                    tech.getId(), ACTIVE_STATUSES, ticket.getId());
            if (load < minLoad) {
                minLoad = load;
                bestTech = tech;
            }
        }

        if (bestTech == null) {
            bestTech = sortedTechnicians.get(0);
            minLoad = 0;
        }

        TicketStatus estadoAnterior = ticket.getStatus();
        TicketStatus estadoNuevo = (estadoAnterior == TicketStatus.ABIERTA) ? TicketStatus.ASIGNADA : estadoAnterior;

        ticket.setTecnico(bestTech);
        ticket.setStatus(estadoNuevo);
        Ticket saved = ticketRepository.save(ticket);

        // Registrar auditoría en historial
        TicketHistory history = TicketHistory.builder()
                .ticket(saved)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(estadoNuevo)
                .autor(admin)
                .comentarioTransicion(truncate("Asignación automática a técnico " + bestTech.getName()
                        + " (" + bestTech.getEmail() + ") por menor carga activa (" + minLoad + " tickets)"))
                .build();
        ticketHistoryRepository.save(history);

        // Registrar nota automática en comentarios
        TicketComment comment = TicketComment.builder()
                .ticket(saved)
                .author(admin)
                .content("[SISTEMA] Incidencia asignada automáticamente al técnico " + bestTech.getName()
                        + " (carga activa previa: " + minLoad + " tickets).")
                .build();
        ticketCommentRepository.save(comment);

        log.info("Ticket #{} asignado automáticamente al técnico [{}] con carga [{}] por admin [{}]",
                ticketId, bestTech.getEmail(), minLoad, adminEmail);
        return TicketMapper.toResponse(saved);
    }

    // ---------------------------------------------------------------- Helpers

    private String truncate(String text) {
        if (text == null || text.length() <= HISTORY_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, HISTORY_MAX_LENGTH - 3) + "...";
    }

    private boolean isAssignedTechnician(Ticket ticket, User user) {
        return ticket.getTecnico() != null && ticket.getTecnico().getId().equals(user.getId());
    }

    private boolean isRequester(Ticket ticket, User user) {
        return ticket.getSolicitante() != null && ticket.getSolicitante().getId().equals(user.getId());
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con correo: " + email));
    }

    private Ticket findTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Incidencia no encontrada con ID: " + id));
    }
}

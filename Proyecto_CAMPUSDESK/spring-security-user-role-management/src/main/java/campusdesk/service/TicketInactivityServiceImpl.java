package campusdesk.service;

import campusdesk.dto.AutoCloseResultDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketComment;
import campusdesk.entity.TicketHistory;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketCommentRepository;
import campusdesk.repository.TicketHistoryRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.TicketWorklogRepository;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implementación del servicio de cierre automático de tickets por inactividad (Ejercicio 10).
 * Cierra exclusivamente tickets en estado RESUELTA que superen el umbral configurado de inactividad
 * sin haber tenido comentarios, worklogs ni actualizaciones posteriores.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketInactivityServiceImpl implements TicketInactivityService {

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketWorklogRepository ticketWorklogRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    @Value("${campusdesk.tickets.cierre-inactividad-horas:48}")
    private int cierreInactividadHoras;

    @Override
    @Scheduled(cron = "${campusdesk.tickets.cierre-inactividad-cron:0 0 * * * *}")
    public int autoCloseInactiveTickets() {
        log.info("Iniciando tarea programada de cierre automático por inactividad (umbral: {} horas)", cierreInactividadHoras);
        User systemUser = findSystemUser(null);
        return executeAutoClose(systemUser);
    }

    @Override
    public AutoCloseResultDTO autoCloseInactiveTicketsManual(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con correo: " + adminEmail));

        if (admin.getRole() != Role.ADMIN) {
            throw new UnauthorizedTicketActionException("Solo un administrador puede ejecutar el cierre de tickets por inactividad");
        }

        log.info("Cierre manual por inactividad solicitado por admin [{}] (umbral: {} horas)", adminEmail, cierreInactividadHoras);
        int closedCount = executeAutoClose(admin);

        return AutoCloseResultDTO.builder()
                .ticketsCerrados(closedCount)
                .umbralHoras(cierreInactividadHoras)
                .mensaje("Se cerraron automáticamente " + closedCount + " incidencias inactivas.")
                .build();
    }

    private int executeAutoClose(User author) {
        List<Ticket> resolvedTickets = ticketRepository.findByStatusOrderByCreatedAtDesc(TicketStatus.RESUELTA);
        if (resolvedTickets == null || resolvedTickets.isEmpty()) {
            log.info("No hay incidencias en estado RESUELTA para evaluar.");
            return 0;
        }

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int closedCount = 0;

        for (Ticket ticket : resolvedTickets) {
            try {
                Boolean wasClosed = transactionTemplate.execute(status -> evaluateAndCloseTicket(ticket.getId(), author));
                if (Boolean.TRUE.equals(wasClosed)) {
                    closedCount++;
                }
            } catch (Exception ex) {
                // Manejo de error aislado por ticket para evitar que un fallo detenga a los demás
                log.error("Error al procesar cierre por inactividad para ticket #{}: {}", ticket.getId(), ex.getMessage(), ex);
            }
        }

        log.info("Proceso de cierre por inactividad completado: {} incidencias cerradas de {} evaluadas",
                closedCount, resolvedTickets.size());
        return closedCount;
    }

    private Boolean evaluateAndCloseTicket(Long ticketId, User author) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null || ticket.getStatus() != TicketStatus.RESUELTA) {
            return false;
        }

        // 1. Determinar cuándo se resolvió el ticket
        Instant resolvedAt = ticketHistoryRepository
                .findFirstByTicketIdAndEstadoNuevoOrderByFechaCambioDesc(ticketId, TicketStatus.RESUELTA)
                .map(TicketHistory::getFechaCambio)
                .orElse(ticket.getUpdatedAt());

        if (resolvedAt == null) {
            resolvedAt = ticket.getCreatedAt();
        }

        // 2. Determinar la última actividad real posterior a la resolución
        Instant latestActivity = resolvedAt;

        if (ticket.getUpdatedAt() != null && ticket.getUpdatedAt().isAfter(latestActivity)) {
            latestActivity = ticket.getUpdatedAt();
        }

        // Consultar comentarios posteriores
        var comments = ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        if (comments != null) {
            for (var comment : comments) {
                if (comment.getCreatedAt() != null && comment.getCreatedAt().isAfter(latestActivity)) {
                    latestActivity = comment.getCreatedAt();
                }
            }
        }

        // Consultar registros de tiempo posteriores
        var worklogs = ticketWorklogRepository.findDetailedByTicketId(ticketId);
        if (worklogs != null) {
            for (var worklog : worklogs) {
                if (worklog.getFechaRegistro() != null && worklog.getFechaRegistro().isAfter(latestActivity)) {
                    latestActivity = worklog.getFechaRegistro();
                }
            }
        }

        // 3. Evaluar umbral de inactividad
        long hoursElapsed = ChronoUnit.HOURS.between(latestActivity, Instant.now());
        if (hoursElapsed < cierreInactividadHoras) {
            log.debug("Ticket #{} en RESUELTA tiene actividad reciente (hace {} h < {} h). No se cierra.",
                    ticketId, hoursElapsed, cierreInactividadHoras);
            return false;
        }

        // 4. Cerrar ticket
        ticket.setStatus(TicketStatus.CERRADA);
        Ticket saved = ticketRepository.save(ticket);

        // Si no se proporcionó autor (ejecución automática), reutilizar un admin o el técnico
        User effectiveAuthor = author != null ? author : findSystemUser(saved);

        // 5. Registrar en historial de auditoría
        TicketHistory history = TicketHistory.builder()
                .ticket(saved)
                .estadoAnterior(TicketStatus.RESUELTA)
                .estadoNuevo(TicketStatus.CERRADA)
                .autor(effectiveAuthor)
                .comentarioTransicion("Cierre automático por inactividad tras superar " + cierreInactividadHoras
                        + " horas en estado RESUELTA sin actividad posterior.")
                .build();
        ticketHistoryRepository.save(history);

        // 6. Registrar comentario automático del sistema
        TicketComment comment = TicketComment.builder()
                .ticket(saved)
                .author(effectiveAuthor)
                .content("[SISTEMA] Incidencia cerrada automáticamente por inactividad (" + cierreInactividadHoras
                        + " horas sin actividad en estado RESUELTA).")
                .esInterno(false)
                .build();
        ticketCommentRepository.save(comment);

        log.info("Ticket #{} cerrado automáticamente por inactividad tras {} horas sin actividad", ticketId, hoursElapsed);
        return true;
    }

    private User findSystemUser(Ticket ticket) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        if (admins != null && !admins.isEmpty()) {
            return admins.get(0);
        }
        if (ticket != null) {
            if (ticket.getTecnico() != null) return ticket.getTecnico();
            if (ticket.getSolicitante() != null) return ticket.getSolicitante();
        }
        return userRepository.findAll().stream().findFirst().orElse(null);
    }
}

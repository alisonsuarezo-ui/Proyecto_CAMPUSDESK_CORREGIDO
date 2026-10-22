package campusdesk.service;

import campusdesk.dto.TicketTimeSummaryDTO;
import campusdesk.dto.WorklogRequestDTO;
import campusdesk.dto.WorklogResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.TicketWorklog;
import campusdesk.entity.User;
import campusdesk.exception.InvalidStateTransitionException;
import campusdesk.exception.TicketNotFoundException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.TicketWorklogRepository;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación del registro de tiempo técnico (Ejercicio 4).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketWorklogServiceImpl implements TicketWorklogService {

    public static final int MIN_MINUTOS = 5;
    public static final int MAX_MINUTOS = 480;

    private final TicketWorklogRepository worklogRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public WorklogResponseDTO registerWorklog(Long ticketId, WorklogRequestDTO dto, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        // Solo el TECHNICIAN asignado puede registrar tiempo (403)
        if (user.getRole() != Role.TECHNICIAN || !isAssignedTechnician(ticket, user)) {
            throw new UnauthorizedTicketActionException("Solo el técnico asignado puede registrar tiempo en esta incidencia");
        }

        // El ticket debe estar EN_PROCESO (409)
        if (ticket.getStatus() != TicketStatus.EN_PROCESO) {
            throw new InvalidStateTransitionException("Solo se puede registrar tiempo cuando la incidencia está EN_PROCESO");
        }

        Integer minutos = dto.getMinutosDedicados();
        if (minutos == null || minutos < MIN_MINUTOS || minutos > MAX_MINUTOS) {
            throw new IllegalArgumentException("Los minutos dedicados deben estar entre " + MIN_MINUTOS + " y " + MAX_MINUTOS);
        }

        TicketWorklog worklog = TicketWorklog.builder()
                .ticket(ticket)
                .tecnico(user)
                .minutosDedicados(minutos)
                .descripcionActividad(dto.getDescripcionActividad().trim())
                .build();
        TicketWorklog saved = worklogRepository.save(worklog);

        log.info("Worklog #{} de {} min registrado en ticket #{} por [{}]", saved.getId(), minutos, ticketId, userEmail);
        return toDto(saved, ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketTimeSummaryDTO getTotalTime(Long ticketId, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        boolean allowed = user.getRole() == Role.ADMIN
                || (user.getRole() == Role.USER && isRequester(ticket, user))
                || (user.getRole() == Role.TECHNICIAN && isAssignedTechnician(ticket, user));
        if (!allowed) {
            throw new UnauthorizedTicketActionException("No tiene permisos para consultar el tiempo registrado de esta incidencia");
        }

        Long total = worklogRepository.sumMinutosByTicketId(ticketId);
        List<WorklogResponseDTO> registros = worklogRepository.findDetailedByTicketId(ticketId)
                .stream()
                .map(w -> toDto(w, ticket))
                .toList();

        return TicketTimeSummaryDTO.builder()
                .ticketId(ticketId)
                .totalMinutos(total != null ? total : 0L)
                .registros(registros)
                .build();
    }

    private WorklogResponseDTO toDto(TicketWorklog w, Ticket ticket) {
        return WorklogResponseDTO.builder()
                .id(w.getId())
                .ticketId(ticket.getId())
                .minutosDedicados(w.getMinutosDedicados())
                .descripcionActividad(w.getDescripcionActividad())
                .fechaRegistro(w.getFechaRegistro())
                .tecnico(TicketMapper.toUserDto(w.getTecnico()))
                .build();
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

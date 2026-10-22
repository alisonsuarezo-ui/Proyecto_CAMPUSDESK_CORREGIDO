package campusdesk.service;

import campusdesk.dto.HistoryResponseDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketHistory;
import campusdesk.entity.User;
import campusdesk.exception.TicketNotFoundException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketHistoryRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación del servicio de historial y trazabilidad de incidencias (RF-06).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketHistoryServiceImpl implements TicketHistoryService {

    private final TicketHistoryRepository historyRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<HistoryResponseDTO> getHistoryByTicketId(Long ticketId, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        validateUserAccess(ticket, user);

        List<TicketHistory> histories = historyRepository.findByTicketIdOrderByFechaCambioAsc(ticketId);
        return histories.stream().map(this::mapToDTO).toList();
    }

    private void validateUserAccess(Ticket ticket, User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        if (user.getRole() == Role.USER) {
            if (ticket.getSolicitante() == null || !ticket.getSolicitante().getId().equals(user.getId())) {
                throw new UnauthorizedTicketActionException("No tiene permisos para consultar el historial de esta incidencia");
            }
            return;
        }

        if (user.getRole() == Role.TECHNICIAN) {
            if (ticket.getTecnico() == null || !ticket.getTecnico().getId().equals(user.getId())) {
                throw new UnauthorizedTicketActionException("Solo el técnico asignado puede consultar el historial de esta incidencia");
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

    private HistoryResponseDTO mapToDTO(TicketHistory history) {
        User autor = history.getAutor();
        UserResponseDTO autorDto = autor != null ? UserResponseDTO.builder()
                .id(autor.getId())
                .name(autor.getName())
                .email(autor.getEmail())
                .role(autor.getRole())
                .build() : null;

        return HistoryResponseDTO.builder()
                .id(history.getId())
                .ticketId(history.getTicket().getId())
                .estadoAnterior(history.getEstadoAnterior())
                .estadoNuevo(history.getEstadoNuevo())
                .autor(autorDto)
                .fechaCambio(history.getFechaCambio())
                .comentarioTransicion(history.getComentarioTransicion())
                .build();
    }
}

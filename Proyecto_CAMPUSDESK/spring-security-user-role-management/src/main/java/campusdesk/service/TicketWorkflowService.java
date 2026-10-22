package campusdesk.service;

import campusdesk.dto.ReopenTicketDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.TransferTicketDTO;

import java.util.List;

/**
 * Ejercicios 2 y 3: reapertura de tickets resueltos y transferencia entre áreas.
 */
public interface TicketWorkflowService {

    /** Ejercicio 2: RESUELTA -> EN_PROCESO dentro de las 48 h, solo por el USER creador. */
    TicketResponseDTO reopenTicket(Long ticketId, ReopenTicketDTO dto, String userEmail);

    /** Ejercicio 2: tickets con reaperturasCount > 0 (solo ADMIN). */
    List<TicketResponseDTO> getReopenedTickets(String userEmail);

    /** Ejercicio 3: cambio de categoría con desvinculación del técnico y retroceso a ABIERTA. */
    TicketResponseDTO transferTicket(Long ticketId, TransferTicketDTO dto, String userEmail);

    /** Ejercicio 7: asignación automática al técnico con menor carga de tickets activos (solo ADMIN). */
    TicketResponseDTO autoAssignTechnician(Long ticketId, String adminEmail);
}

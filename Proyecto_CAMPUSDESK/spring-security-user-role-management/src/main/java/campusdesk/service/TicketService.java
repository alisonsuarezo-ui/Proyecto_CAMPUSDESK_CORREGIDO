package campusdesk.service;

import campusdesk.dto.CreateTicketDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;

import java.util.List;

/**
 * Contrato de servicio para la gestión de incidencias y su máquina de estados (RF-03, RF-04, RF-08).
 */
public interface TicketService {

    TicketResponseDTO createTicket(CreateTicketDTO dto, String userEmail);

    TicketResponseDTO assignTechnician(Long ticketId, Long tecnicoId, String adminEmail);

    TicketResponseDTO updateStatus(Long ticketId, TicketStatus newStatus, String comentario, String userEmail);

    TicketResponseDTO getTicketById(Long ticketId, String userEmail);

    TicketResponseDTO updateTicket(Long ticketId, campusdesk.dto.UpdateTicketDTO dto, String userEmail);

    List<TicketResponseDTO> getTickets(TicketStatus status, TicketPriority priority, TicketCategory category, String userEmail);

    // Ejercicio 1: Sistema de Etiquetas (Tags)
    TicketResponseDTO addTagsToTicket(Long ticketId, List<String> tagNames, String userEmail);

    List<TicketResponseDTO> getTicketsByTag(String tagName, String userEmail);

    // Ejercicio 6: Cambio de prioridad con historial de auditoría
    TicketResponseDTO updatePriority(Long ticketId, campusdesk.dto.ChangePriorityDTO dto, String userEmail);
}

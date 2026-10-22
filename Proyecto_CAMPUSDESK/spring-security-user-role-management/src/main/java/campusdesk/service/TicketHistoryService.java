package campusdesk.service;

import campusdesk.dto.HistoryResponseDTO;

import java.util.List;

/**
 * Contrato de servicio para la trazabilidad y auditoría cronológica de incidencias (RF-06).
 */
public interface TicketHistoryService {

    List<HistoryResponseDTO> getHistoryByTicketId(Long ticketId, String userEmail);
}

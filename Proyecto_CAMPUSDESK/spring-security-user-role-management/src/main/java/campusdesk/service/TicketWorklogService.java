package campusdesk.service;

import campusdesk.dto.TicketTimeSummaryDTO;
import campusdesk.dto.WorklogRequestDTO;
import campusdesk.dto.WorklogResponseDTO;

/**
 * Ejercicio 4: bitácora de registro de tiempo técnico.
 */
public interface TicketWorklogService {

    WorklogResponseDTO registerWorklog(Long ticketId, WorklogRequestDTO dto, String userEmail);

    TicketTimeSummaryDTO getTotalTime(Long ticketId, String userEmail);
}

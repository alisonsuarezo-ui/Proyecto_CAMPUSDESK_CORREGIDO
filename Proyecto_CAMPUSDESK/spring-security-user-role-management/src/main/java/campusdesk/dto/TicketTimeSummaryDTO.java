package campusdesk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Ejercicio 4: resumen de tiempo acumulado de un ticket con el detalle de sus registros. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTimeSummaryDTO {

    private Long ticketId;

    private Long totalMinutos;

    private List<WorklogResponseDTO> registros;
}

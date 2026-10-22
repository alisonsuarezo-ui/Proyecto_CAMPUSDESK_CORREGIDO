package campusdesk.dto;

import campusdesk.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO que representa un evento de auditoría en la línea de tiempo de una incidencia (RF-06).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryResponseDTO {

    private Long id;

    private Long ticketId;

    private TicketStatus estadoAnterior;

    private TicketStatus estadoNuevo;

    private UserResponseDTO autor;

    private Instant fechaCambio;

    private String comentarioTransicion;
}

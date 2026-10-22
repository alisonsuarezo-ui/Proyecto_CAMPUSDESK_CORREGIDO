package campusdesk.dto;

import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * DTO que expone la información detallada de una incidencia en la API REST.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponseDTO {

    private Long id;

    private String title;

    private String description;

    private UserResponseDTO solicitante;

    private UserResponseDTO tecnico;

    private TicketStatus status;

    private TicketPriority priority;

    private TicketCategory category;

    private Set<String> tags;

    private Integer reaperturasCount;

    private Instant createdAt;

    private Instant updatedAt;
}

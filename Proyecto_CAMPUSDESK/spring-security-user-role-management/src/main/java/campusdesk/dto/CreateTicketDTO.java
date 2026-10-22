package campusdesk.dto;

import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la creación de un nuevo ticket de soporte (RF-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketDTO {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 150, message = "El título no puede exceder los 150 caracteres")
    private String title;

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    @NotNull(message = "La prioridad es obligatoria (BAJA, MEDIA, ALTA, CRITICA)")
    private TicketPriority priority;

    @NotNull(message = "La categoría es obligatoria (HARDWARE, SOFTWARE, REDES, ACCESOS, OTROS)")
    private TicketCategory category;

    private TicketStatus status;
}

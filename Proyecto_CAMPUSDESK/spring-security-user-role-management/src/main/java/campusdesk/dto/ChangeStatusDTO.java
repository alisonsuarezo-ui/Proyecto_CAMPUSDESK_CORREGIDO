package campusdesk.dto;

import campusdesk.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la solicitud de cambio de estado en la máquina de estados del ticket (RF-04).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeStatusDTO {

    @NotNull(message = "El nuevo estado es obligatorio")
    private TicketStatus status;

    private String comentario;
}

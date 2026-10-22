package campusdesk.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la asignación de un técnico por parte del administrador (RF-04).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignTicketDTO {

    @NotNull(message = "El ID del técnico asignado es obligatorio")
    private Long tecnicoId;
}

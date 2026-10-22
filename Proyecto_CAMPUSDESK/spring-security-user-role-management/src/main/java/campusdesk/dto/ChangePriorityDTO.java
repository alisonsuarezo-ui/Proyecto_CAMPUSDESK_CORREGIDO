package campusdesk.dto;

import campusdesk.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ejercicio 6: DTO para solicitar cambio de prioridad con motivo obligatorio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePriorityDTO {

    @NotNull(message = "La nueva prioridad es obligatoria (BAJA, MEDIA, ALTA, CRITICA)")
    private TicketPriority priority;

    @NotBlank(message = "El motivo del cambio de prioridad es obligatorio")
    @Size(max = 250, message = "El motivo no puede exceder los 250 caracteres")
    private String motivo;
}

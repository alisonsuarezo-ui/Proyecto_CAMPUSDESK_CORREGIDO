package campusdesk.dto;

import campusdesk.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ejercicio 3: DTO de transferencia de un ticket a otra categoría/área. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferTicketDTO {

    @NotNull(message = "La nueva categoría es obligatoria (HARDWARE, SOFTWARE, REDES, ACCESOS, OTROS)")
    private TicketCategory nuevaCategoria;

    @NotBlank(message = "La justificación de la transferencia es obligatoria")
    @Size(max = 250, message = "La justificación no puede exceder los 250 caracteres")
    private String justificacion;
}

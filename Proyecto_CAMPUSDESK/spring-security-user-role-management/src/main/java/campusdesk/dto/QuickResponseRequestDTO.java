package campusdesk.dto;

import campusdesk.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ejercicio 5: DTO de creación/edición de una plantilla de solución rápida. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickResponseRequestDTO {

    @NotBlank(message = "El título de la plantilla es obligatorio")
    @Size(max = 100, message = "El título no puede exceder los 100 caracteres")
    private String titulo;

    @NotBlank(message = "El contenido de la plantilla es obligatorio")
    @Size(max = 4000, message = "El contenido no puede exceder los 4000 caracteres")
    private String contenido;

    @NotNull(message = "La categoría es obligatoria (HARDWARE, SOFTWARE, REDES, ACCESOS, OTROS)")
    private TicketCategory categoria;
}

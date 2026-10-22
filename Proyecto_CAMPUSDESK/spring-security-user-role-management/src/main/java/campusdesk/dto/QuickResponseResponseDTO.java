package campusdesk.dto;

import campusdesk.entity.TicketCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ejercicio 5: representación de una plantilla de solución rápida. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickResponseResponseDTO {

    private Long id;

    private String titulo;

    private String contenido;

    private TicketCategory categoria;

    private Long tecnicoId;
}

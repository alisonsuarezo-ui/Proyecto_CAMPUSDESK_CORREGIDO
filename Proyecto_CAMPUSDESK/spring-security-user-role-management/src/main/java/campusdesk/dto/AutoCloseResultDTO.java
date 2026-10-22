package campusdesk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para la ejecución del cierre de tickets por inactividad (Ejercicio 10).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoCloseResultDTO {

    private int ticketsCerrados;

    private int umbralHoras;

    private String mensaje;
}

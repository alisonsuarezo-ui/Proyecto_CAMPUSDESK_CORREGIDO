package campusdesk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Ejercicio 4: representación de un registro de tiempo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorklogResponseDTO {

    private Long id;

    private Long ticketId;

    private Integer minutosDedicados;

    private String descripcionActividad;

    private Instant fechaRegistro;

    private UserResponseDTO tecnico;
}

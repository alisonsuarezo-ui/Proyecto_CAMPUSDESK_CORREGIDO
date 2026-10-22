package campusdesk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ejercicio 4: DTO para registrar un bloque de tiempo de trabajo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorklogRequestDTO {

    @NotNull(message = "Los minutos dedicados son obligatorios")
    @Min(value = 5, message = "Se deben registrar mínimo 5 minutos por sesión")
    @Max(value = 480, message = "Se pueden registrar máximo 480 minutos (8 horas) por sesión")
    private Integer minutosDedicados;

    @NotBlank(message = "La descripción de la actividad es obligatoria")
    @Size(max = 500, message = "La descripción no puede exceder los 500 caracteres")
    private String descripcionActividad;
}

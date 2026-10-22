package campusdesk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para las métricas de productividad por técnico (Ejercicio 9).
 * Representa el consolidado de incidencias y tiempo técnico registrado para cada técnico.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicianProductivityDTO {

    private Long tecnicoId;

    private String tecnicoNombre;

    private String tecnicoEmail;

    /** Total histórico de tickets asignados al técnico. */
    private long ticketsAsignados;

    /** Tickets en estado activo: ABIERTA, ASIGNADA, EN_PROCESO. */
    private long ticketsActivos;

    /** Tickets en estado finalizado: RESUELTA, CERRADA. */
    private long ticketsResueltos;

    /** Suma total de minutos registrados en sus worklogs. */
    private long totalMinutosWorklog;

    /**
     * Promedio de minutos registrados por ticket asignado.
     * Denominador utilizado: total de tickets asignados (ticketsAsignados).
     * Si no existen tickets asignados o no hay minutos registrados, el valor es 0.0.
     */
    private double promedioMinutosPorTicket;
}

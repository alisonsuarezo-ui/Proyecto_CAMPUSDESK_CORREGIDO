package campusdesk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para las métricas de soporte del panel de control (RF-07).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricsDTO {

    private long total;

    private long abiertas;

    private long asignadas;

    private long enProceso;

    private long resueltas;

    private long cerradas;
}

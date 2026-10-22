package campusdesk.service;

import campusdesk.dto.DashboardMetricsDTO;
import campusdesk.dto.TechnicianProductivityDTO;

import java.util.List;

/**
 * Contrato de servicio para el cómputo de métricas del panel de control (RF-07 y Ejercicio 9).
 */
public interface DashboardService {

    DashboardMetricsDTO getMetrics(String userEmail);

    /**
     * Ejercicio 9: Métricas agregadas de productividad por técnico (exclusivo para ADMIN).
     */
    List<TechnicianProductivityDTO> getTechnicianProductivity(String adminEmail);
}

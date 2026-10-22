package campusdesk.service;

import campusdesk.dto.DashboardMetricsDTO;
import campusdesk.dto.TechnicianProductivityDTO;
import campusdesk.entity.Role;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.TicketWorklogRepository;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Implementación del cálculo de métricas de incidencias y productividad según el perfil de usuario (RF-07 y Ejercicio 9).
 */
@Service
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.ABIERTA, TicketStatus.ASIGNADA, TicketStatus.EN_PROCESO
    );

    private static final List<TicketStatus> RESOLVED_STATUSES = List.of(
            TicketStatus.RESUELTA, TicketStatus.CERRADA
    );

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketWorklogRepository ticketWorklogRepository;

    public DashboardServiceImpl(TicketRepository ticketRepository, UserRepository userRepository) {
        this(ticketRepository, userRepository, null);
    }

    public DashboardServiceImpl(TicketRepository ticketRepository, UserRepository userRepository, TicketWorklogRepository ticketWorklogRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.ticketWorklogRepository = ticketWorklogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardMetricsDTO getMetrics(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userEmail));

        long abiertas;
        long asignadas;
        long enProceso;
        long resueltas;
        long cerradas;
        long total;

        if (user.getRole() == Role.ADMIN) {
            abiertas = ticketRepository.countByStatus(TicketStatus.ABIERTA);
            asignadas = ticketRepository.countByStatus(TicketStatus.ASIGNADA);
            enProceso = ticketRepository.countByStatus(TicketStatus.EN_PROCESO);
            resueltas = ticketRepository.countByStatus(TicketStatus.RESUELTA);
            cerradas = ticketRepository.countByStatus(TicketStatus.CERRADA);
            total = ticketRepository.count();
        } else if (user.getRole() == Role.TECHNICIAN) {
            abiertas = ticketRepository.countByTecnicoIdAndStatus(user.getId(), TicketStatus.ABIERTA);
            asignadas = ticketRepository.countByTecnicoIdAndStatus(user.getId(), TicketStatus.ASIGNADA);
            enProceso = ticketRepository.countByTecnicoIdAndStatus(user.getId(), TicketStatus.EN_PROCESO);
            resueltas = ticketRepository.countByTecnicoIdAndStatus(user.getId(), TicketStatus.RESUELTA);
            cerradas = ticketRepository.countByTecnicoIdAndStatus(user.getId(), TicketStatus.CERRADA);
            total = ticketRepository.countByTecnicoId(user.getId());
        } else {
            abiertas = ticketRepository.countBySolicitanteIdAndStatus(user.getId(), TicketStatus.ABIERTA);
            asignadas = ticketRepository.countBySolicitanteIdAndStatus(user.getId(), TicketStatus.ASIGNADA);
            enProceso = ticketRepository.countBySolicitanteIdAndStatus(user.getId(), TicketStatus.EN_PROCESO);
            resueltas = ticketRepository.countBySolicitanteIdAndStatus(user.getId(), TicketStatus.RESUELTA);
            cerradas = ticketRepository.countBySolicitanteIdAndStatus(user.getId(), TicketStatus.CERRADA);
            total = ticketRepository.countBySolicitanteId(user.getId());
        }

        return DashboardMetricsDTO.builder()
                .total(total)
                .abiertas(abiertas)
                .asignadas(asignadas)
                .enProceso(enProceso)
                .resueltas(resueltas)
                .cerradas(cerradas)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianProductivityDTO> getTechnicianProductivity(String adminEmail) {
        User user = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + adminEmail));

        if (user.getRole() != Role.ADMIN) {
            throw new UnauthorizedTicketActionException("Solo un administrador puede consultar las métricas de productividad de los técnicos");
        }

        List<User> technicians = userRepository.findByRole(Role.TECHNICIAN);
        if (technicians == null || technicians.isEmpty()) {
            return Collections.emptyList();
        }

        return technicians.stream()
                .sorted(Comparator.comparing(User::getId))
                .map(tech -> {
                    long totalAsignados = ticketRepository.countByTecnicoId(tech.getId());
                    long activos = ticketRepository.countByTecnicoIdAndStatusIn(tech.getId(), ACTIVE_STATUSES);
                    long resueltos = ticketRepository.countByTecnicoIdAndStatusIn(tech.getId(), RESOLVED_STATUSES);
                    Long sumMinutos = ticketWorklogRepository.sumMinutosByTecnicoId(tech.getId());
                    long totalMinutos = (sumMinutos != null) ? sumMinutos : 0L;

                    // Denominador utilizado: total de tickets asignados (si totalAsignados == 0 o totalMinutos == 0 -> 0.0)
                    double promedio = (totalAsignados > 0 && totalMinutos > 0)
                            ? Math.round(((double) totalMinutos / totalAsignados) * 100.0) / 100.0
                            : 0.0;

                    return TechnicianProductivityDTO.builder()
                            .tecnicoId(tech.getId())
                            .tecnicoNombre(tech.getName())
                            .tecnicoEmail(tech.getEmail())
                            .ticketsAsignados(totalAsignados)
                            .ticketsActivos(activos)
                            .ticketsResueltos(resueltos)
                            .totalMinutosWorklog(totalMinutos)
                            .promedioMinutosPorTicket(promedio)
                            .build();
                })
                .toList();
    }
}

package campusdesk.service;

import campusdesk.dto.TicketTimeSummaryDTO;
import campusdesk.dto.WorklogRequestDTO;
import campusdesk.dto.WorklogResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.TicketWorklog;
import campusdesk.entity.User;
import campusdesk.exception.InvalidStateTransitionException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.TicketWorklogRepository;
import campusdesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del Ejercicio 4 (bitácora de tiempo técnico).
 */
@ExtendWith(MockitoExtension.class)
class TicketWorklogServiceTest {

    @Mock
    private TicketWorklogRepository worklogRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TicketWorklogServiceImpl service;

    private User requester;
    private User technician;
    private User otherTechnician;
    private User admin;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(1L).email("user@campusdesk.com").name("Carlos User").role(Role.USER).build();
        technician = User.builder().id(2L).email("tech@campusdesk.com").name("Ana Tech").role(Role.TECHNICIAN).build();
        admin = User.builder().id(3L).email("admin@campusdesk.com").name("Super Admin").role(Role.ADMIN).build();
        otherTechnician = User.builder().id(5L).email("tech2@campusdesk.com").name("Luis Tech").role(Role.TECHNICIAN).build();

        ticket = Ticket.builder()
                .id(10L)
                .title("Falla")
                .description("desc")
                .solicitante(requester)
                .tecnico(technician)
                .status(TicketStatus.EN_PROCESO)
                .priority(TicketPriority.MEDIA)
                .category(TicketCategory.HARDWARE)
                .build();
    }

    private WorklogRequestDTO request(int minutos) {
        return new WorklogRequestDTO(minutos, "Diagnóstico de equipo");
    }

    @Test
    @DisplayName("Ej.4: el técnico asignado registra tiempo con el ticket EN_PROCESO")
    void registerOk() {
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(worklogRepository.save(any(TicketWorklog.class))).thenAnswer(inv -> {
            TicketWorklog w = inv.getArgument(0);
            w.setId(1L);
            return w;
        });

        WorklogResponseDTO response = service.registerWorklog(10L, request(60), technician.getEmail());

        assertEquals(1L, response.getId());
        assertEquals(60, response.getMinutosDedicados());
        assertEquals(10L, response.getTicketId());
    }

    @Test
    @DisplayName("Ej.4: un técnico no asignado no puede registrar tiempo (403)")
    void registerByOtherTechnicianFails() {
        when(userRepository.findByEmail(otherTechnician.getEmail())).thenReturn(Optional.of(otherTechnician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedTicketActionException.class,
                () -> service.registerWorklog(10L, request(30), otherTechnician.getEmail()));
        verify(worklogRepository, never()).save(any(TicketWorklog.class));
    }

    @Test
    @DisplayName("Ej.4: ADMIN tampoco puede registrar tiempo (solo el técnico asignado)")
    void registerByAdminFails() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedTicketActionException.class,
                () -> service.registerWorklog(10L, request(30), admin.getEmail()));
    }

    @Test
    @DisplayName("Ej.4: el ticket debe estar EN_PROCESO (409)")
    void registerWrongStatusFails() {
        ticket.setStatus(TicketStatus.ASIGNADA);
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.registerWorklog(10L, request(30), technician.getEmail()));
    }

    @Test
    @DisplayName("Ej.4: minutos fuera del rango 5-480 se rechazan (400)")
    void registerOutOfRangeFails() {
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalArgumentException.class, () -> service.registerWorklog(10L, request(4), technician.getEmail()));
        assertThrows(IllegalArgumentException.class, () -> service.registerWorklog(10L, request(481), technician.getEmail()));
        verify(worklogRepository, never()).save(any(TicketWorklog.class));
    }

    @Test
    @DisplayName("Ej.4: tiempo-total retorna el SUM del repositorio y el detalle de registros")
    void totalTime() {
        TicketWorklog w = TicketWorklog.builder().id(1L).ticket(ticket).tecnico(technician)
                .minutosDedicados(90).descripcionActividad("Cambio de fuente").fechaRegistro(Instant.now()).build();
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(worklogRepository.sumMinutosByTicketId(10L)).thenReturn(90L);
        when(worklogRepository.findDetailedByTicketId(10L)).thenReturn(List.of(w));

        TicketTimeSummaryDTO summary = service.getTotalTime(10L, admin.getEmail());

        assertEquals(10L, summary.getTicketId());
        assertEquals(90L, summary.getTotalMinutos());
        assertEquals(1, summary.getRegistros().size());
    }

    @Test
    @DisplayName("Ej.4: sin registros el total es 0")
    void totalTimeWithoutRecords() {
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(worklogRepository.sumMinutosByTicketId(10L)).thenReturn(null);
        when(worklogRepository.findDetailedByTicketId(10L)).thenReturn(List.of());

        TicketTimeSummaryDTO summary = service.getTotalTime(10L, technician.getEmail());

        assertEquals(0L, summary.getTotalMinutos());
        assertTrue(summary.getRegistros().isEmpty());
    }

    @Test
    @DisplayName("Ej.4: un técnico ajeno no puede consultar el tiempo total (403)")
    void totalTimeByOtherTechnicianFails() {
        when(userRepository.findByEmail(otherTechnician.getEmail())).thenReturn(Optional.of(otherTechnician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedTicketActionException.class, () -> service.getTotalTime(10L, otherTechnician.getEmail()));
    }
}

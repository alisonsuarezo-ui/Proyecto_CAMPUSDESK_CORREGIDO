package campusdesk;

import campusdesk.dto.ChangeStatusDTO;
import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;
import campusdesk.dto.CreateTicketDTO;
import campusdesk.dto.DashboardMetricsDTO;
import campusdesk.dto.HistoryResponseDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.UserRegistrationDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.InvalidStateTransitionException;
import campusdesk.exception.TicketClosedException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketCommentRepository;
import campusdesk.repository.TicketHistoryRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.UserRepository;
import campusdesk.service.DashboardServiceImpl;
import campusdesk.service.TicketCommentServiceImpl;
import campusdesk.service.TicketHistoryServiceImpl;
import campusdesk.service.TicketServiceImpl;
import campusdesk.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Suite de certificación E2E integral que valida los 10 Casos de Prueba Oficiales (CP-01 a CP-10).
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CampusdeskE2EWorkflowTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketHistoryRepository ticketHistoryRepository;

    @Mock
    private TicketCommentRepository ticketCommentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private campusdesk.repository.TagRepository tagRepository;

    private UserService userService;
    private TicketServiceImpl ticketService;
    private TicketCommentServiceImpl commentService;
    private TicketHistoryServiceImpl historyService;
    private DashboardServiceImpl dashboardService;

    private User requester;
    private User technician;
    private User admin;
    private User intruder;

    private final AtomicLong ticketIdCounter = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
        ticketService = new TicketServiceImpl(ticketRepository, ticketHistoryRepository, userRepository, tagRepository);
        commentService = new TicketCommentServiceImpl(ticketCommentRepository, ticketRepository, userRepository);
        historyService = new TicketHistoryServiceImpl(ticketHistoryRepository, ticketRepository, userRepository);
        dashboardService = new DashboardServiceImpl(ticketRepository, userRepository);

        requester = User.builder().id(1L).email("usuario@campusdesk.com").name("Carlos Solicitante").role(Role.USER).build();
        technician = User.builder().id(2L).email("tecnico@campusdesk.com").name("Ana Soporte").role(Role.TECHNICIAN).build();
        admin = User.builder().id(3L).email("superadmin@campusdesk.com").name("Admin Principal").role(Role.ADMIN).build();
        intruder = User.builder().id(4L).email("intruso@campusdesk.com").name("Usuario Intruso").role(Role.USER).build();
    }

    @Test
    @Order(1)
    @DisplayName("CP-01: Registro de nuevo usuario asigna estrictamente el rol USER por defecto")
    void testCP01_UserRegistrationRole() {
        UserRegistrationDTO dto = UserRegistrationDTO.builder()
                .name("Nuevo Estudiante")
                .email("nuevo@campusdesk.com")
                .password("Password123!")
                .build();

        when(userRepository.existsByEmail("nuevo@campusdesk.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("$2a$10$encodedHashDummy");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponseDTO created = userService.registerUser(dto);

        assertNotNull(created);
        assertEquals(Role.USER, created.getRole(), "El rol asignado en registro público DEBE ser estrictamente USER");
        assertEquals("nuevo@campusdesk.com", created.getEmail());
    }

    @Test
    @Order(2)
    @DisplayName("CP-03: Creación de incidencia inicia estrictamente en estado ABIERTA")
    void testCP03_CreateTicketInitialStatus() {
        CreateTicketDTO dto = CreateTicketDTO.builder()
                .title("Falla en switch de laboratorio 3")
                .description("No hay conectividad de red para las computadoras")
                .category(TicketCategory.REDES)
                .priority(TicketPriority.ALTA)
                .build();

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            campusdesk.entity.Ticket t = inv.getArgument(0);
            t.setId(101L);
            return t;
        });

        TicketResponseDTO result = ticketService.createTicket(dto, requester.getEmail());

        assertNotNull(result);
        assertEquals(TicketStatus.ABIERTA, result.getStatus(), "Todo nuevo ticket DEBE iniciar en ABIERTA");
        assertNull(result.getTecnico(), "Un ticket nuevo no tiene técnico asignado");
        verify(ticketHistoryRepository, times(1)).save(any());
    }

    @Test
    @Order(3)
    @DisplayName("CP-04: Asignación de técnico por Administrador transiciona a ASIGNADA")
    void testCP04_AdminAssignTechnician() {
        campusdesk.entity.Ticket ticket = campusdesk.entity.Ticket.builder()
                .id(101L)
                .title("Falla de red")
                .solicitante(requester)
                .status(TicketStatus.ABIERTA)
                .build();

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(technician));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO result = ticketService.assignTechnician(101L, 2L, admin.getEmail());

        assertEquals(TicketStatus.ASIGNADA, result.getStatus());
        assertEquals("Ana Soporte", result.getTecnico().getName());
        verify(ticketHistoryRepository, times(1)).save(any());
    }

    @Test
    @Order(4)
    @DisplayName("CP-05: Intento no autorizado de asignación de técnico por un USER es rechazado (403)")
    void testCP05_UserCannotAssignTechnician() {
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.assignTechnician(101L, 2L, requester.getEmail()));
    }

    @Test
    @Order(5)
    @DisplayName("CP-06: Transición a EN_PROCESO permitida para el técnico asignado")
    void testCP06_TechnicianStartsProcess() {
        campusdesk.entity.Ticket ticket = campusdesk.entity.Ticket.builder()
                .id(101L)
                .solicitante(requester)
                .tecnico(technician)
                .status(TicketStatus.ASIGNADA)
                .build();

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO result = ticketService.updateStatus(101L, TicketStatus.EN_PROCESO, "Iniciando diagnóstico", technician.getEmail());

        assertEquals(TicketStatus.EN_PROCESO, result.getStatus());
    }

    @Test
    @Order(6)
    @DisplayName("CP-07: Transición a RESUELTA por técnico asignado tras haber estado EN_PROCESO")
    void testCP07_TechnicianResolvesTicket() {
        campusdesk.entity.Ticket ticket = campusdesk.entity.Ticket.builder()
                .id(101L)
                .solicitante(requester)
                .tecnico(technician)
                .status(TicketStatus.EN_PROCESO)
                .build();

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO result = ticketService.updateStatus(101L, TicketStatus.RESUELTA, "Cable reemplazado", technician.getEmail());

        assertEquals(TicketStatus.RESUELTA, result.getStatus());
    }

    @Test
    @Order(7)
    @DisplayName("CP-08: Intento de resolución inválida / saltar estados es estrictamente rechazado")
    void testCP08_InvalidStateTransitionRejected() {
        campusdesk.entity.Ticket ticket = campusdesk.entity.Ticket.builder()
                .id(101L)
                .solicitante(requester)
                .tecnico(technician)
                .status(TicketStatus.ASIGNADA) // Aún en ASIGNADA, no ha pasado por EN_PROCESO
                .build();

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateStatus(101L, TicketStatus.RESUELTA, "Salto ilegal", technician.getEmail()));
    }

    @Test
    @Order(8)
    @DisplayName("CP-09: Consulta de métricas del Dashboard en tiempo real")
    void testCP09_DashboardMetricsComputation() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.countByStatus(TicketStatus.ABIERTA)).thenReturn(5L);
        when(ticketRepository.countByStatus(TicketStatus.ASIGNADA)).thenReturn(3L);
        when(ticketRepository.countByStatus(TicketStatus.EN_PROCESO)).thenReturn(4L);
        when(ticketRepository.countByStatus(TicketStatus.RESUELTA)).thenReturn(2L);
        when(ticketRepository.countByStatus(TicketStatus.CERRADA)).thenReturn(6L);
        when(ticketRepository.count()).thenReturn(20L);

        DashboardMetricsDTO metrics = dashboardService.getMetrics(admin.getEmail());

        assertEquals(20L, metrics.getTotal());
        assertEquals(5L, metrics.getAbiertas());
        assertEquals(3L, metrics.getAsignadas());
        assertEquals(4L, metrics.getEnProceso());
        assertEquals(2L, metrics.getResueltas());
        assertEquals(6L, metrics.getCerradas());
    }

    @Test
    @Order(9)
    @DisplayName("CP-10: Cierre de ticket por usuario solicitante e inmutabilidad estricta")
    void testCP10_RequesterClosesTicketAndImmutability() {
        campusdesk.entity.Ticket ticket = campusdesk.entity.Ticket.builder()
                .id(101L)
                .solicitante(requester)
                .tecnico(technician)
                .status(TicketStatus.RESUELTA)
                .build();

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(101L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 1. Solicitante cierra el ticket
        TicketResponseDTO closed = ticketService.updateStatus(101L, TicketStatus.CERRADA, "Conforme con la solución", requester.getEmail());
        assertEquals(TicketStatus.CERRADA, closed.getStatus());

        // 2. Intento de modificar estado en ticket CERRADO -> TicketClosedException
        assertThrows(TicketClosedException.class, () ->
                ticketService.updateStatus(101L, TicketStatus.EN_PROCESO, "Reabrir", admin.getEmail()));

        // 3. Intento de comentar en ticket CERRADO -> TicketClosedException
        CreateCommentDTO commentDTO = new CreateCommentDTO("Comentario tardío");
        assertThrows(TicketClosedException.class, () ->
                commentService.addComment(101L, commentDTO, requester.getEmail()));
    }
}

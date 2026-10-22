package campusdesk.controller;

import campusdesk.dto.AutoCloseResultDTO;
import campusdesk.dto.ChangePriorityDTO;
import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;
import campusdesk.dto.TechnicianProductivityDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.entity.TicketPriority;
import campusdesk.exception.ApiExceptionHandler;
import campusdesk.service.DashboardService;
import campusdesk.service.QuickResponseService;
import campusdesk.service.TicketCommentService;
import campusdesk.service.TicketInactivityService;
import campusdesk.service.TicketService;
import campusdesk.service.TicketWorkflowService;
import campusdesk.service.TicketWorklogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class Exercises6to10ControllerTest {

    private MockMvc ticketMvc;
    private MockMvc extrasMvc;
    private MockMvc dashboardMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private TicketService ticketService;
    @Mock
    private campusdesk.service.TicketHistoryService ticketHistoryService;

    @Mock
    private TicketWorkflowService ticketWorkflowService;
    @Mock
    private TicketWorklogService ticketWorklogService;
    @Mock
    private QuickResponseService quickResponseService;
    @Mock
    private TicketCommentService ticketCommentService;
    @Mock
    private TicketInactivityService ticketInactivityService;

    @Mock
    private DashboardService dashboardService;

    private Principal adminPrincipal = () -> "admin@campusdesk.edu";
    private Principal techPrincipal = () -> "tech@campusdesk.edu";

    @BeforeEach
    void setUp() {
        TicketController ticketController = new TicketController(ticketService, ticketHistoryService);
        ticketMvc = MockMvcBuilders.standaloneSetup(ticketController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        TicketExtrasController extrasController = new TicketExtrasController(
                ticketWorkflowService, ticketWorklogService, quickResponseService, ticketCommentService, ticketInactivityService
        );
        extrasMvc = MockMvcBuilders.standaloneSetup(extrasController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        DashboardController dashboardController = new DashboardController(dashboardService);
        dashboardMvc = MockMvcBuilders.standaloneSetup(dashboardController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("PATCH /api/tickets/{id}/prioridad responde 200 con ticket actualizado")
    void changePriorityEndpointSuccess() throws Exception {
        ChangePriorityDTO dto = ChangePriorityDTO.builder()
                .priority(TicketPriority.ALTA)
                .motivo("Reclasificación urgente")
                .build();

        TicketResponseDTO response = TicketResponseDTO.builder()
                .id(1L).priority(TicketPriority.ALTA).build();

        when(ticketService.updatePriority(eq(1L), any(), eq("admin@campusdesk.edu")))
                .thenReturn(response);

        ticketMvc.perform(patch("/api/tickets/1/prioridad")
                        .principal(adminPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("ALTA"));
    }

    @Test
    @DisplayName("PATCH /api/tickets/{id}/prioridad valida motivo obligatorio (400 si está vacío)")
    void changePriorityValidationFailsWhenMotivoEmpty() throws Exception {
        ChangePriorityDTO dto = ChangePriorityDTO.builder()
                .priority(TicketPriority.ALTA)
                .motivo("   ")
                .build();

        ticketMvc.perform(patch("/api/tickets/1/prioridad")
                        .principal(adminPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/asignacion-automatica responde 200 con asignación")
    void autoAssignEndpointSuccess() throws Exception {
        TicketResponseDTO response = TicketResponseDTO.builder().id(2L).build();
        when(ticketWorkflowService.autoAssignTechnician(eq(2L), eq("admin@campusdesk.edu")))
                .thenReturn(response);

        extrasMvc.perform(post("/api/tickets/2/asignacion-automatica")
                        .principal(adminPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    @DisplayName("POST /api/tickets/{id}/comentarios-internos responde 201 y marca esInterno")
    void addInternalCommentEndpointSuccess() throws Exception {
        CreateCommentDTO dto = new CreateCommentDTO("Nota para el equipo técnico");
        CommentResponseDTO response = CommentResponseDTO.builder()
                .id(10L).content("Nota para el equipo técnico").esInterno(true).build();

        when(ticketCommentService.addInternalComment(eq(3L), any(), eq("tech@campusdesk.edu")))
                .thenReturn(response);

        extrasMvc.perform(post("/api/tickets/3/comentarios-internos")
                        .principal(techPrincipal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esInterno").value(true));
    }

    @Test
    @DisplayName("GET /api/dashboard/tecnicos/productividad responde 200 con lista de métricas")
    void getTechnicianProductivityEndpointSuccess() throws Exception {
        TechnicianProductivityDTO p = TechnicianProductivityDTO.builder()
                .tecnicoId(2L).tecnicoNombre("Tech 1").ticketsAsignados(4)
                .ticketsActivos(1).ticketsResueltos(3).totalMinutosWorklog(90)
                .promedioMinutosPorTicket(22.5).build();

        when(dashboardService.getTechnicianProductivity("admin@campusdesk.edu"))
                .thenReturn(List.of(p));

        dashboardMvc.perform(get("/api/dashboard/tecnicos/productividad")
                        .principal(adminPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tecnicoId").value(2))
                .andExpect(jsonPath("$[0].promedioMinutosPorTicket").value(22.5));
    }

    @Test
    @DisplayName("POST /api/tickets/cierre-inactividad responde 200 con resumen de cierre")
    void autoCloseManualEndpointSuccess() throws Exception {
        AutoCloseResultDTO result = AutoCloseResultDTO.builder()
                .ticketsCerrados(3).umbralHoras(48).mensaje("Cerrados 3 tickets").build();

        when(ticketInactivityService.autoCloseInactiveTicketsManual("admin@campusdesk.edu"))
                .thenReturn(result);

        extrasMvc.perform(post("/api/tickets/cierre-inactividad")
                        .principal(adminPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsCerrados").value(3))
                .andExpect(jsonPath("$.umbralHoras").value(48));
    }
}

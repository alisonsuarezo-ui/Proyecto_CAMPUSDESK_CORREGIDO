package campusdesk.controller;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.ReopenTicketDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.TicketTimeSummaryDTO;
import campusdesk.dto.TransferTicketDTO;
import campusdesk.dto.WorklogRequestDTO;
import campusdesk.dto.WorklogResponseDTO;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketStatus;
import campusdesk.exception.ApiExceptionHandler;
import campusdesk.service.QuickResponseService;
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
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TicketExtrasControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private TicketWorkflowService ticketWorkflowService;

    @Mock
    private TicketWorklogService ticketWorklogService;

    @Mock
    private QuickResponseService quickResponseService;

    @InjectMocks
    private TicketExtrasController controller;

    private Principal principal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        principal = () -> "test@campusdesk.com";
    }

    // ------------------------------------------------------------ Ejercicio 2

    @Test
    @DisplayName("Ej.2: POST /api/tickets/{id}/reabrir con motivo válido retorna 200 OK")
    void testReopenOk() throws Exception {
        ReopenTicketDTO dto = new ReopenTicketDTO("Sigue sin funcionar el acceso");
        TicketResponseDTO responseDTO = TicketResponseDTO.builder()
                .id(1L)
                .status(TicketStatus.EN_PROCESO)
                .reaperturasCount(1)
                .build();

        when(ticketWorkflowService.reopenTicket(eq(1L), any(ReopenTicketDTO.class), eq("test@campusdesk.com")))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/tickets/1/reabrir")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("EN_PROCESO"))
                .andExpect(jsonPath("$.reaperturasCount").value(1));
    }

    @Test
    @DisplayName("Ej.2: POST /api/tickets/{id}/reabrir con motivo en blanco retorna 400 Bad Request")
    void testReopenBlankMotivoFails() throws Exception {
        ReopenTicketDTO dto = new ReopenTicketDTO("   ");

        mockMvc.perform(post("/api/tickets/1/reabrir")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ej.2: GET /api/tickets/reabiertos retorna lista con 200 OK")
    void testGetReopenedOk() throws Exception {
        TicketResponseDTO responseDTO = TicketResponseDTO.builder()
                .id(1L)
                .reaperturasCount(2)
                .build();

        when(ticketWorkflowService.getReopenedTickets(eq("test@campusdesk.com")))
                .thenReturn(List.of(responseDTO));

        mockMvc.perform(get("/api/tickets/reabiertos")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].reaperturasCount").value(2));
    }

    // ------------------------------------------------------------ Ejercicio 3

    @Test
    @DisplayName("Ej.3: PUT /api/tickets/{id}/transferir con datos válidos retorna 200 OK")
    void testTransferOk() throws Exception {
        TransferTicketDTO dto = new TransferTicketDTO(TicketCategory.SOFTWARE, "Corresponde al área de aplicaciones");
        TicketResponseDTO responseDTO = TicketResponseDTO.builder()
                .id(1L)
                .category(TicketCategory.SOFTWARE)
                .status(TicketStatus.ABIERTA)
                .build();

        when(ticketWorkflowService.transferTicket(eq(1L), any(TransferTicketDTO.class), eq("test@campusdesk.com")))
                .thenReturn(responseDTO);

        mockMvc.perform(put("/api/tickets/1/transferir")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("SOFTWARE"))
                .andExpect(jsonPath("$.status").value("ABIERTA"));
    }

    @Test
    @DisplayName("Ej.3: PUT /api/tickets/{id}/transferir con justificación vacía retorna 400 Bad Request")
    void testTransferEmptyJustificacionFails() throws Exception {
        TransferTicketDTO dto = new TransferTicketDTO(TicketCategory.SOFTWARE, "");

        mockMvc.perform(put("/api/tickets/1/transferir")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ Ejercicio 4

    @Test
    @DisplayName("Ej.4: POST /api/tickets/{id}/worklogs con minutos válidos retorna 201 Created")
    void testAddWorklogOk() throws Exception {
        WorklogRequestDTO dto = new WorklogRequestDTO(45, "Revisión de cableado estructurado");
        WorklogResponseDTO responseDTO = WorklogResponseDTO.builder()
                .id(5L)
                .ticketId(1L)
                .minutosDedicados(45)
                .descripcionActividad("Revisión de cableado estructurado")
                .fechaRegistro(Instant.now())
                .build();

        when(ticketWorklogService.registerWorklog(eq(1L), any(WorklogRequestDTO.class), eq("test@campusdesk.com")))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/tickets/1/worklogs")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.minutosDedicados").value(45));
    }

    @Test
    @DisplayName("Ej.4: POST /api/tickets/{id}/worklogs con minutos fuera de rango (<5) retorna 400 Bad Request")
    void testAddWorklogMinFails() throws Exception {
        WorklogRequestDTO dto = new WorklogRequestDTO(4, "Demasiado breve");

        mockMvc.perform(post("/api/tickets/1/worklogs")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ej.4: GET /api/tickets/{id}/tiempo-total retorna total y registros con 200 OK")
    void testGetTotalTimeOk() throws Exception {
        TicketTimeSummaryDTO summary = TicketTimeSummaryDTO.builder()
                .ticketId(1L)
                .totalMinutos(120L)
                .registros(List.of())
                .build();

        when(ticketWorklogService.getTotalTime(eq(1L), eq("test@campusdesk.com")))
                .thenReturn(summary);

        mockMvc.perform(get("/api/tickets/1/tiempo-total")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(1))
                .andExpect(jsonPath("$.totalMinutos").value(120));
    }

    // ------------------------------------------------------------ Ejercicio 5

    @Test
    @DisplayName("Ej.5: POST /api/tickets/{id}/aplicar-plantilla/{plantillaId} retorna 201 Created")
    void testApplyTemplateOk() throws Exception {
        CommentResponseDTO commentDTO = CommentResponseDTO.builder()
                .id(10L)
                .ticketId(1L)
                .content("Por favor reiniciar el módem.")
                .createdAt(Instant.now())
                .build();

        when(quickResponseService.applyTemplate(eq(1L), eq(7L), eq("test@campusdesk.com")))
                .thenReturn(commentDTO);

        mockMvc.perform(post("/api/tickets/1/aplicar-plantilla/7")
                        .principal(principal))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.content").value("Por favor reiniciar el módem."));
    }
}

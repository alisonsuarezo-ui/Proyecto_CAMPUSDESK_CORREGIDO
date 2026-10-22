package campusdesk.controller;

import campusdesk.dto.AssignTicketDTO;
import campusdesk.dto.ChangeStatusDTO;
import campusdesk.dto.CreateTicketDTO;
import campusdesk.dto.HistoryResponseDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import campusdesk.service.TicketHistoryService;
import campusdesk.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TicketService ticketService;

    @Mock
    private TicketHistoryService ticketHistoryService;

    @InjectMocks
    private TicketController ticketController;

    private Principal principal;
    private TicketResponseDTO sampleTicket;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ticketController).build();
        principal = () -> "superadmin@campusdesk.com";

        sampleTicket = TicketResponseDTO.builder()
                .id(1L)
                .title("Falla de red en laboratorio 3")
                .description("No hay conectividad")
                .status(TicketStatus.ABIERTA)
                .priority(TicketPriority.ALTA)
                .category(TicketCategory.REDES)
                .solicitante(UserResponseDTO.builder().id(1L).name("Carlos").email("superadmin@campusdesk.com").role(Role.ADMIN).build())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("CP-03: POST /api/tickets crea incidencia y retorna 201 Created")
    void testCreateTicket() throws Exception {
        CreateTicketDTO dto = CreateTicketDTO.builder()
                .title("Falla de red en laboratorio 3")
                .description("No hay conectividad en el aula")
                .priority(TicketPriority.ALTA)
                .category(TicketCategory.REDES)
                .build();

        when(ticketService.createTicket(any(CreateTicketDTO.class), eq("superadmin@campusdesk.com")))
                .thenReturn(sampleTicket);

        mockMvc.perform(post("/api/tickets")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ABIERTA"));
    }

    @Test
    @DisplayName("GET /api/tickets retorna lista de incidencias con 200 OK")
    void testGetTickets() throws Exception {
        when(ticketService.getTickets(any(), any(), any(), eq("superadmin@campusdesk.com")))
                .thenReturn(List.of(sampleTicket));

        mockMvc.perform(get("/api/tickets")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Falla de red en laboratorio 3"));
    }

    @Test
    @DisplayName("CP-04: PATCH /api/tickets/{id}/assign asigna técnico y retorna 200 OK")
    void testAssignTechnician() throws Exception {
        AssignTicketDTO dto = new AssignTicketDTO(2L);
        sampleTicket.setStatus(TicketStatus.ASIGNADA);

        when(ticketService.assignTechnician(eq(1L), eq(2L), eq("superadmin@campusdesk.com")))
                .thenReturn(sampleTicket);

        mockMvc.perform(patch("/api/tickets/1/assign")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASIGNADA"));
    }

    @Test
    @DisplayName("CP-06 / CP-07: PATCH /api/tickets/{id}/status actualiza estado y retorna 200 OK")
    void testUpdateStatus() throws Exception {
        ChangeStatusDTO dto = new ChangeStatusDTO(TicketStatus.EN_PROCESO, "Atención iniciada");
        sampleTicket.setStatus(TicketStatus.EN_PROCESO);

        when(ticketService.updateStatus(eq(1L), eq(TicketStatus.EN_PROCESO), eq("Atención iniciada"), eq("superadmin@campusdesk.com")))
                .thenReturn(sampleTicket);

        mockMvc.perform(patch("/api/tickets/1/status")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_PROCESO"));
    }

    @Test
    @DisplayName("GET /api/tickets/{id}/history retorna auditoría cronológica con 200 OK")
    void testGetTicketHistory() throws Exception {
        HistoryResponseDTO historyDTO = HistoryResponseDTO.builder()
                .id(1L)
                .ticketId(1L)
                .estadoAnterior(null)
                .estadoNuevo(TicketStatus.ABIERTA)
                .fechaCambio(Instant.now())
                .comentarioTransicion("Creación")
                .build();

        when(ticketHistoryService.getHistoryByTicketId(eq(1L), eq("superadmin@campusdesk.com")))
                .thenReturn(List.of(historyDTO));

        mockMvc.perform(get("/api/tickets/1/history")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoNuevo").value("ABIERTA"));
    }

    @Test
    @DisplayName("Ejercicio 1: POST /api/tickets/{id}/tags asocia etiquetas y retorna 200 OK")
    void testAddTagsEndpoint() throws Exception {
        sampleTicket.setTags(java.util.Set.of("URGENTE", "REDES"));
        List<String> tagList = List.of("URGENTE", "REDES");

        when(ticketService.addTagsToTicket(eq(1L), any(), eq("superadmin@campusdesk.com")))
                .thenReturn(sampleTicket);

        mockMvc.perform(post("/api/tickets/1/tags")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tagList)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tags").isArray());
    }

    @Test
    @DisplayName("Ejercicio 1: GET /api/tickets/por-tag?nombre={nombre} retorna tickets filtrados con 200 OK")
    void testGetTicketsByTagEndpoint() throws Exception {
        sampleTicket.setTags(java.util.Set.of("URGENTE"));

        when(ticketService.getTicketsByTag(eq("URGENTE"), eq("superadmin@campusdesk.com")))
                .thenReturn(List.of(sampleTicket));

        mockMvc.perform(get("/api/tickets/por-tag")
                        .param("nombre", "URGENTE")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].tags[0]").value("URGENTE"));
    }
}

package campusdesk.controller;

import campusdesk.dto.QuickResponseRequestDTO;
import campusdesk.dto.QuickResponseResponseDTO;
import campusdesk.entity.TicketCategory;
import campusdesk.exception.ApiExceptionHandler;
import campusdesk.exception.DuplicateResourceException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.service.QuickResponseService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuickResponseControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private QuickResponseService quickResponseService;

    @InjectMocks
    private QuickResponseController controller;

    private Principal principal;
    private QuickResponseResponseDTO sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        principal = () -> "tech@campusdesk.com";

        sampleResponse = QuickResponseResponseDTO.builder()
                .id(1L)
                .titulo("Reinicio de router")
                .contenido("Desconectar por 30s")
                .categoria(TicketCategory.REDES)
                .tecnicoId(2L)
                .build();
    }

    @Test
    @DisplayName("Ej.5: POST /api/tecnicos/plantillas crea plantilla y retorna 201 Created")
    void testCreateOk() throws Exception {
        QuickResponseRequestDTO dto = new QuickResponseRequestDTO("Reinicio de router", "Desconectar por 30s", TicketCategory.REDES);

        when(quickResponseService.create(any(QuickResponseRequestDTO.class), eq("tech@campusdesk.com")))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/tecnicos/plantillas")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titulo").value("Reinicio de router"));
    }

    @Test
    @DisplayName("Ej.5: POST /api/tecnicos/plantillas con título duplicado retorna 409 Conflict")
    void testCreateDuplicateConflict() throws Exception {
        QuickResponseRequestDTO dto = new QuickResponseRequestDTO("Reinicio de router", "Desconectar por 30s", TicketCategory.REDES);

        when(quickResponseService.create(any(QuickResponseRequestDTO.class), eq("tech@campusdesk.com")))
                .thenThrow(new DuplicateResourceException("Ya existe una plantilla con este título"));

        mockMvc.perform(post("/api/tecnicos/plantillas")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Ej.5: GET /api/tecnicos/plantillas lista plantillas propias con 200 OK")
    void testListOk() throws Exception {
        when(quickResponseService.listMine(eq("tech@campusdesk.com")))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/tecnicos/plantillas")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Reinicio de router"));
    }

    @Test
    @DisplayName("Ej.5: GET /api/tecnicos/plantillas/{id} consulta plantilla propia con 200 OK")
    void testGetByIdOk() throws Exception {
        when(quickResponseService.getById(eq(1L), eq("tech@campusdesk.com")))
                .thenReturn(sampleResponse);

        mockMvc.perform(get("/api/tecnicos/plantillas/1")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("Ej.5: GET /api/tecnicos/plantillas/{id} de otro técnico retorna 403 Forbidden")
    void testGetByIdOtherForbidden() throws Exception {
        when(quickResponseService.getById(eq(99L), eq("tech@campusdesk.com")))
                .thenThrow(new UnauthorizedTicketActionException("La plantilla no pertenece al técnico autenticado"));

        mockMvc.perform(get("/api/tecnicos/plantillas/99")
                        .principal(principal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Ej.5: PUT /api/tecnicos/plantillas/{id} actualiza plantilla y retorna 200 OK")
    void testUpdateOk() throws Exception {
        QuickResponseRequestDTO dto = new QuickResponseRequestDTO("Reinicio de módem v2", "Nuevo texto", TicketCategory.REDES);
        sampleResponse.setTitulo("Reinicio de módem v2");

        when(quickResponseService.update(eq(1L), any(QuickResponseRequestDTO.class), eq("tech@campusdesk.com")))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/tecnicos/plantillas/1")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Reinicio de módem v2"));
    }

    @Test
    @DisplayName("Ej.5: DELETE /api/tecnicos/plantillas/{id} elimina plantilla y retorna 204 No Content")
    void testDeleteOk() throws Exception {
        doNothing().when(quickResponseService).delete(eq(1L), eq("tech@campusdesk.com"));

        mockMvc.perform(delete("/api/tecnicos/plantillas/1")
                        .principal(principal))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Ej.5: DELETE /api/tecnicos/plantillas/{id} de otro técnico retorna 403 Forbidden")
    void testDeleteOtherForbidden() throws Exception {
        doThrow(new UnauthorizedTicketActionException("La plantilla no pertenece al técnico autenticado"))
                .when(quickResponseService).delete(eq(99L), eq("tech@campusdesk.com"));

        mockMvc.perform(delete("/api/tecnicos/plantillas/99")
                        .principal(principal))
                .andExpect(status().isForbidden());
    }
}

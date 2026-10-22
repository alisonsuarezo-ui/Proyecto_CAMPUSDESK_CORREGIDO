package campusdesk.controller;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;
import campusdesk.service.TicketCommentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TicketCommentService commentService;

    @InjectMocks
    private CommentController commentController;

    private Principal principal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(commentController).build();
        principal = () -> "superadmin@campusdesk.com";
    }

    @Test
    @DisplayName("RF-05: POST /api/tickets/{id}/comments agrega comentario y retorna 201 Created")
    void testAddComment() throws Exception {
        CreateCommentDTO dto = new CreateCommentDTO("Nota técnica de avance");
        CommentResponseDTO responseDTO = CommentResponseDTO.builder()
                .id(1L)
                .ticketId(10L)
                .content("Nota técnica de avance")
                .createdAt(Instant.now())
                .build();

        when(commentService.addComment(eq(10L), any(CreateCommentDTO.class), eq("superadmin@campusdesk.com")))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/tickets/10/comments")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("Nota técnica de avance"));
    }

    @Test
    @DisplayName("RF-05: GET /api/tickets/{id}/comments lista comentarios con 200 OK")
    void testGetComments() throws Exception {
        CommentResponseDTO responseDTO = CommentResponseDTO.builder()
                .id(1L)
                .ticketId(10L)
                .content("Nota técnica de avance")
                .createdAt(Instant.now())
                .build();

        when(commentService.getCommentsByTicketId(eq(10L), eq("superadmin@campusdesk.com")))
                .thenReturn(List.of(responseDTO));

        mockMvc.perform(get("/api/tickets/10/comments")
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Nota técnica de avance"));
    }
}

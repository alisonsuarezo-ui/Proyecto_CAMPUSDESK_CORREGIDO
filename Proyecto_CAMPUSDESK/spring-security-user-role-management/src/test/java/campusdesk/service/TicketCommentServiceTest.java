package campusdesk.service;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketComment;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.TicketClosedException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketCommentRepository;
import campusdesk.repository.TicketRepository;
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

@ExtendWith(MockitoExtension.class)
class TicketCommentServiceTest {

    @Mock
    private TicketCommentRepository commentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TicketCommentServiceImpl commentService;

    private User requester;
    private User technician;
    private User intruder;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(1L).email("user@campusdesk.com").name("Carlos User").role(Role.USER).build();
        technician = User.builder().id(2L).email("tech@campusdesk.com").name("Ana Tech").role(Role.TECHNICIAN).build();
        intruder = User.builder().id(99L).email("intruder@campusdesk.com").name("Intruso").role(Role.USER).build();

        ticket = Ticket.builder()
                .id(10L)
                .title("Problema de conexión")
                .description("Detalles")
                .solicitante(requester)
                .tecnico(technician)
                .status(TicketStatus.EN_PROCESO)
                .build();
    }

    @Test
    @DisplayName("RF-05: El solicitante puede agregar comentario en un ticket activo")
    void testAddCommentByRequester() {
        CreateCommentDTO dto = new CreateCommentDTO("Adjunto más información sobre la falla");

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(commentRepository.save(any(TicketComment.class))).thenAnswer(inv -> {
            TicketComment c = inv.getArgument(0);
            c.setId(100L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CommentResponseDTO response = commentService.addComment(10L, dto, requester.getEmail());

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Adjunto más información sobre la falla", response.getContent());
    }

    @Test
    @DisplayName("RF-05 / RF-08: No se puede agregar comentarios a un ticket CERRADO")
    void testAddCommentToClosedTicketFails() {
        ticket.setStatus(TicketStatus.CERRADA);
        CreateCommentDTO dto = new CreateCommentDTO("Intento de comentar");

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(TicketClosedException.class, () ->
                commentService.addComment(10L, dto, requester.getEmail()));
    }

    @Test
    @DisplayName("RF-05 / RF-08: Usuario ajeno no puede comentar en el ticket")
    void testAddCommentByUnauthorizedUserFails() {
        CreateCommentDTO dto = new CreateCommentDTO("Comentario no autorizado");

        when(userRepository.findByEmail(intruder.getEmail())).thenReturn(Optional.of(intruder));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                commentService.addComment(10L, dto, intruder.getEmail()));
    }

    @Test
    @DisplayName("RF-05: Listar comentarios retorna cronología")
    void testGetCommentsByTicketId() {
        TicketComment comment = TicketComment.builder()
                .id(50L)
                .ticket(ticket)
                .author(technician)
                .content("Revisión en curso")
                .createdAt(Instant.now())
                .build();

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(comment));

        List<CommentResponseDTO> list = commentService.getCommentsByTicketId(10L, technician.getEmail());

        assertEquals(1, list.size());
        assertEquals("Revisión en curso", list.get(0).getContent());
    }
}

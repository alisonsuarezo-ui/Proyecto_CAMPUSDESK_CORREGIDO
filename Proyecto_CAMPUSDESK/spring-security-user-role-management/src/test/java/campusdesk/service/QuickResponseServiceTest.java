package campusdesk.service;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.QuickResponseRequestDTO;
import campusdesk.dto.QuickResponseResponseDTO;
import campusdesk.entity.QuickResponse;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketComment;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.DuplicateResourceException;
import campusdesk.exception.InvalidStateTransitionException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.QuickResponseRepository;
import campusdesk.repository.TicketCommentRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del Ejercicio 5 (plantillas de solución rápida).
 */
@ExtendWith(MockitoExtension.class)
class QuickResponseServiceTest {

    @Mock
    private QuickResponseRepository quickResponseRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketCommentRepository ticketCommentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private QuickResponseServiceImpl service;

    private User requester;
    private User technician;
    private User otherTechnician;
    private Ticket ticket;
    private QuickResponse plantilla;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(1L).email("user@campusdesk.com").name("Carlos User").role(Role.USER).build();
        technician = User.builder().id(2L).email("tech@campusdesk.com").name("Ana Tech").role(Role.TECHNICIAN).build();
        otherTechnician = User.builder().id(5L).email("tech2@campusdesk.com").name("Luis Tech").role(Role.TECHNICIAN).build();

        ticket = Ticket.builder()
                .id(10L)
                .title("Sin internet")
                .description("desc")
                .solicitante(requester)
                .tecnico(technician)
                .status(TicketStatus.EN_PROCESO)
                .priority(TicketPriority.ALTA)
                .category(TicketCategory.REDES)
                .build();

        plantilla = QuickResponse.builder()
                .id(7L)
                .titulo("Reinicio de módem")
                .contenido("Apague el módem 30 segundos y vuelva a encenderlo.")
                .categoria(TicketCategory.REDES)
                .tecnico(technician)
                .build();
    }

    @Test
    @DisplayName("Ej.5: el técnico crea una plantilla propia")
    void createOk() {
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(quickResponseRepository.existsByTecnicoIdAndTituloIgnoreCase(2L, "Limpieza de caché")).thenReturn(false);
        when(quickResponseRepository.saveAndFlush(any(QuickResponse.class))).thenAnswer(inv -> {
            QuickResponse q = inv.getArgument(0);
            q.setId(8L);
            return q;
        });

        QuickResponseResponseDTO dto = service.create(
                new QuickResponseRequestDTO(" Limpieza de caché ", "Ctrl+Shift+Supr", TicketCategory.SOFTWARE), technician.getEmail());

        assertEquals(8L, dto.getId());
        assertEquals("Limpieza de caché", dto.getTitulo());
        assertEquals(2L, dto.getTecnicoId());
    }

    @Test
    @DisplayName("Ej.5: el título debe ser único por técnico (409)")
    void createDuplicateFails() {
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(quickResponseRepository.existsByTecnicoIdAndTituloIgnoreCase(2L, "Reinicio de módem")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.create(
                new QuickResponseRequestDTO("Reinicio de módem", "texto", TicketCategory.REDES), technician.getEmail()));
        verify(quickResponseRepository, never()).saveAndFlush(any(QuickResponse.class));
    }

    @Test
    @DisplayName("Ej.5: un usuario que no es técnico no puede gestionar plantillas (403)")
    void nonTechnicianFails() {
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));

        assertThrows(UnauthorizedTicketActionException.class, () -> service.listMine(requester.getEmail()));
    }

    @Test
    @DisplayName("Ej.5: el técnico solo lista sus propias plantillas")
    void listMine() {
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(quickResponseRepository.findByTecnicoIdOrderByTituloAsc(2L)).thenReturn(List.of(plantilla));

        List<QuickResponseResponseDTO> result = service.listMine(technician.getEmail());

        assertEquals(1, result.size());
        assertEquals("Reinicio de módem", result.get(0).getTitulo());
    }

    @Test
    @DisplayName("Ej.5: no se puede editar ni ver la plantilla de otro técnico (403)")
    void otherTechnicianTemplateFails() {
        when(userRepository.findByEmail(otherTechnician.getEmail())).thenReturn(Optional.of(otherTechnician));
        when(quickResponseRepository.findById(7L)).thenReturn(Optional.of(plantilla));

        assertThrows(UnauthorizedTicketActionException.class, () -> service.getById(7L, otherTechnician.getEmail()));
        assertThrows(UnauthorizedTicketActionException.class, () -> service.delete(7L, otherTechnician.getEmail()));
        verify(quickResponseRepository, never()).delete(any(QuickResponse.class));
    }

    @Test
    @DisplayName("Ej.5: aplicar plantilla publica su contenido como comentario del ticket")
    void applyOk() {
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(quickResponseRepository.findById(7L)).thenReturn(Optional.of(plantilla));
        when(ticketCommentRepository.save(any(TicketComment.class))).thenAnswer(inv -> {
            TicketComment c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        CommentResponseDTO result = service.applyTemplate(10L, 7L, technician.getEmail());

        assertEquals(99L, result.getId());
        assertEquals(10L, result.getTicketId());
        assertEquals(plantilla.getContenido(), result.getContent());

        ArgumentCaptor<TicketComment> captor = ArgumentCaptor.forClass(TicketComment.class);
        verify(ticketCommentRepository).save(captor.capture());
        assertEquals(technician.getId(), captor.getValue().getAuthor().getId());
    }

    @Test
    @DisplayName("Ej.5: no se puede aplicar la plantilla de otro técnico (403)")
    void applyOtherTechnicianTemplateFails() {
        ticket.setTecnico(otherTechnician);
        when(userRepository.findByEmail(otherTechnician.getEmail())).thenReturn(Optional.of(otherTechnician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(quickResponseRepository.findById(7L)).thenReturn(Optional.of(plantilla));

        assertThrows(UnauthorizedTicketActionException.class, () -> service.applyTemplate(10L, 7L, otherTechnician.getEmail()));
        verify(ticketCommentRepository, never()).save(any(TicketComment.class));
    }

    @Test
    @DisplayName("Ej.5: el técnico debe tener asignado el ticket (403)")
    void applyUnassignedTicketFails() {
        ticket.setTecnico(otherTechnician);
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(quickResponseRepository.findById(7L)).thenReturn(Optional.of(plantilla));

        assertThrows(UnauthorizedTicketActionException.class, () -> service.applyTemplate(10L, 7L, technician.getEmail()));
    }

    @Test
    @DisplayName("Ej.5: el ticket debe estar EN_PROCESO (409)")
    void applyWrongStatusFails() {
        ticket.setStatus(TicketStatus.ASIGNADA);
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(quickResponseRepository.findById(7L)).thenReturn(Optional.of(plantilla));

        assertThrows(InvalidStateTransitionException.class, () -> service.applyTemplate(10L, 7L, technician.getEmail()));
    }
}

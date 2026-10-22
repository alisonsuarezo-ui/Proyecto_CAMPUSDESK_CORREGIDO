package campusdesk.service;

import campusdesk.dto.ReopenTicketDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.TransferTicketDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketComment;
import campusdesk.entity.TicketHistory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de los Ejercicios 2 (reapertura) y 3 (transferencia entre áreas).
 */
@ExtendWith(MockitoExtension.class)
class TicketWorkflowServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketHistoryRepository ticketHistoryRepository;

    @Mock
    private TicketCommentRepository ticketCommentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TicketWorkflowServiceImpl service;

    private User requester;
    private User otherUser;
    private User technician;
    private User otherTechnician;
    private User admin;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(1L).email("user@campusdesk.com").name("Carlos User").role(Role.USER).build();
        technician = User.builder().id(2L).email("tech@campusdesk.com").name("Ana Tech").role(Role.TECHNICIAN).build();
        admin = User.builder().id(3L).email("admin@campusdesk.com").name("Super Admin").role(Role.ADMIN).build();
        otherUser = User.builder().id(4L).email("other@campusdesk.com").name("Otro User").role(Role.USER).build();
        otherTechnician = User.builder().id(5L).email("tech2@campusdesk.com").name("Luis Tech").role(Role.TECHNICIAN).build();

        ticket = Ticket.builder()
                .id(10L)
                .title("Falla de acceso")
                .description("No puedo ingresar")
                .solicitante(requester)
                .tecnico(technician)
                .status(TicketStatus.RESUELTA)
                .priority(TicketPriority.ALTA)
                .category(TicketCategory.REDES)
                .build();
    }

    private TicketHistory resolvedHistory(long hoursAgo) {
        return TicketHistory.builder()
                .ticket(ticket)
                .estadoAnterior(TicketStatus.EN_PROCESO)
                .estadoNuevo(TicketStatus.RESUELTA)
                .autor(technician)
                .fechaCambio(Instant.now().minus(hoursAgo, ChronoUnit.HOURS))
                .build();
    }

    // ------------------------------------------------------------------ Ejercicio 2

    @Test
    @DisplayName("Ej.2: el solicitante reabre dentro de 48h -> EN_PROCESO, contador +1 e historial con motivo")
    void reopenWithinWindow() {
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketHistoryRepository.findFirstByTicketIdAndEstadoNuevoOrderByFechaCambioDesc(10L, TicketStatus.RESUELTA))
                .thenReturn(Optional.of(resolvedHistory(47)));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = service.reopenTicket(10L, new ReopenTicketDTO("La solución no funcionó"), requester.getEmail());

        assertEquals(TicketStatus.EN_PROCESO, response.getStatus());
        assertEquals(1, response.getReaperturasCount());

        ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository).save(captor.capture());
        assertEquals(TicketStatus.RESUELTA, captor.getValue().getEstadoAnterior());
        assertEquals(TicketStatus.EN_PROCESO, captor.getValue().getEstadoNuevo());
        assertTrue(captor.getValue().getComentarioTransicion().contains("La solución no funcionó"));
    }

    @Test
    @DisplayName("Ej.2: reabrir pasadas 48h lanza IllegalArgumentException (HTTP 400) y no modifica el ticket")
    void reopenAfterWindowFails() {
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketHistoryRepository.findFirstByTicketIdAndEstadoNuevoOrderByFechaCambioDesc(10L, TicketStatus.RESUELTA))
                .thenReturn(Optional.of(resolvedHistory(49)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reopenTicket(10L, new ReopenTicketDTO("Sigue fallando"), requester.getEmail()));

        assertTrue(ex.getMessage().toLowerCase().contains("nuevo ticket"));
        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(ticketHistoryRepository, never()).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("Ej.2: un usuario que no creó el ticket no puede reabrirlo (403)")
    void reopenByNonCreatorFails() {
        when(userRepository.findByEmail(otherUser.getEmail())).thenReturn(Optional.of(otherUser));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedTicketActionException.class,
                () -> service.reopenTicket(10L, new ReopenTicketDTO("motivo"), otherUser.getEmail()));
    }

    @Test
    @DisplayName("Ej.2: ADMIN y TECHNICIAN no pueden reabrir (solo el USER creador)")
    void reopenByAdminFails() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedTicketActionException.class,
                () -> service.reopenTicket(10L, new ReopenTicketDTO("motivo"), admin.getEmail()));
    }

    @Test
    @DisplayName("Ej.2: solo se reabre desde RESUELTA (409 en otros estados)")
    void reopenWrongStateFails() {
        ticket.setStatus(TicketStatus.EN_PROCESO);
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(InvalidStateTransitionException.class,
                () -> service.reopenTicket(10L, new ReopenTicketDTO("motivo"), requester.getEmail()));
    }

    @Test
    @DisplayName("Ej.2: ADMIN lista tickets reabiertos; otros roles reciben 403")
    void listReopened() {
        ticket.setReaperturasCount(2);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findByReaperturasCountGreaterThanOrderByUpdatedAtDesc(0)).thenReturn(List.of(ticket));

        List<TicketResponseDTO> result = service.getReopenedTickets(admin.getEmail());
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getReaperturasCount());

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        assertThrows(UnauthorizedTicketActionException.class, () -> service.getReopenedTickets(requester.getEmail()));
    }

    // ------------------------------------------------------------------ Ejercicio 3

    @Test
    @DisplayName("Ej.3: ADMIN transfiere -> nueva categoría, técnico null, estado ABIERTA, nota del sistema e historial")
    void transferByAdmin() {
        ticket.setStatus(TicketStatus.EN_PROCESO);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = service.transferTicket(10L,
                new TransferTicketDTO(TicketCategory.ACCESOS, "Es un problema de credenciales"), admin.getEmail());

        assertEquals(TicketCategory.ACCESOS, response.getCategory());
        assertNull(response.getTecnico());
        assertEquals(TicketStatus.ABIERTA, response.getStatus());

        ArgumentCaptor<TicketComment> commentCaptor = ArgumentCaptor.forClass(TicketComment.class);
        verify(ticketCommentRepository).save(commentCaptor.capture());
        assertTrue(commentCaptor.getValue().getContent().contains("[SISTEMA]"));
        assertTrue(commentCaptor.getValue().getContent().contains("Es un problema de credenciales"));

        ArgumentCaptor<TicketHistory> historyCaptor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(ticketHistoryRepository).save(historyCaptor.capture());
        assertEquals(TicketStatus.EN_PROCESO, historyCaptor.getValue().getEstadoAnterior());
        assertEquals(TicketStatus.ABIERTA, historyCaptor.getValue().getEstadoNuevo());
    }

    @Test
    @DisplayName("Ej.3: el técnico asignado también puede transferir")
    void transferByAssignedTechnician() {
        ticket.setStatus(TicketStatus.ASIGNADA);
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = service.transferTicket(10L,
                new TransferTicketDTO(TicketCategory.SOFTWARE, "Corresponde a software"), technician.getEmail());

        assertEquals(TicketCategory.SOFTWARE, response.getCategory());
        assertNull(response.getTecnico());
    }

    @Test
    @DisplayName("Ej.3: un técnico no asignado no puede transferir (403)")
    void transferByOtherTechnicianFails() {
        when(userRepository.findByEmail(otherTechnician.getEmail())).thenReturn(Optional.of(otherTechnician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedTicketActionException.class,
                () -> service.transferTicket(10L, new TransferTicketDTO(TicketCategory.ACCESOS, "x"), otherTechnician.getEmail()));
    }

    @Test
    @DisplayName("Ej.3: transferir a la misma categoría lanza IllegalArgumentException (HTTP 400)")
    void transferSameCategoryFails() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalArgumentException.class,
                () -> service.transferTicket(10L, new TransferTicketDTO(TicketCategory.REDES, "x"), admin.getEmail()));
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("Ej.3: un ticket CERRADO no puede transferirse (409)")
    void transferClosedFails() {
        ticket.setStatus(TicketStatus.CERRADA);
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThrows(TicketClosedException.class,
                () -> service.transferTicket(10L, new TransferTicketDTO(TicketCategory.ACCESOS, "x"), admin.getEmail()));
    }
}

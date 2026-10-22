package campusdesk.service;

import campusdesk.dto.CreateTicketDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketHistory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.User;
import campusdesk.exception.InvalidStateTransitionException;
import campusdesk.exception.TicketClosedException;
import campusdesk.exception.TicketNotFoundException;
import campusdesk.exception.UnauthorizedTicketActionException;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketHistoryRepository ticketHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private campusdesk.repository.TagRepository tagRepository;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private User requester;
    private User technician;
    private User admin;
    private User otherUser;
    private Ticket ticketAbierta;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(1L).email("user@campusdesk.com").name("Carlos User").role(Role.USER).build();
        technician = User.builder().id(2L).email("tech@campusdesk.com").name("Ana Tech").role(Role.TECHNICIAN).build();
        admin = User.builder().id(3L).email("admin@campusdesk.com").name("Super Admin").role(Role.ADMIN).build();
        otherUser = User.builder().id(4L).email("other@campusdesk.com").name("Otro User").role(Role.USER).build();

        ticketAbierta = Ticket.builder()
                .id(10L)
                .title("Falla de red en laboratorio 3")
                .description("No hay conectividad a Internet")
                .solicitante(requester)
                .tecnico(null)
                .status(TicketStatus.ABIERTA)
                .priority(TicketPriority.ALTA)
                .category(TicketCategory.REDES)
                .build();
    }

    @Test
    @DisplayName("CP-03 / RF-03: Crear ticket inicia estrictamente en ABIERTA y genera auditoría")
    void testCreateTicketSuccess() {
        CreateTicketDTO dto = CreateTicketDTO.builder()
                .title("Falla de red en laboratorio 3")
                .description("No hay conectividad a Internet")
                .priority(TicketPriority.ALTA)
                .category(TicketCategory.REDES)
                .build();

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(10L);
            return t;
        });

        TicketResponseDTO response = ticketService.createTicket(dto, requester.getEmail());

        assertNotNull(response);
        assertEquals(TicketStatus.ABIERTA, response.getStatus());
        assertEquals("Falla de red en laboratorio 3", response.getTitle());
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("RF-04: Admin asigna técnico con éxito transicionando a ASIGNADA")
    void testAssignTechnicianByAdmin() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(userRepository.findById(technician.getId())).thenReturn(Optional.of(technician));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.assignTechnician(10L, technician.getId(), admin.getEmail());

        assertNotNull(response);
        assertEquals(TicketStatus.ASIGNADA, response.getStatus());
        assertEquals(technician.getId(), response.getTecnico().getId());
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("RF-04: Solo ADMIN puede asignar técnicos; USER genera UnauthorizedTicketActionException")
    void testAssignTechnicianUnauthorized() {
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.assignTechnician(10L, technician.getId(), requester.getEmail()));
    }

    @Test
    @DisplayName("RF-04: Transición a EN_PROCESO permitida para el técnico asignado")
    void testUpdateStatusToEnProcesoSuccess() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.updateStatus(10L, TicketStatus.EN_PROCESO, "Iniciando diagnóstico", technician.getEmail());

        assertEquals(TicketStatus.EN_PROCESO, response.getStatus());
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("RF-04: Transición a EN_PROCESO rechazada si el ticket no está en ASIGNADA")
    void testUpdateStatusToEnProcesoInvalidState() {
        // Ticket todavía en ABIERTA
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.EN_PROCESO, "Iniciando", technician.getEmail()));
    }

    @Test
    @DisplayName("RF-04: Transición a RESUELTA solo si estuvo EN_PROCESO y ejecutada por el técnico asignado")
    void testUpdateStatusToResueltaSuccess() {
        ticketAbierta.setStatus(TicketStatus.EN_PROCESO);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.updateStatus(10L, TicketStatus.RESUELTA, "Cable reemplazado y probado", technician.getEmail());

        assertEquals(TicketStatus.RESUELTA, response.getStatus());
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("RF-04: Transición a RESUELTA rechazada si intenta saltarse de ASIGNADA a RESUELTA directamente")
    void testUpdateStatusToResueltaSkipStateFails() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.RESUELTA, "Directo a resuelta", technician.getEmail()));
    }

    @Test
    @DisplayName("RF-04: Transición a CERRADA permitida al solicitante cuando el ticket está RESUELTA")
    void testUpdateStatusToCerradaByRequesterSuccess() {
        ticketAbierta.setStatus(TicketStatus.RESUELTA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.updateStatus(10L, TicketStatus.CERRADA, "Conforme con la solución", requester.getEmail());

        assertEquals(TicketStatus.CERRADA, response.getStatus());
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("RF-04: Transición a CERRADA rechazada si el ticket no está RESUELTA (ej. aún EN_PROCESO)")
    void testUpdateStatusToCerradaPrematureFails() {
        ticketAbierta.setStatus(TicketStatus.EN_PROCESO);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.CERRADA, "Cerrar ya", requester.getEmail()));
    }

    @Test
    @DisplayName("RF-04: Transición a CERRADA rechazada si otro usuario no solicitante intenta cerrarla")
    void testUpdateStatusToCerradaUnauthorizedUser() {
        ticketAbierta.setStatus(TicketStatus.RESUELTA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(otherUser.getEmail())).thenReturn(Optional.of(otherUser));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.CERRADA, "Intruso cerrando", otherUser.getEmail()));
    }

    @Test
    @DisplayName("RF-04 / RF-08: Ticket en estado CERRADA es estrictamente inmutable")
    void testTicketClosedIsImmutable() {
        ticketAbierta.setStatus(TicketStatus.CERRADA);

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(TicketClosedException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.EN_PROCESO, "Reabrir intento", admin.getEmail()));
    }

    @Test
    @DisplayName("RF-08: Usuario común no puede ver ticket de otro usuario")
    void testGetTicketByIdForbiddenForOtherUser() {
        when(userRepository.findByEmail(otherUser.getEmail())).thenReturn(Optional.of(otherUser));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.getTicketById(10L, otherUser.getEmail()));
    }

    // =========================================================================
    // CASOS DE PRUEBA ADICIONALES (AUDITORÍA & ENTREGA: E1, E2, E3, F1)
    // =========================================================================

    @Test
    @DisplayName("E1: Reasignar un ticket EN_PROCESO devuelve 409 (InvalidStateTransitionException)")
    void testReassignEnProcesoThrowsConflict() {
        ticketAbierta.setStatus(TicketStatus.EN_PROCESO);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.assignTechnician(10L, technician.getId(), admin.getEmail()));
    }

    @Test
    @DisplayName("E1: Reasignar un ticket RESUELTA devuelve 409 (InvalidStateTransitionException)")
    void testReassignResueltaThrowsConflict() {
        ticketAbierta.setStatus(TicketStatus.RESUELTA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.assignTechnician(10L, technician.getId(), admin.getEmail()));
    }

    @Test
    @DisplayName("E1: Reasignar un ticket CERRADA devuelve 409 (TicketClosedException)")
    void testReassignCerradaThrowsConflict() {
        ticketAbierta.setStatus(TicketStatus.CERRADA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(TicketClosedException.class, () ->
                ticketService.assignTechnician(10L, technician.getId(), admin.getEmail()));
    }

    @Test
    @DisplayName("E1: Reasignar un ticket ASIGNADA a otro técnico es válido y se registra en auditoría")
    void testReassignAsignadaSuccess() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        User secondTech = User.builder().id(5L).email("tech2@campusdesk.com").name("Carlos Tech").role(Role.TECHNICIAN).build();

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(userRepository.findById(5L)).thenReturn(Optional.of(secondTech));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.assignTechnician(10L, 5L, admin.getEmail());

        assertNotNull(response);
        assertEquals(TicketStatus.ASIGNADA, response.getStatus());
        assertEquals(5L, response.getTecnico().getId());
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("E2: Administrador no puede saltarse las reglas de cambio de estado a EN_PROCESO")
    void testAdminCannotBypassToEnProceso() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.EN_PROCESO, "Admin intentando iniciar", admin.getEmail()));
    }

    @Test
    @DisplayName("E2: Administrador no puede saltarse las reglas de cambio de estado a RESUELTA")
    void testAdminCannotBypassToResuelta() {
        ticketAbierta.setStatus(TicketStatus.EN_PROCESO);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.RESUELTA, "Admin intentando resolver", admin.getEmail()));
    }

    @Test
    @DisplayName("E2: Administrador no puede saltarse las reglas de cambio de estado a CERRADA")
    void testAdminCannotBypassToCerrada() {
        ticketAbierta.setStatus(TicketStatus.RESUELTA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.CERRADA, "Admin intentando cerrar", admin.getEmail()));
    }

    @Test
    @DisplayName("E2: Intentar transicionar al mismo estado devuelve 409 (InvalidStateTransitionException)")
    void testTransitionToSameStateFails() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateStatus(10L, TicketStatus.ASIGNADA, "Mismo estado", requester.getEmail()));
    }

    @Test
    @DisplayName("E3: Un técnico no puede consultar una incidencia que no tiene asignada (403)")
    void testTechnicianCannotViewUnassignedTicket() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        User otherTech = User.builder().id(9L).email("othertech@campusdesk.com").role(Role.TECHNICIAN).build();
        when(userRepository.findByEmail(otherTech.getEmail())).thenReturn(Optional.of(otherTech));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.getTicketById(10L, otherTech.getEmail()));
    }

    @Test
    @DisplayName("E3: Un técnico asignado sí puede consultar su incidencia (200)")
    void testTechnicianCanViewAssignedTicket() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        TicketResponseDTO result = ticketService.getTicketById(10L, technician.getEmail());
        assertNotNull(result);
        assertEquals(10L, result.getId());
    }

    @Test
    @DisplayName("F1: Solicitante puede editar su incidencia ABIERTA sin técnico asignado")
    void testUserCanEditOwnOpenUnassignedTicket() {
        campusdesk.dto.UpdateTicketDTO dto = campusdesk.dto.UpdateTicketDTO.builder()
                .title("Título Editado")
                .description("Descripción modificada con mayor detalle")
                .priority(TicketPriority.CRITICA)
                .category(TicketCategory.SOFTWARE)
                .build();

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.updateTicket(10L, dto, requester.getEmail());

        assertNotNull(response);
        assertEquals("Título Editado", response.getTitle());
        assertEquals(TicketPriority.CRITICA, response.getPriority());
        assertEquals(TicketCategory.SOFTWARE, response.getCategory());
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("F1: Otro usuario no puede editar la incidencia del solicitante (403)")
    void testOtherUserCannotEditTicket() {
        campusdesk.dto.UpdateTicketDTO dto = campusdesk.dto.UpdateTicketDTO.builder()
                .title("Intento de Hack")
                .description("Intruso modificando")
                .priority(TicketPriority.BAJA)
                .category(TicketCategory.OTROS)
                .build();

        when(userRepository.findByEmail(otherUser.getEmail())).thenReturn(Optional.of(otherUser));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.updateTicket(10L, dto, otherUser.getEmail()));
    }

    @Test
    @DisplayName("F1: No se puede editar una incidencia que ya tiene técnico asignado (409)")
    void testCannotEditAssignedTicket() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        campusdesk.dto.UpdateTicketDTO dto = campusdesk.dto.UpdateTicketDTO.builder()
                .title("Título Editado")
                .description("Modificación tardía")
                .priority(TicketPriority.MEDIA)
                .category(TicketCategory.REDES)
                .build();

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateTicket(10L, dto, requester.getEmail()));
    }

    @Test
    @DisplayName("F1: Administrador puede editar una incidencia existente")
    void testAdminCanEditTicket() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        campusdesk.dto.UpdateTicketDTO dto = campusdesk.dto.UpdateTicketDTO.builder()
                .title("Título Editado por Admin")
                .description("Descripción modificada por el administrador")
                .priority(TicketPriority.ALTA)
                .category(TicketCategory.HARDWARE)
                .build();

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.updateTicket(10L, dto, admin.getEmail());

        assertNotNull(response);
        assertEquals("Título Editado por Admin", response.getTitle());
        verify(ticketHistoryRepository, times(1)).save(any(TicketHistory.class));
    }

    @Test
    @DisplayName("F1: Actualizar incidencia con transición inválida de estado es rechazada")
    void testUpdateTicketInvalidStatusTransition() {
        campusdesk.dto.UpdateTicketDTO dto = campusdesk.dto.UpdateTicketDTO.builder()
                .title("Intento de Salto de Estado")
                .description("Intentando pasar a CERRADA directo")
                .priority(TicketPriority.MEDIA)
                .category(TicketCategory.REDES)
                .status(TicketStatus.CERRADA)
                .build();

        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(InvalidStateTransitionException.class, () ->
                ticketService.updateTicket(10L, dto, requester.getEmail()));
    }

    // =========================================================================
    // EJERCICIO 1: PRUEBAS DEL SISTEMA DE ETIQUETAS (TAGS)
    // =========================================================================

    @Test
    @DisplayName("Ejercicio 1: ADMIN asocia etiquetas normalizadas a mayúsculas y crea nuevas si no existen")
    void testAddTagsByAdminSuccess() {
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(tagRepository.findByName("URGENTE")).thenReturn(Optional.empty());
        when(tagRepository.save(any(campusdesk.entity.Tag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.addTagsToTicket(10L, java.util.List.of("urgente"), admin.getEmail());

        assertNotNull(response);
        assertNotNull(response.getTags());
        assertTrue(response.getTags().contains("URGENTE"));
        verify(tagRepository, times(1)).save(any(campusdesk.entity.Tag.class));
        verify(ticketRepository, times(1)).save(any(Ticket.class));
    }

    @Test
    @DisplayName("Ejercicio 1: Reutiliza etiquetas existentes sin duplicarlas en base de datos")
    void testAddTagsReusesExistingTag() {
        campusdesk.entity.Tag existingTag = campusdesk.entity.Tag.builder().id(1L).name("REDES").build();

        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(tagRepository.findByName("REDES")).thenReturn(Optional.of(existingTag));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.addTagsToTicket(10L, java.util.List.of("redes", "REDES"), admin.getEmail());

        assertNotNull(response);
        assertTrue(response.getTags().contains("REDES"));
        assertEquals(1, response.getTags().size());
        verify(tagRepository, never()).save(any(campusdesk.entity.Tag.class));
    }

    @Test
    @DisplayName("Ejercicio 1: TECHNICIAN asignado asocia etiquetas exitosamente")
    void testAddTagsByAssignedTechnicianSuccess() {
        ticketAbierta.setStatus(TicketStatus.ASIGNADA);
        ticketAbierta.setTecnico(technician);

        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));
        when(tagRepository.findByName("VPN")).thenReturn(Optional.empty());
        when(tagRepository.save(any(campusdesk.entity.Tag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO response = ticketService.addTagsToTicket(10L, java.util.List.of("vpn"), technician.getEmail());

        assertNotNull(response);
        assertTrue(response.getTags().contains("VPN"));
    }

    @Test
    @DisplayName("Ejercicio 1: TECHNICIAN NO asignado es rechazado con 403 Forbidden")
    void testAddTagsByUnassignedTechnicianForbidden() {
        User otherTech = User.builder().id(99L).email("othertech@campusdesk.com").role(Role.TECHNICIAN).build();
        ticketAbierta.setTecnico(technician); // Técnico asignado es ID 2

        when(userRepository.findByEmail(otherTech.getEmail())).thenReturn(Optional.of(otherTech));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.addTagsToTicket(10L, java.util.List.of("SEGURIDAD"), otherTech.getEmail()));
    }

    @Test
    @DisplayName("Ejercicio 1: Usuario común (USER) es rechazado al intentar asociar etiquetas (403)")
    void testAddTagsByUserForbidden() {
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticketAbierta));

        assertThrows(UnauthorizedTicketActionException.class, () ->
                ticketService.addTagsToTicket(10L, java.util.List.of("IMPRESORA"), requester.getEmail()));
    }

    @Test
    @DisplayName("Ejercicio 1: Filtrar tickets por etiqueta respeta visibilidad por rol")
    void testGetTicketsByTagRespectsRole() {
        // ADMIN ve todo
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(ticketRepository.findByTagName("URGENTE", null, null))
                .thenReturn(java.util.List.of(ticketAbierta));

        java.util.List<TicketResponseDTO> adminResults = ticketService.getTicketsByTag("urgente", admin.getEmail());
        assertEquals(1, adminResults.size());

        // USER solo ve sus tickets
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
        when(ticketRepository.findByTagName("URGENTE", requester.getId(), null))
                .thenReturn(java.util.List.of(ticketAbierta));

        java.util.List<TicketResponseDTO> userResults = ticketService.getTicketsByTag("URGENTE", requester.getEmail());
        assertEquals(1, userResults.size());

        // TECHNICIAN solo ve asignados
        when(userRepository.findByEmail(technician.getEmail())).thenReturn(Optional.of(technician));
        when(ticketRepository.findByTagName("URGENTE", null, technician.getId()))
                .thenReturn(java.util.List.of());

        java.util.List<TicketResponseDTO> techResults = ticketService.getTicketsByTag("urgente", technician.getEmail());
        assertEquals(0, techResults.size());
    }
}

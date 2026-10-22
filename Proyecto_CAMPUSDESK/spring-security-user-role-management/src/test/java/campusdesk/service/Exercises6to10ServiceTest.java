package campusdesk.service;

import campusdesk.dto.AutoCloseResultDTO;
import campusdesk.dto.ChangePriorityDTO;
import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;
import campusdesk.dto.TechnicianProductivityDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Ticket;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketComment;
import campusdesk.entity.TicketHistory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import campusdesk.entity.TicketWorklog;
import campusdesk.entity.User;
import campusdesk.exception.InvalidStateTransitionException;
import campusdesk.exception.TicketClosedException;
import campusdesk.exception.TicketNotFoundException;
import campusdesk.exception.UnauthorizedTicketActionException;
import campusdesk.repository.TicketCommentRepository;
import campusdesk.repository.TicketHistoryRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.TicketWorklogRepository;
import campusdesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias completas para las reglas de negocio de los Ejercicios 6 al 10.
 */
@ExtendWith(MockitoExtension.class)
class Exercises6to10ServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketHistoryRepository ticketHistoryRepository;
    @Mock
    private TicketCommentRepository ticketCommentRepository;
    @Mock
    private TicketWorklogRepository ticketWorklogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private campusdesk.repository.TagRepository tagRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private User admin;
    private User tech1;
    private User tech2;
    private User requester;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(1L).name("Admin").email("admin@campusdesk.edu").role(Role.ADMIN).build();
        tech1 = User.builder().id(2L).name("Tecnico 1").email("tech1@campusdesk.edu").role(Role.TECHNICIAN).build();
        tech2 = User.builder().id(3L).name("Tecnico 2").email("tech2@campusdesk.edu").role(Role.TECHNICIAN).build();
        requester = User.builder().id(4L).name("Usuario").email("user@campusdesk.edu").role(Role.USER).build();
    }

    // =========================================================================
    // EJERCICIO 6: CAMBIO DE PRIORIDAD CON AUDITORÍA
    // =========================================================================
    @Nested
    @DisplayName("Ejercicio 6 - Cambio de Prioridad")
    class Ejercicio6Tests {

        private TicketServiceImpl ticketService;

        @BeforeEach
        void initService() {
            ticketService = new TicketServiceImpl(ticketRepository, ticketHistoryRepository, userRepository, tagRepository);
        }

        @Test
        @DisplayName("ADMIN puede cambiar prioridad y se registra en el historial con motivo")
        void adminCanChangePriority() {
            Ticket ticket = Ticket.builder()
                    .id(10L).title("Ticket Test").description("Desc").status(TicketStatus.ABIERTA)
                    .priority(TicketPriority.BAJA).category(TicketCategory.SOFTWARE).solicitante(requester).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

            ChangePriorityDTO dto = ChangePriorityDTO.builder()
                    .priority(TicketPriority.ALTA)
                    .motivo("Afecta a múltiples usuarios en producción")
                    .build();

            TicketResponseDTO result = ticketService.updatePriority(10L, dto, "admin@campusdesk.edu");

            assertThat(result.getPriority()).isEqualTo(TicketPriority.ALTA);
            assertThat(ticket.getPriority()).isEqualTo(TicketPriority.ALTA);

            ArgumentCaptor<TicketHistory> captor = ArgumentCaptor.forClass(TicketHistory.class);
            verify(ticketHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getComentarioTransicion())
                    .contains("BAJA a ALTA")
                    .contains("Afecta a múltiples usuarios en producción");
        }

        @Test
        @DisplayName("TECHNICIAN asignado puede cambiar prioridad")
        void assignedTechnicianCanChangePriority() {
            Ticket ticket = Ticket.builder()
                    .id(11L).title("Ticket Asignado").status(TicketStatus.EN_PROCESO)
                    .priority(TicketPriority.MEDIA).category(TicketCategory.REDES)
                    .solicitante(requester).tecnico(tech1).build();

            when(userRepository.findByEmail("tech1@campusdesk.edu")).thenReturn(Optional.of(tech1));
            when(ticketRepository.findById(11L)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

            ChangePriorityDTO dto = ChangePriorityDTO.builder()
                    .priority(TicketPriority.CRITICA)
                    .motivo("Corte total de conectividad en campus")
                    .build();

            TicketResponseDTO result = ticketService.updatePriority(11L, dto, "tech1@campusdesk.edu");

            assertThat(result.getPriority()).isEqualTo(TicketPriority.CRITICA);
            verify(ticketRepository).save(ticket);
            verify(ticketHistoryRepository).save(any(TicketHistory.class));
        }

        @Test
        @DisplayName("Rechaza con 400 si la prioridad solicitada es idéntica a la actual")
        void rejectsSamePriorityWith400() {
            Ticket ticket = Ticket.builder()
                    .id(12L).status(TicketStatus.ABIERTA).priority(TicketPriority.MEDIA)
                    .solicitante(requester).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findById(12L)).thenReturn(Optional.of(ticket));

            ChangePriorityDTO dto = ChangePriorityDTO.builder()
                    .priority(TicketPriority.MEDIA)
                    .motivo("Misma prioridad")
                    .build();

            assertThatThrownBy(() -> ticketService.updatePriority(12L, dto, "admin@campusdesk.edu"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("prioridad actual");
        }

        @Test
        @DisplayName("Rechaza con 403 si un USER intenta cambiar la prioridad")
        void rejectsUserWith403() {
            Ticket ticket = Ticket.builder()
                    .id(13L).status(TicketStatus.ABIERTA).priority(TicketPriority.BAJA)
                    .solicitante(requester).build();

            when(userRepository.findByEmail("user@campusdesk.edu")).thenReturn(Optional.of(requester));
            when(ticketRepository.findById(13L)).thenReturn(Optional.of(ticket));

            ChangePriorityDTO dto = ChangePriorityDTO.builder()
                    .priority(TicketPriority.ALTA)
                    .motivo("Quiero prioridad alta")
                    .build();

            assertThatThrownBy(() -> ticketService.updatePriority(13L, dto, "user@campusdesk.edu"))
                    .isInstanceOf(UnauthorizedTicketActionException.class);
        }

        @Test
        @DisplayName("Rechaza con 409 si el ticket está en estado CERRADA")
        void rejectsClosedTicketWith409() {
            Ticket ticket = Ticket.builder()
                    .id(14L).status(TicketStatus.CERRADA).priority(TicketPriority.BAJA)
                    .solicitante(requester).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findById(14L)).thenReturn(Optional.of(ticket));

            ChangePriorityDTO dto = ChangePriorityDTO.builder()
                    .priority(TicketPriority.ALTA)
                    .motivo("Intento cambiar cerrado")
                    .build();

            assertThatThrownBy(() -> ticketService.updatePriority(14L, dto, "admin@campusdesk.edu"))
                    .isInstanceOf(TicketClosedException.class);
        }
    }

    // =========================================================================
    // EJERCICIO 7: ASIGNACIÓN AUTOMÁTICA DE TÉCNICOS
    // =========================================================================
    @Nested
    @DisplayName("Ejercicio 7 - Asignación Automática")
    class Ejercicio7Tests {

        private TicketWorkflowServiceImpl workflowService;

        @BeforeEach
        void initService() {
            workflowService = new TicketWorkflowServiceImpl(ticketRepository, ticketHistoryRepository, ticketCommentRepository, userRepository);
        }

        @Test
        @DisplayName("Asigna al técnico con menor carga de tickets activos y pasa de ABIERTA a ASIGNADA")
        void assignsTechnicianWithLowestActiveLoad() {
            Ticket ticket = Ticket.builder()
                    .id(20L).title("Ticket a asignar").status(TicketStatus.ABIERTA)
                    .category(TicketCategory.SOFTWARE).solicitante(requester).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findById(20L)).thenReturn(Optional.of(ticket));
            when(userRepository.findByRole(Role.TECHNICIAN)).thenReturn(List.of(tech1, tech2));

            // tech1 tiene 4 tickets activos, tech2 tiene 1 ticket activo
            when(ticketRepository.countActiveTicketsByTecnicoIdExcludingTicket(eq(2L), any(), eq(20L))).thenReturn(4L);
            when(ticketRepository.countActiveTicketsByTecnicoIdExcludingTicket(eq(3L), any(), eq(20L))).thenReturn(1L);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

            TicketResponseDTO response = workflowService.autoAssignTechnician(20L, "admin@campusdesk.edu");

            assertThat(response.getTecnico()).isNotNull();
            assertThat(response.getTecnico().getId()).isEqualTo(3L);
            assertThat(response.getStatus()).isEqualTo(TicketStatus.ASIGNADA);
            assertThat(ticket.getTecnico()).isEqualTo(tech2);

            verify(ticketHistoryRepository).save(any(TicketHistory.class));
            verify(ticketCommentRepository).save(any(TicketComment.class));
        }

        @Test
        @DisplayName("Desempate determinista por ID ascendente en caso de igualdad de carga")
        void tieBreaksByLowestTechnicianId() {
            Ticket ticket = Ticket.builder()
                    .id(21L).title("Ticket empate").status(TicketStatus.ABIERTA)
                    .category(TicketCategory.HARDWARE).solicitante(requester).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findById(21L)).thenReturn(Optional.of(ticket));
            when(userRepository.findByRole(Role.TECHNICIAN)).thenReturn(List.of(tech2, tech1)); // Desordenados

            // Ambos técnicos tienen 2 tickets activos
            when(ticketRepository.countActiveTicketsByTecnicoIdExcludingTicket(eq(2L), any(), eq(21L))).thenReturn(2L);
            when(ticketRepository.countActiveTicketsByTecnicoIdExcludingTicket(eq(3L), any(), eq(21L))).thenReturn(2L);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

            TicketResponseDTO response = workflowService.autoAssignTechnician(21L, "admin@campusdesk.edu");

            // tech1 tiene ID 2, tech2 tiene ID 3 -> debe elegir tech1 (menor ID)
            assertThat(response.getTecnico().getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Rechaza asignación si el ticket está en estado final CERRADA o RESUELTA")
        void rejectsFinalStateWith409() {
            Ticket ticketCerrado = Ticket.builder()
                    .id(22L).status(TicketStatus.CERRADA).solicitante(requester).build();
            Ticket ticketResuelto = Ticket.builder()
                    .id(23L).status(TicketStatus.RESUELTA).solicitante(requester).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findById(22L)).thenReturn(Optional.of(ticketCerrado));
            when(ticketRepository.findById(23L)).thenReturn(Optional.of(ticketResuelto));

            assertThatThrownBy(() -> workflowService.autoAssignTechnician(22L, "admin@campusdesk.edu"))
                    .isInstanceOf(TicketClosedException.class);

            assertThatThrownBy(() -> workflowService.autoAssignTechnician(23L, "admin@campusdesk.edu"))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("RESUELTA");
        }

        @Test
        @DisplayName("Rechaza asignación si un no-ADMIN intenta ejecutarla")
        void rejectsNonAdminWith403() {
            when(userRepository.findByEmail("tech1@campusdesk.edu")).thenReturn(Optional.of(tech1));

            assertThatThrownBy(() -> workflowService.autoAssignTechnician(23L, "tech1@campusdesk.edu"))
                    .isInstanceOf(UnauthorizedTicketActionException.class);
        }
    }

    // =========================================================================
    // EJERCICIO 8: COMENTARIOS INTERNOS CON CONTROL DE VISIBILIDAD
    // =========================================================================
    @Nested
    @DisplayName("Ejercicio 8 - Comentarios Internos")
    class Ejercicio8Tests {

        private TicketCommentServiceImpl commentService;

        @BeforeEach
        void initService() {
            commentService = new TicketCommentServiceImpl(ticketCommentRepository, ticketRepository, userRepository);
        }

        @Test
        @DisplayName("ADMIN puede agregar una nota interna con esInterno = true")
        void adminCanAddInternalComment() {
            Ticket ticket = Ticket.builder().id(30L).status(TicketStatus.EN_PROCESO).solicitante(requester).tecnico(tech1).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findById(30L)).thenReturn(Optional.of(ticket));
            when(ticketCommentRepository.save(any(TicketComment.class))).thenAnswer(i -> {
                TicketComment c = i.getArgument(0);
                c.setId(100L);
                return c;
            });

            CreateCommentDTO dto = new CreateCommentDTO("Nota confidencial de diagnóstico de red");
            CommentResponseDTO res = commentService.addInternalComment(30L, dto, "admin@campusdesk.edu");

            assertThat(res.getEsInterno()).isTrue();
            assertThat(res.getContent()).isEqualTo("Nota confidencial de diagnóstico de red");
        }

        @Test
        @DisplayName("Rechaza a un usuario con rol USER al intentar crear una nota interna")
        void rejectsUserFromCreatingInternalComment() {
            Ticket ticket = Ticket.builder().id(31L).status(TicketStatus.EN_PROCESO).solicitante(requester).build();

            when(userRepository.findByEmail("user@campusdesk.edu")).thenReturn(Optional.of(requester));
            when(ticketRepository.findById(31L)).thenReturn(Optional.of(ticket));

            CreateCommentDTO dto = new CreateCommentDTO("Intento de nota interna");
            assertThatThrownBy(() -> commentService.addInternalComment(31L, dto, "user@campusdesk.edu"))
                    .isInstanceOf(UnauthorizedTicketActionException.class);
        }

        @Test
        @DisplayName("Filtrado estricto: USER solo recibe comentarios públicos al consultar el ticket")
        void userOnlyReceivesPublicComments() {
            Ticket ticket = Ticket.builder().id(32L).status(TicketStatus.EN_PROCESO).solicitante(requester).build();

            TicketComment publicComment = TicketComment.builder()
                    .id(1L).content("Comentario público").esInterno(false).author(tech1).ticket(ticket).build();

            when(userRepository.findByEmail("user@campusdesk.edu")).thenReturn(Optional.of(requester));
            when(ticketRepository.findById(32L)).thenReturn(Optional.of(ticket));
            when(ticketCommentRepository.findPublicByTicketIdOrderByCreatedAtAsc(32L)).thenReturn(List.of(publicComment));

            List<CommentResponseDTO> comments = commentService.getCommentsByTicketId(32L, "user@campusdesk.edu");

            assertThat(comments).hasSize(1);
            assertThat(comments.get(0).getContent()).isEqualTo("Comentario público");
            assertThat(comments.get(0).getEsInterno()).isFalse();
            verify(ticketCommentRepository).findPublicByTicketIdOrderByCreatedAtAsc(32L);
            verify(ticketCommentRepository, never()).findByTicketIdOrderByCreatedAtAsc(32L);
        }

        @Test
        @DisplayName("ADMIN recibe todos los comentarios (públicos e internos)")
        void adminReceivesAllComments() {
            Ticket ticket = Ticket.builder().id(33L).status(TicketStatus.EN_PROCESO).solicitante(requester).tecnico(tech1).build();

            TicketComment publicComment = TicketComment.builder()
                    .id(1L).content("Público").esInterno(false).author(tech1).ticket(ticket).build();
            TicketComment internalComment = TicketComment.builder()
                    .id(2L).content("Interno").esInterno(true).author(tech1).ticket(ticket).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findById(33L)).thenReturn(Optional.of(ticket));
            when(ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(33L)).thenReturn(List.of(publicComment, internalComment));

            List<CommentResponseDTO> comments = commentService.getCommentsByTicketId(33L, "admin@campusdesk.edu");

            assertThat(comments).hasSize(2);
            verify(ticketCommentRepository).findByTicketIdOrderByCreatedAtAsc(33L);
        }
    }

    // =========================================================================
    // EJERCICIO 9: DASHBOARD DE PRODUCTIVIDAD POR TÉCNICO
    // =========================================================================
    @Nested
    @DisplayName("Ejercicio 9 - Productividad de Técnicos")
    class Ejercicio9Tests {

        private DashboardServiceImpl dashboardService;

        @BeforeEach
        void initService() {
            dashboardService = new DashboardServiceImpl(ticketRepository, userRepository, ticketWorklogRepository);
        }

        @Test
        @DisplayName("Calcula correctamente métricas independientes sin duplicados ni división por cero")
        void calculatesProductivityMetricsCorrectly() {
            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(userRepository.findByRole(Role.TECHNICIAN)).thenReturn(List.of(tech1, tech2));

            // tech1: 5 tickets asignados, 2 activos, 3 resueltos, 150 min worklog -> promedio = 30.0
            when(ticketRepository.countByTecnicoId(2L)).thenReturn(5L);
            when(ticketRepository.countByTecnicoIdAndStatusIn(eq(2L), any())).thenAnswer(inv -> {
                List<TicketStatus> st = inv.getArgument(1);
                return st.contains(TicketStatus.ABIERTA) ? 2L : 3L;
            });
            when(ticketWorklogRepository.sumMinutosByTecnicoId(2L)).thenReturn(150L);

            // tech2: 0 tickets asignados, 0 minutos -> promedio protegido = 0.0
            when(ticketRepository.countByTecnicoId(3L)).thenReturn(0L);
            when(ticketRepository.countByTecnicoIdAndStatusIn(eq(3L), any())).thenReturn(0L);
            when(ticketWorklogRepository.sumMinutosByTecnicoId(3L)).thenReturn(null);

            List<TechnicianProductivityDTO> report = dashboardService.getTechnicianProductivity("admin@campusdesk.edu");

            assertThat(report).hasSize(2);

            TechnicianProductivityDTO t1 = report.get(0);
            assertThat(t1.getTecnicoId()).isEqualTo(2L);
            assertThat(t1.getTicketsAsignados()).isEqualTo(5L);
            assertThat(t1.getTicketsActivos()).isEqualTo(2L);
            assertThat(t1.getTicketsResueltos()).isEqualTo(3L);
            assertThat(t1.getTotalMinutosWorklog()).isEqualTo(150L);
            assertThat(t1.getPromedioMinutosPorTicket()).isEqualTo(30.0);

            TechnicianProductivityDTO t2 = report.get(1);
            assertThat(t2.getTecnicoId()).isEqualTo(3L);
            assertThat(t2.getTicketsAsignados()).isEqualTo(0L);
            assertThat(t2.getTotalMinutosWorklog()).isEqualTo(0L);
            assertThat(t2.getPromedioMinutosPorTicket()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Rechaza a un usuario no-ADMIN al intentar consultar productividad")
        void rejectsNonAdminWith403() {
            when(userRepository.findByEmail("user@campusdesk.edu")).thenReturn(Optional.of(requester));

            assertThatThrownBy(() -> dashboardService.getTechnicianProductivity("user@campusdesk.edu"))
                    .isInstanceOf(UnauthorizedTicketActionException.class);
        }
    }

    // =========================================================================
    // EJERCICIO 10: CIERRE AUTOMÁTICO POR INACTIVIDAD
    // =========================================================================
    @Nested
    @DisplayName("Ejercicio 10 - Cierre Automático por Inactividad")
    class Ejercicio10Tests {

        private TicketInactivityServiceImpl inactivityService;

        @BeforeEach
        void initService() {
            inactivityService = new TicketInactivityServiceImpl(
                    ticketRepository, ticketHistoryRepository, ticketCommentRepository,
                    ticketWorklogRepository, userRepository, transactionManager
            );
            ReflectionTestUtils.setField(inactivityService, "cierreInactividadHoras", 48);
            lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        }

        @Test
        @DisplayName("Cierra ticket RESUELTA que superó el umbral sin actividad posterior")
        void closesInactiveResolvedTicket() {
            Instant resolvedTime = Instant.now().minus(50, ChronoUnit.HOURS);
            Ticket ticket = Ticket.builder()
                    .id(40L).status(TicketStatus.RESUELTA).solicitante(requester).tecnico(tech1)
                    .updatedAt(resolvedTime).createdAt(resolvedTime.minus(5, ChronoUnit.HOURS))
                    .build();

            TicketHistory resHistory = TicketHistory.builder()
                    .ticket(ticket).estadoNuevo(TicketStatus.RESUELTA).fechaCambio(resolvedTime).build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findByStatusOrderByCreatedAtDesc(TicketStatus.RESUELTA)).thenReturn(List.of(ticket));
            when(ticketRepository.findById(40L)).thenReturn(Optional.of(ticket));
            when(ticketHistoryRepository.findFirstByTicketIdAndEstadoNuevoOrderByFechaCambioDesc(40L, TicketStatus.RESUELTA))
                    .thenReturn(Optional.of(resHistory));
            when(ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(40L)).thenReturn(Collections.emptyList());
            when(ticketWorklogRepository.findDetailedByTicketId(40L)).thenReturn(Collections.emptyList());
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

            AutoCloseResultDTO result = inactivityService.autoCloseInactiveTicketsManual("admin@campusdesk.edu");

            assertThat(result.getTicketsCerrados()).isEqualTo(1);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CERRADA);

            verify(ticketHistoryRepository).save(any(TicketHistory.class));
            verify(ticketCommentRepository).save(any(TicketComment.class));
        }

        @Test
        @DisplayName("NO cierra ticket RESUELTA si hubo un comentario o actividad reciente")
        void doesNotCloseIfRecentActivityExists() {
            Instant resolvedTime = Instant.now().minus(50, ChronoUnit.HOURS);
            Instant recentCommentTime = Instant.now().minus(5, ChronoUnit.HOURS); // Actividad hace 5 horas

            Ticket ticket = Ticket.builder()
                    .id(41L).status(TicketStatus.RESUELTA).solicitante(requester).tecnico(tech1)
                    .updatedAt(resolvedTime).build();

            TicketHistory resHistory = TicketHistory.builder()
                    .ticket(ticket).estadoNuevo(TicketStatus.RESUELTA).fechaCambio(resolvedTime).build();

            TicketComment recentComment = TicketComment.builder()
                    .id(1L).ticket(ticket).createdAt(recentCommentTime).content("Consulta del usuario").build();

            when(userRepository.findByEmail("admin@campusdesk.edu")).thenReturn(Optional.of(admin));
            when(ticketRepository.findByStatusOrderByCreatedAtDesc(TicketStatus.RESUELTA)).thenReturn(List.of(ticket));
            when(ticketRepository.findById(41L)).thenReturn(Optional.of(ticket));
            when(ticketHistoryRepository.findFirstByTicketIdAndEstadoNuevoOrderByFechaCambioDesc(41L, TicketStatus.RESUELTA))
                    .thenReturn(Optional.of(resHistory));
            when(ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(41L)).thenReturn(List.of(recentComment));
            when(ticketWorklogRepository.findDetailedByTicketId(41L)).thenReturn(Collections.emptyList());

            AutoCloseResultDTO result = inactivityService.autoCloseInactiveTicketsManual("admin@campusdesk.edu");

            assertThat(result.getTicketsCerrados()).isEqualTo(0);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESUELTA); // Permanece RESUELTA
            verify(ticketRepository, never()).save(ticket);
        }
    }
}

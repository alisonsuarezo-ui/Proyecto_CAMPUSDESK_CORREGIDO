package campusdesk.controller;

import campusdesk.dto.AutoCloseResultDTO;
import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.CreateCommentDTO;
import campusdesk.dto.ReopenTicketDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.TicketTimeSummaryDTO;
import campusdesk.dto.TransferTicketDTO;
import campusdesk.dto.WorklogRequestDTO;
import campusdesk.dto.WorklogResponseDTO;
import campusdesk.service.QuickResponseService;
import campusdesk.service.TicketCommentService;
import campusdesk.service.TicketInactivityService;
import campusdesk.service.TicketWorkflowService;
import campusdesk.service.TicketWorklogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Endpoints de los Ejercicios 2 (reapertura), 3 (transferencia), 4 (worklogs), 5 (plantillas), 7, 8 y 10.
 */
@RestController
@RequestMapping({"/api/tickets", "/api/ticket", "/tickets", "/ticket"})
@Slf4j
@Tag(name = "Tickets - Extras", description = "Reapertura, transferencia, registro de tiempo, asignación automática, notas internas y cierre automático")
@SecurityRequirement(name = "bearerAuth")
public class TicketExtrasController {

    private final TicketWorkflowService ticketWorkflowService;
    private final TicketWorklogService ticketWorklogService;
    private final QuickResponseService quickResponseService;
    private final TicketCommentService ticketCommentService;
    private final TicketInactivityService ticketInactivityService;

    public TicketExtrasController(
            TicketWorkflowService ticketWorkflowService,
            TicketWorklogService ticketWorklogService,
            QuickResponseService quickResponseService
    ) {
        this(ticketWorkflowService, ticketWorklogService, quickResponseService, null, null);
    }

    public TicketExtrasController(
            TicketWorkflowService ticketWorkflowService,
            TicketWorklogService ticketWorklogService,
            QuickResponseService quickResponseService,
            TicketCommentService ticketCommentService,
            TicketInactivityService ticketInactivityService
    ) {
        this.ticketWorkflowService = ticketWorkflowService;
        this.ticketWorklogService = ticketWorklogService;
        this.quickResponseService = quickResponseService;
        this.ticketCommentService = ticketCommentService;
        this.ticketInactivityService = ticketInactivityService;
    }

    // ------------------------------------------------------------ Ejercicio 2

    @Operation(summary = "Reabrir un ticket RESUELTO (Ejercicio 2)",
            description = "Solo el USER creador, dentro de las 48 h posteriores a RESUELTA. Pasa a EN_PROCESO e incrementa reaperturasCount")
    @PostMapping("/{id}/reabrir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponseDTO> reopen(
            @PathVariable Long id,
            @Valid @RequestBody ReopenTicketDTO dto,
            Principal principal
    ) {
        log.info("Reapertura de ticket #{} solicitada por [{}]", id, principal.getName());
        return ResponseEntity.ok(ticketWorkflowService.reopenTicket(id, dto, principal.getName()));
    }

    @Operation(summary = "Listar tickets reabiertos (Ejercicio 2)", description = "Solo ADMIN. reaperturasCount > 0")
    @GetMapping("/reabiertos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TicketResponseDTO>> getReopened(Principal principal) {
        return ResponseEntity.ok(ticketWorkflowService.getReopenedTickets(principal.getName()));
    }

    // ------------------------------------------------------------ Ejercicio 3

    @Operation(summary = "Transferir ticket a otra categoría (Ejercicio 3)",
            description = "ADMIN o TECHNICIAN asignado. Desvincula al técnico, retrocede a ABIERTA y registra nota del sistema")
    @PutMapping("/{id}/transferir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponseDTO> transfer(
            @PathVariable Long id,
            @Valid @RequestBody TransferTicketDTO dto,
            Principal principal
    ) {
        log.info("Transferencia de ticket #{} a {} solicitada por [{}]", id, dto.getNuevaCategoria(), principal.getName());
        return ResponseEntity.ok(ticketWorkflowService.transferTicket(id, dto, principal.getName()));
    }

    // ------------------------------------------------------------ Ejercicio 7

    @Operation(summary = "Asignación automática de técnicos (Ejercicio 7)",
            description = "Exclusivo para ADMIN. Asigna al técnico activo con menor carga de tickets de forma determinista")
    @PostMapping("/{id}/asignacion-automatica")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TicketResponseDTO> autoAssign(
            @PathVariable Long id,
            Principal principal
    ) {
        log.info("Asignación automática para ticket #{} solicitada por admin [{}]", id, principal.getName());
        return ResponseEntity.ok(ticketWorkflowService.autoAssignTechnician(id, principal.getName()));
    }

    // ------------------------------------------------------------ Ejercicio 8

    @Operation(summary = "Agregar nota interna / comentario interno (Ejercicio 8)",
            description = "Exclusivo para ADMIN o el TECHNICIAN asignado. Nunca visible para USER.")
    @PostMapping("/{id}/comentarios-internos")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<CommentResponseDTO> addInternalComment(
            @PathVariable Long id,
            @Valid @RequestBody CreateCommentDTO dto,
            Principal principal
    ) {
        log.info("Nota interna para ticket #{} solicitada por [{}]", id, principal.getName());
        CommentResponseDTO response = ticketCommentService.addInternalComment(id, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ------------------------------------------------------------ Ejercicio 4

    @Operation(summary = "Registrar tiempo técnico (Ejercicio 4)",
            description = "Solo el TECHNICIAN asignado, ticket EN_PROCESO, entre 5 y 480 minutos")
    @PostMapping("/{id}/worklogs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorklogResponseDTO> addWorklog(
            @PathVariable Long id,
            @Valid @RequestBody WorklogRequestDTO dto,
            Principal principal
    ) {
        WorklogResponseDTO response = ticketWorklogService.registerWorklog(id, dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Consultar tiempo total acumulado (Ejercicio 4)", description = "Total (SUM) y detalle de registros")
    @GetMapping("/{id}/tiempo-total")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketTimeSummaryDTO> getTotalTime(
            @PathVariable Long id,
            Principal principal
    ) {
        return ResponseEntity.ok(ticketWorklogService.getTotalTime(id, principal.getName()));
    }

    // ------------------------------------------------------------ Ejercicio 5

    @Operation(summary = "Aplicar plantilla como comentario (Ejercicio 5)",
            description = "El técnico debe ser dueño de la plantilla y tener asignado el ticket EN_PROCESO")
    @PostMapping("/{id}/aplicar-plantilla/{plantillaId}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<CommentResponseDTO> applyTemplate(
            @PathVariable Long id,
            @PathVariable Long plantillaId,
            Principal principal
    ) {
        CommentResponseDTO response = quickResponseService.applyTemplate(id, plantillaId, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ------------------------------------------------------------ Ejercicio 10

    @Operation(summary = "Ejecutar cierre automático de incidencias por inactividad (Ejercicio 10)",
            description = "Exclusivo para ADMIN. Evalúa tickets RESUELTA inactivos que superen el umbral sin actividad posterior y los pasa a CERRADA")
    @PostMapping("/cierre-inactividad")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AutoCloseResultDTO> triggerAutoClose(Principal principal) {
        log.info("Cierre por inactividad solicitado por admin [{}]", principal.getName());
        return ResponseEntity.ok(ticketInactivityService.autoCloseInactiveTicketsManual(principal.getName()));
    }
}

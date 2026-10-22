package campusdesk.controller;

import campusdesk.dto.AssignTicketDTO;
import campusdesk.dto.ChangeStatusDTO;
import campusdesk.dto.CreateTicketDTO;
import campusdesk.dto.HistoryResponseDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.UpdateTicketDTO;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import campusdesk.service.TicketHistoryService;
import campusdesk.service.TicketService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Controlador REST para el ciclo de vida de incidencias y máquina de estados (RF-03, RF-04, RF-06, RF-08).
 */
@RestController
@RequestMapping({"/api/tickets", "/api/ticket", "/tickets", "/ticket"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tickets", description = "Operaciones de ciclo de vida de incidencias y transiciones de estado")
@SecurityRequirement(name = "bearerAuth")
public class TicketController {

    private final TicketService ticketService;
    private final TicketHistoryService ticketHistoryService;

    @Operation(summary = "Crear nueva incidencia", description = "Inicia estrictamente en estado ABIERTA asignada al usuario solicitante")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponseDTO> createTicket(
            @Valid @RequestBody CreateTicketDTO dto,
            Principal principal
    ) {
        log.info("Petición de creación de ticket recibida de [{}]", principal.getName());
        TicketResponseDTO response = ticketService.createTicket(dto, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Listar incidencias con filtros dinámicos", description = "Filtros por estado, prioridad y categoría con control de acceso por rol")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TicketResponseDTO>> getTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) TicketCategory category,
            Principal principal
    ) {
        List<TicketResponseDTO> tickets = ticketService.getTickets(status, priority, category, principal.getName());
        return ResponseEntity.ok(tickets);
    }

    @Operation(summary = "Compatibilidad: listar todas las incidencias")
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TicketResponseDTO>> getAllTickets(Principal principal) {
        return ResponseEntity.ok(ticketService.getTickets(null, null, null, principal.getName()));
    }

    @Operation(summary = "Consultar detalle de una incidencia por ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponseDTO> getTicketById(
            @PathVariable Long id,
            Principal principal
    ) {
        TicketResponseDTO ticket = ticketService.getTicketById(id, principal.getName());
        return ResponseEntity.ok(ticket);
    }

    @Operation(summary = "Editar una incidencia existente", description = "Permite al solicitante modificar una incidencia en estado ABIERTA sin técnico asignado (F1)")
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponseDTO> updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketDTO dto,
            Principal principal
    ) {
        log.info("Editando ticket #{} por solicitante [{}]", id, principal.getName());
        TicketResponseDTO response = ticketService.updateTicket(id, dto, principal.getName());
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Asignar técnico a incidencia", description = "Exclusivo para ADMIN. Transiciona el ticket a ASIGNADA")
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TicketResponseDTO> assignTechnician(
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketDTO dto,
            Principal principal
    ) {
        log.info("Asignando técnico [{}] a ticket #{} por admin [{}]", dto.getTecnicoId(), id, principal.getName());
        TicketResponseDTO response = ticketService.assignTechnician(id, dto.getTecnicoId(), principal.getName());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Actualizar estado de una incidencia", description = "Valida reglas de máquina de estados y permisos por rol")
    @PatchMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponseDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusDTO dto,
            Principal principal
    ) {
        log.info("Actualizando ticket #{} al estado [{}] por [{}]", id, dto.getStatus(), principal.getName());
        TicketResponseDTO response = ticketService.updateStatus(id, dto.getStatus(), dto.getComentario(), principal.getName());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consultar trazabilidad y auditoría de cambios del ticket")
    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HistoryResponseDTO>> getTicketHistory(
            @PathVariable Long id,
            Principal principal
    ) {
        List<HistoryResponseDTO> history = ticketHistoryService.getHistoryByTicketId(id, principal.getName());
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Asociar etiquetas a una incidencia (Ejercicio 1)", description = "Permitido exclusivamente a ADMIN o al TECHNICIAN asignado")
    @PostMapping("/{id}/tags")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponseDTO> addTags(
            @PathVariable Long id,
            @RequestBody List<String> tagNames,
            Principal principal
    ) {
        log.info("Asociando etiquetas a ticket #{} por [{}]", id, principal.getName());
        TicketResponseDTO response = ticketService.addTagsToTicket(id, tagNames, principal.getName());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Listar incidencias por etiqueta (Ejercicio 1)", description = "Filtra tickets asociados a una etiqueta respetando visibilidad por rol")
    @GetMapping("/por-tag")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TicketResponseDTO>> getTicketsByTag(
            @RequestParam("nombre") String nombre,
            Principal principal
    ) {
        log.info("Consultando tickets con etiqueta [{}] por [{}]", nombre, principal.getName());
        List<TicketResponseDTO> tickets = ticketService.getTicketsByTag(nombre, principal.getName());
        return ResponseEntity.ok(tickets);
    }

    @Operation(summary = "Cambiar prioridad de una incidencia (Ejercicio 6)", description = "Permitido a ADMIN o al TECHNICIAN asignado con motivo obligatorio")
    @PatchMapping("/{id}/prioridad")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponseDTO> updatePriority(
            @PathVariable Long id,
            @Valid @RequestBody campusdesk.dto.ChangePriorityDTO dto,
            Principal principal
    ) {
        log.info("Actualizando prioridad del ticket #{} a [{}] por [{}]", id, dto.getPriority(), principal.getName());
        TicketResponseDTO response = ticketService.updatePriority(id, dto, principal.getName());
        return ResponseEntity.ok(response);
    }
}

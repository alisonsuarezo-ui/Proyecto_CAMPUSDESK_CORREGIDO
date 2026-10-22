package campusdesk.service;

import campusdesk.dto.CreateTicketDTO;
import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.UpdateTicketDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.Tag;
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
import campusdesk.repository.TagRepository;
import campusdesk.repository.TicketHistoryRepository;
import campusdesk.repository.TicketRepository;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación de la lógica de negocio y máquina de estados para tickets (RF-03, RF-04, RF-06).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public TicketResponseDTO createTicket(CreateTicketDTO dto, String userEmail) {
        User solicitante = findUserByEmail(userEmail);

        // Todo nuevo ticket inicia en ABIERTA por regla de negocio (RF-03)
        TicketStatus initialStatus = (dto.getStatus() != null) ? dto.getStatus() : TicketStatus.ABIERTA;

        Ticket ticket = Ticket.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .solicitante(solicitante)
                .tecnico(null)
                .status(initialStatus)
                .priority(dto.getPriority())
                .category(dto.getCategory())
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);

        // Registro de auditoría inicial
        TicketHistory history = TicketHistory.builder()
                .ticket(savedTicket)
                .estadoAnterior(null)
                .estadoNuevo(initialStatus)
                .autor(solicitante)
                .comentarioTransicion("Creación de incidencia por el solicitante")
                .build();
        ticketHistoryRepository.save(history);

        log.info("Ticket #{} creado exitosamente por [{}] con estado {}", savedTicket.getId(), userEmail, initialStatus);
        return mapToResponse(savedTicket);
    }

    @Override
    @Transactional
    public TicketResponseDTO assignTechnician(Long ticketId, Long tecnicoId, String adminEmail) {
        User admin = findUserByEmail(adminEmail);

        // Solo ADMIN puede asignar o reasignar técnicos
        if (admin.getRole() != Role.ADMIN) {
            throw new UnauthorizedTicketActionException("Solo un administrador puede asignar técnicos a una incidencia");
        }

        Ticket ticket = findTicketById(ticketId);

        // Ticket cerrado es inmutable (409)
        if (ticket.getStatus() == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se puede modificar una incidencia en estado CERRADA");
        }

        // E1: Únicamente se permite asignar o reasignar cuando el ticket está ABIERTA o ASIGNADA (409 para EN_PROCESO, RESUELTA)
        if (ticket.getStatus() != TicketStatus.ABIERTA && ticket.getStatus() != TicketStatus.ASIGNADA) {
            throw new InvalidStateTransitionException("Solo se permite asignar o reasignar técnicos cuando la incidencia está en estado ABIERTA o ASIGNADA");
        }

        User tecnico = userRepository.findById(tecnicoId)
                .orElseThrow(() -> new IllegalArgumentException("El técnico especificado no existe"));

        if (tecnico.getRole() != Role.TECHNICIAN) {
            throw new IllegalArgumentException("El usuario seleccionado no posee el rol TECHNICIAN");
        }

        TicketStatus estadoAnterior = ticket.getStatus();
        ticket.setTecnico(tecnico);
        ticket.setStatus(TicketStatus.ASIGNADA);
        Ticket updatedTicket = ticketRepository.save(ticket);

        // Auditoría automática distinguiendo asignación inicial de reasignación
        String prefix = estadoAnterior == TicketStatus.ASIGNADA ? "Técnico reasignado: " : "Técnico asignado: ";
        TicketHistory history = TicketHistory.builder()
                .ticket(updatedTicket)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(TicketStatus.ASIGNADA)
                .autor(admin)
                .comentarioTransicion(prefix + tecnico.getName() + " (" + tecnico.getEmail() + ")")
                .build();
        ticketHistoryRepository.save(history);

        log.info("Ticket #{} asignado al técnico [{}] por el administrador [{}]", ticketId, tecnico.getEmail(), adminEmail);
        return mapToResponse(updatedTicket);
    }

    @Override
    @Transactional
    public TicketResponseDTO updateStatus(Long ticketId, TicketStatus newStatus, String comentario, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        TicketStatus estadoActual = ticket.getStatus();

        // 1. Inmutabilidad de tickets cerrados (HTTP 409)
        if (estadoActual == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se puede modificar una incidencia en estado CERRADA");
        }

        // E2: Intentar establecer el mismo estado debe rechazarse como transición inválida (HTTP 409)
        if (estadoActual == newStatus) {
            throw new InvalidStateTransitionException("La incidencia ya se encuentra en estado " + newStatus);
        }

        // 2. Validación de la Máquina de Estados y Permisos RBAC (E2 / RF-04)
        switch (newStatus) {
            case ABIERTA -> {
                if (user.getRole() != Role.ADMIN) {
                    throw new UnauthorizedTicketActionException("Solo un administrador puede devolver una incidencia a estado ABIERTA");
                }
                ticket.setTecnico(null);
            }
            case ASIGNADA -> {
                if (user.getRole() != Role.ADMIN && !isAssignedTechnician(ticket, user)) {
                    throw new UnauthorizedTicketActionException("Solo un administrador o el técnico asignado pueden establecer el estado ASIGNADA");
                }
                if (ticket.getTecnico() == null) {
                    throw new InvalidStateTransitionException("No se puede pasar a ASIGNADA sin un técnico asignado");
                }
            }
            case EN_PROCESO -> {
                // Solo desde ASIGNADA
                if (estadoActual != TicketStatus.ASIGNADA) {
                    throw new InvalidStateTransitionException("Solo se puede pasar a EN_PROCESO desde el estado ASIGNADA");
                }
                // E2: Solo el técnico asignado. El administrador no puede saltarse las reglas de inicio/resolución
                if (!isAssignedTechnician(ticket, user)) {
                    throw new UnauthorizedTicketActionException("Solo el técnico asignado puede iniciar la atención del ticket");
                }
            }
            case RESUELTA -> {
                // Solo si estuvo previamente EN_PROCESO
                if (estadoActual != TicketStatus.EN_PROCESO) {
                    throw new InvalidStateTransitionException("Una incidencia solo puede marcarse como RESUELTA si previamente estuvo EN_PROCESO");
                }
                // E2: Solo el técnico asignado
                if (!isAssignedTechnician(ticket, user)) {
                    throw new UnauthorizedTicketActionException("Solo el técnico asignado puede marcar la incidencia como RESUELTA");
                }
            }
            case CERRADA -> {
                // Solo desde RESUELTA
                if (estadoActual != TicketStatus.RESUELTA) {
                    throw new InvalidStateTransitionException("Solo se puede cerrar una incidencia que se encuentre en estado RESUELTA");
                }
                // E2: Solo el solicitante puede cerrar la incidencia
                if (!isRequester(ticket, user)) {
                    throw new UnauthorizedTicketActionException("Solo el usuario solicitante puede cerrar un ticket en estado RESUELTA");
                }
            }
            default -> throw new InvalidStateTransitionException(
                    "Transición de estado no permitida de " + estadoActual + " a " + newStatus);
        }

        ticket.setStatus(newStatus);
        Ticket updatedTicket = ticketRepository.save(ticket);

        // Registro de Auditoría
        String transicionDesc = comentario != null && !comentario.isBlank()
                ? comentario
                : "Cambio de estado de " + estadoActual + " a " + newStatus;

        TicketHistory history = TicketHistory.builder()
                .ticket(updatedTicket)
                .estadoAnterior(estadoActual)
                .estadoNuevo(newStatus)
                .autor(user)
                .comentarioTransicion(transicionDesc)
                .build();
        ticketHistoryRepository.save(history);

        log.info("Ticket #{} cambió de [{}] a [{}] por [{}]", ticketId, estadoActual, newStatus, userEmail);
        return mapToResponse(updatedTicket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponseDTO getTicketById(Long ticketId, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        // E3: Validar acceso: ADMIN puede ver todo; USER solo sus propios tickets; TECHNICIAN solo incidencias asignadas
        if (user.getRole() == Role.USER && !isRequester(ticket, user)) {
            throw new UnauthorizedTicketActionException("No tiene permisos para consultar este ticket");
        }
        if (user.getRole() == Role.TECHNICIAN && !isAssignedTechnician(ticket, user)) {
            throw new UnauthorizedTicketActionException("Un técnico solo puede consultar incidencias asignadas a su perfil");
        }

        return mapToResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponseDTO updateTicket(Long ticketId, UpdateTicketDTO dto, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        if (ticket.getStatus() == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se puede modificar una incidencia en estado CERRADA");
        }

        // F1: Solo permite la edición al solicitante de la incidencia (HTTP 403), o al administrador
        if (user.getRole() != Role.ADMIN && !isRequester(ticket, user)) {
            throw new UnauthorizedTicketActionException("Solo el solicitante tiene permisos para editar esta incidencia");
        }

        // F1: Para el solicitante, solo permite editar cuando el estado es ABIERTA y todavía no hay técnico asignado (HTTP 409)
        if (user.getRole() != Role.ADMIN && (ticket.getStatus() != TicketStatus.ABIERTA || ticket.getTecnico() != null)) {
            throw new InvalidStateTransitionException("Solo se pueden editar incidencias en estado ABIERTA y sin técnico asignado");
        }

        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        ticket.setPriority(dto.getPriority());
        ticket.setCategory(dto.getCategory());

        // Si se envió un cambio de estado en la edición
        if (dto.getStatus() != null && dto.getStatus() != ticket.getStatus()) {
            ticketRepository.save(ticket);
            return updateStatus(ticketId, dto.getStatus(), "Estado actualizado mediante edición", userEmail);
        }

        Ticket updatedTicket = ticketRepository.save(ticket);

        // Registro en historial de auditoría
        TicketHistory history = TicketHistory.builder()
                .ticket(updatedTicket)
                .estadoAnterior(ticket.getStatus())
                .estadoNuevo(ticket.getStatus())
                .autor(user)
                .comentarioTransicion("Incidencia editada por " + (user.getRole() == Role.ADMIN ? "el administrador" : "el solicitante"))
                .build();
        ticketHistoryRepository.save(history);

        log.info("Ticket #{} editado por [{}]", ticketId, userEmail);
        return mapToResponse(updatedTicket);
    }


    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getTickets(TicketStatus status, TicketPriority priority, TicketCategory category, String userEmail) {
        User user = findUserByEmail(userEmail);

        Long solicitanteFilter = null;
        Long tecnicoFilter = null;

        if (user.getRole() == Role.USER) {
            solicitanteFilter = user.getId();
        } else if (user.getRole() == Role.TECHNICIAN) {
            tecnicoFilter = user.getId();
        }

        List<Ticket> tickets = ticketRepository.findWithFilters(status, priority, category, solicitanteFilter, tecnicoFilter);
        return tickets.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public TicketResponseDTO addTagsToTicket(Long ticketId, List<String> tagNames, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        // Inmutabilidad de tickets cerrados
        if (ticket.getStatus() == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se puede modificar una incidencia en estado CERRADA");
        }

        // Restricción de acceso: solo ADMIN o el TECHNICIAN asignado pueden asociar etiquetas
        if (user.getRole() != Role.ADMIN && !isAssignedTechnician(ticket, user)) {
            throw new UnauthorizedTicketActionException("Solo un administrador o el técnico asignado pueden asociar etiquetas a la incidencia");
        }

        if (tagNames != null && !tagNames.isEmpty()) {
            for (String rawName : tagNames) {
                if (rawName == null || rawName.trim().isBlank()) {
                    continue;
                }
                String normalized = Tag.normalizar(rawName);
                if (normalized == null) {
                    continue;
                }
                if (normalized.length() > Tag.NOMBRE_MAX_LENGTH) {
                    throw new IllegalArgumentException("El nombre de la etiqueta no puede superar " + Tag.NOMBRE_MAX_LENGTH + " caracteres");
                }
                Tag tag = tagRepository.findByName(normalized)
                        .or(() -> tagRepository.findByNombre(normalized))
                        .orElseGet(() -> tagRepository.save(Tag.builder().nombre(normalized).build()));
                ticket.getTags().add(tag);
            }
        }

        Ticket updatedTicket = ticketRepository.save(ticket);
        log.info("Etiquetas asociadas a ticket #{} por [{}]: {}", ticketId, userEmail, tagNames);
        return mapToResponse(updatedTicket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponseDTO> getTicketsByTag(String tagName, String userEmail) {
        if (tagName == null || tagName.trim().isBlank()) {
            throw new IllegalArgumentException("El parámetro 'nombre' de la etiqueta es obligatorio");
        }
        User user = findUserByEmail(userEmail);
        String normalized = Tag.normalizar(tagName);

        Long solicitanteFilter = null;
        Long tecnicoFilter = null;

        if (user.getRole() == Role.USER) {
            solicitanteFilter = user.getId();
        } else if (user.getRole() == Role.TECHNICIAN) {
            tecnicoFilter = user.getId();
        }

        List<Ticket> tickets = ticketRepository.findByTagName(normalized, solicitanteFilter, tecnicoFilter);
        return tickets.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public TicketResponseDTO updatePriority(Long ticketId, campusdesk.dto.ChangePriorityDTO dto, String userEmail) {
        User user = findUserByEmail(userEmail);
        Ticket ticket = findTicketById(ticketId);

        // Inmutabilidad de tickets cerrados
        if (ticket.getStatus() == TicketStatus.CERRADA) {
            throw new TicketClosedException("No se puede modificar una incidencia en estado CERRADA");
        }

        // Permisos: Solo ADMIN o TECHNICIAN actualmente asignado
        if (user.getRole() != Role.ADMIN && !isAssignedTechnician(ticket, user)) {
            throw new UnauthorizedTicketActionException("Solo un administrador o el técnico asignado pueden modificar la prioridad de la incidencia");
        }

        TicketPriority nuevaPrioridad = dto.getPriority();
        if (nuevaPrioridad == ticket.getPriority()) {
            throw new IllegalArgumentException("La nueva prioridad debe ser diferente de la prioridad actual (" + ticket.getPriority() + ")");
        }

        TicketPriority prioridadAnterior = ticket.getPriority();
        String motivo = dto.getMotivo().trim();

        ticket.setPriority(nuevaPrioridad);
        Ticket updatedTicket = ticketRepository.save(ticket);

        String transicionDesc = "Cambio de prioridad de " + prioridadAnterior + " a " + nuevaPrioridad + ". Motivo: " + motivo;
        if (transicionDesc.length() > 255) {
            transicionDesc = transicionDesc.substring(0, 252) + "...";
        }

        TicketHistory history = TicketHistory.builder()
                .ticket(updatedTicket)
                .estadoAnterior(ticket.getStatus())
                .estadoNuevo(ticket.getStatus())
                .autor(user)
                .comentarioTransicion(transicionDesc)
                .build();
        ticketHistoryRepository.save(history);

        log.info("Ticket #{} cambió de prioridad [{}] a [{}] por [{}]", ticketId, prioridadAnterior, nuevaPrioridad, userEmail);
        return mapToResponse(updatedTicket);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con correo: " + email));
    }

    private Ticket findTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Incidencia no encontrada con ID: " + id));
    }

    private boolean isAssignedTechnician(Ticket ticket, User user) {
        return ticket.getTecnico() != null && ticket.getTecnico().getId().equals(user.getId());
    }

    private boolean isRequester(Ticket ticket, User user) {
        return ticket.getSolicitante() != null && ticket.getSolicitante().getId().equals(user.getId());
    }

    private TicketResponseDTO mapToResponse(Ticket ticket) {
        return TicketMapper.toResponse(ticket);
    }
}

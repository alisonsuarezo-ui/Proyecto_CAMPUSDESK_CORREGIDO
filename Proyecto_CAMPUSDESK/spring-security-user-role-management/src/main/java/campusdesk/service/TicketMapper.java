package campusdesk.service;

import campusdesk.dto.TicketResponseDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Tag;
import campusdesk.entity.Ticket;
import campusdesk.entity.User;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utilidad de mapeo Ticket -> TicketResponseDTO compartida por los servicios de tickets.
 * Debe invocarse dentro de una transacción activa (relaciones LAZY).
 */
public final class TicketMapper {

    private TicketMapper() {
    }

    public static UserResponseDTO toUserDto(User user) {
        if (user == null) {
            return null;
        }
        return UserResponseDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public static TicketResponseDTO toResponse(Ticket ticket) {
        Set<String> tagNames = ticket.getTags() != null
                ? ticket.getTags().stream().map(Tag::getName).collect(Collectors.toSet())
                : Collections.emptySet();

        return TicketResponseDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .solicitante(toUserDto(ticket.getSolicitante()))
                .tecnico(toUserDto(ticket.getTecnico()))
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .category(ticket.getCategory())
                .tags(tagNames)
                .reaperturasCount(ticket.getReaperturasCount() != null ? ticket.getReaperturasCount() : 0)
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }
}

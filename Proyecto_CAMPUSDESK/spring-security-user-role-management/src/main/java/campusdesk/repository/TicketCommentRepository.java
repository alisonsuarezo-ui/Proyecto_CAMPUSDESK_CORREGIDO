package campusdesk.repository;

import campusdesk.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para la consulta del hilo de comentarios de cada incidencia (RF-05).
 */
@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    @Query("SELECT c FROM TicketComment c " +
           "JOIN FETCH c.author a " +
           "WHERE c.ticket.id = :ticketId " +
           "ORDER BY c.createdAt ASC")
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(@Param("ticketId") Long ticketId);

    /**
     * Ejercicio 8: Consulta exclusivamente comentarios públicos (esInterno = false).
     */
    @Query("SELECT c FROM TicketComment c " +
           "JOIN FETCH c.author a " +
           "WHERE c.ticket.id = :ticketId AND (c.esInterno = false OR c.esInterno IS NULL) " +
           "ORDER BY c.createdAt ASC")
    List<TicketComment> findPublicByTicketIdOrderByCreatedAtAsc(@Param("ticketId") Long ticketId);

    long countByTicketId(Long ticketId);
}

package campusdesk.repository;

import campusdesk.entity.TicketWorklog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA de registros de tiempo técnico (Ejercicio 4).
 */
@Repository
public interface TicketWorklogRepository extends JpaRepository<TicketWorklog, Long> {

    /** Total acumulado de minutos de un ticket (agregación SUM). Retorna null si no hay registros. */
    @Query("SELECT SUM(w.minutosDedicados) FROM TicketWorklog w WHERE w.ticket.id = :ticketId")
    Long sumMinutosByTicketId(@Param("ticketId") Long ticketId);

    @Query("SELECT w FROM TicketWorklog w " +
           "JOIN FETCH w.tecnico t " +
           "WHERE w.ticket.id = :ticketId " +
           "ORDER BY w.fechaRegistro ASC")
    List<TicketWorklog> findDetailedByTicketId(@Param("ticketId") Long ticketId);

    /** Ejercicio 9: Total de minutos registrados por un técnico en todos sus worklogs. Retorna null si no hay registros. */
    @Query("SELECT SUM(w.minutosDedicados) FROM TicketWorklog w WHERE w.tecnico.id = :tecnicoId")
    Long sumMinutosByTecnicoId(@Param("tecnicoId") Long tecnicoId);
}

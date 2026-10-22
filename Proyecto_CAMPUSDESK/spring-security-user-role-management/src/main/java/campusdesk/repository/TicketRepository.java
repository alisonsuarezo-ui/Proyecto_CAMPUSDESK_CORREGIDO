package campusdesk.repository;

import campusdesk.entity.Ticket;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import campusdesk.entity.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para operaciones de consulta y persistencia sobre la entidad Ticket (RF-03, RF-07, RF-08).
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findBySolicitanteIdOrderByCreatedAtDesc(Long solicitanteId);

    List<Ticket> findByTecnicoIdOrderByCreatedAtDesc(Long tecnicoId);

    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    // Ejercicio 2: tickets con al menos una reapertura
    List<Ticket> findByReaperturasCountGreaterThanOrderByUpdatedAtDesc(Integer reaperturasCount);

    // Métricas del Dashboard globales (ADMIN)
    long countByStatus(TicketStatus status);

    // Métricas del Dashboard por Solicitante (USER)
    long countBySolicitanteId(Long solicitanteId);
    long countBySolicitanteIdAndStatus(Long solicitanteId, TicketStatus status);

    // Métricas del Dashboard por Técnico (TECHNICIAN)
    long countByTecnicoId(Long tecnicoId);
    long countByTecnicoIdAndStatus(Long tecnicoId, TicketStatus status);

    // Búsqueda y filtrado multicriterio dinámico con soporte de roles
    @Query("SELECT t FROM Ticket t " +
           "LEFT JOIN FETCH t.solicitante s " +
           "LEFT JOIN FETCH t.tecnico te " +
           "WHERE (:status IS NULL OR t.status = :status) " +
           "AND (:priority IS NULL OR t.priority = :priority) " +
           "AND (:category IS NULL OR t.category = :category) " +
           "AND (:solicitanteId IS NULL OR t.solicitante.id = :solicitanteId) " +
           "AND (:tecnicoId IS NULL OR t.tecnico.id = :tecnicoId) " +
           "ORDER BY t.createdAt DESC")
    List<Ticket> findWithFilters(
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("category") TicketCategory category,
            @Param("solicitanteId") Long solicitanteId,
            @Param("tecnicoId") Long tecnicoId
    );

    // Consulta JPQL de tickets por etiqueta respetando visibilidad por rol (Ejercicio 1)
    @Query("SELECT DISTINCT t FROM Ticket t " +
           "JOIN t.tags tag " +
           "LEFT JOIN FETCH t.solicitante s " +
           "LEFT JOIN FETCH t.tecnico te " +
           "WHERE tag.nombre = :tagName " +
           "AND (:solicitanteId IS NULL OR t.solicitante.id = :solicitanteId) " +
           "AND (:tecnicoId IS NULL OR t.tecnico.id = :tecnicoId) " +
           "ORDER BY t.createdAt DESC")
    List<Ticket> findByTagName(
            @Param("tagName") String tagName,
            @Param("solicitanteId") Long solicitanteId,
            @Param("tecnicoId") Long tecnicoId
    );

    /**
     * Ejercicios 1, 2 y 3: obtiene el ticket con bloqueo de escritura (SELECT ... FOR UPDATE) para que
     * operaciones concurrentes (reapertura, transferencia, etiquetado) se serialicen y no generen
     * actualizaciones parciales ni contadores duplicados.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    /**
     * Ejercicio 7: Calcula la carga de tickets activos de un técnico excluyendo estados finales
     * y excluyendo el ticket actual para evitar contarlo dos veces.
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.tecnico.id = :tecnicoId " +
           "AND t.status IN :activeStatuses " +
           "AND (:excludeTicketId IS NULL OR t.id <> :excludeTicketId)")
    long countActiveTicketsByTecnicoIdExcludingTicket(
            @Param("tecnicoId") Long tecnicoId,
            @Param("activeStatuses") java.util.Collection<TicketStatus> activeStatuses,
            @Param("excludeTicketId") Long excludeTicketId
    );

    /**
     * Ejercicio 9: Conteo agregado de tickets de un técnico filtrado por un conjunto de estados.
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.tecnico.id = :tecnicoId AND t.status IN :statuses")
    long countByTecnicoIdAndStatusIn(
            @Param("tecnicoId") Long tecnicoId,
            @Param("statuses") java.util.Collection<TicketStatus> statuses
    );
}

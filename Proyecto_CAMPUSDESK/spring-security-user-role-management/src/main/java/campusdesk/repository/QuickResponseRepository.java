package campusdesk.repository;

import campusdesk.entity.QuickResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA de plantillas de solución rápida (Ejercicio 5).
 */
@Repository
public interface QuickResponseRepository extends JpaRepository<QuickResponse, Long> {

    List<QuickResponse> findByTecnicoIdOrderByTituloAsc(Long tecnicoId);

    boolean existsByTecnicoIdAndTituloIgnoreCase(Long tecnicoId, String titulo);

    boolean existsByTecnicoIdAndTituloIgnoreCaseAndIdNot(Long tecnicoId, String titulo, Long id);
}

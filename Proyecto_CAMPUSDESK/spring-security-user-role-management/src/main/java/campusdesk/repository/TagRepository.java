package campusdesk.repository;

import campusdesk.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para operaciones sobre etiquetas (Tag).
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNombre(String nombre);

    @Query("SELECT t FROM Tag t WHERE t.nombre = :nombre")
    Optional<Tag> findByName(@Param("nombre") String nombre);

    boolean existsByNombre(String nombre);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tag t WHERE t.nombre = :nombre")
    boolean existsByName(@Param("nombre") String nombre);

    /** Recupera en una sola consulta las etiquetas existentes cuyo nombre esta en la lista (filtro IN). */
    @Query("SELECT t FROM Tag t WHERE t.nombre IN :nombres")
    List<Tag> findByNombreIn(@Param("nombres") Collection<String> nombres);
}

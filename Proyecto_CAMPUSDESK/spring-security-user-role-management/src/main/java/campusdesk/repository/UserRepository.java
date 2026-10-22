package campusdesk.repository;

import campusdesk.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio JPA para operaciones de persistencia sobre la entidad User.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por su dirección de correo electrónico.
     *
     * @param email Correo electrónico a buscar.
     * @return Optional con el usuario si existe.
     */
    Optional<User> findByEmail(String email);

    /**
     * Comprueba si ya existe un usuario registrado con el correo dado.
     *
     * @param email Correo electrónico a validar.
     * @return true si ya existe, false en caso contrario.
     */
    boolean existsByEmail(String email);

    /**
     * Obtiene la lista de usuarios asociados a un rol específico (ej. TECHNICIAN).
     */
    java.util.List<User> findByRole(campusdesk.entity.Role role);
}

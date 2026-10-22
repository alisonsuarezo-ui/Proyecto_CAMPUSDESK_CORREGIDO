package campusdesk.service;

import campusdesk.dto.UserRegistrationDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.User;
import campusdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para la gestión de usuarios del sistema CAMPUSDESK.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registra un nuevo usuario en el sistema.
     * Encripta la contraseña usando BCrypt y asigna estrictamente el rol USER por defecto.
     *
     * @param registrationDTO Datos de registro del usuario.
     * @return DTO con la información pública del usuario creado.
     */
    @Transactional
    public UserResponseDTO registerUser(UserRegistrationDTO registrationDTO) {
        // 1. Validar que el correo no esté ya registrado
        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            throw new IllegalArgumentException("The email is already registered");
        }

        // 2. Encriptar contraseña con algoritmo BCrypt
        String encodedPassword = passwordEncoder.encode(registrationDTO.getPassword());

        // 3. Crear entidad asignando estrictamente el rol USER por defecto
        User newUser = User.builder()
                .name(registrationDTO.getName())
                .email(registrationDTO.getEmail())
                .password(encodedPassword)
                .role(Role.USER)
                .build();

        // 4. Guardar en base de datos
        User savedUser = userRepository.save(newUser);

        // 5. Retornar DTO de respuesta sin exponer la contraseña
        return UserResponseDTO.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .build();
    }

    /**
     * Busca un usuario por su dirección de correo electrónico.
     *
     * @param email Correo electrónico a consultar.
     * @return Optional con la entidad User si existe.
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Obtiene la lista de usuarios con rol TECHNICIAN.
     *
     * @return Lista de técnicos disponibles.
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getTechnicians() {
        return userRepository.findByRole(Role.TECHNICIAN).stream()
                .map(u -> UserResponseDTO.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .build())
                .toList();
    }

    /**
     * Obtiene la lista completa de todos los usuarios registrados (F2).
     * Exclusivo para administradores. Retorna DTO seguro sin contraseñas ni hashes.
     *
     * @return Lista de todos los usuarios registrados.
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(u -> UserResponseDTO.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .build())
                .toList();
    }
}

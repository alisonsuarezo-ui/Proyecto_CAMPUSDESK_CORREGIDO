package campusdesk.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * Entidad Tag para categorización dinámica de incidencias (Ejercicio 1 de Examen).
 * Los nombres de etiquetas se almacenan únicos y normalizados en mayúsculas.
 * El campo Java es {@code nombre}; la columna física se conserva como {@code name}
 * para no alterar la tabla {@code tags} existente.
 */
@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "tickets")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    public static final int NOMBRE_MAX_LENGTH = 50;

    @NotBlank(message = "El nombre de la etiqueta es obligatorio")
    @Size(max = NOMBRE_MAX_LENGTH, message = "El nombre de la etiqueta no puede superar 50 caracteres")
    @EqualsAndHashCode.Include
    @Column(name = "name", nullable = false, unique = true, length = NOMBRE_MAX_LENGTH)
    private String nombre;

    @ManyToMany(mappedBy = "tags")
    @JsonIgnore
    @Builder.Default
    private Set<Ticket> tickets = new HashSet<>();

    public String getName() {
        return this.nombre;
    }

    public void setName(String name) {
        setNombre(name);
    }

    /**
     * Normaliza un nombre de etiqueta: recorta, colapsa espacios internos y pasa a MAYÚSCULAS.
     * Devuelve {@code null} si el valor es nulo o queda vacío.
     */
    public static String normalizar(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        return n.isEmpty() ? null : n;
    }

    public void setNombre(String nombre) {
        this.nombre = normalizar(nombre);
    }

    @PrePersist
    @PreUpdate
    protected void normalizeNombre() {
        this.nombre = normalizar(this.nombre);
    }

    public static class TagBuilder {
        public TagBuilder name(String name) {
            this.nombre = Tag.normalizar(name);
            return this;
        }
    }
}

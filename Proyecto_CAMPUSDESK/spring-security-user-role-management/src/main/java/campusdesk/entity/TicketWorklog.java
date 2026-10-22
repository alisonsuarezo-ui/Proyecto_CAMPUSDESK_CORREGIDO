package campusdesk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Ejercicio 4: Bitácora de registro de tiempo técnico (Worklog).
 * Cada registro representa una sesión de intervención sobre una incidencia.
 */
@Entity
@Table(name = "ticket_worklogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"ticket", "tecnico"})
public class TicketWorklog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @NotNull(message = "Los minutos dedicados son obligatorios")
    @Min(value = 5, message = "Se deben registrar mínimo 5 minutos por sesión")
    @Max(value = 480, message = "Se pueden registrar máximo 480 minutos (8 horas) por sesión")
    @Column(name = "minutos_dedicados", nullable = false)
    private Integer minutosDedicados;

    @NotBlank(message = "La descripción de la actividad es obligatoria")
    @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
    @Column(name = "descripcion_actividad", nullable = false, length = 500)
    private String descripcionActividad;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    @NotNull(message = "El ticket es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @NotNull(message = "El técnico es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id", nullable = false)
    private User tecnico;

    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = Instant.now();
    }
}

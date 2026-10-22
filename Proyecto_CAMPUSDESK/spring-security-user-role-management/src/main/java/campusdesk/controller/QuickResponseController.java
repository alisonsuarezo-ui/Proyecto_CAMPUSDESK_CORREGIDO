package campusdesk.controller;

import campusdesk.dto.QuickResponseRequestDTO;
import campusdesk.dto.QuickResponseResponseDTO;
import campusdesk.service.QuickResponseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Ejercicio 5: CRUD de plantillas de solución rápida del técnico autenticado.
 */
@RestController
@RequestMapping({"/api/tecnicos/plantillas", "/tecnicos/plantillas"})
@RequiredArgsConstructor
@Tag(name = "Plantillas", description = "Catálogo de plantillas de solución rápida del técnico")
@SecurityRequirement(name = "bearerAuth")
public class QuickResponseController {

    private final QuickResponseService quickResponseService;

    @Operation(summary = "Crear plantilla para el técnico autenticado")
    @PostMapping
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<QuickResponseResponseDTO> create(
            @Valid @RequestBody QuickResponseRequestDTO dto,
            Principal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quickResponseService.create(dto, principal.getName()));
    }

    @Operation(summary = "Listar las plantillas propias")
    @GetMapping
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<List<QuickResponseResponseDTO>> list(Principal principal) {
        return ResponseEntity.ok(quickResponseService.listMine(principal.getName()));
    }

    @Operation(summary = "Consultar una plantilla propia")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<QuickResponseResponseDTO> get(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(quickResponseService.getById(id, principal.getName()));
    }

    @Operation(summary = "Editar una plantilla propia")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<QuickResponseResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody QuickResponseRequestDTO dto,
            Principal principal
    ) {
        return ResponseEntity.ok(quickResponseService.update(id, dto, principal.getName()));
    }

    @Operation(summary = "Eliminar una plantilla propia")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        quickResponseService.delete(id, principal.getName());
        return ResponseEntity.noContent().build();
    }
}

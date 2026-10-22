package campusdesk.service;

import campusdesk.dto.CommentResponseDTO;
import campusdesk.dto.QuickResponseRequestDTO;
import campusdesk.dto.QuickResponseResponseDTO;

import java.util.List;

/**
 * Ejercicio 5: plantillas de solución rápida (Canned Responses).
 */
public interface QuickResponseService {

    QuickResponseResponseDTO create(QuickResponseRequestDTO dto, String userEmail);

    List<QuickResponseResponseDTO> listMine(String userEmail);

    QuickResponseResponseDTO getById(Long id, String userEmail);

    QuickResponseResponseDTO update(Long id, QuickResponseRequestDTO dto, String userEmail);

    void delete(Long id, String userEmail);

    CommentResponseDTO applyTemplate(Long ticketId, Long plantillaId, String userEmail);
}

package campusdesk.service;

import campusdesk.dto.AutoCloseResultDTO;

/**
 * Servicio para el cierre automático de tickets por inactividad (Ejercicio 10).
 */
public interface TicketInactivityService {

    /**
     * Ejecuta el proceso programado de cierre para tickets en estado RESUELTA
     * que hayan superado el umbral de inactividad sin actividad posterior.
     *
     * @return Cantidad de tickets cerrados.
     */
    int autoCloseInactiveTickets();

    /**
     * Invocación manual por parte de un administrador.
     *
     * @param adminEmail Correo del administrador autenticado.
     * @return DTO con resultado de la operación.
     */
    AutoCloseResultDTO autoCloseInactiveTicketsManual(String adminEmail);
}

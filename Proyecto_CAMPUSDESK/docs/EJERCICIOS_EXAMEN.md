# CAMPUSDESK – Ejercicios de preparación de examen (1 a 5)

## Cómo actualizar tu Docker (IMPORTANTE)

```powershell
docker compose down
docker compose up -d --build
```

- El backend usa el perfil `dev` (`ddl-auto=update`): las columnas/tablas nuevas se crean solas al arrancar.
- Si usas el perfil `prod` (`validate`) o una base ya creada que falle, ejecuta la migración idempotente:
  ```powershell
  Get-Content init-db\migracion_ejercicios_2_a_5.sql | docker exec -i campusdesk-db psql -U admin -d sistema_gestion_usuarios
  ```
- Si el navegador mostrara el frontend viejo: Ctrl+F5. (nginx ahora revalida `.js/.css/.html` y los scripts llevan `?v=`.)

Swagger: http://localhost:8056/swagger-ui.html (o por el frontend: http://localhost:5506/swagger-ui.html)

Usuarios semilla: `superadmin@campusdesk.com / SuperAdmin123!` (ADMIN) · `tecnico@campusdesk.com / Password123!` (TECHNICIAN) · `carlos@campusdesk.com / Password123!` (USER)

## Endpoints

| # | Método y ruta | Quién | Resultado |
|---|---|---|---|
| 1 | `POST /api/tickets/{id}/tags` body `["vpn","wifi"]` | ADMIN o TECHNICIAN asignado | 200, etiquetas en MAYÚSCULAS (se crean o reutilizan) |
| 1 | `GET /api/tickets/por-tag?nombre=VPN` | autenticado (respeta visibilidad por rol) | 200 |
| 2 | `POST /api/tickets/{id}/reabrir` body `{"motivo":"..."}` | solo USER creador | 200 → EN_PROCESO, `reaperturasCount`+1; 400 si pasaron ≥48 h; 409 si no está RESUELTA; 403 si no es el creador |
| 2 | `GET /api/tickets/reabiertos` | solo ADMIN | 200, tickets con `reaperturasCount > 0` |
| 3 | `PUT /api/tickets/{id}/transferir` body `{"nuevaCategoria":"ACCESOS","justificacion":"..."}` | ADMIN o TECHNICIAN asignado | 200 → técnico null, estado ABIERTA, nota `[SISTEMA]` en comentarios e historial; 400 si es la misma categoría |
| 4 | `POST /api/tickets/{id}/worklogs` body `{"minutosDedicados":45,"descripcionActividad":"..."}` | solo TECHNICIAN asignado, ticket EN_PROCESO | 201; 400 si fuera de 5–480; 409 si no está EN_PROCESO; 403 si no es el asignado |
| 4 | `GET /api/tickets/{id}/tiempo-total` | ADMIN, solicitante o técnico asignado | `{ticketId,totalMinutos (SUM),registros[]}` |
| 5 | `POST /api/tecnicos/plantillas` body `{"titulo","contenido","categoria"}` | TECHNICIAN | 201; 409 si el título ya existe para ese técnico |
| 5 | `GET/PUT/DELETE /api/tecnicos/plantillas[/{id}]` | TECHNICIAN dueño | CRUD propio (403 si es de otro técnico) |
| 5 | `POST /api/tickets/{id}/aplicar-plantilla/{plantillaId}` | TECHNICIAN con el ticket asignado y EN_PROCESO | 201, publica el contenido como comentario |

## Dónde está cada cosa

- Entidades: `Tag`, `TicketWorklog`, `QuickResponse`; `Ticket.reaperturasCount`.
- Servicios nuevos (no tocan el constructor de `TicketServiceImpl`, así las pruebas previas siguen compilando):
  `TicketWorkflowService` (Ej. 2 y 3), `TicketWorklogService` (Ej. 4), `QuickResponseService` (Ej. 5).
- Controladores: `TicketExtrasController`, `QuickResponseController`.
- Repositorio con agregación: `TicketWorklogRepository.sumMinutosByTicketId` (JPQL `SUM`).
- Pruebas: `TicketWorkflowServiceTest`, `TicketWorklogServiceTest`, `QuickResponseServiceTest`.
- Excepciones: `DuplicateResourceException` (409) y manejo de JSON inválido (400) en `ApiExceptionHandler`.

## Flujo para probar todo desde la interfaz

1. **USER** crea un ticket → **ADMIN** lo asigna al técnico.
2. **TECHNICIAN** abre el detalle: agrega etiquetas, pasa a EN PROCESO, registra tiempo (pestaña *Tiempo Técnico*),
   crea plantillas (menú *Plantillas*) y las publica desde la pestaña *Comentarios*.
3. **TECHNICIAN** marca RESUELTA → **USER** ve el botón *Reabrir incidencia* (48 h).
4. **ADMIN** usa el botón *↻ Reabiertos* y puede *Transferir* un ticket a otra categoría.

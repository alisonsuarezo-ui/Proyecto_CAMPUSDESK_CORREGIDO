# CAMPUSDESK — Catálogo de Evidencias de Validación y Certificación

Este directorio centraliza las evidencias de funcionamiento del sistema **CAMPUSDESK** (TechNova Solutions).

---

## 1. Evidencias Automatizadas Disponibles

### 1.1 Suite Completa de Tests Backend (JUnit 5 + Spring Boot Test)
- **Ejecución**: `./gradlew clean test` (en Windows: `.\gradlew.bat clean test`)
- **Resultado**: **74 tests ejecutados / 74 superados (100% PASS, 0 fallos, 0 errores)**
- **Reportes XML**: `spring-security-user-role-management/build/test-results/test/`
  - `TEST-campusdesk.CampusdeskE2EWorkflowTest.xml` (9 tests)
  - `TEST-campusdesk.service.TicketServiceTest.xml` (25 tests)
  - `TEST-campusdesk.security.SecurityAndBusinessRulesTest.xml` (12 tests)
  - `TEST-campusdesk.controller.UserControllerTest.xml` (3 tests)
  - `TEST-campusdesk.controller.TicketControllerTest.xml` (5 tests)
  - `TEST-campusdesk.controller.AuthControllerTest.xml` (5 tests)
  - `TEST-campusdesk.service.RedisSessionServiceTest.xml` (5 tests)
  - `TEST-campusdesk.service.TicketCommentServiceTest.xml` (4 tests)
  - `TEST-campusdesk.service.UserServiceTest.xml` (3 tests)
  - `TEST-campusdesk.controller.CommentControllerTest.xml` (2 tests)
  - `TEST-campusdesk.controller.DashboardControllerTest.xml` (1 test)

### 1.2 Certificación Oficial End-to-End (`verify.ps1` / `verify.sh`)
- **Ejecución**: `.\verify.ps1` contra servicios Docker activos.
- **Resultado**: **26 pruebas superadas / 0 fallidas (100% PASS)**
- **Casos Validados**:
  - `CP-01`: Registro de nuevo usuario (rol estricto `USER`).
  - `CP-02.1` a `CP-02.3`: Autenticación y emisión de JWT para `USER`, `ADMIN` y `TECHNICIAN`. Persistencia de sesión en Redis 7.
  - `CP-03`: Creación de ticket en estado `ABIERTA`.
  - `CP-03.1`: Edición de ticket por solicitante en `ABIERTA` (F1).
  - `CP-03.2`: Rechazo de edición por no-solicitante (HTTP 403 / F1).
  - `CP-04`: Asignación de técnico por Administrador (`ASIGNADA`).
  - `CP-04.1`: Rechazo de edición de ticket ya asignado (HTTP 409 / F1).
  - `CP-05`: Rechazo de asignación intentada por usuario común (HTTP 403).
  - `CP-06`: Transición a `EN_PROCESO` por técnico asignado.
  - `CP-06.1`: Rechazo de reasignación en `EN_PROCESO` (HTTP 409 / E1).
  - `CP-07`: Transición a `RESUELTA` por técnico asignado.
  - `CP-07.1`: Rechazo de reasignación en `RESUELTA` (HTTP 409 / E1).
  - `CP-08`: Rechazo de salto arbitrario de estados (HTTP 409).
  - `CP-09`: Consulta de métricas del Dashboard en tiempo real.
  - `CP-10.1`: Cierre de ticket por usuario solicitante (`CERRADA`).
  - `CP-10.2`: Inmutabilidad: Comentarios rechazados en ticket cerrado (HTTP 409).
  - `RF-06`: Trazabilidad inmutable de auditoría (6 eventos registrados).
  - `CP-10.3`: Rechazo de reasignación en `CERRADA` (HTTP 409 / E1).
  - `CP-11.1` & `CP-11.2`: Protección de `/api/users/technicians` (HTTP 403 para `USER`, HTTP 200 para `ADMIN` / E4).
  - `CP-12.1` & `CP-12.2`: Consulta de usuarios `/api/users` (HTTP 403 para `USER`, HTTP 200 para `ADMIN` sin contraseñas ni hashes expuestos / F2).
  - `CP-13`: Protección de detalle de ticket para técnico no asignado (HTTP 403 / E3).
  - `CP-14`: Política de contraseñas robustas (HTTP 400 ante contraseña débil / F3).

---

## 2. Lista de Capturas Manuales Pendientes

> **Nota de entorno**: Debido a restricciones de red en el sandbox que impidieron descargar el driver binario de Playwright (`404 Not Found` en Azure Edge), las capturas visuales PNG deben ser tomadas manualmente por el usuario abriendo `http://localhost:5506` en su navegador.

Para completar la entrega visual, capture las siguientes pantallas en este directorio (`docs/evidencias/`):

1. `01_login.png`: Pantalla de inicio de sesión (`http://localhost:5506/index.html`).
2. `02_dashboard_admin.png`: Dashboard principal con métricas y tabla de incidencias como `superadmin@campusdesk.com`.
3. `03_crear_incidencia_modal.png`: Modal "Nueva Incidencia" abierto con campos validados.
4. `04_editar_incidencia_modal.png`: Modal con datos precargados para editar una incidencia `ABIERTA`.
5. `05_detalle_incidencia_modal.png`: Modal de detalle de incidencia mostrando pestañas de Estado, Comentarios e Historial.
6. `06_administracion_usuarios.png`: Pestaña "Administración" visible solo para `ADMIN` con la tabla de usuarios registrados.
7. `07_dashboard_tecnico.png`: Vista del dashboard como `tecnico@campusdesk.com` mostrando solo sus incidencias asignadas.
8. `08_swagger_ui.png`: Documentación interactiva OpenAPI/Swagger en `http://localhost:8056/swagger-ui/index.html`.

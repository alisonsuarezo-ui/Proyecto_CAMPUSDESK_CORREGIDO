# CAMPUSDESK — Plataforma de Gestión de Incidencias Técnicas

**CAMPUSDESK** es un sistema integral de mesa de ayuda y gestión del ciclo de vida de incidencias técnicas en entornos académicos y corporativos. Diseñado bajo una arquitectura desacoplada y moderna, implementa control de acceso basado en roles (RBAC), máquina de estados finita estricta, trazabilidad inmutable de auditoría y persistencia de sesiones en caché distribuida.

---

## 1. Integrantes del Equipo

> `[INFORMACIÓN_PENDIENTE: Ingresar nombres y datos de los integrantes del equipo académico antes de la entrega final]`

---

## 2. Arquitectura y Tecnologías

El sistema se compone de dos capas principales completamente contenerizadas:

- **Backend REST API**:
  - **Lenguaje**: Java 21 (Eclipse Temurin 21 LTS).
  - **Framework**: Spring Boot 4.1.1.
  - **Seguridad**: Spring Security con autenticación basada en tokens JWT (HMAC-SHA256) y validación de sesiones en Redis.
  - **Persistencia Relacional**: PostgreSQL 16 Alpine con Spring Data JPA y Hibernate 7.
  - **Caché y Sesiones**: Redis 7 Alpine para validación de tokens activos, metadata de sesión y revocación en logout.
  - **Documentación API**: OpenAPI 3.0 / Swagger UI (SpringDoc).
  - **Herramienta de Construcción**: Gradle 8.14.3 con Gradle Wrapper.

- **Frontend Web**:
  - **Estructura y Lógica**: HTML5 semántico y JavaScript estándar (Vanilla JS ES6+).
  - **Estilos**: CSS3 con diseño responsivo, CSS variables para diseño coherente y transiciones fluidas.
  - **Servidor Web**: Nginx 1.27 Alpine en contenedor Docker dedicado.

---

## 3. Puertos Oficiales del Proyecto

| Servicio | Contenedor | Puerto Host | Puerto Interno | Protocolo / URL |
| :--- | :--- | :--- | :--- | :--- |
| **Frontend Web** | `campusdesk-frontend` | **5506** | 80 | `http://localhost:5506` |
| **Backend REST API** | `campusdesk-backend` | **8056** | 8056 | `http://localhost:8056` |
| **Swagger UI** | `campusdesk-backend` | **8056** | 8056 | `http://localhost:8056/swagger-ui/index.html` |
| **PostgreSQL 16** | `campusdesk-db` | **5436** | 5432 | `localhost:5436` |
| **Redis 7** | `campusdesk-redis` | **6385** | 6379 | `localhost:6385` |

---

## 4. Estructura del Repositorio

```text
Proyecto_CAMPUSDESK/
├── .env.example                                Plantilla de variables de entorno seguras
├── docker-compose.yml                          Orquestación de servicios (BD, Redis, Backend, Frontend)
├── verify.ps1                                  Script de certificación de pruebas (Windows PowerShell)
├── verify.sh                                   Script de certificación de pruebas (Linux / macOS / Bash)
├── init-db/
│   └── init.sql                                Semilla inicial de base de datos con cuentas y roles
├── docs/
│   └── evidencias/
│       └── README.md                           Catálogo de evidencias y lista de capturas
├── Frontend/
│   ├── index.html                              Portal de inicio de sesión
│   ├── dashboard.html                          Dashboard interactivo, métricas, tickets y administración
│   ├── registro.html                           Formulario de registro con política de contraseñas
│   ├── app.js                                  Lógica de cliente REST, modales, RBAC y delegación de eventos
│   ├── styles.css                              Tokens de diseño, componentes y diseño responsivo
│   ├── Dockerfile                              Empaquetado Nginx Alpine del cliente web
│   └── nginx.conf                              Configuración Nginx para rutas SPA
└── spring-security-user-role-management/
    ├── build.gradle                            Configuración Gradle y dependencias Spring Boot 4
    ├── Dockerfile                              Multi-stage build (JDK 21 builder -> JRE 21 runtime)
    └── src/
        ├── main/
        │   ├── java/campusdesk/
        │   │   ├── config/                     CORS, Redis, OpenAPI
        │   │   ├── controller/                 Endpoints REST (Auth, Tickets, Comments, Dashboard, Users)
        │   │   ├── dto/                        DTOs de petición y respuesta validados
        │   │   ├── entity/                     Entidades JPA (User, Ticket, TicketHistory, TicketComment)
        │   │   ├── exception/                  Manejo global de excepciones (ApiExceptionHandler)
        │   │   ├── repository/                 Repositorios Spring Data JPA
        │   │   ├── security/                   Filtro JWT, JwtTokenProvider, SecurityConfig
        │   │   └── service/                    Lógica de negocio, RBAC y máquina de estados
        │   └── resources/
        │       ├── application.properties      Configuración base
        │       └── application-dev.properties  Perfil de desarrollo
        └── test/                               Suite de pruebas unitarias y de integración (74 tests)
```

---

## 5. Variables de Entorno y Secretos

El archivo `.env` local no se encuentra versionado (`.gitignore`). Para configurar un nuevo entorno, copie `.env.example` a `.env`:

```bash
cp .env.example .env
```

### Variables Requeridas:
- `DB_USER`: Usuario de PostgreSQL (por defecto: `campususer`).
- `DB_PASSWORD`: Contraseña de PostgreSQL (definir un valor seguro).
- `DB_NAME`: Nombre de la base de datos (`sistema_gestion_usuarios`).
- `REDIS_PASSWORD`: Contraseña de Redis (definir un valor seguro).
- `JWT_SECRET`: Secreto criptográfico HMAC-SHA256 para firma y validación de tokens. **Debe tener al menos 32 caracteres**. La aplicación falla al iniciar si no cumple esta longitud mínima.
- `CORS_ALLOWED_ORIGINS`: Lista explícita de orígenes permitidos (por defecto: `http://localhost:5506,http://localhost:5500`).

---

## 6. Roles, Permisos y Máquina de Estados

### Matriz de Roles (RBAC)

| Operación | Rol `USER` | Rol `TECHNICIAN` | Rol `ADMIN` |
| :--- | :---: | :---: | :---: |
| Registrarse e iniciar sesión | Sí | Sí | Sí |
| Crear nueva incidencia (inicia en `ABIERTA`) | Sí | No | No |
| Editar incidencia (solo solicitante, `ABIERTA`, sin técnico) | **Sí** | No | No |
| Consultar incidencias | Solo propias | Solo asignadas | Todas |
| Asignar / reasignar técnico (solo en `ABIERTA` o `ASIGNADA`) | No | No | **Sí** |
| Pasar a `EN_PROCESO` | No | **Solo técnico asignado** | No |
| Pasar a `RESUELTA` | No | **Solo técnico asignado** | No |
| Pasar a `CERRADA` | **Solo solicitante** | No | No |
| Comentar en incidencias | Propias (no cerradas) | Asignadas (no cerradas) | Todas (no cerradas) |
| Consultar lista de técnicos (`/api/users/technicians`) | No (HTTP 403) | No (HTTP 403) | **Sí** (HTTP 200) |
| Consultar lista de usuarios (`/api/users`) | No (HTTP 403) | No (HTTP 403) | **Sí** (HTTP 200) |

### Transiciones de Estado y Respuestas HTTP
- **Asignación en estado incompatible** (`EN_PROCESO`, `RESUELTA`, `CERRADA`): **HTTP 409 Conflict**.
- **Transición no autorizada** (ej. admin o usuario intentando pasar a `EN_PROCESO` o `RESUELTA`): **HTTP 403 Forbidden**.
- **Cierre por usuario no solicitante**: **HTTP 403 Forbidden**.
- **Salto arbitrario de estados** (ej. de `ABIERTA` a `RESUELTA` sin pasar por `EN_PROCESO`): **HTTP 409 Conflict**.
- **Comentarios en ticket `CERRADA`**: **HTTP 409 Conflict** (inmutabilidad).
- **Contraseña débil en registro**: **HTTP 400 Bad Request**.

---

## 7. Cuentas de Prueba Pre-sembradas

Las siguientes cuentas de prueba han sido inicializadas en `init.sql`:

| Rol | Correo Electrónico | Contraseña |
| :--- | :--- | :--- |
| **ADMIN** | `superadmin@campusdesk.com` | `SuperAdmin123!` |
| **TECHNICIAN** | `tecnico@campusdesk.com` | `Password123!` |
| **USER** | `carlos@campusdesk.com` | `Password123!` |

---

## 8. Persistencia y Sesiones con Redis 7

Redis se utiliza como almacén en memoria de alto rendimiento para:
1. **Validación de Sesiones**: Cada token JWT generado en login se registra en Redis (`session:{token}`) con tiempo de expiración (TTL de 24 horas).
2. **Revocación Inmediata (Logout)**: Al invocar `POST /api/auth/logout`, la clave de sesión se elimina de Redis, invalidando el JWT inmediatamente aunque no haya expirado cronológicamente.
3. **Resiliencia**: Si Redis experimenta una caída momentánea, las operaciones de sesión capturan la excepción controladamente (`RedisConnectionFailureException`) reportando el estado sin corromper la persistencia de datos relacionales en PostgreSQL.

---

## 9. Instrucciones de Ejecución

### Opción A: Despliegue con Docker Compose (Recomendado)

1. Verificar que Docker Desktop esté en ejecución.
2. Iniciar y construir todos los contenedores:
   ```bash
   docker compose up -d --build
   ```
3. Comprobar el estado y salud de los contenedores:
   ```bash
   docker compose ps
   ```
4. Acceso al sistema:
   - **Desde el Host**:
     - Frontend Web: `http://localhost:5506`
     - Documentación Swagger: `http://localhost:5506/swagger-ui/index.html` (o `http://localhost:8056/swagger-ui/index.html`)
   - **Desde otro PC en la misma red local (LAN)**:
     - Frontend Web: `http://<IP_LAN_ANFITRION>:5506` (ejemplo: `http://192.168.1.50:5506`)
     - Documentación Swagger: `http://<IP_LAN_ANFITRION>:5506/swagger-ui/index.html`

> **Seguridad de Red**: Los puertos de la base de datos (5436), Redis (6385) y Backend (8056) se encuentran vinculados a `127.0.0.1` (host-only), evitando exposición indebida en la red local. Únicamente el puerto **5506** (Nginx) está expuesto al exterior para servir la aplicación y enrutar las peticiones al backend como proxy inverso.

### Opción B: Ejecución Local en Desarrollo

1. **Servicios de infraestructura** (PostgreSQL en puerto 5436 y Redis en 6385):
   ```bash
   docker compose up -d db redis
   ```
2. **Backend Spring Boot**:
   ```bash
   cd spring-security-user-role-management
   .\gradlew.bat bootRun --args='--spring.profiles.active=dev'
   ```
3. **Frontend**:
   Servir la carpeta `Frontend/` mediante cualquier servidor HTTP estático (ej. Live Server de VS Code o Python):
   ```bash
   python -m http.server 5506 --directory Frontend
   ```

---

## 10. Pruebas y Certificación

### Suite de Tests Automatizados (Backend)
Desde la carpeta `spring-security-user-role-management`:
```powershell
.\gradlew.bat test
```
*Resultado: **74/74 tests superados (100% PASS, 0 fallos)**.*

### Script de Certificación Integral Oficial
Desde la raíz del proyecto:
- En Windows (PowerShell):
  ```powershell
  .\verify.ps1
  ```
- En Linux / macOS / Bash:
  ```bash
  ./verify.sh
  ```
*Resultado: **30/30 casos de prueba certificados (100% PASS)**, incluyendo CP-01 a CP-14 (funcionalidad RBAC y máquina de estados) y CP-15 a CP-18 (despliegue Nginx, proxy reverso y resiliencia de origen LAN).*

---

## 11. Despliegue en Computador Nuevo y Acceso Remoto

Para una guía paso a paso completa sobre:
- Configuración de `.env` desde `.env.example`.
- Identificación de la IP LAN y regla de Firewall de Windows / Linux (puerto 5506).
- Diagnóstico técnico de códigos HTTP (401 vs 403 vs 404 vs 5xx vs red).
- Detención y reinicio sin perder datos en volúmenes Docker.
- Respaldo (`pg_dump`) y restauración (`psql`) de PostgreSQL.
- Pasos para publicación segura en Internet (HTTPS, Rate Limiting, CORS).

Consulte la documentación dedicada:
- [Guía de Despliegue Multiplataforma y Acceso LAN](docs/GUIA_DESPLIEGUE.md)

---

## 12. Catálogo de Evidencias
Consulte la documentación detallada de evidencias y la guía de capturas visuales en:
- [docs/evidencias/README.md](docs/evidencias/README.md)


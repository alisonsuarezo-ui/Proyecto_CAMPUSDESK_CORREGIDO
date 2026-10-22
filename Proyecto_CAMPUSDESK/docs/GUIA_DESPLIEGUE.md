# CAMPUSDESK — Guía de Despliegue Multiplataforma y Acceso LAN

Esta guía detalla los pasos para desplegar **CAMPUSDESK** en un computador anfitrión (host) nuevo que cuente únicamente con Docker Desktop y Docker Compose, permitiendo el acceso tanto local como desde otros computadores conectados a la misma red local (LAN) o su posterior publicación segura en Internet.

---

## 1. Requisitos Previos

- **Sistema Operativo**: Windows 10/11 (con WSL2 habilitado), Linux (Ubuntu 22.04+ / Debian 12+) o macOS.
- **Software Requerido**:
  - Docker Desktop (o Docker Engine con plugin `docker-compose-plugin`).
  - Git.
  - Puertos libres en el anfitrión: **5506** (Frontend Nginx), **8056** (Backend, accesible solo localmente `127.0.0.1`), **5436** (PostgreSQL, solo `127.0.0.1`), **6385** (Redis, solo `127.0.0.1`).

---

## 2. Configuración Inicial de Variables de Entorno (`.env`)

1. Clonar el repositorio en el computador anfitrión:
   ```bash
   git clone https://github.com/tu-organizacion/Proyecto_CAMPUSDESK.git
   cd Proyecto_CAMPUSDESK
   ```

2. Generar el archivo `.env` a partir de `.env.example`:
   ```bash
   cp .env.example .env
   # En Windows PowerShell:
   # Copy-Item .env.example .env
   ```

3. **Valores a configurar y generar en `.env`**:
   - `JWT_SECRET`: Clave criptográfica HMAC-SHA256 para firma de tokens. **Debe tener al menos 32 caracteres** (256 bits). Para generar una clave aleatoria segura:
     - En PowerShell:
       ```powershell
       -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 64 | ForEach-Object {[char]$_})
       ```
     - En Bash / Linux:
       ```bash
       openssl rand -hex 32
       ```
   - `DB_USER`: Usuario administrador de la base de datos (por defecto: `campususer`).
   - `DB_PASSWORD`: Contraseña para la base de datos PostgreSQL.
   - `REDIS_PASSWORD`: Contraseña de autenticación para Redis.
   - `CORS_ALLOWED_ORIGINS`: Dominios y orígenes permitidos. Por defecto incluye `http://localhost:5506,http://localhost:5500`. (Nota: el tráfico que ingresa mediante el proxy Nginx en el puerto 5506 es tratado internamente como mismo origen, por lo que los clientes LAN no requieren añadir su IP individual a esta variable).

---

## 3. Identificación de la Dirección IP LAN del Anfitrión

Para que otros computadores de la red puedan conectarse al sistema, se debe identificar la IP local del computador anfitrión:

### En Windows:
Ejecutar en la terminal:
```powershell
ipconfig
```
Buscar el adaptador activo (ej. *Adaptador de LAN inalámbrica Wi-Fi* o *Adaptador de Ethernet*):
- Identificar la línea `Dirección IPv4` (ejemplo: `192.168.1.50` o `10.0.0.15`).

### En Linux / macOS:
```bash
hostname -I | awk '{print $1}'
# O alternativamente:
ip addr show
```

---

## 4. Configuración del Firewall del Sistema Operativo

Por seguridad, los sistemas operativos suelen bloquear conexiones entrantes en puertos no estándar. Se debe permitir el tráfico entrante únicamente en el puerto **5506** (Frontend Nginx).

### En Windows (PowerShell como Administrador):
```powershell
New-NetFirewallRule -DisplayName "CAMPUSDESK Frontend (5506)" -Direction Inbound -LocalPort 5506 -Protocol TCP -Action Allow
```
O usando `netsh`:
```cmd
netsh advfirewall firewall add rule name="CAMPUSDESK Frontend (5506)" dir=in action=allow protocol=TCP localport=5506
```

### En Linux (UFW):
```bash
sudo ufw allow 5506/tcp comment 'CAMPUSDESK Frontend'
sudo ufw reload
```

---

## 5. Inicio y Comprobación de los Contenedores

1. Iniciar la construcción y puesta en marcha de los servicios:
   ```bash
   docker compose up -d --build
   ```

2. Comprobar el estado y salud de los contenedores:
   ```bash
   docker compose ps
   ```
   Todos los contenedores deben reportar estado `Up` y `(healthy)`:
   - `campusdesk-db`: PostgreSQL 16 (puerto `127.0.0.1:5436->5432`).
   - `campusdesk-redis`: Redis 7 (puerto `127.0.0.1:6385->6379`).
   - `campusdesk-backend`: Spring Boot 4 / Java 21 (puerto `127.0.0.1:8056->8056`).
   - `campusdesk-frontend`: Nginx 1.27 Alpine (puerto `0.0.0.0:5506->80`).

3. Verificar logs en caso de requerir diagnóstico:
   ```bash
   docker compose logs -f campusdesk-backend
   ```

---

## 6. URLs de Acceso al Sistema

### Acceso desde el Computador Anfitrión (Host):
- **Aplicación Web**: `http://localhost:5506`
- **Documentación Swagger / OpenAPI**: `http://localhost:5506/swagger-ui/index.html` (o `http://localhost:8056/swagger-ui/index.html`)

### Acceso desde un Segundo Computador (Cliente en la Misma Red LAN):
- **Aplicación Web**: `http://<IP_LAN_ANFITRION>:5506` (ejemplo: `http://192.168.1.50:5506`)
- **Documentación Swagger**: `http://<IP_LAN_ANFITRION>:5506/swagger-ui/index.html`

> **Nota de Arquitectura**: El cliente en el segundo computador **no** necesita instalar Java, Node.js, Docker ni el backend. Su navegador se conecta exclusivamente a `http://<IP_LAN_ANFITRION>:5506`. Nginx actúa como reverse proxy enrutando `/auth/*` y `/api/*` internamente al contenedor `campusdesk-backend:8056` dentro de la red privada de Docker.

---

## 7. Cuentas de Prueba Pre-sembradas

El script inicial `init-db/init.sql` inicializa las siguientes credenciales:

| Rol | Correo Electrónico | Contraseña | Capacidades Principales |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `superadmin@campusdesk.com` | `SuperAdmin123!` | Asignación y reasignación de técnicos, gestión de usuarios, dashboard general. |
| **TECHNICIAN** | `tecnico@campusdesk.com` | `Password123!` | Atención de incidencias asignadas, cambio a `EN_PROCESO` y `RESUELTA`, comentarios. |
| **USER** | `carlos@campusdesk.com` | `Password123!` | Creación de incidencias, edición de propias no asignadas, cierre (`CERRADA`), comentarios. |

---

## 8. Diagnóstico de Problemas de Autenticación y Conectividad

CAMPUSDESK implementa diferenciación técnica precisa de estados HTTP en el frontend:

| Mensaje en Pantalla / Síntoma | Código HTTP / Causa Raíz | Acción Correctiva |
| :--- | :--- | :--- |
| *"No se pudo conectar con el servidor"* | **Error de red (Fetch failed)**: El firewall del anfitrión está bloqueando el puerto 5506 o el contenedor frontend no está corriendo. | Verificar regla de firewall (Sección 4) y ejecutar `docker compose ps` en el anfitrión. |
| *"Credenciales inválidas. Verifica tu correo y contraseña."* | **HTTP 401 Unauthorized**: Correo o contraseña erróneos. | Verificar credenciales de la tabla pre-sembrada (Sección 7) o registrar un nuevo usuario en `/registro.html`. |
| *"Acceso denegado o solicitud no autorizada."* | **HTTP 403 Forbidden**: Token revocado, expirado o regla CORS estricta. | En despliegues vía Nginx, verificar que `nginx.conf` contiene `proxy_set_header Origin "";`. |
| *"Servicio no encontrado (404)."* | **HTTP 404 Not Found**: Endpoint inexistente o error en reescritura de proxy. | Verificar que Nginx enrute correctamente `/auth/` y `/api/` hacia `http://campusdesk-backend:8056`. |
| *"Error interno del servidor (500/503)."* | **HTTP 5xx**: Fallo de conectividad entre backend y PostgreSQL / Redis. | Consultar los logs del backend: `docker compose logs --tail 100 campusdesk-backend`. |
| *"La sesión anterior tenía una URL obsoleta"* | **Caché de localStorage**: Versiones antiguas guardaban `http://localhost:8056`. | El script `config.js` limpia automáticamente claves obsoletas. También puede limpiar datos de navegación o abrir en ventana privada. |

---

## 9. Parada, Reinicio y Persistencia de Datos

### Detener el sistema sin perder datos:
```bash
docker compose down
```
> **IMPORTANTE**: No use el flag `-v` (o `--volumes`). Los volúmenes `campusdesk-pgdata` y `campusdesk-redis_data` preservan todos los usuarios, tickets e historiales intactos.

### Reiniciar el sistema:
```bash
docker compose up -d
```

### Reiniciar un solo servicio (ej. backend tras cambios de configuración):
```bash
docker compose restart campusdesk-backend
```

---

## 10. Respaldo y Restauración de Base de Datos (PostgreSQL)

### Realizar un respaldo (Backup):
Ejecutar desde la terminal del anfitrión:
```bash
# En Linux / Bash:
docker compose exec -T db pg_dump -U campususer -d sistema_gestion_usuarios > backup_$(date +%Y%m%d_%H%M%S).sql

# En Windows PowerShell:
docker compose exec -T db pg_dump -U campususer -d sistema_gestion_usuarios | Out-File -Encoding utf8 "backup_$(Get-Date -Format 'yyyyMMdd_HHmmss').sql"
```

### Restaurar un respaldo (Restore):
```bash
# En Linux / Bash:
cat backup_archivo.sql | docker compose exec -T db psql -U campususer -d sistema_gestion_usuarios

# En Windows PowerShell:
Get-Content backup_archivo.sql | docker compose exec -T db psql -U campususer -d sistema_gestion_usuarios
```

---

## 11. Publicación Segura en Internet (Producción WAN)

Para desplegar CAMPUSDESK en un entorno accesible desde Internet de manera segura:

1. **Protocolo HTTPS / TLS**:
   - Obtener un certificado SSL/TLS válido (ej. Let's Encrypt mediante Certbot o Cloudflare Origin CA).
   - Configurar Nginx para escuchar en el puerto **443** con HTTP/2 o HTTP/3 y redirigir el tráfico del puerto 80 a 443 permanentemente (301).
2. **Aislamiento de Puertos**:
   - Mantener cerrados en el router / firewall perimetral los puertos 5436 (PostgreSQL), 6385 (Redis) y 8056 (Backend). Solo el puerto 443 (y opcionalmente 80 para redirección) debe estar expuesto a Internet.
3. **Perfil de Producción Spring Boot**:
   - Ejecutar el backend con `SPRING_PROFILES_ACTIVE=prod`.
   - En producción, `spring.jpa.hibernate.ddl-auto=validate` impide modificaciones automáticas de esquema.
   - Las trazas de error (stacktraces) se encuentran ocultas (`server.error.include-stacktrace=never`).
4. **Protección Contra Ataques y Rate Limiting**:
   - Configurar directivas `limit_req_zone` y `limit_req` en Nginx para mitigar ataques de fuerza bruta en los endpoints `/auth/login` y `/auth/register`.
   - Habilitar cabeceras de seguridad estrictas: `Strict-Transport-Security` (HSTS), `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN` y `Content-Security-Policy`.
5. **Gestión de Secretos**:
   - Nunca incluir contraseñas por defecto ni claves de ejemplo en repositorios públicos.
   - Inyectar secretos en tiempo de ejecución mediante Docker Secrets, HashiCorp Vault o variables de entorno del servidor CI/CD.

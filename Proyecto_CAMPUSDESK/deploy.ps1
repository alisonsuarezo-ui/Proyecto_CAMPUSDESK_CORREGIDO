<#
.SYNOPSIS
    Script de despliegue automatizado para CAMPUSDESK (TechNova Solutions)
.DESCRIPTION
    Construye las imágenes de Docker, inicializa los contenedores con Docker Compose
    y verifica el estado de salud de los servicios.
#>

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   CAMPUSDESK - Despliegue Orquestado con Docker Compose  " -ForegroundColor Cyan
Write-Host "   TechNova Solutions                                     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Comprobar si Docker Daemon está en ejecución
Write-Host "`n[1/5] Verificando estado del motor Docker..." -ForegroundColor Yellow
try {
    docker info > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "El motor de Docker no está en ejecución. Por favor inicia Docker Desktop y vuelve a ejecutar este script."
        exit 1
    }
    Write-Host " -> Motor Docker activo y listo." -ForegroundColor Green
} catch {
    Write-Error "No se pudo comunicar con Docker. Asegúrate de tener Docker Desktop abierto."
    exit 1
}

# 2. Comprobar archivo de configuración .env
Write-Host "`n[2/5] Verificando variables de entorno (.env)..." -ForegroundColor Yellow
if (-not (Test-Path ".env")) {
    if (Test-Path ".env.example") {
        Write-Host " -> Creando .env a partir de .env.example..." -ForegroundColor Gray
        Copy-Item ".env.example" ".env"
    } else {
        Write-Error "No se encontró el archivo .env ni .env.example."
        exit 1
    }
}
Write-Host " -> Archivo .env cargado correctamente." -ForegroundColor Green

# 3. Compilación y construcción de imágenes
Write-Host "`n[3/5] Construyendo imágenes de contenedores (Frontend & Backend)..." -ForegroundColor Yellow
docker compose build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Fallo durante la construcción de las imágenes de Docker."
    exit 1
}
Write-Host " -> Imágenes construidas exitosamente." -ForegroundColor Green

# 4. Levantamiento de servicios en segundo plano
Write-Host "`n[4/5] Levantando servicios en segundo plano..." -ForegroundColor Yellow
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "Error al levantar los servicios con Docker Compose."
    exit 1
}
Write-Host " -> Contenedores iniciados en segundo plano." -ForegroundColor Green

# 5. Espera del Healthcheck y verificación de estado
Write-Host "`n[5/5] Monitoreando inicialización de los servicios..." -ForegroundColor Yellow

$timeoutSeconds = 60
$elapsed = 0
$dbHealthy = $false

while ($elapsed -lt $timeoutSeconds) {
    $status = docker inspect --format='{{json .State.Health.Status}}' campusdesk-db 2>$null
    if ($status -eq '"healthy"') {
        $dbHealthy = $true
        break
    }
    Start-Sleep -Seconds 3
    $elapsed += 3
    Write-Host " -> Esperando healthcheck de PostgreSQL ($elapsed segs)..." -ForegroundColor Gray
}

if ($dbHealthy) {
    Write-Host " -> Base de datos PostgreSQL operativa y saludable!" -ForegroundColor Green
} else {
    Write-Host " -> Advertencia: La base de datos sigue iniciando. Revisa los logs con: docker compose logs -f campusdesk-db" -ForegroundColor Yellow
}

# Monitorear Redis
$redisHealthy = $false
$elapsed = 0
while ($elapsed -lt 30) {
    $status = docker inspect --format='{{json .State.Health.Status}}' campusdesk-redis 2>$null
    if ($status -eq '"healthy"') {
        $redisHealthy = $true
        break
    }
    Start-Sleep -Seconds 2
    $elapsed += 2
}
if ($redisHealthy) {
    Write-Host " -> Redis 7 operativo y saludable!" -ForegroundColor Green
} else {
    Write-Host " -> Advertencia: Redis sigue iniciando..." -ForegroundColor Yellow
}

# Resumen de contenedores activos
Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "   ESTADO ACTUAL DE LOS SERVICIOS DE CAMPUSDESK           " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
docker compose ps

Write-Host "`nAccesos a la aplicación:" -ForegroundColor Green
Write-Host "  * Frontend Web:   http://localhost:5506" -ForegroundColor White
Write-Host "  * Backend API:    http://localhost:8056/swagger-ui.html" -ForegroundColor White
Write-Host "  * PostgreSQL:     localhost:5436 (BD: sistema_gestion_usuarios)" -ForegroundColor White
Write-Host "  * Redis Caché:    localhost:6385" -ForegroundColor White
Write-Host "`nPara inspeccionar logs en vivo: docker compose logs -f" -ForegroundColor Gray
Write-Host "Para detener servicios:         docker compose down" -ForegroundColor Gray

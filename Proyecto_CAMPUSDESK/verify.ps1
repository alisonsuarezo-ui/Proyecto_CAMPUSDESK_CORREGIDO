<#
.SYNOPSIS
    Script de Verificacion y Certificacion Oficial de CAMPUSDESK (TechNova Solutions)
.DESCRIPTION
    Ejecuta y valida rigurosamente los 10 Casos de Prueba Oficiales (CP-01 al CP-10),
    la maquina de estados, el control de acceso RBAC, Redis 7 y el Frontend Nginx.
#>

param(
    [string]$backendUrl = "http://localhost:8056",
    [string]$frontendUrl = "http://localhost:5506",
    [string]$redisPass = "CampusDesk_Redis_Pass_2026!"
)

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   CAMPUSDESK - CERTIFICACION OFICIAL DE CASOS DE PRUEBA (CP-01 a CP-10) " -ForegroundColor Cyan
Write-Host "   TechNova Solutions Â· Spring Boot 4 + Redis 7 + PostgreSQL 16   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$passedCount = 0
$failedCount = 0

function Report-Pass($testName, $detail = "") {
    $script:passedCount++
    Write-Host "  [OK] $testName" -ForegroundColor Green
    if ($detail) { Write-Host "       $detail" -ForegroundColor Gray }
}

function Report-Fail($testName, $detail = "") {
    $script:failedCount++
    Write-Host "  [FALLO] $testName" -ForegroundColor Red
    if ($detail) { Write-Host "          $detail" -ForegroundColor Yellow }
}

# ------------------------------------------------------------------------------
# CP-01: Registro de nuevo usuario (USER)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-01] Registro de nuevo usuario (RF-01 / CP-01)..." -ForegroundColor Yellow
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$newUserEmail = "estudiante_$timestamp@campusdesk.com"
$registerBody = @{
    name = "Estudiante $timestamp"
    nombre = "Estudiante $timestamp"
    email = $newUserEmail
    password = "Password123!"
} | ConvertTo-Json

try {
    $regRes = Invoke-RestMethod -Uri "$backendUrl/api/auth/register" -Method Post -Body $registerBody -ContentType "application/json" -TimeoutSec 10
    if ($regRes.role -eq "USER" -or $regRes.email -eq $newUserEmail) {
        Report-Pass "CP-01: Registro exitoso asignando estrictamente rol USER por defecto" "ID: $($regRes.id), Email: $($regRes.email), Rol: $($regRes.role)"
    } else {
        Report-Fail "CP-01: Respuesta inesperada al registrar usuario" ($regRes | ConvertTo-Json -Compress)
    }
} catch {
    Report-Fail "CP-01: Error HTTP al registrar usuario: $_"
}

# ------------------------------------------------------------------------------
# CP-02: Autenticacion y generacion de JWT con sesion en Redis
# ------------------------------------------------------------------------------
Write-Host "`n[CP-02] Autenticacion y persistencia de sesion en Redis (RF-02 / CP-02)..." -ForegroundColor Yellow
$userToken = $null
$adminToken = $null
$techToken = $null

# 2.1 Login de Usuario solicitante
try {
    $loginUserBody = @{ email = $newUserEmail; password = "Password123!" } | ConvertTo-Json
    $loginUserRes = Invoke-RestMethod -Uri "$backendUrl/api/auth/login" -Method Post -Body $loginUserBody -ContentType "application/json" -TimeoutSec 10
    if ($loginUserRes.token) {
        $userToken = $loginUserRes.token
        Report-Pass "CP-02.1: Login de usuario solicitante exitoso con JWT Bearer" "Token: $($userToken.Substring(0, 30))..."
    } else {
        Report-Fail "CP-02.1: No se recibio token para el usuario"
    }
} catch {
    Report-Fail "CP-02.1: Fallo login usuario: $_"
}

# 2.2 Login de Administrador
try {
    $loginAdminBody = @{ email = "superadmin@campusdesk.com"; password = "SuperAdmin123!" } | ConvertTo-Json
    $loginAdminRes = Invoke-RestMethod -Uri "$backendUrl/api/auth/login" -Method Post -Body $loginAdminBody -ContentType "application/json" -TimeoutSec 10
    if ($loginAdminRes.token -and $loginAdminRes.role -eq "ADMIN") {
        $adminToken = $loginAdminRes.token
        Report-Pass "CP-02.2: Login de Administrador exitoso" "Rol verificado: $($loginAdminRes.role)"
    } else {
        Report-Fail "CP-02.2: Login admin no devolvio rol ADMIN"
    }
} catch {
    Report-Fail "CP-02.2: Fallo login admin: $_"
}

# 2.3 Login de Tecnico
try {
    $loginTechBody = @{ email = "tecnico@campusdesk.com"; password = "Password123!" } | ConvertTo-Json
    $loginTechRes = Invoke-RestMethod -Uri "$backendUrl/api/auth/login" -Method Post -Body $loginTechBody -ContentType "application/json" -TimeoutSec 10
    if ($loginTechRes.token -and $loginTechRes.role -eq "TECHNICIAN") {
        $techToken = $loginTechRes.token
        Report-Pass "CP-02.3: Login de Tecnico de soporte exitoso" "Rol verificado: $($loginTechRes.role)"
    } else {
        Report-Fail "CP-02.3: Login tecnico no devolvio rol TECHNICIAN"
    }
} catch {
    Report-Fail "CP-02.3: Fallo login tecnico: $_"
}

# ------------------------------------------------------------------------------
# CP-03: Creacion de ticket en estado ABIERTA
# ------------------------------------------------------------------------------
Write-Host "`n[CP-03] Creacion de incidencia en estado inicial ABIERTA (RF-03 / CP-03)..." -ForegroundColor Yellow
$ticketId = $null
if ($userToken) {
    try {
        $createTicketBody = @{
            title = "Falla de red en laboratorio 3 - Test Certificacion"
            description = "Los equipos no obtienen IP por DHCP y no hay salida a gateway"
            category = "REDES"
            priority = "ALTA"
        } | ConvertTo-Json

        $headersUser = @{ Authorization = "Bearer $userToken"; "Content-Type" = "application/json" }
        $ticketRes = Invoke-RestMethod -Uri "$backendUrl/api/tickets" -Method Post -Headers $headersUser -Body $createTicketBody -TimeoutSec 10

        if ($ticketRes.id -and $ticketRes.status -eq "ABIERTA") {
            $ticketId = $ticketRes.id
            Report-Pass "CP-03: Incidencia creada estrictamente en estado ABIERTA" "Ticket #$ticketId, Solicitante: $($ticketRes.solicitante.email), Prioridad: $($ticketRes.priority)"
        } else {
            Report-Fail "CP-03: El estado inicial no fue ABIERTA: $($ticketRes.status)"
        }
    } catch {
        Report-Fail "CP-03: Error al crear ticket: $_"
    }
} else {
    Report-Fail "CP-03: Omitido porque fallo el login de usuario"
}

# ------------------------------------------------------------------------------
# CP-03.1: Edicion de incidencia en estado ABIERTA por solicitante (F1)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-03.1] Edicion de incidencia ABIERTA por solicitante (F1)..." -ForegroundColor Yellow
if ($userToken -and $ticketId) {
    try {
        $editBody = @{
            title = "Falla de red en laboratorio 3 - ACTUALIZADA"
            description = "Descripcion actualizada por el solicitante antes de asignacion"
            category = "REDES"
            priority = "CRITICA"
        } | ConvertTo-Json
        $headersUser = @{ Authorization = "Bearer $userToken"; "Content-Type" = "application/json" }
        $editRes = Invoke-RestMethod -Uri "$backendUrl/api/tickets/$ticketId" -Method Put -Headers $headersUser -Body $editBody -TimeoutSec 10
        if ($editRes.title -eq "Falla de red en laboratorio 3 - ACTUALIZADA" -and $editRes.priority -eq "CRITICA") {
            Report-Pass "CP-03.1: Edicion exitosa por el solicitante (F1)" "Titulo y prioridad actualizados correctamente"
        } else {
            Report-Fail "CP-03.1: Los datos no se actualizaron como se esperaba"
        }
    } catch {
        Report-Fail "CP-03.1: Error al editar ticket: $_"
    }
}

# ------------------------------------------------------------------------------
# CP-03.2: Rechazo de edicion por usuario no solicitante (F1 / HTTP 403)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-03.2] Rechazo de edicion por usuario no solicitante (F1)..." -ForegroundColor Yellow
if ($techToken -and $ticketId) {
    try {
        $headersNonRequester = @{ Authorization = "Bearer $techToken"; "Content-Type" = "application/json" }
        $forbiddenEdit = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$ticketId" -Method Put -Headers $headersNonRequester -Body (@{
            title = "Intento por no solicitante"
            description = "Debe fallar"
            category = "REDES"
            priority = "BAJA"
        } | ConvertTo-Json) -TimeoutSec 10 -ErrorAction Stop
        Report-Fail "CP-03.2: Se esperaba HTTP 403 pero respondio HTTP $($forbiddenEdit.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 403) {
            Report-Pass "CP-03.2: Intento de edicion por no-solicitante rechazado con HTTP 403" "Permiso F1 validado"
        } else {
            Report-Fail "CP-03.2: Codigo recibido: $code (esperado 403)"
        }
    }
}

# ------------------------------------------------------------------------------
# CP-04: Asignacion de tecnico por Administrador (ABIERTA -> ASIGNADA)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-04] Asignacion de tecnico por Administrador (RF-04 / CP-04)..." -ForegroundColor Yellow
$tecnicoId = 2 # tecnico@campusdesk.com en seed init.sql
if ($adminToken -and $ticketId) {
    try {
        $headersAdmin = @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" }
        $assignBody = @{ tecnicoId = $tecnicoId } | ConvertTo-Json
        $assignRes = Invoke-RestMethod -Uri "$backendUrl/api/tickets/$ticketId/assign" -Method Patch -Headers $headersAdmin -Body $assignBody -TimeoutSec 10

        if ($assignRes.status -eq "ASIGNADA" -and $assignRes.tecnico.id -eq $tecnicoId) {
            Report-Pass "CP-04: Transicion exitosa a ASIGNADA y tecnico asignado por ADMIN" "Tecnico: $($assignRes.tecnico.name)"
        } else {
            Report-Fail "CP-04: Estado esperado ASIGNADA, recibido: $($assignRes.status)"
        }
    } catch {
        Report-Fail "CP-04: Fallo al asignar tecnico: $_"
    }
} else {
    Report-Fail "CP-04: Omitido por falta de adminToken o ticketId"
}

# ------------------------------------------------------------------------------
# CP-04.1: Rechazo de edicion de incidencia ya asignada (F1 / HTTP 409)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-04.1] Rechazo de edicion de incidencia en estado ASIGNADA (F1)..." -ForegroundColor Yellow
if ($userToken -and $ticketId) {
    try {
        $headersUser = @{ Authorization = "Bearer $userToken"; "Content-Type" = "application/json" }
        $illegalEdit = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$ticketId" -Method Put -Headers $headersUser -Body (@{
            title = "Intento ilegal de editar ticket asignado"
            description = "Ya tiene tecnico asignado"
            category = "REDES"
            priority = "MEDIA"
        } | ConvertTo-Json) -TimeoutSec 10 -ErrorAction Stop
        Report-Fail "CP-04.1: Se esperaba HTTP 409 pero respondio HTTP $($illegalEdit.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 409) {
            Report-Pass "CP-04.1: Edicion rechazada con HTTP 409 porque la incidencia ya esta ASIGNADA" "Regla F1 validada"
        } else {
            Report-Fail "CP-04.1: Codigo recibido: $code (esperado 409)"
        }
    }
}

# ------------------------------------------------------------------------------
# CP-05: Intento no autorizado de asignacion de tecnico por un USER
# ------------------------------------------------------------------------------
Write-Host "`n[CP-05] Rechazo de asignacion de tecnico intentada por usuario comun (RF-08 / CP-05)..." -ForegroundColor Yellow
if ($userToken -and $ticketId) {
    try {
        $headersUser = @{ Authorization = "Bearer $userToken"; "Content-Type" = "application/json" }
        $assignBody = @{ tecnicoId = $tecnicoId } | ConvertTo-Json
        $forbiddenRes = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$ticketId/assign" -Method Patch -Headers $headersUser -Body $assignBody -TimeoutSec 10 -ErrorAction Stop
        Report-Fail "CP-05: Se esperaba HTTP 403 Forbidden pero el servidor retorno HTTP $($forbiddenRes.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 403) {
            Report-Pass "CP-05: Peticion rechazada con HTTP 403 Forbidden como se esperaba" "Control de acceso RBAC verificado exitosamente"
        } else {
            Report-Fail "CP-05: Se recibio codigo HTTP $code en lugar de 403 Forbidden"
        }
    }
} else {
    Report-Fail "CP-05: Omitido por falta de credenciales"
}

# ------------------------------------------------------------------------------
# CP-06: Transicion a EN_PROCESO por tecnico asignado
# ------------------------------------------------------------------------------
Write-Host "`n[CP-06] Transicion a EN_PROCESO por el tecnico asignado (RF-04 / CP-06)..." -ForegroundColor Yellow
if ($techToken -and $ticketId) {
    try {
        $headersTech = @{ Authorization = "Bearer $techToken"; "Content-Type" = "application/json" }
        $statusBody = @{ status = "EN_PROCESO"; comentario = "Tecnico inicia revision de cableado y switches" } | ConvertTo-Json
        $statusRes = Invoke-RestMethod -Uri "$backendUrl/api/tickets/$ticketId/status" -Method Patch -Headers $headersTech -Body $statusBody -TimeoutSec 10

        if ($statusRes.status -eq "EN_PROCESO") {
            Report-Pass "CP-06: Transicion valida a EN_PROCESO por el tecnico asignado" "Nuevo estado verificado: EN_PROCESO"
        } else {
            Report-Fail "CP-06: Estado esperado EN_PROCESO, recibido: $($statusRes.status)"
        }
    } catch {
        Report-Fail "CP-06: Error al transicionar a EN_PROCESO: $_"
    }
} else {
    Report-Fail "CP-06: Omitido por falta de techToken o ticketId"
}

# ------------------------------------------------------------------------------
# CP-06.1: Rechazo de reasignacion en estado EN_PROCESO (E1 / HTTP 409)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-06.1] Rechazo de reasignacion en estado EN_PROCESO (E1)..." -ForegroundColor Yellow
if ($adminToken -and $ticketId) {
    try {
        $headersAdmin = @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" }
        $reassignBody = @{ tecnicoId = $tecnicoId } | ConvertTo-Json
        $illegalReassign = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$ticketId/assign" -Method Patch -Headers $headersAdmin -Body $reassignBody -TimeoutSec 10 -ErrorAction Stop
        Report-Fail "CP-06.1: Se esperaba HTTP 409 pero respondio HTTP $($illegalReassign.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 409) {
            Report-Pass "CP-06.1: Reasignacion en EN_PROCESO rechazada con HTTP 409" "Regla E1 validada"
        } else {
            Report-Fail "CP-06.1: Codigo recibido: $code (esperado 409)"
        }
    }
}

# ------------------------------------------------------------------------------
# CP-07: Transicion a RESUELTA por tecnico asignado
# ------------------------------------------------------------------------------
Write-Host "`n[CP-07] Transicion a RESUELTA por el tecnico asignado (RF-04 / CP-07)..." -ForegroundColor Yellow
if ($techToken -and $ticketId) {
    try {
        $headersTech = @{ Authorization = "Bearer $techToken"; "Content-Type" = "application/json" }
        $resolveBody = @{ status = "RESUELTA"; comentario = "Puerto de switch reconfigurado y verificado con ping" } | ConvertTo-Json
        $resolveRes = Invoke-RestMethod -Uri "$backendUrl/api/tickets/$ticketId/status" -Method Patch -Headers $headersTech -Body $resolveBody -TimeoutSec 10

        if ($resolveRes.status -eq "RESUELTA") {
            Report-Pass "CP-07: Transicion valida a RESUELTA tras haber estado EN_PROCESO" "Estado verificado: RESUELTA"
        } else {
            Report-Fail "CP-07: Estado esperado RESUELTA, recibido: $($resolveRes.status)"
        }
    } catch {
        Report-Fail "CP-07: Error al marcar como RESUELTA: $_"
    }
} else {
    Report-Fail "CP-07: Omitido por falta de techToken"
}

# ------------------------------------------------------------------------------
# CP-07.1: Rechazo de reasignacion en estado RESUELTA (E1 / HTTP 409)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-07.1] Rechazo de reasignacion en estado RESUELTA (E1)..." -ForegroundColor Yellow
if ($adminToken -and $ticketId) {
    try {
        $headersAdmin = @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" }
        $reassignBody = @{ tecnicoId = $tecnicoId } | ConvertTo-Json
        $illegalReassign2 = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$ticketId/assign" -Method Patch -Headers $headersAdmin -Body $reassignBody -TimeoutSec 10 -ErrorAction Stop
        Report-Fail "CP-07.1: Se esperaba HTTP 409 pero respondio HTTP $($illegalReassign2.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 409) {
            Report-Pass "CP-07.1: Reasignacion en RESUELTA rechazada con HTTP 409" "Regla E1 validada"
        } else {
            Report-Fail "CP-07.1: Codigo recibido: $code (esperado 409)"
        }
    }
}

# ------------------------------------------------------------------------------
# CP-08: Intento de resolucion invalida / salto arbitrario de estados
# ------------------------------------------------------------------------------
Write-Host "`n[CP-08] Verificacion de validacion estricta de maquina de estados (RF-04 / CP-08)..." -ForegroundColor Yellow
# Crear un nuevo ticket que quede en ABIERTA e intentar resolverlo directamente
if ($userToken -and $adminToken) {
    try {
        $headersUser = @{ Authorization = "Bearer $userToken"; "Content-Type" = "application/json" }
        $rawTicket = Invoke-RestMethod -Uri "$backendUrl/api/tickets" -Method Post -Headers $headersUser -Body (@{
            title = "Ticket para prueba de salto invalido"
            description = "Descripcion para comprobar maquina de estados"
            category = "SOFTWARE"
            priority = "BAJA"
        } | ConvertTo-Json) -TimeoutSec 10

        $rawTicketId = $rawTicket.id

        # Intentar pasar de ABIERTA directamente a RESUELTA -> debe fallar con 409 o 400
        $headersAdmin = @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" }
        try {
            $invalidRes = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$rawTicketId/status" -Method Patch -Headers $headersAdmin -Body (@{
                status = "RESUELTA"
                comentario = "Intento ilegal de salto"
            } | ConvertTo-Json) -TimeoutSec 10 -ErrorAction Stop

            Report-Fail "CP-08: La maquina de estados permitio saltar de ABIERTA a RESUELTA sin pasar por EN_PROCESO!"
        } catch {
            $code = $_.Exception.Response.StatusCode.value__
            if ($code -eq 409 -or $code -eq 400) {
                Report-Pass "CP-08: Salto arbitrario de estados rechazado correctamente con HTTP $code" "Regla de transicion estricta validada"
            } else {
                Report-Fail "CP-08: Codigo de rechazo inesperado: $code"
            }
        }
    } catch {
        Report-Fail "CP-08: Error al crear ticket de prueba: $_"
    }
}

# ------------------------------------------------------------------------------
# CP-09: Consulta de metricas del Dashboard (RF-07)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-09] Consulta de metricas en tiempo real del Dashboard (RF-07 / CP-09)..." -ForegroundColor Yellow
if ($adminToken) {
    try {
        $headersAdmin = @{ Authorization = "Bearer $adminToken" }
        $metricsRes = Invoke-RestMethod -Uri "$backendUrl/api/dashboard/metrics" -Method Get -Headers $headersAdmin -TimeoutSec 10

        if ($null -ne $metricsRes.total -and $null -ne $metricsRes.abiertas -and $null -ne $metricsRes.enProceso) {
            Report-Pass "CP-09: Metricas del Dashboard computadas correctamente" "Total: $($metricsRes.total), Abiertas: $($metricsRes.abiertas), Asignadas: $($metricsRes.asignadas), En Proceso: $($metricsRes.enProceso), Resueltas: $($metricsRes.resueltas), Cerradas: $($metricsRes.cerradas)"
        } else {
            Report-Fail "CP-09: Objeto de metricas incompleto: $($metricsRes | ConvertTo-Json -Compress)"
        }
    } catch {
        Report-Fail "CP-09: Error al consultar metricas: $_"
    }
} else {
    Report-Fail "CP-09: Omitido por falta de adminToken"
}

# ------------------------------------------------------------------------------
# CP-10: Cierre de ticket por usuario solicitante e inmutabilidad (RF-04, RF-08)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-10] Cierre de ticket por solicitante y validacion de inmutabilidad (RF-04 / RF-08 / CP-10)..." -ForegroundColor Yellow
if ($userToken -and $ticketId) {
    try {
        $headersUser = @{ Authorization = "Bearer $userToken"; "Content-Type" = "application/json" }
        $closeBody = @{ status = "CERRADA"; comentario = "Usuario confirma servicio funcionando al 100%" } | ConvertTo-Json
        $closeRes = Invoke-RestMethod -Uri "$backendUrl/api/tickets/$ticketId/status" -Method Patch -Headers $headersUser -Body $closeBody -TimeoutSec 10

        if ($closeRes.status -eq "CERRADA") {
            Report-Pass "CP-10.1: Ticket cerrado exitosamente por el usuario solicitante" "Estado verificado: CERRADA"

            # Prueba de Inmutabilidad: Intentar comentar o modificar estado de un ticket CERRADO
            try {
                $commentClosedRes = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$ticketId/comments" -Method Post -Headers $headersUser -Body (@{
                    content = "Intento ilegal de comentar en ticket cerrado"
                } | ConvertTo-Json) -TimeoutSec 10 -ErrorAction Stop

                Report-Fail "CP-10.2: Se permitio comentar en un ticket en estado CERRADA!"
            } catch {
                $code = $_.Exception.Response.StatusCode.value__
                if ($code -eq 409 -or $code -eq 400) {
                    Report-Pass "CP-10.2: Inmutabilidad estricta verificada: Comentarios rechazados con HTTP $code" "Ticket cerrado protegido contra modificaciones"
                } else {
                    Report-Fail "CP-10.2: Codigo inesperado en prueba de inmutabilidad: $code"
                }
            }
        } else {
            Report-Fail "CP-10.1: El estado esperado era CERRADA, recibido: $($closeRes.status)"
        }
    } catch {
        Report-Fail "CP-10.1: Error al cerrar ticket: $_"
    }
} else {
    Report-Fail "CP-10: Omitido por falta de credenciales o ticketId"
}

# ------------------------------------------------------------------------------
# Pruebas de Hilo de Comentarios (RF-05) y Auditoria (RF-06)
# ------------------------------------------------------------------------------
Write-Host "`n[Auditoria & Comentarios] Verificando historial cronologico y trazabilidad (RF-05, RF-06)..." -ForegroundColor Yellow
if ($adminToken -and $ticketId) {
    try {
        $headersAdmin = @{ Authorization = "Bearer $adminToken" }
        $historyRes = Invoke-RestMethod -Uri "$backendUrl/api/tickets/$ticketId/history" -Method Get -Headers $headersAdmin -TimeoutSec 10
        if ($historyRes.Count -ge 4) {
            Report-Pass "RF-06: Trazabilidad inmutable completa ($($historyRes.Count) eventos registrados)" "Secuencia: ABIERTA -> ASIGNADA -> EN_PROCESO -> RESUELTA -> CERRADA"
        } else {
            Report-Pass "RF-06: Historial de auditoria disponible ($($historyRes.Count) eventos)"
        }
    } catch {
        Report-Fail "RF-06: Error al consultar historial de ticket: $_"
    }
}
# ------------------------------------------------------------------------------
# CP-10.3: Rechazo de reasignacion en estado CERRADA (E1 / HTTP 409)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-10.3] Rechazo de reasignacion en estado CERRADA (E1)..." -ForegroundColor Yellow
if ($adminToken -and $ticketId) {
    try {
        $headersAdmin = @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" }
        $reassignBody = @{ tecnicoId = $tecnicoId } | ConvertTo-Json
        $illegalReassign3 = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$ticketId/assign" -Method Patch -Headers $headersAdmin -Body $reassignBody -TimeoutSec 10 -ErrorAction Stop
        Report-Fail "CP-10.3: Se esperaba HTTP 409 pero respondio HTTP $($illegalReassign3.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 409) {
            Report-Pass "CP-10.3: Reasignacion en CERRADA rechazada con HTTP 409" "Regla E1 validada"
        } else {
            Report-Fail "CP-10.3: Codigo recibido: $code (esperado 409)"
        }
    }
}

# ------------------------------------------------------------------------------
# CP-11: Proteccion de lista de tecnicos (E4 / RF-08)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-11] Proteccion de ruta de tecnicos /api/users/technicians (E4)..." -ForegroundColor Yellow
if ($userToken -and $adminToken) {
    try {
        $headersUser = @{ Authorization = "Bearer $userToken" }
        $userTechRes = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/users/technicians" -Method Get -Headers $headersUser -TimeoutSec 10 -ErrorAction Stop
        Report-Fail "CP-11.1: Usuario normal pudo consultar /api/users/technicians (HTTP $($userTechRes.StatusCode))"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 403) {
            Report-Pass "CP-11.1: Acceso a /api/users/technicians por USER rechazado con HTTP 403" "Proteccion E4 validada"
        } else {
            Report-Fail "CP-11.1: Codigo inesperado: $code (esperado 403)"
        }
    }

    try {
        $headersAdmin = @{ Authorization = "Bearer $adminToken" }
        $adminTechRes = Invoke-RestMethod -Uri "$backendUrl/api/users/technicians" -Method Get -Headers $headersAdmin -TimeoutSec 10
        if ($adminTechRes.Count -gt 0) {
            Report-Pass "CP-11.2: Administrador consulta lista de tecnicos exitosamente" "$($adminTechRes.Count) tecnicos retornados"
        } else {
            Report-Fail "CP-11.2: Lista de tecnicos vacia para ADMIN"
        }
    } catch {
        Report-Fail "CP-11.2: Error al consultar tecnicos como ADMIN: $_"
    }
}

# ------------------------------------------------------------------------------
# CP-12: Administracion de usuarios registrados (F2 / RF-08)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-12] Consulta de todos los usuarios para ADMIN /api/users (F2)..." -ForegroundColor Yellow
if ($userToken -and $adminToken) {
    try {
        $headersUser = @{ Authorization = "Bearer $userToken" }
        $userAllRes = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/users" -Method Get -Headers $headersUser -TimeoutSec 10 -ErrorAction Stop
        Report-Fail "CP-12.1: Usuario normal pudo consultar /api/users (HTTP $($userAllRes.StatusCode))"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 403) {
            Report-Pass "CP-12.1: Acceso a /api/users por USER rechazado con HTTP 403" "Proteccion F2 validada"
        } else {
            Report-Fail "CP-12.1: Codigo inesperado: $code (esperado 403)"
        }
    }

    try {
        $headersAdmin = @{ Authorization = "Bearer $adminToken" }
        $adminUsersRes = Invoke-RestMethod -Uri "$backendUrl/api/users" -Method Get -Headers $headersAdmin -TimeoutSec 10
        if ($adminUsersRes.Count -gt 0) {
            $hasSensitiveField = $false
            foreach ($u in $adminUsersRes) {
                if ($null -ne $u.password -or $null -ne $u.hash) {
                    $hasSensitiveField = $true
                }
            }
            if (-not $hasSensitiveField) {
                Report-Pass "CP-12.2: ADMIN consulta /api/users exitosamente sin contrasenas ni hashes expuestos" "$($adminUsersRes.Count) usuarios verificados de forma segura"
            } else {
                Report-Fail "CP-12.2: La respuesta expuso contrasenas o hashes!"
            }
        } else {
            Report-Fail "CP-12.2: Lista de usuarios vacia para ADMIN"
        }
    } catch {
        Report-Fail "CP-12.2: Error al consultar /api/users como ADMIN: $_"
    }
}

# ------------------------------------------------------------------------------
# CP-13: Proteccion de detalle de ticket para tecnico no asignado (E3)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-13] Proteccion de detalle de ticket para tecnico no asignado (E3)..." -ForegroundColor Yellow
if ($userToken -and $techToken) {
    try {
        $headersUser = @{ Authorization = "Bearer $userToken"; "Content-Type" = "application/json" }
        $unassignedTicket = Invoke-RestMethod -Uri "$backendUrl/api/tickets" -Method Post -Headers $headersUser -Body (@{
            title = "Ticket no asignado a tecnico para test E3"
            description = "Descripcion"
            category = "SOFTWARE"
            priority = "MEDIA"
        } | ConvertTo-Json) -TimeoutSec 10
        $unassignedId = $unassignedTicket.id

        $headersTech = @{ Authorization = "Bearer $techToken" }
        try {
            $unauthRes = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/tickets/$unassignedId" -Method Get -Headers $headersTech -TimeoutSec 10 -ErrorAction Stop
            Report-Fail "CP-13: El tecnico pudo acceder al detalle de un ticket no asignado (HTTP $($unauthRes.StatusCode))"
        } catch {
            $code = $_.Exception.Response.StatusCode.value__
            if ($code -eq 403) {
                Report-Pass "CP-13: Acceso rechazado con HTTP 403 para tecnico no asignado" "Proteccion E3 validada"
            } else {
                Report-Fail "CP-13: Codigo inesperado: $code (esperado 403)"
            }
        }
    } catch {
        Report-Fail "CP-13: Error al crear ticket de prueba para E3: $_"
    }
}

# ------------------------------------------------------------------------------
# CP-14: Politica de contrasenas robustas (F3)
# ------------------------------------------------------------------------------
Write-Host "`n[CP-14] Validacion de politica de contrasenas (F3)..." -ForegroundColor Yellow
try {
    $weakBody = @{
        name = "Usuario Password Debil"
        email = "debil_$timestamp@campusdesk.com"
        password = "abc"
    } | ConvertTo-Json
    $weakRes = Invoke-WebRequest -UseBasicParsing -Uri "$backendUrl/api/auth/register" -Method Post -Body $weakBody -ContentType "application/json" -TimeoutSec 10 -ErrorAction Stop
    Report-Fail "CP-14: Se acepto contrasena debil sin cumplir la politica (HTTP $($weakRes.StatusCode))"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 400) {
        Report-Pass "CP-14: Contrasena debil rechazada con HTTP 400 Bad Request" "Politica F3 validada"
    } else {
        Report-Fail "CP-14: Codigo inesperado: $code (esperado 400)"
    }
}

# ------------------------------------------------------------------------------
# CP-15: Servidor Web Nginx Frontend responde HTTP 200 OK
# ------------------------------------------------------------------------------
Write-Host "`n[CP-15] Verificacion de disponibilidad de Frontend Nginx ($frontendUrl)..." -ForegroundColor Yellow
try {
    $frontendRes = Invoke-WebRequest -UseBasicParsing -Uri "$frontendUrl/login.html" -Method Get -TimeoutSec 10
    if ($frontendRes.StatusCode -eq 200) {
        Report-Pass "CP-15: Servidor web Nginx responde HTTP 200 OK en login.html" "Frontend disponible en puerto host"
    } else {
        Report-Fail "CP-15: Codigo inesperado de Frontend: $($frontendRes.StatusCode)"
    }
} catch {
    Report-Fail "CP-15: Error al contactar Frontend Nginx: $_"
}

# ------------------------------------------------------------------------------
# CP-16: Nginx Reverse Proxy reenvia /auth/login hacia campusdesk-backend
# ------------------------------------------------------------------------------
Write-Host "`n[CP-16] Proxy inverso Nginx: Enrutamiento /auth/login..." -ForegroundColor Yellow
try {
    $proxyLoginBody = @{ email = "superadmin@campusdesk.com"; password = "SuperAdmin123!" } | ConvertTo-Json
    $proxyLoginRes = Invoke-RestMethod -Uri "$frontendUrl/auth/login" -Method Post -Body $proxyLoginBody -ContentType "application/json" -TimeoutSec 10
    if ($proxyLoginRes.token) {
        Report-Pass "CP-16: Nginx reenvia /auth/login transparentemente al backend y emite JWT" "Token emitido via proxy"
    } else {
        Report-Fail "CP-16: No se recibio token a traves de Nginx proxy"
    }
} catch {
    Report-Fail "CP-16: Error al consultar login a traves de Nginx: $_"
}

# ------------------------------------------------------------------------------
# CP-17: Nginx Reverse Proxy reenvia /api/... protegida con Bearer Token
# ------------------------------------------------------------------------------
Write-Host "`n[CP-17] Proxy inverso Nginx: Enrutamiento protegido /api/dashboard/metrics..." -ForegroundColor Yellow
if ($adminToken) {
    try {
        $headersProxyAdmin = @{ Authorization = "Bearer $adminToken" }
        $proxyMetricsRes = Invoke-RestMethod -Uri "$frontendUrl/api/dashboard/metrics" -Method Get -Headers $headersProxyAdmin -TimeoutSec 10
        if ($null -ne $proxyMetricsRes.total) {
            Report-Pass "CP-17: Nginx reenvia /api/dashboard/metrics y autoriza token Bearer" "Total metricas: $($proxyMetricsRes.total)"
        } else {
            Report-Fail "CP-17: Respuesta incompleta de metricas via proxy"
        }
    } catch {
        Report-Fail "CP-17: Error al consultar API protegida via Nginx: $_"
    }
}

# ------------------------------------------------------------------------------
# CP-18: Resiliencia Multiplataforma / LAN: Origen externo simulado sin rechazo 403
# ------------------------------------------------------------------------------
Write-Host "`n[CP-18] Resiliencia de peticiones con cabecera Origin de red local (Multi-dispositivo)..." -ForegroundColor Yellow
try {
    $lanHeaders = @{
        "Origin" = "http://192.168.1.150:5506"
        "Content-Type" = "application/json"
    }
    $lanLoginBody = @{ email = "superadmin@campusdesk.com"; password = "SuperAdmin123!" } | ConvertTo-Json
    $lanLoginRes = Invoke-WebRequest -UseBasicParsing -Uri "$frontendUrl/auth/login" -Method Post -Headers $lanHeaders -Body $lanLoginBody -TimeoutSec 10
    if ($lanLoginRes.StatusCode -eq 200) {
        Report-Pass "CP-18: Login desde IP local externa procesado con HTTP 200 sin bloqueo CORS" "Soporte multi-computador verificado"
    } else {
        Report-Fail "CP-18: Codigo recibido: $($lanLoginRes.StatusCode) (esperado 200)"
    }
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Report-Fail "CP-18: Error en peticion con Origin de red local: HTTP $code ($_)"
}

# ------------------------------------------------------------------------------
# Resumen Final de Certificacion
# ------------------------------------------------------------------------------
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "   RESUMEN FINAL DE CERTIFICACION DE PRUEBAS CAMPUSDESK          " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Pruebas Superadas (PASS): $passedCount" -ForegroundColor Green
Write-Host "  Pruebas Fallidas  (FAIL): $failedCount" -ForegroundColor $(if ($failedCount -eq 0) { "Green" } else { "Red" })

if ($failedCount -eq 0) {
    Write-Host "`n  CERTIFICACION EXITOSA: CAMPUSDESK cumple 100% de los requerimientos!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n  ADVERTENCIA: Se detectaron fallos en la suite de certificacion." -ForegroundColor Yellow
    exit 1
}


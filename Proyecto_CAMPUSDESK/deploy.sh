#!/usr/bin/env bash
# ==============================================================================
# CAMPUSDESK - Script de Despliegue Automatizado (Bash / Linux / macOS / Git Bash)
# TechNova Solutions
# ==============================================================================

set -e

echo "=========================================================="
echo "   CAMPUSDESK - Despliegue Orquestado con Docker Compose  "
echo "   PostgreSQL 16 + Redis 7 + Spring Boot 4 + Nginx        "
echo "   TechNova Solutions                                     "
echo "=========================================================="

# 1. Verificar motor Docker
echo -e "\n[1/5] Verificando estado del motor Docker..."
if ! docker info > /dev/null 2>&1; then
    echo "ERROR: El daemon de Docker no está en ejecución. Por favor inicia Docker Desktop."
    exit 1
fi
echo " -> Motor Docker activo y listo."

# 2. Verificar archivo .env
echo -e "\n[2/5] Verificando variables de entorno (.env)..."
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        echo " -> Creando .env a partir de .env.example..."
        cp .env.example .env
    else
        echo "ERROR: No se encontró .env ni .env.example."
        exit 1
    fi
fi
echo " -> Archivo .env cargado correctamente."

# 3. Construir imágenes
echo -e "\n[3/5] Construyendo imágenes de contenedores..."
docker compose build

# 4. Iniciar servicios
echo -e "\n[4/5] Levantando servicios en segundo plano..."
docker compose up -d

# 5. Esperar healthchecks
echo -e "\n[5/5] Monitoreando inicialización de servicios..."
TIMEOUT=60
ELAPSED=0

while [ $ELAPSED -lt $TIMEOUT ]; do
    DB_STATUS=$(docker inspect --format='{{json .State.Health.Status}}' campusdesk-db 2>/dev/null || echo "")
    REDIS_STATUS=$(docker inspect --format='{{json .State.Health.Status}}' campusdesk-redis 2>/dev/null || echo "")
    if [ "$DB_STATUS" = "\"healthy\"" ] && [ "$REDIS_STATUS" = "\"healthy\"" ]; then
        echo " -> Base de datos PostgreSQL y Redis 7 saludables y operativos!"
        break
    fi
    sleep 3
    ELAPSED=$((ELAPSED + 3))
    echo " -> Esperando healthchecks ($ELAPSED segs)..."
done

echo -e "\n=========================================================="
echo "   ESTADO ACTUAL DE LOS SERVICIOS DE CAMPUSDESK           "
echo "=========================================================="
docker compose ps

echo -e "\nAccesos disponibles:"
echo "  * Frontend Web:   http://localhost:5506"
echo "  * Backend API:    http://localhost:8056/swagger-ui.html"
echo "  * PostgreSQL:     localhost:5436 (BD: sistema_gestion_usuarios)"
echo "  * Redis Caché:    localhost:6385"
echo -e "\nComandos de utilidad:"
echo "  * Ver logs:       docker compose logs -f"
echo "  * Detener todo:   docker compose down"
echo "  * Verificación:   ./verify.sh"

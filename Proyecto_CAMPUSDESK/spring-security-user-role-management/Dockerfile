# ==============================================================================
# STAGE 1: Builder (Compilación y generación del JAR ejecutable)
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Instalar bash para compatibilidad con el wrapper de Gradle
RUN apk add --no-cache bash

# Copiar archivos de definición y wrapper de Gradle primero para optimizar caché Docker
COPY gradle ./gradle
COPY gradlew build.gradle settings.gradle ./

# Normalizar saltos de línea (eliminar CRLF de entornos Windows) y asignar permisos
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew

# Variables de entorno para asegurar codificación UTF-8 y memoria adecuada durante la compilación
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    GRADLE_OPTS="-Dorg.gradle.jvmargs=\"-Xmx1024m -XX:MaxMetaspaceSize=384m\" -Dfile.encoding=UTF-8"

# Descargar dependencias en caché (sin ejecutar compilación de código fuente)
RUN ./gradlew dependencies --no-daemon || true

# Copiar código fuente del backend
COPY src ./src

# Compilar y empaquetar el artefacto Spring Boot optimizado (omitiendo tests en build de contenedor)
RUN ./gradlew bootJar --no-daemon -x test

# ==============================================================================
# STAGE 2: Runtime (Entorno de ejecución ligero JRE 21 Alpine)
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="TechNova Solutions - CAMPUSDESK DevOps Team"
LABEL application="campusdesk-backend"
LABEL version="0.0.1-SNAPSHOT"

WORKDIR /app

# Crear usuario y grupo de sistema sin privilegios de root (seguridad y menor privilegio)
RUN addgroup -S campusgroup && adduser -S campususer -G campusgroup

# Copiar el JAR generado desde la etapa de construcción
COPY --from=builder /build/build/libs/*.jar app.jar

# Asignar permisos de ejecución al usuario no privilegiado
RUN chown -R campususer:campusgroup /app

# Puerto interno por defecto del backend
EXPOSE 8056

# Cambiar a usuario no root
USER campususer

# Parámetros optimizados de JVM para entornos de contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Comando de arranque del microservicio Spring Boot
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

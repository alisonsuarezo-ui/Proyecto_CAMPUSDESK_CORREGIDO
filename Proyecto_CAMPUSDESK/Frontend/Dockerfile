# ==============================================================================
# CAMPUSDESK - Dockerfile para Frontend Ligero con Nginx Alpine
# TechNova Solutions
# ==============================================================================
FROM nginx:1.27-alpine

LABEL maintainer="TechNova Solutions - CAMPUSDESK DevOps Team"
LABEL application="campusdesk-frontend"
LABEL version="1.0.0"

# Limpiar archivos por defecto de la distribución de Nginx
RUN rm -rf /etc/nginx/conf.d/* /usr/share/nginx/html/*

# Copiar configuración de Nginx con MIME types, compresión gzip y headers de seguridad
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copiar archivos estáticos HTML5, CSS3 y JavaScript
COPY . /usr/share/nginx/html/

# Limpiar archivos de configuración que no necesitan estar expuestos en el document root
RUN rm -f /usr/share/nginx/html/Dockerfile /usr/share/nginx/html/nginx.conf /usr/share/nginx/html/README.md

# Exponer el puerto HTTP interno del contenedor
EXPOSE 80

# Mecanismo de healthcheck para validar que el servicio web responde peticiones HTTP
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1

# Ejecutar Nginx en primer plano
CMD ["nginx", "-g", "daemon off;"]

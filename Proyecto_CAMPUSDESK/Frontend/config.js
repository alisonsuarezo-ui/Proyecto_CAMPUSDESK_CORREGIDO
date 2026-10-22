/**
 * CAMPUSDESK - Configuración Central de Conexión Pública
 * TechNova Solutions
 *
 * Centraliza la resolución de la URL base del Backend (API REST).
 * Prioriza solicitudes relativas al mismo origen cuando el frontend se sirve
 * mediante Nginx (Docker / Red Local / Producción), eliminando dependencias
 * de direcciones IP fijas, puertos codificados o valores obsoletos en localStorage.
 */
(function () {
  'use strict';

  // Configuración global opcional para despliegues con backend en dominio desacoplado
  window.CAMPUSDESK_CONFIG = window.CAMPUSDESK_CONFIG || {
    API_URL: ''
  };

  /**
   * Resuelve la URL base del backend evaluando prioridades:
   * 1. Parámetro de consulta explícito (?api=https://... o ?backend=https://...)
   * 2. Configuración declarativa explícita (window.CAMPUSDESK_CONFIG.API_URL)
   * 3. Mismo origen (Nginx reverse proxy en Docker / LAN / Cloud): window.location.origin
   * 4. Entorno de desarrollo desacoplado sin proxy (ej. Live Server en puerto 5500 o file://) -> http://localhost:8056
   */
  window.getCampusDeskApiUrl = function () {
    // 1. Prioridad: Parámetro explícito en la URL
    try {
      const urlParams = new URLSearchParams(window.location.search);
      const queryApi = urlParams.get('api') || urlParams.get('backend');
      if (queryApi && queryApi.trim()) {
        const cleanQuery = queryApi.trim().replace(/\/+$/, '');
        try { localStorage.setItem('api_url', cleanQuery); } catch (_) {}
        return cleanQuery;
      }
    } catch (_) {
      // Ignorar excepciones de URLSearchParams
    }

    // 2. Prioridad: Configuración explícita en variable global
    if (window.CAMPUSDESK_CONFIG && window.CAMPUSDESK_CONFIG.API_URL && window.CAMPUSDESK_CONFIG.API_URL.trim()) {
      return window.CAMPUSDESK_CONFIG.API_URL.trim().replace(/\/+$/, '');
    }

    // Limpiar localStorage de valores obsoletos de localhost para evitar que rompan accesos remotos
    try {
      const stored = localStorage.getItem('api_url');
      if (stored && (stored.includes('localhost:8056') || stored.includes('127.0.0.1:8056'))) {
        localStorage.removeItem('api_url');
      }
    } catch (_) {}

    // 3. Prioridad Principal: Si se sirve vía HTTP/HTTPS (Nginx Docker, LAN IP, Cloud)
    // El frontend y el proxy inverso conviven en el mismo origen: usar window.location.origin
    if (window.location && window.location.origin && window.location.protocol.startsWith('http')) {
      // Caso especial: desarrollo en Live Server (puerto 5500 sin proxy Nginx)
      if (window.location.port === '5500') {
        return 'http://localhost:8056';
      }
      return window.location.origin;
    }

    // 4. Fallback final para apertura local directa (file://)
    return 'http://localhost:8056';
  };
})();

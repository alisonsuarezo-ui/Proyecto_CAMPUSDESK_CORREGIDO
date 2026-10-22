/**
 * CAMPUSDESK - Guardián de Autenticación (auth-guard.js)
 *
 * Incluye este archivo en la cabecera o al inicio de las páginas protegidas
 * (por ejemplo dashboard.html, tickets.html) mediante:
 * <script src="auth-guard.js"></script>
 */

(function () {
  'use strict';

  // 1. Verificación inmediata de sesión
  const token = sessionStorage.getItem('token');

  if (!token) {
    // Si no hay token en el almacenamiento de sesión, redirigir al login
    window.location.replace('login.html');
    return;
  }

  // 2. Objeto global de utilidades de autenticación para el equipo
  window.AuthGuard = {
    /**
     * Retorna el token JWT actual.
     */
    getToken: function () {
      return sessionStorage.getItem('token');
    },

    /**
     * Retorna el rol del usuario ('ADMIN', 'TECHNICIAN', 'USER').
     */
    getRole: function () {
      return sessionStorage.getItem('role') || 'USER';
    },

    /**
     * Retorna el correo del usuario actual si está guardado.
     */
    getUserEmail: function () {
      return sessionStorage.getItem('userEmail') || '';
    },

    /**
     * Comprueba si el usuario tiene un rol determinado.
     * @param {string} roleName
     */
    hasRole: function (roleName) {
      return this.getRole() === roleName;
    },

    /**
     * Cierra la sesión activa notificando al backend (/auth/logout) para revocar
     * el token en Redis. Si la petición falla, limpia igualmente la sesión local de forma segura.
     */
    logout: async function () {
      const token = this.getToken();
      const baseUrl = (typeof window.getCampusDeskApiUrl === 'function')
        ? window.getCampusDeskApiUrl()
        : (window.location.protocol.startsWith('http') ? window.location.origin : 'http://localhost:8056');

      if (token) {
        try {
          await fetch(`${baseUrl}/auth/logout`, {
            method: 'POST',
            headers: {
              'Authorization': 'Bearer ' + token,
              'Content-Type': 'application/json'
            }
          });
        } catch (e) {
          console.warn('Error al notificar logout al backend:', e);
        }
      }

      // Limpieza segura de todas las claves de sesión locales
      sessionStorage.removeItem('token');
      sessionStorage.removeItem('jwt_token');
      sessionStorage.removeItem('role');
      sessionStorage.removeItem('user_role');
      sessionStorage.removeItem('userEmail');
      sessionStorage.removeItem('user_email');
      window.location.replace('login.html');
    },

    /**
     * Wrapper para realizar peticiones fetch autenticadas agregando el header Bearer.
     * @param {string} url
     * @param {RequestInit} options
     */
    fetchWithAuth: async function (url, options = {}) {
      const headers = options.headers ? new Headers(options.headers) : new Headers();
      headers.set('Authorization', 'Bearer ' + this.getToken());

      if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
        headers.set('Content-Type', 'application/json');
      }

      const response = await fetch(url, {
        ...options,
        headers: headers
      });

      // Si el token expiró o es inválido (401), cerrar sesión automáticamente
      if (response.status === 401) {
        console.warn('Sesión expirada o token inválido (401). Redirigiendo a login...');
        await this.logout();
        return null;
      }

      return response;
    }
  };

})();

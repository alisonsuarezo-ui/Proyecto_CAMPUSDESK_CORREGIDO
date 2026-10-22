/**
 * CAMPUSDESK - Lógica de Frontend Vanilla JS (TechNova Solutions)
 * Arquitectura: HTML5 + CSS3 + Fetch API nativa. Sin frameworks externos.
 * Ciclo de vida de incidencias, máquina de estados, métricas, comentarios, auditoría y administración.
 */
'use strict';

(function () {
  // 1. Configuración de API y URLs
  const BASE_URL = (typeof window.getCampusDeskApiUrl === 'function')
    ? window.getCampusDeskApiUrl()
    : (window.location.protocol.startsWith('http') ? window.location.origin : 'http://localhost:8056');

  // 2. Estado de la Aplicación
  const AppState = {
    currentUser: null,
    tickets: [],
    technicians: [],
    users: [],
    activeTicket: null,
    activeFilterStatus: '',
    activeFilterPriority: '',
    activeFilterCategory: '',
    activeFilterTag: '',
    showReopened: false,
    templates: [],
    searchQuery: '',
    userFilterRole: '',
    userSearchQuery: ''
  };

  // 3. Utilidades y Helpers
  function getHeaders() {
    const token = AuthGuard.getToken();
    return {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token
    };
  }

  let isHandlingSessionExpiry = false;

  /**
   * Cliente HTTP centralizado para peticiones protegidas.
   * Intercepta respuestas 401 (sesión expirada o token revocado) redirigiendo ordenadamente a login.
   */
  async function apiFetch(url, options = {}) {
    const headers = Object.assign({}, getHeaders(), options.headers || {});
    try {
      const resp = await fetch(url, { ...options, headers });
      if (resp.status === 401) {
        console.warn('Sesión expirada o token inválido (401). Redirigiendo a login...');
        if (!isHandlingSessionExpiry) {
          isHandlingSessionExpiry = true;
          showToast('Tu sesión ha expirado. Redirigiendo al inicio de sesión...', 'error');
          setTimeout(async () => {
            await AuthGuard.logout();
          }, 1200);
        }
        return null;
      }
      return resp;
    } catch (err) {
      throw err;
    }
  }

  function formatDate(isoStr) {
    if (!isoStr) return '—';
    try {
      const d = new Date(isoStr);
      return d.toLocaleDateString('es-CO', {
        year: 'numeric',
        month: 'short',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch {
      return isoStr;
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function showToast(message, type = 'success') {
    const toast = document.getElementById('toastBox');
    const msg = document.getElementById('toastMessage');
    if (!toast || !msg) return;

    msg.textContent = message;
    toast.className = `campus-toast show ${type}`;

    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => {
      toast.className = 'campus-toast';
    }, 3800);
  }

  // 4. Inicialización de Usuario y Perfil
  async function initUserProfile() {
    const role = AuthGuard.getRole();
    const storedEmail = AuthGuard.getUserEmail();

    const roleBadge = document.getElementById('navUserRole');
    const emailSpan = document.getElementById('navUserEmail');
    const btnNavAdmin = document.getElementById('btnNavAdmin');

    if (roleBadge) {
      roleBadge.textContent = role;
      roleBadge.className = `role-pill ${role.toLowerCase()}`;
    }
    if (emailSpan) {
      emailSpan.textContent = storedEmail || 'Usuario';
    }

    // F2: Habilitar botón de navegación a Administración solo para ADMIN
    if (btnNavAdmin) {
      if (role === 'ADMIN') {
        btnNavAdmin.style.display = 'inline-flex';
      } else {
        btnNavAdmin.style.display = 'none';
      }
    }

    try {
      const resp = await apiFetch(`${BASE_URL}/api/users/me`);
      if (!resp) return;
      if (resp.ok) {
        const user = await resp.json();
        AppState.currentUser = user;
        if (emailSpan) emailSpan.textContent = user.name || user.email;
        if (roleBadge) {
          roleBadge.textContent = user.role;
          roleBadge.className = `role-pill ${user.role.toLowerCase()}`;
        }
        if (btnNavAdmin) {
          btnNavAdmin.style.display = user.role === 'ADMIN' ? 'inline-flex' : 'none';
        }
      }
    } catch (e) {
      console.warn('No se pudo refrescar el perfil desde el backend:', e);
    }
  }

  // 5. Carga de Técnicos (E4: Exclusivo para ADMIN para asignación de incidencias)
  async function loadTechnicians() {
    if (AuthGuard.getRole() !== 'ADMIN') {
      return;
    }
    try {
      const resp = await apiFetch(`${BASE_URL}/api/users/technicians`);
      if (!resp) return;
      if (resp.ok) {
        AppState.technicians = await resp.json();
      }
    } catch (e) {
      console.warn('Error al cargar lista de técnicos:', e);
    }
  }

  // 5.1 Carga de Todos los Usuarios (F2: Exclusivo para ADMIN)
  async function loadAdminUsers() {
    if (AuthGuard.getRole() !== 'ADMIN') return;
    const tbody = document.getElementById('usersTableBody');
    if (tbody) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align: center; padding: 30px; color: #64748B;">
            Consultando usuarios registrados...
          </td>
        </tr>
      `;
    }

    try {
      const resp = await apiFetch(`${BASE_URL}/api/users`);
      if (!resp || !resp.ok) {
        if (tbody) {
          tbody.innerHTML = `
            <tr>
              <td colspan="5" style="text-align: center; padding: 30px; color: #E11D48;">
                Error al cargar usuarios del servidor.
              </td>
            </tr>
          `;
        }
        return;
      }

      AppState.users = await resp.json();
      renderAdminUsersTable();
    } catch (e) {
      console.error('Error cargando usuarios:', e);
      if (tbody) {
        tbody.innerHTML = `
          <tr>
            <td colspan="5" style="text-align: center; padding: 30px; color: #E11D48;">
              Fallo de conexión al cargar usuarios.
            </td>
          </tr>
        `;
      }
    }
  }

  function renderAdminUsersTable() {
    const tbody = document.getElementById('usersTableBody');
    if (!tbody) return;

    const query = AppState.userSearchQuery.toLowerCase().trim();
    const roleFilter = AppState.userFilterRole;

    const filtered = AppState.users.filter(u => {
      const matchesRole = !roleFilter || u.role === roleFilter;
      const matchesQuery = !query ||
        (u.name || '').toLowerCase().includes(query) ||
        (u.email || '').toLowerCase().includes(query) ||
        String(u.id).includes(query);
      return matchesRole && matchesQuery;
    });

    if (filtered.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align: center; padding: 35px; color: #64748B;">
            No se encontraron usuarios coincidentes con el criterio de búsqueda.
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = filtered.map(u => {
      const roleClass = (u.role || 'USER').toLowerCase();
      return `
        <tr>
          <td style="font-weight: 700; color: #475569;">#${u.id}</td>
          <td style="font-weight: 600; color: #0F172A;">${escapeHtml(u.name)}</td>
          <td style="color: #475569;">${escapeHtml(u.email)}</td>
          <td><span class="role-pill ${roleClass}">${u.role}</span></td>
          <td style="text-align: right; white-space: nowrap;">
            <button class="btn-campus-secondary" style="padding: 5px 10px; font-size: 12px;" onclick="window.CampusDesk.filterTicketsByUser('${escapeHtml(u.email)}')">
              Ver Incidencias
            </button>
          </td>
        </tr>
      `;
    }).join('');
  }

  // 6. Carga de Métricas del Dashboard (RF-07)
  async function loadDashboardMetrics() {
    try {
      const resp = await apiFetch(`${BASE_URL}/api/dashboard/metrics`);
      if (!resp || !resp.ok) return;

      const data = await resp.json();
      const mTotal = document.getElementById('metricTotal');
      const mAbiertas = document.getElementById('metricAbiertas');
      const mAsignadas = document.getElementById('metricAsignadas');
      const mProceso = document.getElementById('metricEnProceso');
      const mResueltas = document.getElementById('metricResueltas');
      const mCerradas = document.getElementById('metricCerradas');

      if (mTotal) mTotal.textContent = data.total ?? 0;
      if (mAbiertas) mAbiertas.textContent = data.abiertas ?? 0;
      if (mAsignadas) mAsignadas.textContent = data.asignadas ?? 0;
      if (mProceso) mProceso.textContent = data.enProceso ?? 0;
      if (mResueltas) mResueltas.textContent = data.resueltas ?? 0;
      if (mCerradas) mCerradas.textContent = data.cerradas ?? 0;
    } catch (e) {
      console.error('Error cargando métricas:', e);
    }
  }

  // 7. Carga y Filtrado de Incidencias (RF-03, RF-08)
  async function loadTickets() {
    const tbody = document.getElementById('ticketsTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
      <tr>
        <td colspan="9" style="text-align: center; padding: 30px; color: #64748B;">
          Actualizando lista de incidencias...
        </td>
      </tr>
    `;

    try {
      const params = new URLSearchParams();
      if (AppState.activeFilterStatus) params.append('status', AppState.activeFilterStatus);
      if (AppState.activeFilterPriority) params.append('priority', AppState.activeFilterPriority);
      if (AppState.activeFilterCategory) params.append('category', AppState.activeFilterCategory);

      // Ejercicio 1: si hay etiqueta, se usa el endpoint /por-tag y el resto de filtros se aplica en cliente
      const tagFilter = (AppState.activeFilterTag || '').trim();
      const useAlt = AppState.showReopened || Boolean(tagFilter);
      let url = `${BASE_URL}/api/tickets?${params.toString()}`;
      if (AppState.showReopened) {
        url = `${BASE_URL}/api/tickets/reabiertos`;
      } else if (tagFilter) {
        url = `${BASE_URL}/api/tickets/por-tag?nombre=${encodeURIComponent(tagFilter)}`;
      }
      const resp = await apiFetch(url);
      if (!resp) return;

      if (!resp.ok) {
        tbody.innerHTML = `
          <tr>
            <td colspan="9" style="text-align: center; padding: 30px; color: #E11D48;">
              Error al obtener incidencias del servidor (${resp.status}).
            </td>
          </tr>
        `;
        return;
      }

      let data = await resp.json();
      if (useAlt) {
        data = data.filter(t =>
          (!tagFilter || (t.tags || []).includes(tagFilter.toUpperCase())) &&
          (!AppState.activeFilterStatus || t.status === AppState.activeFilterStatus) &&
          (!AppState.activeFilterPriority || t.priority === AppState.activeFilterPriority) &&
          (!AppState.activeFilterCategory || t.category === AppState.activeFilterCategory));
      }
      AppState.tickets = data;
      renderTicketsTable();
    } catch (e) {
      tbody.innerHTML = `
        <tr>
          <td colspan="9" style="text-align: center; padding: 30px; color: #E11D48;">
            No se pudo conectar con el servicio de tickets.
          </td>
        </tr>
      `;
      console.error(e);
    }
  }

  function renderTicketsTable() {
    const tbody = document.getElementById('ticketsTableBody');
    if (!tbody) return;

    const query = AppState.searchQuery.toLowerCase().trim();

    const filtered = AppState.tickets.filter(t => {
      if (!query) return true;
      const titleMatch = (t.title || '').toLowerCase().includes(query);
      const descMatch = (t.description || '').toLowerCase().includes(query);
      const idMatch = String(t.id).includes(query);
      const reqMatch = t.solicitante && (t.solicitante.name || t.solicitante.email || '').toLowerCase().includes(query);
      const techMatch = t.tecnico && (t.tecnico.name || t.tecnico.email || '').toLowerCase().includes(query);
      const tagMatch = (t.tags || []).some(tg => String(tg).toLowerCase().includes(query));
      return titleMatch || descMatch || idMatch || reqMatch || techMatch || tagMatch;
    });

    if (filtered.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="9" style="text-align: center; padding: 40px; color: #64748B;">
            <div style="font-size: 24px; margin-bottom: 8px;">📋</div>
            <div style="font-weight: 600; font-size: 15px; color: #334155;">No se encontraron incidencias</div>
            <div style="font-size: 13px; margin-top: 4px;">Modifica los filtros o crea una nueva incidencia con el botón superior.</div>
          </td>
        </tr>
      `;
      return;
    }

    const currentRole = AuthGuard.getRole();
    const currentEmail = AuthGuard.getUserEmail();

    tbody.innerHTML = filtered.map(t => {
      const id = t.id;
      const title = escapeHtml(t.title);
      const cat = t.category || 'OTROS';
      const prio = t.priority || 'MEDIA';
      const status = t.status || 'ABIERTA';
      const solicitante = t.solicitante ? escapeHtml(t.solicitante.name || t.solicitante.email) : '—';
      const tecnico = t.tecnico ? escapeHtml(t.tecnico.name || t.tecnico.email) : '<span style="color: #94A3B8; font-style: italic;">Sin asignar</span>';
      const fecha = formatDate(t.createdAt);
      const tagsHtml = renderTagChips(t.tags);
      const reopenBadge = t.reaperturasCount > 0 ? `<span class="reopen-badge" title="Veces reabierto">↻ ${t.reaperturasCount}</span>` : '';

      // Acciones rápidas según rol y estado estricto
      let shortcutBtn = '';

      // F1: Solicitante puede editar si está ABIERTA y sin técnico asignado
      const isRequester = t.solicitante && t.solicitante.email === currentEmail;
      if (isRequester && status === 'ABIERTA' && !t.tecnico) {
        shortcutBtn = `<button class="btn-campus-secondary" style="padding: 4px 8px; font-size: 11.5px; margin-right: 6px; border-color: #3B82F6; color: #1D4ED8;" onclick="window.CampusDesk.openEditModal(${id})">Editar</button>`;
      } else if (currentRole === 'ADMIN' && (status === 'ABIERTA' || status === 'ASIGNADA')) {
        shortcutBtn = `<button class="btn-campus-secondary" style="padding: 4px 8px; font-size: 11.5px; margin-right: 6px;" onclick="window.CampusDesk.openAssignQuick(${id})">Asignar</button>`;
      } else if (currentRole === 'TECHNICIAN' && status === 'ASIGNADA' && t.tecnico && t.tecnico.email === currentEmail) {
        shortcutBtn = `<button class="btn-campus-primary" style="padding: 4px 8px; font-size: 11.5px; margin-right: 6px;" onclick="window.CampusDesk.quickChangeStatus(${id}, 'EN_PROCESO', 'Iniciando atención')">Atender</button>`;
      } else if (currentRole === 'TECHNICIAN' && status === 'EN_PROCESO' && t.tecnico && t.tecnico.email === currentEmail) {
        shortcutBtn = `<button class="btn-campus-primary" style="padding: 4px 8px; font-size: 11.5px; margin-right: 6px; background: #059669;" onclick="window.CampusDesk.quickChangeStatus(${id}, 'RESUELTA', 'Incidencia resuelta')">Resolver</button>`;
      } else if (status === 'RESUELTA' && isRequester) {
        // E2: Solo el solicitante puede cerrar la incidencia
        shortcutBtn = `<button class="btn-campus-secondary" style="padding: 4px 8px; font-size: 11.5px; margin-right: 6px; border-color: #065F46; color: #065F46;" onclick="window.CampusDesk.quickChangeStatus(${id}, 'CERRADA', 'Cierre confirmado')">Cerrar</button>`;
      }

      return `
        <tr>
          <td style="font-weight: 700; color: #475569;">#${id}</td>
          <td>
            <div style="font-weight: 600; color: #0F172A;">${title}${reopenBadge}</div>
            ${tagsHtml ? `<div class="tag-list" style="margin-top: 5px;">${tagsHtml}</div>` : ''}
          </td>
          <td><span class="badge badge-cat">${cat}</span></td>
          <td><span class="badge badge-prio-${prio}">${prio}</span></td>
          <td><span class="badge badge-status-${status}">${status.replace('_', ' ')}</span></td>
          <td style="font-size: 13px;">${solicitante}</td>
          <td style="font-size: 13px;">${tecnico}</td>
          <td style="font-size: 12.5px; color: #64748B;">${fecha}</td>
          <td style="text-align: right; white-space: nowrap;">
            ${shortcutBtn}
            <button class="btn-campus-secondary" style="padding: 5px 10px; font-size: 12px;" onclick="window.CampusDesk.openDetailModal(${id})">
              Ver Detalle
            </button>
          </td>
        </tr>
      `;
    }).join('');
  }

  // Ejercicio 1: Etiquetas (Tags)
  function renderTagChips(tags) {
    if (!tags || tags.length === 0) return '';
    return Array.from(tags).sort().map(tg =>
      `<span class="tag-chip" title="Filtrar por esta etiqueta" onclick="window.CampusDesk.filterByTag('${escapeHtml(tg).replace(/'/g, '')}')">${escapeHtml(tg)}</span>`
    ).join('');
  }

  function renderTagsPanel(ticket) {
    const list = document.getElementById('detailTags');
    const editor = document.getElementById('tagEditor');
    if (!list || !editor) return;

    const chips = renderTagChips(ticket.tags);
    list.innerHTML = chips || '<span class="tag-empty">Sin etiquetas</span>';

    // Backend: solo ADMIN o el TECHNICIAN asignado pueden asociar etiquetas
    const role = AuthGuard.getRole();
    const email = AuthGuard.getUserEmail();
    const isAssignedTech = ticket.tecnico && ticket.tecnico.email === email;
    const canEdit = (role === 'ADMIN' || isAssignedTech) && ticket.status !== 'CERRADA';

    if (!canEdit) {
      editor.style.display = 'none';
      editor.innerHTML = '';
      return;
    }
    editor.style.display = 'block';
    editor.innerHTML = `
      <div class="tag-editor-row">
        <input type="text" id="newTagInput" maxlength="200" placeholder="Nueva(s) etiqueta(s), separadas por coma (ej. VPN, WIFI)">
        <button type="button" class="btn-campus-primary" style="padding: 9px 16px; font-size: 13.5px;" onclick="window.CampusDesk.addTags(${ticket.id})">
          Agregar etiquetas
        </button>
      </div>`;
    const input = document.getElementById('newTagInput');
    if (input) {
      input.addEventListener('keydown', ev => {
        if (ev.key === 'Enter') { ev.preventDefault(); addTags(ticket.id); }
      });
    }
  }

  async function addTags(ticketId) {
    const input = document.getElementById('newTagInput');
    if (!input) return;
    const names = input.value.split(',').map(x => x.trim()).filter(x => x.length > 0);
    if (names.length === 0) {
      showToast('Escribe al menos una etiqueta', 'error');
      return;
    }
    if (names.some(n => n.length > 50)) {
      showToast('Cada etiqueta debe tener máximo 50 caracteres', 'error');
      return;
    }
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/tags`, {
        method: 'POST',
        body: JSON.stringify(names)
      });
      if (!resp) return;
      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        showToast((err && (err.message || err.error)) || 'No se pudieron asociar las etiquetas', 'error');
        return;
      }
      const updated = await resp.json();
      AppState.activeTicket = updated;
      showToast('Etiquetas asociadas correctamente', 'success');
      renderTagsPanel(updated);
      await loadTickets();
    } catch (e) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(e);
    }
  }

  function filterByTag(tagName) {
    AppState.activeFilterTag = tagName || '';
    const input = document.getElementById('filterTag');
    if (input) input.value = AppState.activeFilterTag;
    closeModal('modalDetailTicket');
    loadTickets();
  }


  // =====================================================================
  // Ejercicios 2-5: Reapertura, Transferencia, Tiempo Técnico, Plantillas
  // =====================================================================
  const CATEGORIES = ['HARDWARE', 'SOFTWARE', 'REDES', 'ACCESOS', 'OTROS'];

  async function readError(resp, fallback) {
    const err = await resp.json().catch(() => null);
    return (err && (err.message || err.error)) || fallback;
  }

  async function refreshAfterChange(ticketId) {
    await loadDashboardMetrics();
    await loadTickets();
    if (ticketId) await openDetailModal(ticketId);
  }

  // ---- Ejercicios 2 y 3: panel de acciones extra en el detalle
  function renderExtraActions(ticket) {
    const box = document.getElementById('extraActions');
    if (!box) return;
    const role = AuthGuard.getRole();
    const email = AuthGuard.getUserEmail();
    const isRequester = ticket.solicitante && ticket.solicitante.email === email;
    const isAssignedTech = ticket.tecnico && ticket.tecnico.email === email;
    let html = '';

    if (ticket.reaperturasCount > 0) {
      html += `<div style="margin-bottom: 10px;"><span class="reopen-badge">↻ Reabierto ${ticket.reaperturasCount} vez/veces</span></div>`;
    }

    // Ejercicio 2: solo el USER creador, ticket RESUELTA
    if (role === 'USER' && isRequester && ticket.status === 'RESUELTA') {
      html += `
        <div class="extra-box warn">
          <div class="extra-box-title">↻ ¿La solución no fue satisfactoria? Reabrir incidencia</div>
          <p style="font-size: 12.5px; color: #92400E; margin-bottom: 8px;">
            Solo es posible dentro de las 48 horas posteriores a la resolución. Pasado ese plazo debes crear un nuevo ticket.
          </p>
          <div class="extra-row">
            <input type="text" id="reopenMotivo" maxlength="250" placeholder="Motivo de la reapertura (obligatorio, máx. 250)" style="flex: 1; min-width: 240px;">
            <button type="button" class="btn-campus-primary" style="background: #D97706;" onclick="window.CampusDesk.reopenTicket(${ticket.id})">Reabrir incidencia</button>
          </div>
        </div>`;
    }

    // Ejercicio 3: ADMIN o técnico asignado, ticket no cerrado
    if ((role === 'ADMIN' || isAssignedTech) && ticket.status !== 'CERRADA') {
      const options = CATEGORIES.filter(c => c !== ticket.category)
        .map(c => `<option value="${c}">${c}</option>`).join('');
      html += `
        <div class="extra-box">
          <div class="extra-box-title">⇄ Transferir a otra área (categoría actual: ${escapeHtml(ticket.category)})</div>
          <p style="font-size: 12.5px; color: #64748B; margin-bottom: 8px;">
            Al transferir, se desvincula al técnico asignado y el ticket vuelve al estado ABIERTA.
          </p>
          <div class="extra-row">
            <select id="transferCategory">${options}</select>
            <input type="text" id="transferJustificacion" maxlength="250" placeholder="Justificación (obligatoria, máx. 250)" style="flex: 1; min-width: 220px;">
            <button type="button" class="btn-campus-secondary" style="border-color: #3B82F6; color: #1D4ED8; font-weight: 600;" onclick="window.CampusDesk.transferTicket(${ticket.id})">Transferir</button>
          </div>
        </div>`;
    }
    box.innerHTML = html;
  }

  async function reopenTicket(ticketId) {
    const input = document.getElementById('reopenMotivo');
    const motivo = input ? input.value.trim() : '';
    if (!motivo) { showToast('El motivo de la reapertura es obligatorio', 'error'); return; }
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/reabrir`, {
        method: 'POST', body: JSON.stringify({ motivo })
      });
      if (!resp) return;
      if (!resp.ok) { showToast(await readError(resp, 'No se pudo reabrir la incidencia'), 'error'); return; }
      showToast('Incidencia reabierta: pasó a EN PROCESO', 'success');
      await refreshAfterChange(ticketId);
    } catch (e) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(e);
    }
  }

  async function transferTicket(ticketId) {
    const sel = document.getElementById('transferCategory');
    const inp = document.getElementById('transferJustificacion');
    const nuevaCategoria = sel ? sel.value : '';
    const justificacion = inp ? inp.value.trim() : '';
    if (!nuevaCategoria) { showToast('Selecciona la nueva categoría', 'error'); return; }
    if (!justificacion) { showToast('La justificación es obligatoria', 'error'); return; }
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/transferir`, {
        method: 'PUT', body: JSON.stringify({ nuevaCategoria, justificacion })
      });
      if (!resp) return;
      if (!resp.ok) { showToast(await readError(resp, 'No se pudo transferir la incidencia'), 'error'); return; }
      showToast(`Incidencia transferida a ${nuevaCategoria}. Estado: ABIERTA`, 'success');
      closeModal('modalDetailTicket');
      await loadDashboardMetrics();
      await loadTickets();
    } catch (e) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(e);
    }
  }

  // ---- Ejercicio 4: Tiempo técnico
  async function loadWorklog(ticket) {
    const summary = document.getElementById('worklogSummary');
    const formBox = document.getElementById('worklogFormBox');
    if (!summary || !formBox) return;
    summary.innerHTML = '<div style="color:#64748B;font-size:13px;">Cargando registros de tiempo...</div>';
    formBox.innerHTML = '';

    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticket.id}/tiempo-total`);
      if (!resp) return;
      if (!resp.ok) {
        summary.innerHTML = `<div style="color:#64748B;font-size:13px;">${escapeHtml(await readError(resp, 'No se pudo cargar el tiempo registrado.'))}</div>`;
      } else {
        const data = await resp.json();
        const total = data.totalMinutos || 0;
        const horas = (total / 60).toFixed(1);
        const items = (data.registros || []).map(r => `
          <div class="worklog-item">
            <strong>${r.minutosDedicados} min</strong> · ${escapeHtml(r.descripcionActividad)}<br>
            <small>${escapeHtml((r.tecnico && (r.tecnico.name || r.tecnico.email)) || '—')} · ${formatDate(r.fechaRegistro)}</small>
          </div>`).join('');
        summary.innerHTML = `
          <div class="worklog-total">⏱ Total acumulado: ${total} min (${horas} h)</div>
          ${items || '<div style="color:#94A3B8;font-style:italic;font-size:13px;">Aún no hay registros de tiempo.</div>'}`;
      }
    } catch (e) {
      summary.innerHTML = '<div style="color:#E11D48;font-size:13px;">Error al cargar el tiempo registrado.</div>';
      console.error(e);
    }

    const email = AuthGuard.getUserEmail();
    const isAssignedTech = AuthGuard.getRole() === 'TECHNICIAN' && ticket.tecnico && ticket.tecnico.email === email;
    if (isAssignedTech && ticket.status === 'EN_PROCESO') {
      formBox.innerHTML = `
        <div class="extra-box">
          <div class="extra-box-title">+ Registrar bloque de tiempo</div>
          <div class="extra-row">
            <input type="number" id="worklogMinutos" min="5" max="480" step="1" placeholder="Minutos (5 - 480)" style="width: 160px;">
            <input type="text" id="worklogDescripcion" maxlength="500" placeholder="Actividad realizada" style="flex: 1; min-width: 220px;">
            <button type="button" class="btn-campus-primary" onclick="window.CampusDesk.addWorklog(${ticket.id})">Registrar tiempo</button>
          </div>
        </div>`;
    } else if (AuthGuard.getRole() === 'TECHNICIAN') {
      formBox.innerHTML = '<div style="font-size:12.5px;color:#64748B;">El registro de tiempo solo está habilitado para el técnico asignado con la incidencia EN PROCESO.</div>';
    }
  }

  async function addWorklog(ticketId) {
    const minInput = document.getElementById('worklogMinutos');
    const descInput = document.getElementById('worklogDescripcion');
    const minutos = minInput ? parseInt(minInput.value, 10) : NaN;
    const descripcion = descInput ? descInput.value.trim() : '';
    if (isNaN(minutos) || minutos < 5 || minutos > 480) {
      showToast('Los minutos deben estar entre 5 y 480', 'error'); return;
    }
    if (!descripcion) { showToast('Describe la actividad realizada', 'error'); return; }
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/worklogs`, {
        method: 'POST', body: JSON.stringify({ minutosDedicados: minutos, descripcionActividad: descripcion })
      });
      if (!resp) return;
      if (!resp.ok) { showToast(await readError(resp, 'No se pudo registrar el tiempo'), 'error'); return; }
      showToast('Tiempo registrado correctamente', 'success');
      if (AppState.activeTicket) await loadWorklog(AppState.activeTicket);
    } catch (e) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(e);
    }
  }

  // ---- Ejercicio 5: Plantillas de solución rápida
  async function fetchMyTemplates() {
    const resp = await apiFetch(`${BASE_URL}/api/tecnicos/plantillas`);
    if (!resp || !resp.ok) return null;
    AppState.templates = await resp.json();
    return AppState.templates;
  }

  async function loadTemplates() {
    const tbody = document.getElementById('templatesTableBody');
    if (!tbody) return;
    try {
      const list = await fetchMyTemplates();
      if (list === null) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:30px;color:#E11D48;">No se pudieron cargar las plantillas.</td></tr>';
        return;
      }
      renderTemplatesTable();
    } catch (e) {
      tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:30px;color:#E11D48;">Error de comunicación con el servidor.</td></tr>';
      console.error(e);
    }
  }

  function renderTemplatesTable() {
    const tbody = document.getElementById('templatesTableBody');
    if (!tbody) return;
    if (!AppState.templates.length) {
      tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:30px;color:#64748B;">Aún no tienes plantillas. Crea la primera con el formulario superior.</td></tr>';
      return;
    }
    tbody.innerHTML = AppState.templates.map(t => {
      const preview = t.contenido.length > 120 ? t.contenido.substring(0, 120) + '…' : t.contenido;
      return `
        <tr>
          <td style="font-weight:600;">${escapeHtml(t.titulo)}</td>
          <td><span class="badge badge-cat">${escapeHtml(t.categoria)}</span></td>
          <td style="font-size:13px;color:#475569;">${escapeHtml(preview)}</td>
          <td style="text-align:right;white-space:nowrap;">
            <button class="btn-campus-secondary" style="padding:5px 10px;font-size:12px;" onclick="window.CampusDesk.editTemplate(${t.id})">Editar</button>
            <button class="btn-campus-secondary" style="padding:5px 10px;font-size:12px;border-color:#E11D48;color:#E11D48;" onclick="window.CampusDesk.deleteTemplate(${t.id})">Eliminar</button>
          </td>
        </tr>`;
    }).join('');
  }

  function resetTemplateForm() {
    const form = document.getElementById('formTemplate');
    if (form) form.reset();
    const idInput = document.getElementById('templateEditId');
    if (idInput) idInput.value = '';
    const btnCancel = document.getElementById('btnCancelTemplateEdit');
    if (btnCancel) btnCancel.style.display = 'none';
    const btnSave = document.getElementById('btnSaveTemplate');
    if (btnSave) btnSave.textContent = 'Guardar Plantilla';
  }

  function editTemplate(id) {
    const t = AppState.templates.find(x => x.id === id);
    if (!t) return;
    document.getElementById('templateEditId').value = t.id;
    document.getElementById('templateTitulo').value = t.titulo;
    document.getElementById('templateCategoria').value = t.categoria;
    document.getElementById('templateContenido').value = t.contenido;
    document.getElementById('btnCancelTemplateEdit').style.display = 'inline-flex';
    document.getElementById('btnSaveTemplate').textContent = 'Actualizar Plantilla';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function saveTemplate(e) {
    e.preventDefault();
    const editId = document.getElementById('templateEditId').value;
    const payload = {
      titulo: document.getElementById('templateTitulo').value.trim(),
      categoria: document.getElementById('templateCategoria').value,
      contenido: document.getElementById('templateContenido').value.trim()
    };
    if (!payload.titulo || !payload.contenido) { showToast('Título y contenido son obligatorios', 'error'); return; }
    try {
      const resp = await apiFetch(
        editId ? `${BASE_URL}/api/tecnicos/plantillas/${editId}` : `${BASE_URL}/api/tecnicos/plantillas`,
        { method: editId ? 'PUT' : 'POST', body: JSON.stringify(payload) }
      );
      if (!resp) return;
      if (!resp.ok) { showToast(await readError(resp, 'No se pudo guardar la plantilla'), 'error'); return; }
      showToast(editId ? 'Plantilla actualizada' : 'Plantilla creada', 'success');
      resetTemplateForm();
      await loadTemplates();
    } catch (err) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(err);
    }
  }

  async function deleteTemplate(id) {
    if (!window.confirm('¿Eliminar esta plantilla?')) return;
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tecnicos/plantillas/${id}`, { method: 'DELETE' });
      if (!resp) return;
      if (!resp.ok) { showToast(await readError(resp, 'No se pudo eliminar la plantilla'), 'error'); return; }
      showToast('Plantilla eliminada', 'success');
      await loadTemplates();
    } catch (err) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(err);
    }
  }

  // Aplicación rápida dentro del detalle del ticket (pestaña Comentarios)
  async function renderQuickTemplatePanel(ticket) {
    const panel = document.getElementById('quickTemplatePanel');
    if (!panel) return;
    panel.innerHTML = '';
    const email = AuthGuard.getUserEmail();
    const isAssignedTech = AuthGuard.getRole() === 'TECHNICIAN' && ticket.tecnico && ticket.tecnico.email === email;
    if (!isAssignedTech || ticket.status !== 'EN_PROCESO') return;

    let list = null;
    try { list = await fetchMyTemplates(); } catch (e) { console.error(e); }
    if (!list || list.length === 0) {
      panel.innerHTML = '<div style="font-size:12.5px;color:#64748B;margin-top:10px;">💬 No tienes plantillas. Créalas en la sección "Plantillas" del menú superior.</div>';
      return;
    }
    const options = list.map(t => `<option value="${t.id}">${escapeHtml(t.titulo)} (${escapeHtml(t.categoria)})</option>`).join('');
    panel.innerHTML = `
      <div class="extra-box" style="margin-top: 12px;">
        <div class="extra-box-title">💬 Aplicar plantilla de solución rápida</div>
        <div class="extra-row">
          <select id="selectTemplate" style="flex: 1; min-width: 220px;">${options}</select>
          <button type="button" class="btn-campus-primary" onclick="window.CampusDesk.applyTemplate(${ticket.id})">Publicar como comentario</button>
        </div>
      </div>`;
  }

  async function applyTemplate(ticketId) {
    const sel = document.getElementById('selectTemplate');
    if (!sel || !sel.value) { showToast('Selecciona una plantilla', 'error'); return; }
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/aplicar-plantilla/${sel.value}`, { method: 'POST' });
      if (!resp) return;
      if (!resp.ok) { showToast(await readError(resp, 'No se pudo aplicar la plantilla'), 'error'); return; }
      showToast('Plantilla publicada como comentario', 'success');
      await loadComments(ticketId);
    } catch (e) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(e);
    }
  }

  function toggleReopenedFilter() {
    AppState.showReopened = !AppState.showReopened;
    const btn = document.getElementById('btnReopened');
    if (btn) {
      btn.classList.toggle('btn-campus-primary', AppState.showReopened);
      btn.classList.toggle('btn-campus-secondary', !AppState.showReopened);
    }
    loadTickets();
  }

  function setupRoleExtras() {
    const role = AuthGuard.getRole();
    const btnTemplates = document.getElementById('btnNavTemplates');
    if (btnTemplates) btnTemplates.style.display = role === 'TECHNICIAN' ? 'inline-flex' : 'none';
    const btnReopened = document.getElementById('btnReopened');
    if (btnReopened) btnReopened.style.display = role === 'ADMIN' ? 'inline-flex' : 'none';
  }

  // 8. Crear / Editar Incidencia (RF-03, F1)
  function openCreateModal() {
    const form = document.getElementById('formCreateTicket');
    if (form) form.reset();

    const editIdInput = document.getElementById('editTicketId');
    if (editIdInput) editIdInput.value = '';

    const modalTitle = document.getElementById('modalCreateTicketTitle');
    if (modalTitle) modalTitle.textContent = 'Crear Nueva Incidencia';

    const statusInput = document.getElementById('newStatus');
    if (statusInput) statusInput.value = 'ABIERTA';

    const submitBtn = document.getElementById('btnSubmitCreate');
    if (submitBtn) submitBtn.textContent = 'Guardar Incidencia';

    openModal('modalCreateTicket');
  }

  async function openEditModal(ticketId) {
    let ticket = AppState.tickets.find(t => t.id === ticketId);
    if (!ticket || !ticket.description) {
      try {
        const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}`);
        if (resp && resp.ok) {
          ticket = await resp.json();
        }
      } catch (e) {
        console.error('Error al obtener datos para edición:', e);
      }
    }

    if (!ticket) {
      showToast('No se pudo cargar la incidencia para edición', 'error');
      return;
    }

    const editIdInput = document.getElementById('editTicketId');
    if (editIdInput) editIdInput.value = ticket.id;

    const modalTitle = document.getElementById('modalCreateTicketTitle');
    if (modalTitle) modalTitle.textContent = `Editar Incidencia #${ticket.id}`;

    const submitBtn = document.getElementById('btnSubmitCreate');
    if (submitBtn) submitBtn.textContent = 'Actualizar Incidencia';

    const titleInput = document.getElementById('newTitle');
    const catInput = document.getElementById('newCategory');
    const prioInput = document.getElementById('newPriority');
    const statusInput = document.getElementById('newStatus');
    const descInput = document.getElementById('newDescription');

    if (titleInput) titleInput.value = ticket.title || '';
    if (catInput) catInput.value = ticket.category || 'REDES';
    if (prioInput) prioInput.value = ticket.priority || 'MEDIA';
    if (statusInput) statusInput.value = ticket.status || 'ABIERTA';
    if (descInput) descInput.value = ticket.description || '';

    closeModal('modalDetailTicket');
    openModal('modalCreateTicket');
  }

  async function handleCreateOrUpdateTicket(e) {
    e.preventDefault();
    const editIdInput = document.getElementById('editTicketId');
    const editId = editIdInput ? editIdInput.value : '';
    const isEditing = Boolean(editId);

    const btnSubmit = document.getElementById('btnSubmitCreate');
    if (btnSubmit) {
      btnSubmit.disabled = true;
      btnSubmit.textContent = isEditing ? 'Actualizando...' : 'Guardando...';
    }

    const payload = {
      title: document.getElementById('newTitle').value.trim(),
      category: document.getElementById('newCategory').value,
      priority: document.getElementById('newPriority').value,
      status: (document.getElementById('newStatus') && document.getElementById('newStatus').value) || 'ABIERTA',
      description: document.getElementById('newDescription').value.trim()
    };

    try {
      const url = isEditing ? `${BASE_URL}/api/tickets/${editId}` : `${BASE_URL}/api/tickets`;
      const method = isEditing ? 'PUT' : 'POST';

      const resp = await apiFetch(url, {
        method: method,
        body: JSON.stringify(payload)
      });
      if (!resp) return;

      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        const msg = (err && (err.message || err.error)) || (isEditing ? 'Error al actualizar la incidencia' : 'Error al crear la incidencia');
        showToast(msg, 'error');
        return;
      }

      const savedTicket = await resp.json();
      showToast(isEditing ? `Incidencia #${savedTicket.id} actualizada correctamente` : `Incidencia #${savedTicket.id} creada exitosamente`, 'success');

      closeModal('modalCreateTicket');
      const form = document.getElementById('formCreateTicket');
      if (form) form.reset();
      if (editIdInput) editIdInput.value = '';

      // Recargar tablero
      await loadDashboardMetrics();
      await loadTickets();

      // Si se estaba visualizando el detalle, actualizarlo
      if (isEditing && AppState.activeTicket && AppState.activeTicket.id === savedTicket.id) {
        await openDetailModal(savedTicket.id);
      }
    } catch (err) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(err);
    } finally {
      if (btnSubmit) {
        btnSubmit.disabled = false;
        btnSubmit.textContent = isEditing ? 'Actualizar Incidencia' : 'Guardar Incidencia';
      }
    }
  }

  // 9. Abrir Detalle Integral del Ticket (RF-04, RF-05, RF-06)
  async function openDetailModal(ticketId) {
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}`);
      if (!resp) return;
      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        const msg = (err && (err.message || err.error)) || 'No se pudo cargar el detalle del ticket';
        showToast(msg, 'error');
        return;
      }

      const ticket = await resp.json();
      AppState.activeTicket = ticket;

      // Renderizar datos básicos
      document.getElementById('detailTicketId').textContent = `#${ticket.id}`;
      document.getElementById('detailTicketTitle').textContent = ticket.title;
      document.getElementById('detailDescription').textContent = ticket.description;

      const statusBadge = `<span class="badge badge-status-${ticket.status}">${ticket.status.replace('_', ' ')}</span>`;
      const prioBadge = `<span class="badge badge-prio-${ticket.priority}">${ticket.priority}</span>`;
      document.getElementById('detailStatusBadge').innerHTML = statusBadge;
      document.getElementById('detailPriorityBadge').innerHTML = prioBadge;

      const solicitante = ticket.solicitante
        ? `${escapeHtml(ticket.solicitante.name)} (${escapeHtml(ticket.solicitante.email)})`
        : '—';
      const tecnico = ticket.tecnico
        ? `${escapeHtml(ticket.tecnico.name)} (${escapeHtml(ticket.tecnico.email)})`
        : '<span style="color: #94A3B8; font-style: italic;">Sin asignar</span>';

      document.getElementById('detailSolicitante').innerHTML = solicitante;
      document.getElementById('detailTecnico').innerHTML = tecnico;

      // Ejercicio 1: etiquetas
      renderTagsPanel(ticket);

      // Ejercicios 2 y 3: reabrir / transferir
      renderExtraActions(ticket);

      // Renderizar Panel de Acciones de Flujo de Estados
      renderActionPanel(ticket);

      // Cargar Comentarios e Historial
      await loadComments(ticket.id);
      await loadHistory(ticket.id);

      // Ejercicio 4 y 5: tiempo técnico y plantillas rápidas
      await loadWorklog(ticket);
      await renderQuickTemplatePanel(ticket);

      // Activar la primera pestaña por defecto
      switchTab('tab-info');

      // Abrir modal
      openModal('modalDetailTicket');
    } catch (e) {
      showToast('Error de comunicación con el servidor', 'error');
      console.error(e);
    }
  }

  function renderActionPanel(ticket) {
    const container = document.getElementById('dynamicActionPanel');
    const role = AuthGuard.getRole();
    const userEmail = AuthGuard.getUserEmail();

    // 1. Si el ticket está CERRADO: Inmutabilidad
    if (ticket.status === 'CERRADA') {
      container.innerHTML = `
        <div class="closed-box">
          <span style="font-size: 18px;">🔒</span>
          <div>
            <div>Esta incidencia está <strong>CERRADA</strong>.</div>
            <div style="font-size: 12px; font-weight: normal; color: #64748B; margin-top: 2px;">
              El ciclo de vida ha concluido satisfactoriamente. Los datos e historial son inmutables.
            </div>
          </div>
        </div>
      `;
      return;
    }

    let actionsHtml = '';

    // Selector universal de cambio de estado desde vista de detalle (RF-04)
    actionsHtml += `
      <div class="action-box" style="background: #F8FAFC; border-color: #CBD5E1; margin-bottom: 14px;">
        <div class="action-box-title" style="color: #1E293B; display: flex; align-items: center; justify-content: space-between;">
          <span>🔄 Cambiar Estado de la Incidencia</span>
          <span style="font-size: 11.5px; font-weight: normal; color: #64748B;">Estado actual: <strong>${ticket.status.replace('_', ' ')}</strong></span>
        </div>
        <p style="font-size: 12.5px; color: #64748B; margin-bottom: 10px;">
          Seleccione el estado deseado. El cambio se procesará validando las reglas de negocio y permisos de su rol.
        </p>
        <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
          <select id="selectDetailNewStatus" style="flex: 1; min-width: 170px; padding: 9px; border: 1px solid #CBD5E1; border-radius: 6px; font-size: 13.5px; background: white;">
            <option value="ABIERTA" ${ticket.status === 'ABIERTA' ? 'selected' : ''}>Abierta</option>
            <option value="ASIGNADA" ${ticket.status === 'ASIGNADA' ? 'selected' : ''}>Asignada</option>
            <option value="EN_PROCESO" ${ticket.status === 'EN_PROCESO' ? 'selected' : ''}>En Proceso</option>
            <option value="RESUELTA" ${ticket.status === 'RESUELTA' ? 'selected' : ''}>Resuelta</option>
            <option value="CERRADA" ${ticket.status === 'CERRADA' ? 'selected' : ''}>Cerrada</option>
          </select>
          <input type="text" id="detailStatusComment" placeholder="Comentario o justificación del cambio..." style="flex: 2; min-width: 200px; padding: 9px; border: 1px solid #CBD5E1; border-radius: 6px; font-size: 13px;">
          <button class="btn-campus-primary" style="padding: 9px 16px; font-size: 13.5px;" onclick="window.CampusDesk.changeStatusFromDetail(${ticket.id})">
            Actualizar Estado
          </button>
        </div>
      </div>
    `;

    // F1: Opción de Editar únicamente si el usuario es el solicitante original
    const isRequester = ticket.solicitante && ticket.solicitante.email === userEmail;
    if (isRequester && ticket.status === 'ABIERTA' && !ticket.tecnico) {
      actionsHtml += `
        <div class="action-box" style="background: #F0FDF4; border-color: #BBF7D0; margin-bottom: 14px;">
          <div class="action-box-title" style="color: #166534;">✏ Edición de Incidencia (F1)</div>
          <p style="font-size: 13px; color: #15803D; margin-bottom: 12px;">
            La incidencia está abierta y aún no ha sido asignada a ningún técnico de soporte. Puedes modificar el título, categoría, prioridad, estado o descripción.
          </p>
          <button class="btn-campus-secondary" style="background: white; border-color: #16A34A; color: #15803D; font-weight: 600;" onclick="window.CampusDesk.openEditModal(${ticket.id})">
            ✏ Editar Datos de la Incidencia
          </button>
        </div>
      `;
    }

    // 2. Acciones para ADMINISTRADOR (Asignar o reasignar técnicos, solo en ABIERTA o ASIGNADA)
    if (role === 'ADMIN' && (ticket.status === 'ABIERTA' || ticket.status === 'ASIGNADA')) {
      const techOptions = AppState.technicians.map(t => {
        const isSelected = ticket.tecnico && ticket.tecnico.id === t.id ? 'selected' : '';
        return `<option value="${t.id}" ${isSelected}>${escapeHtml(t.name)} (${escapeHtml(t.email)})</option>`;
      }).join('');

      const actionTitle = ticket.status === 'ASIGNADA' ? 'Reasignación de Técnico' : 'Asignación de Técnico Responsable';
      const actionBtnText = ticket.status === 'ASIGNADA' ? 'Reasignar Técnico' : 'Asignar Técnico';

      actionsHtml += `
        <div class="action-box">
          <div class="action-box-title">⚙ Administración: ${actionTitle}</div>
          <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
            <select id="selectAssignTech" style="flex: 1; min-width: 200px; padding: 9px; border: 1px solid #CBD5E1; border-radius: 6px;">
              <option value="">-- Seleccionar Técnico --</option>
              ${techOptions}
            </select>
            <button class="btn-campus-primary" style="padding: 9px 16px; font-size: 13.5px;" onclick="window.CampusDesk.assignTechnician(${ticket.id})">
              ${actionBtnText}
            </button>
            <button class="btn-campus-secondary" style="padding: 9px 16px; font-size: 13.5px; border-color: #10B981; color: #059669; font-weight: 600;" onclick="window.CampusDesk.autoAssignTechnician(${ticket.id})" title="Asignar automáticamente al técnico con menor carga de tickets activos">
              ⚡ Asignación Automática (Ej. 7)
            </button>
          </div>
        </div>
      `;
    }

    // Ejercicio 6: Modificación de Prioridad con Auditoría (ADMIN o TECHNICIAN asignado)
    if (role === 'ADMIN' || (ticket.tecnico && ticket.tecnico.email === userEmail)) {
      actionsHtml += `
        <div class="action-box" style="background: #F8FAFC; border-color: #CBD5E1; margin-bottom: 14px;">
          <div class="action-box-title" style="color: #1E293B; display: flex; align-items: center; justify-content: space-between;">
            <span>⚡ Modificar Prioridad (Ejercicio 6)</span>
            <span style="font-size: 11.5px; font-weight: normal; color: #64748B;">Prioridad actual: <strong>${ticket.priority}</strong></span>
          </div>
          <p style="font-size: 12.5px; color: #64748B; margin-bottom: 10px;">
            El cambio registrará auditoría obligatoria con motivo justificativo (máx 250 caracteres).
          </p>
          <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
            <select id="selectChangePriority" style="padding: 9px; border: 1px solid #CBD5E1; border-radius: 6px; font-size: 13px; background: white;">
              <option value="BAJA" ${ticket.priority === 'BAJA' ? 'selected' : ''}>Baja</option>
              <option value="MEDIA" ${ticket.priority === 'MEDIA' ? 'selected' : ''}>Media</option>
              <option value="ALTA" ${ticket.priority === 'ALTA' ? 'selected' : ''}>Alta</option>
              <option value="CRITICA" ${ticket.priority === 'CRITICA' ? 'selected' : ''}>Crítica</option>
            </select>
            <input type="text" id="changePriorityMotivo" maxlength="250" placeholder="Motivo del cambio de prioridad (obligatorio)..." style="flex: 2; min-width: 200px; padding: 9px; border: 1px solid #CBD5E1; border-radius: 6px; font-size: 13px;">
            <button class="btn-campus-secondary" style="padding: 9px 16px; font-size: 13px; font-weight: 600;" onclick="window.CampusDesk.changePriority(${ticket.id})">
              Guardar Prioridad
            </button>
          </div>
        </div>
      `;
    }

    // 3. Acciones para TÉCNICO ASIGNADO (Iniciar atención o Resolver)
    // E2: Exclusivo para el técnico asignado (el administrador no debe saltarse las reglas de atención técnica)
    const isAssignedTech = ticket.tecnico && ticket.tecnico.email === userEmail;
    if (isAssignedTech) {
      if (ticket.status === 'ASIGNADA') {
        actionsHtml += `
          <div class="action-box" style="background: #EFF6FF; border-color: #BFDBFE;">
            <div class="action-box-title" style="color: #1E40AF;">▶ Gestión Técnica: Iniciar Atención</div>
            <p style="font-size: 13px; color: #3B82F6; margin-bottom: 12px;">
              La incidencia se encuentra asignada a tu perfil. Presiona el botón para iniciar el diagnóstico técnico.
            </p>
            <button class="btn-campus-primary" style="background: #2563EB;" onclick="window.CampusDesk.updateStatus(${ticket.id}, 'EN_PROCESO', 'Técnico inicia atención e investigación')">
              Iniciar Atención (Pasar a EN PROCESO)
            </button>
          </div>
        `;
      } else if (ticket.status === 'EN_PROCESO') {
        actionsHtml += `
          <div class="action-box" style="background: #ECFDF5; border-color: #A7F3D0;">
            <div class="action-box-title" style="color: #065F46;">✓ Gestión Técnica: Concluir y Resolver</div>
            <p style="font-size: 13px; color: #047857; margin-bottom: 12px;">
              Indica la solución aplicada para que el usuario solicitante pueda validar y confirmar el cierre.
            </p>
            <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
              <input type="text" id="resolveCommentInput" placeholder="Detalle de solución técnica aplicada..." style="flex: 1; padding: 9px; border: 1px solid #CBD5E1; border-radius: 6px; font-size: 13px;">
              <button class="btn-campus-primary" style="background: #10B981;" onclick="window.CampusDesk.resolveTicket(${ticket.id})">
                Marcar como RESUELTA
              </button>
            </div>
          </div>
        `;
      }
    }

    // 4. Acciones para USUARIO SOLICITANTE (Cerrar ticket resuelto)
    // E2: Exclusivo para el solicitante
    if (isRequester) {
      if (ticket.status === 'RESUELTA') {
        actionsHtml += `
          <div class="action-box" style="background: #F8FAFC; border-color: #CBD5E1;">
            <div class="action-box-title" style="color: #334155;">🔒 Conformidad y Cierre de Incidencia</div>
            <p style="font-size: 13px; color: #64748B; margin-bottom: 12px;">
              El soporte técnico ha marcado el problema como RESUELTO. Si estás conforme con la solución brindada, confirma el cierre definitivo.
            </p>
            <button class="btn-campus-primary" style="background: #334155;" onclick="window.CampusDesk.updateStatus(${ticket.id}, 'CERRADA', 'Solicitante confirma conformidad y cierre definitivo')">
              Confirmar Conformidad y Cerrar Incidencia
            </button>
          </div>
        `;
      }
    }

    container.innerHTML = actionsHtml || `
      <div style="padding: 12px; background: #F8FAFC; border-radius: 6px; font-size: 13px; color: #64748B; text-align: center;">
        No hay transiciones manuales pendientes para tu perfil en el estado actual (${ticket.status.replace('_', ' ')}).
      </div>
    `;
  }

  // 10. Asignar Técnico (RF-04)
  async function assignTechnician(ticketId) {
    const select = document.getElementById('selectAssignTech');
    if (!select || !select.value) {
      showToast('Por favor selecciona un técnico de la lista', 'error');
      return;
    }

    const tecnicoId = parseInt(select.value, 10);
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/assign`, {
        method: 'PATCH',
        body: JSON.stringify({ tecnicoId })
      });
      if (!resp) return;

      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        showToast((err && (err.message || err.error)) || 'Error al asignar técnico', 'error');
        return;
      }

      showToast('Técnico asignado exitosamente', 'success');
      await openDetailModal(ticketId);
      await loadDashboardMetrics();
      await loadTickets();
    } catch (e) {
      showToast('Error de red al asignar técnico', 'error');
      console.error(e);
    }
  }

  // 11. Cambiar Estado en la Máquina de Estados (RF-04)
  async function updateStatus(ticketId, newStatus, defaultComment = '') {
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({
          status: newStatus,
          comentario: defaultComment
        })
      });
      if (!resp) return;

      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        const msg = (err && (err.message || err.error)) || 'Transición de estado no autorizada';
        showToast(msg, 'error');
        return;
      }

      showToast(`Estado actualizado a ${newStatus.replace('_', ' ')}`, 'success');
      await openDetailModal(ticketId);
      await loadDashboardMetrics();
      await loadTickets();
    } catch (e) {
      showToast('Error de comunicación al actualizar estado', 'error');
      console.error(e);
    }
  }

  function resolveTicket(ticketId) {
    const input = document.getElementById('resolveCommentInput');
    const comment = (input && input.value.trim()) || 'Solución técnica verificada';
    updateStatus(ticketId, 'RESUELTA', comment);
  }

  async function changeStatusFromDetail(ticketId) {
    const select = document.getElementById('selectDetailNewStatus');
    const commentInput = document.getElementById('detailStatusComment');
    if (!select || !select.value) {
      showToast('Por favor selecciona un estado de la lista', 'error');
      return;
    }

    const newStatus = select.value;
    if (AppState.activeTicket && AppState.activeTicket.status === newStatus) {
      showToast(`La incidencia ya se encuentra en estado ${newStatus.replace('_', ' ')}`, 'info');
      return;
    }

    const comment = (commentInput && commentInput.value.trim()) || `Cambio de estado a ${newStatus.replace('_', ' ')}`;
    await updateStatus(ticketId, newStatus, comment);
  }

  // 12. Hilo de Comentarios (RF-05)
  async function loadComments(ticketId) {
    const container = document.getElementById('commentsContainer');
    const countBadge = document.getElementById('detailCommentCount');
    const formContainer = document.getElementById('commentFormContainer');

    if (!container) return;
    container.innerHTML = '<div style="color: #64748B; font-size: 13px;">Cargando notas...</div>';

    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/comments`);
      if (!resp) return;

      if (!resp.ok) {
        container.innerHTML = '<div style="color: #64748B; font-size: 13px;">Sin comentarios registrados.</div>';
        return;
      }

      const comments = await resp.json();
      if (countBadge) countBadge.textContent = comments.length;

      // Si ticket está CERRADO, deshabilitar formulario de comentarios
      if (formContainer) {
        if (AppState.activeTicket && AppState.activeTicket.status === 'CERRADA') {
          formContainer.innerHTML = `
            <div class="closed-box" style="margin-top: 14px;">
              <span>🔒</span> No se pueden registrar comentarios adicionales en un ticket CERRADO.
            </div>
          `;
        } else {
          const canPostInternal = (AuthGuard.getRole() === 'ADMIN' || AuthGuard.getRole() === 'TECHNICIAN');
          const internalCheckboxHtml = canPostInternal ? `
            <label style="display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #475569; cursor: pointer;">
              <input type="checkbox" id="commentIsInternal">
              <span>🔒 Nota interna (exclusiva personal técnico)</span>
            </label>
          ` : '';

          formContainer.innerHTML = `
            <form id="formAddComment" style="margin-top: 14px;" onsubmit="window.CampusDesk.handleAddComment(event, ${ticketId})">
              <textarea id="commentContent" rows="3" required placeholder="Escriba una nota o actualización técnica sobre esta incidencia..." style="width: 100%; padding: 10px; border: 1px solid #CBD5E1; border-radius: 6px; resize: vertical;"></textarea>
              <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 8px; flex-wrap: wrap; gap: 10px;">
                <div>${internalCheckboxHtml}</div>
                <button type="submit" class="btn-campus-primary" style="padding: 8px 16px; font-size: 13.5px;" id="btnSubmitComment">
                  Agregar Comentario
                </button>
              </div>
            </form>
          `;
        }
      }

      if (comments.length === 0) {
        container.innerHTML = '<div style="color: #64748B; font-size: 13px; text-align: center; padding: 20px;">No hay comentarios aún. Escribe el primero abajo.</div>';
        return;
      }

      container.innerHTML = comments.map(c => {
        const authorName = c.author ? escapeHtml(c.author.name || c.author.email) : 'Usuario';
        const authorRole = c.author ? c.author.role : 'USER';
        const dateStr = formatDate(c.createdAt);
        const bodyStr = escapeHtml(c.content);
        const isInternal = !!c.esInterno;
        const internalBadge = isInternal
          ? `<span style="background: #FEF3C7; color: #92400E; font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; border: 1px solid #FCD34D;">🔒 NOTA INTERNA</span>`
          : '';
        const cardStyle = isInternal
          ? `style="border-left: 3px solid #D97706; background: #FFFBEB;"`
          : '';

        return `
          <div class="comment-card" ${cardStyle}>
            <div class="comment-header">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span class="comment-author">${authorName}</span>
                <span class="role-pill ${authorRole.toLowerCase()}" style="font-size: 10px; padding: 2px 6px;">${authorRole}</span>
                ${internalBadge}
              </div>
              <span style="color: #94A3B8;">${dateStr}</span>
            </div>
            <div class="comment-body">${bodyStr}</div>
          </div>
        `;
      }).join('');
    } catch (e) {
      console.error(e);
      container.innerHTML = '<div style="color: #E11D48; font-size: 13px;">Error al cargar comentarios.</div>';
    }
  }

  async function handleAddComment(e, ticketId) {
    e.preventDefault();
    const btn = document.getElementById('btnSubmitComment');
    const textarea = document.getElementById('commentContent');
    const content = textarea.value.trim();

    if (!content) return;

    const isInternal = document.getElementById('commentIsInternal') && document.getElementById('commentIsInternal').checked;
    const url = isInternal
      ? `${BASE_URL}/api/tickets/${ticketId}/comentarios-internos`
      : `${BASE_URL}/api/tickets/${ticketId}/comments`;

    if (btn) {
      btn.disabled = true;
      btn.textContent = 'Enviando...';
    }

    try {
      const resp = await apiFetch(url, {
        method: 'POST',
        body: JSON.stringify({ content })
      });
      if (!resp) return;

      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        showToast((err && (err.message || err.error)) || 'Error al agregar comentario', 'error');
        return;
      }

      showToast('Comentario agregado con éxito', 'success');
      textarea.value = '';
      await loadComments(ticketId);
    } catch (err) {
      showToast('Error de red al agregar comentario', 'error');
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = 'Agregar Comentario';
      }
    }
  }

  // 13. Trazabilidad y Auditoría (RF-06)
  async function loadHistory(ticketId) {
    const timeline = document.getElementById('historyTimeline');
    if (!timeline) return;
    timeline.innerHTML = '<div style="color: #64748B; font-size: 13px;">Cargando historial de auditoría...</div>';

    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/history`);
      if (!resp) return;

      if (!resp.ok) {
        timeline.innerHTML = '<div style="color: #64748B; font-size: 13px;">Sin historial registrado.</div>';
        return;
      }

      const history = await resp.json();
      if (history.length === 0) {
        timeline.innerHTML = '<div style="color: #64748B; font-size: 13px;">Sin cambios registrados.</div>';
        return;
      }

      timeline.innerHTML = history.map(h => {
        const dateStr = formatDate(h.fechaCambio);
        const authorName = h.autor ? escapeHtml(h.autor.name || h.autor.email) : 'Sistema';
        const authorRole = h.autor ? h.autor.role : 'SYSTEM';
        const oldState = h.estadoAnterior
          ? `<span class="badge badge-status-${h.estadoAnterior}" style="font-size: 10px;">${h.estadoAnterior.replace('_', ' ')}</span>`
          : '<span style="color: #94A3B8; font-size: 11px;">INICIO</span>';
        const newState = `<span class="badge badge-status-${h.estadoNuevo}" style="font-size: 10px;">${h.estadoNuevo.replace('_', ' ')}</span>`;
        const comment = escapeHtml(h.comentarioTransicion || 'Cambio de estado');

        return `
          <div class="timeline-item">
            <div class="timeline-dot"></div>
            <div class="timeline-content">
              <div class="timeline-meta">
                <span>
                  <strong>${authorName}</strong> (${authorRole})
                </span>
                <span>${dateStr}</span>
              </div>
              <div style="margin: 6px 0; display: flex; align-items: center; gap: 8px;">
                ${oldState} <span style="color: #94A3B8;">&rarr;</span> ${newState}
              </div>
              <div class="timeline-comment">${comment}</div>
            </div>
          </div>
        `;
      }).join('');
    } catch (e) {
      console.error(e);
      timeline.innerHTML = '<div style="color: #E11D48; font-size: 13px;">Error al cargar auditoría.</div>';
    }
  }

  // 13.1 Métodos de los Ejercicios 6 al 10
  async function changePriority(ticketId) {
    const select = document.getElementById('selectChangePriority');
    const motivoInput = document.getElementById('changePriorityMotivo');
    if (!select || !motivoInput) return;

    const priority = select.value;
    const motivo = motivoInput.value.trim();
    if (!motivo) {
      showToast('Debe ingresar un motivo para el cambio de prioridad', 'warning');
      motivoInput.focus();
      return;
    }

    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/prioridad`, {
        method: 'PATCH',
        body: JSON.stringify({ priority, motivo })
      });
      if (!resp) return;

      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        showToast((err && (err.message || err.error)) || 'Error al cambiar la prioridad', 'error');
        return;
      }

      const updated = await resp.json();
      showToast('Prioridad actualizada con éxito', 'success');
      AppState.activeTicket = updated;
      const detailBadge = document.getElementById('detailPriorityBadge');
      if (detailBadge) {
        detailBadge.innerHTML = `<span class="priority-pill ${updated.priority.toLowerCase()}">${updated.priority}</span>`;
      }
      await loadHistory(ticketId);
      await loadTickets();
    } catch (err) {
      showToast('Error de red al cambiar prioridad', 'error');
    }
  }

  async function autoAssignTechnician(ticketId) {
    if (!confirm('¿Desea asignar automáticamente este ticket al técnico activo con menor carga?')) return;
    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/${ticketId}/asignacion-automatica`, {
        method: 'POST'
      });
      if (!resp) return;

      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        showToast((err && (err.message || err.error)) || 'Error en la asignación automática', 'error');
        return;
      }

      const updated = await resp.json();
      const techName = updated.tecnico ? (updated.tecnico.name || updated.tecnico.email) : 'Técnico';
      showToast(`Incidencia asignada automáticamente a ${techName}`, 'success');
      AppState.activeTicket = updated;
      const techCell = document.getElementById('detailTecnico');
      if (techCell) techCell.textContent = techName;
      const statusBadge = document.getElementById('detailStatusBadge');
      if (statusBadge) statusBadge.innerHTML = `<span class="status-pill ${updated.status.toLowerCase()}">${updated.status.replace('_', ' ')}</span>`;
      renderActionPanel(updated);
      await loadHistory(ticketId);
      await loadComments(ticketId);
      await loadTickets();
    } catch (err) {
      showToast('Error de red al asignar automáticamente', 'error');
    }
  }

  async function loadProductivityMetrics() {
    if (AuthGuard.getRole() !== 'ADMIN') return;
    const tbody = document.getElementById('productivityTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
      <tr>
        <td colspan="8" style="text-align: center; padding: 30px; color: #64748B;">
          Calculando métricas de productividad de técnicos...
        </td>
      </tr>
    `;

    try {
      const resp = await apiFetch(`${BASE_URL}/api/dashboard/tecnicos/productividad`);
      if (!resp || !resp.ok) {
        tbody.innerHTML = `
          <tr>
            <td colspan="8" style="text-align: center; padding: 30px; color: #E11D48;">
              Error al consultar las métricas de productividad.
            </td>
          </tr>
        `;
        return;
      }

      const data = await resp.json();
      if (!data || data.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="8" style="text-align: center; padding: 30px; color: #64748B;">
              No se encontraron técnicos registrados en el sistema.
            </td>
          </tr>
        `;
        return;
      }

      tbody.innerHTML = data.map(t => `
        <tr>
          <td><span style="font-weight: 700; color: #10B981;">#${t.tecnicoId}</span></td>
          <td style="font-weight: 600;">${escapeHtml(t.tecnicoNombre)}</td>
          <td style="color: #64748B;">${escapeHtml(t.tecnicoEmail)}</td>
          <td><span class="role-pill user" style="font-size: 11px;">${t.ticketsAsignados}</span></td>
          <td><span class="role-pill technician" style="font-size: 11px;">${t.ticketsActivos}</span></td>
          <td><span class="role-pill admin" style="font-size: 11px;">${t.ticketsResueltos}</span></td>
          <td><strong>${t.totalMinutosWorklog}</strong> min</td>
          <td><strong>${t.promedioMinutosPorTicket}</strong> min/ticket</td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `
        <tr>
          <td colspan="8" style="text-align: center; padding: 30px; color: #E11D48;">
            Fallo de conexión al cargar el reporte de productividad.
          </td>
        </tr>
      `;
    }
  }

  async function triggerAutoClose() {
    if (!confirm('¿Desea ejecutar el proceso de cierre automático por inactividad para tickets en estado RESUELTA con más de 48 horas sin actividad?')) return;
    const btn = document.getElementById('btnTriggerAutoClose');
    if (btn) {
      btn.disabled = true;
      btn.textContent = '⏳ Evaluando inactividad...';
    }

    try {
      const resp = await apiFetch(`${BASE_URL}/api/tickets/cierre-inactividad`, { method: 'POST' });
      if (!resp) return;

      if (!resp.ok) {
        const err = await resp.json().catch(() => null);
        showToast((err && (err.message || err.error)) || 'Error al ejecutar cierre por inactividad', 'error');
        return;
      }

      const result = await resp.json();
      showToast(`Proceso completado: ${result.ticketsCerrados} incidencias cerradas (umbral: ${result.umbralHoras}h)`, 'success');
      await loadTickets();
      await loadDashboardMetrics();
      await loadProductivityMetrics();
    } catch (err) {
      showToast('Error de red al ejecutar cierre por inactividad', 'error');
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = '<span>⏳ Cierre Automático por Inactividad (Ej. 10)</span>';
      }
    }
  }

  // 14. Modales, Navegación de Vistas y Pestañas
  function openModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('active');
  }

  function closeModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('active');
  }

  function switchTab(targetTabId) {
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-tab') === targetTabId);
    });
    document.querySelectorAll('.tab-content').forEach(content => {
      content.style.display = content.id === targetTabId ? 'block' : 'none';
    });
  }

  function switchMainView(viewName) {
    const views = {
      helpdesk: { section: 'sectionHelpdesk', btn: 'btnNavHelpdesk' },
      admin: { section: 'sectionAdmin', btn: 'btnNavAdmin' },
      templates: { section: 'sectionTemplates', btn: 'btnNavTemplates' }
    };
    const role = AuthGuard.getRole();

    if (viewName === 'admin' && role !== 'ADMIN') {
      showToast('Acceso denegado: Solo administradores pueden ver esta sección', 'error');
      return;
    }
    if (viewName === 'templates' && role !== 'TECHNICIAN') {
      showToast('Acceso denegado: Solo técnicos pueden gestionar plantillas', 'error');
      return;
    }
    if (!views[viewName]) viewName = 'helpdesk';

    Object.keys(views).forEach(name => {
      const sec = document.getElementById(views[name].section);
      const btn = document.getElementById(views[name].btn);
      if (sec) sec.style.display = name === viewName ? 'block' : 'none';
      if (btn) btn.classList.toggle('active-view-btn', name === viewName);
    });

    if (viewName === 'admin') {
      loadAdminUsers();
      loadProductivityMetrics();
    } else if (viewName === 'templates') {
      loadTemplates();
    } else {
      loadTickets();
    }
  }

  // 15. Vinculación Integral de Eventos en el DOM (REPARACIÓN CRÍTICA)
  function setupEventListeners() {
    // Logout
    const btnLogout = document.getElementById('btnLogout');
    if (btnLogout) {
      btnLogout.addEventListener('click', async () => {
        await AuthGuard.logout();
      });
    }

    // Refresh
    const btnRefresh = document.getElementById('btnRefresh');
    if (btnRefresh) {
      btnRefresh.addEventListener('click', async () => {
        await loadDashboardMetrics();
        await loadTickets();
        showToast('Tablero de incidencias actualizado');
      });
    }

    // Navegación de Vistas (Helpdesk vs Administración)
    const btnNavHelpdesk = document.getElementById('btnNavHelpdesk');
    if (btnNavHelpdesk) {
      btnNavHelpdesk.addEventListener('click', () => switchMainView('helpdesk'));
    }

    const btnNavAdmin = document.getElementById('btnNavAdmin');
    if (btnNavAdmin) {
      btnNavAdmin.addEventListener('click', () => switchMainView('admin'));
    }

    // Eventos de la Vista de Administración
    const btnRefreshAdminUsers = document.getElementById('btnRefreshAdminUsers');
    if (btnRefreshAdminUsers) {
      btnRefreshAdminUsers.addEventListener('click', async () => {
        await loadAdminUsers();
        await loadProductivityMetrics();
        showToast('Lista de usuarios y métricas actualizadas');
      });
    }

    // Botones de Productividad y Cierre Automático (Ejercicios 9 y 10)
    const btnRefreshProductivity = document.getElementById('btnRefreshProductivity');
    if (btnRefreshProductivity) {
      btnRefreshProductivity.addEventListener('click', async () => {
        await loadProductivityMetrics();
        showToast('Métricas de productividad actualizadas');
      });
    }

    const btnTriggerAutoClose = document.getElementById('btnTriggerAutoClose');
    if (btnTriggerAutoClose) {
      btnTriggerAutoClose.addEventListener('click', triggerAutoClose);
    }

    const searchUserInput = document.getElementById('searchUserInput');
    if (searchUserInput) {
      searchUserInput.addEventListener('input', e => {
        AppState.userSearchQuery = e.target.value;
        renderAdminUsersTable();
      });
    }

    const filterUserRole = document.getElementById('filterUserRole');
    if (filterUserRole) {
      filterUserRole.addEventListener('change', e => {
        AppState.userFilterRole = e.target.value;
        renderAdminUsersTable();
      });
    }

    const btnResetUserFilters = document.getElementById('btnResetUserFilters');
    if (btnResetUserFilters) {
      btnResetUserFilters.addEventListener('click', () => {
        AppState.userSearchQuery = '';
        AppState.userFilterRole = '';
        if (searchUserInput) searchUserInput.value = '';
        if (filterUserRole) filterUserRole.value = '';
        renderAdminUsersTable();
      });
    }

    // Modal Crear / Editar
    const btnOpenCreateModal = document.getElementById('btnOpenCreateModal');
    if (btnOpenCreateModal) {
      btnOpenCreateModal.addEventListener('click', openCreateModal);
    }

    const btnCloseCreateModal = document.getElementById('btnCloseCreateModal');
    if (btnCloseCreateModal) {
      btnCloseCreateModal.addEventListener('click', () => {
        closeModal('modalCreateTicket');
      });
    }

    const btnCancelCreate = document.getElementById('btnCancelCreate');
    if (btnCancelCreate) {
      btnCancelCreate.addEventListener('click', () => {
        closeModal('modalCreateTicket');
      });
    }

    const formCreateTicket = document.getElementById('formCreateTicket');
    if (formCreateTicket) {
      formCreateTicket.addEventListener('submit', handleCreateOrUpdateTicket);
    }

    // Modal Detalle
    const btnCloseDetailModal = document.getElementById('btnCloseDetailModal');
    if (btnCloseDetailModal) {
      btnCloseDetailModal.addEventListener('click', () => {
        closeModal('modalDetailTicket');
      });
    }

    const btnExitDetail = document.getElementById('btnExitDetail');
    if (btnExitDetail) {
      btnExitDetail.addEventListener('click', () => {
        closeModal('modalDetailTicket');
      });
    }

    // Pestañas del Modal
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const tabTarget = btn.getAttribute('data-tab');
        if (tabTarget) {
          switchTab(tabTarget);
        }
      });
    });

    // Filtros de tabla de incidencias
    const filterStatus = document.getElementById('filterStatus');
    if (filterStatus) {
      filterStatus.addEventListener('change', e => {
        AppState.activeFilterStatus = e.target.value;
        updateActiveCard();
        loadTickets();
      });
    }

    const filterPriority = document.getElementById('filterPriority');
    if (filterPriority) {
      filterPriority.addEventListener('change', e => {
        AppState.activeFilterPriority = e.target.value;
        loadTickets();
      });
    }

    const filterCategory = document.getElementById('filterCategory');
    if (filterCategory) {
      filterCategory.addEventListener('change', e => {
        AppState.activeFilterCategory = e.target.value;
        loadTickets();
      });
    }

    const btnNavTemplates = document.getElementById('btnNavTemplates');
    if (btnNavTemplates) btnNavTemplates.addEventListener('click', () => switchMainView('templates'));

    const btnReopened = document.getElementById('btnReopened');
    if (btnReopened) btnReopened.addEventListener('click', toggleReopenedFilter);

    const formTemplate = document.getElementById('formTemplate');
    if (formTemplate) formTemplate.addEventListener('submit', saveTemplate);

    const btnCancelTemplateEdit = document.getElementById('btnCancelTemplateEdit');
    if (btnCancelTemplateEdit) btnCancelTemplateEdit.addEventListener('click', resetTemplateForm);

    const btnRefreshTemplates = document.getElementById('btnRefreshTemplates');
    if (btnRefreshTemplates) btnRefreshTemplates.addEventListener('click', loadTemplates);

    const filterTag = document.getElementById('filterTag');
    if (filterTag) {
      let tagTimer;
      filterTag.addEventListener('input', e => {
        clearTimeout(tagTimer);
        tagTimer = setTimeout(() => {
          AppState.activeFilterTag = e.target.value.trim();
          loadTickets();
        }, 350);
      });
    }

    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
      searchInput.addEventListener('input', e => {
        AppState.searchQuery = e.target.value;
        renderTicketsTable();
      });
    }

    const btnResetFilters = document.getElementById('btnResetFilters');
    if (btnResetFilters) {
      btnResetFilters.addEventListener('click', () => {
        AppState.activeFilterStatus = '';
        AppState.activeFilterPriority = '';
        AppState.activeFilterCategory = '';
        AppState.activeFilterTag = '';
        AppState.showReopened = false;
        const btnReop = document.getElementById('btnReopened');
        if (btnReop) { btnReop.classList.remove('btn-campus-primary'); btnReop.classList.add('btn-campus-secondary'); }
        AppState.searchQuery = '';
        if (filterTag) filterTag.value = '';
        if (filterStatus) filterStatus.value = '';
        if (filterPriority) filterPriority.value = '';
        if (filterCategory) filterCategory.value = '';
        if (searchInput) searchInput.value = '';
        updateActiveCard();
        loadTickets();
      });
    }

    // Click en Tarjetas de Métricas para filtrar
    document.querySelectorAll('.metric-card').forEach(card => {
      card.addEventListener('click', () => {
        const filterVal = card.getAttribute('data-filter') || '';
        AppState.activeFilterStatus = filterVal;
        if (filterStatus) filterStatus.value = filterVal;
        updateActiveCard();
        loadTickets();
      });
    });

    // Cerrar modal al presionar Escape
    window.addEventListener('keydown', e => {
      if (e.key === 'Escape') {
        closeModal('modalCreateTicket');
        closeModal('modalDetailTicket');
      }
    });

    // Cerrar modal al hacer clic en el backdrop / overlay
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
      overlay.addEventListener('click', e => {
        if (e.target === overlay) {
          overlay.classList.remove('active');
        }
      });
    });
  }

  function updateActiveCard() {
    document.querySelectorAll('.metric-card').forEach(card => {
      const filter = card.getAttribute('data-filter');
      if (AppState.activeFilterStatus === filter) {
        card.classList.add('active-filter');
      } else {
        card.classList.remove('active-filter');
      }
    });
  }

  // 16. Exposición Global para Acciones Directas en la Tabla
  window.CampusDesk = {
    openDetailModal: openDetailModal,
    openCreateModal: openCreateModal,
    openEditModal: openEditModal,
    addTags: addTags,
    reopenTicket: reopenTicket,
    transferTicket: transferTicket,
    addWorklog: addWorklog,
    applyTemplate: applyTemplate,
    editTemplate: editTemplate,
    deleteTemplate: deleteTemplate,
    filterByTag: filterByTag,
    changeStatusFromDetail: changeStatusFromDetail,
    changePriority: changePriority,
    autoAssignTechnician: autoAssignTechnician,
    loadProductivityMetrics: loadProductivityMetrics,
    triggerAutoClose: triggerAutoClose,
    openAssignQuick: function(ticketId) {
      openDetailModal(ticketId);
    },
    quickChangeStatus: function(ticketId, newStatus, comment) {
      updateStatus(ticketId, newStatus, comment);
    },
    assignTechnician: assignTechnician,
    updateStatus: updateStatus,
    resolveTicket: resolveTicket,
    handleAddComment: handleAddComment,
    switchMainView: switchMainView,
    filterTicketsByUser: function(userEmail) {
      switchMainView('helpdesk');
      const searchInput = document.getElementById('searchInput');
      if (searchInput) {
        searchInput.value = userEmail;
        AppState.searchQuery = userEmail;
        renderTicketsTable();
      }
    }
  };

  // 17. Inicialización Principal Robusta
  async function initApp() {
    setupEventListeners();
    await initUserProfile();
    setupRoleExtras();
    if (AuthGuard.getRole() === 'ADMIN') {
      await loadTechnicians();
    }
    await loadDashboardMetrics();
    await loadTickets();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
  } else {
    initApp();
  }

})();
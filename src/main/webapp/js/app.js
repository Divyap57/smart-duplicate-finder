/* Shared client code: API helper, shell/layout, toasts, dialogs, formatting. Pages call App.ready.then(...). */
(function () {
  'use strict';

  // ---------- theme (light / dark / system), remembered in this browser only ----------
  function getTheme() { try { return localStorage.getItem('sd-theme') || 'system'; } catch (e) { return 'system'; } }
  function applyTheme(t) {
    const dark = t === 'dark' || (t === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.setAttribute('data-bs-theme', dark ? 'dark' : 'light');
  }
  function setTheme(t) {
    try { localStorage.setItem('sd-theme', t); } catch (e) { /* ignore */ }
    applyTheme(t);
    window.dispatchEvent(new Event('sd-theme'));
  }
  applyTheme(getTheme());

  const BASE = location.pathname.substring(0, location.pathname.lastIndexOf('/') + 1);
  let csrf = '';
  const PALETTE = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#64748b'];

  // ---------- formatting (all user data goes through esc() before it reaches innerHTML) ----------
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  function fmtBytes(n) {
    n = Number(n) || 0;
    if (n < 1024) return n + ' B';
    const u = ['KB', 'MB', 'GB', 'TB', 'PB'];
    let i = -1;
    do { n /= 1024; i++; } while (n >= 1024 && i < u.length - 1);
    return n.toFixed(n >= 100 ? 0 : 1) + ' ' + u[i];
  }
  const fmtNum = n => Number(n || 0).toLocaleString();
  const fmtDate = ms => ms ? new Date(Number(ms)).toLocaleString() : '—';
  function fmtDur(ms) {
    const s = Math.floor((Number(ms) || 0) / 1000);
    const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
    return (h ? h + 'h ' : '') + (h || m ? m + 'm ' : '') + x + 's';
  }
  function statusBadge(status) {
    const cls = { COMPLETED: 'success', RUNNING: 'primary', PAUSED: 'warning', CANCELLED: 'secondary', FAILED: 'danger',
      QUARANTINED: 'warning', RESTORED: 'success', DELETED: 'danger', ACTIVE: 'success', MISSING: 'secondary' }[status] || 'secondary';
    return '<span class="badge badge-status text-bg-' + cls + '">' + esc(status) + '</span>';
  }

  // ---------- API ----------
  async function api(path, opts) {
    opts = opts || {};
    const method = opts.method || (opts.body !== undefined ? 'POST' : 'GET');
    const init = { method, credentials: 'same-origin', headers: {} };
    if (opts.body !== undefined) {
      init.body = JSON.stringify(opts.body);
      init.headers['Content-Type'] = 'application/json';
    }
    if (method !== 'GET') init.headers['X-CSRF-Token'] = csrf;
    let res;
    try {
      res = await fetch(BASE + 'api/' + path, init);
    } catch (e) {
      throw new Error('Cannot reach the server. Is it still running?');
    }
    let data = null;
    try { data = await res.json(); } catch (e) { /* body was not JSON */ }
    if (res.status === 401 && !opts.noRedirect) {
      location.href = BASE + 'login.html';
      throw new Error('Please sign in.');
    }
    if (!res.ok) throw new Error((data && data.error) || 'Request failed (' + res.status + ').');
    return data;
  }

  // ---------- toasts / dialogs ----------
  function toast(msg, type) {
    type = type || 'info';
    let box = document.getElementById('toasts');
    if (!box) { box = document.createElement('div'); box.id = 'toasts'; document.body.appendChild(box); }
    const icon = { ok: 'fa-circle-check', err: 'fa-circle-exclamation', info: 'fa-circle-info' }[type];
    const el = document.createElement('div');
    el.className = 'toast-msg ' + type;
    el.innerHTML = '<i class="fa-solid ' + icon + '"></i><div>' + esc(msg) + '</div>';
    box.appendChild(el);
    setTimeout(() => el.remove(), type === 'err' ? 7000 : 4000);
  }

  /** opts: {title, body (HTML), footer (HTML), size}. Returns {el, body, hide}. */
  function modal(opts) {
    const el = document.createElement('div');
    el.className = 'modal fade';
    el.tabIndex = -1;
    el.innerHTML = '<div class="modal-dialog modal-dialog-scrollable ' + (opts.size || '') + '"><div class="modal-content">'
      + '<div class="modal-header"><h5 class="modal-title">' + esc(opts.title) + '</h5>'
      + '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button></div>'
      + '<div class="modal-body">' + (opts.body || '') + '</div>'
      + (opts.footer ? '<div class="modal-footer">' + opts.footer + '</div>' : '')
      + '</div></div>';
    document.body.appendChild(el);
    const m = new bootstrap.Modal(el);
    el.addEventListener('hidden.bs.modal', () => { m.dispose(); el.remove(); });
    m.show();
    return { el, body: el.querySelector('.modal-body'), hide: () => m.hide() };
  }

  /** message is HTML - escape anything user-controlled before passing it in. Resolves true/false. */
  function confirmDialog(title, message, okLabel, danger) {
    return new Promise(resolve => {
      let accepted = false;
      const d = modal({
        title, body: '<p class="mb-0">' + message + '</p>',
        footer: '<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button>'
          + '<button class="btn ' + (danger ? 'btn-danger' : 'btn-primary') + '" data-ok>' + esc(okLabel || 'Confirm') + '</button>'
      });
      d.el.querySelector('[data-ok]').addEventListener('click', () => { accepted = true; d.hide(); resolve(true); });
      d.el.addEventListener('hidden.bs.modal', () => { if (!accepted) resolve(false); });
    });
  }

  async function busy(btn, fn) {
    const html = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<i class="fa-solid fa-spinner spin"></i> ' + esc(btn.dataset.busy || 'Working…');
    try { return await fn(); } finally { btn.disabled = false; btn.innerHTML = html; }
  }

  function chart(canvasId, type, data, options) {
    const canvas = document.getElementById(canvasId);
    const css = getComputedStyle(document.documentElement);
    Chart.defaults.color = css.getPropertyValue('--sd-muted').trim() || '#64748b';
    Chart.defaults.borderColor = css.getPropertyValue('--sd-border').trim() || '#e2e8f0';
    Chart.defaults.font.family = 'Inter, "Segoe UI", system-ui, sans-serif';
    const prev = Chart.getChart(canvas);
    if (prev) prev.destroy();
    return new Chart(canvas, { type, data, options: Object.assign({ responsive: true, maintainAspectRatio: false }, options || {}) });
  }

  // ---------- shared actions ----------
  async function quarantineFiles(ids, totalBytes) {
    const yes = await confirmDialog('Move to quarantine?',
      'Move <b>' + ids.length + '</b> file' + (ids.length > 1 ? 's' : '') + ' (' + fmtBytes(totalBytes)
      + ') to quarantine?<br><span class="text-muted">Nothing is deleted. You can restore files from the Quarantine page.</span>',
      'Move to quarantine');
    if (!yes) return null;
    const r = await api('quarantine', { body: { ids } });
    if (r.moved) toast(r.moved + (r.moved > 1 ? ' files were' : ' file was') + ' moved to quarantine (' + fmtBytes(r.bytes) + ').', 'ok');
    const bad = r.results.filter(x => !x.ok);
    if (bad.length) toast(bad.length + (bad.length > 1 ? ' files' : ' file') + ' could not be moved: ' + bad[0].message, 'err');
    return r;
  }

  async function showFileDetails(id, onChange) {
    const d = modal({ title: 'File details', size: 'modal-lg', body: '<div class="loading"><i class="fa-solid fa-spinner spin"></i></div>' });
    try {
      const r = await api('files/' + id);
      const f = r.file;
      const copies = r.otherCopies.length
        ? '<ul class="mb-0 ps-3">' + r.otherCopies.map(c => '<li><span class="path">' + esc(c.path) + '</span> ' + statusBadge(c.status) + '</li>').join('') + '</ul>'
        : 'None known';
      d.body.innerHTML = '<dl class="kv mb-0">'
        + '<dt>Name</dt><dd>' + esc(f.name) + '</dd>'
        + '<dt>Full path</dt><dd class="path">' + esc(f.path) + '</dd>'
        + '<dt>Size</dt><dd>' + fmtBytes(f.size) + ' (' + fmtNum(f.size) + ' bytes)</dd>'
        + '<dt>Extension</dt><dd>' + esc(f.extension || '—') + '</dd>'
        + '<dt>Type</dt><dd>' + esc(r.category) + ' · ' + esc(f.mime || '') + '</dd>'
        + '<dt>Hash</dt><dd class="hash">' + esc(f.hash || 'Not hashed (no other file has this size)') + '</dd>'
        + '<dt>Created</dt><dd>' + fmtDate(f.createdMs) + '</dd>'
        + '<dt>Modified</dt><dd>' + fmtDate(f.modifiedMs) + '</dd>'
        + '<dt>Last accessed</dt><dd>' + fmtDate(f.accessedMs) + '</dd>'
        + '<dt>Duplicate group</dt><dd>' + (r.groupId ? '#' + r.groupId : 'Not part of a duplicate group') + '</dd>'
        + '<dt>Status</dt><dd>' + statusBadge(f.status) + (r.existsOnDisk ? '' : ' <span class="text-muted">(not at its original location)</span>') + '</dd>'
        + '<dt>Other copies</dt><dd>' + copies + '</dd></dl>';

      const buttons = [];
      if (r.existsOnDisk) buttons.push('<button class="btn btn-outline-secondary" data-act="open"><i class="fa-regular fa-folder-open"></i> Open location</button>');
      if (r.existsOnDisk && f.hash) buttons.push('<button class="btn btn-warning" data-act="quarantine"><i class="fa-solid fa-box-archive"></i> Quarantine</button>');
      if (r.quarantineId) buttons.push('<button class="btn btn-success" data-act="restore"><i class="fa-solid fa-rotate-left"></i> Restore</button>');
      buttons.push('<button class="btn btn-light" data-bs-dismiss="modal">Close</button>');
      d.el.querySelector('.modal-content').insertAdjacentHTML('beforeend', '<div class="modal-footer">' + buttons.join('') + '</div>');

      d.el.addEventListener('click', async ev => {
        const b = ev.target.closest('[data-act]');
        if (!b) return;
        try {
          if (b.dataset.act === 'open') {
            await api('open-location', { body: { id: f.id } });
            toast('Opened the folder in your file manager.', 'ok');
          } else if (b.dataset.act === 'quarantine') {
            d.hide();
            const res = await quarantineFiles([f.id], f.size);
            if (res && onChange) onChange();
          } else if (b.dataset.act === 'restore') {
            const res = await api('restore', { body: { ids: [r.quarantineId] } });
            toast(res.done ? 'File was restored.' : (res.results[0].message || 'File could not be restored.'), res.done ? 'ok' : 'err');
            d.hide();
            if (onChange) onChange();
          }
        } catch (e) { toast(e.message, 'err'); }
      });
    } catch (e) {
      d.body.innerHTML = '<div class="empty"><i class="fa-solid fa-triangle-exclamation"></i>' + esc(e.message) + '</div>';
    }
  }

  // ---------- shell ----------
  const NAV = [
    ['Overview', [['dashboard', 'Dashboard', 'fa-gauge-high'], ['scan', 'Scan Files', 'fa-magnifying-glass'],
      ['duplicates', 'Duplicate Files', 'fa-clone'], ['storage', 'Storage', 'fa-hard-drive'], ['quarantine', 'Quarantine', 'fa-box-archive']]],
    ['Insights', [['history', 'History', 'fa-clock-rotate-left'], ['logs', 'Activity Logs', 'fa-list-check'], ['analytics', 'Analytics', 'fa-chart-line']]],
    ['Account', [['settings', 'Settings', 'fa-gear']]]
  ];
  const THEME_ICON = { light: 'fa-sun', dark: 'fa-moon', system: 'fa-circle-half-stroke' };

  async function logout() {
    try { await api('logout', { method: 'POST', body: {} }); } catch (e) { /* leave anyway */ }
    location.href = BASE + 'login.html';
  }

  function buildShell(user) {
    const page = document.getElementById('page');
    const key = document.body.dataset.page;
    document.title = (page.dataset.title || 'App') + ' · Smart Duplicate File Finder';
    const shell = document.createElement('div');
    shell.className = 'app-shell';
    shell.innerHTML =
      '<aside class="sidebar" id="sidebar">'
      + '<div class="brand"><div class="logo"><i class="fa-solid fa-broom"></i></div><div>Smart Duplicate<br>File Finder<small>Storage Cleaner</small></div></div>'
      + '<nav>' + NAV.map(g => '<div class="nav-label">' + g[0] + '</div>' + g[1].map(n => '<a href="' + n[0] + '.html" class="' + (n[0] === key ? 'active' : '')
        + '"><i class="fa-solid ' + n[2] + '"></i>' + n[1] + '</a>').join('')).join('') + '</nav>'
      + '<div class="user-card"><div class="avatar">' + esc(user.charAt(0)) + '</div><div class="who"><b>' + esc(user) + '</b><small>Signed in</small></div>'
      + '<button id="logoutBtn" title="Logout" aria-label="Logout"><i class="fa-solid fa-right-from-bracket"></i></button></div></aside>'
      + '<div class="content-wrap"><header class="topbar">'
      + '<button class="icon-btn d-lg-none" id="menuBtn" aria-label="Menu"><i class="fa-solid fa-bars"></i></button>'
      + '<h1>' + esc(page.dataset.title || '') + '</h1>'
      + '<a id="scanPill" href="scan.html" class="scan-pill badge text-bg-primary text-decoration-none align-items-center gap-1"></a>'
      + '<button class="icon-btn" id="themeBtn" title="Change theme" aria-label="Change theme"><i class="fa-solid"></i></button>'
      + '</header><div class="content" id="content"></div></div>';
    document.body.insertBefore(shell, document.body.firstChild);
    shell.querySelector('#content').appendChild(page);

    const themeBtn = shell.querySelector('#themeBtn');
    const paintTheme = () => { themeBtn.querySelector('i').className = 'fa-solid ' + THEME_ICON[getTheme()]; };
    paintTheme();
    window.addEventListener('sd-theme', paintTheme);
    themeBtn.addEventListener('click', () => { setTheme({ light: 'dark', dark: 'system', system: 'light' }[getTheme()]); paintTheme(); });
    shell.querySelector('#menuBtn').addEventListener('click', () => shell.querySelector('#sidebar').classList.toggle('open'));
    shell.querySelector('#logoutBtn').addEventListener('click', logout);

    // small "scan running" indicator on every page
    const pill = shell.querySelector('#scanPill');
    async function pollPill() {
      if (document.hidden || key === 'scan') return;
      try {
        const p = await api('scan/progress');
        const active = p.status === 'RUNNING' || p.status === 'PAUSED';
        pill.classList.toggle('on', active);
        if (active) pill.innerHTML = '<i class="fa-solid fa-spinner spin"></i> Scan ' + p.status.toLowerCase() + (p.percent >= 0 ? ' · ' + p.percent + '%' : '');
      } catch (e) { /* ignore */ }
    }
    pollPill();
    setInterval(pollPill, 3000);
  }

  const ready = (async () => {
    const s = await api('session', { noRedirect: true });
    if (!s.authenticated) {
      location.href = BASE + 'login.html';
      return new Promise(() => { });
    }
    csrf = s.csrf;
    buildShell(s.username);
    return s;
  })();

  window.App = { ready, api, esc, fmtBytes, fmtNum, fmtDate, fmtDur, statusBadge, toast, modal, confirmDialog, busy, chart,
    quarantineFiles, showFileDetails, PALETTE, BASE, getTheme, setTheme, logout, setCsrf: t => { csrf = t; } };
})();

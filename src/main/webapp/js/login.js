(function () {
  'use strict';
  const base = location.pathname.substring(0, location.pathname.lastIndexOf('/') + 1);
  const form = document.getElementById('loginForm');
  const err = document.getElementById('error');
  const btn = document.getElementById('submit');

  function show(msg) {
    err.textContent = msg;   // textContent: never interpreted as HTML
    err.classList.remove('d-none');
  }

  if (new URLSearchParams(location.search).get('registered')) {
    const n = document.getElementById('notice');
    n.textContent = 'Account created. Please sign in.';
    n.classList.remove('d-none');
  }

  // Already signed in? Go straight to the dashboard.
  fetch(base + 'api/session', { credentials: 'same-origin' })
    .then(r => r.json())
    .then(s => { if (s.authenticated) location.replace(base + 'dashboard.html'); })
    .catch(() => { });

  form.addEventListener('submit', async ev => {
    ev.preventDefault();
    err.classList.add('d-none');
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    document.getElementById('username').classList.toggle('is-invalid', !username);
    document.getElementById('password').classList.toggle('is-invalid', !password);
    if (!username || !password) return show('Please enter your username and password.');

    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Signing in…';
    try {
      const res = await fetch(base + 'api/login', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'Sign in failed.');
      location.replace(base + 'dashboard.html');
    } catch (e) {
      show(e.message === 'Failed to fetch' ? 'Cannot reach the server. Is it still running?' : e.message);
      btn.disabled = false;
      btn.textContent = 'Sign in';
    }
  });
})();

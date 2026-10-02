(function () {
  'use strict';
  const base = location.pathname.substring(0, location.pathname.lastIndexOf('/') + 1);
  const $ = id => document.getElementById(id);
  const form = $('signupForm'), err = $('error'), btn = $('submit');

  function show(msg) {
    err.textContent = msg;   // textContent: never interpreted as HTML
    err.classList.remove('d-none');
  }

  // Same rules as the server (AuthService.register); the server remains the authority.
  function check(v) {
    const e = {};
    if (!/^[A-Za-z0-9_.-]{3,32}$/.test(v.username)) e.username = '3-32 characters: letters, numbers, dot, dash or underscore.';
    if (!/^[^\s@]{1,64}@[^\s@]{1,120}\.[^\s@.]{2,}$/.test(v.email)) e.email = 'Please enter a valid email address.';
    if (v.password.length < 8) e.password = 'Password must be at least 8 characters.';
    if (v.password !== v.confirm) e.confirm = 'Passwords do not match.';
    return e;
  }
  function mark(errors) {
    ['username', 'email', 'password', 'confirm'].forEach(id => {
      const el = $(id), fb = el.parentElement.querySelector('.invalid-feedback');
      el.classList.toggle('is-invalid', !!errors[id]);
      el.classList.toggle('is-valid', !errors[id] && el.value !== '');
      if (fb) fb.textContent = errors[id] || '';
    });
  }
  ['username', 'email', 'password', 'confirm'].forEach(id => $(id).addEventListener('blur', () => {
    if ($(id).value !== '') mark(check({ username: $('username').value.trim(), email: $('email').value.trim(), password: $('password').value, confirm: $('confirm').value }));
  }));

  $('password').addEventListener('input', () => {
    const p = $('password').value;
    let score = 0;
    if (p.length >= 8) score++;
    if (p.length >= 12) score++;
    if (/[a-z]/.test(p) && /[A-Z]/.test(p)) score++;
    if (/\d/.test(p) && /[^A-Za-z0-9]/.test(p)) score++;
    const bar = $('strengthBar');
    bar.style.width = (p ? Math.max(1, score) * 25 : 0) + '%';
    bar.style.background = ['#dc2626', '#dc2626', '#f59e0b', '#16a34a', '#16a34a'][p ? score : 0];
    $('strengthText').textContent = !p ? 'Use 8+ characters; mix letters, numbers and symbols.'
      : ['Too weak', 'Weak', 'Fair', 'Good', 'Strong'][score];
  });

  form.addEventListener('submit', async ev => {
    ev.preventDefault();
    err.classList.add('d-none');
    const v = { username: $('username').value.trim(), email: $('email').value.trim(),
      password: $('password').value, confirm: $('confirm').value };
    const problems = check(v);
    mark(problems);
    if (Object.keys(problems).length) return;

    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Creating account…';
    try {
      const res = await fetch(base + 'api/register', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(v)
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'Could not create the account.');
      location.replace(base + 'login.html?registered=1');
    } catch (e) {
      show(e.message === 'Failed to fetch' ? 'Cannot reach the server. Is it still running?' : e.message);
      btn.disabled = false;
      btn.textContent = 'Create account';
    }
  });
})();

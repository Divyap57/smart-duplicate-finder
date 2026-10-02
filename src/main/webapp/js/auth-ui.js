/* Shared by the sign-in and sign-up pages: saved theme + show/hide password buttons. */
(function () {
  'use strict';
  try {
    const t = localStorage.getItem('sd-theme') || 'system';
    const dark = t === 'dark' || (t === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.setAttribute('data-bs-theme', dark ? 'dark' : 'light');
  } catch (e) { /* storage blocked: stay light */ }

  document.addEventListener('click', ev => {
    const b = ev.target.closest('[data-toggle-pw]');
    if (!b) return;
    const input = document.getElementById(b.dataset.togglePw);
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    b.querySelector('i').className = show ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
  });
})();

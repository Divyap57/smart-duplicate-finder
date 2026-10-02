App.ready.then(() => {
  const { esc, fmtDate } = App;
  const $ = id => document.getElementById(id);
  let filled = false;

  async function load() {
    try {
      const r = await App.api('logs?limit=300&action=' + encodeURIComponent($('action').value));
      if (!filled) {
        $('action').insertAdjacentHTML('beforeend', r.actions.map(a => '<option>' + esc(a) + '</option>').join(''));
        filled = true;
      }
      $('table').innerHTML = r.logs.length
        ? '<table class="table table-sm table-hover"><thead><tr><th>Time</th><th>User</th><th>Action</th><th>Description</th><th>IP</th></tr></thead><tbody>'
          + r.logs.map(l => '<tr><td class="text-nowrap">' + fmtDate(l.ts) + '</td><td>' + esc(l.username) + '</td>'
            + '<td><span class="badge text-bg-light border">' + esc(l.action) + '</span></td><td>' + esc(l.description) + '</td><td class="text-muted">' + esc(l.ip || '') + '</td></tr>').join('')
          + '</tbody></table>'
        : '<div class="empty"><i class="fa-regular fa-clock"></i>No activity recorded.</div>';
    } catch (e) { $('table').innerHTML = '<div class="empty">' + esc(e.message) + '</div>'; }
  }

  $('action').addEventListener('change', load);
  $('refresh').addEventListener('click', load);
  load();
});

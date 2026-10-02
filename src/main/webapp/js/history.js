App.ready.then(() => {
  const { esc, fmtBytes, fmtNum, fmtDate, fmtDur, statusBadge } = App;
  const $ = id => document.getElementById(id);

  async function load() {
    try {
      const r = await App.api('scans');
      $('table').innerHTML = r.scans.length
        ? '<table class="table table-hover"><thead><tr><th>#</th><th>Started</th><th>Duration</th><th>Folder</th><th>Files</th><th>Dup. files</th><th>Groups</th><th>Recoverable</th><th>Status</th><th></th></tr></thead><tbody>'
          + r.scans.map(s => '<tr><td>' + s.id + '</td><td class="text-nowrap">' + fmtDate(s.startedAt) + '</td>'
            + '<td class="text-nowrap">' + (s.endedAt ? fmtDur(s.endedAt - s.startedAt) : '—') + '</td><td class="path">' + esc(s.folder) + '</td>'
            + '<td>' + fmtNum(s.totalFiles) + '</td><td>' + fmtNum(s.duplicateFiles) + '</td><td>' + fmtNum(s.duplicateGroups) + '</td>'
            + '<td class="text-nowrap">' + fmtBytes(s.recoverableBytes) + '</td><td>' + statusBadge(s.status) + '</td>'
            + '<td class="text-end"><button class="btn btn-sm btn-light" data-scan="' + s.id + '"><i class="fa-solid fa-circle-info"></i> Details</button></td></tr>').join('')
          + '</tbody></table>'
        : '<div class="empty"><i class="fa-regular fa-folder-open"></i>No scans yet. <a href="scan.html">Start one</a>.</div>';
    } catch (e) { $('table').innerHTML = '<div class="empty">' + esc(e.message) + '</div>'; }
  }

  async function details(id) {
    const d = App.modal({ title: 'Scan #' + id, size: 'modal-lg', body: '<div class="loading"><i class="fa-solid fa-spinner spin"></i></div>' });
    try {
      const r = await App.api('scans/' + id);
      const s = r.scan;
      d.body.innerHTML = '<dl class="kv mb-3">'
        + '<dt>Status</dt><dd>' + statusBadge(s.status) + (s.message ? ' <span class="text-muted">' + esc(s.message) + '</span>' : '') + '</dd>'
        + '<dt>Folder</dt><dd class="path">' + esc(s.folder) + '</dd>'
        + '<dt>Started</dt><dd>' + fmtDate(s.startedAt) + ' by ' + esc(s.username) + '</dd>'
        + '<dt>Finished</dt><dd>' + fmtDate(s.endedAt) + '</dd>'
        + '<dt>Files / folders</dt><dd>' + fmtNum(s.totalFiles) + ' files in ' + fmtNum(s.totalFolders) + ' folders (' + fmtBytes(s.totalBytes) + ')</dd>'
        + '<dt>Duplicates</dt><dd>' + fmtNum(s.duplicateFiles) + ' files in ' + fmtNum(s.duplicateGroups) + ' groups · ' + fmtBytes(s.recoverableBytes) + ' recoverable</dd>'
        + '<dt>Unreadable files</dt><dd>' + fmtNum(s.skippedFiles) + '</dd></dl>'
        + (r.topGroups.length
          ? '<h6>Largest duplicate groups (as found)</h6><table class="table table-sm"><thead><tr><th>Hash</th><th>Copies</th><th>Size each</th><th>Recoverable</th></tr></thead><tbody>'
            + r.topGroups.map(g => '<tr><td class="hash" title="' + esc(g.hash) + '">' + esc(g.hash.substring(0, 16)) + '…</td><td>' + g.fileCount + '</td><td>' + fmtBytes(g.size) + '</td><td>' + fmtBytes(g.recoverableBytes) + '</td></tr>').join('')
            + '</tbody></table>' : '')
        + (r.types.length
          ? '<h6 class="mt-3">File types</h6><div class="d-flex flex-wrap gap-2">' + r.types.map(t => '<span class="badge text-bg-light border">' + esc(t.category) + ' · ' + fmtNum(t.count) + ' · ' + fmtBytes(t.bytes) + '</span>').join('') + '</div>' : '');
    } catch (e) { d.body.innerHTML = '<div class="empty">' + esc(e.message) + '</div>'; }
  }

  $('table').addEventListener('click', ev => { const b = ev.target.closest('[data-scan]'); if (b) details(Number(b.dataset.scan)); });
  $('refresh').addEventListener('click', load);
  load();
});

App.ready.then(async session => {
  const { esc, fmtBytes, fmtNum, fmtDate, statusBadge, PALETTE } = App;
  const $ = id => document.getElementById(id);

  $('welcome').textContent = 'Welcome back, ' + session.username;

  function card(label, value, icon, tone, small) {
    return '<div class="col-6 ' + (small ? 'col-md-3' : 'col-xl-3') + '"><div class="stat' + (small ? ' sm' : '') + '"><div class="ico tone-' + tone + '"><i class="fa-solid ' + icon + '"></i></div>'
      + '<div><div class="label">' + label + '</div><div class="value">' + value + '</div></div></div></div>';
  }

  try {
    const d = await App.api('dashboard');
    $('empty').classList.toggle('d-none', d.hasScan);

    $('cards').innerHTML =
      card('Files scanned', fmtNum(d.totalFiles), 'fa-file', 'blue')
      + card('Duplicate files', fmtNum(d.duplicateFiles), 'fa-clone', 'red')
      + card('Space recoverable', fmtBytes(d.recoverableBytes), 'fa-recycle', 'green')
      + card('Quarantined files', fmtNum(d.quarantined), 'fa-box-archive', 'amber');
    $('cards2').innerHTML =
      card('Folders', fmtNum(d.totalFolders), 'fa-folder', 'slate', true)
      + card('Scanned storage', fmtBytes(d.totalBytes), 'fa-database', 'violet', true)
      + card('Duplicate groups', fmtNum(d.duplicateGroups), 'fa-layer-group', 'amber', true)
      + card('Last scan', d.lastScanAt ? fmtDate(d.lastScanAt) : 'Never', 'fa-clock', 'slate', true);

    const needed = Math.max(0, d.totalBytes - d.recoverableBytes);
    const pct = v => d.totalBytes > 0 ? (v * 100 / d.totalBytes).toFixed(1) + '%' : '0';
    $('mNeeded').style.width = pct(needed);
    $('mDup').style.width = pct(d.recoverableBytes);
    $('lNeeded').textContent = fmtBytes(needed);
    $('lDup').textContent = fmtBytes(d.recoverableBytes);
    $('lTotal').textContent = d.hasScan ? 'of ' + fmtBytes(d.totalBytes) + ' scanned' : 'No scan yet';

    // 1. storage by type
    App.chart('chStorage', 'bar', {
      labels: d.types.map(t => t.category),
      datasets: [{ label: 'Size (GB)', data: d.types.map(t => +(t.bytes / 1073741824).toFixed(3)), backgroundColor: PALETTE, borderRadius: 6 }]
    }, { plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => fmtBytes(d.types[c.dataIndex].bytes) } } }, scales: { y: { beginAtZero: true, title: { display: true, text: 'GB' } } } });

    // 2. duplicate vs unique storage
    const unique = Math.max(0, d.totalBytes - d.recoverableBytes);
    App.chart('chDup', 'doughnut', {
      labels: ['Needed', 'Recoverable duplicates'],
      datasets: [{ data: [unique, d.recoverableBytes], backgroundColor: ['#6366f1', '#f59e0b'], borderWidth: 0 }]
    }, { plugins: { tooltip: { callbacks: { label: c => c.label + ': ' + fmtBytes(c.parsed) } } } });

    // 3. file types by count
    App.chart('chTypes', 'doughnut', {
      labels: d.types.map(t => t.category),
      datasets: [{ data: d.types.map(t => t.count), backgroundColor: PALETTE }]
    }, { plugins: { legend: { position: 'bottom' } } });

    // 4. scan history
    App.chart('chHistory', 'line', {
      labels: d.scanHistory.map(s => '#' + s.id),
      datasets: [
        { label: 'Files scanned', data: d.scanHistory.map(s => s.totalFiles), borderColor: '#6366f1', backgroundColor: '#6366f1', tension: .3, yAxisID: 'y' },
        { label: 'Duplicate files', data: d.scanHistory.map(s => s.duplicateFiles), borderColor: '#dc2626', backgroundColor: '#dc2626', tension: .3, yAxisID: 'y' }
      ]
    }, { scales: { y: { beginAtZero: true } } });

    $('recentScans').innerHTML = d.recentScans.length
      ? '<table class="table table-sm"><thead><tr><th>#</th><th>Folder</th><th>Files</th><th>Status</th></tr></thead><tbody>'
        + d.recentScans.map(s => '<tr><td>' + s.id + '</td><td class="path">' + esc(s.folder) + '</td><td>' + fmtNum(s.totalFiles) + '</td><td>' + statusBadge(s.status) + '</td></tr>').join('')
        + '</tbody></table>'
      : '<div class="empty"><i class="fa-regular fa-folder-open"></i>No scans yet.</div>';

    $('recentActivity').innerHTML = d.recentActivity.length
      ? '<table class="table table-sm"><thead><tr><th>When</th><th>Action</th><th>Details</th></tr></thead><tbody>'
        + d.recentActivity.map(a => '<tr><td class="text-nowrap">' + fmtDate(a.ts) + '</td><td><span class="badge text-bg-light">' + esc(a.action) + '</span></td><td>' + esc(a.description) + '</td></tr>').join('')
        + '</tbody></table>'
      : '<div class="empty"><i class="fa-regular fa-clock"></i>No activity yet.</div>';
  } catch (e) {
    App.toast(e.message, 'err');
  }
});

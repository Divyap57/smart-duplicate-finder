App.ready.then(async () => {
  const { esc, fmtBytes, fmtNum, fmtDate, PALETTE } = App;
  const $ = id => document.getElementById(id);

  async function load() {
    try {
      const d = await App.api('storage');
      $('empty').classList.toggle('d-none', d.hasScan);
      $('storageBody').classList.toggle('d-none', !d.hasScan);
      if (!d.hasScan) return;

      const t = await App.api('dashboard');
      const card = (l, v, i, tone) => '<div class="col-6 col-xl-3"><div class="stat"><div class="ico tone-' + tone + '"><i class="fa-solid ' + i + '"></i></div>'
        + '<div><div class="label">' + l + '</div><div class="value">' + v + '</div></div></div></div>';
      const needed = Math.max(0, t.totalBytes - t.recoverableBytes);
      $('sCards').innerHTML = card('Total scanned storage', fmtBytes(t.totalBytes), 'fa-database', 'violet')
        + card('Needed (unique) storage', fmtBytes(needed), 'fa-hard-drive', 'blue')
        + card('Duplicate files', fmtNum(t.duplicateFiles), 'fa-clone', 'red')
        + card('Recoverable storage', fmtBytes(t.recoverableBytes), 'fa-recycle', 'green');
      const pct = v => t.totalBytes > 0 ? (v * 100 / t.totalBytes).toFixed(1) + '%' : '0';
      $('mNeeded').style.width = pct(needed);
      $('mDup').style.width = pct(t.recoverableBytes);
      $('lNeeded').textContent = fmtBytes(needed);
      $('lDup').textContent = fmtBytes(t.recoverableBytes);

      App.chart('chTypes', 'doughnut', {
        labels: d.types.map(t => t.category),
        datasets: [{ data: d.types.map(t => t.bytes), backgroundColor: PALETTE }]
      }, { plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: c => c.label + ': ' + fmtBytes(c.parsed) } } } });

      const max = Math.max(1, ...d.topFolders.map(f => f.bytes));
      $('folders').innerHTML = d.topFolders.map(f =>
        '<div class="mb-2"><div class="d-flex justify-content-between"><span class="path">' + esc(f.folder) + '</span>'
        + '<span class="text-nowrap ms-2"><b>' + fmtBytes(f.bytes) + '</b> <span class="text-muted">· ' + fmtNum(f.files) + ' files</span></span></div>'
        + '<div class="progress" style="height:6px"><div class="progress-bar" style="width:' + (f.bytes * 100 / max).toFixed(1) + '%"></div></div></div>').join('');

      $('largest').innerHTML = '<table class="table table-hover"><thead><tr><th>File</th><th>Size</th><th>Modified</th><th></th></tr></thead><tbody>'
        + d.largestFiles.map(f => '<tr><td><div class="fw-semibold">' + esc(f.name) + '</div><div class="path">' + esc(f.path) + '</div></td>'
          + '<td class="text-nowrap">' + fmtBytes(f.size) + '</td><td class="text-nowrap">' + fmtDate(f.modifiedMs) + '</td>'
          + '<td class="text-end"><button class="btn btn-sm btn-light" data-details="' + f.id + '"><i class="fa-solid fa-circle-info"></i> Details</button></td></tr>').join('')
        + '</tbody></table>';
    } catch (e) { App.toast(e.message, 'err'); }
  }

  $('largest').addEventListener('click', ev => {
    const b = ev.target.closest('[data-details]');
    if (b) App.showFileDetails(Number(b.dataset.details), load);
  });
  load();
});

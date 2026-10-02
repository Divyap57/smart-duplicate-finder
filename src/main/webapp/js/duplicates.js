App.ready.then(() => {
  const { esc, fmtBytes, fmtNum, fmtDate } = App;
  const $ = id => document.getElementById(id);
  const PAGE = 100;

  let groups = [];            // loaded groups
  let total = 0;
  const selected = new Set(); // file ids chosen for quarantine
  const open = new Set();     // expanded group ids
  const fileSize = new Map(); // file id -> size

  function summary(r) {
    $('sumGroups').textContent = fmtNum(r.totalGroups) + (r.totalGroups === 1 ? ' group' : ' groups');
    $('sumFiles').textContent = fmtNum(r.totalFiles) + ' files';
    $('sumRecover').textContent = fmtBytes(r.recoverableBytes);
  }

  function updateSelection() {
    let bytes = 0;
    selected.forEach(id => { bytes += fileSize.get(id) || 0; });
    $('sumSelected').textContent = selected.size ? selected.size + ' selected · ' + fmtBytes(bytes) + ' will be freed' : 'Nothing selected';
    $('quarantineBtn').disabled = selected.size === 0;
    // reflect state in the DOM without a full re-render
    document.querySelectorAll('input[data-file]').forEach(cb => { cb.checked = selected.has(Number(cb.dataset.file)); });
    groups.forEach(g => {
      const head = document.querySelector('[data-gid="' + g.id + '"] .keep-warn');
      if (head) head.classList.toggle('d-none', !g.files.every(f => selected.has(f.id)));
    });
  }

  function groupHtml(g) {
    const first = g.files[0];
    return '<div class="group' + (open.has(g.id) ? ' open' : '') + '" data-gid="' + g.id + '">'
      + '<div class="group-head" data-toggle>'
      + '<i class="fa-solid fa-chevron-right chev"></i>'
      + '<div class="group-title">' + esc(first.name) + ' <span class="text-muted fw-normal">· ' + esc(g.category) + '</span></div>'
      + '<span class="badge text-bg-danger keep-warn d-none">No copy kept</span>'
      + '<span class="badge text-bg-light">' + g.fileCount + ' copies</span>'
      + '<span class="text-muted">' + fmtBytes(g.size) + ' each</span>'
      + '<span class="badge text-bg-success">Recover ' + fmtBytes(g.recoverableBytes) + '</span></div>'
      + '<div class="group-body"><div class="table-responsive"><table class="table table-sm table-hover mb-0"><thead><tr>'
      + '<th style="width:36px"></th><th>Name &amp; path</th><th>Size</th><th>Modified</th><th>Hash</th><th class="text-end">Actions</th></tr></thead><tbody>'
      + g.files.map(f => '<tr><td><input type="checkbox" class="form-check-input" data-file="' + f.id + '"></td>'
        + '<td><div class="fw-semibold">' + esc(f.name) + '</div><div class="path">' + esc(f.path) + '</div></td>'
        + '<td class="text-nowrap">' + fmtBytes(f.size) + '</td><td class="text-nowrap">' + fmtDate(f.modifiedMs) + '</td>'
        + '<td class="hash" title="' + esc(f.hash) + '">' + esc(f.hash.substring(0, 12)) + '…</td>'
        + '<td class="text-end text-nowrap">'
        + '<button class="btn btn-sm btn-light" data-details="' + f.id + '" title="Details"><i class="fa-solid fa-circle-info"></i></button> '
        + '<button class="btn btn-sm btn-light" data-open="' + f.id + '" title="Open file location"><i class="fa-regular fa-folder-open"></i></button>'
        + '</td></tr>').join('')
      + '</tbody></table></div></div></div>';
  }

  function render() {
    $('groups').innerHTML = groups.length ? groups.map(groupHtml).join('')
      : '<div class="panel"><div class="empty mb-0"><i class="fa-solid fa-circle-check"></i>No duplicate files match. '
        + '<a href="scan.html">Run a scan</a> or relax the filters.</div></div>';
    $('moreBtn').classList.toggle('d-none', groups.length >= total);
    updateSelection();
  }

  function query(offset) {
    const p = new URLSearchParams({ q: $('q').value.trim(), type: $('type').value, minSize: $('minSize').value,
      sort: $('sort').value, offset: offset, limit: PAGE });
    return 'duplicates?' + p.toString();
  }

  async function load(append) {
    try {
      if (!append) { $('groups').innerHTML = '<div class="loading"><i class="fa-solid fa-spinner spin"></i> Loading…</div>'; }
      const r = await App.api(query(append ? groups.length : 0));
      if (!append) { groups = []; selected.clear(); open.clear(); }
      total = r.totalGroups;
      r.groups.forEach(g => { groups.push(g); g.files.forEach(f => fileSize.set(f.id, f.size)); });
      summary(r);
      if (!r.hasScan) {
        $('groups').innerHTML = '<div class="panel"><div class="empty mb-0"><i class="fa-solid fa-magnifying-glass-chart"></i>'
          + 'No scan results yet. <a href="scan.html">Start a scan</a> first.</div></div>';
        return;
      }
      render();
    } catch (e) {
      $('groups').innerHTML = '<div class="panel"><div class="empty mb-0"><i class="fa-solid fa-triangle-exclamation"></i>' + esc(e.message) + '</div></div>';
    }
  }

  // ---------- auto-select rules (applied to every loaded group) ----------
  function keeper(g, rule) {
    const fs = g.files;
    if (rule === 'oldest') return fs.reduce((a, b) => b.modifiedMs < a.modifiedMs ? b : a);
    if (rule === 'newest') return fs.reduce((a, b) => b.modifiedMs > a.modifiedMs ? b : a);
    if (rule === 'shortest') return fs.reduce((a, b) => b.path.length < a.path.length ? b : a);
    return fs[0]; // first
  }
  function applyRule(rule) {
    selected.clear();
    if (rule !== 'clear') {
      groups.forEach(g => { const k = keeper(g, rule); g.files.forEach(f => { if (f.id !== k.id) selected.add(f.id); }); });
    }
    updateSelection();
    if (rule !== 'clear') App.toast(selected.size + ' duplicate' + (selected.size === 1 ? '' : 's') + ' selected. One copy per group is kept.', 'info');
  }

  // ---------- events ----------
  $('groups').addEventListener('click', ev => {
    const det = ev.target.closest('[data-details]');
    if (det) return App.showFileDetails(Number(det.dataset.details), () => load(false));
    const opn = ev.target.closest('[data-open]');
    if (opn) {
      return App.api('open-location', { body: { id: Number(opn.dataset.open) } })
        .then(() => App.toast('Opened the folder in your file manager.', 'ok')).catch(e => App.toast(e.message, 'err'));
    }
    if (ev.target.closest('input[data-file]')) return;
    const head = ev.target.closest('[data-toggle]');
    if (head) {
      const el = head.parentElement;
      const id = Number(el.dataset.gid);
      el.classList.toggle('open');
      if (el.classList.contains('open')) open.add(id); else open.delete(id);
    }
  });
  $('groups').addEventListener('change', ev => {
    const cb = ev.target.closest('input[data-file]');
    if (!cb) return;
    const id = Number(cb.dataset.file);
    if (cb.checked) selected.add(id); else selected.delete(id);
    updateSelection();
  });
  document.querySelectorAll('[data-rule]').forEach(b => b.addEventListener('click', () => applyRule(b.dataset.rule)));
  $('toggleAll').addEventListener('click', () => {
    const allOpen = groups.length && groups.every(g => open.has(g.id));
    groups.forEach(g => { if (allOpen) open.delete(g.id); else open.add(g.id); });
    render();
  });
  $('moreBtn').addEventListener('click', ev => App.busy(ev.currentTarget, () => load(true)));
  $('refreshBtn').addEventListener('click', () => load(false));

  let debounce;
  $('q').addEventListener('input', () => { clearTimeout(debounce); debounce = setTimeout(() => load(false), 300); });
  ['type', 'minSize', 'sort'].forEach(id => $(id).addEventListener('change', () => load(false)));

  $('quarantineBtn').addEventListener('click', async ev => {
    await App.busy(ev.currentTarget, async () => {
      try {
        let bytes = 0;
        selected.forEach(id => { bytes += fileSize.get(id) || 0; });
        const r = await App.quarantineFiles(Array.from(selected), bytes);
        if (r) await load(false);
      } catch (e) { App.toast(e.message, 'err'); }
    });
    updateSelection(); // busy() re-enables the button; restore the right disabled state
  });

  load(false);
});

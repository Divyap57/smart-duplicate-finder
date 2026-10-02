App.ready.then(() => {
  const { esc, fmtBytes, fmtNum, fmtDate, statusBadge } = App;
  const $ = id => document.getElementById(id);
  let items = [];
  const selected = new Set();

  function updateButtons() {
    const n = selected.size;
    $('restoreSel').disabled = n === 0;
    $('deleteSel').disabled = n === 0;
    $('restoreSel').innerHTML = '<i class="fa-solid fa-rotate-left"></i> Restore selected' + (n ? ' (' + n + ')' : '');
    $('deleteSel').innerHTML = '<i class="fa-solid fa-trash"></i> Delete selected' + (n ? ' (' + n + ')' : '');
    const all = $('selAll');
    if (all) all.checked = items.some(i => i.status === 'QUARANTINED') && items.filter(i => i.status === 'QUARANTINED').every(i => selected.has(i.id));
    document.querySelectorAll('input[data-q]').forEach(cb => { cb.checked = selected.has(Number(cb.dataset.q)); });
  }

  function render() {
    if (!items.length) {
      $('table').innerHTML = '<div class="empty"><i class="fa-solid fa-box-open"></i><h5>Quarantine is empty</h5>Files you quarantine from <a href="duplicates.html">Duplicate Files</a> appear here, ready to restore.</div>';
      return updateButtons();
    }
    $('table').innerHTML = '<table class="table table-hover"><thead><tr><th style="width:36px"><input type="checkbox" class="form-check-input" id="selAll"></th>'
      + '<th>File</th><th>Original location</th><th>Quarantined</th><th>Size</th><th>Hash</th><th>Status</th><th class="text-end">Actions</th></tr></thead><tbody>'
      + items.map(i => {
        const live = i.status === 'QUARANTINED';
        return '<tr><td>' + (live ? '<input type="checkbox" class="form-check-input" data-q="' + i.id + '">' : '') + '</td>'
          + '<td class="fw-semibold">' + esc(i.name) + '</td><td class="path">' + esc(i.originalPath) + '</td>'
          + '<td class="text-nowrap">' + fmtDate(i.quarantinedAt) + '</td><td class="text-nowrap">' + fmtBytes(i.size) + '</td>'
          + '<td class="hash" title="' + esc(i.hash) + '">' + esc((i.hash || '').substring(0, 12)) + '…</td><td>' + statusBadge(i.status) + '</td>'
          + '<td class="text-end text-nowrap">' + (live
            ? '<button class="btn btn-sm btn-success" data-restore="' + i.id + '"><i class="fa-solid fa-rotate-left"></i> Restore</button> '
              + '<button class="btn btn-sm btn-outline-danger" data-delete="' + i.id + '"><i class="fa-solid fa-trash"></i> Delete</button>'
            : '') + '</td></tr>';
      }).join('') + '</tbody></table>';
    updateButtons();
  }

  async function load() {
    try {
      const r = await App.api('quarantine?status=' + encodeURIComponent($('status').value));
      items = r.items;
      selected.clear();
      $('summary').textContent = fmtNum(items.length) + (items.length === 1 ? ' item' : ' items') + ' · ' + fmtBytes(r.totalBytes);
      render();
    } catch (e) {
      $('table').innerHTML = '<div class="empty"><i class="fa-solid fa-triangle-exclamation"></i>' + esc(e.message) + '</div>';
    }
  }

  function report(r, verbPast) {
    if (r.done) App.toast(r.done + (r.done > 1 ? ' files were ' : ' file was ') + verbPast + '.', 'ok');
    const bad = r.results.filter(x => !x.ok);
    if (bad.length) App.toast(bad.length + ' could not be processed: ' + bad[0].message, 'err');
  }

  async function restore(ids) {
    try {
      report(await App.api('restore', { body: { ids } }), 'restored to the original location');
      await load();
    } catch (e) { App.toast(e.message, 'err'); }
  }

  async function remove(ids) {
    const bytes = items.filter(i => ids.includes(i.id)).reduce((a, i) => a + i.size, 0);
    const yes = await App.confirmDialog('Delete permanently?',
      'Permanently delete <b>' + ids.length + '</b> file' + (ids.length > 1 ? 's' : '') + ' (' + fmtBytes(bytes)
      + ')?<br><span class="text-danger">This cannot be undone.</span>', 'Delete permanently', true);
    if (!yes) return;
    try {
      report(await App.api('delete', { body: { ids, confirm: true } }), 'permanently deleted');
      await load();
    } catch (e) { App.toast(e.message, 'err'); }
  }

  $('table').addEventListener('click', ev => {
    const r = ev.target.closest('[data-restore]');
    if (r) return restore([Number(r.dataset.restore)]);
    const d = ev.target.closest('[data-delete]');
    if (d) return remove([Number(d.dataset.delete)]);
  });
  $('table').addEventListener('change', ev => {
    if (ev.target.id === 'selAll') {
      items.filter(i => i.status === 'QUARANTINED').forEach(i => { if (ev.target.checked) selected.add(i.id); else selected.delete(i.id); });
    } else if (ev.target.dataset.q) {
      const id = Number(ev.target.dataset.q);
      if (ev.target.checked) selected.add(id); else selected.delete(id);
    }
    updateButtons();
  });
  $('restoreSel').addEventListener('click', () => restore(Array.from(selected)));
  $('deleteSel').addEventListener('click', () => remove(Array.from(selected)));
  $('status').addEventListener('change', load);
  load();
});

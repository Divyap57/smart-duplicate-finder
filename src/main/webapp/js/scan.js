App.ready.then(async () => {
  const { esc, fmtBytes, fmtNum, fmtDur, statusBadge } = App;
  const $ = id => document.getElementById(id);
  let timer = null;
  let startedHere = false;
  let lastStatus = null;

  // ---------- folder picker ----------
  function openPicker() {
    const d = App.modal({
      title: 'Choose a folder', size: 'modal-lg',
      body: '<div class="input-group mb-2"><button class="btn btn-outline-secondary" id="pkUp"><i class="fa-solid fa-arrow-up"></i> Up</button>'
        + '<input class="form-control path" id="pkPath" readonly></div><div class="picker-list" id="pkList"></div>',
      footer: '<button class="btn btn-light" data-bs-dismiss="modal">Cancel</button>'
        + '<button class="btn btn-primary" id="pkUse" disabled><i class="fa-solid fa-check"></i> Use this folder</button>'
    });
    let current = '';
    let parent = null;
    const list = d.el.querySelector('#pkList');

    async function load(path) {
      list.innerHTML = '<div class="loading"><i class="fa-solid fa-spinner spin"></i></div>';
      try {
        const r = await App.api('fs?path=' + encodeURIComponent(path));
        current = r.path;
        parent = r.parent;
        d.el.querySelector('#pkPath').value = current || 'This computer';
        d.el.querySelector('#pkUse').disabled = !current;
        d.el.querySelector('#pkUp').disabled = parent === null;
        list.innerHTML = r.dirs.length
          ? r.dirs.map(x => '<button type="button" data-path="' + esc(x.path) + '"><i class="fa-solid fa-folder text-warning"></i><span>' + esc(x.name) + '</span></button>').join('')
          : '<div class="empty mb-0">No sub-folders here.</div>';
      } catch (e) {
        list.innerHTML = '<div class="empty mb-0"><i class="fa-solid fa-lock"></i>' + esc(e.message) + '</div>';
      }
    }
    list.addEventListener('click', ev => { const b = ev.target.closest('button[data-path]'); if (b) load(b.dataset.path); });
    d.el.querySelector('#pkUp').addEventListener('click', () => load(parent || ''));
    d.el.querySelector('#pkUse').addEventListener('click', () => { $('folder').value = current; d.hide(); });
    load($('folder').value.trim());
  }

  // ---------- progress rendering ----------
  function stat(label, value) {
    return '<div class="col-6 col-md-4 col-xl-3"><div class="stat"><div><div class="label">' + label + '</div><div class="value fs-5">' + value + '</div></div></div></div>';
  }

  function render(p) {
    if (p.status === 'IDLE') { $('progressPanel').classList.add('d-none'); return; }
    $('progressPanel').classList.remove('d-none');
    const active = p.status === 'RUNNING' || p.status === 'PAUSED';
    $('scanNo').textContent = '#' + p.scanId + ' · ' + p.folder;
    $('statusBadge').innerHTML = statusBadge(p.status);
    $('pauseBtn').classList.toggle('d-none', !(p.status === 'RUNNING'));
    $('resumeBtn').classList.toggle('d-none', p.status !== 'PAUSED');
    $('cancelBtn').classList.toggle('d-none', !active);

    const bar = $('bar');
    const indeterminate = p.percent < 0;
    bar.classList.toggle('progress-bar-striped', active);
    bar.classList.toggle('progress-bar-animated', p.status === 'RUNNING');
    bar.classList.toggle('bg-success', p.status === 'COMPLETED');
    bar.classList.toggle('bg-danger', p.status === 'FAILED');
    bar.classList.toggle('bg-secondary', p.status === 'CANCELLED');
    bar.style.width = (indeterminate ? 100 : p.percent) + '%';
    $('percentText').textContent = indeterminate ? '' : p.percent + '%';
    $('phaseText').innerHTML = (p.status === 'RUNNING' ? '<span class="radar"></span>' : '') + esc({ DISCOVERING: 'Step 1 of 3 · Finding files…', HASHING: 'Step 2 of 3 · Comparing file contents…',
      SAVING: 'Step 3 of 3 · Saving results…', DONE: p.status === 'COMPLETED' ? 'Finished' : 'Stopped' }[p.phase] || '');

    $('statGrid').innerHTML =
      stat('Files scanned', fmtNum(p.filesScanned))
      + stat('Folders scanned', fmtNum(p.foldersScanned))
      + stat('Files remaining', p.remaining == null ? '—' : fmtNum(p.remaining))
      + stat('Elapsed', fmtDur(p.elapsedMs))
      + stat('Duplicate files found', fmtNum(p.duplicateFiles))
      + stat('Duplicate groups', fmtNum(p.duplicateGroups))
      + stat('Recoverable storage', fmtBytes(p.recoverableBytes))
      + stat('Unreadable files', fmtNum(p.skipped));
    $('curFolder').textContent = p.currentFolder || '—';
    $('curFile').textContent = p.currentFile || '—';

    const done = $('doneMsg');
    $('resultActions').classList.toggle('d-none', !(p.status === 'COMPLETED'));
    if (!active && p.message) {
      done.className = 'alert mb-0 alert-' + (p.status === 'COMPLETED' ? (p.skipped > 0 ? 'warning' : 'success') : p.status === 'CANCELLED' ? 'secondary' : 'danger');
      done.innerHTML = esc(p.message) + (p.status === 'COMPLETED' ? ' <a href="duplicates.html" class="alert-link">View duplicate files →</a>' : '');
    } else {
      done.className = 'alert d-none mb-0';
    }

    if (lastStatus && lastStatus !== p.status && !active && startedHere) {
      App.toast(p.message || 'Scan finished.', p.status === 'COMPLETED' ? 'ok' : 'err');
    }
    lastStatus = p.status;
    $('startBtn').disabled = active;
    $('folder').disabled = active;
    $('browseBtn').disabled = active;
  }

  async function poll() {
    try {
      const p = await App.api('scan/progress');
      render(p);
      if (p.status !== 'RUNNING' && p.status !== 'PAUSED') stopPolling();
    } catch (e) {
      stopPolling();
      App.toast(e.message, 'err');
    }
  }
  function startPolling() { stopPolling(); timer = setInterval(poll, 700); }
  function stopPolling() { if (timer) { clearInterval(timer); timer = null; } }

  async function command(action, body) {
    const p = await App.api('scan', { body: Object.assign({ action }, body || {}) });
    render(p);
    return p;
  }

  // ---------- wiring ----------
  $('browseBtn').addEventListener('click', openPicker);

  $('startBtn').addEventListener('click', async ev => {
    const btn = ev.currentTarget;
    await App.busy(btn, async () => {
      try {
        startedHere = true;
        lastStatus = null;
        await command('start', { folder: $('folder').value.trim() });
        startPolling();
      } catch (e) {
        startedHere = false;
        App.toast(e.message, 'err');
      }
    });
    // busy() re-enables the button; keep it disabled while a scan is active
    if (lastStatus === 'RUNNING' || lastStatus === 'PAUSED') btn.disabled = true;
  });
  $('pauseBtn').addEventListener('click', async () => { try { await command('pause'); } catch (e) { App.toast(e.message, 'err'); } });
  $('resumeBtn').addEventListener('click', async () => { try { await command('resume'); } catch (e) { App.toast(e.message, 'err'); } });
  $('cancelBtn').addEventListener('click', async () => {
    if (!await App.confirmDialog('Cancel this scan?', 'The scan will stop and no results will be saved.', 'Cancel scan', true)) return;
    try { await command('cancel'); } catch (e) { App.toast(e.message, 'err'); }
  });
  $('folder').addEventListener('keydown', ev => { if (ev.key === 'Enter') $('startBtn').click(); });

  // initial state: default folder from settings, and resume watching a scan that is already running
  try {
    const [settings, p] = await Promise.all([App.api('settings'), App.api('scan/progress')]);
    $('folder').value = settings.defaultDir || '';
    render(p);
    if (p.status === 'RUNNING' || p.status === 'PAUSED') startPolling();

    // links from the dashboard: ?quick=1 scans the default folder, ?pick=1 opens the folder chooser
    const q = new URLSearchParams(location.search);
    const idle = !(p.status === 'RUNNING' || p.status === 'PAUSED');
    if (idle && q.get('quick')) {
      if ($('folder').value) $('startBtn').click();
      else App.toast('Set a default scan folder in Settings to use Quick Scan, or choose a folder below.', 'info');
    } else if (idle && q.get('pick')) {
      openPicker();
    }
    if (q.toString()) history.replaceState(null, '', location.pathname);
  } catch (e) {
    App.toast(e.message, 'err');
  }
});

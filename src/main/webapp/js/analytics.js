App.ready.then(async () => {
  const { fmtBytes, fmtNum, PALETTE } = App;
  try {
    const d = await App.api('analytics');
    document.getElementById('empty').classList.toggle('d-none', d.scans.length > 0 || d.freedByDay.length > 0);

    const labels = d.scans.map(s => '#' + s.id);
    const GB = 1073741824;
    const byteTip = { callbacks: { label: c => fmtBytes(c.raw * GB) } };

    App.chart('c1', 'line', { labels, datasets: [{ label: 'Recoverable (GB)', data: d.scans.map(s => +(s.recoverableBytes / GB).toFixed(3)),
      borderColor: '#f59e0b', backgroundColor: 'rgba(245,158,11,.15)', fill: true, tension: .3 }] },
      { plugins: { legend: { display: false }, tooltip: byteTip }, scales: { y: { beginAtZero: true, title: { display: true, text: 'GB' } } } });

    App.chart('c2', 'line', { labels, datasets: [{ label: 'Files scanned', data: d.scans.map(s => s.totalFiles),
      borderColor: '#2563eb', backgroundColor: 'rgba(37,99,235,.12)', fill: true, tension: .3 }] },
      { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } });

    App.chart('c3', 'bar', { labels, datasets: [{ label: 'Duplicate files', data: d.scans.map(s => s.duplicateFiles), backgroundColor: '#dc2626', borderRadius: 6 }] },
      { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } });

    App.chart('c4', 'bar', { labels: d.freedByDay.map(x => new Date(x.dayMs).toLocaleDateString()),
      datasets: [{ label: 'Freed (GB)', data: d.freedByDay.map(x => +(x.bytes / GB).toFixed(3)), backgroundColor: '#16a34a', borderRadius: 6 }] },
      { plugins: { legend: { display: false }, tooltip: byteTip }, scales: { y: { beginAtZero: true, title: { display: true, text: 'GB' } } } });

    App.chart('c5', 'doughnut', { labels: d.types.map(t => t.category), datasets: [{ data: d.types.map(t => t.count), backgroundColor: PALETTE }] },
      { plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: c => c.label + ': ' + fmtNum(c.parsed) + ' files' } } } });

    App.chart('c6', 'bar', { labels: d.topGroups.map(g => g.name.length > 28 ? g.name.substring(0, 27) + '…' : g.name),
      datasets: [{ label: 'Recoverable (GB)', data: d.topGroups.map(g => +(g.recoverableBytes / GB).toFixed(3)), backgroundColor: '#7c3aed', borderRadius: 6 }] },
      { indexAxis: 'y', plugins: { legend: { display: false }, tooltip: { callbacks: { label: c => fmtBytes(d.topGroups[c.dataIndex].recoverableBytes) + ' · ' + d.topGroups[c.dataIndex].files + ' copies' } } },
        scales: { x: { beginAtZero: true, title: { display: true, text: 'GB' } } } });
  } catch (e) { App.toast(e.message, 'err'); }
});

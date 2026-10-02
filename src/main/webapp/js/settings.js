App.ready.then(async session => {
  const $ = id => document.getElementById(id);

  $('acctName').textContent = session.username;
  $('acctAvatar').textContent = session.username.charAt(0);
  $('settingsLogout').addEventListener('click', () => App.logout());
  const paintSeg = () => document.querySelectorAll('#themeSeg button').forEach(b => b.classList.toggle('on', b.dataset.theme === App.getTheme()));
  document.querySelectorAll('#themeSeg button').forEach(b => b.addEventListener('click', () => { App.setTheme(b.dataset.theme); paintSeg(); }));
  paintSeg();

  function fill(s) {
    $('defaultDir').value = s.defaultDir || '';
    $('excludedDirs').value = (s.excludedDirs || []).join('\n');
    $('minSize').value = Math.round(s.minSize / 1024);
    $('maxSize').value = Math.round(s.maxSize / 1048576);
    $('hashAlgorithm').value = s.hashAlgorithm;
    $('quarantineDir').value = s.quarantineDir;
    $('scanHidden').checked = s.scanHidden;
    $('scanSystem').checked = s.scanSystem;
    $('ignoreSystemDirs').checked = s.ignoreSystemDirs;
  }

  try { fill(await App.api('settings')); } catch (e) { App.toast(e.message, 'err'); }

  $('form').addEventListener('submit', async ev => {
    ev.preventDefault();
    const num = id => Math.max(0, Math.floor(Number($(id).value) || 0));
    const body = {
      defaultDir: $('defaultDir').value.trim(),
      excludedDirs: $('excludedDirs').value.split('\n').map(x => x.trim()).filter(Boolean),
      minSize: num('minSize') * 1024,
      maxSize: num('maxSize') * 1048576,
      hashAlgorithm: $('hashAlgorithm').value,
      quarantineDir: $('quarantineDir').value.trim(),
      scanHidden: $('scanHidden').checked,
      scanSystem: $('scanSystem').checked,
      ignoreSystemDirs: $('ignoreSystemDirs').checked
    };
    await App.busy($('save'), async () => {
      try {
        fill(await App.api('settings', { body }));
        App.toast('Settings saved.', 'ok');
      } catch (e) { App.toast(e.message, 'err'); }
    });
  });
});

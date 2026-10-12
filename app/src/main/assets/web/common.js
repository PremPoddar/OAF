// Shared by OAF's web engines: the file, the colours and reporting back, all through the app.
var theme = JSON.parse(OAF.theme());
document.documentElement.style.setProperty('--page', theme.page);
document.documentElement.style.setProperty('--ink', theme.ink);
document.documentElement.style.setProperty('--accent', theme.accent);
if (theme.dark) document.documentElement.classList.add('dark');

/** The document's bytes. */
function load() {
  return fetch('data:application/octet-stream;base64,' + OAF.data()).then(function (r) { return r.arrayBuffer(); });
}

function done(error) { OAF.done(error ? String(error.message || error) : ''); }

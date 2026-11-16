package com.deckvault.app.peer

/**
 * Responsive dark-matte HTML5 Web Companion served at `GET /` for Safari on iPhone
 * and any browser on LAN. Talks to the exact same DeckVault REST API endpoints.
 */
object DeckVaultWebPortal {
    val HTML: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>DeckVault Web</title>
<style>
  :root { --bg:#0a0a0d; --card:#14151a; --el:#1d1f27; --border:#2a2c36; --text:#ececf1; --muted:#9a9ca8; --accent:#aab4ce; --ok:#8fb8a0; --err:#cf8c92; }
  * { box-sizing:border-box; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; }
  body { margin:0; background:var(--bg); color:var(--text); min-height:100vh; }
  header { position:sticky; top:0; z-index:10; background:rgba(10,10,13,.94); backdrop-filter:blur(10px); border-bottom:1px solid var(--border); padding:12px 16px; display:flex; align-items:center; justify-content:space-between; gap:10px; }
  h1 { margin:0; font-size:17px; font-weight:600; color:var(--text); }
  .sub { font-size:12px; color:var(--muted); }
  .wrap { max-width:980px; margin:0 auto; padding:14px; }
  .card { background:var(--card); border:1px solid var(--border); border-radius:16px; padding:16px; margin-bottom:12px; }
  input, button, select { font:inherit; border-radius:10px; border:1px solid var(--border); padding:9px 12px; background:var(--el); color:var(--text); }
  button { cursor:pointer; font-weight:600; }
  button.primary { background:var(--accent); color:#111318; border-color:transparent; }
  .row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
  .pill { background:var(--card); border:1px solid var(--border); border-radius:10px; padding:7px 10px; font-size:12px; cursor:pointer; white-space:nowrap; }
  .pill.active { border-color:var(--accent); color:var(--accent); }
  .grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(160px,1fr)); gap:10px; margin-top:12px; }
  .item { background:var(--card); border:1px solid var(--border); border-radius:14px; overflow:hidden; display:flex; flex-direction:column; cursor:pointer; }
  .thumb { width:100%; aspect-ratio:16/10; object-fit:cover; background:#000; display:flex; align-items:center; justify-content:center; color:var(--muted); font-size:12px; }
  .meta { padding:9px 10px; }
  .name { font-size:13px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .info { font-size:11px; color:var(--muted); margin-top:3px; }
  .bar { height:4px; background:var(--el); border-radius:4px; overflow:hidden; margin-top:6px; }
  .bar > div { height:100%; background:var(--accent); }
  .modal { position:fixed; inset:0; background:rgba(0,0,0,.92); z-index:50; display:none; flex-direction:column; padding:14px; }
  .modal.open { display:flex; }
  .modal-body { flex:1; display:flex; align-items:center; justify-content:center; overflow:auto; }
  video, img, pre { max-width:100%; max-height:100%; border-radius:12px; }
  pre { background:var(--card); padding:14px; overflow:auto; width:100%; color:var(--text); font-size:12px; }
  .status { font-size:12px; color:var(--ok); margin-top:6px; }
</style>
</head>
<body>
<header>
  <div>
    <h1 id="devTitle">DeckVault Web</h1>
    <div class="sub" id="devSub">Connect from iPhone Safari or any browser</div>
  </div>
  <div class="row">
    <label class="pill" style="display:inline-flex;align-items:center;gap:6px;">
      <span>Upload from iPhone / Device</span>
      <input id="upInput" type="file" multiple style="display:none">
    </label>
    <button onclick="logout()" id="logoutBtn" style="display:none">Lock</button>
  </div>
</header>

<div class="wrap">
  <div id="pairBox" class="card">
    <div style="font-weight:600;margin-bottom:8px;">Enter Pairing PIN</div>
    <div class="row">
      <input id="pinInput" placeholder="6-digit PIN" inputmode="numeric" style="width:160px">
      <button class="primary" onclick="doPair()">Connect</button>
    </div>
    <div id="pairErr" style="color:var(--err);font-size:12px;margin-top:8px;"></div>
  </div>

  <div id="appBox" style="display:none">
    <div class="card">
      <div class="row" id="drivesRow"></div>
      <div class="row" id="quickRow" style="margin-top:8px;"></div>
      <div class="row" style="margin-top:10px;justify-content:space-between;">
        <div class="row" style="flex:1;min-width:180px;">
          <button onclick="goUp()">Up</button>
          <span class="sub" id="curPath" style="word-break:break-all;"></span>
        </div>
        <input id="qInput" placeholder="Filter..." oninput="debouncedLoad()" style="width:150px">
      </div>
      <div id="uploadStatus" class="status"></div>
    </div>

    <div class="row" id="foldersRow"></div>
    <div class="grid" id="filesGrid"></div>
  </div>
</div>

<div class="modal" id="viewer">
  <div class="row" style="justify-content:space-between;margin-bottom:10px;">
    <span id="viewerTitle" style="font-weight:600;"></span>
    <div class="row">
      <a id="dlLink" class="pill" download>Download / Save</a>
      <button onclick="closeViewer()">Close</button>
    </div>
  </div>
  <div class="modal-body" id="viewerBody"></div>
</div>

<script>
const storeKey = "dv_token_" + location.host;
let token = localStorage.getItem(storeKey) || "";
let currentPath = "";
let parentPath = "";
let timer = null;

function fmtBytes(b) {
  if (b < 0) return "";
  const u = ["B","KB","MB","GB","TB"]; let i = 0; let v = b;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return (i === 0 ? v : v.toFixed(1)) + " " + u[i];
}

async function api(url, opts={}) {
  const headers = Object.assign({}, opts.headers || {}, token ? {"X-DeckVault-Token": token} : {});
  const res = await fetch(url, Object.assign({}, opts, {headers}));
  if (res.status === 401) { logout(); throw new Error("Unauthorized"); }
  if (!res.ok) throw new Error(await res.text());
  return res;
}

async function doPair() {
  const pin = document.getElementById("pinInput").value.trim();
  const errEl = document.getElementById("pairErr");
  errEl.textContent = "";
  try {
    const r = await fetch("/api/pair", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body: JSON.stringify({pin, clientName: navigator.platform || "Safari Web"})
    });
    if (!r.ok) throw new Error("Invalid PIN");
    const d = await r.json();
    token = d.token;
    localStorage.setItem(storeKey, token);
    initApp();
  } catch (e) { errEl.textContent = e.message; }
}

function logout() {
  token = ""; localStorage.removeItem(storeKey);
  document.getElementById("pairBox").style.display = "block";
  document.getElementById("appBox").style.display = "none";
  document.getElementById("logoutBtn").style.display = "none";
}

async function initApp() {
  if (!token) return;
  try {
    const r = await (await api("/api/roots")).json();
    document.getElementById("devTitle").textContent = "DeckVault • " + r.deviceName;
    document.getElementById("devSub").textContent = (r.os || "") + " • Ready";
    document.getElementById("pairBox").style.display = "none";
    document.getElementById("appBox").style.display = "block";
    document.getElementById("logoutBtn").style.display = "inline-block";

    const dr = document.getElementById("drivesRow"); dr.innerHTML = "";
    (r.drives || []).forEach(d => {
      const pct = d.totalBytes > 0 ? Math.round((d.usedBytes / d.totalBytes) * 100) : 0;
      const el = document.createElement("div");
      el.className = "pill";
      el.innerHTML = "<b>" + d.name + "</b> • " + fmtBytes(d.freeBytes) + " free / " + fmtBytes(d.totalBytes) +
        '<div class="bar"><div style="width:' + pct + '%"></div></div>';
      el.onclick = () => loadDir(d.path);
      dr.appendChild(el);
    });

    const qr = document.getElementById("quickRow"); qr.innerHTML = "";
    (r.quickFolders || []).forEach(q => {
      const el = document.createElement("div");
      el.className = "pill"; el.textContent = q.name;
      el.onclick = () => loadDir(q.path);
      qr.appendChild(el);
    });

    const first = (r.quickFolders && r.quickFolders[0] && r.quickFolders[0].path) || (r.drives && r.drives[0] && r.drives[0].path) || "";
    if (first) loadDir(first);
  } catch (e) { console.error(e); }
}

function debouncedLoad() { clearTimeout(timer); timer = setTimeout(() => loadDir(currentPath), 250); }
function goUp() { if (parentPath && parentPath !== currentPath) loadDir(parentPath); }

async function loadDir(p) {
  currentPath = p;
  const q = document.getElementById("qInput").value.trim();
  const data = await (await api("/api/list?path=" + encodeURIComponent(p) + "&sort=size&order=desc&filter=all&q=" + encodeURIComponent(q))).json();
  currentPath = data.path; parentPath = data.parentPath || "";
  document.getElementById("curPath").textContent = currentPath + " (" + fmtBytes(data.totalSizeBytes) + ")";

  const fr = document.getElementById("foldersRow"); fr.innerHTML = "";
  (data.folders || []).slice(0, 30).forEach(f => {
    const el = document.createElement("div");
    el.className = "pill"; el.textContent = "📁 " + f.name + (f.sizeBytes >= 0 ? " (" + fmtBytes(f.sizeBytes) + ")" : "");
    el.onclick = () => loadDir(f.path);
    fr.appendChild(el);
  });

  const grid = document.getElementById("filesGrid"); grid.innerHTML = "";
  (data.files || []).forEach(f => {
    const card = document.createElement("div"); card.className = "item";
    const tUrl = f.mediaType === "video"
      ? "/api/video/thumbnail?path=" + encodeURIComponent(f.path) + "&w=400&token=" + encodeURIComponent(token)
      : (f.mediaType === "image" ? "/api/image?path=" + encodeURIComponent(f.path) + "&w=400&token=" + encodeURIComponent(token) : "");
    const thumbHtml = tUrl
      ? '<img class="thumb" loading="lazy" src="' + tUrl + '">'
      : '<div class="thumb">' + (f.extension || f.mediaType || "FILE").toUpperCase() + '</div>';
    card.innerHTML = thumbHtml + '<div class="meta"><div class="name"></div><div class="info">' + fmtBytes(f.sizeBytes) + '</div></div>';
    card.querySelector(".name").textContent = f.name;
    card.onclick = () => openFile(f);
    grid.appendChild(card);
  });
}

function openFile(f) {
  const modal = document.getElementById("viewer");
  const body = document.getElementById("viewerBody");
  const streamUrl = "/api/stream?path=" + encodeURIComponent(f.path) + "&token=" + encodeURIComponent(token);
  document.getElementById("viewerTitle").textContent = f.name;
  document.getElementById("dlLink").href = streamUrl;
  body.innerHTML = "";

  const ext = (f.extension || "").replace(".","").toLowerCase();
  if (f.mediaType === "video") {
    body.innerHTML = '<video controls autoplay playsinline src="' + streamUrl + '"></video>';
  } else if (f.mediaType === "image") {
    body.innerHTML = '<img src="' + streamUrl + '">';
  } else if (f.mediaType === "audio") {
    body.innerHTML = '<audio controls autoplay src="' + streamUrl + '"></audio>';
  } else if (ext === "pdf") {
    body.innerHTML = '<iframe src="' + streamUrl + '" style="width:100%;height:100%;border:0;border-radius:12px;"></iframe>';
  } else if (["txt","md","json","xml","csv","log","py","kt","js","html","css","ini","yaml","yml","sh","bat"].includes(ext)) {
    fetch(streamUrl).then(r => r.text()).then(t => {
      const pre = document.createElement("pre"); pre.textContent = t.slice(0, 250000); body.appendChild(pre);
    });
  } else {
    body.innerHTML = '<div class="card">Tap Download / Save above to open ' + f.name + ' (' + fmtBytes(f.sizeBytes) + ')</div>';
  }
  modal.classList.add("open");
}

function closeViewer() {
  document.getElementById("viewer").classList.remove("open");
  document.getElementById("viewerBody").innerHTML = "";
}

document.getElementById("upInput").addEventListener("change", async (ev) => {
  const files = Array.from(ev.target.files || []);
  if (!files.length || !currentPath) return;
  const st = document.getElementById("uploadStatus");
  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    st.textContent = "Uploading " + file.name + " (" + (i+1) + "/" + files.length + ")...";
    await api("/api/file/upload?dir=" + encodeURIComponent(currentPath) + "&name=" + encodeURIComponent(file.name), {
      method:"POST", body: file
    });
  }
  st.textContent = "Uploaded " + files.length + " file(s) successfully!";
  ev.target.value = "";
  loadDir(currentPath);
});

initApp();
</script>
</body>
</html>
""".trimIndent()
}

"""Embedded dark-matte web companion UI for Safari / iPhone / any browser."""

WEB_PORTAL_HTML = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,viewport-fit=cover"/>
<title>DeckVault Web Companion</title>
<style>
  :root { --bg:#0b0c10; --panel:#14151b; --card:#1b1d25; --border:#2b2e3a; --text:#ececf1; --muted:#9a9ca9; --accent:#8eb8ff; }
  *{box-sizing:border-box}
  body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;background:var(--bg);color:var(--text)}
  header{position:sticky;top:0;z-index:10;background:var(--panel);border-bottom:1px solid var(--border);padding:12px 16px;display:flex;align-items:center;gap:10px;flex-wrap:wrap}
  h1{font-size:17px;margin:0;flex:1}
  button,input,select{font:inherit;color:var(--text);background:var(--card);border:1px solid var(--border);border-radius:10px;padding:8px 12px}
  button.primary{background:var(--accent);color:#09101d;border-color:transparent;font-weight:600}
  main{max-width:1100px;margin:0 auto;padding:16px}
  .drives{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:10px;margin-bottom:16px}
  .drive{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:12px;cursor:pointer}
  .bar{height:6px;background:#262936;border-radius:99px;overflow:hidden;margin:6px 0}
  .bar>div{height:100%;background:var(--accent)}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(165px,1fr));gap:10px}
  .item{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:10px;cursor:pointer;display:flex;flex-direction:column;gap:6px}
  .thumb{width:100%;aspect-ratio:16/10;object-fit:cover;border-radius:9px;background:#101118;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:12px}
  .name{font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .meta{font-size:11px;color:var(--muted)}
  .modal{position:fixed;inset:0;background:rgba(0,0,0,.92);display:none;flex-direction:column;z-index:50;padding:16px}
  .modal.open{display:flex}
  .modal-body{flex:1;display:flex;align-items:center;justify-content:center;overflow:auto}
  video,audio,iframe,img.full{max-width:100%;max-height:82vh;border-radius:12px}
  pre.full{width:100%;max-width:960px;max-height:80vh;overflow:auto;background:var(--panel);padding:16px;border-radius:12px;font-size:12px;white-space:pre-wrap}
  .login-box{max-width:360px;margin:70px auto;background:var(--panel);border:1px solid var(--border);border-radius:18px;padding:22px;display:flex;flex-direction:column;gap:12px}
</style>
</head>
<body>
<header>
  <h1 id="title">DeckVault</h1>
  <button id="upBtn" style="display:none" onclick="goUp()">Up</button>
  <label id="uploadLbl" style="display:none">
    <input type="file" id="upInput" multiple style="display:none" onchange="uploadFiles(this.files)"/>
    <span style="display:inline-block;padding:8px 12px;background:var(--accent);color:#09101d;border-radius:10px;font-weight:600;cursor:pointer">+ Upload Photos / Files</span>
  </label>
  <button id="outBtn" style="display:none" onclick="logout()">Sign Out</button>
</header>
<main id="app"></main>
<div class="modal" id="viewer">
  <div style="display:flex;align-items:center;gap:10px;margin-bottom:10px">
    <strong id="vTitle" style="flex:1"></strong>
    <a id="vDl" download><button>Download</button></a>
    <button onclick="closeViewer()">Close</button>
  </div>
  <div class="modal-body" id="vBody"></div>
</div>
<script>
const storeKey="dv_web_token";
let token=localStorage.getItem(storeKey)||"";
let curPath="",parentPath="";
function fmt(b){if(b==null||b<0)return"";const u=["B","KB","MB","GB","TB"];let i=0;while(b>=1024&&i<u.length-1){b/=1024;i++}return(b>=10?b.toFixed(1):b.toFixed(2))+" "+u[i]}
async function api(p,opt={}){const h=Object.assign({},opt.headers||{});if(token)h["X-DeckVault-Token"]=token;const r=await fetch(p,Object.assign({},opt,{headers:h}));if(r.status===401){token="";localStorage.removeItem(storeKey);renderLogin();throw new Error("PIN required")}if(!r.ok)throw new Error(await r.text());return r.json()}
function renderLogin(msg=""){
  document.getElementById("upBtn").style.display="none";
  document.getElementById("uploadLbl").style.display="none";
  document.getElementById("outBtn").style.display="none";
  document.getElementById("app").innerHTML=`<div class="login-box"><h2 style="margin:0">Connect to Device</h2><div class="meta">Enter the 6-digit PIN shown on DeckVault (PC, Z Fold, or Tab S8 Ultra).</div><input id="pin" inputmode="numeric" maxlength="6" placeholder="6-digit PIN" /><button class="primary" onclick="doPair()">Unlock</button><a href="/DeckVault.apk" style="color:var(--accent);font-size:13px;text-align:center;text-decoration:none;margin-top:4px">Download Android APK (v1.2.0)</a>${msg?`<div style="color:#cf8c92;font-size:13px">${msg}</div>`:""}</div>`;
}
async function doPair(){
  const pin=document.getElementById("pin").value.trim();
  try{
    const res=await fetch("/api/pair",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({pin,clientName:"iPhone / Web Browser"})});
    if(!res.ok){renderLogin("Invalid PIN");return}
    const d=await res.json();
    token=d.token;localStorage.setItem(storeKey,token);loadRoots();
  }catch(e){renderLogin(e.message)}
}
function logout(){token="";localStorage.removeItem(storeKey);renderLogin()}
async function loadRoots(){
  try{
    const d=await api("/api/roots");
    document.getElementById("title").textContent="DeckVault • "+d.deviceName;
    document.getElementById("uploadLbl").style.display="";
    document.getElementById("outBtn").style.display="";
    const drivesHtml=(d.drives||[]).map(dr=>{
      const total=Math.max(1,dr.totalBytes||1),used=Math.max(0,total-(dr.freeBytes||0)),pct=Math.min(100,Math.round(used*100/total));
      return `<div class="drive" onclick='openDir(${JSON.stringify(dr.path)})'><div class="name">${dr.name}</div><div class="meta">${fmt(dr.freeBytes)} free / ${fmt(dr.totalBytes)}</div><div class="bar"><div style="width:${pct}%"></div></div></div>`;
    }).join("");
    const quickHtml=(d.quickFolders||[]).map(q=>`<button onclick='openDir(${JSON.stringify(q.path)})'>${q.name}</button>`).join(" ");
    window._rootsHtml=`<div class="drives">${drivesHtml}</div><div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">${quickHtml}</div>`;
    const first=(d.quickFolders&&d.quickFolders[0])||(d.drives&&d.drives[0]);
    if(first)await openDir(first.path);else document.getElementById("app").innerHTML=window._rootsHtml;
  }catch(e){if(e.message!=="PIN required")renderLogin(e.message)}
}
async function openDir(path){
  curPath=path;
  const d=await api("/api/list?path="+encodeURIComponent(path)+"&sort=size&order=desc&filter=all");
  parentPath=d.parentPath||"";
  document.getElementById("upBtn").style.display=parentPath?"":"none";
  const folders=(d.folders||[]).map(f=>`<div class="item" onclick='openDir(${JSON.stringify(f.path)})'><div class="thumb">Folder</div><div class="name">${f.name}</div><div class="meta">${fmt(f.sizeBytes)}</div></div>`).join("");
  const files=(d.files||[]).map(f=>{
    let th=`<div class="thumb">${(f.extension||f.ext||"file").toUpperCase()}</div>`;
    if(f.mediaType==="video")th=`<img class="thumb" loading="lazy" src="/api/video/thumbnail?path=${encodeURIComponent(f.path)}&w=400&token=${encodeURIComponent(token)}"/>`;
    else if(f.mediaType==="image")th=`<img class="thumb" loading="lazy" src="/api/image?path=${encodeURIComponent(f.path)}&w=400&token=${encodeURIComponent(token)}"/>`;
    return `<div class="item" onclick='viewFile(${JSON.stringify(f)})'>${th}<div class="name">${f.name}</div><div class="meta">${fmt(f.sizeBytes)}</div></div>`;
  }).join("");
  document.getElementById("app").innerHTML=(window._rootsHtml||"")+`<div class="meta" style="margin-bottom:10px">${d.path} • ${fmt(d.totalSizeBytes)}</div><div class="grid">${folders}${files}</div>`;
}
function goUp(){if(parentPath)openDir(parentPath)}
async function uploadFiles(files){
  if(!files||!files.length||!curPath)return;
  for(const f of files){
    await fetch("/api/file/upload?dir="+encodeURIComponent(curPath)+"&name="+encodeURIComponent(f.name)+"&conflict=rename",{method:"POST",headers:{"X-DeckVault-Token":token},body:f});
  }
  openDir(curPath);
}
async function viewFile(f){
  const url="/api/stream?path="+encodeURIComponent(f.path)+"&token="+encodeURIComponent(token);
  document.getElementById("vTitle").textContent=f.name;
  const dl=document.getElementById("vDl");dl.href=url;dl.download=f.name;
  const b=document.getElementById("vBody");
  const ext=("."+(f.extension||f.ext||"")).replace(/^\.\./,".").toLowerCase();
  if(f.mediaType==="video")b.innerHTML=`<video src="${url}" controls playsinline autoplay></video>`;
  else if(f.mediaType==="image")b.innerHTML=`<img class="full" src="/api/image?path=${encodeURIComponent(f.path)}&token=${encodeURIComponent(token)}"/>`;
  else if(f.mediaType==="audio")b.innerHTML=`<audio src="${url}" controls autoplay></audio>`;
  else if(ext===".pdf")b.innerHTML=`<iframe src="${url}" style="width:100%;height:80vh;border:0"></iframe>`;
  else if([".txt",".md",".json",".xml",".csv",".log",".py",".kt",".swift",".js",".html",".css"].includes(ext)){
    const r=await fetch(url);const txt=(await r.text()).slice(0,250000);
    const p=document.createElement("pre");p.className="full";p.textContent=txt;b.innerHTML="";b.appendChild(p);
  }else{
    b.innerHTML=`<div class="meta">Ready to download ${f.name} (${fmt(f.sizeBytes)})</div>`;
  }
  document.getElementById("viewer").classList.add("open");
}
function closeViewer(){document.getElementById("vBody").innerHTML="";document.getElementById("viewer").classList.remove("open")}
if(token)loadRoots();else renderLogin();
</script>
</body>
</html>"""

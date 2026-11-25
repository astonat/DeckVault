"""DeckVault Standalone Pop-Out Video Player UI (Minimalist MPC-HC Replacement)."""

POPOUT_PLAYER_HTML = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>DeckVault Player</title>
<style>
  :root{
    --bg:#000000; --panel:rgba(12,13,18,.92); --card:#14161e; --elev:#1c1f29;
    --border:rgba(255,255,255,.11); --text:#f1f2f6; --muted:#9aa0b2;
    --primary:#60a5fa; --on-primary:#051c3d; --primary-container:rgba(96,165,250,.22);
    --danger:#f28b96; --success:#7ad6a2;
  }
  *{box-sizing:border-box;user-select:none}
  html,body{height:100%;width:100%;margin:0;padding:0;background:#000;color:var(--text);font-family:"Segoe UI Variable","Segoe UI",Inter,-apple-system,sans-serif;overflow:hidden}
  button,input,select{font:inherit;color:var(--text)}

  /* Frameless Edge Resize Grips */
  .win-edge{position:fixed;z-index:220}
  .win-n{top:0;left:6px;right:6px;height:5px;cursor:ns-resize}
  .win-s{bottom:0;left:6px;right:6px;height:5px;cursor:ns-resize}
  .win-w{top:6px;bottom:6px;left:0;width:5px;cursor:ew-resize}
  .win-e{top:6px;bottom:6px;right:0;width:5px;cursor:ew-resize}
  .win-nw{top:0;left:0;width:8px;height:8px;cursor:nwse-resize}
  .win-ne{top:0;right:0;width:8px;height:8px;cursor:nesw-resize}
  .win-sw{bottom:0;left:0;width:8px;height:8px;cursor:nesw-resize}
  .win-se{bottom:0;right:0;width:8px;height:8px;cursor:nwse-resize}

  /* Video Stage */
  .stage-wrap{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;background:#000;overflow:hidden}
  .stage-wrap.hide-cursor{cursor:none}
  .video-box{position:relative;overflow:hidden;flex-shrink:0;background:#000}
  .video-stage{position:absolute;left:0;top:0}
  .video-stage video{position:absolute;left:50%;top:50%;display:block;max-width:none;max-height:none;object-fit:fill}

  /* Center/Corner OSD Feedback (MPC-HC style) */
  .osd-pill{position:fixed;top:46px;left:14px;background:rgba(8,9,13,.84);border:1px solid var(--border);border-radius:9px;padding:5px 10px;font-size:12px;font-weight:700;color:var(--text);pointer-events:none;opacity:0;transform:translateY(-4px);transition:opacity .16s,transform .16s;z-index:160;backdrop-filter:blur(8px)}
  .osd-pill.show{opacity:1;transform:translateY(0)}

  /* Loading / Buffering Bar */
  .buf-bar{position:fixed;top:0;left:0;right:0;height:2px;background:transparent;overflow:hidden;z-index:190;pointer-events:none}
  .buf-fill{height:100%;width:32%;background:var(--primary);position:absolute;left:-34%;opacity:0}
  .buf-bar.active .buf-fill{opacity:1;animation:dvBuf 1.05s infinite ease-in-out}
  @keyframes dvBuf{0%{left:-34%}100%{left:102%}}

  /* Slim Auto-Hiding Top Bar */
  .top-bar{position:fixed;top:0;left:0;right:0;height:34px;padding:0 6px 0 10px;background:linear-gradient(180deg,rgba(0,0,0,.86) 0%,rgba(0,0,0,.52) 72%,transparent 100%);display:flex;align-items:center;gap:6px;z-index:140;transition:opacity .22s,transform .22s}
  .top-bar.hidden{opacity:0;transform:translateY(-8px);pointer-events:none}
  .drag-strip{flex:1;min-width:0;height:100%;display:flex;align-items:center;gap:7px;-webkit-app-region:drag}
  .brand-dot{width:7px;height:7px;border-radius:50%;background:var(--primary);flex-shrink:0}
  .vid-title{font-size:12px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-shadow:0 1px 4px rgba(0,0,0,.9)}
  .meta-tag{font-size:10px;color:var(--muted);background:rgba(0,0,0,.58);border:1px solid var(--border);border-radius:6px;padding:1px 6px;white-space:nowrap;flex-shrink:0}

  .ico-btn{width:26px;height:24px;border-radius:7px;border:1px solid transparent;background:transparent;color:var(--text);display:inline-flex;align-items:center;justify-content:center;cursor:pointer;font-size:12px;padding:0;transition:background .12s,border-color .12s,color .12s;-webkit-app-region:no-drag}
  .ico-btn:hover{background:rgba(255,255,255,.12);border-color:var(--border)}
  .ico-btn.active{background:var(--primary-container);border-color:var(--primary);color:var(--primary)}
  .ico-btn.close:hover{background:#b91c1c;border-color:#dc2626;color:#fff}

  /* Slim Auto-Hiding Bottom Transport Bar (MPC-HC Minimalist Style) */
  .bot-bar{position:fixed;left:0;right:0;bottom:0;padding:16px 10px 7px;background:linear-gradient(0deg,rgba(0,0,0,.90) 0%,rgba(0,0,0,.64) 68%,transparent 100%);display:flex;flex-direction:column;gap:4px;z-index:140;transition:opacity .22s,transform .22s}
  .bot-bar.hidden{opacity:0;transform:translateY(8px);pointer-events:none}

  .tl-wrap{position:relative;height:16px;display:flex;align-items:center;cursor:pointer}
  .tl-track{width:100%;height:4px;background:rgba(255,255,255,.22);border-radius:99px;position:relative;overflow:hidden;transition:height .12s}
  .tl-wrap:hover .tl-track{height:6px}
  .tl-buf{position:absolute;top:0;bottom:0;left:0;background:rgba(255,255,255,.28)}
  .tl-ab{position:absolute;top:0;bottom:0;background:rgba(122,214,162,.42);display:none}
  .tl-fill{position:absolute;top:0;bottom:0;left:0;background:var(--primary);border-radius:99px}
  .tl-tip{position:absolute;bottom:20px;transform:translateX(-50%);background:var(--panel);border:1px solid var(--border);border-radius:8px;padding:3px;display:none;flex-direction:column;align-items:center;gap:2px;pointer-events:none;z-index:20;box-shadow:0 8px 22px rgba(0,0,0,.85)}
  .tl-tip-img{width:136px;height:76px;border-radius:6px;background-repeat:no-repeat;background-color:#000}
  .tl-tip-txt{font-size:10px;font-weight:700;font-family:Consolas,monospace}

  .ctrl-row{display:flex;align-items:center;gap:4px;min-height:26px}
  .t-btn{height:24px;min-width:24px;padding:0 6px;border-radius:7px;border:1px solid transparent;background:transparent;color:var(--text);font-size:11px;font-weight:600;display:inline-flex;align-items:center;justify-content:center;gap:4px;cursor:pointer;white-space:nowrap}
  .t-btn:hover:not(:disabled){background:rgba(255,255,255,.12);border-color:var(--border)}
  .t-btn:disabled{opacity:.35;cursor:default}
  .t-btn.play{background:var(--primary);color:var(--on-primary);min-width:30px;padding:0 9px}
  .t-btn.play:hover{filter:brightness(1.08)}
  .t-btn.active{background:var(--primary-container);border-color:var(--primary);color:var(--primary)}
  .time-lbl{font-size:11px;font-family:Consolas,monospace;color:var(--text);padding:0 5px;cursor:pointer;white-space:nowrap;opacity:.92}
  .time-lbl:hover{color:var(--primary)}
  .vol-wrap{display:inline-flex;align-items:center;gap:4px;padding:0 2px}
  .vol-range{width:64px;accent-color:var(--primary);cursor:pointer;height:4px}
  .vol-pct{font-size:10px;color:var(--muted);min-width:28px;text-align:right;font-family:Consolas,monospace}

  /* Compact Popover Options Menu */
  .pop-menu{position:fixed;top:38px;right:10px;width:236px;max-height:calc(100vh - 88px);overflow-y:auto;background:var(--panel);border:1px solid var(--border);border-radius:13px;padding:9px;display:none;flex-direction:column;gap:8px;z-index:180;box-shadow:0 16px 40px rgba(0,0,0,.88);backdrop-filter:blur(14px)}
  .pop-menu.open{display:flex}
  .pop-sec{display:flex;flex-direction:column;gap:5px;padding-bottom:7px;border-bottom:1px solid rgba(255,255,255,.07)}
  .pop-sec:last-child{border-bottom:0;padding-bottom:0}
  .pop-lbl{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:var(--muted)}
  .chip-row{display:flex;flex-wrap:wrap;gap:4px}
  .m-chip{padding:3px 7px;border-radius:7px;border:1px solid var(--border);background:rgba(255,255,255,.04);color:var(--muted);font-size:11px;font-weight:600;cursor:pointer}
  .m-chip:hover{color:var(--text);border-color:var(--primary)}
  .m-chip.active{background:var(--primary-container);border-color:var(--primary);color:var(--primary)}
  .menu-act{width:100%;padding:6px 8px;border-radius:8px;border:0;background:transparent;color:var(--text);font-size:11px;font-weight:600;text-align:left;cursor:pointer;display:flex;align-items:center;justify-content:space-between;gap:8px}
  .menu-act:hover{background:rgba(255,255,255,.10)}
  .menu-act span.k{font-size:10px;color:var(--muted);font-family:Consolas,monospace}

  /* Right-Click Context Menu */
  .ctx-menu{position:fixed;min-width:196px;background:var(--panel);border:1px solid var(--border);border-radius:12px;padding:5px;display:none;flex-direction:column;gap:2px;z-index:210;box-shadow:0 16px 36px rgba(0,0,0,.9);backdrop-filter:blur(14px)}
  .ctx-menu.open{display:flex}
  .ctx-sep{height:1px;background:rgba(255,255,255,.08);margin:3px 4px}

  /* Empty / Drop-to-Play Screen */
  .empty-drop{position:fixed;inset:0;display:none;flex-direction:column;align-items:center;justify-content:center;gap:12px;background:#050609;z-index:120;padding:24px;text-align:center}
  .empty-drop.show{display:flex}
  .drop-btn{padding:8px 16px;border-radius:10px;border:1px solid var(--primary);background:var(--primary-container);color:var(--primary);font-size:12px;font-weight:700;cursor:pointer}
  .drop-btn:hover{background:var(--primary);color:var(--on-primary)}
</style>
</head>
<body>

<div id="winEdges" style="display:none">
  <div class="win-edge win-n" data-edge="n"></div><div class="win-edge win-s" data-edge="s"></div>
  <div class="win-edge win-w" data-edge="w"></div><div class="win-edge win-e" data-edge="e"></div>
  <div class="win-edge win-nw" data-edge="nw"></div><div class="win-edge win-ne" data-edge="ne"></div>
  <div class="win-edge win-sw" data-edge="sw"></div><div class="win-edge win-se" data-edge="se"></div>
</div>

<div class="buf-bar" id="bufBar"><div class="buf-fill"></div></div>
<div class="osd-pill" id="osdPill"></div>

<div class="stage-wrap" id="stageWrap">
  <div class="video-box" id="videoBox">
    <div class="video-stage" id="videoStage">
      <video id="vidEl" playsinline crossorigin="anonymous"></video>
    </div>
  </div>
</div>

<div class="empty-drop" id="emptyDrop">
  <div style="font-size:28px">▶</div>
  <div style="font-size:15px;font-weight:700">DeckVault Pop-Out Player</div>
  <div style="font-size:12px;color:var(--muted);max-width:340px">Open a video file or set DeckVault as your Windows default video player to replace MPC-HC.</div>
  <div style="display:flex;gap:8px;flex-wrap:wrap;justify-content:center">
    <button class="drop-btn" onclick="openFileDialog()">Open Video File (Ctrl+O)</button>
    <button class="m-chip" style="padding:8px 12px" onclick="registerDefaultPlayer()">Set as Windows Video Player</button>
  </div>
</div>

<!-- Slim Auto-Hiding Top Titlebar -->
<div class="top-bar" id="topBar">
  <div class="drag-strip" id="dragRegion">
    <span class="brand-dot"></span>
    <span class="vid-title" id="vidTitle">DeckVault Player</span>
    <span class="meta-tag" id="metaTag" style="display:none">HD</span>
  </div>
  <button class="ico-btn" id="pinBtn" onclick="toggleOnTop()" title="Always on Top (Ctrl+T)">📌</button>
  <button class="ico-btn" id="minChromeBtn" onclick="toggleMinimalMode()" title="Minimal Hover-Only Controls (1)">▢</button>
  <button class="ico-btn" id="optBtn" onclick="toggleOptionsMenu(event)" title="Player Options">⚙</button>
  <button class="ico-btn" onclick="winAction('minimize')" title="Minimize">─</button>
  <button class="ico-btn" onclick="toggleFullscreen()" title="Fullscreen (F)">□</button>
  <button class="ico-btn close" onclick="winAction('close')" title="Close (Esc)">✕</button>
</div>

<!-- Compact Options Popover Menu -->
<div class="pop-menu" id="optMenu" onclick="event.stopPropagation()">
  <div class="pop-sec">
    <div class="pop-lbl">Playback Speed</div>
    <div class="chip-row" id="speedChips"></div>
  </div>
  <div class="pop-sec">
    <div class="pop-lbl">Scale &amp; Aspect</div>
    <div class="chip-row" id="fitChips"></div>
    <div class="chip-row" id="aspectChips"></div>
  </div>
  <div class="pop-sec">
    <div class="pop-lbl">Transform &amp; Loop</div>
    <div class="chip-row">
      <button class="m-chip" id="rotChip" onclick="cycleRotate()">Rot 0°</button>
      <button class="m-chip" id="flipHChip" onclick="toggleFlip('h')">Flip H</button>
      <button class="m-chip" id="flipVChip" onclick="toggleFlip('v')">Flip V</button>
      <button class="m-chip" id="loopFileChip" onclick="toggleLoopFile()">Loop: Off</button>
    </div>
    <div class="chip-row">
      <button class="m-chip" id="abAChip" onclick="markAbPoint('a')">Set A</button>
      <button class="m-chip" id="abBChip" onclick="markAbPoint('b')">Set B</button>
      <button class="m-chip" onclick="clearAbLoop()">Clear A-B</button>
    </div>
  </div>
  <div class="pop-sec">
    <button class="menu-act" onclick="closeMenus();saveSnapshot()"><span>📷 Save Frame Snapshot</span><span class="k">F5</span></button>
    <button class="menu-act" onclick="closeMenus();openFileDialog()"><span>📂 Open Video File...</span><span class="k">Ctrl+O</span></button>
    <button class="menu-act" onclick="closeMenus();revealCurrent()"><span>🗂 Show in Explorer</span></button>
    <button class="menu-act" onclick="closeMenus();openInFullDeckVault()"><span>✂ Open in DeckVault Studio</span></button>
    <button class="menu-act" onclick="closeMenus();registerDefaultPlayer()"><span>⭐ Set as Default Video Player</span></button>
  </div>
</div>

<!-- Compact Right-Click Context Menu -->
<div class="ctx-menu" id="ctxMenu" onclick="event.stopPropagation()">
  <button class="menu-act" onclick="closeMenus();togglePlay()"><span>⏯ Play / Pause</span><span class="k">Space</span></button>
  <button class="menu-act" onclick="closeMenus();toggleFullscreen()"><span>⛶ Fullscreen</span><span class="k">F</span></button>
  <button class="menu-act" onclick="closeMenus();toggleOnTop()"><span>📌 Always on Top</span><span class="k">Ctrl+T</span></button>
  <button class="menu-act" onclick="closeMenus();cycleRotate()"><span>↻ Rotate 90°</span><span class="k">R</span></button>
  <button class="menu-act" onclick="closeMenus();toggleLoopFile()"><span>🔁 Toggle Loop</span><span class="k">L</span></button>
  <div class="ctx-sep"></div>
  <button class="menu-act" onclick="closeMenus();stepSibling(-1)"><span>⏮ Previous Video</span><span class="k">PgUp</span></button>
  <button class="menu-act" onclick="closeMenus();stepSibling(1)"><span>⏭ Next Video</span><span class="k">PgDn</span></button>
  <button class="menu-act" onclick="closeMenus();saveSnapshot()"><span>📷 Save Snapshot</span><span class="k">F5</span></button>
  <div class="ctx-sep"></div>
  <button class="menu-act" onclick="closeMenus();openFileDialog()"><span>📂 Open File...</span><span class="k">Ctrl+O</span></button>
  <button class="menu-act" onclick="closeMenus();revealCurrent()"><span>🗂 Show in Explorer</span></button>
  <button class="menu-act" onclick="closeMenus();openInFullDeckVault()"><span>✂ Open in DeckVault Studio</span></button>
  <button class="menu-act" onclick="closeMenus();registerDefaultPlayer()"><span>⭐ Set as Default Video Player</span></button>
  <div class="ctx-sep"></div>
  <button class="menu-act" onclick="winAction('close')" style="color:var(--danger)"><span>✕ Close Player</span><span class="k">Esc</span></button>
</div>

<!-- Slim Auto-Hiding Bottom Transport Bar -->
<div class="bot-bar" id="botBar">
  <div class="tl-wrap" id="tlWrap">
    <div class="tl-tip" id="tlTip">
      <div class="tl-tip-img" id="tlTipImg"></div>
      <span class="tl-tip-txt" id="tlTipTxt">0:00</span>
    </div>
    <div class="tl-track">
      <div class="tl-buf" id="tlBuf"></div>
      <div class="tl-ab" id="tlAb"></div>
      <div class="tl-fill" id="tlFill"></div>
    </div>
  </div>
  <div class="ctrl-row">
    <button class="t-btn" id="prevBtn" onclick="stepSibling(-1)" title="Previous Video in Folder (PgUp)">⏮</button>
    <button class="t-btn" onclick="nudgeFrame(-1)" title="Previous Frame (,)">-1f</button>
    <button class="t-btn play" id="playBtn" onclick="togglePlay()" title="Play / Pause (Space)">▶</button>
    <button class="t-btn" onclick="nudgeFrame(1)" title="Next Frame (.)">+1f</button>
    <button class="t-btn" id="nextBtn" onclick="stepSibling(1)" title="Next Video in Folder (PgDn)">⏭</button>
    <span class="time-lbl" id="timeLbl" onclick="toggleRemainingTime()" title="Click to toggle remaining time">0:00 / 0:00</span>
    <span style="flex:1"></span>
    <button class="t-btn" id="speedBadge" onclick="cycleSpeedQuick()" title="Playback Speed ([ / ] or click)">1.0x</button>
    <div class="vol-wrap">
      <button class="t-btn" id="muteBtn" onclick="toggleMute()" title="Mute (M)">🔊</button>
      <input class="vol-range" id="volRange" type="range" min="0" max="200" step="5" value="100" oninput="setVolumePct(Number(this.value))" title="Volume (up to 200% boost)"/>
      <span class="vol-pct" id="volPct">100%</span>
    </div>
    <button class="t-btn" onclick="toggleFullscreen()" title="Fullscreen (F)">⛶</button>
  </div>
</div>

<script>
const st = {
  token: "",
  localHostId: "",
  hostId: "",
  path: "",
  file: null,
  siblings: [],
  siblingIdx: -1,
  durationSec: 0,
  isTranscode: false,
  transcodeOffsetSec: 0,
  rotate: 0,
  flipH: false,
  flipV: false,
  fit: "contain",
  aspect: "auto",
  speed: 1.0,
  volPct: 100,
  loopFile: false,
  abA: null,
  abB: null,
  showRemaining: false,
  onTop: false,
  minimal: false,
  isFullscreen: false,
  audioCtx: null,
  gainNode: null,
  hideTimer: null,
  osdTimer: null,
};

const PALETTE_ACCENTS = {
  cobalt:"#60a5fa", emerald:"#34d399", amethyst:"#c084fc", crimson:"#fb7185",
  amber:"#fbbf24", "cyber-teal":"#2dd4bf", slate:"#b9c3de", sage:"#a8c2b2",
  clay:"#d1b3a7", "plain-black":"#e2e2e7"
};

function applySavedTheme(){
  try{
    const raw = JSON.parse(localStorage.getItem("dv_prefs") || "{}");
    const acc = PALETTE_ACCENTS[raw.palette] || "#60a5fa";
    document.documentElement.style.setProperty("--primary", acc);
    document.documentElement.style.setProperty("--primary-container", acc + "33");
  }catch{}
}

function showOsd(msg){
  const el = document.getElementById("osdPill");
  el.textContent = msg;
  el.classList.add("show");
  clearTimeout(st.osdTimer);
  st.osdTimer = setTimeout(()=>el.classList.remove("show"), 1350);
}

function fmtTime(sec){
  if(!isFinite(sec) || sec < 0) sec = 0;
  const s = Math.floor(sec), h = Math.floor(s/3600), m = Math.floor((s%3600)/60), sc = s%60;
  return h > 0 ? `${h}:${String(m).padStart(2,"0")}:${String(sc).padStart(2,"0")}` : `${m}:${String(sc).padStart(2,"0")}`;
}
function fmtRes(w, h){
  if(!w || !h) return "";
  const m = Math.min(w, h), x = Math.max(w, h);
  if(x >= 3800 || m >= 2100) return "4K";
  if(x >= 2500 || m >= 1400) return "1440p";
  if(x >= 1900 || m >= 1050) return "1080p";
  if(x >= 1250 || m >= 700) return "720p";
  return m + "p";
}

function hostMediaUrl(hostId, route, qsObj){
  const q = new URLSearchParams(Object.assign({}, qsObj, {token: st.token})).toString();
  if(!hostId || hostId === st.localHostId) return `${route}?${q}`;
  return `/api/fleet/proxy?hostId=${encodeURIComponent(hostId)}&route=${encodeURIComponent(route + "?" + q)}&token=${encodeURIComponent(st.token)}`;
}
async function apiFetch(url, opts={}){
  const headers = Object.assign({}, opts.headers || {});
  if(st.token) headers["X-DeckVault-Token"] = st.token;
  const res = await fetch(url, Object.assign({}, opts, {headers}));
  if(!res.ok) throw new Error(await res.text() || `HTTP ${res.status}`);
  return res.json();
}

function setBuffering(on){
  document.getElementById("bufBar").classList.toggle("active", !!on);
}

function ensureAudioBoost(v){
  try{
    if(!st.audioCtx){
      const AC = window.AudioContext || window.webkitAudioContext;
      st.audioCtx = new AC();
      const src = st.audioCtx.createMediaElementSource(v);
      st.gainNode = st.audioCtx.createGain();
      src.connect(st.gainNode);
      st.gainNode.connect(st.audioCtx.destination);
    }
    if(st.audioCtx.state === "suspended") st.audioCtx.resume();
  }catch{}
}

function currentAbsSec(){
  return (st.isTranscode ? st.transcodeOffsetSec : 0) + (document.getElementById("vidEl").currentTime || 0);
}

function needsLiveTranscode(f){
  const vc = (f?.meta?.videoCodec || "").toLowerCase();
  const ext = ("." + (f?.extension || "")).toLowerCase();
  const fps = f?.meta?.fps || 0;
  return ["hevc","h265","hev1","hvc1","mpeg4","msmpeg4v3","wmv3","vc1","prores","mpeg2video"].includes(vc)
    || [".mkv",".avi",".wmv",".flv",".mts",".m2ts",".ts",".vob",".mpg",".mpeg"].includes(ext)
    || fps > 90;
}

function startTranscodeStream(startSec=0){
  if(!st.path) return;
  const v = document.getElementById("vidEl");
  st.isTranscode = true;
  st.transcodeOffsetSec = Math.max(0, startSec);
  v.src = hostMediaUrl(st.hostId, "/api/stream/playable", {path: st.path, start: st.transcodeOffsetSec.toFixed(2), v: Date.now()});
  v.playbackRate = st.speed;
  const mt = document.getElementById("metaTag");
  mt.style.display = "";
  mt.textContent = "LIVE H.264";
  v.play().catch(()=>{});
}

function seekTo(sec){
  const d = Math.max(0.1, st.durationSec || 1);
  const clamped = Math.max(0, Math.min(d, sec));
  if(st.isTranscode){
    startTranscodeStream(clamped);
  }else{
    document.getElementById("vidEl").currentTime = clamped;
  }
  updateHud();
}
function seekRel(dt){
  seekTo(currentAbsSec() + dt);
  showOsd(`${dt >= 0 ? "+" : ""}${dt}s (${fmtTime(currentAbsSec())})`);
}
function nudgeFrame(dir){
  const v = document.getElementById("vidEl");
  v.pause();
  const fps = st.file?.meta?.fps || 30;
  seekTo(currentAbsSec() + (dir / fps));
  showOsd(dir > 0 ? "+1 frame" : "-1 frame");
}

function layoutVideo(){
  const wrap = document.getElementById("stageWrap"), box = document.getElementById("videoBox"), stage = document.getElementById("videoStage"), v = document.getElementById("vidEl");
  if(!wrap || !box || !stage || !v) return;
  const wrapW = Math.max(40, wrap.clientWidth), wrapH = Math.max(40, wrap.clientHeight);
  const natW = v.videoWidth || st.file?.meta?.width || 16;
  const natH = v.videoHeight || st.file?.meta?.height || 9;
  const oddRot = (st.rotate % 180) !== 0;
  const oriW = oddRot ? natH : natW, oriH = oddRot ? natW : natH;
  const arMap = {"9-16": 9/16, "16-9": 16/9, "4-3": 4/3, "1-1": 1};

  let boxW, boxH, stageW, stageH, stageL = 0, stageT = 0;
  if(st.fit === "contain" && st.aspect === "auto"){
    const s = Math.min(wrapW / oriW, wrapH / oriH);
    boxW = Math.max(20, Math.round(oriW * s));
    boxH = Math.max(20, Math.round(oriH * s));
    stageW = boxW; stageH = boxH;
  }else if(st.fit === "fill" && st.aspect === "auto"){
    boxW = wrapW; boxH = wrapH; stageW = wrapW; stageH = wrapH;
  }else{
    const targetRatio = arMap[st.aspect] || (wrapW / wrapH);
    const bs = Math.min(wrapW / targetRatio, wrapH);
    boxW = Math.max(20, Math.round(targetRatio * bs));
    boxH = Math.max(20, Math.round(bs));
    if(st.fit === "fill"){ stageW = boxW; stageH = boxH; }
    else{
      const vs = st.fit === "cover" ? Math.max(boxW / oriW, boxH / oriH) : Math.min(boxW / oriW, boxH / oriH);
      stageW = oriW * vs; stageH = oriH * vs;
      stageL = (boxW - stageW) / 2; stageT = (boxH - stageH) / 2;
    }
  }
  box.style.width = boxW + "px"; box.style.height = boxH + "px";
  stage.style.width = stageW + "px"; stage.style.height = stageH + "px";
  stage.style.left = stageL + "px"; stage.style.top = stageT + "px";
  const vidW = oddRot ? stageH : stageW, vidH = oddRot ? stageW : stageH;
  v.style.width = vidW + "px"; v.style.height = vidH + "px";
  v.style.transform = `translate(-50%,-50%) scale(${st.flipH?-1:1},${st.flipV?-1:1}) rotate(${st.rotate}deg)`;
}
window.addEventListener("resize", layoutVideo);

function updateHud(){
  const v = document.getElementById("vidEl");
  const d = Math.max(0.01, st.durationSec || v.duration || 1);
  const cur = currentAbsSec();
  document.getElementById("tlFill").style.width = Math.min(100, (cur / d) * 100).toFixed(2) + "%";
  const rightTxt = st.showRemaining ? ("-" + fmtTime(Math.max(0, d - cur))) : fmtTime(d);
  document.getElementById("timeLbl").textContent = `${fmtTime(cur)} / ${rightTxt}`;
  if(v.buffered.length > 0 && !st.isTranscode){
    document.getElementById("tlBuf").style.width = Math.min(100, (v.buffered.end(v.buffered.length - 1) / d) * 100).toFixed(1) + "%";
  }
  const abEl = document.getElementById("tlAb");
  if(st.abA !== null && st.abB !== null && st.abB > st.abA + 0.1){
    abEl.style.display = "block";
    abEl.style.left = ((st.abA / d) * 100).toFixed(2) + "%";
    abEl.style.width = (((st.abB - st.abA) / d) * 100).toFixed(2) + "%";
    if(!v.seeking && (cur >= st.abB || cur < st.abA - 0.35)) seekTo(st.abA);
  }else{
    abEl.style.display = "none";
  }
}

function togglePlay(){
  const v = document.getElementById("vidEl");
  if(!st.path){ openFileDialog(); return; }
  if(v.paused){ v.play().catch(()=>{}); showOsd("▶ Play"); }
  else{ v.pause(); showOsd("⏸ Pause"); }
}
function toggleMute(){
  const v = document.getElementById("vidEl");
  v.muted = !v.muted;
  document.getElementById("muteBtn").textContent = v.muted ? "🔇" : (st.volPct > 100 ? "🔊+" : "🔊");
  showOsd(v.muted ? "Muted" : `Volume ${st.volPct}%`);
}
function setVolumePct(pct){
  st.volPct = Math.max(0, Math.min(200, Math.round(pct)));
  const v = document.getElementById("vidEl");
  v.muted = st.volPct === 0;
  document.getElementById("volRange").value = String(st.volPct);
  document.getElementById("volPct").textContent = st.volPct + "%";
  document.getElementById("muteBtn").textContent = v.muted ? "🔇" : (st.volPct > 100 ? "🔊+" : "🔊");
  ensureAudioBoost(v);
  if(st.volPct <= 100){ v.volume = st.volPct / 100; if(st.gainNode) st.gainNode.gain.value = 1.0; }
  else{ v.volume = 1.0; if(st.gainNode) st.gainNode.gain.value = st.volPct / 100; }
  localStorage.setItem("dv_player_vol", String(st.volPct));
}
function setSpeed(sp){
  st.speed = Math.max(0.25, Math.min(3.0, Number(sp) || 1.0));
  document.getElementById("vidEl").playbackRate = st.speed;
  document.getElementById("speedBadge").textContent = st.speed.toFixed(st.speed % 1 === 0 ? 1 : 2).replace(/0$/, "") + "x";
  renderOptionChips();
  showOsd(`Speed ${st.speed}x`);
}
function cycleSpeedQuick(){
  const list = [1.0, 1.25, 1.5, 2.0, 0.5, 0.75];
  const idx = list.findIndex(x => Math.abs(x - st.speed) < 0.04);
  setSpeed(list[(idx + 1) % list.length]);
}
function setFit(f){ st.fit = f; renderOptionChips(); layoutVideo(); showOsd("Scale: " + f); }
function setAspect(a){ st.aspect = a; renderOptionChips(); layoutVideo(); showOsd("Aspect: " + a); }
function cycleRotate(){ st.rotate = (st.rotate + 90) % 360; renderOptionChips(); layoutVideo(); showOsd(`Rotate ${st.rotate}°`); }
function toggleFlip(axis){
  if(axis === "h") st.flipH = !st.flipH; else st.flipV = !st.flipV;
  renderOptionChips(); layoutVideo();
  showOsd(`Flip ${axis.toUpperCase()}: ${(axis==="h"?st.flipH:st.flipV)?"On":"Off"}`);
}
function toggleLoopFile(){
  st.loopFile = !st.loopFile;
  document.getElementById("vidEl").loop = st.loopFile;
  renderOptionChips();
  showOsd("Loop File: " + (st.loopFile ? "On" : "Off"));
}
function markAbPoint(which){
  const cur = currentAbsSec();
  if(which === "a"){ st.abA = cur; showOsd("Loop A: " + fmtTime(cur)); }
  else{ st.abB = cur; if(st.abA === null) st.abA = 0; showOsd("Loop B: " + fmtTime(cur)); }
  renderOptionChips(); updateHud();
}
function clearAbLoop(){ st.abA = null; st.abB = null; renderOptionChips(); updateHud(); showOsd("A-B Loop Cleared"); }
function toggleRemainingTime(){ st.showRemaining = !st.showRemaining; updateHud(); }

function renderOptionChips(){
  const sc = document.getElementById("speedChips");
  sc.innerHTML = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3].map(x =>
    `<button class="m-chip ${Math.abs(st.speed - x) < 0.04 ? "active" : ""}" onclick="setSpeed(${x})">${x}x</button>`
  ).join("");
  document.getElementById("fitChips").innerHTML = [["contain","Fit"],["cover","Fill"],["fill","Stretch"]].map(([k,l]) =>
    `<button class="m-chip ${st.fit===k?"active":""}" onclick="setFit('${k}')">${l}</button>`
  ).join("");
  document.getElementById("aspectChips").innerHTML = [["auto","Auto"],["16-9","16:9"],["9-16","9:16"],["4-3","4:3"],["1-1","1:1"]].map(([k,l]) =>
    `<button class="m-chip ${st.aspect===k?"active":""}" onclick="setAspect('${k}')">${l}</button>`
  ).join("");
  document.getElementById("rotChip").textContent = `Rot ${st.rotate}°`;
  document.getElementById("rotChip").classList.toggle("active", st.rotate !== 0);
  document.getElementById("flipHChip").classList.toggle("active", st.flipH);
  document.getElementById("flipVChip").classList.toggle("active", st.flipV);
  document.getElementById("loopFileChip").textContent = "Loop: " + (st.loopFile ? "On" : "Off");
  document.getElementById("loopFileChip").classList.toggle("active", st.loopFile);
  document.getElementById("abAChip").classList.toggle("active", st.abA !== null);
  document.getElementById("abBChip").classList.toggle("active", st.abB !== null);
}

/* Auto-Hide Chrome (MPC-HC feel) */
function showChrome(pinOpen=false){
  document.getElementById("topBar").classList.remove("hidden");
  document.getElementById("botBar").classList.remove("hidden");
  document.getElementById("stageWrap").classList.remove("hide-cursor");
  clearTimeout(st.hideTimer);
  const v = document.getElementById("vidEl");
  const menuOpen = document.getElementById("optMenu").classList.contains("open") || document.getElementById("ctxMenu").classList.contains("open");
  if(!pinOpen && !menuOpen && st.path && (!v.paused || st.minimal)){
    st.hideTimer = setTimeout(()=>{
      document.getElementById("topBar").classList.add("hidden");
      document.getElementById("botBar").classList.add("hidden");
      if(st.isFullscreen || st.minimal) document.getElementById("stageWrap").classList.add("hide-cursor");
    }, st.minimal ? 1100 : 2200);
  }
}
function toggleMinimalMode(){
  st.minimal = !st.minimal;
  document.getElementById("minChromeBtn").classList.toggle("active", st.minimal);
  showOsd(st.minimal ? "Minimal Mode: On" : "Minimal Mode: Off");
  showChrome();
}
function closeMenus(){
  document.getElementById("optMenu").classList.remove("open");
  document.getElementById("ctxMenu").classList.remove("open");
}
function toggleOptionsMenu(ev){
  if(ev) ev.stopPropagation();
  document.getElementById("ctxMenu").classList.remove("open");
  const m = document.getElementById("optMenu");
  m.classList.toggle("open");
  showChrome(m.classList.contains("open"));
}

/* Native Window Controls */
function winAction(fn){ if(window.pywebview?.api?.[fn]) window.pywebview.api[fn](); }
async function toggleOnTop(){
  if(window.pywebview?.api?.toggle_on_top){
    st.onTop = await window.pywebview.api.toggle_on_top();
    document.getElementById("pinBtn").classList.toggle("active", st.onTop);
    showOsd(st.onTop ? "📌 Always on Top: On" : "Always on Top: Off");
  }
}
function toggleFullscreen(){
  if(window.pywebview?.api?.toggle_fullscreen){
    st.isFullscreen = !st.isFullscreen;
    window.pywebview.api.toggle_fullscreen();
    requestAnimationFrame(layoutVideo);
  }else if(document.fullscreenElement){
    document.exitFullscreen(); st.isFullscreen = false;
  }else{
    document.documentElement.requestFullscreen?.(); st.isFullscreen = true;
  }
}
async function openFileDialog(){
  if(window.pywebview?.api?.pick_video_file){
    const chosen = await window.pywebview.api.pick_video_file();
    if(chosen) await loadVideo(chosen, st.localHostId);
  }else{
    showOsd("Drop a video file onto the window");
  }
}
async function revealCurrent(){
  if(!st.path || (st.hostId && st.hostId !== st.localHostId)){ showOsd("Explorer reveal is for local files"); return; }
  await apiFetch("/api/system/reveal", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({path: st.path})});
}
async function openInFullDeckVault(){
  if(window.pywebview?.api?.open_in_main_app){
    await window.pywebview.api.open_in_main_app(st.path || "");
    showOsd("Opening DeckVault App...");
  }
}
async function saveSnapshot(){
  if(!st.path) return;
  try{
    const res = await apiFetch(
      st.hostId && st.hostId !== st.localHostId
        ? `/api/fleet/proxy?hostId=${encodeURIComponent(st.hostId)}&route=${encodeURIComponent("/api/video/snapshot")}`
        : "/api/video/snapshot",
      {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({path: st.path, ms: Math.round(currentAbsSec()*1000)})}
    );
    showOsd("📷 Saved " + (res.name || "snapshot.png"));
  }catch(e){ showOsd("Snapshot failed"); }
}
async function registerDefaultPlayer(){
  try{
    const res = await apiFetch("/api/system/file-assoc/register", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({openSettings: true})
    });
    showOsd(res.message || "Registered DeckVault Player!");
  }catch(e){ showOsd("Assoc error: " + e.message); }
}

async function loadVideo(path, hostId=""){
  if(!path){
    document.getElementById("emptyDrop").classList.add("show");
    return;
  }
  document.getElementById("emptyDrop").classList.remove("show");
  st.path = path;
  st.hostId = hostId || st.localHostId;
  st.isTranscode = false;
  st.transcodeOffsetSec = 0;
  st.abA = null; st.abB = null;
  st.rotate = 0; st.flipH = false; st.flipV = false;
  renderOptionChips();

  let ctx = null;
  try{
    ctx = await apiFetch("/api/player/context?" + new URLSearchParams({path, hostId: st.hostId}).toString());
  }catch{}
  st.file = ctx?.file || {name: path.split(/[\\/]/).pop() || "Video", path, extension: (path.split(".").pop()||"").toLowerCase(), meta:{}};
  st.siblings = ctx?.siblings || [];
  st.siblingIdx = typeof ctx?.index === "number" ? ctx.index : -1;
  st.durationSec = (st.file.meta?.durationMs || 0) / 1000.0;

  document.title = st.file.name + " — DeckVault Player";
  document.getElementById("vidTitle").textContent = st.file.name;
  const mt = document.getElementById("metaTag");
  const rTxt = fmtRes(st.file.meta?.width, st.file.meta?.height);
  const fpsTxt = st.file.meta?.fps ? `${Math.round(st.file.meta.fps)}fps` : "";
  const badge = [rTxt, fpsTxt, st.file.meta?.videoCodec].filter(Boolean).join(" • ");
  mt.textContent = badge || (st.file.extension || "VIDEO").toUpperCase();
  mt.style.display = "";

  document.getElementById("prevBtn").disabled = st.siblingIdx <= 0;
  document.getElementById("nextBtn").disabled = st.siblingIdx < 0 || st.siblingIdx >= st.siblings.length - 1;

  const v = document.getElementById("vidEl");
  v.loop = st.loopFile;
  v.playbackRate = st.speed;
  if(needsLiveTranscode(st.file)){
    startTranscodeStream(0);
  }else{
    v.src = hostMediaUrl(st.hostId, "/api/stream", {path: st.path, v: Date.now()});
    v.play().catch(()=>{});
  }
  showChrome();
}

function stepSibling(delta){
  if(st.siblingIdx < 0 || !st.siblings.length) return;
  const next = st.siblings[st.siblingIdx + delta];
  if(next){ loadVideo(next.path, st.hostId); showOsd(next.name); }
}

/* Wire Video Events, Timeline Hover Storyboard, Drag-Resize & Keyboard Shortcuts */
function initInteractions(){
  const v = document.getElementById("vidEl");
  v.onloadstart = ()=>setBuffering(true);
  v.onwaiting = ()=>setBuffering(true);
  v.onstalled = ()=>setBuffering(true);
  v.oncanplay = ()=>setBuffering(false);
  v.onplaying = ()=>{ setBuffering(false); document.getElementById("playBtn").textContent = "⏸"; ensureAudioBoost(v); showChrome(); };
  v.onpause = ()=>{ document.getElementById("playBtn").textContent = "▶"; showChrome(true); };
  v.ontimeupdate = updateHud;
  v.onloadedmetadata = ()=>{
    const d = Math.max(0.1, st.isTranscode ? (st.durationSec || v.duration || 1) : (v.duration || st.durationSec || 1));
    st.durationSec = d;
    updateHud(); layoutVideo();
  };
  v.onloadeddata = ()=>{
    if(!st.isTranscode && v.videoWidth === 0){ startTranscodeStream(0); }
    layoutVideo();
  };
  v.onerror = ()=>{
    setBuffering(false);
    if(!st.isTranscode && st.path){ startTranscodeStream(0); }
  };

  // Timeline hover storyboard & click/drag seek
  const tl = document.getElementById("tlWrap"), tip = document.getElementById("tlTip"), img = document.getElementById("tlTipImg"), txt = document.getElementById("tlTipTxt");
  let draggingSeek = false;
  const fracFromEv = (e) => {
    const r = tl.getBoundingClientRect();
    return Math.max(0, Math.min(0.999, (e.clientX - r.left) / Math.max(1, r.width)));
  };
  tl.onmousemove = (e) => {
    const d = st.durationSec || v.duration || 0; if(!d || !st.path) return;
    const r = tl.getBoundingClientRect(), frac = fracFromEv(e);
    const idx = Math.floor(frac * 16), col = idx % 4, row = Math.floor(idx / 4);
    const sb = hostMediaUrl(st.hostId, "/api/video/storyboard", {path: st.path, cols: 4, rows: 4});
    tip.style.display = "flex";
    tip.style.left = Math.max(72, Math.min(r.width - 72, e.clientX - r.left)) + "px";
    img.style.backgroundImage = `url("${sb}")`; img.style.backgroundSize = "400% 400%";
    img.style.backgroundPosition = `${col*(100/3)}% ${row*(100/3)}%`;
    txt.textContent = fmtTime(frac * d);
    if(draggingSeek) seekTo(frac * d);
  };
  tl.onmouseleave = ()=>{ if(!draggingSeek) tip.style.display = "none"; };
  tl.onmousedown = (e)=>{
    if(e.button !== 0) return;
    const d = st.durationSec || v.duration || 0; if(!d) return;
    draggingSeek = true;
    seekTo(fracFromEv(e) * d);
  };
  window.addEventListener("mouseup", ()=>{ if(draggingSeek){ draggingSeek = false; tip.style.display = "none"; } });

  // Stage click, double-click fullscreen, right-click menu, mouse wheel volume
  const wrap = document.getElementById("stageWrap");
  let clickTimer = null;
  wrap.onclick = (e)=>{
    closeMenus();
    if(e.target.closest("#topBar,#botBar,#optMenu,#ctxMenu")) return;
    clearTimeout(clickTimer);
    clickTimer = setTimeout(()=>{ if(st.path) togglePlay(); }, 185);
  };
  wrap.ondblclick = ()=>{
    clearTimeout(clickTimer);
    toggleFullscreen();
  };
  wrap.oncontextmenu = (e)=>{
    e.preventDefault();
    document.getElementById("optMenu").classList.remove("open");
    const m = document.getElementById("ctxMenu");
    m.style.left = Math.min(window.innerWidth - 208, e.clientX) + "px";
    m.style.top = Math.min(window.innerHeight - 310, e.clientY) + "px";
    m.classList.add("open");
    showChrome(true);
  };
  window.addEventListener("wheel", (e)=>{
    if(e.target.closest("#optMenu")) return;
    e.preventDefault();
    setVolumePct(st.volPct + (e.deltaY < 0 ? 5 : -5));
    showOsd(`Volume ${st.volPct}%`);
    showChrome();
  }, {passive: false});
  window.addEventListener("mousemove", ()=>showChrome());
  window.addEventListener("click", (e)=>{
    if(!e.target.closest("#optMenu,#optBtn")) document.getElementById("optMenu").classList.remove("open");
    if(!e.target.closest("#ctxMenu")) document.getElementById("ctxMenu").classList.remove("open");
  });

  // Keyboard shortcuts (MPC-HC compatible)
  window.addEventListener("keydown", (e)=>{
    if(e.ctrlKey && e.key.toLowerCase() === "t"){ e.preventDefault(); toggleOnTop(); return; }
    if(e.ctrlKey && e.key.toLowerCase() === "o"){ e.preventDefault(); openFileDialog(); return; }
    if(e.altKey && e.key === "Enter"){ e.preventDefault(); toggleFullscreen(); return; }
    if(e.key === " "){ e.preventDefault(); togglePlay(); }
    else if(e.key === "ArrowRight"){ e.preventDefault(); seekRel(e.ctrlKey ? 30 : (e.shiftKey ? 1 : 5)); }
    else if(e.key === "ArrowLeft"){ e.preventDefault(); seekRel(e.ctrlKey ? -30 : (e.shiftKey ? -1 : -5)); }
    else if(e.key === "ArrowUp"){ e.preventDefault(); setVolumePct(st.volPct + 5); showOsd(`Volume ${st.volPct}%`); }
    else if(e.key === "ArrowDown"){ e.preventDefault(); setVolumePct(st.volPct - 5); showOsd(`Volume ${st.volPct}%`); }
    else if(e.key === ","){ nudgeFrame(-1); }
    else if(e.key === "."){ nudgeFrame(1); }
    else if(e.key === "["){ setSpeed(Math.max(0.25, +(st.speed - 0.25).toFixed(2))); }
    else if(e.key === "]"){ setSpeed(Math.min(3.0, +(st.speed + 0.25).toFixed(2))); }
    else if(e.key === "Backspace"){ setSpeed(1.0); }
    else if(e.key.toLowerCase() === "f"){ toggleFullscreen(); }
    else if(e.key.toLowerCase() === "m"){ toggleMute(); }
    else if(e.key.toLowerCase() === "r"){ cycleRotate(); }
    else if(e.key.toLowerCase() === "l"){ toggleLoopFile(); }
    else if(e.key === "1"){ toggleMinimalMode(); }
    else if(e.key === "PageUp"){ e.preventDefault(); stepSibling(-1); }
    else if(e.key === "PageDown"){ e.preventDefault(); stepSibling(1); }
    else if(e.key === "F5"){ e.preventDefault(); saveSnapshot(); }
    else if(e.key === "Escape"){
      if(st.isFullscreen) toggleFullscreen();
      else winAction("close");
    }
  });
}

function initPyWebViewChrome(){
  const isP = !!window.pywebview;
  document.getElementById("winEdges").style.display = isP ? "block" : "none";
  document.getElementById("dragRegion").ondblclick = ()=>toggleFullscreen();
  if(!isP) return;
  document.querySelectorAll(".win-edge").forEach(el=>{
    el.onmousedown = async (e)=>{
      e.preventDefault();
      const edge = el.dataset.edge;
      const b = await window.pywebview.api.get_bounds();
      const sx = e.screenX, sy = e.screenY;
      const move = (ev)=>{
        const dx = ev.screenX - sx, dy = ev.screenY - sy;
        let x = b.x, y = b.y, w = b.w, h = b.h;
        if(edge.includes("e")) w = Math.max(360, b.w + dx);
        if(edge.includes("s")) h = Math.max(220, b.h + dy);
        if(edge.includes("w")){ w = Math.max(360, b.w - dx); x = b.x + (b.w - w); }
        if(edge.includes("n")){ h = Math.max(220, b.h - dy); y = b.y + (b.h - h); }
        window.pywebview.api.set_bounds(x, y, w, h);
      };
      const up = ()=>{ window.removeEventListener("mousemove", move); window.removeEventListener("mouseup", up); };
      window.addEventListener("mousemove", move);
      window.addEventListener("mouseup", up);
    };
  });
}
window.addEventListener("pywebviewready", initPyWebViewChrome);

async function initPlayer(){
  applySavedTheme();
  initPyWebViewChrome();
  initInteractions();
  renderOptionChips();
  const savedVol = Number(localStorage.getItem("dv_player_vol") || "100");
  setVolumePct(isFinite(savedVol) ? savedVol : 100);
  try{
    const bs = await fetch("/api/desktop/bootstrap");
    if(bs.ok){
      const d = await bs.json();
      st.token = d.token || "";
      st.localHostId = d.localHostId || "";
    }
  }catch{}
  const params = new URLSearchParams(window.location.search);
  const p = params.get("path") || "";
  const hid = params.get("hostId") || st.localHostId;
  await loadVideo(p, hid);
}
initPlayer();
</script>
</body>
</html>
"""

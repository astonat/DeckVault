"""DeckVault Desktop & Web Studio UI (Flush Dark Matte OLED + VLC-grade Video Player & Cut/Crop Studio)."""

DESKTOP_APP_HTML = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"/>
<title>DeckVault</title>
<style>
  :root{
    --bg:#000000; --panel:#090a0d; --card:#111217; --elev:#181a21; --high:#22252f;
    --border:#252833; --text:#ededf2; --muted:#989aa8;
    --primary:#8eb8ff; --on-primary:#071326; --primary-container:#1b2c47;
    --secondary:#a4b8d0; --tertiary:#a5c7b4;
    --success:#8fb8a0; --warn:#d1bc95; --danger:#cf8c92;
  }
  body.soft{ --bg:#0d0e12; --panel:#14151b; --card:#1b1d25; --elev:#22242e; --high:#2c2f3b; --border:#2e313e; }
  body.pal-sage{ --primary:#95bfa7; --on-primary:#071d12; --primary-container:#1a3627; }
  body.pal-sand{ --primary:#cdb894; --on-primary:#231907; --primary-container:#3a2f1a; }
  body.pal-rose{ --primary:#c9969e; --on-primary:#280a10; --primary-container:#3e1f26; }
  body.pal-stone{ --primary:#b8bcc8; --on-primary:#15171d; --primary-container:#2b2e38; }

  *{box-sizing:border-box;user-select:none}
  input,textarea,pre,.selectable{user-select:text}
  html,body{height:100%;margin:0;background:var(--bg);color:var(--text);font-family:"Segoe UI Variable","Segoe UI",Inter,-apple-system,sans-serif;overflow:hidden}
  button,input,select{font:inherit;color:var(--text)}
  ::-webkit-scrollbar{width:7px;height:7px}
  ::-webkit-scrollbar-thumb{background:var(--high);border-radius:99px}
  ::-webkit-scrollbar-track{background:transparent}

  /* Frameless Edge Resize Grips */
  .win-edge{position:fixed;z-index:250}
  .win-n{top:0;left:6px;right:6px;height:5px;cursor:ns-resize}
  .win-s{bottom:0;left:6px;right:6px;height:5px;cursor:ns-resize}
  .win-w{left:0;top:6px;bottom:6px;width:5px;cursor:ew-resize}
  .win-e{right:0;top:6px;bottom:6px;width:5px;cursor:ew-resize}
  .win-nw{top:0;left:0;width:8px;height:8px;cursor:nwse-resize}
  .win-ne{top:0;right:0;width:8px;height:8px;cursor:nesw-resize}
  .win-sw{bottom:0;left:0;width:8px;height:8px;cursor:nesw-resize}
  .win-se{bottom:0;right:0;width:8px;height:8px;cursor:nwse-resize}

  /* Flush Custom Titlebar */
  .titlebar{height:38px;background:var(--panel);border-bottom:1px solid var(--border);display:flex;align-items:center;padding-left:12px;gap:10px;flex-shrink:0}
  .titlebar-drag{flex:1;height:100%;display:flex;align-items:center;gap:10px;min-width:0}
  .brand-dot{width:9px;height:9px;border-radius:50%;background:var(--primary);box-shadow:0 0 8px rgba(142,184,255,.45)}
  .brand-name{font-size:12px;font-weight:700;letter-spacing:.3px}
  .title-pill{background:var(--card);border:1px solid var(--border);border-radius:99px;padding:2px 9px;font-size:11px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:340px}
  .win-ctrls{display:none;height:100%;align-items:stretch}
  .win-ctrls.show{display:flex}
  .wbtn{width:46px;border:none;background:transparent;color:var(--muted);cursor:pointer;font-size:13px;display:flex;align-items:center;justify-content:center;transition:background .12s,color .12s}
  .wbtn:hover{background:var(--elev);color:var(--text)}
  .wbtn.close:hover{background:#8c2d38;color:#fff}

  /* Main App Frame */
  .app-wrap{display:flex;flex-direction:column;height:100vh;width:100vw;overflow:hidden}
  .shell{display:flex;flex:1;min-height:0;overflow:hidden}
  .rail{width:66px;background:var(--bg);border-right:1px solid var(--border);display:flex;flex-direction:column;align-items:center;padding:8px 5px;gap:5px;flex-shrink:0}
  .rail.hidden{display:none}
  .rail-btn{width:54px;padding:7px 4px;border-radius:13px;border:none;background:transparent;color:var(--muted);cursor:pointer;display:flex;flex-direction:column;align-items:center;gap:3px;font-size:10px;font-weight:600;transition:background .14s,color .14s}
  .rail-btn:hover{background:var(--elev);color:var(--text)}
  .rail-btn.active{background:var(--primary-container);color:var(--primary)}
  .rail-sep{width:34px;height:1px;background:var(--border);margin:3px 0}
  .rail-spacer{flex:1}
  .saved-pill{background:var(--card);border:1px solid var(--border);border-radius:11px;padding:5px 4px;text-align:center;width:56px}
  .saved-pill b{display:block;font-size:8px;color:var(--success);letter-spacing:.4px}
  .saved-pill span{font-size:10px;font-weight:700}

  /* Adjustable Left Tree Panel */
  .tree-pane{width:300px;min-width:200px;max-width:540px;background:var(--panel);border-right:1px solid var(--border);display:flex;flex-direction:column;padding:11px;gap:9px;flex-shrink:0;overflow:hidden}
  .tree-pane.collapsed{display:none}
  .resizer{width:6px;cursor:col-resize;background:transparent;flex-shrink:0;display:flex;align-items:center;justify-content:center}
  .resizer::after{content:"";width:2px;height:44px;border-radius:99px;background:var(--border);transition:background .15s}
  .resizer:hover::after{background:var(--primary)}
  .resizer.hidden{display:none}

  .host-box{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:8px 10px;display:flex;align-items:center;gap:8px;cursor:pointer;flex:1;min-width:0}
  .host-box:hover{border-color:var(--primary)}
  .host-title{font-size:13px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .host-sub{font-size:11px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .icon-btn{width:33px;height:33px;border-radius:10px;border:1px solid var(--border);background:var(--card);color:var(--text);cursor:pointer;display:inline-flex;align-items:center;justify-content:center;flex-shrink:0}
  .icon-btn:hover{border-color:var(--primary);color:var(--primary)}

  .chip-row{display:flex;gap:6px;overflow-x:auto;padding-bottom:2px;flex-shrink:0}
  .chip{padding:5px 10px;border-radius:99px;border:1px solid var(--border);background:var(--card);color:var(--muted);font-size:11px;font-weight:600;cursor:pointer;white-space:nowrap;display:inline-flex;align-items:center;gap:5px}
  .chip:hover{color:var(--text);border-color:var(--high)}
  .chip.active{background:var(--primary-container);border-color:var(--primary);color:var(--primary)}
  .dot{width:6px;height:6px;border-radius:50%;background:var(--muted)}
  .chip.active .dot{background:var(--success)}

  .drive-strip{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:7px 9px;cursor:pointer;min-width:128px}
  .drive-strip.active,.drive-strip.drop-target{border-color:var(--primary)}
  .cloud-box{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:7px 8px;display:flex;flex-direction:column;gap:5px;flex-shrink:0}
  .cloud-hdr{display:flex;align-items:center;justify-content:space-between;font-size:10px;font-weight:700;color:var(--muted);letter-spacing:.4px}
  .cloud-tab{background:var(--elev);border:1px solid var(--border);border-radius:10px;padding:5px 9px;font-size:11px;font-weight:600;color:var(--text);cursor:pointer;display:inline-flex;align-items:center;gap:6px;white-space:nowrap}
  .cloud-tab:hover,.cloud-tab.active,.cloud-tab.drop-target{border-color:var(--primary);color:var(--primary);background:var(--primary-container)}
  .bar{height:5px;border-radius:99px;background:var(--elev);overflow:hidden;margin:4px 0 3px}
  .bar>div{height:100%;background:var(--primary);border-radius:99px}

  .tree-scroll{flex:1;overflow-y:auto;display:flex;flex-direction:column;gap:3px;padding-right:2px}
  .tree-row{padding:7px 9px;border-radius:11px;border:1px solid transparent;background:transparent;cursor:pointer;display:flex;flex-direction:column;gap:4px;text-align:left}
  .tree-row:hover{background:var(--card);border-color:var(--border)}
  .tree-row-top{display:flex;align-items:center;gap:7px;font-size:12px;font-weight:600}
  .tree-row-name{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}

  /* Center Browser Pane */
  .main-pane{flex:1;display:flex;flex-direction:column;min-width:0;padding:11px 14px 6px;gap:9px;overflow:hidden;position:relative}
  .topbar{display:flex;align-items:center;gap:7px;flex-wrap:wrap}
  .crumbs{display:flex;align-items:center;gap:4px;overflow-x:auto;flex:1;min-width:160px;padding:2px 0}
  .crumb{padding:4px 8px;border-radius:8px;background:var(--card);border:1px solid var(--border);font-size:11px;font-weight:600;color:var(--muted);cursor:pointer;white-space:nowrap}
  .crumb:hover{color:var(--text);border-color:var(--primary)}
  .crumb.last{color:var(--text);background:var(--elev)}

  .pill-btn{padding:6px 11px;border-radius:11px;border:1px solid var(--border);background:var(--card);color:var(--text);font-size:12px;font-weight:600;cursor:pointer;display:inline-flex;align-items:center;gap:5px;white-space:nowrap}
  .pill-btn:hover:not(:disabled){border-color:var(--primary)}
  .pill-btn:disabled{opacity:.38;cursor:default}
  .pill-btn.primary{background:var(--primary);color:var(--on-primary);border-color:transparent}
  .pill-btn.danger{color:var(--danger);border-color:#44282c;background:#1e1214}

  .search-row{display:flex;gap:7px;align-items:center;flex-wrap:wrap}
  .input{background:var(--card);border:1px solid var(--border);border-radius:11px;padding:7px 11px;outline:none;font-size:12px}
  .input:focus{border-color:var(--primary)}

  .browser-scroll{flex:1;overflow-y:auto;padding-bottom:84px;position:relative}
  .browser-scroll.drop-target{outline:2px dashed var(--primary);outline-offset:-4px;background:rgba(142,184,255,.04)}
  .marquee-rect{position:fixed;border:1.5px solid var(--primary);background:rgba(142,184,255,.16);border-radius:4px;pointer-events:none;z-index:45;display:none}
  .grid{display:grid;grid-template-columns:repeat(var(--grid-cols,4),minmax(0,1fr));gap:10px}
  .grid.auto{grid-template-columns:repeat(auto-fill,minmax(var(--card-min,195px),1fr))}
  .media-card{background:var(--card);border:1px solid var(--border);border-radius:15px;overflow:hidden;cursor:pointer;display:flex;flex-direction:column;position:relative;transition:border-color .14s,transform .1s}
  .media-card:hover{border-color:var(--high)}
  .media-card.focused{border-color:var(--secondary)}
  .media-card.selected{border-color:var(--primary);box-shadow:0 0 0 1px var(--primary);background:var(--elev)}
  .sel-check{position:absolute;top:7px;left:7px;width:21px;height:21px;border-radius:99px;border:2px solid rgba(255,255,255,.72);background:rgba(0,0,0,.62);color:#000;font-size:11px;font-weight:900;display:flex;align-items:center;justify-content:center;opacity:0;transition:opacity .12s;z-index:4;cursor:pointer}
  .media-card:hover .sel-check,.list-row:hover .sel-check,body.has-selection .sel-check,body.select-mode .sel-check,.selected .sel-check{opacity:1}
  .selected .sel-check{background:var(--primary);border-color:var(--primary);color:var(--on-primary)}
  .thumb-wrap{position:relative;width:100%;aspect-ratio:var(--card-aspect,16/10);background:#07080b;overflow:hidden;display:flex;align-items:center;justify-content:center}
  .thumb-wrap img{width:100%;height:100%;object-fit:cover;display:block}
  .sb-hover{position:absolute;inset:0;background-repeat:no-repeat;display:none;pointer-events:none}
  .badge{position:absolute;bottom:6px;right:6px;background:rgba(0,0,0,.8);border:1px solid rgba(255,255,255,.12);padding:2px 6px;border-radius:6px;font-size:10px;font-weight:700}
  .badge.tl{right:auto;left:6px;top:auto;bottom:6px}
  .card-body{padding:8px 10px;display:flex;flex-direction:column;gap:4px}
  .card-title{font-size:12px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .card-meta{display:flex;align-items:center;justify-content:space-between;font-size:11px;color:var(--muted)}
  .card-actions{display:flex;gap:4px;margin-top:2px;flex-wrap:wrap}
  .mini-btn{padding:3px 7px;border-radius:7px;border:1px solid var(--border);background:var(--elev);color:var(--text);font-size:10px;font-weight:600;cursor:pointer;white-space:nowrap}
  .mini-btn:hover{border-color:var(--primary);color:var(--primary)}

  .list-col{display:flex;flex-direction:column;gap:5px}
  .list-col.compact{gap:3px}
  .list-row{background:var(--card);border:1px solid var(--border);border-radius:13px;padding:7px 11px;display:flex;align-items:center;gap:11px;cursor:pointer;position:relative}
  .list-row .sel-check{position:static;flex-shrink:0;width:18px;height:18px;opacity:.45}
  .list-col.compact .list-row{padding:5px 9px;border-radius:10px}
  .list-row:hover{border-color:var(--high)}
  .list-row.selected{border-color:var(--primary);background:var(--elev)}
  .list-thumb{width:64px;height:40px;border-radius:8px;background:#07080b;object-fit:cover;flex-shrink:0;display:flex;align-items:center;justify-content:center;font-size:10px;color:var(--muted);font-weight:700}
  .list-col.compact .list-thumb{width:46px;height:30px}

  /* Split Dual-Pane Browser */
  .split-pane{flex:0 0 460px;min-width:290px;max-width:65vw;border-left:1px solid var(--border);background:var(--bg)}
  .split-hdr{display:flex;align-items:center;gap:6px;padding:6px 8px;background:var(--panel);border:1px solid var(--border);border-radius:12px;flex-wrap:wrap}
  .est-box{background:var(--elev);border:1px solid var(--border);border-radius:14px;padding:12px;display:flex;flex-direction:column;gap:8px}
  .est-bar{height:8px;border-radius:99px;background:var(--high);overflow:hidden;position:relative}
  .est-bar-fill{height:100%;background:var(--success);border-radius:99px;transition:width .2s}

  /* Adjustable Right Inspector Panel */
  .insp-pane{width:280px;min-width:210px;max-width:440px;background:var(--panel);border-left:1px solid var(--border);display:flex;flex-direction:column;padding:12px;gap:10px;overflow-y:auto;flex-shrink:0}
  .insp-pane.hidden{display:none}
  .kv-row{display:flex;justify-content:space-between;gap:8px;font-size:11px;padding:4px 0;border-bottom:1px solid rgba(255,255,255,.04)}
  .kv-row span{color:var(--muted)}
  .kv-row b{text-align:right;word-break:break-all;font-weight:600}

  /* Bottom Status Footer */
  .footer-bar{height:28px;background:var(--panel);border-top:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;padding:0 14px;font-size:11px;color:var(--muted);flex-shrink:0}

  .sel-bar{position:absolute;bottom:14px;left:14px;right:14px;background:var(--high);border:1px solid var(--primary);border-radius:16px;padding:9px 14px;display:none;align-items:center;gap:8px;z-index:20;box-shadow:0 12px 30px rgba(0,0,0,.65)}
  .sel-bar.open{display:flex}

  /* Customize Grid Popover */
  .popover{position:absolute;top:52px;right:18px;width:300px;background:var(--panel);border:1px solid var(--border);border-radius:18px;padding:14px;display:none;flex-direction:column;gap:10px;z-index:50;box-shadow:0 16px 40px rgba(0,0,0,.85)}
  .popover.open{display:flex}

  /* Overlays & Modals */
  .overlay{position:fixed;inset:38px 0 0 0;background:#000;display:none;flex-direction:column;z-index:60}
  .overlay.fullscreen-all{inset:0}
  .overlay.open{display:flex}
  .dialog-bg{position:fixed;inset:0;background:rgba(0,0,0,.78);display:none;align-items:center;justify-content:center;z-index:90;padding:18px}
  .dialog-bg.open{display:flex}
  .dialog{width:min(780px,96vw);max-height:88vh;background:var(--panel);border:1px solid var(--border);border-radius:22px;display:flex;flex-direction:column;overflow:hidden}
  .dialog-hdr{padding:14px 18px;border-bottom:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;gap:10px}
  .dialog-body{padding:16px 18px;overflow-y:auto;display:flex;flex-direction:column;gap:13px}
  .section-card{background:var(--card);border:1px solid var(--border);border-radius:15px;padding:13px;display:flex;flex-direction:column;gap:9px}
  .drives-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(255px,1fr));gap:10px}
  .drive-card{background:var(--panel);border:1px solid var(--border);border-radius:13px;padding:12px;cursor:pointer;display:flex;flex-direction:column;gap:5px}
  .drive-card:hover{border-color:var(--primary)}

  /* VLC-Rival Video Player & Crop/Cut Studio */
  .cinema-wrap{flex:1;position:relative;display:flex;align-items:center;justify-content:center;background:#000;overflow:hidden;min-height:0}
  .video-box{position:relative;overflow:hidden;flex-shrink:0;background:#000}
  .video-stage{position:absolute;left:0;top:0}
  .video-stage video{position:absolute;left:50%;top:50%;display:block;max-width:none;max-height:none;object-fit:fill}

  /* Interactive Visual Crop Box Overlay */
  .crop-layer{position:absolute;inset:0;display:none;cursor:crosshair;z-index:3}
  .crop-layer.active{display:block}
  .crop-shade{position:absolute;background:rgba(0,0,0,.64);pointer-events:none}
  .crop-rect{position:absolute;border:2px solid var(--primary);box-shadow:0 0 0 1px rgba(0,0,0,.6);cursor:move}
  .crop-grid-v1,.crop-grid-v2{position:absolute;top:0;bottom:0;width:1px;background:rgba(255,255,255,.28);pointer-events:none}
  .crop-grid-v1{left:33.33%}.crop-grid-v2{left:66.66%}
  .crop-grid-h1,.crop-grid-h2{position:absolute;left:0;right:0;height:1px;background:rgba(255,255,255,.28);pointer-events:none}
  .crop-grid-h1{top:33.33%}.crop-grid-h2{top:66.66%}
  .ch{position:absolute;width:12px;height:12px;background:var(--primary);border:2px solid #000;border-radius:3px}
  .ch-nw{top:-6px;left:-6px;cursor:nwse-resize}.ch-ne{top:-6px;right:-6px;cursor:nesw-resize}
  .ch-sw{bottom:-6px;left:-6px;cursor:nesw-resize}.ch-se{bottom:-6px;right:-6px;cursor:nwse-resize}
  .ch-n{top:-6px;left:50%;transform:translateX(-50%);cursor:ns-resize}.ch-s{bottom:-6px;left:50%;transform:translateX(-50%);cursor:ns-resize}
  .ch-w{left:-6px;top:50%;transform:translateY(-50%);cursor:ew-resize}.ch-e{right:-6px;top:50%;transform:translateY(-50%);cursor:ew-resize}
  .crop-readout{position:absolute;top:8px;left:50%;transform:translateX(-50%);background:rgba(0,0,0,.84);border:1px solid var(--primary);color:var(--primary);padding:3px 10px;border-radius:99px;font-size:11px;font-weight:700;pointer-events:none;white-space:nowrap}
  .crop-preview-badge{position:absolute;bottom:14px;left:50%;transform:translateX(-50%);background:rgba(12,18,28,.88);border:1px solid var(--primary);color:var(--primary);padding:5px 12px;border-radius:99px;font-size:11px;font-weight:700;cursor:pointer;z-index:6;display:none;box-shadow:0 6px 18px rgba(0,0,0,.7)}
  .crop-preview-badge.open{display:flex;align-items:center;gap:8px}

  /* Cinema Transport HUD */
  .hud-top{position:absolute;top:0;left:0;right:0;background:linear-gradient(180deg,rgba(0,0,0,.85),transparent);padding:12px 18px;display:flex;align-items:center;gap:10px;transition:opacity .25s;z-index:8}
  .hud-bottom{background:var(--panel);border-top:1px solid var(--border);padding:10px 16px;display:flex;flex-direction:column;gap:8px;flex-shrink:0}
  .timeline-wrap{position:relative;height:20px;display:flex;align-items:center;cursor:pointer}
  .timeline-track{width:100%;height:6px;background:var(--high);border-radius:99px;position:relative;overflow:hidden}
  .timeline-buf{position:absolute;top:0;bottom:0;left:0;background:rgba(255,255,255,.18)}
  .timeline-fill{position:absolute;top:0;bottom:0;left:0;background:var(--primary)}
  .timeline-ab{position:absolute;top:0;bottom:0;background:rgba(165,199,180,.35);display:none}
  .timeline-thumb-tip{position:absolute;bottom:24px;transform:translateX(-50%);background:var(--panel);border:1px solid var(--border);border-radius:10px;padding:4px;display:none;flex-direction:column;align-items:center;gap:3px;pointer-events:none;z-index:20;box-shadow:0 8px 24px rgba(0,0,0,.8)}
  .sb-tip-img{width:140px;height:78px;border-radius:7px;background-repeat:no-repeat;background-color:#000}

  .studio-drawer{background:var(--panel);border-top:1px solid var(--border);padding:16px 18px 11px;display:none;flex-direction:column;gap:9px;max-height:44vh;overflow-y:auto;flex-shrink:0}
  .studio-drawer.open{display:flex}
  .sb-strip{position:relative;height:56px;margin:10px 8px 2px;cursor:pointer;user-select:none}
  .sb-frames{position:relative;width:100%;height:100%;border-radius:10px;overflow:hidden;background:var(--card);border:1px solid var(--border)}
  .sb-row{display:flex;height:100%;width:100%}
  .sb-cell{flex:1;background-size:400% 400%;background-repeat:no-repeat}
  .trim-mask-l,.trim-mask-r{position:absolute;top:0;bottom:0;background:rgba(0,0,0,.74);pointer-events:none}
  .trim-mask-l{left:0}.trim-mask-r{right:0}
  .trim-window{position:absolute;top:-1px;bottom:-1px;border-top:3px solid var(--primary);border-bottom:3px solid var(--primary);pointer-events:none;box-shadow:inset 0 0 0 1px rgba(165,199,180,.28)}
  .trim-playhead{position:absolute;top:-3px;bottom:-3px;width:2px;background:#fff;box-shadow:0 0 6px #000;pointer-events:none;z-index:4}
  .trim-handle{position:absolute;top:-4px;bottom:-4px;width:14px;background:var(--primary);border:2px solid #0a0f18;border-radius:5px;cursor:ew-resize;z-index:6;display:flex;align-items:center;justify-content:center;transform:translateX(-50%);box-shadow:0 2px 10px rgba(0,0,0,.7)}
  .trim-handle::after{content:"";width:2px;height:20px;border-left:1px solid #0a0f18;border-right:1px solid #0a0f18;opacity:.75}
  .trim-handle:hover,.trim-handle.active{background:#c5e5d2;transform:translateX(-50%) scaleX(1.12)}
  .trim-handle-tag{position:absolute;bottom:calc(100% + 4px);left:50%;transform:translateX(-50%);background:#0a0f18;border:1px solid var(--primary);color:var(--primary);font-size:10px;font-weight:700;font-family:Consolas,monospace;padding:1px 6px;border-radius:6px;white-space:nowrap;pointer-events:none}

  input[type=range]{accent-color:var(--primary);cursor:pointer}
  .ctx-menu{position:fixed;background:var(--elev);border:1px solid var(--border);border-radius:13px;padding:5px;min-width:190px;display:none;flex-direction:column;gap:2px;z-index:180;box-shadow:0 16px 36px rgba(0,0,0,.85)}
  .ctx-menu.open{display:flex}
  .ctx-item{padding:7px 11px;border:none;background:transparent;text-align:left;border-radius:8px;font-size:12px;cursor:pointer;display:flex;justify-content:space-between}
  .ctx-item:hover{background:var(--primary-container);color:var(--primary)}
  .ctx-item.danger{color:var(--danger)}

  .drop-mask{position:fixed;inset:0;background:rgba(14,22,38,.86);border:3px dashed var(--primary);display:none;align-items:center;justify-content:center;z-index:210;font-size:20px;font-weight:700;color:var(--primary)}
  .drop-mask.open{display:flex}
  .toast{position:fixed;top:48px;left:50%;transform:translateX(-50%);background:var(--high);border:1px solid var(--primary);color:var(--text);padding:8px 16px;border-radius:99px;font-size:12px;font-weight:600;z-index:220;display:none;box-shadow:0 8px 20px rgba(0,0,0,.7)}
  .toast.open{display:block}
</style>
</head>
<body>

<!-- Frameless Edge/Corner Resize Grips (Active inside Desktop App) -->
<div id="winEdges" style="display:none">
  <div class="win-edge win-n" data-edge="n"></div><div class="win-edge win-s" data-edge="s"></div>
  <div class="win-edge win-w" data-edge="w"></div><div class="win-edge win-e" data-edge="e"></div>
  <div class="win-edge win-nw" data-edge="nw"></div><div class="win-edge win-ne" data-edge="ne"></div>
  <div class="win-edge win-sw" data-edge="sw"></div><div class="win-edge win-se" data-edge="se"></div>
</div>

<div class="app-wrap">
  <!-- Flush Title Bar -->
  <header class="titlebar" id="titleBar">
    <div class="titlebar-drag pywebview-drag-region" id="dragRegion">
      <span class="brand-dot"></span>
      <span class="brand-name">DeckVault</span>
      <span class="title-pill" id="titleStatusPill">Connecting...</span>
    </div>
    <button class="mini-btn" onclick="openAllDrives()" title="All Drives & Devices">All Drives</button>
    <button class="mini-btn" onclick="openDevicesModal()" title="Pair Devices">Pair</button>
    <button class="mini-btn" onclick="openSettingsModal()" title="Settings">Settings</button>
    <div class="win-ctrls" id="winCtrls">
      <button class="wbtn" onclick="winAction('minimize')" title="Minimize">&#8212;</button>
      <button class="wbtn" onclick="winAction('toggle_max')" title="Maximize / Restore">&#9723;</button>
      <button class="wbtn close" onclick="winAction('close')" title="Close">&#10005;</button>
    </div>
  </header>

  <div class="shell">
    <!-- Slim Navigation Rail -->
    <nav class="rail" id="sideRail">
      <button class="rail-btn" onclick="togglePane()" title="Toggle Folder Panel">Folders</button>
      <button class="rail-btn active" id="railBrowse" onclick="showBrowseMode()">Browse</button>
      <button class="rail-btn" id="railDrives" onclick="openAllDrives()">Drives</button>
      <button class="rail-btn" id="railCloud" onclick="openCloudModal()" title="Google Drive & Connected Cloud Storage">Cloud</button>
      <button class="rail-btn" id="railHogs" onclick="loadSpaceHogs()">Hogs</button>
      <div class="rail-sep"></div>
      <div id="railHosts" style="display:flex;flex-direction:column;gap:5px;width:100%;align-items:center"></div>
      <div class="rail-spacer"></div>
      <div class="saved-pill" id="savedPill" style="display:none"><b>SAVED</b><span id="savedText">0 B</span></div>
      <button class="rail-btn" onclick="toggleSplitView()" title="Toggle Split View">Split</button>
      <button class="rail-btn" onclick="toggleInspector()" title="Toggle Details Inspector">Info</button>
      <button class="rail-btn" onclick="openDevicesModal()">Devices</button>
      <button class="rail-btn" onclick="openSettingsModal()">Settings</button>
    </nav>

    <!-- Adjustable Left Panel: Devices, Cloud Tabs, Drives, Quick Folders, Folder Tree -->
    <aside class="tree-pane" id="treePane">
      <div style="display:flex;align-items:center;gap:6px">
        <div class="host-box" onclick="openDevicesModal()">
          <span id="activeHostIcon" style="font-size:11px;font-weight:800;color:var(--primary)">PC</span>
          <div style="min-width:0;flex:1">
            <div class="host-title" id="activeHostTitle">Connecting...</div>
            <div class="host-sub" id="activeHostSub">Local & Wi-Fi</div>
          </div>
          <span style="color:var(--muted);font-size:11px">Swap</span>
        </div>
        <button class="icon-btn" onclick="openAllDrives()" title="All Devices & Drives">All</button>
        <button class="icon-btn" onclick="openSettingsModal()" title="Settings">Set</button>
      </div>

      <div class="chip-row" id="hostSwitchRow"></div>
      <div class="cloud-box" id="cloudStripSection">
        <div class="cloud-hdr">
          <span>CLOUD & STORAGE APPS</span>
          <button class="mini-btn" onclick="openCloudModal()">+ Connect</button>
        </div>
        <div class="chip-row" id="cloudStripRow"></div>
      </div>
      <div class="chip-row" id="favStripRow" style="display:none"></div>
      <div class="chip-row" id="drivesStripRow"></div>
      <div class="chip-row" id="quickRow"></div>

      <div style="display:flex;align-items:center;justify-content:space-between;font-size:12px">
        <button class="mini-btn" id="treeUpBtn" onclick="goUp()">↑ Above</button>
        <span id="treeCurInfo" style="color:var(--muted);font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:110px"></span>
        <div style="display:flex;gap:4px">
          <button class="mini-btn" id="treeSortBtn" onclick="toggleTreeSort()">Size</button>
          <button class="mini-btn" onclick="rescanActive()">Rescan</button>
        </div>
      </div>

      <div class="tree-scroll" id="treeList"></div>
    </aside>
    <div class="resizer" id="leftResizer"></div>

    <!-- Center Main Media Browser -->
    <main class="main-pane" id="mainPane">
      <div class="topbar">
        <button class="pill-btn" onclick="togglePane()">Folders</button>
        <button class="pill-btn" id="topUpBtn" onclick="goUp()" title="Go to Above Folder (Backspace)">&#8593; Above Folder</button>
        <button class="pill-btn" id="favCurBtn" onclick="toggleFavoriteCurrentFolder()" title="Pin folder to Cross-Device Favorites">☆ Favorite</button>
        <div class="crumbs" id="breadcrumbs"></div>
        <button class="pill-btn" id="selectModeBtn" onclick="toggleSelectMode()" title="Multi-Select Checkboxes">☑ Select</button>
        <button class="pill-btn" id="splitToggleBtn" onclick="toggleSplitView()" title="Open/Close Split View">◫ Split View</button>
        <button class="pill-btn" onclick="createFolderHere()" title="New Folder">+ Folder</button>
        <button class="pill-btn" onclick="toggleGridPopover()" title="Grid Columns & Card Size">Grid / Cards</button>
        <button class="pill-btn" id="viewBtn" onclick="toggleViewMode()">Grid</button>
        <select class="input" id="sortSelect" onchange="changeSort(this.value)" style="padding:5px 9px">
          <option value="size:desc">Size ↓</option>
          <option value="size:asc">Size ↑</option>
          <option value="name:asc">Name A-Z</option>
          <option value="name:desc">Name Z-A</option>
          <option value="date:desc">Newest</option>
          <option value="duration:desc">Longest</option>
          <option value="type:asc">Type</option>
        </select>
        <button class="pill-btn" id="inspBtn" onclick="toggleInspector()" title="Details Panel">Inspector</button>
        <label class="pill-btn primary" style="cursor:pointer">
          <input type="file" id="uploadInput" multiple style="display:none" onchange="uploadLocalFiles(this.files)"/>
          + Upload
        </label>
      </div>

      <div class="search-row">
        <input class="input" id="searchBox" placeholder="Search files... (Ctrl+F)" oninput="onSearchInput()" style="flex:1;min-width:170px"/>
        <button class="chip" id="recursiveChip" onclick="toggleRecursive()">Deep Search: Off</button>
        <div class="chip-row" id="filterRow" style="flex:0 1 auto"></div>
      </div>

      <div class="browser-scroll" id="browserArea"></div>

      <!-- Quick Grid / Card Customizer Popover -->
      <div class="popover" id="gridPopover">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <strong style="font-size:13px">Grid & Card Layout</strong>
          <button class="mini-btn" onclick="toggleGridPopover(false)">Close</button>
        </div>
        <div>
          <div style="display:flex;justify-content:space-between;font-size:12px"><span>Columns</span><b id="popColsLbl">Auto</b></div>
          <input type="range" id="popColsRange" min="0" max="8" step="1" value="0" oninput="setPref('gridCols', Number(this.value))" style="width:100%"/>
        </div>
        <div id="popMinWrap">
          <div style="display:flex;justify-content:space-between;font-size:12px"><span>Auto Card Width</span><b id="popWidthLbl">195 px</b></div>
          <input type="range" id="popWidthRange" min="130" max="340" step="10" value="195" oninput="setPref('cardMinPx', Number(this.value))" style="width:100%"/>
        </div>
        <div>
          <span style="font-size:11px;color:var(--muted)">Thumbnail Aspect</span>
          <div style="display:flex;gap:5px;flex-wrap:wrap;margin-top:4px" id="popAspectChips"></div>
        </div>
        <label style="display:flex;justify-content:space-between;font-size:12px"><span>Compact List Density</span><input type="checkbox" id="popCompactChk" onchange="setPref('compactList', this.checked)"/></label>
        <label style="display:flex;justify-content:space-between;font-size:12px"><span>Hover Video Storyboard Scrub</span><input type="checkbox" id="popHoverSbChk" onchange="setPref('hoverStoryboard', this.checked)"/></label>
      </div>

      <!-- Multi-Select Action Bar -->
      <div class="sel-bar" id="selBar" style="flex-wrap:wrap">
        <strong id="selCount" style="font-size:13px">0 selected</strong>
        <button class="mini-btn" onclick="selectAllFiles()">All</button>
        <button class="mini-btn" onclick="invertSelection()">Invert</button>
        <span style="flex:1"></span>
        <button class="pill-btn" onclick="openTransferModal(getSelectedFiles(), true)">Move To...</button>
        <button class="pill-btn" onclick="openTransferModal(getSelectedFiles(), false)">Copy To...</button>
        <button class="pill-btn" onmousedown="startNativeExternalDrag(event, Array.from(state.selected))" onclick="copyFilesToWindowsClipboard(state.activeHostId, Array.from(state.selected))" title="Drag onto Dropbox/Explorer or click to Copy to Windows Clipboard (Ctrl+C)">⤢ Drag / Copy App</button>
        <button class="pill-btn" onclick="openOptimizeSelected('compress')">Compress</button>
        <button class="pill-btn" onclick="openOptimizeSelected('upscale')">Upscale</button>
        <button class="pill-btn" onclick="backupSelectionToGDrive()">Google Drive</button>
        <button class="pill-btn danger" onclick="deletePaths(Array.from(state.selected), false)">♻ Recycle</button>
        <button class="pill-btn danger" onclick="deletePaths(Array.from(state.selected), true)">✕ Delete Forever</button>
        <button class="pill-btn" onclick="clearSelection()">Clear</button>
      </div>
    </main>

    <!-- Adjustable Split View Dual-Pane Browser -->
    <div class="resizer" id="splitResizer" style="display:none"></div>
    <section class="main-pane split-pane" id="splitPane" style="display:none">
      <div class="split-hdr">
        <strong style="font-size:12px;color:var(--primary)">◫ Split</strong>
        <select class="input" id="splitHostSelect" onchange="openInSplitView(this.value)" style="padding:4px 7px;font-size:11px;max-width:130px"></select>
        <button class="mini-btn" id="splitUpBtn" onclick="splitGoUp()">↑ Up</button>
        <button class="mini-btn" onclick="createSplitFolderHere()">+ Folder</button>
        <span style="flex:1"></span>
        <button class="mini-btn" onclick="transferMainToSplit(false)" title="Copy main-pane selected items here">Copy Here</button>
        <button class="mini-btn" onclick="transferMainToSplit(true)" title="Move main-pane selected items here">Move Here</button>
        <button class="mini-btn" onclick="closeSplitView()">✕</button>
      </div>
      <div class="crumbs" id="splitCrumbs" style="flex:0 0 auto"></div>
      <div class="browser-scroll" id="splitBrowserArea" style="padding-bottom:14px"></div>
    </section>

    <!-- Adjustable Right Details / Inspector Panel -->
    <div class="resizer" id="rightResizer"></div>
    <aside class="insp-pane" id="inspPane">
      <div style="display:flex;align-items:center;justify-content:space-between">
        <strong style="font-size:13px">Inspector</strong>
        <button class="mini-btn" onclick="toggleInspector(false)">Hide</button>
      </div>
      <div id="inspBody" style="font-size:12px;color:var(--muted)">Click or select a file to inspect codec, resolution, size, and quick edit tools.</div>
    </aside>
  </div>

  <!-- Subtle Status Footer -->
  <footer class="footer-bar">
    <span id="footerLeft">Ready</span>
    <span id="footerRight" style="cursor:pointer" onclick="openSettingsModal()">Wi-Fi Companion</span>
  </footer>
</div>

<!-- Full-Screen All Devices & Drives Dashboard -->
<div class="overlay" id="allDrivesOverlay">
  <div class="dialog-hdr" style="background:var(--panel)">
    <div>
      <strong style="font-size:18px">All Connected Devices, Cloud & Drives</strong>
      <div id="allDrivesSummary" style="font-size:12px;color:var(--muted)">Scanning devices...</div>
    </div>
    <div style="display:flex;gap:8px">
      <button class="pill-btn" onclick="refreshAllDrives()">Refresh</button>
      <button class="pill-btn" onclick="openCloudModal()">+ Connect Cloud / Storage</button>
      <button class="pill-btn primary" onclick="openDevicesModal()">+ Pair Device</button>
      <button class="pill-btn" onclick="closeOverlay('allDrivesOverlay')">Close</button>
    </div>
  </div>
  <div id="allDrivesBody" style="flex:1;overflow-y:auto;padding:18px;max-width:1200px;width:100%;margin:0 auto;display:flex;flex-direction:column;gap:14px"></div>
</div>

<!-- VLC-Rival Cinema Video Player & Cut/Crop Studio -->
<div class="overlay" id="videoOverlay">
  <div class="cinema-wrap" id="cinemaWrap" ondblclick="toggleCinemaFullscreen()">
    <div class="hud-top" id="vidHudTop">
      <div style="min-width:0;flex:1">
        <strong id="vidTitle" style="font-size:14px">Video</strong>
        <div id="vidMeta" style="font-size:11px;color:var(--muted)"></div>
      </div>
      <span class="mini-btn" id="vidCodecBadge" style="background:rgba(0,0,0,.65)">HD</span>
      <button class="pill-btn" id="vidVlcBtn" onclick="openInExternalPlayer()" title="Open in VLC / System Player">Open VLC</button>
      <button class="pill-btn" onclick="toggleCropOverlay(event)" id="cropTopBtn">Crop Box (C)</button>
      <button class="pill-btn" onclick="toggleCropPreview(event)" id="cropPreviewBtn" title="Preview cropped & cut area">Preview Crop (P)</button>
      <button class="pill-btn primary" onclick="toggleStudioDrawer()">Cut & Edit Studio</button>
      <button class="pill-btn" onclick="openOptimizeModal([state.activeFile],'compress')">Compress</button>
      <button class="pill-btn" onclick="openOptimizeModal([state.activeFile],'upscale')">Upscale</button>
      <button class="pill-btn" onclick="takeVideoSnapshot()" title="Save Frame PNG">Snapshot</button>
      <button class="pill-btn" onclick="openTransferModal([state.activeFile], true)">Move</button>
      <button class="pill-btn danger" onclick="deleteSingleOverlayFile(false)">♻ Recycle</button>
      <button class="pill-btn danger" onclick="deleteSingleOverlayFile(true)">✕ Del Forever</button>
      <button class="pill-btn" onclick="closeVideoOverlay()">Close (Esc)</button>
    </div>

    <div class="crop-preview-badge" id="cropPreviewBadge" onclick="toggleCropPreview(event)">
      <span>Previewing Cropped Output</span>
      <span style="text-decoration:underline">Edit Handles</span>
    </div>

    <div class="video-box" id="videoBox">
      <div class="video-stage" id="videoStage">
        <video id="vidEl" playsinline crossorigin="anonymous"></video>
      </div>
      <!-- Interactive Visual Crop Box Directly On Oriented Video -->
      <div class="crop-layer" id="cropLayer">
        <div class="crop-shade" id="csTop"></div>
        <div class="crop-shade" id="csBot"></div>
        <div class="crop-shade" id="csLeft"></div>
        <div class="crop-shade" id="csRight"></div>
        <div class="crop-rect" id="cropRect">
          <div class="crop-grid-v1"></div><div class="crop-grid-v2"></div>
          <div class="crop-grid-h1"></div><div class="crop-grid-h2"></div>
          <div class="crop-readout" id="cropReadout">Crop</div>
          <div class="ch ch-nw" data-h="nw"></div><div class="ch ch-n" data-h="n"></div><div class="ch ch-ne" data-h="ne"></div>
          <div class="ch ch-w" data-h="w"></div><div class="ch ch-e" data-h="e"></div>
          <div class="ch ch-sw" data-h="sw"></div><div class="ch ch-s" data-h="s"></div><div class="ch ch-se" data-h="se"></div>
        </div>
      </div>
    </div>
  </div>

  <!-- Custom VLC-Grade Transport HUD -->
  <div class="hud-bottom">
    <div class="timeline-wrap" id="vidTimeline">
      <div class="timeline-thumb-tip" id="vidTip">
        <div class="sb-tip-img" id="vidTipImg"></div>
        <span id="vidTipTime" style="font-size:10px;font-weight:700">0:00</span>
      </div>
      <div class="timeline-track">
        <div class="timeline-buf" id="vidBuf"></div>
        <div class="timeline-ab" id="vidAbBar"></div>
        <div class="timeline-fill" id="vidFill"></div>
      </div>
    </div>

    <div style="display:flex;align-items:center;gap:7px;flex-wrap:wrap">
      <button class="mini-btn" onclick="stepVideo(-1)">Prev</button>
      <button class="mini-btn" onclick="nudgeFrame(-1)" title="Previous Frame (,)">-1f</button>
      <button class="mini-btn" onclick="seekVideoRel(-prefs.seekStep)" id="seekBackBtn">-10s</button>
      <button class="pill-btn primary" id="playPauseBtn" onclick="togglePlay()">Play</button>
      <button class="mini-btn" onclick="seekVideoRel(prefs.seekStep)" id="seekFwdBtn">+10s</button>
      <button class="mini-btn" onclick="nudgeFrame(1)" title="Next Frame (.)">+1f</button>
      <button class="mini-btn" onclick="stepVideo(1)">Next</button>

      <span id="vidTimeCode" style="font-size:12px;font-family:Consolas,monospace;margin:0 4px">00:00.0 / 00:00.0</span>

      <span style="flex:1"></span>

      <button class="mini-btn" id="abLoopBtn" onclick="toggleAbLoop()">A-B Loop: Off</button>
      <button class="mini-btn" onclick="cycleRotate()" id="hudRotBtn" title="Rotate 90°">Rot 0°</button>
      <select class="input" id="vidAspectSelect" onchange="setVideoAspect(this.value)" style="padding:3px 7px;font-size:11px" title="Video Aspect">
        <option value="auto">Aspect: Auto</option>
        <option value="9-16">9:16 Vertical</option>
        <option value="16-9">16:9 Wide</option>
        <option value="4-3">4:3</option>
        <option value="1-1">1:1 Square</option>
      </select>
      <select class="input" id="vidFitSelect" onchange="setVideoFit(this.value)" style="padding:3px 7px;font-size:11px" title="Scale Mode">
        <option value="contain">Fit</option>
        <option value="cover">Fill (Zoom)</option>
        <option value="fill">Stretch</option>
      </select>
      <select class="input" id="vidSpeed" onchange="setVidSpeed(this.value)" style="padding:3px 7px;font-size:11px">
        <option value="0.25">0.25x</option><option value="0.5">0.5x</option><option value="0.75">0.75x</option>
        <option value="1" selected>1.0x</option><option value="1.25">1.25x</option><option value="1.5">1.5x</option>
        <option value="2">2.0x</option><option value="3">3.0x</option><option value="4">4.0x</option>
      </select>
      <button class="mini-btn" id="muteBtn" onclick="toggleMute()">Vol</button>
      <input type="range" id="volRange" min="0" max="200" step="5" value="100" oninput="setVolumePct(Number(this.value))" style="width:85px"/>
      <span id="volLbl" style="font-size:11px;width:36px">100%</span>
      <button class="mini-btn" onclick="toggleCinemaFullscreen()" title="Fullscreen (F)">Full</button>
    </div>
  </div>

  <!-- Cut & Visual Crop Studio Drawer -->
  <div class="studio-drawer" id="studioDrawer">
    <div class="sb-strip" id="sbStrip">
      <div class="sb-frames" id="sbFrames" onclick="seekFromStoryboard(event)">
        <div class="sb-row" id="sbCells"></div>
        <div class="trim-mask-l" id="trimMaskL"></div>
        <div class="trim-mask-r" id="trimMaskR"></div>
      </div>
      <div class="trim-window" id="trimActiveWin"></div>
      <div class="trim-playhead" id="trimPlayhead"></div>
      <div class="trim-handle" id="trimHandleL" data-trim="in" title="Drag Cut Start (In)">
        <span class="trim-handle-tag" id="trimTagL">0:00.0</span>
      </div>
      <div class="trim-handle" id="trimHandleR" data-trim="out" title="Drag Cut End (Out)">
        <span class="trim-handle-tag" id="trimTagR">0:00.0</span>
      </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
      <div>
        <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--muted)"><span>Cut Start (In)</span><b id="inLbl">0:00.0</b></div>
        <input type="range" id="inRange" min="0" max="100" step="0.05" value="0" style="width:100%" oninput="onTrimSlider()" onchange="autoPreviewCut()"/>
      </div>
      <div>
        <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--muted)"><span>Cut End (Out)</span><b id="outLbl">0:00.0</b></div>
        <input type="range" id="outRange" min="0" max="100" step="0.05" value="100" style="width:100%" oninput="onTrimSlider()" onchange="autoPreviewCut()"/>
      </div>
    </div>

    <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center">
      <button class="mini-btn" onclick="setTrimFromPlayhead(true)">Set In ([)</button>
      <button class="mini-btn" onclick="setTrimFromPlayhead(false)">Set Out (])</button>
      <button class="mini-btn" onclick="nudgeTrim(true,-0.5)">In -0.5s</button>
      <button class="mini-btn" onclick="nudgeTrim(true,0.5)">In +0.5s</button>
      <button class="mini-btn" onclick="nudgeTrim(false,-0.5)">Out -0.5s</button>
      <button class="mini-btn" onclick="nudgeTrim(false,0.5)">Out +0.5s</button>
      <button class="mini-btn" onclick="previewTrim()" style="border-color:var(--primary);color:var(--primary)">Preview Cut &amp; Crop</button>
      <span class="mini-btn" id="trimEstLbl">Full clip</span>
    </div>

    <div style="display:flex;gap:6px;flex-wrap:wrap;align-items:center;padding-top:4px;border-top:1px solid var(--border)">
      <strong style="font-size:11px;color:var(--primary)">Crop & Transform:</strong>
      <button class="mini-btn" id="cropToggleBtn" onclick="toggleCropOverlay(event)">Enable Crop Box</button>
      <button class="mini-btn" id="studioCropPreviewBtn" onclick="toggleCropPreview(event)">Preview Cropped (P)</button>
      <button class="mini-btn" onclick="applyCropPreset(null)">Free</button>
      <button class="mini-btn" onclick="applyCropPreset(16/9)">16:9</button>
      <button class="mini-btn" onclick="applyCropPreset(9/16)">9:16</button>
      <button class="mini-btn" onclick="applyCropPreset(1)">1:1</button>
      <button class="mini-btn" onclick="applyCropPreset(4/3)">4:3</button>
      <button class="mini-btn" onclick="resetCrop()">Reset Crop</button>
      <button class="mini-btn" onclick="cycleRotate()" id="rotBtn">Rot: 0°</button>
      <button class="mini-btn" onclick="toggleTransformFlag('flipH')" id="flipHBtn">Flip H</button>
      <button class="mini-btn" onclick="toggleTransformFlag('flipV')" id="flipVBtn">Flip V</button>
      <button class="mini-btn" onclick="toggleTransformFlag('muteOut')" id="muteOutBtn">Mute Audio: No</button>
    </div>

    <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center;padding-top:4px;border-top:1px solid var(--border)">
      <select class="input" id="editQualityMode" style="padding:4px 8px">
        <option value="lossless">Auto (Lossless Copy if no Crop/Transform)</option>
        <option value="compress">Compress / Re-encode H.264</option>
      </select>
      <label style="font-size:11px;color:var(--muted)">CRF <input type="number" id="editCrf" min="16" max="32" value="22" class="input" style="width:52px;padding:3px 6px"/></label>
      <select class="input" id="editSaveMode" style="padding:4px 8px">
        <option value="overwrite">Save Over Original</option>
        <option value="copy">Save as Copy (_edited.mp4)</option>
      </select>
      <label style="font-size:11px;color:var(--muted)"><input type="checkbox" id="editRecycle" checked/> Recycle Original</label>
      <span style="flex:1"></span>
      <button class="pill-btn primary" id="exportEditBtn" onclick="executeStudioEdit()">Render & Save Edit</button>
    </div>
  </div>
</div>

<!-- Image Lightbox Overlay -->
<div class="overlay" id="imageOverlay">
  <div class="dialog-hdr" style="background:var(--panel);padding:10px 16px">
    <strong id="imgTitle" style="flex:1">Image</strong>
    <div style="display:flex;gap:6px">
      <button class="pill-btn" onclick="stepImage(-1)">Prev</button>
      <button class="pill-btn" onclick="stepImage(1)">Next</button>
      <button class="pill-btn" onclick="openOptimizeModal([state.activeFile],'compress')">Compress</button>
      <button class="pill-btn" onclick="openOptimizeModal([state.activeFile],'upscale')">Upscale</button>
      <button class="pill-btn" onclick="openTransferModal([state.activeFile], true)">Move</button>
      <button class="pill-btn danger" onclick="deleteSingleOverlayFile(false)">♻ Recycle</button>
      <button class="pill-btn danger" onclick="deleteSingleOverlayFile(true)">✕ Del Forever</button>
      <button class="pill-btn" onclick="closeOverlay('imageOverlay')">Close</button>
    </div>
  </div>
  <div class="cinema-wrap"><img id="imgEl" style="max-width:100%;max-height:100%;object-fit:contain"/></div>
</div>

<!-- Universal Audio / PDF / Text / File Overlay -->
<div class="overlay" id="docOverlay">
  <div class="dialog-hdr" style="background:var(--panel);padding:10px 16px">
    <strong id="docTitle" style="flex:1">File</strong>
    <div style="display:flex;gap:6px">
      <a id="docDl" download><button class="pill-btn">Download</button></a>
      <button class="pill-btn" onclick="openTransferModal([state.activeFile], true)">Move</button>
      <button class="pill-btn danger" onclick="deleteSingleOverlayFile(false)">♻ Recycle</button>
      <button class="pill-btn danger" onclick="deleteSingleOverlayFile(true)">✕ Del Forever</button>
      <button class="pill-btn" onclick="closeDocOverlay()">Close</button>
    </div>
  </div>
  <div id="docBody" style="flex:1;overflow:auto;display:flex;align-items:center;justify-content:center;padding:16px"></div>
</div>

<!-- Cloud & Storage Apps Modal -->
<div class="dialog-bg" id="cloudModal">
  <div class="dialog">
    <div class="dialog-hdr">
      <div><strong>Cloud & Storage Apps</strong><div style="font-size:11px;color:var(--muted)">Browse Google Drive directly as a separate tab or connect Dropbox, OneDrive, iCloud, MEGA, or any sync/NAS folder</div></div>
      <button class="pill-btn" onclick="closeDialog('cloudModal')">Close</button>
    </div>
    <div class="dialog-body" id="cloudModalBody"></div>
  </div>
</div>

<!-- Built-In Compressor & Upscaler Studio Modal -->
<div class="dialog-bg" id="optimizeModal">
  <div class="dialog">
    <div class="dialog-hdr">
      <div><strong id="optTitle">Media Optimizer</strong><div id="optSub" style="font-size:11px;color:var(--muted)"></div></div>
      <div style="display:flex;gap:6px">
        <button class="chip active" id="optTabCmp" onclick="switchOptTab('compress')">Compressor</button>
        <button class="chip" id="optTabUp" onclick="switchOptTab('upscale')">Upscaler & Enhancer</button>
        <button class="pill-btn" onclick="closeDialog('optimizeModal')">Close</button>
      </div>
    </div>
    <div class="dialog-body" id="optBody"></div>
  </div>
</div>

<!-- Devices & PIN-Only Pairing Modal -->
<div class="dialog-bg" id="devicesModal">
  <div class="dialog">
    <div class="dialog-hdr">
      <div><strong>Devices & Wi-Fi Discovery</strong><div style="font-size:11px;color:var(--muted)">Tap Connect on any PC, Laptop, Fold, Tablet, or iPhone and enter ONLY its 6-digit PIN</div></div>
      <button class="pill-btn" onclick="closeDialog('devicesModal')">Close</button>
    </div>
    <div class="dialog-body" id="devicesBody"></div>
  </div>
</div>

<!-- Move / Copy Cross-Device Modal -->
<div class="dialog-bg" id="transferModal">
  <div class="dialog">
    <div class="dialog-hdr">
      <strong id="transferTitle">Move / Copy Files</strong>
      <button class="pill-btn" onclick="closeDialog('transferModal')">Close</button>
    </div>
    <div class="dialog-body">
      <div class="chip-row" id="transferHostChips"></div>
      <div style="display:flex;gap:8px;align-items:center">
        <button class="mini-btn" onclick="transferUp()">↑ Up</button>
        <span id="transferPathLbl" style="font-size:12px;color:var(--muted);flex:1;overflow:hidden;text-overflow:ellipsis"></span>
        <button class="mini-btn" onclick="createTransferSubfolder()">+ New Folder</button>
      </div>
      <div id="transferFoldersList" style="max-height:240px;overflow-y:auto;display:flex;flex-direction:column;gap:4px"></div>
      <div style="display:flex;justify-content:space-between;align-items:center;gap:8px">
        <div style="display:flex;gap:6px">
          <button class="chip" id="modeMoveChip" onclick="setTransferMove(true)">Move (Delete Source)</button>
          <button class="chip" id="modeCopyChip" onclick="setTransferMove(false)">Copy (Keep Source)</button>
        </div>
        <button class="pill-btn primary" onclick="executeTransfer()">Transfer Here</button>
      </div>
    </div>
  </div>
</div>

<!-- Comprehensive Settings Modal (All Android Settings + PC Controls) -->
<div class="dialog-bg" id="settingsModal">
  <div class="dialog">
    <div class="dialog-hdr"><strong>DeckVault Settings</strong><button class="pill-btn" onclick="closeDialog('settingsModal')">Close</button></div>
    <div class="dialog-body" id="settingsBody"></div>
  </div>
</div>

<!-- LAN Browser Login Modal -->
<div class="dialog-bg" id="lanLoginModal">
  <div class="dialog" style="max-width:390px">
    <div class="dialog-hdr"><strong>Unlock DeckVault</strong></div>
    <div class="dialog-body">
      <div style="font-size:12px;color:var(--muted)">Enter the 6-digit PIN shown on DeckVault Companion to connect from this browser.</div>
      <input class="input" id="lanPinInput" inputmode="numeric" maxlength="6" placeholder="6-digit PIN"/>
      <button class="pill-btn primary" onclick="pairLanBrowser()">Unlock</button>
      <a href="/DeckVault.apk" style="color:var(--primary);font-size:12px;text-align:center;text-decoration:none">Download Android APK (v1.2.0)</a>
      <div id="lanErr" style="color:var(--danger);font-size:12px"></div>
    </div>
  </div>
</div>

<div class="marquee-rect" id="marqueeRect"></div>
<div class="ctx-menu" id="ctxMenu"></div>
<div class="drop-mask" id="dropMask">Drop files here to upload to current folder</div>
<div class="toast" id="toast"></div>

<script>
const DEFAULT_PREFS = {
  pureBlack: true,
  palette: "",
  viewMode: localStorage.getItem("dv_view") || "gallery",
  sort: localStorage.getItem("dv_sort") || "size:desc",
  gridCols: 0, // 0 = auto responsive
  cardMinPx: 195,
  cardAspect: "16/10",
  compactList: false,
  showRail: true,
  showInspector: true,
  showDriveStrips: true,
  showQuickFolders: true,
  showSizes: true,
  showDuration: true,
  showResolution: true,
  showFilenames: true,
  hoverStoryboard: true,
  defaultSpeed: 1.0,
  seekStep: 10,
  defaultCrf: 22,
  recycleDefault: true,
  folderWidthPx: 300,
  inspectorWidthPx: 280,
};

let prefs = (() => {
  try {
    const saved = JSON.parse(localStorage.getItem("dv_prefs") || "{}");
    const merged = Object.assign({}, DEFAULT_PREFS, saved);
    if (localStorage.getItem("dv_pure_black") === "0" && saved.pureBlack === undefined) merged.pureBlack = false;
    if (localStorage.getItem("dv_pal") && !saved.palette) merged.palette = localStorage.getItem("dv_pal");
    return merged;
  } catch { return Object.assign({}, DEFAULT_PREFS); }
})();

function savePrefs(){ localStorage.setItem("dv_prefs", JSON.stringify(prefs)); }
function setPref(key, val){ prefs[key]=val; savePrefs(); applyVisualPrefs(); renderBrowser(); }

const state = {
  token: localStorage.getItem("dv_web_token") || "",
  localHostId: "",
  localDeviceName: "This PC",
  localPin: "",
  localIps: [],
  hosts: [],
  activeHostId: "",
  drives: [],
  quickFolders: [],
  currentPath: "",
  parentPath: "",
  treeSort: "size",
  filter: "all",
  recursive: false,
  spaceHogs: false,
  spaceSaved: 0,
  folders: [],
  files: [],
  selected: new Set(),
  selectMode: false,
  lastSelectedIdx: -1,
  focusedFile: null,
  discovered: [],
  activeFile: null,
  transferItems: [],
  transferMove: true,
  transferHostId: "",
  transferPath: "",
  transferParent: "",
  edit: { cropActive:false, cropPreview:false, x:0.1, y:0.1, w:0.8, h:0.8, rotate:0, flipH:false, flipV:false, muteOut:false },
  abLoop: false,
  isTranscode: false,
  transcodeOffsetSec: 0,
  vidDurationSec: 0,
  favorites: [],
  gdrive: null,
  cloudProviders: [],
  opt: { items:[], hostId:"", tab:"compress", est:null },
  split: { open:false, hostId:"", currentPath:"", parentPath:"", folders:[], files:[], widthPx:460 },
  audioCtx: null,
  gainNode: null,
  sbMetaMap: {},
};

function fmt(b){if(b==null||b<0)return"";const u=["B","KB","MB","GB","TB"];let i=0,v=b;while(v>=1024&&i<u.length-1){v/=1024;i++}return(i===0?Math.round(v):(v>=10?v.toFixed(1):v.toFixed(2)))+" "+u[i]}
function fmtDur(ms){if(!ms||ms<=0)return"";const s=Math.floor(ms/1000),h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sc=s%60;return h>0?`${h}:${String(m).padStart(2,'0')}:${String(sc).padStart(2,'0')}`:`${m}:${String(sc).padStart(2,'0')}`}
function fmtPrec(sec){if(!isFinite(sec)||sec<0)sec=0;const m=Math.floor(sec/60),r=(sec%60).toFixed(1).padStart(4,'0');return`${m}:${r}`}
function fmtRes(w,h){if(!w||!h)return"";const m=Math.min(w,h),x=Math.max(w,h);if(x>=3800||m>=2100)return"4K";if(x>=2500||m>=1400)return"1440p";if(x>=1900||m>=1050)return"1080p";if(x>=1250||m>=700)return"720p";return m+"p"}
function devBadge(os="",name=""){const o=(os||"").toLowerCase(),n=(name||"").toLowerCase();if(o.includes("ios")||n.includes("iphone"))return"iOS";if(o.includes("android")&&(n.includes("tab")||n.includes("pad")))return"TAB";if(o.includes("android"))return"PHN";if(n.includes("laptop")||n.includes("book"))return"LAP";return"PC"}
function showToast(m){const t=document.getElementById("toast");t.textContent=m;t.classList.add("open");clearTimeout(window._tt);window._tt=setTimeout(()=>t.classList.remove("open"),2800)}

/* Frameless Window Bridge */
function initDesktopChrome(){
  const isPyWebView = !!(window.pywebview);
  document.getElementById("winCtrls").classList.toggle("show", isPyWebView);
  document.getElementById("winEdges").style.display = isPyWebView ? "block" : "none";
  document.getElementById("dragRegion").ondblclick = () => { if(window.pywebview) winAction("toggle_max"); };
  if(isPyWebView){
    document.querySelectorAll(".win-edge").forEach(el=>{
      el.onmousedown = async (e) => {
        e.preventDefault();
        const edge = el.dataset.edge;
        const b = await window.pywebview.api.get_bounds();
        const sx = e.screenX, sy = e.screenY;
        const move = (ev) => {
          const dx = ev.screenX - sx, dy = ev.screenY - sy;
          let x=b.x, y=b.y, w=b.w, h=b.h;
          if(edge.includes("e")) w = b.w + dx;
          if(edge.includes("s")) h = b.h + dy;
          if(edge.includes("w")){ w = Math.max(760, b.w - dx); x = b.x + (b.w - w); }
          if(edge.includes("n")){ h = Math.max(520, b.h - dy); y = b.y + (b.h - h); }
          window.pywebview.api.set_bounds(x, y, w, h);
        };
        const up = () => { window.removeEventListener("mousemove", move); window.removeEventListener("mouseup", up); };
        window.addEventListener("mousemove", move);
        window.addEventListener("mouseup", up);
      };
    });
  }
}
window.addEventListener("pywebviewready", initDesktopChrome);
function winAction(fn){ if(window.pywebview?.api?.[fn]) window.pywebview.api[fn](); }

/* Apply Theme, Grid Size & Panel Adjustments */
function applyVisualPrefs(){
  document.body.className = (!prefs.pureBlack ? "soft " : "") + (prefs.palette || "");
  document.getElementById("sideRail").classList.toggle("hidden", !prefs.showRail);
  const insp = document.getElementById("inspPane");
  insp.classList.toggle("hidden", !prefs.showInspector);
  document.getElementById("rightResizer").classList.toggle("hidden", !prefs.showInspector);
  document.getElementById("treePane").style.width = prefs.folderWidthPx + "px";
  insp.style.width = prefs.inspectorWidthPx + "px";
  document.documentElement.style.setProperty("--grid-cols", prefs.gridCols > 0 ? prefs.gridCols : 4);
  document.documentElement.style.setProperty("--card-min", prefs.cardMinPx + "px");
  document.documentElement.style.setProperty("--card-aspect", prefs.cardAspect === "auto" ? "16/10" : prefs.cardAspect);
  document.getElementById("viewBtn").textContent = prefs.viewMode === "gallery" ? "Grid" : "List";
  document.getElementById("sortSelect").value = prefs.sort;
  document.getElementById("seekBackBtn").textContent = "-" + prefs.seekStep + "s";
  document.getElementById("seekFwdBtn").textContent = "+" + prefs.seekStep + "s";

  // Sync grid popover inputs if present
  document.getElementById("popColsRange").value = prefs.gridCols;
  document.getElementById("popColsLbl").textContent = prefs.gridCols === 0 ? "Auto" : prefs.gridCols + " / row";
  document.getElementById("popWidthRange").value = prefs.cardMinPx;
  document.getElementById("popWidthLbl").textContent = prefs.cardMinPx + " px";
  document.getElementById("popMinWrap").style.display = prefs.gridCols === 0 ? "block" : "none";
  document.getElementById("popCompactChk").checked = prefs.compactList;
  document.getElementById("popHoverSbChk").checked = prefs.hoverStoryboard;
  const ac = document.getElementById("popAspectChips");
  ac.innerHTML = "";
  [["16/10","16:10"],["16/9","16:9"],["4/3","4:3"],["1/1","1:1"],["auto","Auto"]].forEach(([v,l])=>{
    const b=document.createElement("button");
    b.className="mini-btn"+(prefs.cardAspect===v?" active":"");
    if(prefs.cardAspect===v){b.style.borderColor="var(--primary)";b.style.color="var(--primary)";}
    b.textContent=l; b.onclick=()=>setPref("cardAspect",v); ac.appendChild(b);
  });
}

async function sysFetch(url, opts={}){
  const h=Object.assign({}, opts.headers||{});
  if(state.token) h["X-DeckVault-Token"]=state.token;
  const r=await fetch(url, Object.assign({}, opts, {headers:h}));
  if(r.status===401){document.getElementById("lanLoginModal").classList.add("open");throw new Error("PIN required")}
  if(!r.ok) throw new Error(await r.text());
  return r.json();
}
function hostUrl(hostId, route){
  if(!hostId || hostId===state.localHostId) return route;
  return `/api/fleet/proxy?hostId=${encodeURIComponent(hostId)}&route=${encodeURIComponent(route)}`;
}
function hostMediaUrl(hostId, route, qsObj){
  const q = new URLSearchParams(Object.assign({}, qsObj, {token: state.token})).toString();
  if(!hostId || hostId===state.localHostId) return `${route}?${q}`;
  return `/api/fleet/proxy?hostId=${encodeURIComponent(hostId)}&route=${encodeURIComponent(route + "?" + q)}&token=${encodeURIComponent(state.token)}`;
}
async function hostApi(hostId, route, opts={}){ return sysFetch(hostUrl(hostId, route), opts); }

async function initApp(){
  initDesktopChrome();
  applyVisualPrefs();
  renderFilters();
  try{
    const bs = await fetch("/api/desktop/bootstrap");
    if(bs.ok){
      const d = await bs.json();
      state.token = d.token;
      state.localHostId = d.localHostId;
      state.localDeviceName = d.deviceName;
      state.localPin = d.pin;
      state.localIps = d.ips || [];
      state.favorites = d.favorites || [];
      state.gdrive = d.gdrive || null;
      state.cloudProviders = d.cloudProviders || [];
      buildHostList(d.fleetHosts || []);
      renderCloudSection();
      renderFavoritesStrip();
      await selectHost(state.hosts[0].hostId);
      return;
    }
  }catch{}
  if(state.token){
    try{
      const ping = await sysFetch("/api/ping");
      state.localHostId = ping.hostId; state.localDeviceName = ping.deviceName;
      state.hosts = [{hostId:ping.hostId, name:ping.deviceName, label:ping.deviceName, os:ping.os||"Windows", isLocal:true}];
      await selectHost(ping.hostId);
      return;
    }catch{}
  }
  document.getElementById("lanLoginModal").classList.add("open");
}

function buildHostList(fleet){
  const local = {hostId:state.localHostId, name:state.localDeviceName, label:state.localDeviceName + " (This PC)", os:"Windows", isLocal:true, ip:state.localIps[0]||"127.0.0.1", port:48720};
  state.hosts = [local, ...fleet.filter(h=>h.hostId!==state.localHostId)];
}

async function pairLanBrowser(){
  const pin=document.getElementById("lanPinInput").value.trim();
  const res=await fetch("/api/pair",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({pin,clientName:"Web Browser"})});
  if(!res.ok){document.getElementById("lanErr").textContent="Invalid PIN";return}
  const d=await res.json();
  state.token=d.token; localStorage.setItem("dv_web_token",state.token);
  document.getElementById("lanLoginModal").classList.remove("open");
  state.localHostId=d.hostId; state.localDeviceName=d.deviceName;
  state.hosts=[{hostId:d.hostId,name:d.deviceName,label:d.deviceName,os:d.os||"Windows",isLocal:true}];
  await selectHost(d.hostId);
}

function showTargetCtxMenu(ev, hostId, path, label, kind="folder", cloudId=""){
  ev.preventDefault(); ev.stopPropagation();
  const menu=document.getElementById("ctxMenu");
  menu.style.left=Math.min(window.innerWidth-220, ev.clientX)+"px";
  menu.style.top=Math.min(window.innerHeight-240, ev.clientY)+"px";
  const openAction = path
    ? (hostId===state.activeHostId ? `loadFolder(${JSON.stringify(path)})` : `selectHost(${JSON.stringify(hostId)},${JSON.stringify(path)})`)
    : `selectHost(${JSON.stringify(hostId)})`;
  let html = `<button class="ctx-item" onclick='hideCtx();${openAction}'>Open ${kind==="device"?"Device":(label||"Here")}</button>`;
  html += `<button class="ctx-item" onclick='hideCtx();openInSplitView(${JSON.stringify(hostId)},${JSON.stringify(path)})'>◫ Open in Split View</button>`;
  if(path){
    html += `<button class="ctx-item" onclick='hideCtx();toggleFavoriteCurrentFolder(${JSON.stringify(path)},${JSON.stringify(label||path)},${JSON.stringify(hostId)})'>★ Pin / Unpin Favorite</button>`;
  }
  if(state.selected.size>0 && path){
    html += `<button class="ctx-item" onclick='hideCtx();dropItemsInto(${JSON.stringify(hostId)},${JSON.stringify(path)},{hostId:state.activeHostId,paths:Array.from(state.selected)},false)'>Copy ${state.selected.size} Selected Here</button>`;
    html += `<button class="ctx-item" onclick='hideCtx();dropItemsInto(${JSON.stringify(hostId)},${JSON.stringify(path)},{hostId:state.activeHostId,paths:Array.from(state.selected)},true)'>Move ${state.selected.size} Selected Here</button>`;
  }
  if(kind==="cloud"){
    html += `<button class="ctx-item" onclick='hideCtx();openCloudModal()'>Manage Cloud Apps...</button>`;
    if(cloudId && cloudId.startsWith("cloud_")){
      html += `<button class="ctx-item danger" onclick='hideCtx();removeCloudProvider(${JSON.stringify(cloudId)})'>Disconnect Storage Tab</button>`;
    }
  }
  menu.innerHTML = html;
  menu.classList.add("open");
}

function wireDropTarget(el, destHostId, destPath){
  el.ondragover=(ev)=>{
    if(ev.dataTransfer?.types?.includes("application/x-deckvault-items")){
      ev.preventDefault(); ev.stopPropagation();
      el.classList.add("drop-target");
    }
  };
  el.ondragleave=()=>el.classList.remove("drop-target");
  el.ondrop=(ev)=>{
    el.classList.remove("drop-target");
    const raw=ev.dataTransfer?.getData("application/x-deckvault-items");
    if(!raw) return;
    ev.preventDefault(); ev.stopPropagation();
    try{
      const payload=JSON.parse(raw);
      dropItemsInto(destHostId, destPath, payload, !!ev.shiftKey);
    }catch{}
  };
}

async function dropItemsInto(destHostId, destPath, payload, move){
  const srcHostId=payload?.hostId || state.activeHostId;
  const paths=(payload?.paths || []).filter(Boolean);
  if(!paths.length || !destPath) return;
  try{
    if(srcHostId===destHostId){
      await hostApi(destHostId,"/api/file/transfer",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({paths,destinationDir:destPath,mode:move?"move":"copy",conflict:"rename"})});
    }else{
      await sysFetch("/api/fleet/relay",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceHostId:srcHostId,targetHostId:destHostId,paths,destinationDir:destPath,move})});
    }
    showToast((move?"Moved ":"Copied ")+paths.length+" item(s) -> "+destPath);
    clearSelection();
    if(state.currentPath) await loadFolder(state.currentPath);
    if(state.split.open && state.split.currentPath) await loadSplitFolder(state.split.currentPath);
  }catch(e){showToast("Transfer failed: "+e.message)}
}

function renderCloudSection(){
  const row=document.getElementById("cloudStripRow");
  if(!row) return;
  row.innerHTML="";
  const list=state.cloudProviders || [];
  const hasGdrive = list.some(c=>c.provider==="gdrive");
  if(!hasGdrive){
    const gd=document.createElement("button");
    gd.className="cloud-tab";
    gd.innerHTML=`<span>☁</span><span>Google Drive</span><span style="color:var(--muted);font-size:10px">Setup</span>`;
    gd.onclick=()=>openCloudModal();
    gd.oncontextmenu=(ev)=>showTargetCtxMenu(ev, state.localHostId, state.gdrive?.savedDir||"", "Google Drive", "cloud");
    row.appendChild(gd);
  }
  const icons={gdrive:"☁ Google Drive",onedrive:"☁ OneDrive",dropbox:"📦 Dropbox",icloud:"☁ iCloud",mega:"M MEGA",box:"📁 Box",nextcloud:"☁ Nextcloud"};
  list.forEach(c=>{
    const b=document.createElement("button");
    const active = state.activeHostId===state.localHostId && state.currentPath && c.path && state.currentPath.toLowerCase().startsWith(c.path.toLowerCase());
    b.className="cloud-tab"+(active?" active":"");
    const lbl=icons[c.provider] || ("☁ "+c.name);
    const freeStr = c.freeBytes>0 ? `<span style="color:var(--muted);font-size:10px">${fmt(c.freeBytes)}</span>` : "";
    b.innerHTML=`<span>${c.provider==="custom"?"🗂 "+c.name:lbl}</span>${freeStr}`;
    b.title=c.path;
    b.onclick=()=>{
      if(state.activeHostId===state.localHostId) loadFolder(c.path);
      else selectHost(state.localHostId, c.path);
    };
    b.oncontextmenu=(ev)=>showTargetCtxMenu(ev, state.localHostId, c.path, c.name, "cloud", c.id);
    wireDropTarget(b, state.localHostId, c.path);
    row.appendChild(b);
  });
}

async function openCloudModal(){
  try{
    const [gd, cl] = await Promise.all([sysFetch("/api/gdrive/status"), sysFetch("/api/cloud/list")]);
    state.gdrive = gd; state.cloudProviders = cl.cloudProviders || []; renderCloudSection();
  }catch{}
  const body=document.getElementById("cloudModalBody");
  const curList=state.cloudProviders || [];
  body.innerHTML=`
    <div class="section-card">
      <strong style="font-size:13px">Detected & Connected Cloud Drives</strong>
      ${!curList.length ? `<div style="font-size:12px;color:var(--muted)">No synced cloud drive folders detected automatically yet.</div>` : curList.map(c=>`
        <div class="list-row" style="cursor:default">
          <strong style="font-size:12px;min-width:110px">${c.name}</strong>
          <span style="font-size:11px;color:var(--muted);flex:1;word-break:break-all">${c.path} ${c.freeBytes>0?"("+fmt(c.freeBytes)+" free)":""}</span>
          <button class="mini-btn" onclick="closeDialog('cloudModal');selectHost(state.localHostId,${JSON.stringify(c.path)})">Open</button>
          <button class="mini-btn" onclick="closeDialog('cloudModal');openInSplitView(state.localHostId,${JSON.stringify(c.path)})">Split View</button>
          ${c.custom?`<button class="mini-btn" onclick="removeCloudProvider(${JSON.stringify(c.id)})">Remove</button>`:""}
        </div>
      `).join("")}
    </div>
    <div class="section-card">
      <strong style="font-size:13px">Connect Dropbox, OneDrive, Google Drive, iCloud, MEGA or Network/Storage Folder</strong>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <select class="input" id="cloudProvType" style="padding:6px 9px">
          <option value="gdrive">Google Drive</option>
          <option value="dropbox">Dropbox</option>
          <option value="onedrive">OneDrive</option>
          <option value="icloud">iCloud Drive</option>
          <option value="mega">MEGA</option>
          <option value="box">Box</option>
          <option value="nextcloud">Nextcloud</option>
          <option value="custom">Custom / NAS / External</option>
        </select>
        <input class="input" id="cloudNameIn" placeholder="Tab Name (e.g. Dropbox)" style="width:150px"/>
        <input class="input" id="cloudPathIn" placeholder="Local or mounted path (e.g. C:\\Users\\...\\Dropbox or I:\\My Drive)" style="flex:1;min-width:220px"/>
        <button class="pill-btn primary" onclick="connectCloudProvider()">+ Connect</button>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;font-size:11px;color:var(--muted)">
        <a href="https://drive.google.com/drive/my-drive" target="_blank" style="color:var(--primary)">Open Google Drive Web</a>
        <span>•</span>
        <a href="https://www.dropbox.com/home" target="_blank" style="color:var(--primary)">Open Dropbox Web</a>
        <span>•</span>
        <a href="https://onedrive.live.com/" target="_blank" style="color:var(--primary)">Open OneDrive Web</a>
        <span>•</span>
        <a href="https://www.icloud.com/iclouddrive" target="_blank" style="color:var(--primary)">Open iCloud Web</a>
      </div>
    </div>
    <div class="section-card">
      <strong style="font-size:13px">Google Drive Quick Backup Target</strong>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <input class="input" id="gdDirModalIn" placeholder="e.g. I:\\My Drive\\DeckVault Backup" value="${(state.gdrive?.savedDir||"").replace(/"/g,'&quot;')}" style="flex:1;min-width:200px"/>
        <button class="pill-btn primary" onclick="saveGDriveFromCloud()">Save Backup Folder</button>
      </div>
    </div>
  `;
  document.getElementById("cloudModal").classList.add("open");
}

async function connectCloudProvider(){
  const provider=document.getElementById("cloudProvType").value;
  const name=document.getElementById("cloudNameIn").value.trim() || document.getElementById("cloudProvType").selectedOptions[0].text;
  const path=document.getElementById("cloudPathIn").value.trim();
  if(!path){ showToast("Enter a folder or drive path"); return; }
  try{
    const res=await sysFetch("/api/cloud/connect",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({provider,name,path})});
    state.cloudProviders=res.cloudProviders||[];
    renderCloudSection();
    await openCloudModal();
    showToast("Connected "+name);
  }catch(e){showToast("Could not connect: "+e.message)}
}

async function removeCloudProvider(id){
  try{
    const res=await sysFetch("/api/cloud/remove",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({id})});
    state.cloudProviders=res.cloudProviders||[];
    renderCloudSection();
    if(document.getElementById("cloudModal").classList.contains("open")) await openCloudModal();
    showToast("Disconnected storage tab");
  }catch(e){showToast(e.message)}
}

async function saveGDriveFromCloud(){
  const savedDir=document.getElementById("gdDirModalIn").value.trim();
  state.gdrive=await sysFetch("/api/gdrive/configure",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({savedDir})});
  showToast("Google Drive backup folder saved");
}

function renderHostBars(){
  const rail = document.getElementById("railHosts"), sw = document.getElementById("hostSwitchRow");
  rail.innerHTML=""; sw.innerHTML="";
  state.hosts.forEach(h=>{
    const active = h.hostId===state.activeHostId, lbl = h.label || h.name;
    const b=document.createElement("button");
    b.className="rail-btn"+(active?" active":""); b.textContent=lbl.slice(0,8); b.title=lbl+" (Right-click for Split View)";
    b.onclick=()=>selectHost(h.hostId);
    b.oncontextmenu=(ev)=>showTargetCtxMenu(ev, h.hostId, "", lbl, "device");
    rail.appendChild(b);

    const c=document.createElement("button");
    c.className="chip"+(active?" active":""); c.innerHTML=`<span class="dot"></span>${lbl}`;
    c.title="Left-click to open • Right-click for Split View";
    c.onclick=()=>selectHost(h.hostId);
    c.oncontextmenu=(ev)=>showTargetCtxMenu(ev, h.hostId, "", lbl, "device");
    sw.appendChild(c);
  });
  const cur = state.hosts.find(h=>h.hostId===state.activeHostId) || state.hosts[0];
  if(cur){
    document.getElementById("activeHostIcon").textContent=devBadge(cur.os, cur.label||cur.name);
    document.getElementById("activeHostTitle").textContent=cur.label||cur.name;
    document.getElementById("activeHostSub").textContent=`${cur.os||"Device"} • ${cur.ip||"LAN"}`;
    document.getElementById("titleStatusPill").textContent=`${cur.label||cur.name} • ${state.hosts.length} Device(s)`;
  }
}

async function selectHost(hostId, targetPath=""){
  state.activeHostId = hostId; state.spaceHogs = false; clearSelection(); renderHostBars();
  try{
    const r = await hostApi(hostId, "/api/roots");
    state.drives = r.drives || []; state.quickFolders = r.quickFolders || []; state.spaceSaved = r.spaceSavedBytes || 0;
    if(hostId === state.localHostId && r.cloudProviders){ state.cloudProviders = r.cloudProviders; renderCloudSection(); }
    updateSavedBadge(); renderDriveAndQuickStrips();
    const first = targetPath || (state.quickFolders.find(q=>q.name==="Videos")?.path) || (state.quickFolders[0]?.path) || (state.drives[0]?.path) || "";
    if(first) await loadFolder(first);
  }catch(e){showToast("Could not reach device: "+e.message)}
}

function updateSavedBadge(){
  const p=document.getElementById("savedPill");
  if(state.spaceSaved>0){p.style.display="";document.getElementById("savedText").textContent=fmt(state.spaceSaved)}
  else p.style.display="none";
}

function renderDriveAndQuickStrips(){
  const drRow=document.getElementById("drivesStripRow"); drRow.innerHTML="";
  drRow.style.display = prefs.showDriveStrips ? "flex" : "none";
  if(prefs.showDriveStrips) state.drives.forEach(d=>{
    const total=Math.max(1,d.totalBytes||1), used=Math.max(0,total-(d.freeBytes||0)), pct=Math.min(100,Math.round(used*100/total));
    const el=document.createElement("div");
    el.className="drive-strip"+(state.currentPath.startsWith(d.path)?" active":"");
    el.innerHTML=`<div style="display:flex;justify-content:space-between;font-size:11px;font-weight:700"><span>${d.name}</span><span style="color:var(--primary)">${fmt(d.freeBytes)}</span></div><div class="bar"><div style="width:${pct}%"></div></div><div style="font-size:10px;color:var(--muted)">${fmt(total)} total</div>`;
    el.title="Click to open • Right-click for Split View";
    el.onclick=()=>loadFolder(d.path);
    el.oncontextmenu=(ev)=>showTargetCtxMenu(ev, state.activeHostId, d.path, d.name, "drive");
    wireDropTarget(el, state.activeHostId, d.path);
    drRow.appendChild(el);
  });
  const qRow=document.getElementById("quickRow"); qRow.innerHTML="";
  qRow.style.display = prefs.showQuickFolders ? "flex" : "none";
  if(prefs.showQuickFolders) state.quickFolders.forEach(q=>{
    const c=document.createElement("button");
    c.className="chip"+(state.currentPath===q.path?" active":""); c.textContent=q.name;
    c.onclick=()=>loadFolder(q.path);
    c.oncontextmenu=(ev)=>showTargetCtxMenu(ev, state.activeHostId, q.path, q.name, "folder");
    wireDropTarget(c, state.activeHostId, q.path);
    qRow.appendChild(c);
  });
  renderFavoritesStrip();
  renderCloudSection();
}

function renderFavoritesStrip(){
  const row=document.getElementById("favStripRow");
  if(!row) return;
  row.innerHTML="";
  if(!state.favorites || !state.favorites.length){ row.style.display="none"; return; }
  row.style.display="flex";
  state.favorites.forEach(fav=>{
    const b=document.createElement("button");
    const active = fav.hostId===state.activeHostId && fav.path===state.currentPath;
    b.className="chip"+(active?" active":"");
    b.title=`${fav.hostName}: ${fav.path} (Right-click for Split View)`;
    b.textContent=`★ ${fav.name} (${fav.hostName.split(" ")[0]})`;
    b.onclick=()=>{
      if(fav.hostId===state.activeHostId) loadFolder(fav.path);
      else selectHost(fav.hostId, fav.path);
    };
    b.oncontextmenu=(ev)=>showTargetCtxMenu(ev, fav.hostId, fav.path, fav.name, "folder");
    wireDropTarget(b, fav.hostId, fav.path);
    row.appendChild(b);
  });
}

function updateFavButtonState(){
  const btn=document.getElementById("favCurBtn");
  if(!btn) return;
  const isFav = state.favorites.some(x=>x.hostId===state.activeHostId && x.path===state.currentPath);
  btn.textContent = isFav ? "★ Favorited" : "☆ Favorite";
  btn.classList.toggle("primary", isFav);
}

async function toggleFavoriteCurrentFolder(pathOverride="", nameOverride="", hostOverride=""){
  const targetPath = pathOverride || state.currentPath;
  if(!targetPath) return;
  const hid = hostOverride || state.activeHostId;
  const curHost = state.hosts.find(h=>h.hostId===hid) || {hostId:hid, name:"Device", os:"Device"};
  try{
    const res = await sysFetch("/api/favorites/toggle", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({hostId:curHost.hostId, hostName:curHost.label||curHost.name, os:curHost.os, path:targetPath, name:nameOverride})
    });
    state.favorites = res.favorites || [];
    updateFavButtonState(); renderFavoritesStrip();
    showToast(res.added ? "Added to Favorites" : "Removed from Favorites");
  }catch(e){showToast("Favorite failed: "+e.message)}
}

function renderBreadcrumbs(pathStr, label=""){
  const el=document.getElementById("breadcrumbs"); el.innerHTML="";
  document.getElementById("topUpBtn").disabled = !state.parentPath;
  document.getElementById("treeUpBtn").disabled = !state.parentPath;
  if(label){ el.innerHTML=`<span class="crumb last">${label}</span>`; return; }
  const sep = pathStr.includes("\\") ? "\\" : "/";
  const rawParts = pathStr.split(/[\\/]+/).filter(Boolean);
  let acc = sep === "/" ? "/" : "";
  rawParts.forEach((seg, idx)=>{
    if(sep==="\\") acc = idx===0 ? seg+"\\" : acc + (acc.endsWith("\\")?"":"\\") + seg;
    else acc = acc==="/" ? "/"+seg : acc+"/"+seg;
    const target = acc;
    const b=document.createElement("button");
    b.className="crumb"+(idx===rawParts.length-1?" last":"");
    b.textContent=seg;
    b.onclick=()=>loadFolder(target);
    el.appendChild(b);
  });
}

async function loadFolder(path){
  state.currentPath=path; state.spaceHogs=false; clearSelection(); renderDriveAndQuickStrips();
  const [sortBy, order] = prefs.sort.split(":");
  const q = document.getElementById("searchBox").value.trim();
  const qs = new URLSearchParams({path, sort:sortBy, order, filter:state.filter});
  if(q) qs.set("q", q);
  if(state.recursive && q){
    const d = await hostApi(state.activeHostId, "/api/search?" + new URLSearchParams({path, q}).toString());
    state.folders=[]; state.files=d.files||[]; state.parentPath="";
    renderBreadcrumbs(path, `Deep Search: "${q}"`);
    updateFooter(`${state.files.length} matches`);
  }else{
    const d = await hostApi(state.activeHostId, "/api/list?" + qs.toString());
    state.folders=d.folders||[]; state.files=d.files||[]; state.parentPath=d.parentPath||"";
    renderBreadcrumbs(d.path||path);
    updateFooter(`${state.folders.length} folders • ${state.files.length} files • ${fmt(d.totalSizeBytes)}`);
  }
  loadTree(path);
  renderBrowser();
  updateFavButtonState();
}

function updateFooter(leftTxt){
  document.getElementById("footerLeft").textContent = leftTxt;
  const ip = state.localIps[0] || "127.0.0.1";
  document.getElementById("footerRight").textContent = `Sharing http://${ip}:48720  •  PIN: ${state.localPin||"—"}`;
}

async function loadTree(path){
  try{
    const t = await hostApi(state.activeHostId, "/api/tree?" + new URLSearchParams({path, sort:state.treeSort}).toString());
    const box=document.getElementById("treeList"); box.innerHTML="";
    document.getElementById("treeCurInfo").textContent=state.parentPath ? "Subfolder" : "Drive Root";
    (t.children||[]).forEach(ch=>{
      const row=document.createElement("button"); row.className="tree-row";
      const pct=Math.max(2, Math.min(100, Math.round(ch.percentOfParent||0)));
      row.innerHTML=`<div class="tree-row-top"><span style="color:var(--primary)">📁</span><span class="tree-row-name">${ch.name}</span><span style="color:var(--muted);font-size:11px">${fmt(ch.sizeBytes)}</span></div><div class="bar" style="height:3px;margin:0"><div style="width:${pct}%"></div></div>`;
      row.onclick=()=>loadFolder(ch.path);
      row.oncontextmenu=(ev)=>showCtxMenu(ev, {name:ch.name, path:ch.path, isDir:true}, state.activeHostId);
      wireDropTarget(row, state.activeHostId, ch.path);
      box.appendChild(row);
    });
  }catch{}
}

function renderFilters(){
  const items=[["all","All"],["video","Video"],["image","Images"],["audio","Audio"],["large","100MB+"]];
  const r=document.getElementById("filterRow"); r.innerHTML="";
  items.forEach(([k,lbl])=>{
    const b=document.createElement("button");
    b.className="chip"+(state.filter===k?" active":""); b.textContent=lbl;
    b.onclick=()=>{state.filter=k;renderFilters();if(state.currentPath)loadFolder(state.currentPath)};
    r.appendChild(b);
  });
}

function renderBrowser(){
  const area=document.getElementById("browserArea"); area.innerHTML="";
  document.body.classList.toggle("select-mode", !!state.selectMode);
  document.body.classList.toggle("has-selection", state.selected.size>0);
  if(state.folders.length>0 && !state.spaceHogs){
    const fStrip=document.createElement("div"); fStrip.className="chip-row"; fStrip.style.marginBottom="9px";
    state.folders.forEach(f=>{
      const c=document.createElement("button"); c.className="chip"; c.textContent=`📁 ${f.name} (${fmt(f.sizeBytes)})`;
      c.onclick=()=>loadFolder(f.path);
      c.oncontextmenu=(ev)=>showCtxMenu(ev, {name:f.name, path:f.path, isDir:true}, state.activeHostId);
      wireDropTarget(c, state.activeHostId, f.path);
      fStrip.appendChild(c);
    });
    area.appendChild(fStrip);
  }
  if(!state.files.length){
    area.innerHTML += `<div style="padding:48px;text-align:center;color:var(--muted)">No files match here</div>`;
    return;
  }
  const wrap=document.createElement("div");
  if(prefs.viewMode==="gallery"){
    wrap.className = prefs.gridCols === 0 ? "grid auto" : "grid";
  }else{
    wrap.className = prefs.compactList ? "list-col compact" : "list-col";
  }

  state.files.forEach((f, idx)=>{
    const sel = state.selected.has(f.path), foc = state.focusedFile?.path===f.path;
    const thumbUrl = f.mediaType==="video"
      ? hostMediaUrl(state.activeHostId, "/api/video/thumbnail", {path:f.path, w:480})
      : (f.mediaType==="image" ? hostMediaUrl(state.activeHostId, "/api/image", {path:f.path, w:480}) : "");
    const sbUrl = (f.mediaType==="video" && prefs.hoverStoryboard)
      ? hostMediaUrl(state.activeHostId, "/api/video/storyboard", {path:f.path, cols:4, rows:4}) : "";

    const el=document.createElement("div");
    el.className=(prefs.viewMode==="gallery"?"media-card":"list-row")+(sel?" selected":"")+(foc?" focused":"");
    el.dataset.path = f.path;
    el.draggable = true;

    if(prefs.viewMode==="gallery"){
      const thHtml = thumbUrl ? `<img loading="lazy" src="${thumbUrl}" draggable="false"/>` : `<span style="color:var(--primary);font-weight:700;font-size:13px">${(f.extension||f.mediaType||"FILE").toUpperCase()}</span>`;
      const dur = (prefs.showDuration && f.meta?.durationMs) ? `<span class="badge">${fmtDur(f.meta.durationMs)}</span>` : "";
      const resTxt = prefs.showResolution ? fmtRes(f.meta?.width, f.meta?.height) : "";
      const resBadge = resTxt ? `<span class="badge tl">${resTxt}</span>` : "";
      const sbDiv = sbUrl ? `<div class="sb-hover"></div>` : "";
      el.innerHTML=`<div class="sel-check" data-act="sel">${sel?"✓":""}</div><div class="thumb-wrap">${thHtml}${sbDiv}${resBadge}${dur}</div><div class="card-body">${prefs.showFilenames?`<div class="card-title" title="${f.name}">${f.name}</div>`:""}<div class="card-meta"><span>${prefs.showSizes?fmt(f.sizeBytes):""}</span><span>${(f.extension||"").toUpperCase()}</span></div><div class="card-actions"><button class="mini-btn" data-act="sel">${sel?"✓":"Select"}</button>${f.mediaType==="video"?`<button class="mini-btn" data-act="trim">Edit</button>`:""}<button class="mini-btn" data-act="move">Move</button><button class="mini-btn" data-act="more">•••</button></div></div>`;
      if(sbUrl){
        const tw = el.querySelector(".thumb-wrap"), sbh = el.querySelector(".sb-hover");
        tw.onmousemove = (mv) => {
          const rect = tw.getBoundingClientRect();
          const frac = Math.max(0, Math.min(0.999, (mv.clientX - rect.left) / rect.width));
          const sidx = Math.floor(frac * 16), col = sidx % 4, row = Math.floor(sidx / 4);
          sbh.style.display = "block";
          sbh.style.backgroundImage = `url("${sbUrl}")`;
          sbh.style.backgroundSize = "400% 400%";
          sbh.style.backgroundPosition = `${col*(100/3)}% ${row*(100/3)}%`;
        };
        tw.onmouseleave = () => { sbh.style.display = "none"; };
      }
    }else{
      const thHtml = thumbUrl ? `<img class="list-thumb" loading="lazy" src="${thumbUrl}" draggable="false"/>` : `<div class="list-thumb">${(f.extension||"FILE").toUpperCase()}</div>`;
      el.innerHTML=`<div class="sel-check" data-act="sel">${sel?"✓":""}</div>${thHtml}<div style="flex:1;min-width:0"><div class="card-title">${f.name}</div><div style="font-size:11px;color:var(--muted)">${fmt(f.sizeBytes)} ${f.meta?.durationMs?"• "+fmtDur(f.meta.durationMs):""} ${f.meta?.height?"• "+fmtRes(f.meta.width,f.meta.height):""}</div></div><button class="mini-btn" data-act="sel">${sel?"✓ Selected":"Select"}</button>${f.mediaType==="video"?`<button class="mini-btn" data-act="trim">Edit</button>`:""}<button class="mini-btn" data-act="move">Move</button><button class="mini-btn" data-act="more">•••</button>`;
    }

    el.ondragstart=(ev)=>{
      const dragPaths = state.selected.has(f.path) ? Array.from(state.selected) : [f.path];
      if(ev.dataTransfer){
        ev.dataTransfer.effectAllowed = "copyMove";
        ev.dataTransfer.setData("application/x-deckvault-items", JSON.stringify({hostId:state.activeHostId, paths:dragPaths}));
        const absUrl = new URL(hostMediaUrl(state.activeHostId,"/api/stream",{path:f.path}), window.location.origin).href;
        ev.dataTransfer.setData("DownloadURL", `application/octet-stream:${f.name}:${absUrl}`);
      }
    };
    el.onclick=(ev)=>{
      setFocusedFile(f);
      const act = ev.target?.dataset?.act;
      if(act==="sel"){ev.stopPropagation();handleSelectClick(idx, f.path, ev.shiftKey);return}
      if(act==="trim"){ev.stopPropagation();openFileViewer(f, true);return}
      if(act==="move"){ev.stopPropagation();openTransferModal([f], true);return}
      if(act==="more"){ev.stopPropagation();showCtxMenu(ev, f, state.activeHostId);return}
      if(ev.shiftKey){ handleSelectClick(idx, f.path, true); return; }
      if(state.selectMode || state.selected.size>0 || ev.ctrlKey || ev.metaKey){
        handleSelectClick(idx, f.path, false);
      }
    };
    el.ondblclick=(ev)=>{ if(!ev.target?.dataset?.act && !state.selectMode) openFileViewer(f, false); };
    el.oncontextmenu=(ev)=>showCtxMenu(ev, f, state.activeHostId);
    wrap.appendChild(el);
  });
  area.appendChild(wrap);
}

/* Marquee Rubber-Band Drag Selection on Empty Area */
(()=>{
  const area=document.getElementById("browserArea"), box=document.getElementById("marqueeRect");
  if(!area || !box) return;
  area.addEventListener("mousedown",(ev)=>{
    if(ev.button!==0 || ev.target.closest(".media-card,.list-row,.chip,button")) return;
    const sx=ev.clientX, sy=ev.clientY;
    let moved=false;
    const baseSel = (ev.ctrlKey||ev.shiftKey) ? new Set(state.selected) : new Set();
    const mm=(m)=>{
      const dx=m.clientX-sx, dy=m.clientY-sy;
      if(!moved && Math.hypot(dx,dy)<6) return;
      moved=true;
      const l=Math.min(sx,m.clientX), t=Math.min(sy,m.clientY), w=Math.abs(dx), h=Math.abs(dy);
      box.style.cssText=`display:block;left:${l}px;top:${t}px;width:${w}px;height:${h}px`;
      const mr={left:l,top:t,right:l+w,bottom:t+h};
      const next=new Set(baseSel);
      area.querySelectorAll(".media-card[data-path],.list-row[data-path]").forEach(card=>{
        const cr=card.getBoundingClientRect();
        const hit = !(cr.right<mr.left || cr.left>mr.right || cr.bottom<mr.top || cr.top>mr.bottom);
        if(hit) next.add(card.dataset.path);
      });
      state.selected = next;
      area.querySelectorAll(".media-card[data-path],.list-row[data-path]").forEach(card=>{
        const s=next.has(card.dataset.path);
        card.classList.toggle("selected", s);
        const ck=card.querySelector(".sel-check");
        if(ck) ck.textContent = s ? "✓" : "";
      });
      updateSelBar();
    };
    const mu=()=>{
      box.style.display="none";
      window.removeEventListener("mousemove",mm);
      window.removeEventListener("mouseup",mu);
    };
    window.addEventListener("mousemove",mm);
    window.addEventListener("mouseup",mu);
  });
})();

function setFocusedFile(f){
  state.focusedFile = f;
  renderInspector();
}
function renderInspector(){
  const box=document.getElementById("inspBody");
  const f = state.focusedFile;
  if(!f){ box.innerHTML=`<div style="color:var(--muted)">Single-click a file to inspect metadata, drag out to another app, or double-click to play / edit.</div>`; return; }
  const m = f.meta || {};
  const th = f.mediaType==="video"
    ? `<img src="${hostMediaUrl(state.activeHostId,"/api/video/thumbnail",{path:f.path,w:420})}" style="width:100%;border-radius:11px;background:#000"/>`
    : (f.mediaType==="image" ? `<img src="${hostMediaUrl(state.activeHostId,"/api/image",{path:f.path,w:420})}" style="width:100%;border-radius:11px;background:#000"/>` : "");
  const canOpt = f.mediaType==="video" || f.mediaType==="image";
  box.innerHTML = `
    ${th}
    <strong style="font-size:13px;word-break:break-word">${f.name}</strong>
    <div style="display:flex;gap:5px;flex-wrap:wrap">
      <button class="pill-btn primary" onclick='openFileViewer(state.focusedFile,false)'>Open / Play</button>
      ${f.mediaType==="video"?`<button class="pill-btn" onclick='openFileViewer(state.focusedFile,true)'>Cut / Crop</button>`:""}
      ${canOpt?`<button class="mini-btn" onclick='openOptimizeModal([state.focusedFile],"compress")'>Compress</button>`:""}
      ${canOpt?`<button class="mini-btn" onclick='openOptimizeModal([state.focusedFile],"upscale")'>Upscale</button>`:""}
      <button class="mini-btn" onclick='openTransferModal([state.focusedFile],true)'>Move</button>
      <button class="mini-btn" onclick='openTransferModal([state.focusedFile],false)'>Copy</button>
      <button class="mini-btn" onmousedown='startNativeExternalDrag(event,[state.focusedFile.path])' onclick='copyFilesToWindowsClipboard(state.activeHostId,[state.focusedFile.path])'>⤢ Drag / App</button>
      <button class="mini-btn" onclick='backupPathsToGDrive([state.focusedFile.path])'>G-Drive</button>
      <button class="mini-btn" onclick='renamePrompt(state.focusedFile)'>Rename</button>
      <button class="mini-btn" style="color:var(--danger)" onclick='deletePaths([state.focusedFile.path],false)'>♻ Recycle</button>
      <button class="mini-btn" style="color:var(--danger)" onclick='deletePaths([state.focusedFile.path],true)'>✕ Del Forever</button>
    </div>
    <div>
      <div class="kv-row"><span>Size</span><b>${fmt(f.sizeBytes)}</b></div>
      <div class="kv-row"><span>Type</span><b>${f.mediaType.toUpperCase()} (${(f.extension||"—").toUpperCase()})</b></div>
      ${m.durationMs?`<div class="kv-row"><span>Duration</span><b>${fmtDur(m.durationMs)}</b></div>`:""}
      ${m.width?`<div class="kv-row"><span>Resolution</span><b>${m.width} × ${m.height}</b></div>`:""}
      ${m.fps?`<div class="kv-row"><span>FPS</span><b>${m.fps.toFixed(2)}</b></div>`:""}
      ${m.videoCodec?`<div class="kv-row"><span>Video Codec</span><b>${m.videoCodec}</b></div>`:""}
      ${m.audioCodec?`<div class="kv-row"><span>Audio Codec</span><b>${m.audioCodec}</b></div>`:""}
      ${m.bitrate?`<div class="kv-row"><span>Bitrate</span><b>${(m.bitrate/1e6).toFixed(2)} Mbps</b></div>`:""}
      <div class="kv-row"><span>Path</span><b>${f.path}</b></div>
    </div>
    ${state.activeHostId===state.localHostId?`<button class="mini-btn" onclick='revealLocal(state.focusedFile.path)'>Show in Windows Explorer</button>`:""}
  `;
}

async function revealLocal(path){ await sysFetch("/api/system/reveal",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({path})}); }

function showCtxMenu(ev, item, hostOverride=""){
  ev.preventDefault(); ev.stopPropagation();
  const hid = hostOverride || state.activeHostId;
  const fromSplit = (hostOverride && hostOverride===state.split.hostId && state.split.open && ev.target?.closest("#splitPane"));
  const menu=document.getElementById("ctxMenu");
  menu.style.left=Math.min(window.innerWidth-235, ev.clientX)+"px";
  menu.style.top=Math.min(window.innerHeight-340, ev.clientY)+"px";
  if(item.isDir){
    menu.innerHTML=`
      <button class="ctx-item" onclick='hideCtx();${fromSplit?`loadSplitFolder(${JSON.stringify(item.path)})`:`loadFolder(${JSON.stringify(item.path)})`}'>Open Folder</button>
      <button class="ctx-item" onclick='hideCtx();openInSplitView(${JSON.stringify(hid)},${JSON.stringify(item.path)})'>◫ Open in Split View</button>
      <button class="ctx-item" onclick='hideCtx();toggleFavoriteCurrentFolder(${JSON.stringify(item.path)},${JSON.stringify(item.name)},${JSON.stringify(hid)})'>★ Pin / Unpin Favorite</button>
      <button class="ctx-item" onclick='hideCtx();openTransferModal([${JSON.stringify(item)}],true,${JSON.stringify(hid)})'>Move Folder...</button>
      <button class="ctx-item" onclick='hideCtx();openTransferModal([${JSON.stringify(item)}],false,${JSON.stringify(hid)})'>Copy Folder...</button>
      <button class="ctx-item" onclick='hideCtx();backupPathsToGDrive([${JSON.stringify(item.path)}],${JSON.stringify(hid)})'>Back Up to Google Drive</button>
      <button class="ctx-item danger" onclick='hideCtx();deletePaths([${JSON.stringify(item.path)}],false,${!!fromSplit},${JSON.stringify(hid)})'>♻ Move Folder to Recycle Bin</button>
      <button class="ctx-item danger" onclick='hideCtx();deletePaths([${JSON.stringify(item.path)}],true,${!!fromSplit},${JSON.stringify(hid)})'>✕ Permanently Delete Folder</button>
    `;
  }else{
    if(!fromSplit) state.focusedFile = item;
    const canOpt = item.mediaType==="video" || item.mediaType==="image";
    const ctxPaths = (!fromSplit && state.selected.has(item.path) && state.selected.size>1) ? Array.from(state.selected) : [item.path];
    menu.innerHTML=`
      <button class="ctx-item" onclick='hideCtx();openFileViewer(${JSON.stringify(item)},false,${JSON.stringify(hid)})'>Open / Play</button>
      ${item.mediaType==="video"?`<button class="ctx-item" onclick='hideCtx();openFileViewer(${JSON.stringify(item)},true,${JSON.stringify(hid)})'>Cut, Rotate & Crop Studio</button>`:""}
      ${canOpt?`<button class="ctx-item" onclick='hideCtx();openOptimizeModal([${JSON.stringify(item)}],"compress",${JSON.stringify(hid)})'>🗜 Compress & Estimate Size...</button>`:""}
      ${canOpt?`<button class="ctx-item" onclick='hideCtx();openOptimizeModal([${JSON.stringify(item)}],"upscale",${JSON.stringify(hid)})'>✨ Upscale & Enhance...</button>`:""}
      <button class="ctx-item" onclick='hideCtx();openTransferModal([${JSON.stringify(item)}],true,${JSON.stringify(hid)})'>Move To Device / Folder...</button>
      <button class="ctx-item" onclick='hideCtx();openTransferModal([${JSON.stringify(item)}],false,${JSON.stringify(hid)})'>Copy To Device / Folder...</button>
      <button class="ctx-item" onclick='hideCtx();copyFilesToWindowsClipboard(${JSON.stringify(hid)},${JSON.stringify(ctxPaths)})'>Copy File(s) to App Clipboard (Ctrl+C)</button>
      <button class="ctx-item" onmousedown='startNativeExternalDrag(event,${JSON.stringify(ctxPaths)},${JSON.stringify(hid)})' onclick='hideCtx()'>⤢ Hold & Drag to External App</button>
      <button class="ctx-item" onclick='hideCtx();backupPathsToGDrive(${JSON.stringify(ctxPaths)},${JSON.stringify(hid)})'>Back Up to Google Drive</button>
      <button class="ctx-item" onclick='hideCtx();renamePrompt(${JSON.stringify(item)},${JSON.stringify(hid)})'>Rename (F2)</button>
      <button class="ctx-item" onclick='hideCtx();navigator.clipboard?.writeText(${JSON.stringify(item.path)});showToast("Path copied")'>Copy Path</button>
      ${hid===state.localHostId?`<button class="ctx-item" onclick='hideCtx();revealLocal(${JSON.stringify(item.path)})'>Show in Explorer</button>`:""}
      <button class="ctx-item danger" onclick='hideCtx();deletePaths(${JSON.stringify(ctxPaths)},false,${!!fromSplit},${JSON.stringify(hid)})'>♻ Move to Recycle Bin (Del)</button>
      <button class="ctx-item danger" onclick='hideCtx();deletePaths(${JSON.stringify(ctxPaths)},true,${!!fromSplit},${JSON.stringify(hid)})'>✕ Permanently Delete (Shift+Del)</button>
    `;
  }
  menu.classList.add("open");
}
function hideCtx(){document.getElementById("ctxMenu").classList.remove("open")}
window.addEventListener("click", (e)=>{ if(!e.target.closest("#ctxMenu")) hideCtx(); if(!e.target.closest("#gridPopover") && !e.target.closest("button[onclick*='toggleGridPopover']")) toggleGridPopover(false); });

function toggleGridPopover(force){ const p=document.getElementById("gridPopover"); p.classList.toggle("open", force!==undefined ? force : !p.classList.contains("open")); }
function togglePane(){document.getElementById("treePane").classList.toggle("collapsed")}
function toggleInspector(force){ setPref("showInspector", force!==undefined ? force : !prefs.showInspector); }
function goUp(){if(state.parentPath)loadFolder(state.parentPath)}
function toggleTreeSort(){state.treeSort=state.treeSort==="size"?"name":"size";document.getElementById("treeSortBtn").textContent=state.treeSort==="size"?"Size":"Name";if(state.currentPath)loadTree(state.currentPath)}
async function rescanActive(){await hostApi(state.activeHostId,"/api/rescan",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"});showToast("Rescanning drives...");setTimeout(()=>selectHost(state.activeHostId,state.currentPath),800)}
function toggleViewMode(){setPref("viewMode", prefs.viewMode==="gallery"?"list":"gallery"); localStorage.setItem("dv_view", prefs.viewMode);}
function changeSort(v){setPref("sort", v); localStorage.setItem("dv_sort", v); if(state.currentPath)loadFolder(state.currentPath)}
function toggleRecursive(){state.recursive=!state.recursive;document.getElementById("recursiveChip").classList.toggle("active",state.recursive);document.getElementById("recursiveChip").textContent="Deep Search: "+(state.recursive?"On":"Off");if(state.currentPath)loadFolder(state.currentPath)}
function onSearchInput(){clearTimeout(window._st);window._st=setTimeout(()=>{if(state.currentPath)loadFolder(state.currentPath)},240)}
function showBrowseMode(){closeOverlay("allDrivesOverlay");if(state.spaceHogs&&state.currentPath)loadFolder(state.currentPath)}
async function createFolderHere(){if(!state.currentPath)return;const n=prompt("Create new folder inside current directory:");if(!n)return;await hostApi(state.activeHostId,"/api/folder/create",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({parentDir:state.currentPath,name:n})});await loadFolder(state.currentPath);}

function toggleSelectMode(){
  state.selectMode = !state.selectMode;
  const btn = document.getElementById("selectModeBtn");
  if(btn){ btn.classList.toggle("primary", state.selectMode); btn.textContent = state.selectMode ? "☑ Selecting" : "☑ Select"; }
  renderBrowser();
}
function handleSelectClick(idx, path, shift){
  if(shift && state.lastSelectedIdx>=0 && state.files.length){
    const a=Math.min(state.lastSelectedIdx, idx), b=Math.max(state.lastSelectedIdx, idx);
    for(let i=a;i<=b;i++) state.selected.add(state.files[i].path);
  }else{
    if(state.selected.has(path)) state.selected.delete(path);
    else state.selected.add(path);
    state.lastSelectedIdx = idx;
  }
  updateSelBar(); renderBrowser();
}
function toggleSelect(path){if(state.selected.has(path))state.selected.delete(path);else state.selected.add(path);updateSelBar();renderBrowser()}
function selectAllFiles(){state.files.forEach(f=>state.selected.add(f.path));updateSelBar();renderBrowser()}
function invertSelection(){ const next=new Set(); state.files.forEach(f=>{ if(!state.selected.has(f.path)) next.add(f.path); }); state.selected=next; updateSelBar(); renderBrowser(); }
function clearSelection(){state.selected.clear();state.lastSelectedIdx=-1;updateSelBar();renderBrowser()}
function getSelectedFiles(){return state.files.filter(f=>state.selected.has(f.path))}
function updateSelBar(){
  const bar=document.getElementById("selBar");
  document.body.classList.toggle("has-selection", state.selected.size>0);
  if(state.selected.size>0){bar.classList.add("open");document.getElementById("selCount").textContent=`${state.selected.size} selected`}
  else bar.classList.remove("open");
}

async function loadSpaceHogs(){
  if(!state.currentPath)return;
  state.spaceHogs=true;
  const d=await hostApi(state.activeHostId,"/api/largest?"+new URLSearchParams({path:state.currentPath,filter:"video",limit:"80"}).toString());
  state.folders=[]; state.files=d.files||[];
  renderBreadcrumbs(state.currentPath, `Largest Space Hogs (${state.files.length})`);
  renderBrowser();
}

/* All Devices, Cloud & Drives Dashboard */
async function openAllDrives(){document.getElementById("allDrivesOverlay").classList.add("open");await refreshAllDrives()}
async function refreshAllDrives(){
  const body=document.getElementById("allDrivesBody");
  body.innerHTML=`<div style="padding:24px;color:var(--muted)">Querying all paired devices & cloud drives...</div>`;
  try{ const cl=await sysFetch("/api/cloud/list"); state.cloudProviders=cl.cloudProviders||[]; renderCloudSection(); }catch{}
  const snaps = await Promise.all(state.hosts.map(async h=>{
    try{const r=await hostApi(h.hostId,"/api/roots");return{host:h,online:true,os:r.os||h.os,drives:r.drives||[],quick:r.quickFolders||[]}}
    catch(e){return{host:h,online:false,os:h.os,drives:[],quick:[],err:e.message}}
  }));
  const allDrives = snaps.flatMap(s=>s.drives);
  const totalCap = allDrives.reduce((a,d)=>a+(d.totalBytes||0),0), totalFree = allDrives.reduce((a,d)=>a+(d.freeBytes||0),0);
  document.getElementById("allDrivesSummary").textContent=`${snaps.filter(s=>s.online).length}/${snaps.length} devices online • ${state.cloudProviders.length} cloud mount(s) • ${allDrives.length} drives • ${fmt(totalFree)} free of ${fmt(totalCap)}`;
  body.innerHTML="";
  if(state.cloudProviders.length){
    const cc=document.createElement("div"); cc.className="section-card";
    cc.innerHTML=`<div style="display:flex;align-items:center;justify-content:space-between"><strong style="font-size:15px">Cloud & Storage Apps</strong><button class="mini-btn" onclick="openCloudModal()">Manage / Connect Apps</button></div><div class="drives-grid">${state.cloudProviders.map(c=>`<div class="drive-card" onclick='jumpToDrive(${JSON.stringify(state.localHostId)},${JSON.stringify(c.path)})' oncontextmenu='showTargetCtxMenu(event,${JSON.stringify(state.localHostId)},${JSON.stringify(c.path)},${JSON.stringify(c.name)},"cloud",${JSON.stringify(c.id)})'><div style="display:flex;justify-content:space-between;font-weight:700;font-size:13px"><span>☁ ${c.name}</span><span style="color:var(--primary)">${c.freeBytes>0?fmt(c.freeBytes)+" free":"Cloud"}</span></div><div style="font-size:11px;color:var(--muted);word-break:break-all">${c.path}</div><div style="font-size:10px;color:var(--muted)">Left-click Open • Right-click Split View</div></div>`).join("")}</div>`;
    body.appendChild(cc);
  }
  snaps.forEach(s=>{
    const card=document.createElement("div"); card.className="section-card";
    const status = s.online ? `<span style="color:var(--success)">● Online</span>` : `<span style="color:var(--danger)">● Offline</span>`;
    let html=`<div style="display:flex;align-items:center;justify-content:space-between;cursor:context-menu" oncontextmenu='showTargetCtxMenu(event,${JSON.stringify(s.host.hostId)},"",${JSON.stringify(s.host.label||s.host.name)},"device")'><div><strong style="font-size:15px">${s.host.label||s.host.name}</strong> <span class="mini-btn">${devBadge(s.os,s.host.name)} • ${s.os}</span></div><div style="display:flex;gap:8px;align-items:center"><button class="mini-btn" onclick='closeOverlay("allDrivesOverlay");openInSplitView(${JSON.stringify(s.host.hostId)})'>◫ Split View</button>${status}</div></div>`;
    if(s.online){
      html+=`<div class="drives-grid">`;
      s.drives.forEach(dr=>{
        const tot=Math.max(1,dr.totalBytes||1), used=Math.max(0,tot-(dr.freeBytes||0)), pct=Math.min(100,Math.round(used*100/tot));
        html+=`<div class="drive-card" onclick='jumpToDrive(${JSON.stringify(s.host.hostId)},${JSON.stringify(dr.path)})' oncontextmenu='showTargetCtxMenu(event,${JSON.stringify(s.host.hostId)},${JSON.stringify(dr.path)},${JSON.stringify(dr.name)},"drive")'><div style="display:flex;justify-content:space-between;font-weight:700;font-size:13px"><span>${dr.name}</span><span style="color:var(--primary)">${fmt(dr.freeBytes)} free</span></div><div class="bar"><div style="width:${pct}%"></div></div><div style="font-size:11px;color:var(--muted)">${fmt(used)} used (${pct}%) • ${fmt(tot)} total</div></div>`;
      });
      html+=`</div>`;
      if(s.quick.length){html+=`<div class="chip-row">`;s.quick.forEach(q=>{html+=`<button class="chip" onclick='jumpToDrive(${JSON.stringify(s.host.hostId)},${JSON.stringify(q.path)})' oncontextmenu='showTargetCtxMenu(event,${JSON.stringify(s.host.hostId)},${JSON.stringify(q.path)},${JSON.stringify(q.name)},"folder")'>📁 ${q.name}</button>`});html+=`</div>`;}
    }else html+=`<div style="font-size:12px;color:var(--danger)">${s.err||"Unreachable on Wi-Fi"}</div>`;
    card.innerHTML=html; body.appendChild(card);
  });
}
function jumpToDrive(hostId, path){closeOverlay("allDrivesOverlay");selectHost(hostId, path)}

/* Wi-Fi Discovery & PIN-Only Pairing Modal */
async function openDevicesModal(){document.getElementById("devicesModal").classList.add("open");await scanAndRenderDevices()}
async function scanAndRenderDevices(){
  const body=document.getElementById("devicesBody");
  body.innerHTML=`<div style="color:var(--muted);font-size:13px">Scanning Wi-Fi for PC, Laptop, Fold, Tablet & iPhone...</div>`;
  try{ const d=await sysFetch("/api/fleet/discover"); state.discovered=d.devices||[]; }catch{ state.discovered=[]; }
  renderDevicesBody();
}
function renderDevicesBody(){
  const body=document.getElementById("devicesBody"); body.innerHTML="";
  const pairedSec=document.createElement("div"); pairedSec.className="section-card";
  pairedSec.innerHTML=`<div style="display:flex;justify-content:space-between;align-items:center"><strong style="font-size:13px">Connected Devices (Tap to Switch)</strong><button class="mini-btn" onclick="scanAndRenderDevices()">Rescan Wi-Fi</button></div>`;
  state.hosts.forEach(h=>{
    const r=document.createElement("div"); r.className="list-row";
    r.innerHTML=`<span class="mini-btn">${devBadge(h.os,h.name)}</span><div style="flex:1"><div style="font-weight:700;font-size:13px">${h.label||h.name}</div><div style="font-size:11px;color:var(--muted)">${h.os} • ${h.ip||"Local"}:${h.port||48720}</div></div>${!h.isLocal?`<button class="mini-btn" onclick='event.stopPropagation();removePaired(${JSON.stringify(h.hostId)})'>Remove</button>`:""}`;
    r.onclick=()=>{closeDialog("devicesModal");selectHost(h.hostId)}; pairedSec.appendChild(r);
  });
  body.appendChild(pairedSec);

  const discSec=document.createElement("div"); discSec.className="section-card";
  discSec.innerHTML=`<strong style="font-size:13px;color:var(--primary)">Found on Wi-Fi — Enter PIN Only</strong>`;
  const others = state.discovered.filter(d=>d.hostId!==state.localHostId);
  if(!others.length) discSec.innerHTML+=`<div style="font-size:12px;color:var(--muted)">No other devices responded yet. Open DeckVault on your Laptop, Tab S8 Ultra, Fold, or iPhone and click Rescan.</div>`;
  else others.forEach(d=>{
    const ip=(d.ips&&d.ips[0])||"";
    const row=document.createElement("div"); row.className="list-row"; row.style.flexWrap="wrap";
    row.innerHTML=`<span class="mini-btn">${devBadge(d.os,d.deviceName)}</span><div style="flex:1;min-width:140px"><div style="font-weight:700;font-size:13px">${d.deviceName}</div><div style="font-size:11px;color:var(--muted)">${d.os} • ${ip} ${d.freeBytes>0?"• "+fmt(d.freeBytes)+" free":""}</div></div><input class="input" type="password" inputmode="numeric" maxlength="6" placeholder="6-digit PIN" id="pin_${d.hostId}" style="width:120px;padding:6px 9px"/><button class="pill-btn primary" onclick='pairDiscovered(${JSON.stringify(ip)},${d.port||48720},${JSON.stringify(d.deviceName)},"pin_${d.hostId}")'>Connect</button>`;
    discSec.appendChild(row);
  });
  body.appendChild(discSec);
}
async function pairDiscovered(ip, port, label, inputId){
  const pin=document.getElementById(inputId).value.trim();
  if(!pin)return showToast("Enter the 6-digit PIN shown on "+label);
  try{
    const res=await sysFetch("/api/fleet/pair",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({ip,port,pin,label})});
    buildHostList(res.fleetHosts||[]); showToast("Connected to "+label); closeDialog("devicesModal");
    await selectHost(res.host.hostId);
  }catch(e){showToast("Pair failed: "+e.message)}
}
async function removePaired(hostId){
  const res=await sysFetch("/api/fleet/remove",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({hostId})});
  buildHostList(res.fleetHosts||[]);
  if(state.activeHostId===hostId) await selectHost(state.localHostId);
  renderDevicesBody();
}

/* VLC-Rival Cinema Video Player & Visual Cut / Crop Studio */
function openFileViewer(f, openStudio){
  state.activeFile=f;
  const ext=("."+(f.extension||"")).toLowerCase();
  if(f.mediaType==="video") openVideoOverlay(f, openStudio);
  else if(f.mediaType==="image") openImageOverlay(f);
  else if(f.mediaType==="audio") openDocOverlay(f, `<audio src="${hostMediaUrl(state.activeHostId,"/api/stream",{path:f.path})}" controls autoplay style="width:min(560px,90vw)"></audio>`);
  else if(ext===".pdf") openDocOverlay(f, `<iframe src="${hostMediaUrl(state.activeHostId,"/api/stream",{path:f.path})}" style="width:100%;height:100%;border:0;border-radius:12px"></iframe>`);
  else if([".txt",".md",".json",".xml",".csv",".log",".py",".kt",".swift",".js",".html",".css",".ps1",".bat",".ini",".yaml",".yml"].includes(ext)){
    openDocOverlay(f, `<pre id="txtView" class="selectable" style="width:min(1000px,94vw);height:100%;overflow:auto;background:var(--panel);padding:16px;border-radius:14px;border:1px solid var(--border);font-size:12px;white-space:pre-wrap">Loading...</pre>`);
    fetch(hostMediaUrl(state.activeHostId,"/api/stream",{path:f.path})).then(r=>r.text()).then(t=>{const el=document.getElementById("txtView");if(el)el.textContent=t.slice(0,300000)});
  }else{
    openDocOverlay(f, `<div class="section-card" style="max-width:460px"><strong>${f.name}</strong><div style="font-size:12px;color:var(--muted)">Size: ${fmt(f.sizeBytes)}<br/>Path: ${f.path}</div></div>`);
  }
}

function ensureAudioBoost(v){
  try{
    if(!state.audioCtx){
      const AC = window.AudioContext || window.webkitAudioContext;
      state.audioCtx = new AC();
      const src = state.audioCtx.createMediaElementSource(v);
      state.gainNode = state.audioCtx.createGain();
      src.connect(state.gainNode);
      state.gainNode.connect(state.audioCtx.destination);
    }
    if(state.audioCtx.state==="suspended") state.audioCtx.resume();
  }catch{}
}

function needsLiveTranscode(f){
  const vc = (f.meta?.videoCodec || "").toLowerCase();
  const ext = ("." + (f.extension || "")).toLowerCase();
  const fps = f.meta?.fps || 0;
  return ["hevc","h265","hev1","hvc1","mpeg4","msmpeg4v3","wmv3","vc1","prores","mpeg2video"].includes(vc)
    || [".mkv",".avi",".wmv",".flv",".mts",".m2ts",".ts",".vob"].includes(ext)
    || fps > 90;
}

function startTranscodeStream(startSec=0){
  if(!state.activeFile) return;
  const v=document.getElementById("vidEl");
  state.isTranscode=true;
  state.transcodeOffsetSec=Math.max(0, startSec);
  v.src=hostMediaUrl(state.activeHostId, "/api/stream/playable", {path:state.activeFile.path, start:state.transcodeOffsetSec.toFixed(2), v:Date.now()});
  v.playbackRate=parseFloat(document.getElementById("vidSpeed").value)||1;
  document.getElementById("vidCodecBadge").textContent="LIVE H.264 STREAM";
  v.play().catch(()=>{});
}

function seekVideoTo(sec){
  const d = Math.max(0.1, state.vidDurationSec || 1);
  const clamped = Math.max(0, Math.min(d, sec));
  if(state.isTranscode){
    startTranscodeStream(clamped);
  }else{
    document.getElementById("vidEl").currentTime = clamped;
  }
}

function openVideoOverlay(f, openStudio){
  state.activeFile=f;
  state.edit = { cropActive:false, cropPreview:false, x:0.1, y:0.1, w:0.8, h:0.8, rotate:0, flipH:false, flipV:false, muteOut:false };
  state.abLoop = false;
  state.isTranscode = false;
  state.transcodeOffsetSec = 0;
  state.vidDurationSec = (f.meta?.durationMs || 0) / 1000.0;
  document.getElementById("abLoopBtn").textContent="A-B Loop: Off";
  document.getElementById("vidTitle").textContent=f.name;
  const m=f.meta||{};
  document.getElementById("vidMeta").textContent=`${fmt(f.sizeBytes)} ${m.width?`• ${m.width}×${m.height}`:""} ${m.fps?`• ${m.fps.toFixed(1)}fps`:""}`;
  document.getElementById("vidCodecBadge").textContent=[fmtRes(m.width,m.height),m.videoCodec,m.audioCodec].filter(Boolean).join(" • ")||"VIDEO";
  document.getElementById("editCrf").value=prefs.defaultCrf;
  document.getElementById("editRecycle").checked=prefs.recycleDefault;
  document.getElementById("vidSpeed").value=String(prefs.defaultSpeed);
  document.getElementById("vidFitSelect").value="contain";
  document.getElementById("vidAspectSelect").value="auto";

  const v=document.getElementById("vidEl");
  v.playbackRate=prefs.defaultSpeed;
  v.onerror = () => {
    if(!state.isTranscode){
      showToast("Converting codec cleanly for playback...");
      startTranscodeStream(0);
    }
  };
  v.onloadeddata = () => {
    if(!state.isTranscode && v.videoWidth === 0){
      showToast("Switching to Live H.264 Stream...");
      startTranscodeStream(0);
    }
    layoutCinemaVideo();
  };
  v.onloadedmetadata=()=>{
    const d = Math.max(1, state.isTranscode ? (state.vidDurationSec || v.duration || 1) : (v.duration || state.vidDurationSec || 1));
    state.vidDurationSec = d;
    if(!state.isTranscode || state.transcodeOffsetSec === 0){
      document.getElementById("inRange").max=d; document.getElementById("outRange").max=d;
      document.getElementById("inRange").value=0; document.getElementById("outRange").value=d;
      updateTrimUI();
    }
    updateCropBoxDOM();
    updateTransformButtons();
  };
  v.ontimeupdate=updateCinemaTimeHUD;
  v.onplay=()=>{document.getElementById("playPauseBtn").textContent="Pause";ensureAudioBoost(v);};
  v.onpause=()=>{document.getElementById("playPauseBtn").textContent="Play";};

  loadStoryboardCells(f.path);
  document.getElementById("cropLayer").classList.remove("active");
  document.getElementById("cropTopBtn").textContent="Crop Box (C)";
  document.getElementById("cropToggleBtn").textContent="Enable Crop Box";
  document.getElementById("studioDrawer").classList.toggle("open", !!openStudio);
  document.getElementById("videoOverlay").classList.add("open");
  updateTransformButtons();
  requestAnimationFrame(layoutCinemaVideo);

  if(needsLiveTranscode(f)){
    startTranscodeStream(0);
  }else{
    v.src=hostMediaUrl(state.activeHostId,"/api/stream",{path:f.path, v:Date.now()});
    v.play().catch(()=>{});
  }
}

function updateCinemaTimeHUD(){
  const v=document.getElementById("vidEl");
  const d=Math.max(0.01, state.vidDurationSec || v.duration || 1);
  const cur=(state.isTranscode ? state.transcodeOffsetSec : 0) + (v.currentTime||0);
  const pct=Math.min(100, (cur/d)*100).toFixed(2)+"%";
  document.getElementById("vidFill").style.width=pct;
  const ph=document.getElementById("trimPlayhead");
  if(ph) ph.style.left=pct;
  document.getElementById("vidTimeCode").textContent=`${fmtPrec(cur)} / ${fmtPrec(d)}`;
  if(v.buffered.length>0 && !state.isTranscode){
    const bend=v.buffered.end(v.buffered.length-1);
    document.getElementById("vidBuf").style.width=Math.min(100,(bend/d)*100).toFixed(1)+"%";
  }
  if(state.abLoop && !v.seeking){
    const a=parseFloat(document.getElementById("inRange").value), b=parseFloat(document.getElementById("outRange").value);
    if(cur>=b || cur<a-0.4) seekVideoTo(a);
  }
}

function togglePlay(){ const v=document.getElementById("vidEl"); if(v.paused)v.play(); else v.pause(); }
function seekVideoRel(dt){
  const cur=(state.isTranscode ? state.transcodeOffsetSec : 0) + (document.getElementById("vidEl").currentTime||0);
  seekVideoTo(cur + dt);
}
function nudgeFrame(dir){ const v=document.getElementById("vidEl"); v.pause(); const fps=state.activeFile?.meta?.fps||30; seekVideoRel(dir/fps); }
function toggleMute(){ const v=document.getElementById("vidEl"); v.muted=!v.muted; document.getElementById("muteBtn").textContent=v.muted?"Muted":"Vol"; }
function setVolumePct(pct){
  const v=document.getElementById("vidEl"); v.muted=pct===0;
  document.getElementById("volLbl").textContent=pct+"%";
  ensureAudioBoost(v);
  if(pct<=100){ v.volume=pct/100; if(state.gainNode) state.gainNode.gain.value=1.0; }
  else { v.volume=1.0; if(state.gainNode) state.gainNode.gain.value=pct/100; }
}
function setVideoFit(m){ layoutCinemaVideo(); }
function setVideoAspect(a){ layoutCinemaVideo(); }
async function openInExternalPlayer(){
  if(!state.activeFile) return;
  if(state.activeHostId!==state.localHostId){
    showToast("External VLC launch is for files on this PC"); return;
  }
  try{
    const res = await sysFetch("/api/system/open", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({path:state.activeFile.path})});
    showToast("Opened in " + (res.player || "Player"));
  }catch(e){showToast("Could not open player: "+e.message)}
}
function setVidSpeed(s){ document.getElementById("vidEl").playbackRate=parseFloat(s); }
function toggleAbLoop(){ state.abLoop=!state.abLoop; document.getElementById("abLoopBtn").textContent="A-B Loop: "+(state.abLoop?"On":"Off"); updateTrimUI(); }
function toggleCinemaFullscreen(){ document.getElementById("videoOverlay").classList.toggle("fullscreen-all"); requestAnimationFrame(layoutCinemaVideo); }

/* Hover Storyboard Preview on Main Timeline */
(()=>{
  const tl=document.getElementById("vidTimeline"), tip=document.getElementById("vidTip"), img=document.getElementById("vidTipImg"), tm=document.getElementById("vidTipTime");
  tl.onmousemove=(e)=>{
    const d=state.vidDurationSec || document.getElementById("vidEl").duration || 0; if(!d||!state.activeFile)return;
    const r=tl.getBoundingClientRect(), frac=Math.max(0,Math.min(0.999,(e.clientX-r.left)/r.width));
    const idx=Math.floor(frac*16), col=idx%4, row=Math.floor(idx/4);
    const sb=hostMediaUrl(state.activeHostId,"/api/video/storyboard",{path:state.activeFile.path, cols:4, rows:4});
    tip.style.display="flex"; tip.style.left=Math.max(76,Math.min(r.width-76, e.clientX-r.left))+"px";
    img.style.backgroundImage=`url("${sb}")`; img.style.backgroundSize="400% 400%";
    img.style.backgroundPosition=`${col*(100/3)}% ${row*(100/3)}%`;
    tm.textContent=fmtPrec(frac*d);
  };
  tl.onmouseleave=()=>{tip.style.display="none"};
  tl.onclick=(e)=>{ const d=state.vidDurationSec || document.getElementById("vidEl").duration || 0, r=tl.getBoundingClientRect(); if(d>0) seekVideoTo(((e.clientX-r.left)/r.width)*d); };
})();

function loadStoryboardCells(path){
  const sbUrl=hostMediaUrl(state.activeHostId,"/api/video/storyboard",{path, cols:4, rows:4});
  const row=document.getElementById("sbCells"); row.innerHTML="";
  for(let r=0;r<4;r++)for(let c=0;c<4;c++){
    const d=document.createElement("div"); d.className="sb-cell";
    d.style.backgroundImage=`url("${sbUrl}")`; d.style.backgroundPosition=`${c*(100/3)}% ${r*(100/3)}%`;
    row.appendChild(d);
  }
}
function seekFromStoryboard(e){
  const d=state.vidDurationSec || document.getElementById("vidEl").duration || 0, r=document.getElementById("sbFrames").getBoundingClientRect();
  if(d>0) seekVideoTo(((e.clientX-r.left)/r.width)*d);
}
function toggleStudioDrawer(){
  document.getElementById("studioDrawer").classList.toggle("open");
  requestAnimationFrame(layoutCinemaVideo);
}
function onTrimSlider(){
  let a=parseFloat(document.getElementById("inRange").value), b=parseFloat(document.getElementById("outRange").value);
  if(a>b-0.3){ a=Math.max(0,b-0.3); document.getElementById("inRange").value=a; }
  updateTrimUI();
}
function updateTrimUI(){
  const a=parseFloat(document.getElementById("inRange").value), b=parseFloat(document.getElementById("outRange").value), d=Math.max(0.01,parseFloat(document.getElementById("outRange").max)||1);
  const aPct=Math.max(0,Math.min(100,(a/d)*100)), bPct=Math.max(aPct,Math.min(100,(b/d)*100));
  document.getElementById("inLbl").textContent=fmtPrec(a);
  document.getElementById("outLbl").textContent=fmtPrec(b);
  document.getElementById("trimMaskL").style.width=aPct.toFixed(2)+"%";
  document.getElementById("trimMaskR").style.width=(100-bPct).toFixed(2)+"%";
  const win=document.getElementById("trimActiveWin"), hl=document.getElementById("trimHandleL"), hr=document.getElementById("trimHandleR");
  if(win){ win.style.left=aPct.toFixed(2)+"%"; win.style.width=(bPct-aPct).toFixed(2)+"%"; }
  if(hl){ hl.style.left=aPct.toFixed(2)+"%"; document.getElementById("trimTagL").textContent=fmtPrec(a); }
  if(hr){ hr.style.left=bPct.toFixed(2)+"%"; document.getElementById("trimTagR").textContent=fmtPrec(b); }
  const ab=document.getElementById("vidAbBar");
  ab.style.display=state.abLoop?"block":"none";
  ab.style.left=aPct.toFixed(2)+"%"; ab.style.width=Math.max(0,bPct-aPct).toFixed(2)+"%";
  const estRatio=Math.max(0,b-a)/d, sz=state.activeFile?.sizeBytes||0;
  document.getElementById("trimEstLbl").textContent=`Clip: ${fmtPrec(b-a)} (~${fmt(sz*estRatio)})`;
}
function currentAbsVideoSec(){ return (state.isTranscode ? state.transcodeOffsetSec : 0) + (document.getElementById("vidEl").currentTime||0); }
function autoPreviewCut(){
  const a=parseFloat(document.getElementById("inRange").value);
  state.abLoop=true;
  document.getElementById("abLoopBtn").textContent="A-B Loop: On";
  if(hasActiveCrop()) setCropPreview(true);
  updateTrimUI();
  seekVideoTo(a);
  document.getElementById("vidEl").play().catch(()=>{});
}
function setTrimFromPlayhead(isIn){
  const cur=currentAbsVideoSec();
  if(isIn) document.getElementById("inRange").value=Math.min(cur,parseFloat(document.getElementById("outRange").value)-0.3);
  else document.getElementById("outRange").value=Math.max(cur,parseFloat(document.getElementById("inRange").value)+0.3);
  updateTrimUI();
}
function nudgeTrim(isIn, dt){
  const el=document.getElementById(isIn?"inRange":"outRange");
  el.value=Math.max(0,Math.min(parseFloat(el.max),parseFloat(el.value)+dt));
  onTrimSlider(); autoPreviewCut();
}
function previewTrim(){ autoPreviewCut(); }

/* Draggable Cut Bars on Storyboard Strip */
(()=>{
  const strip=document.getElementById("sbStrip");
  if(!strip) return;
  let lastSeekTs=0;
  const startDrag=(ev, which)=>{
    ev.preventDefault(); ev.stopPropagation();
    const el=document.getElementById(which==="in"?"trimHandleL":"trimHandleR");
    el.classList.add("active");
    const v=document.getElementById("vidEl");
    v.pause();
    const move=(m)=>{
      const cx=m.touches ? m.touches[0].clientX : m.clientX;
      const r=strip.getBoundingClientRect(), d=Math.max(0.5, parseFloat(document.getElementById("outRange").max)||state.vidDurationSec||1);
      const frac=Math.max(0, Math.min(1, (cx-r.left)/Math.max(1,r.width)));
      const sec=frac*d;
      const inEl=document.getElementById("inRange"), outEl=document.getElementById("outRange");
      if(which==="in"){
        inEl.value=Math.max(0, Math.min(parseFloat(outEl.value)-0.3, sec));
      }else{
        outEl.value=Math.min(d, Math.max(parseFloat(inEl.value)+0.3, sec));
      }
      updateTrimUI();
      const now=performance.now();
      if(!state.isTranscode && now-lastSeekTs>65){
        lastSeekTs=now;
        v.currentTime=parseFloat((which==="in"?inEl:outEl).value);
      }
    };
    const up=()=>{
      el.classList.remove("active");
      window.removeEventListener("mousemove",move);
      window.removeEventListener("mouseup",up);
      window.removeEventListener("touchmove",move);
      window.removeEventListener("touchend",up);
      autoPreviewCut();
    };
    window.addEventListener("mousemove",move);
    window.addEventListener("mouseup",up);
    window.addEventListener("touchmove",move,{passive:false});
    window.addEventListener("touchend",up);
  };
  document.getElementById("trimHandleL").onmousedown=(e)=>startDrag(e,"in");
  document.getElementById("trimHandleR").onmousedown=(e)=>startDrag(e,"out");
  document.getElementById("trimHandleL").ontouchstart=(e)=>startDrag(e,"in");
  document.getElementById("trimHandleR").ontouchstart=(e)=>startDrag(e,"out");
})();

/* Oriented Video Layout & Visual Crop Box */
function hasActiveCrop(){
  const e=state.edit;
  return e.x>0.005 || e.y>0.005 || e.w<0.995 || e.h<0.995;
}
function getOrientedVideoDims(){
  const v=document.getElementById("vidEl");
  const natW=v.videoWidth || state.activeFile?.meta?.width || 16;
  const natH=v.videoHeight || state.activeFile?.meta?.height || 9;
  const oddRot=(state.edit.rotate % 180) !== 0;
  return { natW, natH, oriW: oddRot ? natH : natW, oriH: oddRot ? natW : natH, oddRot };
}
function layoutCinemaVideo(){
  const wrap=document.getElementById("cinemaWrap"), box=document.getElementById("videoBox"), stage=document.getElementById("videoStage"), v=document.getElementById("vidEl");
  if(!wrap || !box || !stage || !v) return;
  const wrapW=Math.max(40, wrap.clientWidth), wrapH=Math.max(40, wrap.clientHeight);
  const { oriW, oriH, oddRot } = getOrientedVideoDims();
  const e=state.edit;
  const fitVal=document.getElementById("vidFitSelect")?.value || "contain";
  const aspectVal=document.getElementById("vidAspectSelect")?.value || "auto";
  const arMap={"9-16":9/16,"16-9":16/9,"4-3":4/3,"1-1":1};

  let boxW, boxH, stageW, stageH, stageL=0, stageT=0;
  if(e.cropPreview && hasActiveCrop()){
    const cW=Math.max(0.05, e.w)*oriW, cH=Math.max(0.05, e.h)*oriH;
    const s=Math.min(wrapW/cW, wrapH/cH);
    boxW=Math.max(20, Math.round(cW*s));
    boxH=Math.max(20, Math.round(cH*s));
    stageW=oriW*s; stageH=oriH*s;
    stageL=-(e.x*stageW); stageT=-(e.y*stageH);
  }else if(e.cropActive || (fitVal==="contain" && aspectVal==="auto")){
    const s=Math.min(wrapW/oriW, wrapH/oriH);
    boxW=Math.max(20, Math.round(oriW*s));
    boxH=Math.max(20, Math.round(oriH*s));
    stageW=boxW; stageH=boxH;
  }else if(fitVal==="fill" && aspectVal==="auto"){
    boxW=wrapW; boxH=wrapH; stageW=wrapW; stageH=wrapH;
  }else{
    const targetRatio=arMap[aspectVal] || (wrapW/wrapH);
    const bs=Math.min(wrapW/targetRatio, wrapH/1);
    boxW=Math.max(20, Math.round(targetRatio*bs));
    boxH=Math.max(20, Math.round(bs));
    if(fitVal==="fill"){ stageW=boxW; stageH=boxH; }
    else{
      const vs=fitVal==="cover" ? Math.max(boxW/oriW, boxH/oriH) : Math.min(boxW/oriW, boxH/oriH);
      stageW=oriW*vs; stageH=oriH*vs;
      stageL=(boxW-stageW)/2; stageT=(boxH-stageH)/2;
    }
  }

  box.style.width=boxW+"px"; box.style.height=boxH+"px";
  stage.style.width=stageW+"px"; stage.style.height=stageH+"px";
  stage.style.left=stageL+"px"; stage.style.top=stageT+"px";

  const vidW=oddRot ? stageH : stageW, vidH=oddRot ? stageW : stageH;
  v.style.width=vidW+"px"; v.style.height=vidH+"px";
  v.style.transform=`translate(-50%,-50%) scale(${e.flipH?-1:1},${e.flipV?-1:1}) rotate(${e.rotate}deg)`;
  updateCropBoxDOM();
}
function setCropPreview(on){
  if(on && !hasActiveCrop()){
    showToast("Drag Crop Box handles first to preview crop");
    if(!state.edit.cropActive) toggleCropOverlay();
    return;
  }
  state.edit.cropPreview=!!on;
  if(state.edit.cropPreview) state.edit.cropActive=false;
  document.getElementById("cropLayer").classList.toggle("active", state.edit.cropActive);
  document.getElementById("cropPreviewBadge").classList.toggle("open", state.edit.cropPreview);
  updateTransformButtons();
  layoutCinemaVideo();
}
function toggleCropPreview(ev){
  if(ev) ev.stopPropagation();
  if(state.edit.cropPreview){
    state.edit.cropPreview=false;
    state.edit.cropActive=true;
    document.getElementById("cropLayer").classList.add("active");
    document.getElementById("cropPreviewBadge").classList.remove("open");
    updateTransformButtons();
    layoutCinemaVideo();
  }else{
    setCropPreview(true);
  }
}
function toggleCropOverlay(ev){
  if(ev) ev.stopPropagation();
  state.edit.cropPreview=false;
  document.getElementById("cropPreviewBadge").classList.remove("open");
  state.edit.cropActive = !state.edit.cropActive;
  if(state.edit.cropActive){
    if(state.edit.w>0.99 && state.edit.h>0.99){ state.edit.x=0.1; state.edit.y=0.1; state.edit.w=0.8; state.edit.h=0.8; }
    document.getElementById("vidAspectSelect").value="auto";
    document.getElementById("vidFitSelect").value="contain";
    if(!document.getElementById("studioDrawer").classList.contains("open")) document.getElementById("studioDrawer").classList.add("open");
  }
  document.getElementById("cropLayer").classList.toggle("active", state.edit.cropActive);
  updateTransformButtons();
  requestAnimationFrame(layoutCinemaVideo);
}
function resetCrop(){
  state.edit.x=0; state.edit.y=0; state.edit.w=1; state.edit.h=1; state.edit.cropActive=false; state.edit.cropPreview=false;
  document.getElementById("cropLayer").classList.remove("active");
  document.getElementById("cropPreviewBadge").classList.remove("open");
  updateTransformButtons();
  layoutCinemaVideo();
}
function applyCropPreset(ratio){
  state.edit.cropPreview=false;
  document.getElementById("cropPreviewBadge").classList.remove("open");
  if(!state.edit.cropActive){
    state.edit.cropActive=true;
    document.getElementById("cropLayer").classList.add("active");
    document.getElementById("vidAspectSelect").value="auto";
    document.getElementById("vidFitSelect").value="contain";
  }
  const { oriW, oriH } = getOrientedVideoDims();
  if(!ratio){ updateTransformButtons(); layoutCinemaVideo(); return; }
  const srcRatio = oriW / oriH;
  let w=0.82, h=(w * srcRatio) / ratio;
  if(h > 0.88){ h=0.88; w=(h * ratio) / srcRatio; }
  state.edit.w=w; state.edit.h=h; state.edit.x=(1-w)/2; state.edit.y=(1-h)/2;
  updateTransformButtons();
  layoutCinemaVideo();
}
function updateCropBoxDOM(){
  const e=state.edit, r=document.getElementById("cropRect");
  r.style.left=(e.x*100)+"%"; r.style.top=(e.y*100)+"%"; r.style.width=(e.w*100)+"%"; r.style.height=(e.h*100)+"%";
  document.getElementById("csTop").style.cssText=`top:0;left:0;right:0;height:${e.y*100}%`;
  document.getElementById("csBot").style.cssText=`bottom:0;left:0;right:0;height:${(1-e.y-e.h)*100}%`;
  document.getElementById("csLeft").style.cssText=`top:${e.y*100}%;height:${e.h*100}%;left:0;width:${e.x*100}%`;
  document.getElementById("csRight").style.cssText=`top:${e.y*100}%;height:${e.h*100}%;right:0;width:${(1-e.x-e.w)*100}%`;
  const { oriW, oriH } = getOrientedVideoDims();
  const pw=Math.round(oriW*e.w), ph=Math.round(oriH*e.h);
  document.getElementById("cropReadout").textContent=`Out: ${pw} × ${ph} px`;
}
(()=>{
  const layer=document.getElementById("cropLayer"), rect=document.getElementById("cropRect");
  layer.onmousedown=(ev)=>{
    ev.stopPropagation();
    const h = ev.target?.dataset?.h || (ev.target===rect || ev.target.parentElement===rect ? "move" : "");
    if(!h) return;
    const box=layer.getBoundingClientRect(), orig=Object.assign({},state.edit), sx=ev.clientX, sy=ev.clientY;
    const mm=(m)=>{
      const dx=(m.clientX-sx)/box.width, dy=(m.clientY-sy)/box.height;
      let {x,y,w,h:ch}=orig;
      if(h==="move"){ x=Math.max(0,Math.min(1-w,orig.x+dx)); y=Math.max(0,Math.min(1-ch,orig.y+dy)); }
      else{
        if(h.includes("w")){ const nx=Math.max(0,Math.min(orig.x+orig.w-0.06,orig.x+dx)); w=orig.x+orig.w-nx; x=nx; }
        if(h.includes("e")){ w=Math.max(0.06,Math.min(1-orig.x,orig.w+dx)); }
        if(h.includes("n")){ const ny=Math.max(0,Math.min(orig.y+orig.h-0.06,orig.y+dy)); ch=orig.y+orig.h-ny; y=ny; }
        if(h.includes("s")){ ch=Math.max(0.06,Math.min(1-orig.y,orig.h+dy)); }
      }
      state.edit.x=x; state.edit.y=y; state.edit.w=w; state.edit.h=ch;
      updateCropBoxDOM();
    };
    const mu=()=>{ window.removeEventListener("mousemove",mm); window.removeEventListener("mouseup",mu); };
    window.addEventListener("mousemove",mm); window.addEventListener("mouseup",mu);
  };
})();

function cycleRotate(){ state.edit.rotate=(state.edit.rotate+90)%360; updateTransformButtons(); layoutCinemaVideo(); }
function toggleTransformFlag(k){ state.edit[k]=!state.edit[k]; updateTransformButtons(); layoutCinemaVideo(); }
function updateTransformButtons(){
  const e=state.edit;
  document.getElementById("rotBtn").textContent=`Rot: ${e.rotate}°`;
  const hr=document.getElementById("hudRotBtn"); if(hr) hr.textContent=`Rot ${e.rotate}°`;
  document.getElementById("flipHBtn").style.borderColor=e.flipH?"var(--primary)":"var(--border)";
  document.getElementById("flipVBtn").style.borderColor=e.flipV?"var(--primary)":"var(--border)";
  document.getElementById("muteOutBtn").textContent="Mute Audio: "+(e.muteOut?"Yes":"No");
  document.getElementById("cropTopBtn").textContent = e.cropActive ? "Crop Box: ON" : "Crop Box (C)";
  document.getElementById("cropToggleBtn").textContent = e.cropActive ? "Hide Crop Box" : "Enable Crop Box";
  const pb=document.getElementById("cropPreviewBtn"), spb=document.getElementById("studioCropPreviewBtn");
  if(pb){ pb.textContent=e.cropPreview?"Edit Crop (P)":"Preview Crop (P)"; pb.style.borderColor=e.cropPreview?"var(--primary)":"var(--border)"; pb.style.color=e.cropPreview?"var(--primary)":""; }
  if(spb){ spb.textContent=e.cropPreview?"Edit Handles (P)":"Preview Cropped (P)"; spb.style.borderColor=e.cropPreview?"var(--primary)":"var(--border)"; spb.style.color=e.cropPreview?"var(--primary)":""; }
}
window.addEventListener("resize", ()=>{ if(document.getElementById("videoOverlay").classList.contains("open")) layoutCinemaVideo(); });

async function takeVideoSnapshot(){
  try{
    const res=await hostApi(state.activeHostId,"/api/video/snapshot",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({path:state.activeFile.path, ms:Math.round(currentAbsVideoSec()*1000)})});
    showToast("Saved snapshot: "+res.name);
    await loadFolder(state.currentPath);
  }catch(e){showToast("Snapshot failed: "+e.message)}
}

function toLegacyPreTransformCrop(c, rot, flipH, flipV){
  let {x, y, w, h} = c;
  if(flipV) y = Math.max(0, 1 - y - h);
  if(flipH) x = Math.max(0, 1 - x - w);
  const r = ((rot % 360) + 360) % 360;
  if(r === 90){
    return { x: y, y: Math.max(0, 1 - x - w), w: h, h: w };
  }else if(r === 180){
    return { x: Math.max(0, 1 - x - w), y: Math.max(0, 1 - y - h), w, h };
  }else if(r === 270){
    return { x: Math.max(0, 1 - y - h), y: x, w: h, h: w };
  }
  return { x, y, w, h };
}

async function executeStudioEdit(){
  const btn=document.getElementById("exportEditBtn"); btn.disabled=true; btn.textContent="Rendering...";
  const e=state.edit;
  let cropPayload = (e.cropActive || e.x>0.005 || e.y>0.005 || e.w<0.995 || e.h<0.995) ? {x:e.x, y:e.y, w:e.w, h:e.h} : null;
  try{
    const isLocalOrSupportsEdit = state.activeHostId===state.localHostId || state.hosts.find(h=>h.hostId===state.activeHostId)?.os==="Windows";
    if(cropPayload && isLocalOrSupportsEdit){
      try{
        const ping = await hostApi(state.activeHostId, "/api/ping");
        if(ping?.videoEditOrder !== "transform_first"){
          cropPayload = toLegacyPreTransformCrop(cropPayload, e.rotate, e.flipH, e.flipV);
        }
      }catch(_){
        if(state.activeHostId !== state.localHostId){
          cropPayload = toLegacyPreTransformCrop(cropPayload, e.rotate, e.flipH, e.flipV);
        }
      }
    }
    const route = isLocalOrSupportsEdit ? "/api/video/edit" : "/api/video/trim";
    const res = await hostApi(state.activeHostId, route, {
      method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({
        path: state.activeFile.path,
        startMs: Math.round(parseFloat(document.getElementById("inRange").value)*1000),
        endMs: Math.round(parseFloat(document.getElementById("outRange").value)*1000),
        crop: cropPayload,
        rotate: e.rotate,
        flipH: e.flipH,
        flipV: e.flipV,
        muteAudio: e.muteOut,
        speed: 1.0,
        mode: document.getElementById("editQualityMode").value,
        crf: Number(document.getElementById("editCrf").value)||22,
        saveMode: document.getElementById("editSaveMode").value,
        recycleOriginal: document.getElementById("editRecycle").checked
      })
    });
    showToast("Saved edit! " + (res.savedBytes>0 ? "Reclaimed "+fmt(res.savedBytes) : res.path));
    closeVideoOverlay();
    await loadFolder(state.currentPath);
  }catch(err){showToast("Edit failed: "+err.message)}
  finally{btn.disabled=false;btn.textContent="Render & Save Edit"}
}

function closeVideoOverlay(){const v=document.getElementById("vidEl");v.pause();v.removeAttribute("src");v.load();document.getElementById("videoOverlay").classList.remove("fullscreen-all");closeOverlay("videoOverlay")}
function stepVideo(delta){const list=state.files.filter(f=>f.mediaType==="video"),i=list.findIndex(f=>f.path===state.activeFile?.path);if(i>=0&&list[i+delta])openVideoOverlay(list[i+delta],false)}

function openImageOverlay(f){state.activeFile=f;document.getElementById("imgTitle").textContent=f.name;document.getElementById("imgEl").src=hostMediaUrl(state.activeHostId,"/api/image",{path:f.path});document.getElementById("imageOverlay").classList.add("open")}
function stepImage(delta){const list=state.files.filter(f=>f.mediaType==="image"),i=list.findIndex(f=>f.path===state.activeFile?.path);if(i>=0&&list[i+delta])openImageOverlay(list[i+delta])}
function openDocOverlay(f,html){state.activeFile=f;document.getElementById("docTitle").textContent=f.name;const dl=document.getElementById("docDl");dl.href=hostMediaUrl(state.activeHostId,"/api/stream",{path:f.path});dl.download=f.name;document.getElementById("docBody").innerHTML=html;document.getElementById("docOverlay").classList.add("open")}
function closeDocOverlay(){document.getElementById("docBody").innerHTML="";closeOverlay("docOverlay")}
function closeOverlay(id){document.getElementById(id).classList.remove("open")}
function closeDialog(id){document.getElementById(id).classList.remove("open")}

async function deletePaths(paths, permanent=false, refreshSplit=false, hostOverride=""){
  const cleanPaths = Array.from(new Set((paths||[]).filter(Boolean)));
  if(!cleanPaths.length) return;
  const hid = hostOverride || state.activeHostId;
  const msg = permanent
    ? `PERMANENTLY delete ${cleanPaths.length} item(s)?\n\nThis bypasses Recycle Bin and cannot be undone.`
    : `Move ${cleanPaths.length} item(s) to Recycle Bin?`;
  if(!confirm(msg)) return;

  if(state.activeFile && cleanPaths.includes(state.activeFile.path)){
    closeVideoOverlay();
    await new Promise(r=>setTimeout(r, 120));
  }else if(document.getElementById("videoOverlay").classList.contains("open")){
    const v=document.getElementById("vidEl"); v.pause();
  }

  try{
    const r=await hostApi(hid,"/api/file/delete",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({paths:cleanPaths,recycle:!permanent})});
    if((r.deletedCount||0)===0 && r.errors?.length){
      if(!permanent && confirm(`Recycle Bin failed (${r.errors[0]}).\n\nPERMANENTLY delete ${cleanPaths.length} item(s) instead?`)){
        return await deletePaths(cleanPaths, true, refreshSplit, hid);
      }
      showToast("Delete failed: " + r.errors[0]);
      return;
    }
    const errNote = r.errors?.length ? ` (${r.errors.length} skipped: ${r.errors[0]})` : "";
    showToast((permanent ? "Permanently deleted " : "Recycled ") + (r.deletedCount||0) + " item(s)" + errNote);
    clearSelection();
    if(state.currentPath && hid===state.activeHostId) await loadFolder(state.currentPath);
    if(state.split.open && state.split.currentPath && (refreshSplit || hid===state.split.hostId)) await loadSplitFolder(state.split.currentPath);
  }catch(e){ showToast("Delete error: " + e.message); }
}
async function deleteSingleOverlayFile(permanent=false){if(!state.activeFile)return;const p=state.activeFile.path;closeVideoOverlay();closeDocOverlay();closeOverlay("imageOverlay");await deletePaths([p], permanent)}
async function renamePrompt(f, hostOverride=""){
  if(!f)return; const hid=hostOverride||state.activeHostId;
  const n=prompt("Rename file:",f.name);if(!n||n===f.name)return;
  await hostApi(hid,"/api/file/rename",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({path:f.path,newName:n})});
  if(hid===state.activeHostId && state.currentPath) await loadFolder(state.currentPath);
  if(state.split.open && hid===state.split.hostId && state.split.currentPath) await loadSplitFolder(state.split.currentPath);
}

/* External App Drag-Out (Dropbox/Explorer/Desktop) & Windows Clipboard (Ctrl+C) */
async function startNativeExternalDrag(ev, paths, hostOverride=""){
  if(ev){ ev.stopPropagation(); }
  const clean=Array.from(new Set((paths||[]).filter(Boolean)));
  const hid=hostOverride || state.activeHostId;
  if(!clean.length) return;
  try{
    showToast(hid===state.localHostId ? "Drag onto Dropbox, Explorer, or Desktop..." : `Preparing ${clean.length} file(s) from device...`);
    const prep = await sysFetch("/api/system/prepare-drag", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({hostId:hid, paths:clean})});
    const localPaths = prep.localPaths || [];
    if(window.pywebview?.api?.start_native_drag && localPaths.length){
      const started = await window.pywebview.api.start_native_drag(localPaths);
      if(started) return;
    }
    await copyFilesToWindowsClipboard(hid, clean);
  }catch(e){ showToast("Drag failed: " + e.message); }
}

async function copyFilesToWindowsClipboard(hostId, paths){
  const clean=Array.from(new Set((paths||[]).filter(Boolean)));
  if(!clean.length) return;
  try{
    const res = await sysFetch("/api/system/copy-files-clipboard", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({hostId: hostId || state.activeHostId, paths: clean})
    });
    showToast(`Copied ${res.count} file(s) to Windows Clipboard — press Ctrl+V in Dropbox or Explorer`);
  }catch(e){ showToast("Clipboard copy failed: " + e.message); }
}

/* Split Dual-Pane Browser */
function toggleSplitView(){
  if(state.split.open) closeSplitView();
  else openInSplitView(state.activeHostId || state.localHostId, "");
}
function closeSplitView(){
  state.split.open=false;
  document.getElementById("splitPane").style.display="none";
  document.getElementById("splitResizer").style.display="none";
  const btn=document.getElementById("splitToggleBtn");
  if(btn){ btn.classList.remove("primary"); btn.textContent="◫ Split View"; }
}
async function openInSplitView(hostId, path=""){
  const hid = hostId || state.activeHostId || state.localHostId;
  state.split.open = true;
  state.split.hostId = hid;
  const sp = document.getElementById("splitPane"), sr = document.getElementById("splitResizer");
  sp.style.display="flex"; sp.style.flex=`0 0 ${state.split.widthPx}px`;
  sr.style.display="flex";
  const btn=document.getElementById("splitToggleBtn");
  if(btn){ btn.classList.add("primary"); btn.textContent="◫ Close Split"; }
  const sel=document.getElementById("splitHostSelect");
  if(sel){
    sel.innerHTML="";
    state.hosts.forEach(h=>{
      const o=document.createElement("option");
      o.value=h.hostId; o.textContent=h.label||h.name; o.selected=(h.hostId===hid);
      sel.appendChild(o);
    });
  }
  try{
    let target = path;
    if(!target){
      const r=await hostApi(hid,"/api/roots");
      target=(r.drives&&r.drives[0]?.path)||(r.quickFolders&&r.quickFolders[0]?.path)||"";
    }
    if(target) await loadSplitFolder(target);
  }catch(e){showToast("Split view error: "+e.message)}
}
async function loadSplitFolder(path){
  state.split.currentPath = path;
  const d = await hostApi(state.split.hostId, "/api/list?" + new URLSearchParams({path, sort:"name", order:"asc", filter:"all"}).toString());
  state.split.folders = d.folders || [];
  state.split.files = d.files || [];
  state.split.parentPath = d.parentPath || "";
  document.getElementById("splitUpBtn").disabled = !state.split.parentPath;

  const cb=document.getElementById("splitCrumbs"); cb.innerHTML="";
  const sep=path.includes("\\")?"\\":"/"; const segs=path.split(/[\\/]+/).filter(Boolean);
  let acc=sep==="/"?"/":"";
  segs.forEach((seg,idx)=>{
    acc = sep==="\\" ? (idx===0?seg+"\\":acc+(acc.endsWith("\\")?"":"\\")+seg) : (acc==="/"?"/"+seg:acc+"/"+seg);
    const tgt=acc;
    const b=document.createElement("button"); b.className="crumb"+(idx===segs.length-1?" last":"");
    b.textContent=seg; b.onclick=()=>loadSplitFolder(tgt); cb.appendChild(b);
  });
  renderSplitBrowser();
}
function splitGoUp(){ if(state.split.parentPath) loadSplitFolder(state.split.parentPath); }
async function createSplitFolderHere(){
  if(!state.split.currentPath) return;
  const n=prompt("New folder inside Split View:"); if(!n) return;
  await hostApi(state.split.hostId,"/api/folder/create",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({parentDir:state.split.currentPath,name:n})});
  await loadSplitFolder(state.split.currentPath);
}
async function transferMainToSplit(move){
  const paths = state.selected.size>0 ? Array.from(state.selected) : (state.focusedFile ? [state.focusedFile.path] : []);
  if(!paths.length || !state.split.currentPath){ showToast("Select files in main pane first"); return; }
  await dropItemsInto(state.split.hostId, state.split.currentPath, {hostId:state.activeHostId, paths}, move);
}
function renderSplitBrowser(){
  const area=document.getElementById("splitBrowserArea"); area.innerHTML="";
  wireDropTarget(area, state.split.hostId, state.split.currentPath);
  if(state.split.folders.length){
    const fs=document.createElement("div"); fs.className="chip-row"; fs.style.marginBottom="8px";
    state.split.folders.forEach(f=>{
      const c=document.createElement("button"); c.className="chip"; c.textContent=`📁 ${f.name} (${fmt(f.sizeBytes)})`;
      c.onclick=()=>loadSplitFolder(f.path);
      c.oncontextmenu=(ev)=>showCtxMenu(ev,{name:f.name,path:f.path,isDir:true},state.split.hostId);
      wireDropTarget(c, state.split.hostId, f.path);
      fs.appendChild(c);
    });
    area.appendChild(fs);
  }
  const list=document.createElement("div"); list.className="list-col compact";
  state.split.files.forEach(f=>{
    const r=document.createElement("div"); r.className="list-row"; r.draggable=true;
    const th = f.mediaType==="video"
      ? `<img class="list-thumb" loading="lazy" src="${hostMediaUrl(state.split.hostId,"/api/video/thumbnail",{path:f.path,w:320})}" draggable="false"/>`
      : (f.mediaType==="image" ? `<img class="list-thumb" loading="lazy" src="${hostMediaUrl(state.split.hostId,"/api/image",{path:f.path,w:320})}" draggable="false"/>` : `<div class="list-thumb">${(f.extension||"FILE").toUpperCase()}</div>`);
    r.innerHTML=`${th}<div style="flex:1;min-width:0"><div class="card-title">${f.name}</div><div style="font-size:10px;color:var(--muted)">${fmt(f.sizeBytes)}</div></div><button class="mini-btn" data-act="more">•••</button>`;
    r.ondragstart=(ev)=>{
      if(ev.dataTransfer){
        ev.dataTransfer.effectAllowed="copyMove";
        ev.dataTransfer.setData("application/x-deckvault-items", JSON.stringify({hostId:state.split.hostId, paths:[f.path]}));
        const absUrl=new URL(hostMediaUrl(state.split.hostId,"/api/stream",{path:f.path}), window.location.origin).href;
        ev.dataTransfer.setData("DownloadURL", `application/octet-stream:${f.name}:${absUrl}`);
      }
    };
    r.onclick=(ev)=>{
      if(ev.target?.dataset?.act==="more"){ ev.stopPropagation(); showCtxMenu(ev, f, state.split.hostId); }
    };
    r.ondblclick=()=>{ if(state.split.hostId===state.activeHostId) openFileViewer(f, false); };
    r.oncontextmenu=(ev)=>showCtxMenu(ev, f, state.split.hostId);
    list.appendChild(r);
  });
  if(!state.split.files.length && !state.split.folders.length){
    list.innerHTML=`<div style="padding:36px;text-align:center;color:var(--muted)">Empty folder — drop files here to copy/move</div>`;
  }
  area.appendChild(list);
}

/* Built-In Compressor & Upscaler Studio */
function openOptimizeSelected(tab="compress"){
  const files=getSelectedFiles().filter(f=>f.mediaType==="video"||f.mediaType==="image");
  if(!files.length){ showToast("Select video or image files to optimize"); return; }
  openOptimizeModal(files, tab, state.activeHostId);
}
async function openOptimizeModal(items, tab="compress", hostOverride=""){
  const valid=(items||[]).filter(Boolean);
  if(!valid.length) return;
  state.opt.items=valid;
  state.opt.hostId=hostOverride || state.activeHostId;
  state.opt.tab=tab;
  const first=valid[0];
  document.getElementById("optTitle").textContent = valid.length>1 ? `Optimize ${valid.length} Items` : `Optimize: ${first.name}`;
  const totalBytes=valid.reduce((a,x)=>a+(x.sizeBytes||0),0);
  const resTxt = first.meta?.width ? `${first.meta.width}×${first.meta.height}` : (first.mediaType||"media").toUpperCase();
  document.getElementById("optSub").textContent = `${fmt(totalBytes)} • ${resTxt}${valid.length>1?" (batch)":""}`;
  document.getElementById("optimizeModal").classList.add("open");
  switchOptTab(tab);
}
function switchOptTab(tab){
  state.opt.tab=tab;
  document.getElementById("optTabCmp").classList.toggle("active", tab==="compress");
  document.getElementById("optTabUp").classList.toggle("active", tab==="upscale");
  renderOptimizeBody();
}
function renderOptimizeBody(){
  const body=document.getElementById("optBody");
  const first=state.opt.items[0];
  if(!first) return;
  const totalBefore=state.opt.items.reduce((a,x)=>a+(x.sizeBytes||0),0);
  const ow=first.meta?.width || 1920, oh=first.meta?.height || 1080;

  if(state.opt.tab==="compress"){
    body.innerHTML=`
      <div class="est-box" id="cmpEstBox">
        <div style="display:flex;justify-content:space-between;align-items:baseline;flex-wrap:wrap;gap:6px">
          <span>Before: <b>${fmt(totalBefore)}</b></span>
          <span>Estimated After: <b id="cmpEstAfter" style="color:var(--success);font-size:15px">Calculating...</b></span>
          <span id="cmpEstPct" style="font-size:12px;color:var(--primary);font-weight:700"></span>
        </div>
        <div class="est-bar"><div class="est-bar-fill" id="cmpEstBar" style="width:55%"></div></div>
        <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--muted)" id="cmpEstNote">Drag sliders or click Sample Probe for 1.5s real FFmpeg prediction</div>
      </div>
      <div class="section-card">
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
          <label style="font-size:12px">Resolution
            <select class="input" id="cmpHeight" onchange="refreshOptimizeEstimate(false)" style="width:100%;margin-top:4px">
              <option value="0">Keep Original</option>
              <option value="1080">1080p HD</option>
              <option value="720">720p Compact</option>
              <option value="480">480p Web/Mobile</option>
            </select>
          </label>
          <label style="font-size:12px">Codec
            <select class="input" id="cmpCodec" onchange="refreshOptimizeEstimate(false)" style="width:100%;margin-top:4px">
              <option value="h264">H.264 (Maximum Compatibility)</option>
              <option value="h265">H.265 / HEVC (~35% Smaller)</option>
            </select>
          </label>
        </div>
        <div>
          <div style="display:flex;justify-content:space-between;font-size:12px"><span>Compression CRF (higher = smaller file)</span><b id="cmpCrfVal">24</b></div>
          <input type="range" id="cmpCrf" min="18" max="34" value="24" oninput="document.getElementById('cmpCrfVal').textContent=this.value;refreshOptimizeEstimate(false)" style="width:100%"/>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
          <label style="font-size:12px">Audio Bitrate
            <select class="input" id="cmpAudio" onchange="refreshOptimizeEstimate(false)" style="width:100%;margin-top:4px">
              <option value="192">192 kbps High</option>
              <option value="128" selected>128 kbps Standard</option>
              <option value="64">64 kbps Voice/Compact</option>
              <option value="0">Mute (Remove Audio)</option>
            </select>
          </label>
          <label style="font-size:12px">Exact Target Size MB (0 = Use CRF)
            <input type="number" class="input" id="cmpTargetMb" min="0" step="1" value="0" oninput="refreshOptimizeEstimate(false)" style="width:100%;margin-top:4px"/>
          </label>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
          <select class="input" id="cmpSaveMode" style="padding:5px 9px">
            <option value="copy">Save as Copy (_compressed)</option>
            <option value="overwrite">Save Over Original (Recycle Old)</option>
          </select>
          <button class="pill-btn" onclick="refreshOptimizeEstimate(true)" title="Runs 1.5s test encode on PC to predict exact final MB">⚡ Sample Probe Exact Size</button>
          <span style="flex:1"></span>
          <button class="pill-btn primary" id="cmpRunBtn" onclick="executeCompress()">Compress ${state.opt.items.length} File(s)</button>
        </div>
      </div>
    `;
    refreshOptimizeEstimate(false);
  }else{
    body.innerHTML=`
      <div class="est-box">
        <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
          <span>Input: <b>${ow} × ${oh}</b> (${fmt(first.sizeBytes)})</span>
          <span>Output: <b id="upOutDims" style="color:var(--primary);font-size:14px">${ow*2} × ${oh*2}</b></span>
        </div>
        <div style="font-size:11px;color:var(--muted)" id="upNote">Lanczos Detail-Preserving Resampling + Contrast-Adaptive Unsharp Filter</div>
      </div>
      <div class="section-card">
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
          <label style="font-size:12px">Upscale Target
            <select class="input" id="upPreset" onchange="updateUpscalePreviewInfo()" style="width:100%;margin-top:4px">
              <option value="2x">2x Resolution Boost</option>
              <option value="4k">4K UHD (3840 wide)</option>
              <option value="1440p">1440p QHD (2560 wide)</option>
              <option value="1080p">1080p FHD (1920 wide)</option>
            </select>
          </label>
          <label style="font-size:12px">Detail Sharpening
            <select class="input" id="upSharpen" style="width:100%;margin-top:4px">
              <option value="0.4">Subtle (0.4)</option>
              <option value="0.9" selected>Crisp Enhancement (0.9)</option>
              <option value="1.4">Ultra Sharp (1.4)</option>
            </select>
          </label>
        </div>
        <label style="display:flex;justify-content:space-between;font-size:12px"><span>Temporal Denoise (Clean Compression Artifacts)</span><input type="checkbox" id="upDenoise"/></label>
        <label style="display:flex;justify-content:space-between;font-size:12px"><span>Smooth 60 FPS Motion Interpolation (Video Only)</span><input type="checkbox" id="upFps60"/></label>
        <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
          <select class="input" id="upSaveMode" style="padding:5px 9px">
            <option value="copy">Save as Copy (_upscaled)</option>
            <option value="overwrite">Save Over Original</option>
          </select>
          <span style="flex:1"></span>
          <button class="pill-btn primary" id="upRunBtn" onclick="executeUpscale()">Upscale ${state.opt.items.length} File(s)</button>
        </div>
      </div>
    `;
    updateUpscalePreviewInfo();
  }
}
function updateUpscalePreviewInfo(){
  const first=state.opt.items[0]; if(!first) return;
  const ow=first.meta?.width||1280, oh=first.meta?.height||720;
  const p=document.getElementById("upPreset")?.value||"2x";
  let nw=ow*2, nh=oh*2;
  if(p==="4k"){ nw=3840; nh=Math.round(oh*(3840/Math.max(1,ow))); }
  else if(p==="1440p"){ nw=2560; nh=Math.round(oh*(2560/Math.max(1,ow))); }
  else if(p==="1080p"){ nw=1920; nh=Math.round(oh*(1920/Math.max(1,ow))); }
  const lbl=document.getElementById("upOutDims");
  if(lbl) lbl.textContent=`${nw} × ${nh} (${p.toUpperCase()})`;
}
async function refreshOptimizeEstimate(sampleProbe){
  const first=state.opt.items[0]; if(!first) return;
  const afterEl=document.getElementById("cmpEstAfter"), pctEl=document.getElementById("cmpEstPct"), barEl=document.getElementById("cmpEstBar"), noteEl=document.getElementById("cmpEstNote");
  if(!afterEl) return;
  if(sampleProbe) afterEl.textContent="Sampling 1.5s encode...";
  try{
    const est = await hostApi(state.opt.hostId, "/api/media/compress-estimate", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({
        path: first.path,
        targetHeight: Number(document.getElementById("cmpHeight").value)||0,
        crf: Number(document.getElementById("cmpCrf").value)||24,
        codec: document.getElementById("cmpCodec").value,
        audioKbps: Number(document.getElementById("cmpAudio").value),
        targetMb: Number(document.getElementById("cmpTargetMb").value)||0,
        sampleProbe: !!sampleProbe,
      })
    });
    const ratio = est.estimatedBytes / Math.max(1, first.sizeBytes);
    const totalBefore = state.opt.items.reduce((a,x)=>a+(x.sizeBytes||0),0);
    const totalEst = state.opt.items.length>1 ? Math.round(totalBefore*ratio) : est.estimatedBytes;
    const savedPct = Math.round((1 - (totalEst / Math.max(1, totalBefore))) * 100);
    afterEl.textContent = "~" + fmt(totalEst);
    afterEl.style.color = savedPct >= 0 ? "var(--success)" : "var(--warn)";
    pctEl.textContent = savedPct >= 0 ? `Saves ~${savedPct}%` : `Larger (+${Math.abs(savedPct)}%)`;
    barEl.style.width = Math.max(4, Math.min(100, Math.round((totalEst / Math.max(1, totalBefore))*100))) + "%";
    noteEl.textContent = `Out: ${est.newWidth}×${est.newHeight} • ${est.encoderUsed} (${est.method==="sample_probe"?"1.5s Sample Probe Verified":est.method})`;
  }catch(e){ afterEl.textContent="Estimate unavailable"; }
}
async function executeCompress(){
  const btn=document.getElementById("cmpRunBtn"); btn.disabled=true; btn.textContent="Compressing...";
  let done=0, saved=0;
  try{
    for(const item of state.opt.items){
      const res=await hostApi(state.opt.hostId,"/api/media/compress",{
        method:"POST", headers:{"Content-Type":"application/json"},
        body:JSON.stringify({
          path:item.path,
          targetHeight:Number(document.getElementById("cmpHeight").value)||0,
          crf:Number(document.getElementById("cmpCrf").value)||24,
          codec:document.getElementById("cmpCodec").value,
          audioKbps:Number(document.getElementById("cmpAudio").value),
          targetMb:Number(document.getElementById("cmpTargetMb").value)||0,
          saveMode:document.getElementById("cmpSaveMode").value,
          recycleOriginal:true,
        })
      });
      done++; saved += (res.savedBytes||0);
    }
    showToast(`Compressed ${done} file(s)! ${saved>0?"Reclaimed "+fmt(saved):""}`);
    closeDialog("optimizeModal");
    if(state.currentPath) await loadFolder(state.currentPath);
    if(state.split.open && state.split.currentPath) await loadSplitFolder(state.split.currentPath);
  }catch(e){showToast("Compression failed: "+e.message)}
  finally{ btn.disabled=false; btn.textContent=`Compress ${state.opt.items.length} File(s)`; }
}
async function executeUpscale(){
  const btn=document.getElementById("upRunBtn"); btn.disabled=true; btn.textContent="Upscaling...";
  let done=0;
  try{
    for(const item of state.opt.items){
      await hostApi(state.opt.hostId,"/api/media/upscale",{
        method:"POST", headers:{"Content-Type":"application/json"},
        body:JSON.stringify({
          path:item.path,
          preset:document.getElementById("upPreset").value,
          sharpen:Number(document.getElementById("upSharpen").value),
          denoise:document.getElementById("upDenoise").checked,
          fps60:document.getElementById("upFps60").checked,
          crf:19,
          saveMode:document.getElementById("upSaveMode").value,
          recycleOriginal:true,
        })
      });
      done++;
    }
    showToast(`Upscaled & enhanced ${done} file(s)!`);
    closeDialog("optimizeModal");
    if(state.currentPath) await loadFolder(state.currentPath);
    if(state.split.open && state.split.currentPath) await loadSplitFolder(state.split.currentPath);
  }catch(e){showToast("Upscale failed: "+e.message)}
  finally{ btn.disabled=false; btn.textContent=`Upscale ${state.opt.items.length} File(s)`; }
}

/* Cross-Device Move & Copy */
async function openTransferModal(items, move, sourceHostOverride=""){
  state.transferItems=items; state.transferMove=move; state.transferSourceHostId=sourceHostOverride||state.activeHostId; state.transferHostId=state.transferSourceHostId;
  document.getElementById("transferTitle").textContent=`${move?"Move":"Copy"} ${items.length} item(s)`;
  document.getElementById("transferModal").classList.add("open");
  setTransferMove(move);
  await pickTransferHost(state.transferHostId);
}
function setTransferMove(m){state.transferMove=m;document.getElementById("modeMoveChip").classList.toggle("active",m);document.getElementById("modeCopyChip").classList.toggle("active",!m)}
async function pickTransferHost(hid){
  state.transferHostId=hid;
  const chips=document.getElementById("transferHostChips"); chips.innerHTML="";
  state.hosts.forEach(h=>{const c=document.createElement("button");c.className="chip"+(h.hostId===hid?" active":"");c.textContent=h.label||h.name;c.onclick=()=>pickTransferHost(h.hostId);chips.appendChild(c)});
  const r=await hostApi(hid,"/api/roots");
  const first=(r.drives&&r.drives[0]?.path)||(r.quickFolders&&r.quickFolders[0]?.path)||"";
  if(first)await loadTransferDir(first);
}
async function loadTransferDir(path){
  state.transferPath=path; document.getElementById("transferPathLbl").textContent=path;
  const d=await hostApi(state.transferHostId,"/api/list?"+new URLSearchParams({path,sort:"name",order:"asc",filter:"all"}).toString());
  state.transferParent=d.parentPath||"";
  const box=document.getElementById("transferFoldersList"); box.innerHTML="";
  (d.folders||[]).forEach(f=>{const b=document.createElement("button");b.className="tree-row";b.innerHTML=`<div class="tree-row-top"><span>📁</span><span class="tree-row-name">${f.name}</span></div>`;b.onclick=()=>loadTransferDir(f.path);box.appendChild(b)});
}
function transferUp(){if(state.transferParent)loadTransferDir(state.transferParent)}
async function createTransferSubfolder(){const n=prompt("New folder name:");if(!n)return;await hostApi(state.transferHostId,"/api/folder/create",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({parentDir:state.transferPath,name:n})});loadTransferDir(state.transferPath)}
async function executeTransfer(){
  const paths=state.transferItems.map(i=>i.path);
  const srcHid=state.transferSourceHostId||state.activeHostId;
  try{
    if(state.transferHostId===srcHid){
      await hostApi(srcHid,"/api/file/transfer",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({paths,destinationDir:state.transferPath,move:state.transferMove,conflict:"rename"})});
    }else{
      await sysFetch("/api/fleet/relay",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({sourceHostId:srcHid,targetHostId:state.transferHostId,paths,destinationDir:state.transferPath,move:state.transferMove})});
    }
    showToast((state.transferMove?"Moved ":"Copied ")+paths.length+" item(s)");
    closeDialog("transferModal"); clearSelection();
    if(state.currentPath)await loadFolder(state.currentPath);
    if(state.split.open && state.split.currentPath)await loadSplitFolder(state.split.currentPath);
  }catch(e){showToast("Transfer failed: "+e.message)}
}

/* Upload + Drag & Drop */
async function uploadLocalFiles(files){
  if(!files||!files.length||!state.currentPath)return;
  for(const f of files){
    showToast("Sending "+f.name+"...");
    await sysFetch(hostUrl(state.activeHostId,"/api/file/upload?"+new URLSearchParams({dir:state.currentPath,name:f.name,conflict:"rename"}).toString()),{method:"POST",body:f});
  }
  showToast("Uploaded "+files.length+" file(s)"); await loadFolder(state.currentPath);
}
window.addEventListener("dragover",e=>{if(e.dataTransfer?.types?.includes("Files")){e.preventDefault();document.getElementById("dropMask").classList.add("open")}});
window.addEventListener("dragleave",e=>{if(!e.relatedTarget)document.getElementById("dropMask").classList.remove("open")});
window.addEventListener("drop",e=>{if(e.dataTransfer?.files?.length){e.preventDefault();document.getElementById("dropMask").classList.remove("open");uploadLocalFiles(e.dataTransfer.files)}});

/* Comprehensive Settings Modal (Matching All Android Settings + Desktop Controls) */
function openSettingsModal(){
  const ip=state.localIps[0]||"127.0.0.1";
  const body=document.getElementById("settingsBody");
  const pals=[["","Slate Blue"],["pal-sage","Sage Mint"],["pal-sand","Warm Sand"],["pal-rose","Muted Rose"],["pal-stone","Stone Neutral"]];
  body.innerHTML=`
    <div class="section-card">
      <strong style="font-size:13px">Appearance & Theme</strong>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Pure #000000 OLED Black Background</span><input type="checkbox" ${prefs.pureBlack?"checked":""} onchange="setPref('pureBlack',this.checked)"/></label>
      <div class="chip-row">${pals.map(([v,n])=>`<button class="chip ${prefs.palette===v?"active":""}" onclick="setPref('palette','${v}');openSettingsModal()">${n}</button>`).join("")}</div>
    </div>
    <div class="section-card">
      <strong style="font-size:13px">Panels & Grid Layout</strong>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Show Left Navigation Rail</span><input type="checkbox" ${prefs.showRail?"checked":""} onchange="setPref('showRail',this.checked)"/></label>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Show Right Details Inspector</span><input type="checkbox" ${prefs.showInspector?"checked":""} onchange="setPref('showInspector',this.checked)"/></label>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Show Drive Gauges in Left Panel</span><input type="checkbox" ${prefs.showDriveStrips?"checked":""} onchange="setPref('showDriveStrips',this.checked);renderDriveAndQuickStrips()"/></label>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Show Quick Folders in Left Panel</span><input type="checkbox" ${prefs.showQuickFolders?"checked":""} onchange="setPref('showQuickFolders',this.checked);renderDriveAndQuickStrips()"/></label>
      <div><div style="display:flex;justify-content:space-between;font-size:12px"><span>Grid Columns (0 = Auto)</span><b>${prefs.gridCols===0?"Auto":prefs.gridCols}</b></div><input type="range" min="0" max="8" value="${prefs.gridCols}" oninput="setPref('gridCols',Number(this.value));openSettingsModal()" style="width:100%"/></div>
      <div><div style="display:flex;justify-content:space-between;font-size:12px"><span>Auto Card Width</span><b>${prefs.cardMinPx} px</b></div><input type="range" min="130" max="340" step="10" value="${prefs.cardMinPx}" oninput="setPref('cardMinPx',Number(this.value))" style="width:100%"/></div>
    </div>
    <div class="section-card">
      <strong style="font-size:13px">Cards & Thumbnails</strong>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Show File Sizes</span><input type="checkbox" ${prefs.showSizes?"checked":""} onchange="setPref('showSizes',this.checked)"/></label>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Show Duration Badges</span><input type="checkbox" ${prefs.showDuration?"checked":""} onchange="setPref('showDuration',this.checked)"/></label>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Show Resolution Badges (4K/1080p)</span><input type="checkbox" ${prefs.showResolution?"checked":""} onchange="setPref('showResolution',this.checked)"/></label>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Show Filenames on Grid Cards</span><input type="checkbox" ${prefs.showFilenames?"checked":""} onchange="setPref('showFilenames',this.checked)"/></label>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Hover Storyboard Scrubbing on Video Cards</span><input type="checkbox" ${prefs.hoverStoryboard?"checked":""} onchange="setPref('hoverStoryboard',this.checked)"/></label>
    </div>
    <div class="section-card">
      <strong style="font-size:13px">VLC Player & Studio Trimmer Defaults</strong>
      <div style="display:flex;gap:10px;align-items:center;justify-content:space-between;font-size:12px"><span>Seek Step Seconds</span><div>${[5,10,15,30].map(s=>`<button class="chip ${prefs.seekStep===s?"active":""}" onclick="setPref('seekStep',${s});openSettingsModal()">${s}s</button>`).join(" ")}</div></div>
      <div><div style="display:flex;justify-content:space-between;font-size:12px"><span>Default Compression Quality (CRF)</span><b>${prefs.defaultCrf}</b></div><input type="range" min="16" max="32" value="${prefs.defaultCrf}" oninput="setPref('defaultCrf',Number(this.value))" style="width:100%"/></div>
      <label style="display:flex;justify-content:space-between;font-size:12px"><span>Send Replaced / Deleted Originals to Recycle Bin</span><input type="checkbox" ${prefs.recycleDefault?"checked":""} onchange="setPref('recycleDefault',this.checked)"/></label>
    </div>
    <div class="section-card">
      <strong style="font-size:13px">Google Drive Backup Destination</strong>
      <div style="font-size:12px;color:var(--muted)">
        ${state.gdrive?.driveAppInstalled ? "Google Drive for Desktop detected" : "Install Google Drive for Desktop or point to any Google Drive sync folder / rclone remote"}
      </div>
      <input class="input" id="gdDirInput" placeholder="e.g. G:\\My Drive\\DeckVault Backup" value="${(state.gdrive?.savedDir||"").replace(/"/g,'&quot;')}" style="width:100%"/>
      <input class="input" id="gdRemoteInput" placeholder="Optional rclone remote (e.g. gdrive:)" value="${(state.gdrive?.savedRemote||"").replace(/"/g,'&quot;')}" style="width:100%"/>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="pill-btn primary" onclick="saveGDriveConfig()">Save Google Drive Settings</button>
        <a href="https://drive.google.com/drive/my-drive" target="_blank"><button class="pill-btn" type="button">Open Google Drive Web</button></a>
      </div>
    </div>
    <div class="section-card">
      <strong style="font-size:13px">This PC Companion Server & Paired Devices</strong>
      <div style="font-size:12px;color:var(--muted)">Wi-Fi IPs: <b>${state.localIps.join(", ")||ip}</b> : 48720<br/>Pairing PIN: <b style="color:var(--primary)">${state.localPin}</b><br/>Browser / iPhone URL: <b>http://${ip}:48720/</b><br/>Android APK URL: <b>http://${ip}:48720/DeckVault.apk</b></div>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="pill-btn" onclick="rotatePcPin()">Generate New PIN</button>
        <button class="pill-btn" onclick="rescanActive()">Rescan Disks</button>
        <button class="pill-btn" onclick="ensureFirewall()">Allow Firewall Port</button>
      </div>
    </div>
  `;
  document.getElementById("settingsModal").classList.add("open");
}

async function rotatePcPin(){ const r=await sysFetch("/api/desktop/pin/new",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}); state.localPin=r.pin; showToast("New PC PIN: "+r.pin); openSettingsModal(); updateFooter(document.getElementById("footerLeft").textContent); }
async function ensureFirewall(){ const r=await sysFetch("/api/desktop/firewall",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}); showToast(r.message||"Firewall checked"); }

async function saveGDriveConfig(){
  const savedDir=document.getElementById("gdDirInput").value.trim();
  const savedRemote=document.getElementById("gdRemoteInput").value.trim();
  state.gdrive=await sysFetch("/api/gdrive/configure",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({savedDir,savedRemote})});
  showToast("Google Drive backup destination saved");
}

function backupSelectionToGDrive(){
  const paths=Array.from(state.selected);
  if(paths.length) backupPathsToGDrive(paths);
}

async function backupPathsToGDrive(paths, hostOverride=""){
  if(!paths || !paths.length) return;
  try{
    state.gdrive = await sysFetch("/api/gdrive/status");
    const hasTarget = (state.gdrive.savedDir || state.gdrive.savedRemote);
    if(!hasTarget){
      showToast("Set your Google Drive folder (e.g. G:\\My Drive\\DeckVault Backup) or rclone remote first");
      openCloudModal();
      return;
    }
    showToast(`Backing up ${paths.length} item(s) to Google Drive...`);
    const res = await sysFetch("/api/gdrive/backup", {
      method:"POST", headers:{"Content-Type":"application/json"},
      body:JSON.stringify({sourceHostId:hostOverride||state.activeHostId, paths})
    });
    showToast(`Backed up ${res.backedUpCount} item(s) -> ${res.destination}`);
    clearSelection();
  }catch(e){showToast("Google Drive backup: "+e.message)}
}

/* Adjustable Left, Split & Right Panel Resizers */
(()=>{
  const lr=document.getElementById("leftResizer"), tp=document.getElementById("treePane");
  const sr=document.getElementById("splitResizer"), sp=document.getElementById("splitPane");
  const rr=document.getElementById("rightResizer"), ip=document.getElementById("inspPane");
  let mode=null;
  lr.onmousedown=()=>{mode="left"};
  if(sr) sr.onmousedown=()=>{mode="split"};
  rr.onmousedown=()=>{mode="right"};
  window.addEventListener("mouseup",()=>{if(mode){savePrefs();mode=null}});
  window.addEventListener("mousemove",(e)=>{
    if(mode==="left"){
      const railW=prefs.showRail?66:0;
      prefs.folderWidthPx=Math.max(200,Math.min(540,e.clientX-railW));
      tp.style.width=prefs.folderWidthPx+"px";
    }else if(mode==="split" && state.split.open){
      const rEdge=sp.getBoundingClientRect().right;
      state.split.widthPx=Math.max(290,Math.min(window.innerWidth*0.62, rEdge-e.clientX));
      sp.style.flex=`0 0 ${state.split.widthPx}px`;
    }else if(mode==="right"){
      prefs.inspectorWidthPx=Math.max(210,Math.min(440,window.innerWidth-e.clientX));
      ip.style.width=prefs.inspectorWidthPx+"px";
    }
  });
})();

/* VLC & File Browser Keyboard Shortcuts */
window.addEventListener("keydown",(e)=>{
  const tag=(e.target?.tagName||"").toUpperCase();
  const typing = tag==="INPUT"||tag==="TEXTAREA"||tag==="SELECT";
  const vidOpen = document.getElementById("videoOverlay").classList.contains("open");
  if(vidOpen && !typing){
    if(e.code==="Space"){e.preventDefault();togglePlay();}
    else if(e.key==="ArrowLeft"){e.preventDefault();seekVideoRel(-prefs.seekStep);}
    else if(e.key==="ArrowRight"){e.preventDefault();seekVideoRel(prefs.seekStep);}
    else if(e.key===","){e.preventDefault();nudgeFrame(-1);}
    else if(e.key==="."){e.preventDefault();nudgeFrame(1);}
    else if(e.key==="["){e.preventDefault();setTrimFromPlayhead(true);}
    else if(e.key==="]"){e.preventDefault();setTrimFromPlayhead(false);}
    else if(e.key==="c"||e.key==="C"){e.preventDefault();toggleCropOverlay();}
    else if(e.key==="p"||e.key==="P"){e.preventDefault();toggleCropPreview();}
    else if(e.key==="m"||e.key==="M"){e.preventDefault();toggleMute();}
    else if(e.key==="f"||e.key==="F"){e.preventDefault();toggleCinemaFullscreen();}
    else if(e.key==="Escape"){e.preventDefault();closeVideoOverlay();}
    return;
  }
  if(!typing){
    if(e.ctrlKey && e.key.toLowerCase()==="a"){e.preventDefault();selectAllFiles();}
    else if(e.ctrlKey && e.key.toLowerCase()==="c" && (state.selected.size>0 || state.focusedFile)){
      e.preventDefault();
      const paths=state.selected.size>0 ? Array.from(state.selected) : [state.focusedFile.path];
      copyFilesToWindowsClipboard(state.activeHostId, paths);
    }
    else if(e.ctrlKey && e.key.toLowerCase()==="f"){e.preventDefault();document.getElementById("searchBox").focus();}
    else if(e.key==="Backspace"){e.preventDefault();goUp();}
    else if(e.key==="F2" && state.focusedFile){e.preventDefault();renamePrompt(state.focusedFile);}
    else if(e.key==="Delete" && (state.selected.size>0 || state.focusedFile)){
      e.preventDefault();
      deletePaths(state.selected.size>0 ? Array.from(state.selected) : [state.focusedFile.path], !!e.shiftKey);
    }
    else if(e.key==="Escape"){
      clearSelection();
      ["allDrivesOverlay","imageOverlay","docOverlay"].forEach(closeOverlay);
      ["devicesModal","transferModal","settingsModal","cloudModal","optimizeModal"].forEach(closeDialog);
    }
  }
});

initApp();
</script>
</body>
</html>"""

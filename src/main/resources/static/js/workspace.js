// ===================================================================
//  CollabSpace · Workspace JavaScript
//  Features: whiteboard (draw/erase/undo), real-time sync via STOMP,
//  multi-format export, auto-save, char counter, recent rooms
// ===================================================================

const appRoot    = document.querySelector('.workspace-layout');
const ROOM_ID    = appRoot.dataset.roomId;
const USERNAME   = appRoot.dataset.username;
const IS_AUTH    = appRoot.dataset.auth === 'true';
const SENDER_ID  = `${USERNAME}-${Math.random().toString(36).slice(2, 8)}`;

function scopedAuthRecentKey() {
    const scope = (USERNAME || 'unknown').toLowerCase().replace(/[^a-z0-9@._-]/g, '_');
    return `cw_authenticated_recent_rooms:${scope}`;
}

// ── DOM refs ────────────────────────────────────────────────────────
const canvas         = document.getElementById('whiteboardCanvas');
const ctx            = canvas.getContext('2d');
const colorPicker    = document.getElementById('colorPicker');
const brushSizeInput = document.getElementById('brushSize');
const brushPreviewDot= document.getElementById('brushPreviewDot');
const clearCanvasBtn = document.getElementById('clearCanvasBtn');
const eraserBtn      = document.getElementById('eraserBtn');
const undoBtn        = document.getElementById('undoBtn');
const sharedText     = document.getElementById('sharedText');
const copyRoomBtn    = document.getElementById('copyRoomBtn');
const copyLinkBtn    = document.getElementById('copyLinkBtn');
const saveIndicator  = document.getElementById('saveIndicator');
const charCount      = document.getElementById('charCount');
const lastSaved      = document.getElementById('lastSaved');
const statusDot      = document.getElementById('statusDot');
const statusDot2     = document.getElementById('statusDot2');
const connStatus     = document.getElementById('connStatus');

// ── State ───────────────────────────────────────────────────────────
let stompClient       = null;
let drawing           = false;
let eraserMode        = false;
let lastX = 0, lastY  = 0;
let currentColor      = '#111111';
let currentBrush      = 4;
let applyingRemoteText= false;
let textSyncTimeout   = null;
let saveTimeout       = null;
let drawingSaveTimeout= null;

const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

function withCsrf(headers = {}) {
    if (csrfToken) {
        headers[csrfHeader] = csrfToken;
    }
    headers['X-Requested-With'] = 'XMLHttpRequest';
    return headers;
}

function logSave(action, status) {
    console.log(`[CollabSpace] ${action}: ${status}`);
}

// Undo stack: array of ImageData snapshots
const undoStack = [];
const MAX_UNDO  = 30;

// ── Canvas setup ────────────────────────────────────────────────────
ctx.lineCap  = 'round';
ctx.lineJoin = 'round';

function saveSnapshot() {
    if (undoStack.length >= MAX_UNDO) undoStack.shift();
    undoStack.push(ctx.getImageData(0, 0, canvas.width, canvas.height));
}

function undo(broadcast = true) {
    if (!undoStack.length) return;
    ctx.putImageData(undoStack.pop(), 0, 0);
    queueDrawingSave();
    if (broadcast && stompClient?.connected) {
        stompClient.send(`/app/room/${ROOM_ID}/draw`, {}, JSON.stringify({ senderId: SENDER_ID, type: 'undo' }));
    }
}

function clearCanvas(broadcast = true) {
    saveSnapshot();
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    queueDrawingSave();
    if (broadcast && stompClient?.connected) {
        stompClient.send(`/app/room/${ROOM_ID}/draw`, {}, JSON.stringify({ senderId: SENDER_ID, type: 'clear' }));
    }
}

// ── Colour swatches ─────────────────────────────────────────────────
document.querySelectorAll('.swatch').forEach(swatch => {
    swatch.addEventListener('click', () => {
        document.querySelectorAll('.swatch').forEach(s => s.classList.remove('active'));
        swatch.classList.add('active');
        currentColor = swatch.dataset.color;
        colorPicker.value = currentColor;
        setEraserMode(false);
    });
});

colorPicker.addEventListener('input', () => {
    currentColor = colorPicker.value;
    document.querySelectorAll('.swatch').forEach(s => s.classList.remove('active'));
    setEraserMode(false);
    updateBrushPreview();
});

// ── Brush size preview ──────────────────────────────────────────────
function updateBrushPreview() {
    const size = parseInt(brushSizeInput.value);
    currentBrush = size;
    const dot = brushPreviewDot;
    const clamped = Math.min(size, 30);
    dot.style.width  = clamped + 'px';
    dot.style.height = clamped + 'px';
    dot.style.background = eraserMode ? '#fff' : currentColor;
}

brushSizeInput.addEventListener('input', updateBrushPreview);
updateBrushPreview();

// ── Eraser toggle ────────────────────────────────────────────────────
function setEraserMode(on) {
    eraserMode = on;
    eraserBtn.classList.toggle('active', on);
    canvas.classList.toggle('eraser-mode', on);
    updateBrushPreview();
}

eraserBtn.addEventListener('click', () => setEraserMode(!eraserMode));

// ── Clear canvas ────────────────────────────────────────────────────
clearCanvasBtn.addEventListener('click', () => clearCanvas(true));

// ── Undo ────────────────────────────────────────────────────────────
undoBtn.addEventListener('click', () => undo(true));

document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'z') { e.preventDefault(); undo(true); }
    if (e.key === 'e' || e.key === 'E') {
        if (!e.target.matches('textarea, input')) setEraserMode(!eraserMode);
    }
});

// ── Drawing ──────────────────────────────────────────────────────────
function drawStroke(sX, sY, eX, eY, color, brushSize, eraser) {
    ctx.globalCompositeOperation = eraser ? 'destination-out' : 'source-over';
    ctx.strokeStyle = eraser ? 'rgba(0,0,0,1)' : color;
    ctx.lineWidth   = eraser ? brushSize * 2 : brushSize;
    ctx.beginPath();
    ctx.moveTo(sX, sY);
    ctx.lineTo(eX, eY);
    ctx.stroke();
    ctx.globalCompositeOperation = 'source-over';
}

function pointerPos(event) {
    const rect = canvas.getBoundingClientRect();
    return {
        x: (event.clientX - rect.left) * (canvas.width  / rect.width),
        y: (event.clientY - rect.top)  * (canvas.height / rect.height)
    };
}

canvas.addEventListener('pointerdown', e => {
    drawing = true;
    canvas.setPointerCapture(e.pointerId);
    const pos = pointerPos(e);
    saveSnapshot();
    lastX = pos.x;
    lastY = pos.y;
});

canvas.addEventListener('pointermove', e => {
    if (!drawing) return;
    const pos = pointerPos(e);
    const msg = {
        senderId: SENDER_ID,
        startX: lastX, startY: lastY,
        endX:   pos.x, endY:   pos.y,
        color:  currentColor,
        brushSize: currentBrush,
        eraser: eraserMode
    };
    drawStroke(msg.startX, msg.startY, msg.endX, msg.endY, msg.color, msg.brushSize, msg.eraser);
    queueDrawingSave();
    if (stompClient?.connected) {
        stompClient.send(`/app/room/${ROOM_ID}/draw`, {}, JSON.stringify(msg));
    }
    lastX = pos.x;
    lastY = pos.y;
});

window.addEventListener('pointerup', () => {
    if (drawing) {
        queueDrawingSave();
    }
    drawing = false;
});

// ── WebSocket ────────────────────────────────────────────────────────
function setConnectionStatus(state) {
    // state: 'connecting' | 'connected' | 'disconnected'
    const map = {
        connecting:   { color: 'connecting', label: 'Connecting…' },
        connected:    { color: '',           label: 'Connected' },
        disconnected: { color: 'disconnected', label: 'Disconnected' }
    };
    const s = map[state] || map.disconnected;
    [statusDot, statusDot2].forEach(d => {
        if (!d) return;
        d.className = 'status-dot' + (s.color ? ' ' + s.color : '');
    });
    if (connStatus) connStatus.textContent = s.label;
}

function connectSocket() {
    setConnectionStatus('connecting');
    try {
        const sock = new SockJS('/ws');
        stompClient = Stomp.over(sock);
        stompClient.debug = null;

        stompClient.connect({}, () => {
            setConnectionStatus('connected');

            stompClient.subscribe(`/topic/room/${ROOM_ID}/draw`, payload => {
                const m = JSON.parse(payload.body);
                if (m.senderId === SENDER_ID) return;
                
                if (m.type === 'clear') {
                    clearCanvas(false);
                } else if (m.type === 'undo') {
                    undo(false);
                } else {
                    drawStroke(m.startX, m.startY, m.endX, m.endY, m.color, m.brushSize, m.eraser);
                    queueDrawingSave();
                }
            });

            stompClient.subscribe(`/topic/room/${ROOM_ID}/text`, payload => {
                const m = JSON.parse(payload.body);
                if (m.senderId === SENDER_ID) return;
                applyingRemoteText = true;
                sharedText.value = m.content || '';
                applyingRemoteText = false;
                updateCharCount();
            });
        }, () => {
            setConnectionStatus('disconnected');
            // Reconnect after 3s
            setTimeout(connectSocket, 3000);
        });
    } catch (err) {
        setConnectionStatus('disconnected');
        setTimeout(connectSocket, 4000);
    }
}

// ── Shared notepad ───────────────────────────────────────────────────
function updateCharCount() {
    const text  = sharedText.value;
    const chars = text.length;
    const words = text.trim() ? text.trim().split(/\s+/).length : 0;
    charCount.textContent = `${chars.toLocaleString()} chars · ${words.toLocaleString()} words`;
    // Track total for stats
    const prev = parseInt(localStorage.getItem('cw_total_chars') || '0');
    localStorage.setItem('cw_total_chars', Math.max(prev, chars));
}

sharedText.addEventListener('input', () => {
    updateCharCount();
    if (applyingRemoteText) return;

    if (stompClient?.connected) {
        clearTimeout(textSyncTimeout);
        textSyncTimeout = setTimeout(() => {
            stompClient.send(`/app/room/${ROOM_ID}/text`, {}, JSON.stringify({
                senderId: SENDER_ID,
                content:  sharedText.value
            }));
        }, 120);
    }

    // Auto-save to backend
    setSaveState('saving');
    clearTimeout(saveTimeout);
    saveTimeout = setTimeout(() => {
        const payload = JSON.stringify({ content: sharedText.value || '' });
        fetch(`/api/rooms/${ROOM_ID}/document`, {
            method:  'POST',
            headers: withCsrf({ 'Content-Type': 'application/json' }),
            body:    payload
        })
        .then(async r => {
            const text = await r.text();
            if (r.ok) { setSaveState('saved'); logSave('Text', 'saved'); }
            else { 
                setSaveState('error');
                logSave('Text', `HTTP ${r.status}: ${text.substring(0, 100)}`);
            }
        })
        .catch(e => { setSaveState('error'); logSave('Text', 'network error: ' + e.message); });
    }, 600);
});

function setSaveState(state) {
    if (!saveIndicator) return;
    saveIndicator.className = '';
    if (state === 'saving') {
        saveIndicator.className = 'saving';
        saveIndicator.textContent = '● Saving…';
    } else if (state === 'saved') {
        saveIndicator.className = 'saved';
        saveIndicator.textContent = '✓ Saved';
        if (lastSaved) lastSaved.textContent = 'Last saved ' + new Date().toLocaleTimeString();
    } else if (state === 'error') {
        saveIndicator.className = 'error';
        saveIndicator.textContent = '⚠ Save failed';
    } else {
        saveIndicator.textContent = '● Idle';
    }
}

function queueDrawingSave() {
    setSaveState('saving');
    clearTimeout(drawingSaveTimeout);
    drawingSaveTimeout = setTimeout(() => {
        try {
            const drawingData = canvas.toDataURL('image/png', 0.7);
            const payload = JSON.stringify({ drawingData });
            logSave('Drawing', `sending ${(payload.length/1024).toFixed(1)}KB`);
            
            fetch(`/api/rooms/${ROOM_ID}/drawing`, {
                method: 'POST',
                headers: withCsrf({ 'Content-Type': 'application/json' }),
                body: payload
            })
            .then(async r => {
                const text = await r.text();
                if (r.ok) { setSaveState('saved'); logSave('Drawing', 'saved'); }
                else { 
                    setSaveState('error');
                    logSave('Drawing', `HTTP ${r.status}: ${text.substring(0, 100)}`);
                }
            })
            .catch(e => { setSaveState('error'); logSave('Drawing', 'network error: ' + e.message); });
        } catch (e) {
            setSaveState('error');
            logSave('Drawing', 'exception: ' + e.message);
        }
    }, 1200);
}

function restoreDrawing(drawingData) {
    if (!drawingData) return;
    const img = new Image();
    img.onload = () => {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    };
    img.src = drawingData;
}

function flushPendingSaves() {
    try {
        clearTimeout(saveTimeout);
        clearTimeout(drawingSaveTimeout);
        clearTimeout(textSyncTimeout);

        const textContent = sharedText.value || '';
        const drawingData = canvas.toDataURL('image/png', 0.7);

        logSave('FLUSH', 'saving text + drawing on page leave');

        const headers = withCsrf({ 'Content-Type': 'application/json' });
        const docPayload = JSON.stringify({ content: textContent });
        const drawPayload = JSON.stringify({ drawingData });
        
        logSave('FLUSH', `text=${docPayload.length}, drawing=${(drawPayload.length/1024).toFixed(1)}KB`);

        if (navigator.sendBeacon) {
            try {
                const docOk = navigator.sendBeacon(
                    `/api/rooms/${ROOM_ID}/document`,
                    new Blob([docPayload], { type: 'application/json' })
                );
                const drawOk = navigator.sendBeacon(
                    `/api/rooms/${ROOM_ID}/drawing`,
                    new Blob([drawPayload], { type: 'application/json' })
                );
                logSave('FLUSH', `sendBeacon doc=${docOk} draw=${drawOk}`);
                if (docOk && drawOk) {
                    return;
                }
            } catch (e) {
                logSave('FLUSH', 'sendBeacon failed: ' + e.message);
            }
        }

        fetch(`/api/rooms/${ROOM_ID}/document`, {
            method: 'POST',
            headers,
            body: docPayload,
            keepalive: true
        }).then(r => logSave('FLUSH-TEXT', `HTTP ${r.status}`)).catch(e => logSave('FLUSH-TEXT', 'error: ' + e.message));

        fetch(`/api/rooms/${ROOM_ID}/drawing`, {
            method: 'POST',
            headers,
            body: drawPayload,
            keepalive: true
        }).then(r => logSave('FLUSH-DRAW', `HTTP ${r.status}`)).catch(e => logSave('FLUSH-DRAW', 'error: ' + e.message));
    } catch (err) {
        logSave('FLUSH', 'exception: ' + err.message);
    }
}

// Markdown bold helper
document.getElementById('formatMdBtn')?.addEventListener('click', () => {
    const start = sharedText.selectionStart;
    const end   = sharedText.selectionEnd;
    const sel   = sharedText.value.substring(start, end);
    if (!sel) return;
    const replacement = `**${sel}**`;
    sharedText.setRangeText(replacement, start, end, 'end');
    sharedText.dispatchEvent(new Event('input'));
});

// Clear notes
document.getElementById('clearNoteBtn')?.addEventListener('click', () => {
    if (confirm('Clear all notes in this room?')) {
        sharedText.value = '';
        sharedText.dispatchEvent(new Event('input'));
        updateCharCount();
    }
});

// ── Copy room ID / link ──────────────────────────────────────────────
function flashBtn(btn, msg, restore) {
    const orig = btn.textContent;
    btn.textContent = msg;
    setTimeout(() => btn.textContent = restore || orig, 1500);
}

copyRoomBtn?.addEventListener('click', async () => {
    await navigator.clipboard.writeText(ROOM_ID).catch(() => null);
    flashBtn(copyRoomBtn, '✓ Copied!', '📋 Copy ID');
});

copyLinkBtn?.addEventListener('click', async () => {
    const url = location.origin + '/workspace/' + ROOM_ID;
    await navigator.clipboard.writeText(url).catch(() => null);
    flashBtn(copyLinkBtn, '✓ Copied!', '🔗 Share Link');
});

// ── Export: whiteboard ────────────────────────────────────────────────
function exportCanvas(format) {
    closeExportMenus();
    if (format === 'svg') {
        // Export as embedded PNG inside SVG
        const dataUrl = canvas.toDataURL('image/png');
        const svgStr = `<svg xmlns="http://www.w3.org/2000/svg" width="${canvas.width}" height="${canvas.height}">
  <image href="${dataUrl}" width="${canvas.width}" height="${canvas.height}"/>
</svg>`;
        downloadBlob(
            new Blob([svgStr], { type: 'image/svg+xml' }),
            `whiteboard-${ROOM_ID}.svg`
        );
        return;
    }
    // PNG / JPEG: build composite with white background for JPEG
    const offscreen = document.createElement('canvas');
    offscreen.width  = canvas.width;
    offscreen.height = canvas.height;
    const offCtx = offscreen.getContext('2d');
    if (format === 'jpeg') {
        offCtx.fillStyle = '#ffffff';
        offCtx.fillRect(0, 0, offscreen.width, offscreen.height);
    }
    offCtx.drawImage(canvas, 0, 0);
    const mimeType = format === 'jpeg' ? 'image/jpeg' : 'image/png';
    offscreen.toBlob(blob => {
        downloadBlob(blob, `whiteboard-${ROOM_ID}.${format === 'jpeg' ? 'jpg' : 'png'}`);
    }, mimeType, 0.95);
}

// ── Export: notes ─────────────────────────────────────────────────────
function exportNotes(format) {
    closeExportMenus();
    const content = sharedText.value;
    const ts      = new Date().toISOString().slice(0, 16).replace('T', '_');

    if (format === 'txt') {
        downloadBlob(
            new Blob([content], { type: 'text/plain;charset=utf-8' }),
            `notes-${ROOM_ID}-${ts}.txt`
        );
    } else if (format === 'md') {
        const md = `# Room: ${ROOM_ID}\n\n_Exported: ${new Date().toLocaleString()}_\n\n---\n\n${content}`;
        downloadBlob(
            new Blob([md], { type: 'text/markdown;charset=utf-8' }),
            `notes-${ROOM_ID}-${ts}.md`
        );
    } else if (format === 'json') {
        const obj = {
            roomId:    ROOM_ID,
            exportedAt: new Date().toISOString(),
            author:    USERNAME,
            content
        };
        downloadBlob(
            new Blob([JSON.stringify(obj, null, 2)], { type: 'application/json' }),
            `notes-${ROOM_ID}-${ts}.json`
        );
    } else if (format === 'pdf') {
        // Use browser print-to-PDF
        const win = window.open('', '_blank');
        win.document.write(`<!DOCTYPE html><html><head>
            <title>Notes · ${ROOM_ID}</title>
            <style>
                body { font-family: 'Georgia', serif; margin: 40px 60px; line-height: 1.8; color: #111; font-size: 16px; }
                h1   { font-size: 24px; border-bottom: 2px solid #ddd; padding-bottom: 10px; }
                pre  { white-space: pre-wrap; word-break: break-word; font-family: 'Courier New', monospace; font-size: 14px; }
                .meta { color: #666; font-size: 13px; margin-bottom: 30px; }
            </style>
        </head><body>
            <h1>Room: ${ROOM_ID}</h1>
            <div class="meta">Exported by ${USERNAME} · ${new Date().toLocaleString()}</div>
            <pre>${escapeHtml(content)}</pre>
        </body></html>`);
        win.document.close();
        win.focus();
        win.print();
    }
}

function escapeHtml(str) {
    return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function downloadBlob(blob, filename) {
    const url  = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href  = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
}

// ── Export menus ─────────────────────────────────────────────────────
function closeExportMenus() {
    document.querySelectorAll('.export-menu').forEach(m => m.classList.remove('open'));
}

document.getElementById('wbExportToggle')?.addEventListener('click', e => {
    e.stopPropagation();
    const menu = document.getElementById('wbExportMenu');
    const wasOpen = menu.classList.contains('open');
    closeExportMenus();
    if (!wasOpen) menu.classList.add('open');
});

document.getElementById('noteExportToggle')?.addEventListener('click', e => {
    e.stopPropagation();
    const menu = document.getElementById('noteExportMenu');
    const wasOpen = menu.classList.contains('open');
    closeExportMenus();
    if (!wasOpen) menu.classList.add('open');
});

document.addEventListener('click', closeExportMenus);

// ── Recent rooms ──────────────────────────────────────────────────────
function recordRoom() {
    try {
        const upsert = (key) => {
            let rooms = JSON.parse(localStorage.getItem(key) || '[]');
            rooms = rooms.filter(r => r.id !== ROOM_ID);
            rooms.unshift({ id: ROOM_ID, time: Date.now() });
            localStorage.setItem(key, JSON.stringify(rooms.slice(0, 8)));
        };

        if (IS_AUTH) {
            upsert(scopedAuthRecentKey());
            fetch('/api/users/me/rooms/sync', {
                method: 'POST',
                headers: withCsrf({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ roomIds: [ROOM_ID] })
            })
            .then(() => logSave('Recent', 'synced room to account'))
            .catch((e) => logSave('Recent', 'sync failed: ' + (e?.message || 'unknown')));
        } else {
            upsert('cw_recent_rooms');
        }
    } catch (_) {}
}

// ── Init ─────────────────────────────────────────────────────────────
recordRoom();

// Load existing document then connect
logSave('INIT', `loading room ${ROOM_ID}`);

fetch(`/api/rooms/${ROOM_ID}/document`)
    .then(r => {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
    })
    .then(data => {
        logSave('LOAD', 'restoring text + drawing');
        sharedText.value = data.content || '';
        restoreDrawing(data.drawingData || '');
        updateCharCount();
    })
    .catch(e => logSave('LOAD', 'error: ' + e.message))
    .finally(() => {
        logSave('INIT', 'connecting socket');
        connectSocket();
    });

window.addEventListener('beforeunload', (e) => {
    flushPendingSaves();
});

window.addEventListener('pagehide', (e) => {
    flushPendingSaves();
});

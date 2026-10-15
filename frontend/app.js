'use strict';

/* ------------------------------------------------------------------
 * Configuración de la URL base de la API
 *   (sin parámetro)  → http://localhost:8080      (cross-origin, requiere CORS)
 *   ?api=            → "" = mismo origen          (usar con dev_server.py --proxy)
 *   ?api=http://host:puerto                       (otro backend)
 * ------------------------------------------------------------------ */
const params = new URLSearchParams(location.search);
const API = params.has('api') ? params.get('api').replace(/\/+$/, '') : 'http://localhost:8080';

const $ = (id) => document.getElementById(id);
const nf = new Intl.NumberFormat('es');
const fmtMs = (ms) => (ms >= 1000 ? (ms / 1000).toFixed(2) + ' s' : ms >= 10 ? ms.toFixed(1) + ' ms' : ms.toFixed(2) + ' ms');
const SOURCE_LABEL = { DATABASE: 'BASE DE DATOS', CACHE_MISS: 'CACHÉ · fallo', CACHE_HIT: 'CACHÉ · acierto' };

const state = { busy: false, ready: false, stations: [] };

/* ---------------------------- HTTP ---------------------------- */
class ApiError extends Error {
  constructor(kind, message, status) {
    super(message);
    this.kind = kind;
    this.status = status;
  }
}

async function api(path, options) {
  const t0 = performance.now();
  let res;
  try {
    res = await fetch(API + path, options);
  } catch (err) {
    // fetch() solo rechaza con TypeError cuando no hay respuesta legible:
    // servidor caído, red, o bloqueo CORS (el navegador no distingue el motivo en JS).
    throw new ApiError('network',
      `No se pudo leer la respuesta de ${API || location.origin}.\n` +
      `• Si el backend está apagado, arráncalo (mvn spring-boot:run).\n` +
      `• Si está encendido, casi seguro es CORS: abre DevTools → Console y busca "blocked by CORS policy".\n` +
      `  Tu origen es ${location.origin}; debe figurar en lab.cors.origins (perfil dev).`);
  }
  const roundTrip = performance.now() - t0;
  let body = null;
  try { body = await res.json(); } catch { /* cuerpo vacío */ }
  if (!res.ok) {
    throw new ApiError('http', (body && (body.message || body.error)) || `HTTP ${res.status}`, res.status);
  }
  return {
    body,
    roundTrip,
    source: res.headers.get('X-Data-Source'),
    serverTiming: res.headers.get('Server-Timing'),
  };
}

/* ---------------------------- UI helpers ---------------------------- */
function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  Object.assign(node, props);
  for (const c of children) node.append(c);
  return node;
}

function banner(message, kind = 'error') {
  const b = $('banner');
  b.textContent = message;
  b.className = 'banner' + (kind === 'info' ? ' info' : '');
  b.hidden = false;
}
const hideBanner = () => { $('banner').hidden = true; };

function setBusy(busy) {
  state.busy = busy;
  for (const id of ['btn-db', 'btn-cache', 'btn-duel', 'btn-clear', 'btn-stats']) {
    $(id).disabled = busy || (!state.ready && id !== 'btn-stats');
  }
}

async function guarded(task) {
  if (state.busy) return;
  hideBanner();
  setBusy(true);
  try {
    await task();
  } catch (err) {
    banner(err instanceof ApiError ? err.message : `Error inesperado: ${err.message}`);
  } finally {
    setBusy(false);
  }
}

const scenario = () => document.querySelector('input[name=scenario]:checked').value;
const station = () => $('station').value;

/* ---------------------------- estado del backend ---------------------------- */
async function loadStatus() {
  try {
    const { body } = await api('/api/status');
    $('pill-backend').className = 'pill ' + (body.ready ? 'pill-ok' : 'pill-wait');
    $('pill-backend').textContent = body.ready
      ? `${nf.format(body.totalReadings)} lecturas · cargadas en ${nf.format(body.seedMs)} ms`
      : 'generando datos de prueba…';

    if (!state.stations.length) {
      state.stations = body.stations;
      $('station').replaceChildren(...body.stations.map((s) => el('option', { value: s, textContent: s })));
    }
    state.ready = body.ready;
    setBusy(false);
    if (!body.ready) setTimeout(loadStatus, 1500);
    else hideBanner();
  } catch (err) {
    $('pill-backend').className = 'pill pill-bad';
    $('pill-backend').textContent = 'sin conexión';
    banner(err.message);
    setTimeout(loadStatus, 4000);
  }
}

/* ---------------------------- consulta individual ---------------------------- */
const READING_COLS = [
  ['id', (r) => r.id],
  ['estación', (r) => r.stationCode],
  ['fecha', (r) => r.recordedAt.replace('T', ' ')],
  ['temp °C', (r) => r.temperature.toFixed(2)],
  ['humedad %', (r) => r.humidity.toFixed(2)],
  ['presión hPa', (r) => r.pressure.toFixed(2)],
];
const REPORT_COLS = [
  ['estación', (r) => r.stationCode],
  ['año', (r) => r.year],
  ['mes', (r) => String(r.month).padStart(2, '0')],
  ['T media', (r) => r.avgTemp.toFixed(2)],
  ['T mín', (r) => r.minTemp.toFixed(2)],
  ['T máx', (r) => r.maxTemp.toFixed(2)],
  ['humedad', (r) => r.avgHumidity.toFixed(2)],
  ['muestras', (r) => nf.format(r.samples)],
];

function runSingle(mode) {
  return guarded(async () => {
    const sc = scenario();
    const path = sc === 'readings'
      ? `/api/readings/${encodeURIComponent(station())}?mode=${mode}&sample=15`
      : `/api/report/monthly?mode=${mode}&sample=15`;
    const res = await api(path);
    renderOutcome(res, sc);
    await refreshStats();
  });
}

function renderOutcome(res, sc) {
  const o = res.body;
  const src = $('m-source');
  src.textContent = SOURCE_LABEL[o.source] ?? o.source;
  src.className = 'v src-' + o.source;
  $('m-server').textContent = fmtMs(o.serverMs);
  $('m-total').textContent = fmtMs(res.roundTrip);
  $('m-count').textContent = nf.format(o.totalRecords);

  const exposed = res.source !== null;
  $('headers-note').textContent = exposed
    ? `Cabeceras leídas desde JavaScript → X-Data-Source: ${res.source} · Server-Timing: ${res.serverTiming}`
    : 'El navegador ocultó X-Data-Source y Server-Timing: falta Access-Control-Expose-Headers en el backend.';
  $('c-hdr').textContent = exposed ? 'visibles (exposedHeaders OK)' : 'ocultas por CORS';

  const cols = sc === 'readings' ? READING_COLS : REPORT_COLS;
  const table = $('preview');
  table.replaceChildren(
    el('thead', {}, el('tr', {}, ...cols.map(([h]) => el('th', { textContent: h })))),
    el('tbody', {}, ...o.sample.map((row) => el('tr', {}, ...cols.map(([, f]) => el('td', { textContent: f(row) }))))),
  );
  table.hidden = false;

  const tr = el('tr', {},
    el('td', { textContent: new Date().toLocaleTimeString('es') }),
    el('td', { textContent: sc === 'readings' ? `serie ${station()}` : 'reporte global' }),
    el('td', { textContent: SOURCE_LABEL[o.source] ?? o.source, className: 'src-' + o.source }),
    el('td', { textContent: nf.format(o.totalRecords) }),
    el('td', { textContent: fmtMs(o.serverMs) }),
    el('td', { textContent: fmtMs(res.roundTrip) }),
  );
  const tbody = document.querySelector('#log tbody');
  tbody.prepend(tr);
  while (tbody.rows.length > 50) tbody.deleteRow(-1);
}

/* ---------------------------- duelo ---------------------------- */
function runDuel() {
  return guarded(async () => {
    banner('Ejecutando el duelo… la primera tanda puede tardar unos segundos.', 'info');
    const q = new URLSearchParams({ scenario: scenario(), station: station(), rounds: $('rounds').value });
    const { body: d } = await api('/api/lab/duel?' + q);
    hideBanner();
    renderDuel(d);
    await refreshStats();
  });
}

function renderDuel(d) {
  $('duel-empty').hidden = true;
  $('duel-box').hidden = false;
  $('d-db').textContent = fmtMs(d.dbMedianMs);
  $('d-hit').textContent = fmtMs(d.hitMedianMs);
  $('d-miss').textContent = fmtMs(d.missMs);
  $('d-speed').textContent = '×' + nf.format(Math.round(d.speedup));
  $('chart-wrap').replaceChildren(buildChart(d));
}

const NS = 'http://www.w3.org/2000/svg';
function s(tag, attrs = {}, text) {
  const n = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
  if (text !== undefined) n.textContent = text;
  return n;
}

function buildChart(d) {
  const W = 760, H = 320, L = 64, R = 20, T = 28, B = 38;
  const all = [...d.database, ...d.cache].map((a) => Math.max(a.ms, 0.001));
  const lo = Math.floor(Math.log10(Math.min(...all)));
  const hi = Math.ceil(Math.log10(Math.max(...all)));
  const dMin = lo, dMax = hi === lo ? lo + 1 : hi;
  const y = (v) => T + (H - T - B) * (1 - (Math.log10(Math.max(v, 0.001)) - dMin) / (dMax - dMin));
  const n = d.rounds;
  const x = (i) => L + 24 + (W - L - R - 48) * (n === 1 ? 0 : (i - 1) / (n - 1));

  const svg = s('svg', {
    viewBox: `0 0 ${W} ${H}`, role: 'img',
    'aria-label': `Tiempos por repetición. Mediana BD ${d.dbMedianMs} ms, mediana caché ${d.hitMedianMs} ms.`,
  });

  for (let p = dMin; p <= dMax; p++) {
    const yy = y(10 ** p);
    svg.append(s('line', { x1: L, x2: W - R, y1: yy, y2: yy, class: 'axis' }));
    svg.append(s('text', { x: L - 8, y: yy + 4, 'text-anchor': 'end', class: 'tick' }, `${10 ** p} ms`));
  }
  for (let i = 1; i <= n; i++) {
    svg.append(s('text', { x: x(i), y: H - 14, 'text-anchor': 'middle', class: 'tick' }, `#${i}`));
  }

  const series = [
    { data: d.database, color: 'var(--db)', name: 'Base de datos', shape: 'square' },
    { data: d.cache, color: 'var(--cache)', name: 'Caché', shape: 'circle' },
  ];
  for (const sr of series) {
    svg.append(s('polyline', {
      points: sr.data.map((a) => `${x(a.round)},${y(a.ms)}`).join(' '),
      fill: 'none', style: `stroke:${sr.color}`, 'stroke-width': 2,
    }));
    for (const a of sr.data) {
      const cx = x(a.round), cy = y(a.ms);
      const miss = a.source === 'CACHE_MISS';
      const mark = sr.shape === 'square'
        ? s('rect', { x: cx - 4.5, y: cy - 4.5, width: 9, height: 9, style: `fill:${sr.color}` })
        : s('circle', { cx, cy, r: 5, style: `stroke:${sr.color};fill:${miss ? 'var(--panel)' : sr.color}`, 'stroke-width': 2 });
      mark.append(s('title', {}, `${sr.name} · #${a.round} · ${fmtMs(a.ms)}`));
      svg.append(mark);
      if (miss) svg.append(s('text', { x: cx + 9, y: cy - 8, class: 'lbl', style: 'fill:var(--warn)' }, 'fallo (miss)'));
    }
  }

  svg.append(s('rect', { x: L, y: 6, width: 10, height: 10, style: 'fill:var(--db)' }));
  svg.append(s('text', { x: L + 16, y: 15, class: 'lbl', style: 'fill:var(--ink)' }, 'Base de datos'));
  svg.append(s('circle', { cx: L + 134, cy: 11, r: 5, style: 'fill:var(--cache)' }));
  svg.append(s('text', { x: L + 144, y: 15, class: 'lbl', style: 'fill:var(--ink)' }, 'Caché'));
  return svg;
}

/* ---------------------------- caché ---------------------------- */
async function refreshStats() {
  const { body } = await api('/api/cache/stats');
  document.querySelector('#stats tbody').replaceChildren(...body.map((c) => el('tr', {},
    el('td', { textContent: c.name }),
    el('td', { textContent: nf.format(c.hits) }),
    el('td', { textContent: nf.format(c.misses) }),
    el('td', { textContent: (c.hitRate * 100).toFixed(1) + ' %' }),
    el('td', { textContent: c.entries }),
  )));
}

function clearCache() {
  return guarded(async () => {
    // DELETE no es un método "simple": el navegador envía antes un preflight OPTIONS.
    await api('/api/cache', { method: 'DELETE' });
    await refreshStats();
    banner('Cachés vaciadas: la próxima lectura con caché será un fallo (miss).', 'info');
  });
}

/* ---------------------------- arranque ---------------------------- */
function init() {
  $('c-front').textContent = location.origin;
  $('c-api').textContent = API || '(relativa: mismo origen)';
  const same = !API || new URL(API, location.href).origin === location.origin;
  $('c-same').textContent = same ? 'sí → no interviene CORS' : 'no → el navegador exige CORS';
  $('pill-origin').textContent = `${location.host} → ${API ? new URL(API).host : 'mismo origen'}`;

  for (const r of document.querySelectorAll('input[name=scenario]')) {
    r.addEventListener('change', () => { $('station').disabled = scenario() !== 'readings'; });
  }
  $('station').disabled = scenario() !== 'readings';
  $('rounds').addEventListener('input', (e) => { $('rounds-out').textContent = e.target.value; });

  $('btn-db').addEventListener('click', () => runSingle('db'));
  $('btn-cache').addEventListener('click', () => runSingle('cache'));
  $('btn-duel').addEventListener('click', runDuel);
  $('btn-clear').addEventListener('click', clearCache);
  $('btn-stats').addEventListener('click', () => guarded(refreshStats));
  $('btn-log-clear').addEventListener('click', () => document.querySelector('#log tbody').replaceChildren());

  setBusy(true);
  loadStatus().then(() => guarded(refreshStats).catch(() => {}));
}

init();

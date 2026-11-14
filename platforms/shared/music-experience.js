import { optimizer } from "./performance-optimizer.js";

const RESOLVER = "https://miltvplus-resolver-api.onrender.com";
const safeUrl = value => {
  try { const url = new URL(value); return url.protocol === "https:" && !url.username && !url.password; }
  catch { return false; }
};
const regionCode = () => {
  const saved = String(localStorage.getItem("miltvplus.country") || "").trim().toUpperCase();
  if (/^[A-Z]{2}$/.test(saved)) return saved;
  const match = String(navigator.language || "").match(/[-_]([A-Za-z]{2})$/);
  return match ? match[1].toUpperCase() : "AR";
};
const decadeOf = year => {
  const value = Number(year);
  if (!Number.isFinite(value) || value < 1900) return "Sin fecha";
  return Math.floor(value / 10) * 10 + "s";
};

export function mountMusicExperience({ statusElement } = {}) {
  const main = document.querySelector("main");
  const channelGrid = document.getElementById("channels");
  const emptyState = document.getElementById("empty");
  if (!main || !channelGrid || document.getElementById("music-experience")) return () => {};

  const nav = document.createElement("nav");
  nav.className = "section-nav";
  nav.hidden = true;
  nav.setAttribute("aria-hidden", "true");
  nav.setAttribute("aria-label", "Control interno de sección");
  nav.innerHTML = '<button type="button" id="nav-tv" aria-pressed="true">TV en vivo</button><button type="button" id="nav-music" aria-pressed="false">Música</button>';
  main.insertBefore(nav, channelGrid);

  const section = document.createElement("section");
  section.id = "music-experience";
  section.hidden = true;
  section.innerHTML = `
    <div class="music-toolbar">
      <label>Género <select id="music-genre"><option value="all">Todos</option></select></label>
      <label>Época <select id="music-decade"><option value="all">Todas</option></select></label>
      <label>Ambiente <select id="music-mood"><option value="all">Normal</option><option value="immersive">Inmersivo</option></select></label>
    </div>
    <p class="music-description">Catálogo autorizado · mezclas continuas por género y época · reproducción dentro de MILTVPLUS</p>
    <div id="music-list" class="music-grid" aria-live="polite"></div>
    <div id="music-empty" class="music-empty" hidden>No hay pistas publicadas con reproducción directa autorizada para esta región.</div>
    <div id="music-player" class="music-player">
      <div class="music-now"><strong id="music-title">Elegí una pista</strong><span id="music-artist">MILTVPLUS Música</span></div>
      <audio id="music-audio" controls preload="none"></audio>
      <div class="music-controls">
        <button type="button" id="music-prev">Anterior</button>
        <button type="button" id="music-toggle">Reproducir</button>
        <button type="button" id="music-next">Siguiente</button>
        <label><input type="checkbox" id="music-repeat"> Repetir mezcla</label>
      </div>
      <p id="music-status" role="status">La música se reproduce dentro de la aplicación.</p>
    </div>`;
  main.appendChild(section);

  const $ = id => document.getElementById(id);
  const state = { items: [], queue: [], index: -1, loaded: false, repeat: false, immersive: false, disposed: false };
  const audio = $("music-audio");
  const avplay = window.webapis?.avplay || null;
  const setStatus = message => {
    $("music-status").textContent = message;
    if (statusElement) statusElement.textContent = message;
  };
  const showSection = music => {
    if (!music) stopPlayback();
    channelGrid.hidden = music;
    if (emptyState) emptyState.hidden = music || channelGrid.childElementCount > 0;
    main.querySelector("h1").textContent = music ? "Música" : "Televisión en vivo";
    const description = main.querySelector(":scope > p");
    if (description) description.hidden = music;
    section.hidden = !music;
    $("nav-tv").setAttribute("aria-pressed", String(!music));
    $("nav-music").setAttribute("aria-pressed", String(music));
    (music ? $("music-genre") : channelGrid.querySelector("button"))?.focus();
    if (music && !state.loaded) loadCatalog();
  };
  $("nav-tv").addEventListener("click", () => showSection(false));
  $("nav-music").addEventListener("click", () => showSection(true));

  async function loadCatalog() {
    setStatus("Cargando música autorizada…");
    try {
      const response = await fetch(RESOLVER + "/api/v2/catalog/musica/" + regionCode(), {
        headers: { Accept: "application/json" }, cache: "no-store"
      });
      const body = await response.json();
      if (!response.ok || !body.success || !Array.isArray(body.items)) throw new Error(body.error || "No se pudo consultar el catálogo.");
      state.items = body.items.filter(item => item && typeof item.id === "string" && typeof item.title === "string");
      state.loaded = true;
      populateFilters();
      renderItems();
      setStatus(state.items.length + " pistas en el catálogo autorizado. Las pistas sin derechos vigentes no se pueden reproducir.");
    } catch (error) {
      setStatus("No se pudo cargar Música: " + error.message);
      $("music-empty").hidden = false;
    }
  }

  function populateFilters() {
    const genres = [...new Set(state.items.map(item => String(item.genre || "").trim()).filter(Boolean))].sort();
    const decades = [...new Set(state.items.map(item => decadeOf(item.release_year)).filter(value => value !== "Sin fecha"))].sort();
    genres.forEach(value => $("music-genre").add(new Option(value, value.toLowerCase())));
    decades.forEach(value => $("music-decade").add(new Option(value, value)));
  }

  function filteredItems() {
    const genre = $("music-genre").value;
    const decade = $("music-decade").value;
    return state.items.filter(item => {
      const genreMatch = genre === "all" || String(item.genre || "").toLowerCase() === genre;
      const decadeMatch = decade === "all" || decadeOf(item.release_year) === decade;
      return genreMatch && decadeMatch;
    });
  }

  function renderItems() {
    const root = $("music-list");
    root.replaceChildren();
    const items = filteredItems();
    $("music-empty").hidden = items.length > 0;
    items.forEach(item => {
      const card = document.createElement("article");
      card.className = "music-card";
      const title = document.createElement("strong");
      title.textContent = item.title;
      const detail = document.createElement("span");
      detail.textContent = [item.creator, item.album, item.genre, item.release_year].filter(Boolean).join(" · ") || "Pista musical";
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = safeUrl(item.stream_url) ? "Reproducir" : "No disponible";
      button.disabled = !safeUrl(item.stream_url);
      button.addEventListener("click", () => playItem(item, items));
      card.append(title, detail, button);
      root.appendChild(card);
    });
    state.queue = items.filter(item => safeUrl(item.stream_url));
  }

  async function stopPlayback() {
    try { audio.pause(); audio.removeAttribute("src"); audio.load(); } catch (_) {}
    if (avplay) {
      try { avplay.stop(); } catch (_) {}
      try { avplay.close(); } catch (_) {}
    }
    optimizer.destruirInstancia(audio);
    $("music-toggle").textContent = "Reproducir";
  }

  async function playItem(item, queue = state.queue) {
    const list = Array.isArray(queue) ? queue : [];
    state.queue = list.filter(entry => safeUrl(entry.stream_url));
    state.index = state.queue.findIndex(entry => entry.id === item.id);
    if (state.index < 0) state.queue.unshift(item), state.index = 0;
    setStatus("Verificando autorización y preparando audio…");
    try {
      const response = await fetch(RESOLVER + "/api/v2/resolve/musica/" + regionCode() + "/" + encodeURIComponent(item.id), {
        headers: { Accept: "application/json" }, cache: "no-store"
      });
      const body = await response.json();
      if (!response.ok || !body.success || body.mode !== "direct_stream" || body.mediaType !== "audio" || !safeUrl(body.streamUrl)) {
        throw new Error(body.error || "El servidor no autorizó la reproducción directa.");
      }
      await stopPlayback();
      $("music-title").textContent = body.title || item.title;
      $("music-artist").textContent = [body.creator || item.creator, body.album || item.album].filter(Boolean).join(" · ");
      if (avplay) {
        avplay.open(body.streamUrl);
        avplay.setListener({ onstreamcompleted: () => advance(1), onerror: error => setStatus("AVPlay no pudo reproducir este audio: " + String(error)) });
        avplay.prepareAsync(() => {
          try { avplay.play(); $("music-toggle").textContent = "Pausar"; setStatus("Reproduciendo en el motor multimedia del televisor."); }
          catch (error) { setStatus("No se pudo iniciar AVPlay: " + error.message); }
        }, error => setStatus("No se pudo preparar el audio: " + String(error)));
      } else {
        optimizer.optimizarReproductor(audio);
        audio.src = body.streamUrl;
        await audio.play();
        $("music-toggle").textContent = "Pausar";
        setStatus("Reproducción interna iniciada.");
      }
    } catch (error) {
      setStatus("No se pudo reproducir esta pista: " + error.message);
    }
  }

  function advance(delta) {
    if (!state.queue.length) return;
    let next = state.index + delta;
    if (next < 0) next = state.repeat ? state.queue.length - 1 : 0;
    if (next >= state.queue.length) next = state.repeat ? 0 : state.queue.length - 1;
    playItem(state.queue[next], state.queue);
  }
  $("music-genre").addEventListener("change", renderItems);
  $("music-decade").addEventListener("change", renderItems);
  $("music-mood").addEventListener("change", () => {
    state.immersive = $("music-mood").value === "immersive";
    section.classList.toggle("music-immersive", state.immersive);
  });
  $("music-prev").addEventListener("click", () => advance(-1));
  $("music-next").addEventListener("click", () => advance(1));
  $("music-repeat").addEventListener("change", event => { state.repeat = event.target.checked; });
  $("music-toggle").addEventListener("click", async () => {
    try {
      if (avplay) {
        if (avplay.getState() === "PLAYING") { avplay.pause(); $("music-toggle").textContent = "Reproducir"; }
        else { avplay.play(); $("music-toggle").textContent = "Pausar"; }
      } else if (audio.paused) { await audio.play(); $("music-toggle").textContent = "Pausar"; }
      else { audio.pause(); $("music-toggle").textContent = "Reproducir"; }
    } catch (error) { setStatus("Control de reproducción no disponible: " + error.message); }
  });
  audio.addEventListener("ended", () => advance(1));
  audio.addEventListener("error", () => setStatus("El formato de audio no es compatible con este modelo de televisor."));
  document.addEventListener("visibilitychange", () => { if (document.hidden) stopPlayback(); });
  const cleanup = () => { if (state.disposed) return; state.disposed = true; stopPlayback(); };
  window.addEventListener("pagehide", cleanup);
  return cleanup;
}

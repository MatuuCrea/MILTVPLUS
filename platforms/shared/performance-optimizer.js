/**
 * MILTVPLUS — optimización de recursos para equipos de bajos recursos.
 *
 * No intenta forzar el recolector de basura del entorno, que no está disponible de forma estándar.
 * Libera referencias, listeners, fuentes y buffers de forma determinista.
 * Las listas se virtualizan cuando se proporciona itemHeight.
 */
export class PerformanceOptimizer {
  constructor({ overscan = 3, maxDomNodes = 48 } = {}) {
    this.overscan = Math.max(0, Math.min(12, Number(overscan) || 0));
    this.maxDomNodes = Math.max(12, Math.min(120, Number(maxDomNodes) || 48));
    this.activePlayerInstance = null;
    this.playerCleanup = null;
    this.virtualLists = new WeakMap();
  }

  optimizarReproductor(videoElement) {
    if (!videoElement || typeof videoElement !== "object") return false;
    if (this.activePlayerInstance && this.activePlayerInstance !== videoElement) {
      this.destruirInstancia(this.activePlayerInstance);
    }
    videoElement.preload = "none";
    videoElement.setAttribute?.("playsinline", "");
    videoElement.setAttribute?.("webkit-playsinline", "");
    videoElement.disablePictureInPicture = true;
    // No se fuerza translateZ: puede aumentar consumo de GPU/RAM en TVs económicas.
    this.activePlayerInstance = videoElement;
    return true;
  }

  destruirInstancia(videoElement = this.activePlayerInstance) {
    if (!videoElement) return;
    try {
      videoElement.pause?.();
      videoElement.removeAttribute?.("src");
      videoElement.srcObject = null;
      // Vacía fuentes <source> y el buffer administrado por el elemento.
      if (videoElement.querySelectorAll) {
        videoElement.querySelectorAll("source").forEach(source => source.remove());
      }
      videoElement.load?.();
    } catch (error) {
      // La limpieza debe continuar aunque el dispositivo ya haya destruido el nodo.
    } finally {
      if (this.activePlayerInstance === videoElement) this.activePlayerInstance = null;
      if (this.playerCleanup) {
        try { this.playerCleanup(); } catch (_) {}
        this.playerCleanup = null;
      }
    }
  }

  registrarLimpieza(callback) {
    if (typeof callback !== "function") return () => {};
    this.playerCleanup = callback;
    return () => { if (this.playerCleanup === callback) this.playerCleanup = null; };
  }

  /**
   * Renderiza solo la ventana visible + overscan.
   * options.itemHeight es obligatorio para virtualizar; sin él usa fragmento
   * de DOM (modo compatible) y limita el tamaño para evitar bloqueos severos.
   */
  virtualizarLista(itemsArray, contenedorUI, renderizarCallback, options = {}) {
    if (!Array.isArray(itemsArray) || !contenedorUI || typeof renderizarCallback !== "function") return () => {};
    this.destruirLista(contenedorUI);
    const doc = contenedorUI.ownerDocument || globalThis.document;
    if (!doc) return () => {};

    const itemHeight = Number(options.itemHeight);
    if (!(itemHeight > 0)) {
      const fragment = doc.createDocumentFragment();
      for (let i = 0; i < itemsArray.length; i++) {
        const node = renderizarCallback(itemsArray[i], i);
        if (node) fragment.appendChild(node);
      }
      contenedorUI.replaceChildren(fragment);
      return () => contenedorUI.replaceChildren();
    }

    const overscan = Math.max(0, Math.min(20, Number(options.overscan ?? this.overscan)));
    const rowHeight = itemHeight;
    const state = { itemsArray, contenedorUI, renderizarCallback, rowHeight, overscan, frame: 0, disposed: false };
    const spacer = doc.createElement("div");
    spacer.setAttribute("aria-hidden", "true");
    spacer.style.height = (itemsArray.length * rowHeight) + "px";
    spacer.style.width = "1px";
    spacer.style.pointerEvents = "none";
    spacer.style.position = "relative";
    spacer.style.top = "0";
    spacer.style.left = "0";
    const layer = doc.createElement("div");
    layer.style.position = "absolute";
    layer.style.inset = "0 0 auto 0";
    layer.style.willChange = "transform";
    const previousPosition = contenedorUI.style.position;
    if (!previousPosition) contenedorUI.style.position = "relative";
    contenedorUI.replaceChildren(spacer, layer);

    const draw = () => {
      state.frame = 0;
      if (state.disposed) return;
      const top = Math.max(0, contenedorUI.scrollTop || 0);
      const height = Math.max(rowHeight, contenedorUI.clientHeight || rowHeight * 8);
      const start = Math.max(0, Math.floor(top / rowHeight) - overscan);
      const visible = Math.ceil(height / rowHeight) + overscan * 2;
      const end = Math.min(itemsArray.length, start + Math.min(this.maxDomNodes, visible));
      const fragment = doc.createDocumentFragment();
      for (let i = start; i < end; i++) {
        const node = renderizarCallback(itemsArray[i], i);
        if (!node) continue;
        node.dataset && (node.dataset.virtualIndex = String(i));
        fragment.appendChild(node);
      }
      layer.replaceChildren(fragment);
      layer.style.transform = "translateY(" + (start * rowHeight) + "px)";
    };
    const schedule = () => {
      if (!state.frame && !state.disposed) {
        const raf = doc.defaultView?.requestAnimationFrame || (fn => setTimeout(fn, 16));
        state.frame = raf(draw);
      }
    };
    contenedorUI.addEventListener("scroll", schedule, { passive: true });
    const Resize = doc.defaultView?.ResizeObserver;
    if (Resize) {
      state.resizeObserver = new Resize(schedule);
      state.resizeObserver.observe(contenedorUI);
    }
    state.dispose = () => {
      if (state.disposed) return;
      state.disposed = true;
      contenedorUI.removeEventListener("scroll", schedule);
      state.resizeObserver?.disconnect();
      if (state.frame) {
        const cancel = doc.defaultView?.cancelAnimationFrame || clearTimeout;
        cancel(state.frame);
      }
      contenedorUI.replaceChildren();
      if (!previousPosition) contenedorUI.style.position = "";
      this.virtualLists.delete(contenedorUI);
    };
    this.virtualLists.set(contenedorUI, state);
    draw();
    return state.dispose;
  }

  destruirLista(contenedorUI) {
    const state = contenedorUI && this.virtualLists.get(contenedorUI);
    if (state?.dispose) state.dispose();
  }

  destruirTodo() {
    this.destruirInstancia();
  }
}

export const optimizer = new PerformanceOptimizer();

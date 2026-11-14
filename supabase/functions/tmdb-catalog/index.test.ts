import { buildTmdbRoute } from "./routes.ts";

Deno.test("películas populares usa idioma español y región argentina", () => {
  const route = buildTmdbRoute({ action: "movie_popular" });
  if (route.path !== "/movie/popular") throw new Error("Ruta incorrecta");
  if (route.params.language !== "es-AR" || route.params.region !== "AR") throw new Error("Faltan preferencias regionales");
});

Deno.test("búsqueda de series valida query y construye ruta permitida", () => {
  const route = buildTmdbRoute({ action: "search_tv", query: "Los simuladores" });
  if (route.path !== "/search/tv" || route.params.query !== "Los simuladores") throw new Error("Ruta de búsqueda incorrecta");
});

Deno.test("rechaza acciones arbitrarias y rutas abiertas", () => {
  let rejected = false;
  try { buildTmdbRoute({ action: "https://example.com" }); } catch { rejected = true; }
  if (!rejected) throw new Error("Se aceptó una acción no permitida");
});

Deno.test("temporadas requieren identificadores válidos", () => {
  let rejected = false;
  try { buildTmdbRoute({ action: "tv_season", id: 12, season: -1 }); } catch { rejected = true; }
  if (!rejected) throw new Error("Se aceptó una temporada inválida");
});

Deno.test("series en emisión usa la ruta TMDB correcta", () => {
  const route = buildTmdbRoute({ action: "tv_on_the_air", page: 2 });
  if (route.path !== "/tv/on_the_air" || route.params.page !== "2") throw new Error("Ruta de series en emisión incorrecta");
});

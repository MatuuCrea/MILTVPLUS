export type RequestInput = {
  action?: string;
  query?: string;
  id?: number;
  season?: number;
  page?: number;
};

export type TmdbRoute = { path: string; params: Record<string, string> };

const ACTIONS = new Set([
  "movie_popular", "movie_top_rated", "movie_upcoming",
  "tv_popular", "tv_on_the_air",
  "search_movie", "search_tv",
  "movie_details", "tv_details", "movie_credits", "tv_credits", "tv_season",
]);

export function buildTmdbRoute(input: RequestInput): TmdbRoute {
  const action = input.action ?? "";
  if (!ACTIONS.has(action)) throw new Error("Acción de catálogo no permitida.");

  const page = Math.min(500, Math.max(1, Number.isInteger(input.page) ? input.page! : 1));
  const params: Record<string, string> = { language: "es-AR", page: String(page) };

  const listingRoutes: Record<string, string> = {
    movie_popular: "/movie/popular",
    movie_top_rated: "/movie/top_rated",
    movie_upcoming: "/movie/upcoming",
    tv_popular: "/tv/popular",
    tv_on_the_air: "/tv/on_the_air",
  };
  if (listingRoutes[action]) {
    if (action.startsWith("movie_")) params.region = "AR";
    return { path: listingRoutes[action], params };
  }

  if (action === "search_movie" || action === "search_tv") {
    const query = (input.query ?? "").trim();
    if (query.length < 2 || query.length > 100) throw new Error("La búsqueda debe tener entre 2 y 100 caracteres.");
    params.query = query;
    params.include_adult = "false";
    return { path: action === "search_movie" ? "/search/movie" : "/search/tv", params };
  }

  const id = input.id;
  if (!Number.isSafeInteger(id) || (id ?? 0) <= 0) throw new Error("Identificador TMDB inválido.");
  if (action === "movie_details") return { path: `/movie/${id}`, params: { ...params, append_to_response: "credits,videos,images" } };
  if (action === "tv_details") return { path: `/tv/${id}`, params: { ...params, append_to_response: "credits,videos,images" } };
  if (action === "movie_credits") return { path: `/movie/${id}/credits`, params };
  if (action === "tv_credits") return { path: `/tv/${id}/credits`, params };

  const season = input.season;
  if (!Number.isSafeInteger(season) || (season ?? -1) < 0 || (season ?? 101) > 100) throw new Error("Temporada inválida.");
  return { path: `/tv/${id}/season/${season}`, params };
}

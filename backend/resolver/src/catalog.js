const TV_FIELDS = [
  "id", "slug", "title", "description", "country_code", "language_code", "category",
  "logo_url", "official_watch_url", "stream_url", "playback_type", "source_status",
  "last_checked_at", "rights_verified", "rights_holder", "rights_reference",
  "rights_regions", "rights_valid_from", "rights_valid_until", "is_published"
].join(",");

const SPORTS_FIELDS = [
  "id", "title", "description", "country_code", "sport", "competition", "starts_at", "ends_at",
  "poster_url", "official_info_url", "stream_url", "rights_verified", "rights_holder",
  "rights_reference", "rights_regions", "rights_valid_from", "rights_valid_until", "is_published"
].join(",");

const MUSIC_FIELDS = [
  "id", "title", "creator", "description", "country_code", "item_type", "album", "genre",
  "release_year", "external_url", "artwork_url", "stream_url", "rights_verified",
  "rights_holder", "rights_reference", "rights_regions", "rights_valid_from", "rights_valid_until",
  "is_published"
].join(",");

const MEDIA_FIELDS = [
  "id", "title", "description", "kind", "country_code", "language_code", "genre", "genres",
  "release_year", "duration_seconds", "age_rating", "poster_url", "backdrop_url",
  "stream_url", "source_url", "is_adult_content", "rights_verified", "rights_holder",
  "rights_reference", "rights_regions", "rights_valid_from", "rights_valid_until", "is_published"
].join(",");

export class CatalogError extends Error {
  constructor(message, status = 502) {
    super(message);
    this.status = status;
  }
}

export class SupabaseCatalog {
  constructor({ url, anonKey, fetchImpl = fetch }) {
    this.baseUrl = url.replace(/\/$/, "");
    this.anonKey = anonKey;
    this.fetchImpl = fetchImpl;
  }

  async request(path, accessToken) {
    const anonymousJwt = !accessToken && this.anonKey.split(".").length === 3 ? this.anonKey : null;
    const bearer = accessToken || anonymousJwt;
    const response = await this.fetchImpl(this.baseUrl + path, {
      method: "GET",
      headers: {
        apikey: this.anonKey,
        ...(bearer ? { Authorization: "Bearer " + bearer } : {}),
        Accept: "application/json"
      },
      signal: AbortSignal.timeout(10_000)
    });
    if (!response.ok) {
      const status = response.status === 401 || response.status === 403 ? 403 : 502;
      throw new CatalogError("No se pudo consultar el catálogo autorizado.", status);
    }
    return response.json();
  }

  async verifyAdultConsent(accessToken) {
    if (!accessToken) throw new CatalogError("El espacio +18 requiere iniciar sesión y confirmar mayoría de edad.", 401);
    const user = await this.request("/auth/v1/user", accessToken);
    if (!user || typeof user.id !== "string" || !/^[0-9a-f-]{36}$/i.test(user.id)) {
      throw new CatalogError("La sesión no es válida.", 401);
    }
    const query = new URLSearchParams({
      select: "confirmed_18_plus,confirmed_at",
      user_id: "eq." + user.id,
      limit: "1"
    });
    const rows = await this.request("/rest/v1/age_access_consents?" + query, accessToken);
    if (!Array.isArray(rows) || rows.length !== 1 ||
        rows[0].confirmed_18_plus !== true || !rows[0].confirmed_at) {
      throw new CatalogError("El acceso +18 requiere una confirmación de mayoría de edad vigente en tu cuenta.", 403);
    }
    return { userId: user.id, accessToken };
  }

  async list({ category, country, accessToken }) {
    const query = new URLSearchParams({ select: category === "tv_en_vivo" ? TV_FIELDS : category === "deportes" ? SPORTS_FIELDS : category === "musica" ? MUSIC_FIELDS : MEDIA_FIELDS, is_published: "eq.true", order: "title.asc", limit: "500" });
    if (country !== "ALL") query.set("country_code", "eq." + country);

    if (category === "tv_en_vivo") {
      query.set("playback_type", "in.(official_web,hls,dash,youtube_live)");
      return this.request("/rest/v1/tv_channels?" + query, accessToken);
    }

    if (category === "musica") {
      query.set("order", "created_at.desc");
      return this.request("/rest/v1/music_items?" + query, accessToken);
    }

    if (category === "peliculas") {
      query.set("kind", "eq.movie");
      query.set("is_adult_content", "eq.false");
      return this.request("/rest/v1/catalog_items?" + query, accessToken);
    }

    if (category === "series") {
      query.set("kind", "in.(series,episode)");
      query.set("is_adult_content", "eq.false");
      return this.request("/rest/v1/catalog_items?" + query, accessToken);
    }

    if (category === "adultos") {
      query.set("kind", "in.(movie,series,episode)");
      query.set("is_adult_content", "eq.true");
      return this.request("/rest/v1/catalog_items?" + query, accessToken);
    }

    throw new CatalogError("Categoría no admitida.", 400);
  }

  async find({ category, country, id, accessToken }) {
    const query = new URLSearchParams({
      select: category === "tv_en_vivo" ? TV_FIELDS : category === "musica" ? MUSIC_FIELDS : MEDIA_FIELDS,
      id: "eq." + id,
      is_published: "eq.true",
      limit: "1"
    });
    if (country !== "ALL") query.set("country_code", "eq." + country);

    let path;
    if (category === "tv_en_vivo") {
      path = "/rest/v1/tv_channels?" + query;
    } else {
      if (category === "musica") {
        path = "/rest/v1/music_items?" + query;
      } else if (category === "peliculas") {
        query.set("kind", "eq.movie");
        query.set("is_adult_content", "eq.false");
      } else if (category === "series") {
        query.set("kind", "in.(series,episode)");
        query.set("is_adult_content", "eq.false");
      } else if (category === "adultos") {
        query.set("kind", "in.(movie,series,episode)");
        query.set("is_adult_content", "eq.true");
      } else {
        throw new CatalogError("Categoría no admitida.", 400);
      }
      if (category !== "musica") path = "/rest/v1/catalog_items?" + query;
    }
    const rows = await this.request(path, accessToken);
    return Array.isArray(rows) ? rows[0] ?? null : null;
  }
}

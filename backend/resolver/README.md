# MILTVPLUS Authorized Media Resolver

Node.js 22 + Express service for the MILTVPLUS catalogue and playback hand-off. It is deliberately an **authorization gate**, not a stream extractor.

## Capabilities

- Reads only published rows through Supabase REST and existing Row Level Security.
- Organizes TV, movies, series/episodes and the separately gated adult catalogue.
- Returns native HLS/DASH URLs only when the rights grant is verified, current, region-matched and the source is online.
- Returns the broadcaster's official HTTPS page for `official_web` and `youtube_live`; playback stays in the broadcaster's official player.
- Refuses unverified, expired, out-of-region, offline or malformed sources.
- TV pages and direct streams require an `online` health check no older than 15 minutes; stale checks are hidden from playback until the monitor refreshes them.
- Does not rewrite, scrape, decrypt, proxy or extract third-party stream URLs. No `yt-dlp`, ad removal, paywall bypass, DRM circumvention, or fake auto-healing.
- Uses bounded request timeouts, Helmet security headers, rate limiting and exact-origin CORS configuration.

## Configuration

Node.js 22 or newer is required.

```sh
cp .env.example .env
npm install --no-package-lock
npm run check
npm test
npm start
```

Required environment variables:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY` (or `SUPABASE_PUBLISHABLE_KEY`)

The service uses the public key and caller's JWT only. Do not put a Supabase service-role key, signing key, TMDB token or other secret in this service's public configuration. Set `CORS_ORIGINS` to a comma-separated exact origin allowlist if browser clients need CORS.

## HTTP API

- `GET /healthz`
- `GET /api/v2/catalog/tv_en_vivo/AR`
- `GET /api/v2/catalog/peliculas/AR`
- `GET /api/v2/catalog/series/AR`
- `GET /api/v2/catalog/adultos/AR` (requires `Authorization: Bearer <Supabase user access token>` and a recorded 18+ consent)
- `GET /api/v2/resolve/tv_en_vivo/AR/<uuid>`
- `GET /api/v2/resolve/peliculas/AR/<uuid>`
- `GET /api/v2/resolve/series/AR/<uuid>`
- `GET /api/v2/resolve/adultos/AR/<uuid>` (same account and consent gate)

Country is an ISO 3166-1 alpha-2 code; `global` is accepted for directory browsing. Direct playback still requires a specific region and a matching rights grant. Global browse therefore does not grant worldwide playback.

### Resolution behavior

| Catalog row | API result |
|---|---|
| TV `official_web` | Official HTTPS viewing page |
| TV `youtube_live` | Official YouTube Live page; never extracted |
| TV `hls` / `dash` | Direct URL only with current rights and online status |
| Movie/series with licensed stream | Direct URL only with current rights |
| Movie/series with official source page | Official page, not an extracted stream |
| Missing/expired/region-mismatched rights | HTTP 403/503; no playable URL |

## Adult catalogue protection

Migration `20261114120000_adult_catalog_access_gate.sql` adds `is_adult_content` and updates the database read policy. Adult rows are hidden by RLS unless the signed-in user has a self-attested 18+ consent record. The resolver checks the user's Supabase session and consent again before returning adult rows.

This is an access-control gate, **not formal identity or legal-age verification**. Do not describe it as such.

## Availability and auto-recovery

The existing Supabase TV health monitor checks registered official pages periodically and records status. This resolver re-checks publication, source status, rights dates and region at request time. It does not invent replacement URLs or rotate expiring third-party tokens. A replacement source must be supplied and approved by the rights holder before it can be published.

## Deployment

The included Dockerfile can be used on a Node-compatible host. Provide the environment variables through the host's secret/configuration manager. Hosting, outbound bandwidth and any paid plan are infrastructure costs outside the app; do not silently add a paid dependency to MILTVPLUS. This repository change does not create a hosted service or claim a public endpoint exists.

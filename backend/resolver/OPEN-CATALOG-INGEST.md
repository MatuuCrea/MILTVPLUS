# Rights-gated open catalog ingestion

Run from `backend/resolver` with Node 22:

```sh
SUPABASE_URL=https://<project>.supabase.co \
SUPABASE_SERVICE_ROLE_KEY=<server-only-secret> \
npm run sync:catalog
```

The script reads `src/approved-catalog.json`, validates direct media URLs, checks HLS manifests or media content types, and upserts accepted rows into the existing catalog tables. It never downloads or republishes media.

## Required human review

Each manifest item must contain a stable UUID `id` (reused on every sync), plus `rights_review_status: "approved"`, `rights_verified: true`, a named `rights_holder`, an HTTPS `rights_reference` that documents redistribution/embedding rights, explicit `rights_regions`, and a valid `rights_valid_from` (plus optional `rights_valid_until`). Approval is a curator's legal review, not inferred from a public M3U index, a successful HTTP response, a Creative Commons label without license/scope review, or a public-domain claim without provenance.

Supported item types: `tv` (HLS; also requires an official HTTPS watch URL for the channel record), `sports` (HLS; requires an official event info URL and ISO start time), `movie`, `series`, `episode` (direct MP4-like video; episodes also require `series_id`, `season_number`, and `episode_number`), and `music` (direct audio). HLS requires an HTTPS manifest beginning with `#EXTM3U` and containing an HLS tag. Video/audio URLs must answer HEAD with an appropriate media content type. Redirects are rejected.

Only use a Supabase service-role key in the protected backend environment; never put it in mobile, desktop, Tizen, webOS, source control, or a public build. The manifest starts empty intentionally. No catalog rows are invented or published by default. Review rejected entries from the JSON report before correcting their source or rights metadata.

## Native-only playback API

`GET /api/v2/native/resolve/:category/:country/:contentId` returns only an authorized direct HTTPS media URL suitable for a native player. It never returns `watchUrl`, YouTube page URLs, or browser fallbacks. It responds with HTTP 409 when the catalog item only has an official web page or no direct stream. Clients should pass the Supabase bearer token for protected content. Existing `/api/v2/resolve/...` remains a compatibility endpoint and may describe official web pages; native clients should use the strict route.

## YouTube and TMDB boundaries

The resolver deliberately does not extract YouTube's internal media URLs or bypass YouTube's player/advertising/authorization controls. A YouTube channel ID or watch URL is not a redistribution license and is not accepted as a native stream source. TMDB is metadata discovery only: posters, descriptions, dates, and IDs do not include audiovisual playback rights or media streams. A TMDB title must not be published as playable until a separately licensed direct stream has passed the rights-gated ingestion workflow above.

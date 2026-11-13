# Android TV catalog playback routing

The remote `tv_channels` mapper keeps two playback modes separate:

- `hls` / `dash`: an HTTPS direct manifest is exposed as `actionUrl` and opened by `ChannelPlayerActivity` (Media3/ExoPlayer).
- `official_web`: the broadcaster's `official_watch_url` is exposed as `infoUrl` and opened by `OfficialWatchActivity` (immersive WebView).
- `youtube_live`: the broadcaster's official YouTube Live page in `stream_url` is exposed as `infoUrl`, not `actionUrl`, so Media3 does not try to decode a YouTube webpage as an HLS/DASH manifest. The YouTube page is opened in the official WebView and remains subject to YouTube/broadcaster availability, region, account and embedding policies.

The official-player lineup refreshes from the published Supabase catalog while open. The direct HLS/DASH player refreshes its native playlist independently. A YouTube URL is not treated as a native HLS/DASH stream, and a page that serves a replay or VOD must not be labeled `youtube_live`.

No direct stream or redistribution rights are inferred from a channel name or a public page. The database publication guard continues to require documented rights for embedded/retransmitted stream modes.

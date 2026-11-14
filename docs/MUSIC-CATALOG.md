# MILTVPLUS Music

The Android client uses the published `public.music_items` catalog for discovery. It supports artists, albums, playlists and tracks. Search within the screen filters the published catalog; the Spotify and YouTube Music actions open each provider's official search page using the entered term.

## Playback behavior

- A published row with a verified direct `stream_url` plays in the in-app Media3 player.
- A row with only `external_url` opens that official provider page. The app does not scrape provider pages or bypass subscriptions/DRM.
- The Android `MusicPlaybackService` owns a MediaSession and foreground playback notification so audio can continue with the screen locked or while another app is open.
- Audio focus is handled by Media3. Android may pause or duck playback for phone calls, alarms, other audio apps, Bluetooth routing changes, or system policy; the app cannot guarantee zero interruptions.

## Publishing a direct audio stream

The database migration `20261114002753_add_licensed_music_streams.sql` adds `stream_url`. A direct stream must be HTTPS and the row must include:

- `rights_verified = true`
- `rights_holder` with the actual rights holder
- `rights_reference` with a contract/license/public-domain reference
- `rights_regions` listing the permitted ISO country codes

A database check constraint rejects a direct stream if any of those rights fields are missing. The row must also be `is_published = true` before the public app can see it. Set `external_url` for the artist/label/platform's official page when there is no direct redistribution license.

## Empty catalog

The live music table currently contains no published music rows. The app intentionally shows a welcoming empty state and official-provider search links rather than fake songs, fake counts, or unauthorized streams. Add approved tracks and URLs when the rights are ready.

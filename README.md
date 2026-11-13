# Veluna (Android)

Native Android rewrite of Veluna built with Kotlin, Jetpack Compose, Material Design 3, Media3 ExoPlayer, Room, DataStore, and OkHttp.

## Features

- **Home & Discovery**: Time-of-day greeting, mood/genre filter chips, pinned Shortcuts, Jump Back In, Familiar Artists, Quick Picks, and YouTube Music InnerTube shelves.
- **Search & Moods**: Live debounced search suggestions, categorized results (Top, Songs, Videos, Albums, Artists, Playlists), search history, and Moods & Genres discovery tiles.
- **Library & Local Music**: Local playlists with Photo Picker cover editing, `On Repeat` auto-playlist, `Liked Music`, saved albums, followed artists, offline track downloads, and SAF local audio file/folder playback.
- **Immersive Player & Audio**: Media3 `MediaSessionService` background playback, dynamic artwork wash, Queue + Autoplay radio continuation, 10-band Equalizer presets, loudness normalization, playback speed, and sleep timer.
- **Synced Lyrics & Comments**: Multi-provider lyrics waterfall (`Boidu`, `LyricsPlus`, `LRCLIB`, `YouTube Music`, `SimpMusic`) with click-to-seek, timing offset nudge, kana/hangul romanization, and YouTube track comments.
- **Listening Stats & History**: Range filters, daily bar chart, 7x24 weekday-hour heatmap, streaks, top songs/artists/albums, and chronological listening history.
- **Themes, Scrobbling & Backup**: 7 theme presets, Light/Dark/System modes, custom/artwork-adaptive accent colors, Last.fm & ListenBrainz scrobbling, M3U8/CSV/YouTube playlist import & export, and JSON backup/restore.

## Build

Open in Android Studio or build with Gradle (`gradle :app:assembleDebug`).

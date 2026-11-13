# Veluna (Android)

[![Build & Release APK](../../actions/workflows/release-apk.yml/badge.svg)](../../actions/workflows/release-apk.yml)
[![Download APK](https://img.shields.io/badge/Download-veluna--android.apk-EA580C?style=flat&logo=android&logoColor=white)](../../releases/latest)

Native Android rewrite of Veluna built with Kotlin, Jetpack Compose, Material Design 3, Media3 ExoPlayer, Room, DataStore, and OkHttp.

## Download & Install (Android 8.0+)

Prebuilt, installable APKs are published automatically on **GitHub Releases**:

- **[Open Latest Release & Download `veluna-android.apk`](../../releases/latest)**
- Direct APK link (once the first GitHub Actions build completes): [`veluna-android.apk`](../../releases/latest/download/veluna-android.apk)

### Sideloading Instructions

1. On your Android device (Android 8.0 / API 26 or newer), open the **[Latest Release](../../releases/latest)** page and tap **`veluna-android.apk`** under **Assets** to download it.
2. Open the downloaded `.apk` file from your browser downloads or Files app.
3. If Android asks for permission, tap **Settings** and enable **Allow from this source** (Install unknown apps).
4. Tap **Install** and open **Veluna**.

> **Note for Maintainers**: Every push to `main` / `master`, semantic tag (`v*`), or manual run in the **Actions** tab triggers `.github/workflows/release-apk.yml` to compile `:app:assembleDebug` and publish a verified `veluna-android.apk` directly to GitHub Releases.

## Features

- **Home & Discovery**: Time-of-day greeting, mood/genre filter chips, pinned Shortcuts, Jump Back In, Familiar Artists, Quick Picks, and YouTube Music InnerTube shelves.
- **Search & Moods**: Live debounced search suggestions, categorized results (Top, Songs, Videos, Albums, Artists, Playlists), search history, and Moods & Genres discovery tiles.
- **Library & Local Music**: Local playlists with Photo Picker cover editing, `On Repeat` auto-playlist, `Liked Music`, saved albums, followed artists, offline track downloads, and SAF local audio file/folder playback.
- **Immersive Player & Audio**: Media3 `MediaSessionService` background playback, dynamic artwork wash, Queue + Autoplay radio continuation, 10-band Equalizer presets, loudness normalization, playback speed, and sleep timer.
- **Synced Lyrics & Comments**: Multi-provider lyrics waterfall (`Boidu`, `LyricsPlus`, `LRCLIB`, `YouTube Music`, `SimpMusic`) with click-to-seek, timing offset nudge, kana/hangul romanization, and YouTube track comments.
- **Listening Stats & History**: Range filters, daily bar chart, 7x24 weekday-hour heatmap, streaks, top songs/artists/albums, and chronological listening history.
- **Themes, Scrobbling & Backup**: 7 theme presets, Light/Dark/System modes, custom/artwork-adaptive accent colors, Last.fm & ListenBrainz scrobbling, M3U8/CSV/YouTube playlist import & export, and JSON backup/restore.

## Build Locally

Open in Android Studio or build an installable APK locally with Gradle 9.3.1 (JDK 21):

```bash
gradle :app:assembleDebug
```

The output APK will be written to `app/build/outputs/apk/debug/app-debug.apk`.

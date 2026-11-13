package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.local.BlockedArtistEntity
import com.example.data.local.DownloadedTrackEntity
import com.example.data.local.FollowedArtistEntity
import com.example.data.local.LikedSongEntity
import com.example.data.local.ListenEntity
import com.example.data.local.ListenSongEntity
import com.example.data.local.LocalPlaylistEntity
import com.example.data.local.LocalPlaylistTrackEntity
import com.example.data.local.LocalTrackEntity
import com.example.data.local.SavedAlbumEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.ShortcutEntity
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaDao
import com.example.data.local.VelunaUserPrefs
import com.example.data.network.InnerTubeClient
import com.example.model.BrowseItem
import com.example.model.BrowseKind
import com.example.model.DailyListening
import com.example.model.HistoryGroup
import com.example.model.LIKED_MUSIC_ID
import com.example.model.LOCAL_PLAYLIST_PREFIX
import com.example.model.LOCAL_SONG_PREFIX
import com.example.model.ON_REPEAT_ID
import com.example.model.PlaylistPage
import com.example.model.SongItem
import com.example.model.StatsRange
import com.example.model.StatsSummary
import com.example.model.TopAlbumStat
import com.example.model.TopArtistStat
import com.example.model.TopSongStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class VelunaBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val prefs: VelunaUserPrefs,
    val playlists: List<BackupPlaylist> = emptyList(),
    val likedSongs: List<SongItem> = emptyList(),
    val blockedArtists: List<String> = emptyList()
)

@Serializable
data class BackupPlaylist(
    val title: String,
    val description: String,
    val tracks: List<SongItem>
)

class VelunaRepository(
    private val context: Context,
    private val dao: VelunaDao,
    private val prefsRepo: UserPrefsRepository,
    val innerTube: InnerTubeClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeSong(song: SongItem): String = json.encodeToString(song)
    fun decodeSong(raw: String): SongItem? = runCatching { json.decodeFromString<SongItem>(raw) }.getOrNull()

    val shortcutsFlow: Flow<List<BrowseItem>> = dao.observeShortcuts().map { rows ->
        rows.map {
            BrowseItem(
                kind = runCatching { BrowseKind.valueOf(it.kind) }.getOrDefault(BrowseKind.PLAYLIST),
                id = it.id,
                title = it.title,
                subtitle = it.subtitle,
                thumbnail = it.thumbnail
            )
        }
    }

    val playlistsBrowseFlow: Flow<List<BrowseItem>> = combine(
        dao.observePlaylists(),
        dao.observePlaylistTrackCounts(),
        dao.observeLikedSongs()
    ) { playlists, counts, liked ->
        val countMap = counts.associateBy { it.playlistId }
        val result = mutableListOf<BrowseItem>()
        result.add(
            BrowseItem(
                kind = BrowseKind.PLAYLIST,
                id = ON_REPEAT_ID,
                title = "On Repeat",
                subtitle = "Your most replayed tracks"
            )
        )
        result.add(
            BrowseItem(
                kind = BrowseKind.PLAYLIST,
                id = LIKED_MUSIC_ID,
                title = "Liked Music",
                subtitle = "${liked.size} songs"
            )
        )
        playlists.forEach { pl ->
            val info = countMap[pl.id]
            val thumb = pl.coverUri ?: info?.firstSongJson?.let(::decodeSong)?.thumbnail
            result.add(
                BrowseItem(
                    kind = BrowseKind.PLAYLIST,
                    id = "$LOCAL_PLAYLIST_PREFIX${pl.id}",
                    title = pl.title,
                    subtitle = "${info?.count ?: 0} tracks",
                    thumbnail = thumb
                )
            )
        }
        result
    }

    val likedSongsFlow: Flow<List<SongItem>> = dao.observeLikedSongs().map { list ->
        list.mapNotNull { decodeSong(it.songJson)?.copy(rating = com.example.model.Rating.LIKE) }
    }

    val likedVideoIdsFlow: Flow<Set<String>> = dao.observeLikedVideoIds()
        .map { it.toHashSet() }
        .distinctUntilChanged()

    val savedAlbumsFlow: Flow<List<BrowseItem>> = dao.observeSavedAlbums().map { list ->
        list.map {
            BrowseItem(
                kind = BrowseKind.ALBUM,
                id = it.albumId,
                title = it.title,
                subtitle = it.subtitle,
                thumbnail = it.thumbnail
            )
        }
    }

    val followedArtistsFlow: Flow<List<BrowseItem>> = dao.observeFollowedArtists().map { list ->
        list.map {
            BrowseItem(
                kind = BrowseKind.ARTIST,
                id = it.channelId,
                title = it.name,
                subtitle = it.subscribers,
                thumbnail = it.thumbnail
            )
        }
    }

    val localTracksFlow: Flow<List<SongItem>> = dao.observeLocalTracks().map { list ->
        list.map {
            SongItem(
                videoId = "$LOCAL_SONG_PREFIX${it.uri}",
                title = it.title,
                artists = it.artist,
                album = it.album,
                duration = formatSecs(it.durationSecs),
                thumbnail = it.coverUri,
                isLocal = true,
                localUri = it.uri
            )
        }
    }

    val downloadedTracksFlow: Flow<List<SongItem>> = dao.observeDownloads().map { list ->
        list.mapNotNull { entity ->
            decodeSong(entity.songJson)?.copy(localUri = entity.filePath)
        }
    }

    val searchHistoryFlow: Flow<List<String>> = dao.observeSearchHistory().map { list ->
        list.map { it.query }
    }

    val blockedArtistsFlow: Flow<List<BlockedArtistEntity>> = dao.observeBlockedArtists()

    fun observeIsAlbumSaved(albumId: String): Flow<Boolean> = dao.observeIsAlbumSaved(albumId)
    fun observeIsArtistFollowed(channelId: String): Flow<Boolean> = dao.observeIsArtistFollowed(channelId)

    suspend fun toggleLike(song: SongItem): Boolean {
        val liked = dao.isSongLiked(song.videoId)
        if (liked) {
            dao.deleteLikedSong(song.videoId)
            return false
        } else {
            dao.insertLikedSong(LikedSongEntity(song.videoId, encodeSong(song)))
            return true
        }
    }

    suspend fun isLiked(videoId: String): Boolean = dao.isSongLiked(videoId)

    suspend fun toggleSaveAlbum(album: com.example.model.AlbumPage) {
        val saved = dao.isAlbumSaved(album.id)
        setAlbumSaved(album, !saved)
    }

    suspend fun setAlbumSaved(album: com.example.model.AlbumPage, save: Boolean) {
        if (save) {
            dao.insertSavedAlbum(
                SavedAlbumEntity(
                    albumId = album.id,
                    title = album.title,
                    subtitle = album.artist ?: album.subtitle,
                    thumbnail = album.thumbnail
                )
            )
        } else {
            dao.deleteSavedAlbum(album.id)
        }
    }

    suspend fun setArtistFollowed(artist: com.example.model.ArtistPage, follow: Boolean) {
        if (follow) {
            dao.insertFollowedArtist(
                FollowedArtistEntity(
                    channelId = artist.channelId,
                    name = artist.name,
                    thumbnail = artist.thumbnail ?: artist.avatar,
                    subscribers = artist.subscribers
                )
            )
        } else {
            dao.deleteFollowedArtist(artist.channelId)
        }
    }

    suspend fun createPlaylist(title: String, description: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.insertPlaylist(
            LocalPlaylistEntity(
                title = title.trim(),
                description = description.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updatePlaylist(playlistId: Long, title: String, description: String, coverUri: String?) {
        dao.updatePlaylist(playlistId, title.trim(), description.trim(), coverUri, System.currentTimeMillis())
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.clearPlaylistTracks(playlistId)
        dao.deletePlaylist(playlistId)
        dao.deleteShortcut("$LOCAL_PLAYLIST_PREFIX$playlistId")
    }

    suspend fun addTrackToPlaylist(playlistId: Long, song: SongItem, skipDuplicates: Boolean = true): Boolean {
        val existing = dao.getPlaylistTracks(playlistId)
        if (skipDuplicates && existing.any { it.videoId == song.videoId }) return false
        val pos = dao.nextPlaylistPosition(playlistId)
        dao.insertPlaylistTrack(
            LocalPlaylistTrackEntity(
                playlistId = playlistId,
                videoId = song.videoId,
                songJson = encodeSong(song),
                position = pos
            )
        )
        return true
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, videoId: String) {
        dao.deletePlaylistTrack(playlistId, videoId)
    }

    suspend fun removeDuplicateTracksFromPlaylist(playlistId: Long): Int {
        val tracks = dao.getPlaylistTracks(playlistId)
        val seen = mutableSetOf<String>()
        var removed = 0
        tracks.forEach { row ->
            if (!seen.add(row.videoId)) {
                dao.deletePlaylistTrackByRowId(row.id)
                removed++
            }
        }
        return removed
    }

    suspend fun loadPlaylistPage(id: String): Result<PlaylistPage> = withContext(Dispatchers.IO) {
        when {
            id == ON_REPEAT_ID -> {
                val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
                val listens = dao.getListensSince(thirtyDaysAgo)
                val songsMap = dao.getAllListenSongs().associateBy { it.videoId }
                val counts = listens.groupBy { it.videoId }
                    .mapValues { (_, list) -> list.size to list.sumOf { it.secs } }
                    .entries
                    .sortedWith(compareByDescending<Map.Entry<String, Pair<Int, Long>>> { it.value.first }.thenByDescending { it.value.second })
                    .take(25)
                val tracks = counts.mapNotNull { (vid, _) ->
                    songsMap[vid]?.let { decodeSong(it.songJson) }
                }
                Result.success(
                    PlaylistPage(
                        id = ON_REPEAT_ID,
                        title = "On Repeat",
                        subtitle = "${tracks.size} songs • Most played in the last 30 days",
                        thumbnail = tracks.firstOrNull()?.thumbnail,
                        items = tracks,
                        owned = false,
                        isLocal = true
                    )
                )
            }
            id == LIKED_MUSIC_ID -> {
                val tracks = dao.getLikedSongsOnce().mapNotNull {
                    decodeSong(it.songJson)?.copy(rating = com.example.model.Rating.LIKE)
                }
                Result.success(
                    PlaylistPage(
                        id = LIKED_MUSIC_ID,
                        title = "Liked Music",
                        subtitle = "${tracks.size} songs • Auto playlist",
                        thumbnail = tracks.firstOrNull()?.thumbnail,
                        items = tracks,
                        owned = false,
                        isLocal = true
                    )
                )
            }
            id.startsWith(LOCAL_PLAYLIST_PREFIX) -> {
                val numId = id.removePrefix(LOCAL_PLAYLIST_PREFIX).toLongOrNull()
                    ?: return@withContext Result.failure(IllegalArgumentException("Invalid playlist id"))
                val pl = dao.getPlaylistById(numId)
                    ?: return@withContext Result.failure(IllegalStateException("Playlist not found"))
                val rows = dao.getPlaylistTracks(numId)
                val tracks = rows.mapNotNull { decodeSong(it.songJson)?.copy(addedAt = it.addedAt) }
                Result.success(
                    PlaylistPage(
                        id = id,
                        title = pl.title,
                        subtitle = "${tracks.size} tracks",
                        description = pl.description,
                        thumbnail = pl.coverUri ?: tracks.firstOrNull()?.thumbnail,
                        coverUri = pl.coverUri,
                        items = tracks,
                        owned = true,
                        isLocal = true
                    )
                )
            }
            else -> innerTube.getPlaylist(id)
        }
    }

    suspend fun toggleShortcut(item: BrowseItem) {
        dao.upsertShortcut(
            ShortcutEntity(
                id = item.id,
                kind = item.kind.name,
                title = item.title,
                subtitle = item.subtitle,
                thumbnail = item.thumbnail,
                position = 0
            )
        )
    }

    suspend fun removeShortcut(id: String) = dao.deleteShortcut(id)

    suspend fun recordSearchQuery(query: String) {
        if (query.isNotBlank()) dao.upsertSearchHistory(SearchHistoryEntity(query.trim()))
    }

    suspend fun removeSearchQuery(query: String) = dao.deleteSearchHistory(query)
    suspend fun clearSearchHistory() = dao.clearSearchHistory()

    suspend fun blockArtist(name: String, channelId: String? = null) {
        if (name.isNotBlank()) dao.insertBlockedArtist(BlockedArtistEntity(name.trim(), channelId))
    }

    suspend fun unblockArtist(name: String) = dao.deleteBlockedArtist(name)

    // --- Local Audio Files (Play Policy compliant SAF picking) ---
    suspend fun importAudioUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val entities = mutableListOf<LocalTrackEntity>()
        val coversDir = File(context.filesDir, "local_covers").apply { mkdirs() }

        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                    ?: "Audio Track"
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: "Unknown Artist"
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?: "Local Audio"
                val trackNo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull() ?: 0
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

                val embeddedArt = retriever.embeddedPicture
                var coverUri: String? = null
                if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                    val file = File(coversDir, "${uri.toString().hashCode()}.jpg")
                    file.writeBytes(embeddedArt)
                    coverUri = Uri.fromFile(file).toString()
                }

                entities.add(
                    LocalTrackEntity(
                        uri = uri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        albumKey = "${album.lowercase()}::${artist.lowercase()}",
                        trackNo = trackNo,
                        durationSecs = durationMs / 1000L,
                        coverUri = coverUri
                    )
                )
            } catch (_: Throwable) {
                // Skip unreadable URI
            } finally {
                runCatching { retriever.release() }
            }
        }
        if (entities.isNotEmpty()) {
            dao.upsertLocalTracks(entities)
        }
        entities.size
    }

    suspend fun importFolderTree(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val audioUris = mutableListOf<Uri>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx)
                val mime = cursor.getString(mimeIdx).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioUris.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                }
            }
        }
        importAudioUris(audioUris)
    }

    suspend fun removeLocalTrack(uri: String) = dao.deleteLocalTrack(uri)

    // --- Playlist Import (Spotify CSV / M3U / YouTube URL) ---
    suspend fun importFromYouTubeUrl(urlOrId: String, onProgress: (String) -> Unit): Result<Long> = withContext(Dispatchers.IO) {
        val listId = extractYouTubePlaylistId(urlOrId)
            ?: return@withContext Result.failure(IllegalArgumentException("Could not find a YouTube playlist ID in that link."))
        onProgress("Fetching playlist from YouTube Music…")
        val page = innerTube.getPlaylist(listId).getOrElse { return@withContext Result.failure(it) }
        val newId = createPlaylist(page.title, page.description.orEmpty())
        page.items.forEach { song ->
            addTrackToPlaylist(newId, song, skipDuplicates = false)
        }
        Result.success(newId)
    }

    suspend fun importFromFileUri(
        uri: Uri,
        defaultTitle: String,
        onProgress: (done: Int, total: Int, status: String) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return@withContext Result.failure(IllegalStateException("Could not read file"))

        val queries = parseCsvOrM3uQueries(text)
        if (queries.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No tracks found in file (supports Exportify CSV or M3U)."))
        }

        val playlistId = createPlaylist(defaultTitle, "Imported playlist")
        queries.forEachIndexed { idx, q ->
            onProgress(idx + 1, queries.size, "Matching: $q")
            val match = innerTube.searchSongs(q).getOrNull()?.firstOrNull()
            if (match != null) {
                addTrackToPlaylist(playlistId, match, skipDuplicates = true)
            }
        }
        Result.success(playlistId)
    }

    fun exportPlaylistAsM3u(title: String, tracks: List<SongItem>): String {
        val sb = StringBuilder("#EXTM3U\n#PLAYLIST:$title\n")
        tracks.forEach { s ->
            val secs = s.durationSeconds.takeIf { it > 0 } ?: -1
            sb.append("#EXTINF:$secs,${s.artists} - ${s.title}\n")
            if (s.isLocal && s.localUri != null) {
                sb.append("${s.localUri}\n")
            } else {
                sb.append("https://music.youtube.com/watch?v=${s.videoId}\n")
            }
        }
        return sb.toString()
    }

    // --- Backup & Restore ---
    suspend fun safeExportBackupJson(prefs: VelunaUserPrefs): String = withContext(Dispatchers.IO) {
        val playlistRows = dao.getPlaylistsOnce()
        val likedRows = dao.getLikedSongsOnce().mapNotNull { decodeSong(it.songJson) }
        val blockedRows = dao.getBlockedArtistsOnce().map { it.name }

        val playlists = playlistRows.map { pl ->
            val tracks = dao.getPlaylistTracks(pl.id).mapNotNull { decodeSong(it.songJson) }
            BackupPlaylist(pl.title, pl.description, tracks)
        }

        json.encodeToString(
            VelunaBackupPayload(
                prefs = prefs,
                playlists = playlists,
                likedSongs = likedRows,
                blockedArtists = blockedRows
            )
        )
    }

    suspend fun importBackupJson(rawJson: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.decodeFromString<VelunaBackupPayload>(rawJson)
            prefsRepo.applyBackup(payload.prefs)
            payload.likedSongs.forEach { song ->
                dao.insertLikedSong(LikedSongEntity(song.videoId, encodeSong(song)))
            }
            payload.blockedArtists.forEach { name ->
                dao.insertBlockedArtist(BlockedArtistEntity(name))
            }
            var count = 0
            payload.playlists.forEach { pl ->
                val id = createPlaylist(pl.title, pl.description)
                pl.tracks.forEach { addTrackToPlaylist(id, it, skipDuplicates = false) }
                count++
            }
            count
        }
    }

    // --- Stats & History ---
    suspend fun computeStats(range: StatsRange): StatsSummary = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val daysCount = range.days
        val sinceMs = if (daysCount != null) nowMs - daysCount.toLong() * 86_400_000L else 0L
        val allListens = dao.getAllListensAsc()
        val songMap = dao.getAllListenSongs().mapNotNull { row ->
            decodeSong(row.songJson)?.let { row.videoId to it }
        }.toMap()

        val inWindow = allListens.filter { it.playedAt >= sinceMs }
        val prevSecs = if (daysCount != null) {
            val prevStart = sinceMs - daysCount.toLong() * 86_400_000L
            allListens.filter { it.playedAt in prevStart until sinceMs }.sumOf { it.secs }
        } else null

        val zone = ZoneId.systemDefault()
        val dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        val labelFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        val dailyMap = linkedMapOf<String, Long>()
        val targetDays = daysCount ?: 30
        val today = LocalDate.now(zone)
        for (i in (targetDays - 1) downTo 0) {
            val d = today.minusDays(i.toLong())
            dailyMap[d.format(dayFmt)] = 0L
        }

        val heatmap = LongArray(7 * 24)
        val songAgg = mutableMapOf<String, Pair<Int, Long>>()

        inWindow.forEach { l ->
            val zdt = Instant.ofEpochMilli(l.playedAt).atZone(zone)
            val key = zdt.toLocalDate().format(dayFmt)
            if (key in dailyMap || daysCount == null) {
                dailyMap[key] = (dailyMap[key] ?: 0L) + l.secs
            }
            val dow = zdt.dayOfWeek.value % 7 // 0=Sunday..6=Saturday
            heatmap[dow * 24 + zdt.hour] += l.secs

            val prev = songAgg[l.videoId] ?: (0 to 0L)
            songAgg[l.videoId] = (prev.first + 1) to (prev.second + l.secs)
        }

        val dailySeries = dailyMap.entries.toList().takeLast(if (targetDays > 90) 90 else targetDays).map { (key, secs) ->
            val d = runCatching { LocalDate.parse(key, dayFmt) }.getOrNull()
            DailyListening(dateKey = key, label = d?.format(labelFmt) ?: key, secs = secs)
        }

        val topSongs = songAgg.entries.mapNotNull { (vid, agg) ->
            val song = songMap[vid] ?: return@mapNotNull null
            TopSongStat(song = song, plays = agg.first, secs = agg.second)
        }.sortedWith(compareByDescending<TopSongStat> { it.plays }.thenByDescending { it.secs })

        val artistAgg = mutableMapOf<String, TopArtistStat>()
        val albumAgg = mutableMapOf<String, TopAlbumStat>()
        topSongs.forEach { st ->
            val primaryArtist = st.song.artists.split(",").firstOrNull()?.trim().orEmpty().ifBlank { "Unknown Artist" }
            val curA = artistAgg[primaryArtist]
            artistAgg[primaryArtist] = if (curA == null) {
                TopArtistStat(primaryArtist, st.song.artistId, st.song.thumbnail, st.plays, st.secs)
            } else {
                curA.copy(plays = curA.plays + st.plays, secs = curA.secs + st.secs)
            }

            val alId = st.song.albumId
            val alTitle = st.song.album
            if (!alId.isNullOrBlank() && !alTitle.isNullOrBlank()) {
                val curAl = albumAgg[alId]
                albumAgg[alId] = if (curAl == null) {
                    TopAlbumStat(alId, alTitle, primaryArtist, st.song.thumbnail, st.plays, st.secs)
                } else {
                    curAl.copy(plays = curAl.plays + st.plays, secs = curAl.secs + st.secs)
                }
            }
        }

        val (curStreak, bestStreak) = computeStreaks(allListens, zone)

        StatsSummary(
            range = range,
            totalSecs = inWindow.sumOf { it.secs },
            prevSecs = prevSecs,
            totalPlays = inWindow.size,
            uniqueSongs = topSongs.size,
            uniqueArtists = artistAgg.size,
            currentStreak = curStreak,
            bestStreak = bestStreak,
            dailySeries = dailySeries,
            hourlyHeatmap = heatmap.toList(),
            topSongs = topSongs.take(10),
            topArtists = artistAgg.values.sortedByDescending { it.secs }.take(8),
            topAlbums = albumAgg.values.sortedByDescending { it.secs }.take(6)
        )
    }

    fun observeHistoryGroups(): Flow<List<HistoryGroup>> = combine(
        dao.observeAllListens().distinctUntilChanged { old, new ->
            old.size == new.size &&
                old.firstOrNull()?.id == new.firstOrNull()?.id &&
                old.lastOrNull()?.id == new.lastOrNull()?.id
        },
        dao.observeListenSongs().distinctUntilChanged { old, new ->
            old.size == new.size &&
                old.firstOrNull()?.videoId == new.firstOrNull()?.videoId &&
                old.lastOrNull()?.videoId == new.lastOrNull()?.videoId
        }
    ) { listens, songs ->
        val map = songs.mapNotNull { row -> decodeSong(row.songJson)?.let { row.videoId to it } }.toMap()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

        val grouped = linkedMapOf<String, MutableList<Pair<Long, SongItem>>>()
        listens.forEach { l ->
            val song = map[l.videoId] ?: return@forEach
            val date = Instant.ofEpochMilli(l.playedAt).atZone(zone).toLocalDate()
            val title = when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> date.format(fmt)
            }
            grouped.getOrPut(title) { mutableListOf() }.add(l.playedAt to song)
        }
        grouped.entries.map { HistoryGroup(it.key, it.value) }
    }.flowOn(Dispatchers.Default)

    private fun computeStreaks(listens: List<ListenEntity>, zone: ZoneId): Pair<Int, Int> {
        if (listens.isEmpty()) return 0 to 0
        val days = listens.map { Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() }
            .distinct().sorted()
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1].plusDays(1)) {
                run++
                if (run > best) best = run
            } else {
                run = 1
            }
        }
        val today = LocalDate.now(zone)
        var current = 0
        var cursor = if (days.last() == today || days.last() == today.minusDays(1)) days.last() else return 0 to best
        val daySet = days.toHashSet()
        while (daySet.contains(cursor)) {
            current++
            cursor = cursor.minusDays(1)
        }
        return current to best
    }

    private fun extractYouTubePlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("PL") || trimmed.startsWith("VL") || trimmed.startsWith("OLAK")) return trimmed
        val match = Regex("""[?&]list=([A-Za-z0-9_\-]+)""").find(trimmed)
        return match?.groupValues?.getOrNull(1)
    }

    private fun parseCsvOrM3uQueries(text: String): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        // M3U
        if (lines.first().startsWith("#EXTM3U")) {
            return lines.filter { it.startsWith("#EXTINF:") }
                .mapNotNull { it.substringAfter(',', "").trim().takeIf(String::isNotBlank) }
                .take(150)
        }

        // Exportify / generic CSV
        val headerCols = splitCsvLine(lines.first()).map { it.lowercase() }
        val trackIdx = headerCols.indexOfFirst { "track" in it || "title" in it || "song" in it }.takeIf { it >= 0 } ?: 0
        val artistIdx = headerCols.indexOfFirst { "artist" in it }.takeIf { it >= 0 } ?: 1

        return lines.drop(1).mapNotNull { line ->
            val cols = splitCsvLine(line)
            val title = cols.getOrNull(trackIdx)?.trim().orEmpty()
            val artist = cols.getOrNull(artistIdx)?.trim().orEmpty()
            if (title.isBlank()) null else "$title $artist".trim()
        }.take(150)
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        out.add(cur.toString())
        return out
    }

    private fun formatSecs(secs: Long): String {
        if (secs <= 0) return "0:00"
        val m = secs / 60
        val s = secs % 60
        return "%d:%02d".format(m, s)
    }
}

package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.data.local.BlockedArtistEntity
import com.example.data.local.LikedSongEntity
import com.example.data.local.LocalPlaylistEntity
import com.example.data.local.LocalPlaylistTrackEntity
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaDao
import com.example.data.local.VelunaUserPrefs
import com.example.data.repository.VelunaRepository
import com.example.model.AudioQuality
import com.example.model.SongItem
import com.example.model.TasteProfile
import com.example.model.TasteSeed
import com.example.model.ThemeMode
import com.example.model.VelunaThemePreset
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

data class CloudAuthUiState(
    val isSignedIn: Boolean = false,
    val uid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isAuthBusy: Boolean = false,
    val isSyncing: Boolean = false,
    val syncStatus: String? = null,
    val lastSyncedAtMs: Long? = null,
    val errorMessage: String? = null
)

class CloudSyncCoordinator(
    private val context: Context,
    private val dao: VelunaDao,
    private val prefsRepo: UserPrefsRepository,
    private val repository: VelunaRepository,
    private val cloudRepo: CloudSyncRepository,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val suppressLocalPushCount = AtomicInteger(0)

    private val _authState = MutableStateFlow(buildUiState(auth.currentUser))
    val authState: StateFlow<CloudAuthUiState> = _authState.asStateFlow()

    private var activeUserId: String? = null
    private var autoSignInAttempted = false
    private val watcherJobs = mutableListOf<Job>()

    // Track cloud baselines for diffing incremental local edits -> Firestore
    private val syncedLikedIds = mutableSetOf<String>()
    private val syncedHiddenNames = mutableSetOf<String>()
    private val syncedPlaylistTimestamps = mutableMapOf<String, String>() // cloudKey -> signature
    private val localPlaylistToCloudKey = mutableMapOf<Long, String>()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _authState.update { cur ->
            if (user == null) {
                CloudAuthUiState(
                    isSignedIn = false,
                    isAuthBusy = false,
                    isSyncing = false
                )
            } else {
                cur.copy(
                    isSignedIn = true,
                    uid = user.uid,
                    displayName = user.displayName ?: cur.displayName,
                    email = user.email ?: cur.email,
                    photoUrl = user.photoUrl?.toString() ?: cur.photoUrl,
                    isAuthBusy = false
                )
            }
        }

        if (user == null) {
            stopSyncForSignedOut()
        } else if (activeUserId != user.uid) {
            startSyncForUser(user)
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.let { existing ->
            startSyncForUser(existing)
        }
    }

    private fun buildUiState(user: FirebaseUser?): CloudAuthUiState {
        return if (user == null) {
            CloudAuthUiState(isSignedIn = false)
        } else {
            CloudAuthUiState(
                isSignedIn = true,
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl?.toString()
            )
        }
    }

    fun attemptAutoSignInOnLaunch(activityContext: Context) {
        if (autoSignInAttempted || auth.currentUser != null) return
        autoSignInAttempted = true
        scope.launch {
            GoogleAuthHelper.attemptAutoSignIn(activityContext)
        }
    }

    fun signIn(activityContext: Context) {
        if (_authState.value.isAuthBusy) return
        _authState.update { it.copy(isAuthBusy = true, errorMessage = null) }
        scope.launch {
            when (val outcome = GoogleAuthHelper.signInWithGoogle(activityContext)) {
                is GoogleSignInOutcome.Success -> {
                    _authState.update {
                        it.copy(
                            isSignedIn = true,
                            uid = outcome.user.uid,
                            displayName = outcome.user.displayName,
                            email = outcome.user.email,
                            photoUrl = outcome.user.photoUrl?.toString(),
                            isAuthBusy = false,
                            errorMessage = null
                        )
                    }
                    if (activeUserId != outcome.user.uid) {
                        startSyncForUser(outcome.user)
                    }
                }
                GoogleSignInOutcome.Cancelled -> {
                    _authState.update { it.copy(isAuthBusy = false) }
                }
                is GoogleSignInOutcome.Error -> {
                    _authState.update {
                        it.copy(
                            isAuthBusy = false,
                            errorMessage = outcome.message
                        )
                    }
                }
            }
        }
    }

    fun signOut() {
        _authState.update { it.copy(isAuthBusy = true, errorMessage = null) }
        scope.launch {
            stopSyncForSignedOut()
            GoogleAuthHelper.signOut(context)
            _authState.value = CloudAuthUiState(isSignedIn = false)
        }
    }

    fun syncNow() {
        val user = auth.currentUser ?: return
        scope.launch {
            runBidirectionalMerge(user)
        }
    }

    fun clearError() {
        _authState.update { it.copy(errorMessage = null) }
    }

    private fun stopSyncForSignedOut() {
        activeUserId = null
        watcherJobs.forEach { it.cancel() }
        watcherJobs.clear()
        syncedLikedIds.clear()
        syncedHiddenNames.clear()
        syncedPlaylistTimestamps.clear()
        localPlaylistToCloudKey.clear()
    }

    private fun startSyncForUser(user: FirebaseUser) {
        watcherJobs.forEach { it.cancel() }
        watcherJobs.clear()
        activeUserId = user.uid

        val mainJob = scope.launch {
            runBidirectionalMerge(user)
            if (auth.currentUser?.uid == user.uid) {
                launchWatchersLocked(user)
            }
        }
        watcherJobs.add(mainJob)
    }

    private suspend fun runBidirectionalMerge(user: FirebaseUser) {
        syncMutex.withLock {
            if (auth.currentUser?.uid != user.uid) return@withLock
            _authState.update {
                it.copy(isSyncing = true, syncStatus = "Syncing with cloud…", errorMessage = null)
            }
            suppressLocalPushCount.incrementAndGet()
            try {
                val snapshot = cloudRepo.fetchAccountSnapshot()
                if (auth.currentUser?.uid != user.uid) return@withLock

                // 1. Merge Profile & Preferences
                val localPrefs = prefsRepo.prefsFlow.first()
                val localTaste = prefsRepo.tasteProfileFlow.first()
                val cloudProfile = snapshot.profile

                val resolvedDisplayName = when {
                    localPrefs.userDisplayName.isNotBlank() && localPrefs.userDisplayName != "Listener" ->
                        localPrefs.userDisplayName
                    !cloudProfile?.displayName.isNullOrBlank() && cloudProfile?.displayName != "Listener" ->
                        cloudProfile!!.displayName
                    !user.displayName.isNullOrBlank() -> user.displayName!!
                    else -> "Listener"
                }

                if (resolvedDisplayName != localPrefs.userDisplayName) {
                    prefsRepo.setDisplayName(resolvedDisplayName)
                }

                val shouldAdoptCloudPrefs = cloudProfile != null &&
                    !localTaste.onboardingCompleted &&
                    cloudProfile.onboardingCompleted

                if (shouldAdoptCloudPrefs && cloudProfile != null) {
                    VelunaThemePreset.entries.find { it.id == cloudProfile.themePreset }
                        ?.let { prefsRepo.setThemePreset(it) }
                    runCatching { ThemeMode.valueOf(cloudProfile.themeMode) }.getOrNull()
                        ?.let { prefsRepo.setThemeMode(it) }
                    prefsRepo.setCustomAccent(cloudProfile.customAccentHex)
                    prefsRepo.setAdaptToArtwork(cloudProfile.adaptToArtwork)
                    prefsRepo.setCornerRadius(cloudProfile.cornerRadiusDp.toInt())
                    runCatching { AudioQuality.valueOf(cloudProfile.audioQuality) }.getOrNull()
                        ?.let { prefsRepo.setAudioQuality(it) }
                    prefsRepo.setAutoplay(cloudProfile.autoplay)
                    prefsRepo.setLoudnessNormalization(cloudProfile.loudnessNormalization)
                    prefsRepo.setHideMusicVideos(cloudProfile.hideMusicVideos)
                }

                // 2. Merge Taste Profile & Seeds
                val cloudArtists = snapshot.tasteSeeds
                    .filter { it.kind.equals("ARTIST", ignoreCase = true) }
                    .map { TasteSeed(it.seedId, it.name, it.subtitle, it.thumbnail) }
                val cloudSongs = snapshot.tasteSeeds
                    .filter { it.kind.equals("SONG", ignoreCase = true) }
                    .map { TasteSeed(it.seedId, it.name, it.subtitle, it.thumbnail) }

                val mergedGenres = (localTaste.selectedGenres + (cloudProfile?.selectedGenres ?: emptyList()))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(25)

                val mergedArtists = (localTaste.seedArtists + cloudArtists)
                    .distinctBy { it.name.lowercase().trim() }
                    .take(30)

                val mergedSongs = (localTaste.seedSongs + cloudSongs)
                    .distinctBy { it.name.lowercase().trim() + "::" + (it.subtitle?.lowercase()?.trim().orEmpty()) }
                    .take(30)

                val mergedCompleted = localTaste.onboardingCompleted || (cloudProfile?.onboardingCompleted == true)
                val mergedTaste = localTaste.copy(
                    onboardingCompleted = mergedCompleted,
                    selectedGenres = mergedGenres,
                    seedArtists = mergedArtists,
                    seedSongs = mergedSongs
                )
                if (mergedTaste != localTaste) {
                    prefsRepo.saveTasteProfile(mergedTaste)
                }

                val latestPrefs = prefsRepo.prefsFlow.first()
                cloudRepo.upsertUserProfile(
                    prefs = latestPrefs,
                    tasteProfile = mergedTaste,
                    email = user.email,
                    photoUrl = user.photoUrl?.toString(),
                    overrideDisplayName = resolvedDisplayName
                )
                if (mergedArtists.isNotEmpty() || mergedSongs.isNotEmpty()) {
                    cloudRepo.upsertTasteSeeds(mergedArtists, mergedSongs)
                }

                // 3. Merge Liked Songs (Union)
                val localLiked = dao.getLikedSongsOnce()
                val localLikedMap = localLiked.associateBy { it.videoId }
                val cloudLikedMap = snapshot.likedSongs.associateBy { it.videoId }

                for (cloudLike in snapshot.likedSongs) {
                    if (cloudLike.videoId !in localLikedMap && cloudLike.videoId.isNotBlank()) {
                        val song = cloudLike.toSongItem()
                        dao.insertLikedSong(
                            LikedSongEntity(
                                videoId = song.videoId,
                                songJson = repository.encodeSong(song),
                                likedAt = cloudLike.likedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
                for (localEntity in localLiked) {
                    if (localEntity.videoId !in cloudLikedMap) {
                        repository.decodeSong(localEntity.songJson)?.let { song ->
                            cloudRepo.syncLikedSong(song, liked = true, likedAtMs = localEntity.likedAt)
                        }
                    }
                }
                syncedLikedIds.clear()
                syncedLikedIds.addAll((localLikedMap.keys + cloudLikedMap.keys).filter { it.isNotBlank() })

                // 4. Merge Custom Playlists
                val localPlaylists = dao.getPlaylistsOnce()
                val localByKey = mutableMapOf<String, LocalPlaylistEntity>()
                val localByTitle = mutableMapOf<String, LocalPlaylistEntity>()
                localPlaylists.forEach { pl ->
                    val key = localPlaylistToCloudKey[pl.id] ?: "pl_local_${pl.id}"
                    localPlaylistToCloudKey[pl.id] = key
                    localByKey[key] = pl
                    localByTitle[pl.title.lowercase().trim()] = pl
                }

                val matchedLocalIds = mutableSetOf<Long>()
                val nextPlaylistSignatures = mutableMapOf<String, String>()

                for (cloudPl in snapshot.playlists) {
                    val matchedLocal = localByKey[cloudPl.playlistKey]
                        ?: localByTitle[cloudPl.title.lowercase().trim()]

                    if (matchedLocal != null) {
                        matchedLocalIds.add(matchedLocal.id)
                        localPlaylistToCloudKey[matchedLocal.id] = cloudPl.playlistKey
                        val localTracks = dao.getPlaylistTracks(matchedLocal.id)
                            .mapNotNull { repository.decodeSong(it.songJson) }
                        val cloudTracks = cloudPl.tracks.mapNotNull { CloudSyncRepository.songFromCloudMap(it) }

                        if (cloudPl.clientUpdatedAt > matchedLocal.updatedAt && cloudTracks.isNotEmpty()) {
                            dao.updatePlaylist(
                                id = matchedLocal.id,
                                title = cloudPl.title.ifBlank { matchedLocal.title },
                                description = cloudPl.description,
                                coverUri = cloudPl.coverUri ?: matchedLocal.coverUri,
                                updatedAt = cloudPl.clientUpdatedAt
                            )
                            dao.clearPlaylistTracks(matchedLocal.id)
                            cloudTracks.forEachIndexed { idx, s ->
                                dao.insertPlaylistTrack(
                                    LocalPlaylistTrackEntity(
                                        playlistId = matchedLocal.id,
                                        videoId = s.videoId,
                                        songJson = repository.encodeSong(s),
                                        position = idx
                                    )
                                )
                            }
                            nextPlaylistSignatures[cloudPl.playlistKey] = playlistSig(
                                cloudPl.title, cloudPl.description, cloudTracks.map { it.videoId }
                            )
                        } else {
                            val mergedTracks = (localTracks + cloudTracks).distinctBy { it.videoId }
                            if (mergedTracks.size > localTracks.size) {
                                mergedTracks.drop(localTracks.size).forEach { extra ->
                                    repository.addTrackToPlaylist(matchedLocal.id, extra, skipDuplicates = true)
                                }
                            }
                            cloudRepo.upsertPlaylist(
                                playlistKey = cloudPl.playlistKey,
                                title = matchedLocal.title,
                                description = matchedLocal.description,
                                coverUri = matchedLocal.coverUri,
                                tracks = mergedTracks,
                                clientUpdatedAt = maxOf(matchedLocal.updatedAt, cloudPl.clientUpdatedAt)
                            )
                            nextPlaylistSignatures[cloudPl.playlistKey] = playlistSig(
                                matchedLocal.title, matchedLocal.description, mergedTracks.map { it.videoId }
                            )
                        }
                    } else {
                        val cloudTracks = cloudPl.tracks.mapNotNull { CloudSyncRepository.songFromCloudMap(it) }
                        val now = System.currentTimeMillis()
                        val newLocalId = dao.insertPlaylist(
                            LocalPlaylistEntity(
                                title = cloudPl.title.ifBlank { "Playlist" },
                                description = cloudPl.description,
                                coverUri = cloudPl.coverUri,
                                createdAt = now,
                                updatedAt = cloudPl.clientUpdatedAt.takeIf { it > 0L } ?: now
                            )
                        )
                        localPlaylistToCloudKey[newLocalId] = cloudPl.playlistKey
                        cloudTracks.forEachIndexed { idx, s ->
                            dao.insertPlaylistTrack(
                                LocalPlaylistTrackEntity(
                                    playlistId = newLocalId,
                                    videoId = s.videoId,
                                    songJson = repository.encodeSong(s),
                                    position = idx
                                )
                            )
                        }
                        nextPlaylistSignatures[cloudPl.playlistKey] = playlistSig(
                            cloudPl.title, cloudPl.description, cloudTracks.map { it.videoId }
                        )
                    }
                }

                for (localPl in localPlaylists) {
                    if (localPl.id !in matchedLocalIds) {
                        val key = localPlaylistToCloudKey[localPl.id] ?: "pl_local_${localPl.id}"
                        localPlaylistToCloudKey[localPl.id] = key
                        val tracks = dao.getPlaylistTracks(localPl.id)
                            .mapNotNull { repository.decodeSong(it.songJson) }
                        cloudRepo.upsertPlaylist(
                            playlistKey = key,
                            title = localPl.title,
                            description = localPl.description,
                            coverUri = localPl.coverUri,
                            tracks = tracks,
                            clientUpdatedAt = localPl.updatedAt
                        )
                        nextPlaylistSignatures[key] = playlistSig(
                            localPl.title, localPl.description, tracks.map { it.videoId }
                        )
                    }
                }
                syncedPlaylistTimestamps.clear()
                syncedPlaylistTimestamps.putAll(nextPlaylistSignatures)

                // 5. Merge Hidden Artists (Union)
                val localBlocked = dao.getBlockedArtistsOnce()
                val localBlockedMap = localBlocked.associateBy { it.name.lowercase().trim() }
                val cloudBlockedMap = snapshot.hiddenArtists.associateBy { it.name.lowercase().trim() }

                for (cloudHidden in snapshot.hiddenArtists) {
                    val norm = cloudHidden.name.lowercase().trim()
                    if (norm.isNotEmpty() && norm !in localBlockedMap) {
                        dao.insertBlockedArtist(
                            BlockedArtistEntity(
                                name = cloudHidden.name,
                                channelId = cloudHidden.channelId,
                                blockedAt = cloudHidden.blockedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
                for (localHidden in localBlocked) {
                    val norm = localHidden.name.lowercase().trim()
                    if (norm.isNotEmpty() && norm !in cloudBlockedMap) {
                        cloudRepo.syncHiddenArtist(
                            name = localHidden.name,
                            channelId = localHidden.channelId,
                            blocked = true,
                            blockedAtMs = localHidden.blockedAt
                        )
                    }
                }
                syncedHiddenNames.clear()
                syncedHiddenNames.addAll((localBlockedMap.keys + cloudBlockedMap.keys).filter { it.isNotBlank() })

                val now = System.currentTimeMillis()
                _authState.update {
                    it.copy(
                        displayName = resolvedDisplayName,
                        isSyncing = false,
                        syncStatus = "Cloud sync up to date",
                        lastSyncedAtMs = now,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cloud sync failed", e)
                _authState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = "Cloud sync paused",
                        errorMessage = e.message ?: "Cloud sync failed."
                    )
                }
            } finally {
                suppressLocalPushCount.decrementAndGet()
            }
        }
    }

    @Suppress("OPT_IN_USAGE")
    private fun launchWatchersLocked(user: FirebaseUser) {
        val uid = user.uid

        // Watch Preferences & TasteProfile
        val prefsJob = scope.launch {
            var lastHash: Int? = null
            combine(prefsRepo.prefsFlow, prefsRepo.tasteProfileFlow) { prefs, taste -> prefs to taste }
                .debounce(800L)
                .collectLatest { (prefs, taste) ->
                    if (auth.currentUser?.uid != uid || suppressLocalPushCount.get() > 0) return@collectLatest
                    val sig = Triple(prefs, taste.selectedGenres, taste.seedArtists to taste.seedSongs).hashCode() +
                        taste.onboardingCompleted.hashCode()
                    if (lastHash == null) {
                        lastHash = sig
                        return@collectLatest
                    }
                    if (sig == lastHash) return@collectLatest
                    lastHash = sig
                    runCatching {
                        cloudRepo.upsertUserProfile(
                            prefs = prefs,
                            tasteProfile = taste,
                            email = user.email,
                            photoUrl = user.photoUrl?.toString()
                        )
                        cloudRepo.upsertTasteSeeds(taste.seedArtists, taste.seedSongs)
                        _authState.update {
                            it.copy(lastSyncedAtMs = System.currentTimeMillis(), syncStatus = "Cloud sync up to date")
                        }
                    }.onFailure { err ->
                        Log.w(TAG, "Profile auto-sync skipped: ${err.message}")
                    }
                }
        }
        watcherJobs.add(prefsJob)

        // Watch Liked Songs
        val likesJob = scope.launch {
            dao.observeLikedSongs().collectLatest { rows ->
                if (auth.currentUser?.uid != uid || suppressLocalPushCount.get() > 0) return@collectLatest
                syncMutex.withLock {
                    if (auth.currentUser?.uid != uid || suppressLocalPushCount.get() > 0) return@withLock
                    val currentMap = rows.associateBy { it.videoId }
                    val added = currentMap.keys - syncedLikedIds
                    val removed = syncedLikedIds - currentMap.keys
                    if (added.isEmpty() && removed.isEmpty()) return@withLock

                    for (id in added) {
                        val entity = currentMap[id] ?: continue
                        val song = repository.decodeSong(entity.songJson) ?: continue
                        runCatching {
                            cloudRepo.syncLikedSong(song, liked = true, likedAtMs = entity.likedAt)
                            syncedLikedIds.add(id)
                        }
                    }
                    for (id in removed) {
                        val dummy = SongItem(videoId = id, title = "", artists = "")
                        runCatching {
                            cloudRepo.syncLikedSong(dummy, liked = false)
                            syncedLikedIds.remove(id)
                        }
                    }
                    _authState.update {
                        it.copy(lastSyncedAtMs = System.currentTimeMillis(), syncStatus = "Cloud sync up to date")
                    }
                }
            }
        }
        watcherJobs.add(likesJob)

        // Watch Playlists + Track Counts
        val playlistsJob = scope.launch {
            combine(dao.observePlaylists(), dao.observePlaylistTrackCounts()) { pls, _ -> pls }
                .debounce(500L)
                .collectLatest { playlists ->
                    if (auth.currentUser?.uid != uid || suppressLocalPushCount.get() > 0) return@collectLatest
                    syncMutex.withLock {
                        if (auth.currentUser?.uid != uid || suppressLocalPushCount.get() > 0) return@withLock
                        val activeKeys = mutableSetOf<String>()
                        var changed = false

                        for (pl in playlists) {
                            val key = localPlaylistToCloudKey[pl.id] ?: "pl_local_${pl.id}".also {
                                localPlaylistToCloudKey[pl.id] = it
                            }
                            activeKeys.add(key)
                            val tracks = dao.getPlaylistTracks(pl.id)
                                .mapNotNull { repository.decodeSong(it.songJson) }
                            val sig = playlistSig(pl.title, pl.description, tracks.map { it.videoId })
                            if (syncedPlaylistTimestamps[key] != sig) {
                                runCatching {
                                    cloudRepo.upsertPlaylist(
                                        playlistKey = key,
                                        title = pl.title,
                                        description = pl.description,
                                        coverUri = pl.coverUri,
                                        tracks = tracks,
                                        clientUpdatedAt = pl.updatedAt
                                    )
                                    syncedPlaylistTimestamps[key] = sig
                                    changed = true
                                }
                            }
                        }

                        val deletedKeys = syncedPlaylistTimestamps.keys - activeKeys
                        for (delKey in deletedKeys) {
                            runCatching {
                                cloudRepo.deletePlaylist(delKey)
                                syncedPlaylistTimestamps.remove(delKey)
                                changed = true
                            }
                        }

                        if (changed) {
                            _authState.update {
                                it.copy(lastSyncedAtMs = System.currentTimeMillis(), syncStatus = "Cloud sync up to date")
                            }
                        }
                    }
                }
        }
        watcherJobs.add(playlistsJob)

        // Watch Hidden Artists
        val hiddenJob = scope.launch {
            dao.observeBlockedArtists().collectLatest { rows ->
                if (auth.currentUser?.uid != uid || suppressLocalPushCount.get() > 0) return@collectLatest
                syncMutex.withLock {
                    if (auth.currentUser?.uid != uid || suppressLocalPushCount.get() > 0) return@withLock
                    val byNorm = rows.associateBy { it.name.lowercase().trim() }
                    val added = byNorm.keys - syncedHiddenNames
                    val removed = syncedHiddenNames - byNorm.keys
                    if (added.isEmpty() && removed.isEmpty()) return@withLock

                    for (norm in added) {
                        val row = byNorm[norm] ?: continue
                        runCatching {
                            cloudRepo.syncHiddenArtist(row.name, row.channelId, blocked = true, blockedAtMs = row.blockedAt)
                            syncedHiddenNames.add(norm)
                        }
                    }
                    for (norm in removed) {
                        runCatching {
                            cloudRepo.syncHiddenArtist(norm, null, blocked = false)
                            syncedHiddenNames.remove(norm)
                        }
                    }
                    _authState.update {
                        it.copy(lastSyncedAtMs = System.currentTimeMillis(), syncStatus = "Cloud sync up to date")
                    }
                }
            }
        }
        watcherJobs.add(hiddenJob)
    }

    private fun playlistSig(title: String, description: String, trackIds: List<String>): String =
        "$title|${description.hashCode()}|${trackIds.joinToString(",")}"

    companion object {
        private const val TAG = "CloudSyncCoordinator"
    }
}

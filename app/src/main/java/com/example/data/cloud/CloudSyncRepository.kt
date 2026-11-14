package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.data.local.VelunaUserPrefs
import com.example.model.SongItem
import com.example.model.TasteProfile
import com.example.model.TasteSeed
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<CloudUserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(CloudUserProfile::class.java) }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeLikedSongs(userId: String): Flow<List<CloudLikedSong>> = flow {
        val colRef = db.collection("users").document(userId).collection("liked_songs")
        emitAll(
            colRef.snapshots()
                .map { snap -> snap.toObjects(CloudLikedSong::class.java) }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observePlaylists(userId: String): Flow<List<CloudPlaylist>> = flow {
        val colRef = db.collection("users").document(userId).collection("playlists")
        emitAll(
            colRef.snapshots()
                .map { snap -> snap.toObjects(CloudPlaylist::class.java) }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeTasteSeeds(userId: String): Flow<List<CloudTasteSeed>> = flow {
        val colRef = db.collection("users").document(userId).collection("taste_seeds")
        emitAll(
            colRef.snapshots()
                .map { snap -> snap.toObjects(CloudTasteSeed::class.java) }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeHiddenArtists(userId: String): Flow<List<CloudHiddenArtist>> = flow {
        val colRef = db.collection("users").document(userId).collection("hidden_artists")
        emitAll(
            colRef.snapshots()
                .map { snap -> snap.toObjects(CloudHiddenArtist::class.java) }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserProfile(): CloudUserProfile? {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.get().await().toObject(CloudUserProfile::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun fetchAccountSnapshot(): CloudAccountSnapshot {
        val uid = requireUserId()
        val userDoc = db.collection("users").document(uid)
        return try {
            val profile = userDoc.get().await().toObject(CloudUserProfile::class.java)
            val seeds = userDoc.collection("taste_seeds").get().await().toObjects(CloudTasteSeed::class.java)
            val liked = userDoc.collection("liked_songs").get().await().toObjects(CloudLikedSong::class.java)
            val playlists = userDoc.collection("playlists").get().await().toObjects(CloudPlaylist::class.java)
            val hidden = userDoc.collection("hidden_artists").get().await().toObjects(CloudHiddenArtist::class.java)
            CloudAccountSnapshot(
                profile = profile,
                tasteSeeds = seeds,
                likedSongs = liked,
                playlists = playlists,
                hiddenArtists = hidden
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userDoc.path)
            throw e
        }
    }

    suspend fun upsertUserProfile(
        prefs: VelunaUserPrefs,
        tasteProfile: TasteProfile,
        email: String?,
        photoUrl: String?,
        overrideDisplayName: String? = null
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safeName = (overrideDisplayName ?: prefs.userDisplayName)
            .trim()
            .ifBlank { "Listener" }
            .take(80)
        val safeEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        val safePhoto = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
        val safeGenres = tasteProfile.selectedGenres
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(25)
        val safeAccent = prefs.customAccentHex?.trim()?.takeIf { it.isNotEmpty() }?.take(16)

        val mutableFields = mapOf<String, Any?>(
            "displayName" to safeName,
            "email" to safeEmail,
            "photoUrl" to safePhoto,
            "themePreset" to prefs.themePreset.id.take(32),
            "themeMode" to prefs.themeMode.name,
            "customAccentHex" to safeAccent,
            "adaptToArtwork" to prefs.adaptToArtwork,
            "cornerRadiusDp" to prefs.cornerRadiusDp.coerceIn(4, 24).toLong(),
            "audioQuality" to prefs.audioQuality.name,
            "autoplay" to prefs.autoplay,
            "loudnessNormalization" to prefs.loudnessNormalization,
            "hideMusicVideos" to prefs.hideMusicVideos,
            "selectedGenres" to safeGenres,
            "onboardingCompleted" to tasteProfile.onboardingCompleted,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val exists = docRef.get().await().exists()
            if (exists) {
                docRef.update(mutableFields).await()
            } else {
                val createPayload = (mutableFields + mapOf(
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )).filterValues { it != null }
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun upsertTasteSeeds(artists: List<TasteSeed>, songs: List<TasteSeed>) {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("taste_seeds")
        val targetMap = linkedMapOf<String, Map<String, Any?>>()

        artists.take(30).forEach { seed ->
            val docId = stableDocId("art", seed.id.ifBlank { seed.name })
            targetMap[docId] = mapOf(
                "kind" to "ARTIST",
                "name" to seed.name.trim().ifBlank { "Artist" }.take(160),
                "subtitle" to seed.subtitle?.take(160),
                "thumbnail" to seed.thumbnail?.take(500)
            )
        }
        songs.take(30).forEach { seed ->
            val docId = stableDocId("sng", seed.id.ifBlank { seed.name })
            targetMap[docId] = mapOf(
                "kind" to "SONG",
                "name" to seed.name.trim().ifBlank { "Track" }.take(160),
                "subtitle" to seed.subtitle?.take(160),
                "thumbnail" to seed.thumbnail?.take(500)
            )
        }

        try {
            val existingDocs = colRef.get().await().documents
            val existingIds = existingDocs.map { it.id }.toSet()

            for (doc in existingDocs) {
                if (doc.id !in targetMap) {
                    doc.reference.delete().await()
                }
            }
            for ((docId, fields) in targetMap) {
                val ref = colRef.document(docId)
                if (docId in existingIds) {
                    ref.update(fields + ("updatedAt" to FieldValue.serverTimestamp())).await()
                } else {
                    val createPayload = (fields + mapOf(
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )).filterValues { it != null }
                    ref.set(createPayload).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colRef.path)
            throw e
        }
    }

    suspend fun syncLikedSong(song: SongItem, liked: Boolean, likedAtMs: Long = System.currentTimeMillis()) {
        val uid = requireUserId()
        val safeId = sanitizeDocId(song.videoId, prefix = "trk")
        val docRef = db.collection("users").document(uid).collection("liked_songs").document(safeId)
        try {
            if (!liked) {
                docRef.delete().await()
                return
            }
            val mutableFields = mapOf<String, Any?>(
                "title" to song.title.trim().ifBlank { "Track" }.take(200),
                "artists" to song.artists.trim().take(200),
                "album" to song.album?.take(200),
                "albumId" to song.albumId?.take(128),
                "artistId" to song.artistId?.take(128),
                "duration" to song.duration?.take(24),
                "durationSecs" to song.durationSeconds.coerceAtLeast(0L),
                "thumbnail" to song.thumbnail?.take(500),
                "explicit" to song.explicit,
                "likedAt" to likedAtMs.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (docRef.get().await().exists()) {
                docRef.update(mutableFields).await()
            } else {
                val createPayload = (mutableFields + ("createdAt" to FieldValue.serverTimestamp()))
                    .filterValues { it != null }
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (liked) OperationType.WRITE else OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun upsertPlaylist(
        playlistKey: String,
        title: String,
        description: String,
        coverUri: String?,
        tracks: List<SongItem>,
        clientUpdatedAt: Long
    ) {
        val uid = requireUserId()
        val safeKey = sanitizeDocId(playlistKey, prefix = "pl")
        val docRef = db.collection("users").document(uid).collection("playlists").document(safeKey)

        val serializedTracks = tracks.take(250).map { s ->
            mapOf<String, Any?>(
                "videoId" to s.videoId.take(128),
                "title" to s.title.take(200),
                "artists" to s.artists.take(200),
                "album" to s.album?.take(200),
                "albumId" to s.albumId?.take(128),
                "artistId" to s.artistId?.take(128),
                "duration" to s.duration?.take(24),
                "durationSecs" to s.durationSeconds.coerceAtLeast(0L),
                "thumbnail" to s.thumbnail?.take(500),
                "explicit" to s.explicit
            ).filterValues { it != null }
        }

        val mutableFields = mapOf<String, Any?>(
            "title" to title.trim().ifBlank { "Playlist" }.take(120),
            "description" to description.take(500),
            "coverUri" to coverUri?.takeIf { !it.startsWith("content://") }?.take(500),
            "tracks" to serializedTracks,
            "clientUpdatedAt" to clientUpdatedAt.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            if (docRef.get().await().exists()) {
                docRef.update(mutableFields).await()
            } else {
                val createPayload = (mutableFields + ("createdAt" to FieldValue.serverTimestamp()))
                    .filterValues { it != null }
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deletePlaylist(playlistKey: String) {
        val uid = requireUserId()
        val safeKey = sanitizeDocId(playlistKey, prefix = "pl")
        val docRef = db.collection("users").document(uid).collection("playlists").document(safeKey)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun syncHiddenArtist(name: String, channelId: String?, blocked: Boolean, blockedAtMs: Long = System.currentTimeMillis()) {
        val uid = requireUserId()
        val key = stableDocId("blk", name.lowercase().trim())
        val docRef = db.collection("users").document(uid).collection("hidden_artists").document(key)
        try {
            if (!blocked) {
                docRef.delete().await()
                return
            }
            val mutableFields = mapOf<String, Any?>(
                "name" to name.trim().ifBlank { "Artist" }.take(160),
                "channelId" to channelId?.take(128),
                "blockedAt" to blockedAtMs.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (docRef.get().await().exists()) {
                docRef.update(mutableFields).await()
            } else {
                val createPayload = (mutableFields + ("createdAt" to FieldValue.serverTimestamp()))
                    .filterValues { it != null }
                docRef.set(createPayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (blocked) OperationType.WRITE else OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        fun stableDocId(prefix: String, raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(24)
            return "${prefix}_$digest".take(128)
        }

        fun sanitizeDocId(raw: String, prefix: String): String {
            val clean = raw.trim()
            return if (clean.isNotEmpty() && clean.length <= 128 && clean.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
                clean
            } else {
                stableDocId(prefix, raw)
            }
        }

        fun songFromCloudMap(map: Map<String, Any?>): SongItem? {
            val videoId = (map["videoId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val title = (map["title"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            return SongItem(
                videoId = videoId,
                title = title,
                artists = (map["artists"] as? String).orEmpty(),
                album = map["album"] as? String,
                albumId = map["albumId"] as? String,
                artistId = map["artistId"] as? String,
                duration = map["duration"] as? String,
                thumbnail = map["thumbnail"] as? String,
                explicit = (map["explicit"] as? Boolean) ?: false
            )
        }
    }
}

package com.example.data.cloud

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.VelunaUserPrefs
import com.example.model.SongItem
import com.example.model.TasteProfile
import com.example.model.TasteSeed
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun authenticatedOwner_canSyncProfileSeedsLikesAndPlaylists() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore, auth)

        val tasteProfile = TasteProfile(
            onboardingCompleted = true,
            selectedGenres = listOf("Electronic", "Indie"),
            seedArtists = listOf(TasteSeed(id = "UC_daft", name = "Daft Punk")),
            seedSongs = listOf(TasteSeed(id = "trk_abc", name = "Instant Crush"))
        )
        val songId = "trk_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val plKey = "pl_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val sampleSong = SongItem(
            videoId = songId,
            title = "Instant Crush",
            artists = "Daft Punk",
            duration = "5:37"
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile(
                prefs = VelunaUserPrefs(userDisplayName = "Alice"),
                tasteProfile = tasteProfile,
                email = ALICE_EMAIL,
                photoUrl = "https://example.com/alice.png"
            )
            repo.upsertTasteSeeds(tasteProfile.seedArtists, tasteProfile.seedSongs)
            repo.syncLikedSong(sampleSong, liked = true)
            repo.upsertPlaylist(
                playlistKey = plKey,
                title = "Focus Mix",
                description = "Chill electronics",
                coverUri = null,
                tracks = listOf(sampleSong),
                clientUpdatedAt = System.currentTimeMillis()
            )
        }

        val snapshot = withTimeout(DEFAULT_TIMEOUT_MS) { repo.fetchAccountSnapshot() }
        assertThat(snapshot.profile?.userId).isEqualTo(uid)
        assertThat(snapshot.profile?.displayName).isEqualTo("Alice")
        assertThat(snapshot.likedSongs.map { it.videoId }).contains(songId)
        assertThat(snapshot.playlists.map { it.playlistKey }).contains(plKey)

        // Update profile succeeds without triggering createdAt immutability violation
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile(
                prefs = VelunaUserPrefs(userDisplayName = "Alice Updated"),
                tasteProfile = tasteProfile,
                email = ALICE_EMAIL,
                photoUrl = null
            )
        }
        val updated = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getUserProfile() }
        assertThat(updated?.displayName).isEqualTo("Alice Updated")
    }

    @Test
    fun unauthenticatedCaller_rejectedBySecurityRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = CloudSyncRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeLikedSongs(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated listener")
        } catch (e: Exception) {
            val fsError = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertThat(fsError.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }

    @Test
    fun crossUserAccess_rejectedBySecurityRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore, auth)
        val songId = "trk_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.syncLikedSong(
                SongItem(videoId = songId, title = "Secret Track", artists = "Artist"),
                liked = true
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeLikedSongs(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user read")
        } catch (e: Exception) {
            val fsError = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertThat(fsError.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_veluna@test.com"
        const val BOB_EMAIL = "bob_veluna@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

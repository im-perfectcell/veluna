package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AudioQuality
import com.example.model.EqualizerPresets
import com.example.model.LyricsProviderInfo
import com.example.model.ThemeMode
import com.example.model.VelunaThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.velunaPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "veluna_prefs")

@Serializable
data class VelunaUserPrefs(
    val themePreset: VelunaThemePreset = VelunaThemePreset.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val customAccentHex: String? = null,
    val adaptToArtwork: Boolean = true,
    val cornerRadiusDp: Int = 12,
    val classicPlayerLayout: Boolean = false,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val autoplay: Boolean = true,
    val crossfadeSecs: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val loudnessNormalization: Boolean = true,
    val hideMusicVideos: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPresetName: String = "Flat",
    val eqBandsDb: List<Float> = EqualizerPresets.presets.getValue("Flat"),
    val lyricsProviderOrder: String = "boidu,lyricsplus,lrclib,youtube,simpmusic",
    val romanizeLyrics: Boolean = false,
    val translateLyrics: Boolean = true,
    val scrobblingEnabled: Boolean = false,
    val lastFmUsername: String = "",
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
    val lastFmSessionKey: String = "",
    val listenBrainzToken: String = "",
    val showMoodChips: Boolean = true,
    val showHeroArtworkBackdrop: Boolean = true,
    val userDisplayName: String = "Listener"
)

class UserPrefsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CUSTOM_ACCENT = stringPreferencesKey("custom_accent")
        val ADAPT_TO_ARTWORK = booleanPreferencesKey("adapt_to_artwork")
        val CORNER_RADIUS = intPreferencesKey("corner_radius")
        val CLASSIC_PLAYER = booleanPreferencesKey("classic_player")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val AUTOPLAY = booleanPreferencesKey("autoplay")
        val CROSSFADE_SECS = intPreferencesKey("crossfade_secs")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LOUDNESS_NORM = booleanPreferencesKey("loudness_norm")
        val HIDE_VIDEOS = booleanPreferencesKey("hide_videos")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val LYRICS_PROVIDERS = stringPreferencesKey("lyrics_providers")
        val ROMANIZE_LYRICS = booleanPreferencesKey("romanize_lyrics")
        val TRANSLATE_LYRICS = booleanPreferencesKey("translate_lyrics")
        val SCROBBLING_ENABLED = booleanPreferencesKey("scrobbling_enabled")
        val LASTFM_USER = stringPreferencesKey("lastfm_user")
        val LASTFM_KEY = stringPreferencesKey("lastfm_key")
        val LASTFM_SECRET = stringPreferencesKey("lastfm_secret")
        val LASTFM_SESSION = stringPreferencesKey("lastfm_session")
        val LISTENBRAINZ_TOKEN = stringPreferencesKey("listenbrainz_token")
        val SHOW_MOOD_CHIPS = booleanPreferencesKey("show_mood_chips")
        val SHOW_HERO_BACKDROP = booleanPreferencesKey("show_hero_backdrop")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
    }

    val prefsFlow: Flow<VelunaUserPrefs> = context.velunaPrefsDataStore.data.map { p ->
        val preset = p[Keys.THEME_PRESET]?.let { id ->
            VelunaThemePreset.entries.find { it.id == id }
        } ?: VelunaThemePreset.DEFAULT
        val mode = p[Keys.THEME_MODE]?.let { name ->
            runCatching { ThemeMode.valueOf(name) }.getOrNull()
        } ?: ThemeMode.DARK
        val quality = p[Keys.AUDIO_QUALITY]?.let { name ->
            runCatching { AudioQuality.valueOf(name) }.getOrNull()
        } ?: AudioQuality.HIGH
        val bands = p[Keys.EQ_BANDS]?.let { raw ->
            runCatching { json.decodeFromString<List<Float>>(raw) }.getOrNull()
        }?.takeIf { it.size == 10 } ?: EqualizerPresets.presets.getValue("Flat")

        VelunaUserPrefs(
            themePreset = preset,
            themeMode = mode,
            customAccentHex = p[Keys.CUSTOM_ACCENT]?.takeIf { it.isNotBlank() },
            adaptToArtwork = p[Keys.ADAPT_TO_ARTWORK] ?: true,
            cornerRadiusDp = p[Keys.CORNER_RADIUS] ?: 12,
            classicPlayerLayout = p[Keys.CLASSIC_PLAYER] ?: false,
            audioQuality = quality,
            autoplay = p[Keys.AUTOPLAY] ?: true,
            crossfadeSecs = p[Keys.CROSSFADE_SECS] ?: 0,
            playbackSpeed = p[Keys.PLAYBACK_SPEED] ?: 1.0f,
            loudnessNormalization = p[Keys.LOUDNESS_NORM] ?: true,
            hideMusicVideos = p[Keys.HIDE_VIDEOS] ?: false,
            eqEnabled = p[Keys.EQ_ENABLED] ?: false,
            eqPresetName = p[Keys.EQ_PRESET] ?: "Flat",
            eqBandsDb = bands,
            lyricsProviderOrder = p[Keys.LYRICS_PROVIDERS] ?: "boidu,lyricsplus,lrclib,youtube,simpmusic",
            romanizeLyrics = p[Keys.ROMANIZE_LYRICS] ?: false,
            translateLyrics = p[Keys.TRANSLATE_LYRICS] ?: true,
            scrobblingEnabled = p[Keys.SCROBBLING_ENABLED] ?: false,
            lastFmUsername = p[Keys.LASTFM_USER].orEmpty(),
            lastFmApiKey = p[Keys.LASTFM_KEY].orEmpty(),
            lastFmApiSecret = p[Keys.LASTFM_SECRET].orEmpty(),
            lastFmSessionKey = p[Keys.LASTFM_SESSION].orEmpty(),
            listenBrainzToken = p[Keys.LISTENBRAINZ_TOKEN].orEmpty(),
            showMoodChips = p[Keys.SHOW_MOOD_CHIPS] ?: true,
            showHeroArtworkBackdrop = p[Keys.SHOW_HERO_BACKDROP] ?: true,
            userDisplayName = p[Keys.DISPLAY_NAME] ?: "Listener"
        )
    }

    suspend fun setThemePreset(preset: VelunaThemePreset) {
        context.velunaPrefsDataStore.edit { it[Keys.THEME_PRESET] = preset.id }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.velunaPrefsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCustomAccent(hex: String?) {
        context.velunaPrefsDataStore.edit {
            if (hex.isNullOrBlank()) it.remove(Keys.CUSTOM_ACCENT)
            else it[Keys.CUSTOM_ACCENT] = hex
        }
    }

    suspend fun setAdaptToArtwork(enabled: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.ADAPT_TO_ARTWORK] = enabled }
    }

    suspend fun setCornerRadius(dp: Int) {
        context.velunaPrefsDataStore.edit { it[Keys.CORNER_RADIUS] = dp.coerceIn(4, 24) }
    }

    suspend fun setClassicPlayerLayout(enabled: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.CLASSIC_PLAYER] = enabled }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.velunaPrefsDataStore.edit { it[Keys.AUDIO_QUALITY] = quality.name }
    }

    suspend fun setAutoplay(enabled: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.AUTOPLAY] = enabled }
    }

    suspend fun setCrossfadeSecs(secs: Int) {
        context.velunaPrefsDataStore.edit { it[Keys.CROSSFADE_SECS] = secs.coerceIn(0, 12) }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.velunaPrefsDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setLoudnessNormalization(enabled: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.LOUDNESS_NORM] = enabled }
    }

    suspend fun setHideMusicVideos(enabled: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.HIDE_VIDEOS] = enabled }
    }

    suspend fun setEqualizer(enabled: Boolean, presetName: String, bands: List<Float>) {
        context.velunaPrefsDataStore.edit {
            it[Keys.EQ_ENABLED] = enabled
            it[Keys.EQ_PRESET] = presetName
            it[Keys.EQ_BANDS] = json.encodeToString(bands)
        }
    }

    suspend fun setLyricsProvidersOrder(orderString: String) {
        context.velunaPrefsDataStore.edit { it[Keys.LYRICS_PROVIDERS] = orderString }
    }

    suspend fun setRomanizeLyrics(enabled: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.ROMANIZE_LYRICS] = enabled }
    }

    suspend fun setTranslateLyrics(enabled: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.TRANSLATE_LYRICS] = enabled }
    }

    suspend fun setScrobblingConfig(
        enabled: Boolean,
        lastFmUser: String,
        lastFmKey: String,
        lastFmSecret: String,
        lastFmSession: String,
        listenBrainzToken: String
    ) {
        context.velunaPrefsDataStore.edit {
            it[Keys.SCROBBLING_ENABLED] = enabled
            it[Keys.LASTFM_USER] = lastFmUser
            it[Keys.LASTFM_KEY] = lastFmKey
            it[Keys.LASTFM_SECRET] = lastFmSecret
            it[Keys.LASTFM_SESSION] = lastFmSession
            it[Keys.LISTENBRAINZ_TOKEN] = listenBrainzToken
        }
    }

    suspend fun setShowMoodChips(show: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.SHOW_MOOD_CHIPS] = show }
    }

    suspend fun setShowHeroArtworkBackdrop(show: Boolean) {
        context.velunaPrefsDataStore.edit { it[Keys.SHOW_HERO_BACKDROP] = show }
    }

    suspend fun setDisplayName(name: String) {
        context.velunaPrefsDataStore.edit { it[Keys.DISPLAY_NAME] = name.ifBlank { "Listener" } }
    }

    suspend fun applyBackup(backup: VelunaUserPrefs) {
        context.velunaPrefsDataStore.edit {
            it[Keys.THEME_PRESET] = backup.themePreset.id
            it[Keys.THEME_MODE] = backup.themeMode.name
            if (backup.customAccentHex.isNullOrBlank()) it.remove(Keys.CUSTOM_ACCENT)
            else it[Keys.CUSTOM_ACCENT] = backup.customAccentHex
            it[Keys.ADAPT_TO_ARTWORK] = backup.adaptToArtwork
            it[Keys.CORNER_RADIUS] = backup.cornerRadiusDp
            it[Keys.CLASSIC_PLAYER] = backup.classicPlayerLayout
            it[Keys.AUDIO_QUALITY] = backup.audioQuality.name
            it[Keys.AUTOPLAY] = backup.autoplay
            it[Keys.CROSSFADE_SECS] = backup.crossfadeSecs
            it[Keys.PLAYBACK_SPEED] = backup.playbackSpeed
            it[Keys.LOUDNESS_NORM] = backup.loudnessNormalization
            it[Keys.HIDE_VIDEOS] = backup.hideMusicVideos
            it[Keys.EQ_ENABLED] = backup.eqEnabled
            it[Keys.EQ_PRESET] = backup.eqPresetName
            it[Keys.EQ_BANDS] = json.encodeToString(backup.eqBandsDb)
            it[Keys.LYRICS_PROVIDERS] = backup.lyricsProviderOrder
            it[Keys.ROMANIZE_LYRICS] = backup.romanizeLyrics
            it[Keys.TRANSLATE_LYRICS] = backup.translateLyrics
            it[Keys.SCROBBLING_ENABLED] = backup.scrobblingEnabled
            it[Keys.LASTFM_USER] = backup.lastFmUsername
            it[Keys.LASTFM_KEY] = backup.lastFmApiKey
            it[Keys.LASTFM_SECRET] = backup.lastFmApiSecret
            it[Keys.LASTFM_SESSION] = backup.lastFmSessionKey
            it[Keys.LISTENBRAINZ_TOKEN] = backup.listenBrainzToken
            it[Keys.SHOW_MOOD_CHIPS] = backup.showMoodChips
            it[Keys.SHOW_HERO_BACKDROP] = backup.showHeroArtworkBackdrop
            it[Keys.DISPLAY_NAME] = backup.userDisplayName
        }
    }

    companion object {
        val ALL_LYRICS_PROVIDERS = listOf(
            "boidu" to "Boidu (Apple Word-Timed)",
            "lyricsplus" to "LyricsPlus (KPOE)",
            "lrclib" to "LRCLIB",
            "youtube" to "YouTube Music",
            "simpmusic" to "SimpMusic"
        )

        fun parseProviderOrder(raw: String): List<LyricsProviderInfo> {
            val seen = mutableSetOf<String>()
            val out = mutableListOf<LyricsProviderInfo>()
            val names = ALL_LYRICS_PROVIDERS.toMap()
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { tok ->
                val enabled = !tok.startsWith("-")
                val id = tok.removePrefix("-")
                val name = names[id] ?: return@forEach
                if (seen.add(id)) {
                    out.add(LyricsProviderInfo(id = id, name = name, enabled = enabled))
                }
            }
            ALL_LYRICS_PROVIDERS.forEach { (id, name) ->
                if (seen.add(id)) {
                    out.add(LyricsProviderInfo(id = id, name = name, enabled = true))
                }
            }
            return out
        }

        fun serializeProviderOrder(list: List<LyricsProviderInfo>): String =
            list.joinToString(",") { if (it.enabled) it.id else "-${it.id}" }
    }
}

package de.jworuna.webrtc_l4s.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_STORE = "settings"

val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_STORE)

object SettingsKeys {
    val NAME: Preferences.Key<String> = stringPreferencesKey("name")
    val CLIENT_ID: Preferences.Key<String> = stringPreferencesKey("client_id")
    val SIGNALING_URL: Preferences.Key<String> = stringPreferencesKey("signaling_url")
    val USE_SCREAM: Preferences.Key<Boolean> = booleanPreferencesKey("use_scream")
    val USE_SLICE: Preferences.Key<Boolean> = booleanPreferencesKey("use_slice")
    val USE_TRICKLE_ICE: Preferences.Key<Boolean> = booleanPreferencesKey("use_trickle_ice")
    val FRONT_FORMAT: Preferences.Key<String> = stringPreferencesKey("front_format")
    val BACK_FORMAT: Preferences.Key<String> = stringPreferencesKey("back_format")
    val LOG_API_URL: Preferences.Key<String> = stringPreferencesKey("log_api_url")
    val LOG_USERNAME: Preferences.Key<String> = stringPreferencesKey("log_username")
    val LOG_PASSWORD: Preferences.Key<String> = stringPreferencesKey("log_password")
    val ENABLE_API_LOGGING: Preferences.Key<Boolean> = booleanPreferencesKey("enable_api_logging")
    val ENABLE_LOGCAT_LOGGING: Preferences.Key<Boolean> = booleanPreferencesKey("enable_logcat_logging")
    val ENABLE_LOG_LOCATION: Preferences.Key<Boolean> = booleanPreferencesKey("enable_log_location")
    val LOG_INTERVAL_MS: Preferences.Key<Int> = intPreferencesKey("log_interval_ms")
    val LOG_BATCH_SIZE: Preferences.Key<Int> = intPreferencesKey("log_batch_size")
    val VIDEO_CODEC: Preferences.Key<String> = stringPreferencesKey("video_codec")
    val MUTE_ON_START: Preferences.Key<Boolean> = booleanPreferencesKey("mute_on_start")
    val MIN_BITRATE_KBPS: Preferences.Key<Int> = intPreferencesKey("min_bitrate_kbps")
    val MAX_BITRATE_KBPS: Preferences.Key<Int> = intPreferencesKey("max_bitrate_kbps")
    val STUN_TURN_API_URL: Preferences.Key<String> = stringPreferencesKey("stun_turn_api_url")
    val STUN_TURN_USERNAME: Preferences.Key<String> = stringPreferencesKey("stun_turn_username")
    val STUN_TURN_PASSWORD: Preferences.Key<String> = stringPreferencesKey("stun_turn_password")
}

data class SettingsState(
    val name: String = "",
    val clientId: String = "",
    val signalingUrl: String = "",
    val useScream: Boolean = false,
    val useSlice: Boolean = false,
    val useTrickleIce: Boolean = true,
    val frontFormat: String = "",
    val backFormat: String = "",
    val logApiUrl: String = "",
    val logUsername: String = "",
    val logPassword: String = "",
    val enableApiLogging: Boolean = false,
    val enableLogcatLogging: Boolean = false,
    val enableLogLocation: Boolean = true,
    val logIntervalMs: Int = 50,
    val logBatchSize: Int = 200,
    val videoCodec: String = "All Codecs",
    val muteOnStart: Boolean = false,
    val minBitrateKbps: Int = 300,
    val maxBitrateKbps: Int = 200000,
    val stunTurnServerUrl: String = "",
    val stunTurnUsername: String = "",
    val stunTurnPassword: String = ""
)

fun Context.readSettings(): Flow<SettingsState> =
    settingsDataStore.data.map { prefs ->
        SettingsState(
            name = prefs[SettingsKeys.NAME] ?: "",
            clientId = prefs[SettingsKeys.CLIENT_ID] ?: "",
            signalingUrl = prefs[SettingsKeys.SIGNALING_URL] ?: "",
            useScream = prefs[SettingsKeys.USE_SCREAM] ?: false,
            useSlice = prefs[SettingsKeys.USE_SLICE] ?: false,
            useTrickleIce = prefs[SettingsKeys.USE_TRICKLE_ICE] ?: true,
            frontFormat = prefs[SettingsKeys.FRONT_FORMAT] ?: "",
            backFormat = prefs[SettingsKeys.BACK_FORMAT] ?: "",
            logApiUrl = prefs[SettingsKeys.LOG_API_URL] ?: "",
            logUsername = prefs[SettingsKeys.LOG_USERNAME] ?: "",
            logPassword = prefs[SettingsKeys.LOG_PASSWORD] ?: "",
            enableApiLogging = prefs[SettingsKeys.ENABLE_API_LOGGING] ?: false,
            enableLogcatLogging = prefs[SettingsKeys.ENABLE_LOGCAT_LOGGING] ?: false,
            enableLogLocation = prefs[SettingsKeys.ENABLE_LOG_LOCATION] ?: false,
            logIntervalMs = prefs[SettingsKeys.LOG_INTERVAL_MS] ?: 50,
            logBatchSize = prefs[SettingsKeys.LOG_BATCH_SIZE] ?: 200,
            videoCodec = prefs[SettingsKeys.VIDEO_CODEC] ?: "All Codecs",
            muteOnStart = prefs[SettingsKeys.MUTE_ON_START] ?: false,
            minBitrateKbps = prefs[SettingsKeys.MIN_BITRATE_KBPS] ?: 300,
            maxBitrateKbps = prefs[SettingsKeys.MAX_BITRATE_KBPS] ?: 20000,
            stunTurnServerUrl = prefs[SettingsKeys.STUN_TURN_API_URL] ?: "stun:stun.l.google.com:19302",
            stunTurnUsername = prefs[SettingsKeys.STUN_TURN_USERNAME] ?: "",
            stunTurnPassword = prefs[SettingsKeys.STUN_TURN_PASSWORD] ?: ""
        )
    }

suspend fun Context.saveSettings(state: SettingsState) {
    settingsDataStore.edit { prefs ->
        prefs[SettingsKeys.NAME] = state.name
        prefs[SettingsKeys.CLIENT_ID] = state.clientId
        prefs[SettingsKeys.SIGNALING_URL] = state.signalingUrl
        prefs[SettingsKeys.USE_SCREAM] = state.useScream
        prefs[SettingsKeys.USE_SLICE] = state.useSlice
        prefs[SettingsKeys.USE_TRICKLE_ICE] = state.useTrickleIce
        prefs[SettingsKeys.FRONT_FORMAT] = state.frontFormat
        prefs[SettingsKeys.BACK_FORMAT] = state.backFormat
        prefs[SettingsKeys.LOG_API_URL] = state.logApiUrl
        prefs[SettingsKeys.LOG_USERNAME] = state.logUsername
        prefs[SettingsKeys.LOG_PASSWORD] = state.logPassword
        prefs[SettingsKeys.ENABLE_API_LOGGING] = state.enableApiLogging
        prefs[SettingsKeys.ENABLE_LOGCAT_LOGGING] = state.enableLogcatLogging
        prefs[SettingsKeys.ENABLE_LOG_LOCATION] = state.enableLogLocation
        prefs[SettingsKeys.LOG_INTERVAL_MS] = state.logIntervalMs
        prefs[SettingsKeys.LOG_BATCH_SIZE] = state.logBatchSize
        prefs[SettingsKeys.VIDEO_CODEC] = state.videoCodec
        prefs[SettingsKeys.MUTE_ON_START] = state.muteOnStart
        prefs[SettingsKeys.MIN_BITRATE_KBPS] = state.minBitrateKbps
        prefs[SettingsKeys.MAX_BITRATE_KBPS] = state.maxBitrateKbps
        prefs[SettingsKeys.STUN_TURN_API_URL] = state.stunTurnServerUrl
        prefs[SettingsKeys.STUN_TURN_USERNAME] = state.stunTurnUsername
        prefs[SettingsKeys.STUN_TURN_PASSWORD] = state.stunTurnPassword
    }
}

package com.kdresdell.iptvtv

import android.content.Context
import java.net.URLEncoder

// Optional local doorbell camera (Reolink-style RTSP). Entered by hand in
// Settings and stored only on this device - never in the repo or the APK,
// which is served from the apk-server. Blank host = feature off.
data class CameraConfig(
    // Off by default; the Settings switch. Off keeps the saved fields but
    // hides the Doorbell channel.
    val enabled: Boolean = false,
    val host: String = "",
    val username: String = "",
    val password: String = "",
    // Sub stream by default: lighter on the network and the TV's decoder,
    // plenty for a glance. Main stream is the HD fallback.
    val useHd: Boolean = false
) {
    val isActive: Boolean
        get() = enabled && host.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

class CameraPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("doorbell_camera", Context.MODE_PRIVATE)

    fun load(): CameraConfig = CameraConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        host = prefs.getString(KEY_HOST, "") ?: "",
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        password = prefs.getString(KEY_PASSWORD, "") ?: "",
        useHd = prefs.getBoolean(KEY_HD, false)
    )

    fun save(config: CameraConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_HOST, config.host)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putBoolean(KEY_HD, config.useHd)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOST = "host"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_HD = "use_hd"
    }
}

// The doorbell rides the normal channel plumbing as a synthetic LiveChannel
// with a reserved negative id (provider stream ids are always positive).
// It is injected into the favorites list at runtime - never stored in
// live_channels (wiped on every catalog sync) or persisted by FavoritesStore.
// Its blank categoryId is also what keeps EpgSync from asking the provider
// for a guide for it.
object DoorbellChannel {
    const val STREAM_ID = -1

    val channel = LiveChannel(streamId = STREAM_ID, name = "Doorbell", categoryId = "")

    fun isDoorbell(streamId: Int): Boolean = streamId == STREAM_ID

    // Keeps the doorbell out of the persisted list, and puts it last in the
    // live one when the camera is configured.
    fun withDoorbell(channels: List<LiveChannel>, config: CameraConfig): List<LiveChannel> {
        val real = channels.filterNot { isDoorbell(it.streamId) }
        return if (config.isActive) real + channel else real
    }

    fun rtspUrl(config: CameraConfig): String {
        val host = config.host.trim().let { if (it.contains(':')) it else "$it:554" }
        val path = if (config.useHd) "h264Preview_01_main" else "h264Preview_01_sub"
        return "rtsp://${encode(config.username)}:${encode(config.password)}@$host/$path"
    }

    // URLEncoder turns a space into "+", which is only valid in query strings.
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

package com.kdresdell.iptvtv

import android.content.Context

// Minimal local-only credential storage. Nothing here is ever sent
// anywhere except directly to the provider server the user enters.
data class ProviderCredentials(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
) {
    val isComplete: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

class ProviderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("provider_credentials", Context.MODE_PRIVATE)

    fun load(): ProviderCredentials = ProviderCredentials(
        serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: "",
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        password = prefs.getString(KEY_PASSWORD, "") ?: ""
    )

    fun save(credentials: ProviderCredentials) {
        prefs.edit()
            .putString(KEY_SERVER_URL, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}

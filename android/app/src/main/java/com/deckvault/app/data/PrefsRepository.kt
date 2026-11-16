package com.deckvault.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "deckvault_prefs")

class PrefsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val prefsKey = stringPreferencesKey("user_preferences_v1")

    private fun migrateLegacyEnums(raw: String): String = raw
        .replace("\"COSMIC_VIOLET\"", "\"MATTE_SLATE\"")
        .replace("\"AMOLED_CYAN\"", "\"MATTE_SAGE\"")
        .replace("\"SAMSUNG_CORAL\"", "\"MATTE_CLAY\"")
        .replace("\"EMERALD_VOLT\"", "\"PLAIN_BLACK\"")

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { store ->
        val raw = store[prefsKey]
        if (raw.isNullOrBlank()) {
            UserPreferences()
        } else {
            runCatching { json.decodeFromString<UserPreferences>(migrateLegacyEnums(raw)) }
                .getOrDefault(UserPreferences())
        }
    }

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        context.dataStore.edit { store ->
            val current = store[prefsKey]?.let {
                runCatching { json.decodeFromString<UserPreferences>(migrateLegacyEnums(it)) }.getOrNull()
            } ?: UserPreferences()
            val updated = transform(current)
            store[prefsKey] = json.encodeToString(UserPreferences.serializer(), updated)
        }
    }

    suspend fun upsertHost(host: SavedHost, makeActive: Boolean = true) {
        update { prefs ->
            val filtered = prefs.hosts.filterNot { it.hostId == host.hostId || (it.ip == host.ip && it.port == host.port) }
            prefs.copy(
                hosts = listOf(host) + filtered,
                activeHostId = if (makeActive) host.hostId else prefs.activeHostId ?: host.hostId,
            )
        }
    }

    suspend fun removeHost(hostId: String) {
        update { prefs ->
            val remaining = prefs.hosts.filterNot { it.hostId == hostId }
            val nextActive = if (prefs.activeHostId == hostId) remaining.firstOrNull()?.hostId else prefs.activeHostId
            prefs.copy(hosts = remaining, activeHostId = nextActive)
        }
    }
}

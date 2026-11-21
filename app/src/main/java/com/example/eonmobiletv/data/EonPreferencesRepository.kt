package com.example.eonmobiletv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.eonDataStore: DataStore<Preferences> by preferencesDataStore(name = "eon_mobile_tv_prefs")

class EonPreferencesRepository(private val context: Context) {

    private object Keys {
        val FAVORITE_IDS = stringSetPreferencesKey("favorite_ids")
        val CACHED_M3U = stringPreferencesKey("cached_m3u")
        val LAST_PLAYLIST_TITLE = stringPreferencesKey("last_playlist_title")
        val EXTERNAL_EPG_URL = stringPreferencesKey("external_epg_url")
        val LAST_EPG_REFRESH_EPOCH_SEC = longPreferencesKey("last_epg_refresh_epoch_sec")
    }

    val favoriteIdsFlow: Flow<Set<String>> = context.eonDataStore.data.map { prefs ->
        prefs[Keys.FAVORITE_IDS] ?: emptySet()
    }

    val cachedM3uFlow: Flow<String?> = context.eonDataStore.data.map { prefs ->
        prefs[Keys.CACHED_M3U]
    }

    val lastPlaylistTitleFlow: Flow<String> = context.eonDataStore.data.map { prefs ->
        prefs[Keys.LAST_PLAYLIST_TITLE] ?: "Вграден EON демо списък"
    }

    val externalEpgUrlFlow: Flow<String?> = context.eonDataStore.data.map { prefs ->
        prefs[Keys.EXTERNAL_EPG_URL]
    }

    val lastEpgRefreshEpochSecFlow: Flow<Long> = context.eonDataStore.data.map { prefs ->
        prefs[Keys.LAST_EPG_REFRESH_EPOCH_SEC] ?: 0L
    }

    suspend fun toggleFavorite(channelId: String) {
        context.eonDataStore.edit { prefs ->
            val updated = prefs[Keys.FAVORITE_IDS]?.toMutableSet() ?: mutableSetOf()
            if (!updated.add(channelId)) {
                updated.remove(channelId)
            }
            prefs[Keys.FAVORITE_IDS] = updated
        }
    }

    suspend fun savePlaylist(title: String, content: String) {
        context.eonDataStore.edit { prefs ->
            prefs[Keys.LAST_PLAYLIST_TITLE] = title
            prefs[Keys.CACHED_M3U] = content
        }
    }

    suspend fun setExternalEpgUrl(url: String?) {
        context.eonDataStore.edit { prefs ->
            if (url.isNullOrBlank()) {
                prefs.remove(Keys.EXTERNAL_EPG_URL)
            } else {
                prefs[Keys.EXTERNAL_EPG_URL] = url.trim()
            }
        }
    }

    suspend fun markEpgRefreshed(epochSecond: Long = Instant.now().epochSecond) {
        context.eonDataStore.edit { prefs ->
            prefs[Keys.LAST_EPG_REFRESH_EPOCH_SEC] = epochSecond
        }
    }
}

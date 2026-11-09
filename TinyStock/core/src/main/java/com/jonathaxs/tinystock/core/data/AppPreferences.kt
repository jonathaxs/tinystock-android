package com.jonathaxs.tinystock.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.service.SelectedStoreStorage
import com.jonathaxs.tinystock.core.service.StoreSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.appPreferencesStore by preferencesDataStore(name = "app_preferences")

/**
 * Preferencias do app gravadas em DataStore (equivale ao `UserDefaults` / `@AppStorage` do iOS).
 *
 * Usa os mesmos nomes de chave do iOS: `app.selectedStoreID`, `app.accentColor` e
 * `app.didCompleteWelcome`.
 */
class AppPreferences(
    private val dataStore: DataStore<Preferences>,
) : SelectedStoreStorage {

    override suspend fun load(): UUID? {
        val raw = dataStore.data.first()[SELECTED_STORE_PREF_KEY] ?: return null
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun save(id: UUID) {
        dataStore.edit { prefs ->
            prefs[SELECTED_STORE_PREF_KEY] = id.toString()
        }
    }

    val accentColor: Flow<AppAccentColor> = dataStore.data.map { prefs ->
        AppAccentColor.fromRawValue(prefs[ACCENT_COLOR_PREF_KEY])
    }

    suspend fun setAccentColor(color: AppAccentColor) {
        dataStore.edit { prefs ->
            prefs[ACCENT_COLOR_PREF_KEY] = color.rawValue
        }
    }

    val didCompleteWelcome: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DID_COMPLETE_WELCOME_PREF_KEY] ?: false
    }

    suspend fun setDidCompleteWelcome(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[DID_COMPLETE_WELCOME_PREF_KEY] = value
        }
    }

    companion object {
        const val SELECTED_STORE_KEY = StoreSession.SELECTED_STORE_KEY
        const val ACCENT_COLOR_KEY = AppAccentColor.STORAGE_KEY
        const val DID_COMPLETE_WELCOME_KEY = "app.didCompleteWelcome"

        private val SELECTED_STORE_PREF_KEY = stringPreferencesKey(SELECTED_STORE_KEY)
        private val ACCENT_COLOR_PREF_KEY = stringPreferencesKey(ACCENT_COLOR_KEY)
        private val DID_COMPLETE_WELCOME_PREF_KEY = booleanPreferencesKey(DID_COMPLETE_WELCOME_KEY)

        fun from(context: Context): AppPreferences =
            AppPreferences(context.applicationContext.appPreferencesStore)
    }
}

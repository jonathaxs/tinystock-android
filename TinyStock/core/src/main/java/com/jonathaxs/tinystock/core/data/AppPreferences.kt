package com.jonathaxs.tinystock.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.OrderReminderError
import com.jonathaxs.tinystock.core.model.OrderReminderSettings
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.core.service.SelectedStoreStorage
import com.jonathaxs.tinystock.core.service.StoreSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private val Context.appPreferencesStore by preferencesDataStore(name = "app_preferences")

/**
 * Preferencias do app gravadas em DataStore (equivale ao `UserDefaults` / `@AppStorage` do iOS).
 *
 * Usa os mesmos nomes de chave do iOS: `app.selectedStoreID`, `app.accentColor` e
 * `app.didCompleteWelcome`, `products.catalogMode`, `products.catalogViewMode`, `calendar.export.enabled` e
 * `notifications.orderReminders.v1`.
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

    /** Modo do catalogo na Inicio (`products` ou `variants`, valores do iOS). Null = nunca escolhido. */
    val catalogMode: Flow<String?> = dataStore.data.map { prefs -> prefs[CATALOG_MODE_PREF_KEY] }

    suspend fun setCatalogMode(rawValue: String) {
        dataStore.edit { prefs -> prefs[CATALOG_MODE_PREF_KEY] = rawValue }
    }

    /** Catalogo em lista ou grade (`list` ou `grid`, valores do iOS). Null = nunca escolhido. */
    val catalogViewMode: Flow<String?> = dataStore.data.map { prefs -> prefs[CATALOG_VIEW_MODE_PREF_KEY] }

    suspend fun setCatalogViewMode(rawValue: String) {
        dataStore.edit { prefs -> prefs[CATALOG_VIEW_MODE_PREF_KEY] = rawValue }
    }

    /** Calendario no modo dia ou lista (`day` ou `list`, valores do iOS). Null = nunca escolhido. */
    val ordersDisplayMode: Flow<String?> = dataStore.data.map { prefs -> prefs[ORDERS_DISPLAY_MODE_PREF_KEY] }

    suspend fun setOrdersDisplayMode(rawValue: String) {
        dataStore.edit { prefs -> prefs[ORDERS_DISPLAY_MODE_PREF_KEY] = rawValue }
    }

    /** Mostrar as opcoes de exportar para a agenda no detalhe do pedido (ligado por padrao, como no iOS). */
    val calendarExportEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[CALENDAR_EXPORT_PREF_KEY] ?: true }

    suspend fun setCalendarExportEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[CALENDAR_EXPORT_PREF_KEY] = value }
    }

    /**
     * Preferencias dos lembretes em JSON com os mesmos campos do iOS. Arquivo ilegivel ou horario invalido
     * vira erro na leitura, para a tela avisar em vez de esconder o problema.
     */
    suspend fun loadOrderReminderSettings(): OrderReminderSettings {
        val raw = dataStore.data.first()[ORDER_REMINDERS_PREF_KEY] ?: return OrderReminderSettings()
        val settings = decodeReminderSettings(raw)
        if (!settings.hasValidTime) throw DomainException(OrderReminderError.InvalidTime)
        return settings
    }

    suspend fun saveOrderReminderSettings(settings: OrderReminderSettings) {
        if (!settings.hasValidTime) throw DomainException(OrderReminderError.InvalidTime)
        dataStore.edit { prefs -> prefs[ORDER_REMINDERS_PREF_KEY] = encodeReminderSettings(settings) }
    }

    /** O Android so mostra o pedido de permissao poucas vezes; guardar se ja pediu separa "nao pedida". */
    suspend fun notificationPermissionRequested(): Boolean =
        dataStore.data.first()[NOTIFICATION_PERMISSION_PREF_KEY] ?: false

    suspend fun setNotificationPermissionRequested() {
        dataStore.edit { prefs -> prefs[NOTIFICATION_PERMISSION_PREF_KEY] = true }
    }

    companion object {
        const val SELECTED_STORE_KEY = StoreSession.SELECTED_STORE_KEY
        const val ACCENT_COLOR_KEY = AppAccentColor.STORAGE_KEY
        const val DID_COMPLETE_WELCOME_KEY = "app.didCompleteWelcome"
        const val CATALOG_MODE_KEY = "products.catalogMode"
        const val CATALOG_VIEW_MODE_KEY = "products.catalogViewMode"
        const val ORDERS_DISPLAY_MODE_KEY = "orders.displayMode"
        const val CALENDAR_EXPORT_KEY = "calendar.export.enabled"
        const val ORDER_REMINDERS_KEY = OrderReminderSettings.STORAGE_KEY
        const val NOTIFICATION_PERMISSION_KEY = "notifications.permissionRequested"

        private val SELECTED_STORE_PREF_KEY = stringPreferencesKey(SELECTED_STORE_KEY)
        private val ACCENT_COLOR_PREF_KEY = stringPreferencesKey(ACCENT_COLOR_KEY)
        private val DID_COMPLETE_WELCOME_PREF_KEY = booleanPreferencesKey(DID_COMPLETE_WELCOME_KEY)
        private val CATALOG_MODE_PREF_KEY = stringPreferencesKey(CATALOG_MODE_KEY)
        private val CATALOG_VIEW_MODE_PREF_KEY = stringPreferencesKey(CATALOG_VIEW_MODE_KEY)
        private val ORDERS_DISPLAY_MODE_PREF_KEY = stringPreferencesKey(ORDERS_DISPLAY_MODE_KEY)
        private val CALENDAR_EXPORT_PREF_KEY = booleanPreferencesKey(CALENDAR_EXPORT_KEY)
        private val ORDER_REMINDERS_PREF_KEY = stringPreferencesKey(ORDER_REMINDERS_KEY)
        private val NOTIFICATION_PERMISSION_PREF_KEY = booleanPreferencesKey(NOTIFICATION_PERMISSION_KEY)

        internal fun encodeReminderSettings(settings: OrderReminderSettings): String = JsonObject(
            mapOf(
                "isEnabled" to JsonPrimitive(settings.isEnabled),
                "productionEnabled" to JsonPrimitive(settings.productionEnabled),
                "shippingEnabled" to JsonPrimitive(settings.shippingEnabled),
                "dayBeforeEnabled" to JsonPrimitive(settings.dayBeforeEnabled),
                "dueDayEnabled" to JsonPrimitive(settings.dueDayEnabled),
                "overdueEnabled" to JsonPrimitive(settings.overdueEnabled),
                "soundEnabled" to JsonPrimitive(settings.soundEnabled),
                "hour" to JsonPrimitive(settings.hour),
                "minute" to JsonPrimitive(settings.minute),
            ),
        ).toString()

        /** Campo ausente fica com o padrao; tipo errado ou JSON quebrado lanca excecao. */
        internal fun decodeReminderSettings(raw: String): OrderReminderSettings {
            val json = Json.parseToJsonElement(raw).jsonObject
            val defaults = OrderReminderSettings()
            fun flag(name: String, default: Boolean) = json[name]?.jsonPrimitive?.boolean ?: default
            fun number(name: String, default: Int) = json[name]?.jsonPrimitive?.int ?: default
            return OrderReminderSettings(
                isEnabled = flag("isEnabled", defaults.isEnabled),
                productionEnabled = flag("productionEnabled", defaults.productionEnabled),
                shippingEnabled = flag("shippingEnabled", defaults.shippingEnabled),
                dayBeforeEnabled = flag("dayBeforeEnabled", defaults.dayBeforeEnabled),
                dueDayEnabled = flag("dueDayEnabled", defaults.dueDayEnabled),
                overdueEnabled = flag("overdueEnabled", defaults.overdueEnabled),
                soundEnabled = flag("soundEnabled", defaults.soundEnabled),
                hour = number("hour", defaults.hour),
                minute = number("minute", defaults.minute),
            )
        }

        fun from(context: Context): AppPreferences =
            AppPreferences(context.applicationContext.appPreferencesStore)
    }
}

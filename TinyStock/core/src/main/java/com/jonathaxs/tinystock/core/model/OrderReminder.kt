package com.jonathaxs.tinystock.core.model

import com.jonathaxs.tinystock.core.util.DomainError
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Preferencias locais dos lembretes de pedidos. Porte do `OrderReminderSettings`. */
data class OrderReminderSettings(
    val isEnabled: Boolean = false,
    val productionEnabled: Boolean = true,
    val shippingEnabled: Boolean = true,
    val dayBeforeEnabled: Boolean = true,
    val dueDayEnabled: Boolean = true,
    val overdueEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0,
) {
    val hasValidTime: Boolean
        get() = hour in 0..23 && minute in 0..59

    companion object {
        /** Mesma chave do `OrderReminderSettingsStore` do iOS. */
        const val STORAGE_KEY = "notifications.orderReminders.v1"
    }
}

enum class OrderReminderError : DomainError {
    InvalidTime,
}

/** Retrato puro de pedidos e lojas para o planejador de lembretes. */
data class OrderReminderSnapshot(
    val orders: List<SalesOrder>,
    val stores: List<StoreProfile>,
)

/** Falhas operacionais reportadas na coordenacao de lembretes. O texto fica no :app. */
enum class OrderReminderIssue {
    Preferences,
    Permission,
    DataRead,
    Scheduling,
}

/**
 * Lembrete local calculado para um pedido. Porte do `OrderReminder`.
 *
 * No :core nao ha texto localizado: [storeName] e [productName] carregam os dados que o :app usa para
 * montar o titulo e o corpo da notificacao, sem incluir nome ou dados do comprador.
 */
data class OrderReminder(
    val storeID: UUID,
    val orderID: UUID,
    val kind: Kind,
    val timing: Timing,
    val fireDate: Instant,
    val storeName: String = "",
    val productName: String = "",
    val soundEnabled: Boolean = true,
) {
    enum class Kind(val rawValue: String) {
        Production("production"),
        Shipping("shipping");

        companion object {
            fun fromRawValue(raw: String): Kind? = entries.firstOrNull { it.rawValue == raw }
        }
    }

    enum class Timing(val rawValue: String) {
        DayBefore("dayBefore"),
        DueDay("dueDay"),
        Overdue("overdue");

        companion object {
            fun fromRawValue(raw: String): Timing? = entries.firstOrNull { it.rawValue == raw }
        }
    }

    val id: String
        get() = "$IDENTIFIER_PREFIX$storeID.$orderID.${kind.rawValue}.${timing.rawValue}"

    /** Compara data, conteudo e som para evitar reagendamentos desnecessarios. */
    fun signature(): String {
        val raw = buildString {
            append(storeID)
            append('|')
            append(orderID)
            append('|')
            append(kind.rawValue)
            append('|')
            append(timing.rawValue)
            append('|')
            append(fireDate.epochSecond)
            append('.')
            append(fireDate.nano)
            append('|')
            append(soundEnabled)
            append('|')
            append(storeName.length)
            append(':')
            append(storeName)
            append('|')
            append(productName.length)
            append(':')
            append(productName)
        }
        return Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    /** Metadados gravados junto com a notificacao para validar a rota e a assinatura. */
    fun userInfo(): Map<String, String> = mapOf(
        KEY_STORE_ID to storeID.toString(),
        KEY_ORDER_ID to orderID.toString(),
        KEY_REMINDER_KIND to kind.rawValue,
        KEY_REMINDER_SIGNATURE to signature(),
    )

    companion object {
        // O prefixo delimita somente os lembretes de pedidos, inclusive entre versoes do servico.
        const val IDENTIFIER_PREFIX = "tinystock.order-reminder."

        const val KEY_STORE_ID = "storeID"
        const val KEY_ORDER_ID = "orderID"
        const val KEY_REMINDER_KIND = "reminderKind"
        const val KEY_REMINDER_SIGNATURE = "reminderSignature"
    }
}

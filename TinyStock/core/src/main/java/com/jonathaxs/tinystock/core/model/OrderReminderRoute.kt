package com.jonathaxs.tinystock.core.model

import com.jonathaxs.tinystock.core.data.TinyStockStore
import java.util.UUID

data class OrderReminderDestination(
    val store: StoreProfile,
    val order: SalesOrder,
)

/**
 * Destino validado de um lembrete antes de abrir loja e pedido. Porte do `OrderReminderRoute`.
 */
data class OrderReminderRoute(
    val storeID: UUID,
    val orderID: UUID,
    val id: UUID = UUID.randomUUID(),
) {
    /** Confere se a loja continua ativa e se o pedido ainda pertence a ela. */
    suspend fun resolve(store: TinyStockStore): OrderReminderDestination? {
        val foundStore = store.store(storeID)?.takeIf { !it.isArchived } ?: return null
        val foundOrder = store.order(orderID)?.takeIf { it.storeID == storeID } ?: return null
        return OrderReminderDestination(store = foundStore, order = foundOrder)
    }

    companion object {
        operator fun invoke(identifier: String, userInfo: Map<String, Any?>): OrderReminderRoute? =
            from(identifier, userInfo)

        fun from(identifier: String, userInfo: Map<String, Any?>): OrderReminderRoute? {
            if (!identifier.startsWith(OrderReminder.IDENTIFIER_PREFIX)) return null
            val parts = identifier.removePrefix(OrderReminder.IDENTIFIER_PREFIX).split(".")
            if (parts.size != 4) return null

            val storeID = parseUuid(parts[0]) ?: return null
            val orderID = parseUuid(parts[1]) ?: return null
            if (OrderReminder.Kind.fromRawValue(parts[2]) == null) return null
            if (OrderReminder.Timing.fromRawValue(parts[3]) == null) return null

            val payloadStoreID = userInfo[OrderReminder.KEY_STORE_ID] as? String ?: return null
            val payloadOrderID = userInfo[OrderReminder.KEY_ORDER_ID] as? String ?: return null
            val payloadKind = userInfo[OrderReminder.KEY_REMINDER_KIND] as? String ?: return null

            if (!payloadStoreID.equals(storeID.toString(), ignoreCase = true)) return null
            if (!payloadOrderID.equals(orderID.toString(), ignoreCase = true)) return null
            if (payloadKind != parts[2]) return null

            return OrderReminderRoute(storeID = storeID, orderID = orderID)
        }

        private fun parseUuid(raw: String): UUID? {
            if (raw.length != 36) return null
            return try {
                val uuid = UUID.fromString(raw)
                if (uuid.toString().equals(raw, ignoreCase = true)) uuid else null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

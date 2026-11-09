package com.jonathaxs.tinystock.core.model

import java.time.Instant
import java.util.UUID

object OrderCalendarExportSettings {
    const val IS_ENABLED_KEY = "calendar.export.enabled"
}

enum class OrderCalendarEventKind(val rawValue: String) {
    Production("production"),
    Shipping("shipping");

    companion object {
        fun fromRawValue(raw: String): OrderCalendarEventKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * Rascunho de evento de pedido pronto para exportacao ao Calendario. Porte do `OrderCalendarEventDraft`.
 *
 * O :core guarda os dados estruturados do pedido e da loja; o :app monta o titulo e as notas com as
 * strings localizadas antes de abrir o intent do Calendario.
 */
data class OrderCalendarEventDraft(
    val orderID: UUID,
    val kind: OrderCalendarEventKind,
    val startDate: Instant,
    val endDate: Instant,
    val storeName: String = "",
    /** Primeiro produto distinto do pedido, ou null quando o :app deve usar o nome padrao. */
    val primaryProductName: String? = null,
    /** Quantidade de produtos distintos alem do primeiro. */
    val additionalProductCount: Int = 0,
    val buyerName: String = "",
    val externalReference: String = "",
    val channel: SalesChannel? = null,
    val customChannelDisplayName: String? = null,
) {
    val id: String = "$orderID.${kind.rawValue}"
}

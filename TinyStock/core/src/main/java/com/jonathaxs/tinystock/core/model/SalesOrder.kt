package com.jonathaxs.tinystock.core.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Pedido com as linhas dentro ([items]). Porte do `SalesOrder` do iOS, onde as linhas sao uma relacao;
 * aqui o pedido e gravado e lido inteiro pelo armazenamento.
 *
 * Canal, atendimento e status ficam como texto cru: valor desconhecido continua gravado, mas os
 * getters devolvem null e nada e liberado por um valor padrao silencioso.
 */
data class SalesOrder(
    val id: UUID = UUID.randomUUID(),
    val storeID: UUID = StoreScope.UNASSIGNED_STORE_ID,
    val channelRawValue: String = SalesChannel.Direct.rawValue,
    val customChannelName: String = "",
    val fulfillmentRawValue: String = OrderFulfillment.ReadyStock.rawValue,
    /** Sem valor informado, comeca no status inicial do atendimento. */
    val statusRawValue: String = (OrderFulfillment.fromRawValue(fulfillmentRawValue)?.initialStatus
        ?: SalesOrderStatus.New).rawValue,
    val buyerName: String = "",
    val externalReference: String = "",
    val orderedAt: Instant = Instant.now(),
    val productionDueAt: Instant? = null,
    val shippingDueAt: Instant? = null,
    /** Prazo planejado e data efetiva ficam separados, para medir o que foi feito. */
    val productionStartedAt: Instant? = null,
    val producedAt: Instant? = null,
    val shippedAt: Instant? = null,
    val completedAt: Instant? = null,
    val cancelledAt: Instant? = null,
    val cancellationReason: String = "",
    val trackingCode: String = "",
    val note: String = "",
    /** Taxa do canal no momento da venda, sem consultar a taxa atual do marketplace. */
    val channelFeePercentage: BigDecimal = BigDecimal.ZERO,
    val channelFeeAmount: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val items: List<SalesOrderItem> = emptyList(),
) {
    val channel: SalesChannel? get() = SalesChannel.fromRawValue(channelRawValue)
    val fulfillment: OrderFulfillment? get() = OrderFulfillment.fromRawValue(fulfillmentRawValue)
    val status: SalesOrderStatus? get() = SalesOrderStatus.fromRawValue(statusRawValue)

    /**
     * Nome digitado para o canal "Outro". Null quando a tela deve mostrar o nome do canal (ou
     * "desconhecido", se [channel] for null). Substitui o `channelDisplayName` do iOS, que tinha texto.
     */
    val customChannelDisplayName: String?
        get() = customChannelName.trim().takeIf { channel == SalesChannel.Other && it.isNotEmpty() }

    /** Linhas na ordem escolhida; empate pelo id em texto, como no iOS. */
    val itemList: List<SalesOrderItem>
        get() = items.sortedWith(compareBy<SalesOrderItem> { it.position }.thenBy { it.id.toString() })

    /** Em BigDecimal porque a soma de varias linhas pode passar do limite de um Int. */
    val totalQuantity: BigDecimal get() = items.sumOf { it.quantity.toBigDecimal() }
    val total: BigDecimal get() = items.sumOf { it.subtotal }
    val totalCost: BigDecimal get() = items.sumOf { it.subtotalCost }
    val grossProfit: BigDecimal get() = total - totalCost
    val netProfit: BigDecimal get() = grossProfit - channelFeeAmount

    /** Consulta pura. Mudar status, datas e estoque e do servico de gerenciamento. */
    fun canTransition(next: SalesOrderStatus): Boolean {
        val status = status ?: return false
        val fulfillment = fulfillment ?: return false
        return status.canTransition(next, fulfillment)
    }
}

package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StockMovementKind
import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.core.util.addOrNull
import com.jonathaxs.tinystock.core.util.ensure
import com.jonathaxs.tinystock.core.util.negateOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Dados administrativos editaveis de um pedido. Porte do `SalesOrderDetails`. */
data class SalesOrderDetails(
    val channel: SalesChannel,
    val customChannelName: String = "",
    val buyerName: String = "",
    val externalReference: String = "",
    val orderedAt: Instant,
    val productionDueAt: Instant? = null,
    val shippingDueAt: Instant,
    val trackingCode: String = "",
    val note: String = "",
    val channelFeePercentage: BigDecimal = BigDecimal.ZERO,
)

enum class SalesOrderManagementError : DomainError {
    OrderUnavailable,
    EditingNotAllowed,
    CancellationNotAllowed,
    InvalidOrderData,
    StockHistoryMismatch,
    QuantityOverflow,
    TransitionNotAllowed,
    InvalidTransitionDate,
}

/**
 * Edita, avanca e cancela pedidos sem separar o estado deles dos efeitos no estoque.
 * Porte de `SalesOrderManagementService.swift` (que no iOS estende `SalesOrderService`).
 */
object SalesOrderManagementService {

    private val HUNDRED = BigDecimal(100)

    /** Edita somente os dados administrativos. Itens e atendimento continuam retratos imutaveis. */
    suspend fun update(
        id: UUID,
        details: SalesOrderDetails,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        store: TinyStockStore,
    ): SalesOrder = store.inTransaction {
        val order = requiredOrder(id, store)
        val status = order.status ?: throw DomainException(SalesOrderManagementError.InvalidOrderData)
        val fulfillment = order.fulfillment ?: throw DomainException(SalesOrderManagementError.InvalidOrderData)
        ensure(!status.isTerminal && status != SalesOrderStatus.Shipped) {
            SalesOrderManagementError.EditingNotAllowed
        }
        SalesOrderService.validateDates(
            fulfillment = fulfillment,
            orderedAt = details.orderedAt,
            productionDueAt = details.productionDueAt,
            shippingDueAt = details.shippingDueAt,
            now = now,
            zone = zone,
        )
        ensure(details.channelFeePercentage >= BigDecimal.ZERO && details.channelFeePercentage <= HUNDRED) {
            SalesOrderError.InvalidChannelFee
        }
        val fee = try {
            ChannelFeeCalculator.fee(order.total, details.channelFeePercentage)
        } catch (_: DomainException) {
            throw DomainException(SalesOrderError.InvalidMoney)
        }

        val updated = order.copy(
            channelRawValue = details.channel.rawValue,
            customChannelName = if (details.channel == SalesChannel.Other) {
                SalesOrderService.clean(details.customChannelName)
            } else {
                ""
            },
            buyerName = SalesOrderService.clean(details.buyerName),
            externalReference = SalesOrderService.clean(details.externalReference),
            orderedAt = details.orderedAt,
            productionDueAt = if (fulfillment == OrderFulfillment.Production) details.productionDueAt else null,
            shippingDueAt = details.shippingDueAt,
            trackingCode = SalesOrderService.clean(details.trackingCode),
            note = SalesOrderService.clean(details.note),
            channelFeePercentage = details.channelFeePercentage,
            channelFeeAmount = fee,
            updatedAt = now,
        )
        store.saveOrder(updated)
        updated
    }

    /** Cancela antes do despacho e devolve a baixa de pronta entrega na mesma transacao. */
    suspend fun cancel(
        id: UUID,
        reason: String = "",
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): SalesOrder = store.inTransaction {
        val order = requiredOrder(id, store)
        val status = order.status ?: throw DomainException(SalesOrderManagementError.InvalidOrderData)
        val fulfillment = order.fulfillment ?: throw DomainException(SalesOrderManagementError.InvalidOrderData)
        ensure(status.canTransition(SalesOrderStatus.Cancelled, fulfillment)) {
            SalesOrderManagementError.CancellationNotAllowed
        }
        val targets = if (fulfillment == OrderFulfillment.ReadyStock) {
            cancellationTargets(order, store)
        } else {
            emptyList()
        }

        val cleanReason = SalesOrderService.clean(reason)
        for (target in targets) {
            StockService.reverseOrderWithdrawal(
                movement = target.movement,
                orderID = order.id,
                variant = target.variant,
                product = target.product,
                note = cleanReason,
                date = date,
                store = store,
            )
        }
        val cancelled = order.copy(
            statusRawValue = SalesOrderStatus.Cancelled.rawValue,
            cancelledAt = date,
            cancellationReason = cleanReason,
            updatedAt = date,
        )
        store.saveOrder(cancelled)
        cancelled
    }

    /** Avanca o pedido por uma transicao permitida e registra a data efetiva correspondente. */
    suspend fun transition(
        id: UUID,
        nextStatus: SalesOrderStatus,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): SalesOrder = store.inTransaction {
        val order = requiredOrder(id, store)
        ensure(nextStatus != SalesOrderStatus.Cancelled && order.canTransition(nextStatus)) {
            SalesOrderManagementError.TransitionNotAllowed
        }
        ensure(!date.isBefore(order.orderedAt)) {
            SalesOrderManagementError.InvalidTransitionDate
        }
        val previousDate = effectivePreviousDate(nextStatus, order)
        if (previousDate != null) {
            ensure(!date.isBefore(previousDate)) {
                SalesOrderManagementError.InvalidTransitionDate
            }
        }

        val updated = when (nextStatus) {
            SalesOrderStatus.InProduction -> order.copy(
                statusRawValue = nextStatus.rawValue,
                productionStartedAt = date,
                updatedAt = date,
            )
            SalesOrderStatus.ReadyToShip -> order.copy(
                statusRawValue = nextStatus.rawValue,
                producedAt = if (order.fulfillment == OrderFulfillment.Production) date else order.producedAt,
                updatedAt = date,
            )
            SalesOrderStatus.Shipped -> order.copy(
                statusRawValue = nextStatus.rawValue,
                shippedAt = date,
                updatedAt = date,
            )
            SalesOrderStatus.Completed -> order.copy(
                statusRawValue = nextStatus.rawValue,
                completedAt = date,
                updatedAt = date,
            )
            SalesOrderStatus.New,
            SalesOrderStatus.AwaitingProduction,
            SalesOrderStatus.Cancelled,
            -> order.copy(
                statusRawValue = nextStatus.rawValue,
                updatedAt = date,
            )
        }
        store.saveOrder(updated)
        updated
    }

    private data class CancellationTarget(
        val product: Product,
        val variant: ProductVariant,
        val movement: StockMovement,
    )

    private suspend fun requiredOrder(id: UUID, store: TinyStockStore): SalesOrder =
        store.order(id) ?: throw DomainException(SalesOrderManagementError.OrderUnavailable)

    private fun effectivePreviousDate(status: SalesOrderStatus, order: SalesOrder): Instant? = when (status) {
        SalesOrderStatus.ReadyToShip -> order.productionStartedAt
        SalesOrderStatus.Shipped -> order.producedAt
        SalesOrderStatus.Completed -> order.shippedAt
        SalesOrderStatus.New,
        SalesOrderStatus.AwaitingProduction,
        SalesOrderStatus.InProduction,
        SalesOrderStatus.Cancelled,
        -> null
    }

    private suspend fun cancellationTargets(order: SalesOrder, store: TinyStockStore): List<CancellationTarget> {
        val items = order.itemList
        ensure(items.isNotEmpty()) { SalesOrderManagementError.InvalidOrderData }
        val storeID = order.storeID
        val products = store.products(storeID)
        val variants = store.variants(storeID)
        val movements = store.movements(storeID).filter {
            it.referenceID == order.id && it.kind == StockMovementKind.OrderWithdrawal
        }
        ensure(movements.size == items.size) { SalesOrderManagementError.StockHistoryMismatch }

        val targets = mutableListOf<CancellationTarget>()
        for (item in items) {
            val product = products.filter { it.id == item.productID }.singleOrNull()
            val variant = variants.filter { it.id == item.variantID }.singleOrNull()
            val movement = movements.filter {
                it.productID == item.productID && it.variantID == item.variantID
            }.singleOrNull()
            if (product == null || variant == null || !variant.belongsTo(product) ||
                movement == null || movement.reversedAt != null
            ) {
                throw DomainException(SalesOrderManagementError.StockHistoryMismatch)
            }
            val expectedDelta = item.quantity.negateOrNull()
                ?: throw DomainException(SalesOrderManagementError.StockHistoryMismatch)
            ensure(movement.quantityDelta == expectedDelta) { SalesOrderManagementError.StockHistoryMismatch }
            variant.quantity.addOrNull(item.quantity)
                ?: throw DomainException(SalesOrderManagementError.QuantityOverflow)
            targets.add(CancellationTarget(product = product, variant = variant, movement = movement))
        }
        return targets
    }
}

/** Atalho para manter a chamada `SalesOrderService.update(...)` igual ao iOS. */
suspend fun SalesOrderService.update(
    id: UUID,
    details: SalesOrderDetails,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    store: TinyStockStore,
): SalesOrder = SalesOrderManagementService.update(id, details, now, zone, store)

/** Atalho para manter a chamada `SalesOrderService.cancel(...)` igual ao iOS. */
suspend fun SalesOrderService.cancel(
    id: UUID,
    reason: String = "",
    date: Instant = Instant.now(),
    store: TinyStockStore,
): SalesOrder = SalesOrderManagementService.cancel(id, reason, date, store)

/** Atalho para manter a chamada `SalesOrderService.transition(...)` igual ao iOS. */
suspend fun SalesOrderService.transition(
    id: UUID,
    nextStatus: SalesOrderStatus,
    date: Instant = Instant.now(),
    store: TinyStockStore,
): SalesOrder = SalesOrderManagementService.transition(id, nextStatus, date, store)

package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.StoreScope
import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.core.util.addOrNull
import com.jonathaxs.tinystock.core.util.ensure
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** IDs permitem resolver os objetos no armazenamento no momento da gravacao. Porte do `SalesOrderLine`. */
data class SalesOrderLine(
    val productID: UUID,
    val variantID: UUID,
    val quantity: Int,
)

sealed interface SalesOrderError : DomainError {
    data object EmptyOrder : SalesOrderError
    data object StoreUnavailable : SalesOrderError
    data object ProductUnavailable : SalesOrderError
    data object VariantMismatch : SalesOrderError
    data object InvalidQuantity : SalesOrderError
    data object QuantityOverflow : SalesOrderError
    data class InsufficientStock(
        val productName: String,
        val variantName: String,
        val available: Int,
        val requested: Int,
    ) : SalesOrderError
    data object InvalidMoney : SalesOrderError
    data object InvalidChannelFee : SalesOrderError
    data object InvalidDates : SalesOrderError
    data object FutureOrderDate : SalesOrderError
    data object ShippingBeforeOrder : SalesOrderError
    data object ProductionDateRequired : SalesOrderError
    data object InvalidProductionDate : SalesOrderError
    data object DuplicateOrder : SalesOrderError
}

/** Valida o pedido inteiro antes de registrar itens e baixa de estoque. Porte do `SalesOrderService`. */
object SalesOrderService {

    private val HUNDRED = BigDecimal(100)

    /** Confirma o pedido e as baixas de estoque na mesma transacao. */
    suspend fun register(
        id: UUID = UUID.randomUUID(),
        storeID: UUID,
        lines: List<SalesOrderLine>,
        fulfillment: OrderFulfillment,
        channel: SalesChannel = SalesChannel.Direct,
        customChannelName: String = "",
        buyerName: String = "",
        externalReference: String = "",
        orderedAt: Instant = Instant.now(),
        productionDueAt: Instant? = null,
        shippingDueAt: Instant,
        note: String = "",
        channelFeePercentage: BigDecimal = BigDecimal.ZERO,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        store: TinyStockStore,
    ): SalesOrder = store.inTransaction {
        ensure(lines.isNotEmpty()) { SalesOrderError.EmptyOrder }
        val targetStore = store.store(storeID)
        ensure(storeID != StoreScope.UNASSIGNED_STORE_ID && targetStore != null && !targetStore.isArchived) {
            SalesOrderError.StoreUnavailable
        }
        // A tela deve reutilizar o id do rascunho em tentativas repetidas para impedir dupla baixa.
        ensure(store.order(id) == null) { SalesOrderError.DuplicateOrder }
        validateDates(
            fulfillment = fulfillment,
            orderedAt = orderedAt,
            productionDueAt = productionDueAt,
            shippingDueAt = shippingDueAt,
            now = now,
            zone = zone,
        )
        ensure(channelFeePercentage >= BigDecimal.ZERO && channelFeePercentage <= HUNDRED) {
            SalesOrderError.InvalidChannelFee
        }

        val resolved = resolve(lines, storeID, store)
        val snapshots = resolved.mapIndexed { index, line ->
            if (fulfillment == OrderFulfillment.ReadyStock && line.quantity > line.variant.quantity) {
                throw DomainException(
                    SalesOrderError.InsufficientStock(
                        productName = line.product.name,
                        variantName = line.variant.name,
                        available = line.variant.quantity,
                        requested = line.quantity,
                    ),
                )
            }
            try {
                SalesOrderItem.snapshot(
                    product = line.product,
                    variant = line.variant,
                    quantity = line.quantity,
                    position = index,
                )
            } catch (_: DomainException) {
                throw DomainException(SalesOrderError.InvalidMoney)
            }
        }
        val revenue = snapshots.sumOf { it.subtotal }
        val fee = try {
            ChannelFeeCalculator.fee(revenue, channelFeePercentage)
        } catch (_: DomainException) {
            throw DomainException(SalesOrderError.InvalidMoney)
        }

        val order = SalesOrder(
            id = id,
            storeID = storeID,
            channelRawValue = channel.rawValue,
            customChannelName = if (channel == SalesChannel.Other) clean(customChannelName) else "",
            fulfillmentRawValue = fulfillment.rawValue,
            statusRawValue = fulfillment.initialStatus.rawValue,
            buyerName = clean(buyerName),
            externalReference = clean(externalReference),
            orderedAt = orderedAt,
            productionDueAt = if (fulfillment == OrderFulfillment.Production) productionDueAt else null,
            shippingDueAt = shippingDueAt,
            note = clean(note),
            channelFeePercentage = channelFeePercentage,
            channelFeeAmount = fee,
            createdAt = now,
            updatedAt = now,
            items = snapshots,
        )
        if (fulfillment == OrderFulfillment.ReadyStock) {
            for (line in resolved) {
                StockService.registerOrderWithdrawal(
                    quantity = line.quantity,
                    variant = line.variant,
                    product = line.product,
                    orderID = order.id,
                    date = now,
                    store = store,
                )
            }
        }
        store.saveOrder(order)
        order
    }

    private data class ResolvedLine(
        val product: Product,
        val variant: ProductVariant,
        val quantity: Int,
    )

    private suspend fun resolve(
        lines: List<SalesOrderLine>,
        storeID: UUID,
        store: TinyStockStore,
    ): List<ResolvedLine> {
        val products = store.products(storeID)
        val variants = store.variants(storeID)
        val result = mutableListOf<ResolvedLine>()
        for (line in lines) {
            ensure(line.quantity > 0) { SalesOrderError.InvalidQuantity }
            val product = products.filter { it.id == line.productID }.singleOrNull()
                ?: throw DomainException(SalesOrderError.ProductUnavailable)
            val variant = variants.filter { it.id == line.variantID }.singleOrNull()
                ?.takeIf { it.belongsTo(product) }
                ?: throw DomainException(SalesOrderError.VariantMismatch)
            val existingIndex = result.indexOfFirst { it.variant.id == variant.id }
            if (existingIndex >= 0) {
                val sum = result[existingIndex].quantity.addOrNull(line.quantity)
                    ?: throw DomainException(SalesOrderError.QuantityOverflow)
                result[existingIndex] = result[existingIndex].copy(quantity = sum)
            } else {
                result.add(ResolvedLine(product = product, variant = variant, quantity = line.quantity))
            }
        }
        return result
    }

    internal fun validateDates(
        fulfillment: OrderFulfillment,
        orderedAt: Instant,
        productionDueAt: Instant?,
        shippingDueAt: Instant,
        now: Instant,
        zone: ZoneId,
    ) {
        val orderedDay = orderedAt.atZone(zone).toLocalDate()
        val nowDay = now.atZone(zone).toLocalDate()
        val shippingDay = shippingDueAt.atZone(zone).toLocalDate()
        // A interface seleciona dias. Um despacho hoje as 00h nao precede uma venda hoje a tarde.
        ensure(!orderedDay.isAfter(nowDay)) { SalesOrderError.FutureOrderDate }
        ensure(!shippingDay.isBefore(orderedDay)) { SalesOrderError.ShippingBeforeOrder }
        if (fulfillment == OrderFulfillment.Production) {
            val productionDue = productionDueAt ?: throw DomainException(SalesOrderError.ProductionDateRequired)
            val productionDay = productionDue.atZone(zone).toLocalDate()
            ensure(!productionDay.isBefore(orderedDay) && !productionDay.isAfter(shippingDay)) {
                SalesOrderError.InvalidProductionDate
            }
        }
    }

    internal fun clean(text: String): String = text.trim()
}

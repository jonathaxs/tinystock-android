package com.jonathaxs.tinystock.core.report

import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.service.SalesOrderSchedule
import java.time.Instant
import java.time.ZoneId

/**
 * Numeros da loja exibidos na caixa do topo da tela Inicio. Porte do `HomeSummary` do iOS.
 */
data class HomeSummary(
    val products: Int = 0,
    val units: Int = 0,
    val outOfStock: Int = 0,
    val production: Int = 0,
    val readyToShip: Int = 0,
    val shipped: Int = 0,
    val overdue: Int = 0,
) {
    val openOrders: Int
        get() = production + readyToShip + shipped

    constructor(
        products: List<Product>,
        variants: List<ProductVariant>,
        orders: List<SalesOrder>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) : this(
        summary = from(
            products = products,
            variants = variants,
            orders = orders,
            now = now,
            zone = zone,
        ),
    )

    private constructor(summary: HomeSummary) : this(
        products = summary.products,
        units = summary.units,
        outOfStock = summary.outOfStock,
        production = summary.production,
        readyToShip = summary.readyToShip,
        shipped = summary.shipped,
        overdue = summary.overdue,
    )

    companion object {
        /** Calcula o resumo puro da loja a partir de produtos, variacoes e pedidos. */
        fun from(
            products: List<Product>,
            variants: List<ProductVariant>,
            orders: List<SalesOrder>,
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): HomeSummary {
            var units = 0
            var outOfStock = 0
            for (product in products) {
                val quantity = variants.filter { it.belongsTo(product) }.sumOf { it.quantity }
                units += quantity
                if (quantity <= 0) outOfStock += 1
            }

            var production = 0
            var readyToShip = 0
            var shipped = 0
            var overdue = 0
            for (order in orders) {
                val status = order.status ?: continue
                when (status) {
                    SalesOrderStatus.New -> {
                        // Pedido recem-criado segue para producao ou envio conforme o tipo.
                        if (order.fulfillment == OrderFulfillment.Production) {
                            production += 1
                        } else {
                            readyToShip += 1
                        }
                    }
                    SalesOrderStatus.AwaitingProduction, SalesOrderStatus.InProduction -> production += 1
                    SalesOrderStatus.ReadyToShip -> readyToShip += 1
                    SalesOrderStatus.Shipped -> shipped += 1
                    SalesOrderStatus.Completed, SalesOrderStatus.Cancelled -> Unit
                }
                if (SalesOrderSchedule.isOverdue(order, now = now, zone = zone)) {
                    overdue += 1
                }
            }

            return HomeSummary(
                products = products.size,
                units = units,
                outOfStock = outOfStock,
                production = production,
                readyToShip = readyToShip,
                shipped = shipped,
                overdue = overdue,
            )
        }
    }
}

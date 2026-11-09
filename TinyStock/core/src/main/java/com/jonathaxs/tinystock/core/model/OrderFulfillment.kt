package com.jonathaxs.tinystock.core.model

/** Pronta entrega (sai do estoque) ou sob encomenda (passa pela producao). */
enum class OrderFulfillment(val rawValue: String) {
    ReadyStock("readyStock"),
    Production("production");

    val initialStatus: SalesOrderStatus
        get() = when (this) {
            ReadyStock -> SalesOrderStatus.ReadyToShip
            Production -> SalesOrderStatus.AwaitingProduction
        }

    companion object {
        fun fromRawValue(raw: String): OrderFulfillment? = entries.firstOrNull { it.rawValue == raw }
    }
}

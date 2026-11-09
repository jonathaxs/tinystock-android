package com.jonathaxs.tinystock.core.model

enum class SalesOrderStatus(val rawValue: String) {
    New("new"),
    AwaitingProduction("awaitingProduction"),
    InProduction("inProduction"),
    ReadyToShip("readyToShip"),
    Shipped("shipped"),
    Completed("completed"),
    Cancelled("cancelled");

    val isTerminal: Boolean get() = this == Completed || this == Cancelled

    fun allowedNextStatuses(fulfillment: OrderFulfillment): List<SalesOrderStatus> = when (this) {
        New -> listOf(fulfillment.initialStatus, Cancelled)
        // A acao rapida de marcar como produzido pode pular o inicio explicito da producao.
        AwaitingProduction ->
            if (fulfillment == OrderFulfillment.Production) listOf(InProduction, ReadyToShip, Cancelled) else emptyList()
        InProduction ->
            if (fulfillment == OrderFulfillment.Production) listOf(ReadyToShip, Cancelled) else emptyList()
        ReadyToShip -> listOf(Shipped, Cancelled)
        // Devolucao depois do envio nao e cancelamento simples com reposicao automatica.
        Shipped -> listOf(Completed)
        Completed, Cancelled -> emptyList()
    }

    fun canTransition(next: SalesOrderStatus, fulfillment: OrderFulfillment): Boolean =
        next in allowedNextStatuses(fulfillment)

    companion object {
        fun fromRawValue(raw: String): SalesOrderStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

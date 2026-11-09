package com.jonathaxs.tinystock.core.model

/** Onde o pedido foi vendido. O nome na tela vem das strings do :app. */
enum class SalesChannel(val rawValue: String) {
    Direct("direct"),
    Shopee("shopee"),
    MercadoLivre("mercadoLivre"),
    Other("other");

    companion object {
        fun fromRawValue(raw: String): SalesChannel? = entries.firstOrNull { it.rawValue == raw }
    }
}

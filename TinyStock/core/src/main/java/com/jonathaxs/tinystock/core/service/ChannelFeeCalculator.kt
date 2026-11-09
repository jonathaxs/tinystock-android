package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.ensure
import java.math.BigDecimal
import java.math.RoundingMode

enum class ChannelFeeError : DomainError {
    NegativeRevenue,
    InvalidPercentage,
}

/** Calcula a taxa cobrada pelo canal de venda com arredondamento monetario. Porte do `ChannelFeeCalculator`. */
object ChannelFeeCalculator {

    private val HUNDRED = BigDecimal(100)

    /** Calcula a taxa sobre a receita e arredonda para centavos. */
    fun fee(revenue: BigDecimal, percentage: BigDecimal): BigDecimal {
        ensure(revenue >= BigDecimal.ZERO) { ChannelFeeError.NegativeRevenue }
        ensure(percentage >= BigDecimal.ZERO && percentage <= HUNDRED) { ChannelFeeError.InvalidPercentage }
        return revenue.multiply(percentage).divide(HUNDRED, 2, RoundingMode.HALF_UP)
    }
}

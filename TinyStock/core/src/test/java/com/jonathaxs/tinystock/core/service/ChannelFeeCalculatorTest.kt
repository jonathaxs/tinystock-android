package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.support.assertDecimal
import com.jonathaxs.tinystock.core.support.expectError
import org.junit.Test
import java.math.BigDecimal

/** Porte de `ChannelFeeCalculatorTests.swift`. */
class ChannelFeeCalculatorTest {

    @Test
    fun taxaPercentualUsaAReceitaDaVenda() {
        val fee = ChannelFeeCalculator.fee(BigDecimal(100), BigDecimal(14))

        assertDecimal("14", fee)
    }

    @Test
    fun taxaEhArredondadaParaCentavos() {
        val fee = ChannelFeeCalculator.fee(BigDecimal("19.90"), BigDecimal(14))

        assertDecimal("2.79", fee)
    }

    @Test
    fun taxaZeroNaoDescontaNada() {
        assertDecimal("0", ChannelFeeCalculator.fee(BigDecimal(100), BigDecimal.ZERO))
    }

    @Test
    fun percentualForaDoIntervaloEhRecusado() {
        expectError(ChannelFeeError.InvalidPercentage) {
            ChannelFeeCalculator.fee(BigDecimal(100), BigDecimal(101))
        }
        expectError(ChannelFeeError.InvalidPercentage) {
            ChannelFeeCalculator.fee(BigDecimal(100), BigDecimal(-1))
        }
    }

    @Test
    fun receitaNegativaEhRecusada() {
        expectError(ChannelFeeError.NegativeRevenue) {
            ChannelFeeCalculator.fee(BigDecimal(-1), BigDecimal(10))
        }
    }
}

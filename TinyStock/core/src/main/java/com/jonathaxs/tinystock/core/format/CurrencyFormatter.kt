package com.jonathaxs.tinystock.core.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Exibicao e leitura de valores em dinheiro no padrao brasileiro (real). Porte do `CurrencyFormatter`.
 */
object CurrencyFormatter {

    /** Codigo ISO da moeda. O app trabalha so em real por enquanto. */
    const val CURRENCY_CODE = "BRL"

    /**
     * Devolve o valor formatado para apresentacao, por exemplo "R$ 45,00".
     * A locale entra como parametro para respeitar o aparelho e permitir teste deterministico.
     */
    fun string(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.currency = Currency.getInstance(CURRENCY_CODE)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.format(value)
    }

    /**
     * Valor para preencher campo de formulario, por exemplo "45,90", sem simbolo nem milhar.
     * Zero volta como texto vazio para o campo mostrar o placeholder em vez de "0,00".
     */
    fun editableText(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        if (value.compareTo(BigDecimal.ZERO) == 0) return ""
        val formatter = NumberFormat.getNumberInstance(locale)
        formatter.isGroupingUsed = false
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.format(value)
    }

    /**
     * Converte o texto digitado em [BigDecimal], aceitando virgula ou ponto como separador decimal.
     * Devolve null quando nao sobra nenhum digito.
     *
     * Criterio pela quantidade de digitos depois do ultimo separador:
     * - ate 2 digitos: separador decimal ("1.50" vira 1.50);
     * - 3 ou mais: separador de milhar ("1.500" vira 1500).
     */
    fun decimal(text: String): BigDecimal? {
        // O sinal negativo so conta quando vem antes de tudo.
        val isNegative = text.trimStart { it.isWhitespace() }.startsWith("-")

        // 1. Mantem apenas digitos e separadores. Simbolo de moeda, espaco e letra caem fora.
        val cleaned = text.filter { it.isDigit() || it == ',' || it == '.' }
        if (cleaned.none { it.isDigit() }) return null

        // 2. Quebra em parte inteira e centavos, aplicando a regra dos 2 digitos.
        var integerPart = cleaned
        var decimalPart = ""

        val separatorIndex = cleaned.indexOfLast { it == ',' || it == '.' }
        if (separatorIndex >= 0) {
            val afterSeparator = cleaned.substring(separatorIndex + 1)
            if (afterSeparator.length <= 2) {
                integerPart = cleaned.substring(0, separatorIndex)
                decimalPart = afterSeparator
            }
        }

        // 3. O separador que sobrou na parte inteira e milhar e pode sumir.
        integerPart = integerPart.filter { it.isDigit() }

        // 4. Monta o formato neutro para a leitura nao depender do locale.
        val sign = if (isNegative) "-" else ""
        val normalizedInteger = integerPart.ifEmpty { "0" }
        val normalized = if (decimalPart.isEmpty()) {
            "$sign$normalizedInteger"
        } else {
            "$sign$normalizedInteger.$decimalPart"
        }

        val value = normalized.toBigDecimalOrNull() ?: return null
        return if (value.signum() == 0) BigDecimal.ZERO else value
    }
}

/** Texto do valor no formato de moeda usado pelo aplicativo. */
val BigDecimal.currencyText: String
    get() = CurrencyFormatter.string(this)

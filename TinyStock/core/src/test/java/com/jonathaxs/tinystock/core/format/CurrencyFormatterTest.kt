package com.jonathaxs.tinystock.core.format

import com.jonathaxs.tinystock.core.support.assertDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/** Porte de `CurrencyFormatterTests.swift`. */
class CurrencyFormatterTest {

    private val localeBR: Locale = Locale.forLanguageTag("pt-BR")

    // O formatador do JVM separa simbolo e numero com espaco nao quebravel; trocamos por espaco comum.
    private fun normalizedSpaces(text: String): String =
        text.replace("\u00A0", " ").replace("\u202F", " ")

    private fun money(cents: Int): BigDecimal = BigDecimal.valueOf(cents.toLong(), 2)

    @Test
    fun formataValorInteiroComDuasCasas() {
        val texto = CurrencyFormatter.string(BigDecimal(45), localeBR)
        assertEquals("R$ 45,00", normalizedSpaces(texto))
    }

    @Test
    fun formataValorComCentavosESeparadorDeMilhar() {
        val texto = CurrencyFormatter.string(money(123456), localeBR)
        assertEquals("R$ 1.234,56", normalizedSpaces(texto))
    }

    @Test
    fun atalhoDoDecimalUsaOMesmoFormatador() {
        val valor = money(4500)
        assertEquals(CurrencyFormatter.string(valor), valor.currencyText)
    }

    @Test
    fun textoDeEdicaoNaoTemSimboloNemMilhar() {
        val texto = CurrencyFormatter.editableText(money(123456), localeBR)
        assertEquals("1234,56", texto)
    }

    @Test
    fun textoDeEdicaoCompletaAsCasasDecimais() {
        assertEquals("45,00", CurrencyFormatter.editableText(BigDecimal(45), localeBR))
    }

    @Test
    fun textoDeEdicaoDeZeroVemVazio() {
        assertEquals("", CurrencyFormatter.editableText(BigDecimal.ZERO, localeBR))
        assertEquals("", CurrencyFormatter.editableText(BigDecimal("0.00"), localeBR))
    }

    @Test
    fun textoDeEdicaoVoltaNoParser() {
        val original = money(4590)
        val texto = CurrencyFormatter.editableText(original, localeBR)
        assertDecimal("45.90", CurrencyFormatter.decimal(texto)!!)
    }

    @Test
    fun leVirgulaComoSeparadorDecimal() {
        assertDecimal("45.90", CurrencyFormatter.decimal("45,90")!!)
    }

    @Test
    fun lePontoComoSeparadorDecimal() {
        assertDecimal("45.90", CurrencyFormatter.decimal("45.90")!!)
    }

    @Test
    fun ignoraSimboloEEspacoDaMoeda() {
        assertDecimal("1234.56", CurrencyFormatter.decimal("R$ 1.234,56")!!)
    }

    @Test
    fun tratraPontoComoMilharQuandoSobramTresDigitos() {
        assertDecimal("1500", CurrencyFormatter.decimal("1.500")!!)
    }

    @Test
    fun aceitaTextoTerminadoEmSeparador() {
        assertDecimal("45", CurrencyFormatter.decimal("45,")!!)
    }

    @Test
    fun leValorSemSeparadorNenhum() {
        assertDecimal("90", CurrencyFormatter.decimal("90")!!)
    }

    @Test
    fun devolveNilQuandoNaoTemDigito() {
        assertNull(CurrencyFormatter.decimal(""))
        assertNull(CurrencyFormatter.decimal("R$ "))
    }

    @Test
    fun idaEVoltaPreservaOValor() {
        val original = money(8990)
        val texto = CurrencyFormatter.string(original, localeBR)
        assertDecimal("89.90", CurrencyFormatter.decimal(texto)!!)
    }
}

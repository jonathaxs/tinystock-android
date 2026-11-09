package com.jonathaxs.tinystock.core.support

import com.jonathaxs.tinystock.core.data.InMemoryTinyStockStore
import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.DomainException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.math.BigDecimal

/**
 * Roda o teste com um armazenamento em memoria novo. Equivale ao `TestDatabase.makeCleanContext()` do
 * iOS: cada teste comeca vazio.
 */
fun withStore(block: suspend (InMemoryTinyStockStore) -> Unit) = runBlocking { block(InMemoryTinyStockStore()) }

/** Equivale ao `#expect(throws: Erro.caso) { ... }` do iOS. Aceita chamadas suspend dentro do bloco. */
inline fun expectError(expected: DomainError, block: () -> Unit) {
    try {
        block()
    } catch (exception: DomainException) {
        assertEquals(expected, exception.error)
        return
    }
    fail("Esperava o erro $expected")
}

/** Compara dinheiro pelo valor, sem olhar a escala (10 e 10.00 sao iguais). */
fun assertDecimal(expected: String, actual: BigDecimal) {
    assertEquals("esperava $expected, veio ${actual.toPlainString()}", 0, BigDecimal(expected).compareTo(actual))
}

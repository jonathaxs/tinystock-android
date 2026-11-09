package com.jonathaxs.tinystock.core.util

/**
 * Marca os enums de erro do dominio (equivale aos `enum ...Error: Error` do iOS). O :core nao tem texto
 * para o usuario: a UI traduz cada caso com as strings do :app.
 */
interface DomainError

/** Excecao unica que carrega o erro do dominio. */
class DomainException(val error: DomainError) : Exception(error.toString())

/** Equivale ao `guard condicao else { throw erro }` do iOS. */
inline fun ensure(condition: Boolean, error: () -> DomainError) {
    if (!condition) throw DomainException(error())
}

/** Soma de Int que acusa estouro em vez de dar a volta (como o `addingReportingOverflow` do iOS). */
fun Int.addOrNull(other: Int): Int? = try {
    Math.addExact(this, other)
} catch (_: ArithmeticException) {
    null
}

fun Int.subtractOrNull(other: Int): Int? = try {
    Math.subtractExact(this, other)
} catch (_: ArithmeticException) {
    null
}

fun Int.negateOrNull(): Int? = try {
    Math.negateExact(this)
} catch (_: ArithmeticException) {
    null
}

package com.jonathaxs.tinystock.core.util

import java.text.Normalizer
import java.util.Locale

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
private val combiningMarks = Regex("\\p{Mn}+")

/**
 * Texto sem acento e em minusculas, para comparar nomes. Equivale ao `folding([.caseInsensitive,
 * .diacriticInsensitive], locale: pt_BR).lowercased()` do iOS.
 */
fun String.foldedForComparison(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase(ptBR)

/**
 * Busca que ignora maiusculas e acentos, como o `localizedStandardContains` do iOS. Consulta vazia
 * nao encontra nada aqui; quem chama decide o que fazer com busca vazia.
 */
fun String.containsFolded(query: String): Boolean {
    val folded = query.foldedForComparison()
    return folded.isNotEmpty() && foldedForComparison().contains(folded)
}

/** Ordem de nomes para listas: sem acento e sem diferenca de maiusculas, depois o texto original. */
val FoldedNameOrder: Comparator<String> =
    compareBy<String> { it.foldedForComparison() }.thenBy { it }

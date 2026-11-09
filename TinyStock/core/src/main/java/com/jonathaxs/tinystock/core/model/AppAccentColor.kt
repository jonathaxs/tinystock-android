package com.jonathaxs.tinystock.core.model

/**
 * Cores de destaque que o usuario pode escolher. Porte do `AppAccentColor` do iOS.
 *
 * Fica no :core sem depender de UI: guarda cada cor como ARGB cru (0xAARRGGBB), com a versao clara e a
 * escura das cores de sistema da Apple usadas no iOS. A camada de UI converte para o tipo de cor dela.
 * O [rawValue] e o mesmo do iOS, para o backup e as preferencias continuarem compativeis.
 */
enum class AppAccentColor(val rawValue: String, val lightArgb: Long, val darkArgb: Long) {
    Cyan("cyan", 0xFF00C0E8, 0xFF3CD3FE),
    Green("green", 0xFF34C759, 0xFF30D158),
    Blue("blue", 0xFF0088FF, 0xFF0091FF),
    Purple("purple", 0xFFCB30E0, 0xFFDB34F2),
    Red("red", 0xFFFF383C, 0xFFFF4245),
    Orange("orange", 0xFFFF8D28, 0xFFFF9230),
    Yellow("yellow", 0xFFFFCC00, 0xFFFFD600),
    Pink("pink", 0xFFFF2D55, 0xFFFF375F);

    /** Amarelo e ciano sao claros demais para texto branco por cima. */
    val prefersDarkContent: Boolean get() = this == Yellow || this == Cyan

    companion object {
        /** Mesma chave do `@AppStorage` do iOS. */
        const val STORAGE_KEY = "app.accentColor"

        val Default = Cyan

        /** Valor salvo para a cor; desconhecido ou vazio volta para a padrao. */
        fun fromRawValue(raw: String?): AppAccentColor = entries.firstOrNull { it.rawValue == raw } ?: Default
    }
}

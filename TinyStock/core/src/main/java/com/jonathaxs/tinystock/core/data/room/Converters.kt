package com.jonathaxs.tinystock.core.data.room

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Conversores de tipo do Room.
 *
 * - [UUID] vira texto canonico (`toString` / `UUID.fromString`).
 * - [Instant] vira epoch em milissegundos (`toEpochMilli` / `Instant.ofEpochMilli`); perde fracao
 *   abaixo de 1 ms.
 * - [BigDecimal] vira texto plano (`toPlainString` / `BigDecimal(String)`), preservando a escala
 *   exata (por exemplo, `"10.50"` volta `"10.50"`).
 */
class Converters {

    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)
}

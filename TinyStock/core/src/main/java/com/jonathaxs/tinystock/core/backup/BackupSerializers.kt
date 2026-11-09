package com.jonathaxs.tinystock.core.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID

// Conversores que reproduzem o JSONEncoder/JSONDecoder do iOS, para o arquivo servir nos dois apps.

/** UUID em maiusculas, como o `uuidString` do iOS. Le maiuscula ou minuscula, so no formato completo. */
internal object UuidSerializer : KSerializer<UUID> {
    private val pattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TinyStockUUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString().uppercase(Locale.ROOT))
    }

    override fun deserialize(decoder: Decoder): UUID {
        val text = decoder.decodeString()
        // O UUID.fromString do Java aceita formas curtas ("1-1-1-1-1"); o iOS nao.
        if (!pattern.matches(text)) throw SerializationException("UUID invalido")
        return UUID.fromString(text)
    }
}

/**
 * Data como segundos desde 1970 (numero com fracao), igual a estrategia do `BackupManager.encode` do iOS.
 * Na leitura aceita tambem texto ISO 8601 com fuso, com ou sem fracao, como o decoder do iOS.
 */
internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TinyStockInstant", PrimitiveKind.DOUBLE)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Instant) {
        val seconds = BigDecimal.valueOf(value.epochSecond)
            .add(BigDecimal.valueOf(value.nano.toLong(), 9))
            .stripTrailingZeros()
        encoder.json().encodeJsonElement(JsonUnquotedLiteral(seconds.toPlainString()))
    }

    override fun deserialize(decoder: Decoder): Instant {
        val primitive = decoder.jsonPrimitive()
        if (primitive.isString) {
            return OffsetDateTime.parse(primitive.content, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        }
        val seconds = BigDecimal(primitive.content)
        val whole = seconds.setScale(0, RoundingMode.FLOOR)
        val nanos = seconds.subtract(whole).movePointRight(9).setScale(0, RoundingMode.HALF_EVEN).toLong()
        return Instant.ofEpochSecond(whole.longValueExact(), nanos)
    }
}

/** Dinheiro como numero JSON sem aspas, como o `Decimal` do iOS. Le o texto exato, sem passar por Double. */
internal object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TinyStockDecimal", PrimitiveKind.DOUBLE)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.json().encodeJsonElement(JsonUnquotedLiteral(value.stripTrailingZeros().toPlainString()))
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val primitive = decoder.jsonPrimitive()
        // O iOS recusa dinheiro entre aspas.
        if (primitive.isString) throw SerializationException("Valor em texto")
        return BigDecimal(primitive.content)
    }
}

/** Foto em Base64 padrao, como o `Data` do iOS. */
internal object Base64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TinyStockData", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray = Base64.getDecoder().decode(decoder.decodeString())
}

private fun Encoder.json(): JsonEncoder =
    this as? JsonEncoder ?: throw SerializationException("O backup so e gravado em JSON")

private fun Decoder.jsonPrimitive(): JsonPrimitive {
    val element = (this as? JsonDecoder ?: throw SerializationException("O backup so e lido de JSON"))
        .decodeJsonElement()
    return element as? JsonPrimitive ?: throw SerializationException("Esperava um valor simples")
}

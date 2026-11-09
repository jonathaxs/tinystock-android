@file:UseSerializers(UuidSerializer::class, InstantSerializer::class, DecimalSerializer::class, Base64Serializer::class)

package com.jonathaxs.tinystock.core.backup

import com.jonathaxs.tinystock.core.util.DomainError
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * O banco inteiro num arquivo JSON versionado. Porte do `BackupPayload` do iOS, com os mesmos nomes de
 * campo: um backup feito no iPhone abre no Android e o contrario tambem.
 *
 * Os campos ficam em ordem alfabetica, como o `.sortedKeys` do iOS. Campo com valor padrao pode faltar
 * no arquivo (backups mais antigos), igual ao `decodeIfPresent ?? padrao` do iOS. Fotos vao dentro do
 * arquivo em Base64 (`imageData`); no aparelho viram arquivos do [com.jonathaxs.tinystock.core.data.ImageFileStore].
 */
@Serializable
data class BackupPayload(
    val exportedAt: Instant,
    val orders: List<SalesOrderSnapshot> = emptyList(),
    val products: List<ProductSnapshot>,
    /** Vendas do formato v1. Na v2 o campo existe e vem sempre vazio. */
    val sales: List<JsonElement>,
    val selectedStoreID: UUID? = null,
    val stockMovements: List<StockMovementSnapshot> = emptyList(),
    val stores: List<StoreSnapshot> = emptyList(),
    val variants: List<ProductVariantSnapshot> = emptyList(),
    val version: Int,
) {
    val summary: BackupSummary
        get() = BackupSummary(
            storeCount = stores.size,
            productCount = products.size,
            variantCount = variants.size,
            orderCount = orders.size,
        )

    @Serializable
    class StoreSnapshot(
        val archivedAt: Instant? = null,
        val createdAt: Instant,
        val id: UUID,
        val imageData: ByteArray? = null,
        val isArchived: Boolean,
        val name: String,
        /** Backups anteriores a ordenacao das lojas nao trazem o campo. */
        val sortOrder: Int = 0,
        val trashedAt: Instant? = null,
        val updatedAt: Instant,
        val wasArchivedBeforeTrash: Boolean = false,
    )

    /** O iOS nao guarda a ordem do catalogo no backup; a restauracao mantem a ordem que ja existia. */
    @Serializable
    class ProductSnapshot(
        val category: String,
        val costPrice: BigDecimal,
        val createdAt: Instant,
        val id: UUID,
        val imageData: ByteArray? = null,
        val minimumStock: Int,
        val name: String,
        val quantity: Int,
        val salePrice: BigDecimal,
        /** Opcional por causa do formato v1; a v2 exige. */
        val storeID: UUID? = null,
        val updatedAt: Instant,
    )

    @Serializable
    class ProductVariantSnapshot(
        val createdAt: Instant,
        val id: UUID,
        /** Backups anteriores as fotos de variacao nao trazem o campo. */
        val imageData: ByteArray? = null,
        val isDefault: Boolean = false,
        val name: String,
        val productID: UUID,
        val quantity: Int,
        val storeID: UUID,
        val updatedAt: Instant,
    )

    @Serializable
    class StockMovementSnapshot(
        val balanceAfter: Int,
        val createdAt: Instant,
        val id: UUID,
        /** Texto cru: tipo desconhecido (de uma versao futura) atravessa o backup sem mudar. */
        val kind: String,
        val note: String,
        val productID: UUID,
        val quantityDelta: Int,
        val referenceID: UUID? = null,
        val reversedAt: Instant? = null,
        val reversedMovementID: UUID? = null,
        val storeID: UUID,
        val variantID: UUID,
    )

    @Serializable
    class SalesOrderSnapshot(
        val buyerName: String,
        val cancellationReason: String,
        val cancelledAt: Instant? = null,
        val channel: String,
        val channelFeeAmount: BigDecimal,
        val channelFeePercentage: BigDecimal,
        val completedAt: Instant? = null,
        val createdAt: Instant,
        val customChannelName: String,
        val externalReference: String,
        val fulfillment: String,
        val id: UUID,
        val items: List<SalesOrderItemSnapshot>,
        val note: String,
        val orderedAt: Instant,
        val producedAt: Instant? = null,
        val productionDueAt: Instant? = null,
        val productionStartedAt: Instant? = null,
        val shippedAt: Instant? = null,
        val shippingDueAt: Instant? = null,
        val status: String,
        val storeID: UUID,
        val trackingCode: String,
        val updatedAt: Instant,
    )

    @Serializable
    class SalesOrderItemSnapshot(
        val id: UUID,
        val position: Int,
        val productID: UUID,
        val productName: String,
        val quantity: Int,
        val storeID: UUID,
        val unitCost: BigDecimal,
        val unitPrice: BigDecimal,
        val variantID: UUID,
        val variantName: String,
    )
}

/** Contagem mostrada antes de confirmar a restauracao. */
data class BackupSummary(
    val storeCount: Int,
    val productCount: Int,
    val variantCount: Int,
    val orderCount: Int,
)

data class BackupRestoreResult(
    /** Loja que o app deve abrir depois de restaurar. */
    val selectedStoreID: UUID,
    val summary: BackupSummary,
)

sealed interface BackupError : DomainError {
    /** Arquivo que nao e backup do TinyStock, esta corrompido ou tem dados incoerentes. */
    data object InvalidFile : BackupError

    /**
     * Versao que este app nao restaura: uma futura, ou a v1 (anterior as lojas), que so o iOS importa.
     * Para a v1, a tela pode orientar a abrir no iPhone e exportar de novo.
     */
    data class UnsupportedVersion(val version: Int) : BackupError
}

package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.core.util.FoldedNameOrder
import com.jonathaxs.tinystock.core.util.addOrNull
import com.jonathaxs.tinystock.core.util.ensure
import com.jonathaxs.tinystock.core.util.foldedForComparison
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ProductVariantError : DomainError {
    EmptyName,
    DuplicateName,
    NegativeQuantity,
    ProductMismatch,
}

/** Variacoes de um produto: criar, renomear e consultar. Porte do `ProductVariantService`. */
object ProductVariantService {

    /** Cria uma variacao ligada ao produto e a loja dele. Devolve a versao ja com o saldo inicial. */
    suspend fun create(
        product: Product,
        name: String,
        isDefault: Boolean = false,
        initialQuantity: Int = 0,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): ProductVariant = store.inTransaction {
        ensure(initialQuantity >= 0) { ProductVariantError.NegativeQuantity }
        val cleanName = if (isDefault) {
            ProductVariant.INTERNAL_DEFAULT_NAME
        } else {
            validatedName(name, product, excluding = null, store = store)
        }
        if (isDefault) {
            ensure(variants(product, store).isEmpty()) { ProductVariantError.ProductMismatch }
        }

        val variant = ProductVariant(
            storeID = product.storeID,
            productID = product.id,
            name = cleanName,
            isDefault = isDefault,
            quantity = 0,
            createdAt = date,
            updatedAt = date,
        )
        store.saveVariant(variant)

        if (initialQuantity > 0) {
            StockService.registerInitialStock(initialQuantity, variant, product, date, store)
        }
        store.variant(variant.id) ?: variant
    }

    /** Renomeia sem mexer no saldo. Devolve a versao gravada. */
    suspend fun rename(
        variant: ProductVariant,
        name: String,
        product: Product,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): ProductVariant = store.inTransaction {
        ensure(variant.belongsTo(product)) { ProductVariantError.ProductMismatch }
        val current = store.variant(variant.id)?.takeIf { it.belongsTo(product) }
            ?: throw DomainException(ProductVariantError.ProductMismatch)
        val renamed = current.copy(
            name = validatedName(name, product, excluding = current.id, store = store),
            updatedAt = date,
        )
        store.saveVariant(renamed)
        renamed
    }

    /** Variacoes do produto, conferindo produto e loja, em ordem de nome. */
    suspend fun variants(product: Product, store: TinyStockStore): List<ProductVariant> =
        store.variants(product.storeID)
            .filter { it.productID == product.id }
            .sortedWith(compareBy(FoldedNameOrder) { it.name })

    suspend fun totalQuantity(product: Product, store: TinyStockStore): Int =
        variants(product, store).fold(0) { total, variant ->
            total.addOrNull(variant.quantity) ?: throw DomainException(StockError.QuantityOverflow)
        }

    /** Soma para exibir, sem o limite de um Int quando ha muitas variacoes. */
    fun displayedQuantity(product: Product, variants: List<ProductVariant>): BigDecimal =
        variants.filter { it.belongsTo(product) }.sumOf { it.quantity.toBigDecimal() }

    /** Considera os nomes finais juntos, inclusive quando duas variacoes trocam de nome. */
    internal fun validateEdits(inputs: List<ProductVariantInput>, existing: List<ProductVariant>) {
        val existingIDs = existing.map { it.id }.toSet()
        val editedIDs = inputs.mapNotNull { it.existingID }
        val editedIDSet = editedIDs.toSet()
        ensure(editedIDSet.size == editedIDs.size && existingIDs.containsAll(editedIDSet)) {
            ProductVariantError.ProductMismatch
        }

        val names = existing
            .filter { it.id !in editedIDSet }
            .map { comparableName(it.name) }
            .toMutableSet()
        for (input in inputs) {
            val name = input.name.trim()
            ensure(name.isNotEmpty()) { ProductVariantError.EmptyName }
            ensure(names.add(comparableName(name))) { ProductVariantError.DuplicateName }
            if (input.existingID == null && input.initialQuantity < 0) {
                throw DomainException(ProductVariantError.NegativeQuantity)
            }
        }
    }

    internal suspend fun applyEdits(
        inputs: List<ProductVariantInput>,
        existing: List<ProductVariant>,
        product: Product,
        store: TinyStockStore,
    ) {
        store.inTransaction {
            // Primeiro renomeia todas as existentes. Seus saldos e historicos nao sao alterados.
            for (input in inputs) {
                val existingID = input.existingID ?: continue
                val variant = existing.firstOrNull { it.id == existingID } ?: continue
                val current = store.variant(variant.id)?.takeIf { it.belongsTo(product) }
                    ?: throw DomainException(ProductVariantError.ProductMismatch)
                val name = input.name.trim()
                if (current.name != name || current.isDefault) {
                    store.saveVariant(
                        current.copy(
                            name = name,
                            isDefault = false,
                            updatedAt = Instant.now(),
                        ),
                    )
                }
            }
            for (input in inputs) {
                if (input.existingID == null) {
                    create(product = product, name = input.name, initialQuantity = input.initialQuantity, store = store)
                }
            }
        }
    }

    /** Preserva a identidade, o saldo e o historico ao ocultar a unica variacao do produto. */
    internal suspend fun applyDefault(
        existing: List<ProductVariant>,
        product: Product,
        store: TinyStockStore,
    ) {
        store.inTransaction {
            ensure(existing.size <= 1) { ProductFormError.CannotDisableMultipleVariations }
            val variant = existing.firstOrNull()
            if (variant != null) {
                ensure(variant.belongsTo(product)) { ProductVariantError.ProductMismatch }
                val current = store.variant(variant.id)?.takeIf { it.belongsTo(product) }
                    ?: throw DomainException(ProductVariantError.ProductMismatch)
                if (!current.isDefault || current.name != ProductVariant.INTERNAL_DEFAULT_NAME) {
                    store.saveVariant(
                        current.copy(
                            name = ProductVariant.INTERNAL_DEFAULT_NAME,
                            isDefault = true,
                            updatedAt = Instant.now(),
                        ),
                    )
                }
            } else {
                create(product = product, name = "", isDefault = true, store = store)
            }
        }
    }

    private suspend fun validatedName(
        name: String,
        product: Product,
        excluding: UUID?,
        store: TinyStockStore,
    ): String {
        val cleanName = name.trim()
        ensure(cleanName.isNotEmpty()) { ProductVariantError.EmptyName }
        val candidate = comparableName(cleanName)
        val isDuplicate = variants(product, store).any { it.id != excluding && comparableName(it.name) == candidate }
        ensure(!isDuplicate) { ProductVariantError.DuplicateName }
        return cleanName
    }

    internal fun comparableName(name: String): String = name.foldedForComparison()
}

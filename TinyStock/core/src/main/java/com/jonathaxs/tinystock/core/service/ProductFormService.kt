package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.core.util.ensure
import java.math.BigDecimal
import java.util.UUID

enum class ProductFormError : DomainError {
    MissingVariation,
    CannotDisableMultipleVariations,
}

/** Valores de uma variacao no formulario, sem modificar o armazenamento durante a edicao. */
data class ProductVariantInput(
    val id: UUID = UUID.randomUUID(),
    val existingID: UUID? = null,
    val name: String = "",
    val initialQuantity: Int = 0,
)

/** Coordena o cadastro completo de produto e variacoes. Porte do `ProductFormService`. */
object ProductFormService {

    suspend fun apply(
        product: Product? = null,
        storeID: UUID,
        name: String,
        costPrice: BigDecimal,
        salePrice: BigDecimal,
        imageFileName: String?,
        variants: List<ProductVariantInput>,
        usesVariants: Boolean = true,
        store: TinyStockStore,
    ): Product = store.inTransaction {
        val currentProduct = if (product != null) {
            ensure(product.storeID == storeID) { ProductVariantError.ProductMismatch }
            val stored = store.product(product.id)
            if (stored != null) {
                ensure(stored.storeID == storeID) { ProductVariantError.ProductMismatch }
                stored
            } else {
                product
            }
        } else {
            null
        }
        ensure(currentProduct != null || !usesVariants || variants.isNotEmpty()) {
            ProductFormError.MissingVariation
        }

        // Valida toda a lista antes de alterar produto, nomes ou saldos.
        val existing = if (currentProduct != null) {
            ProductVariantService.variants(currentProduct, store)
        } else {
            emptyList()
        }
        if (usesVariants) {
            ProductVariantService.validateEdits(variants, existing = existing)
        } else if (existing.size > 1) {
            throw DomainException(ProductFormError.CannotDisableMultipleVariations)
        }

        val savedProduct = if (currentProduct != null) {
            ProductService.update(
                product = currentProduct,
                name = name,
                costPrice = costPrice,
                salePrice = salePrice,
                imageFileName = imageFileName,
                store = store,
            )
        } else {
            ProductService.create(
                storeID = storeID,
                name = name,
                costPrice = costPrice,
                salePrice = salePrice,
                imageFileName = imageFileName,
                store = store,
            )
        }

        if (usesVariants) {
            ProductVariantService.applyEdits(variants, existing = existing, product = savedProduct, store = store)
        } else {
            ProductVariantService.applyDefault(existing = existing, product = savedProduct, store = store)
        }
        savedProduct
    }
}

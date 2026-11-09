package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.InMemoryTinyStockStore
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

/** Porte de `ProductFormServiceTests.swift`. */
class ProductFormServiceTest {

    @Test
    fun cadastroSalvaVariacoesESaldosComHistorico() = withStore { store ->
        val storeID = UUID.randomUUID()
        val product = ProductFormService.apply(
            storeID = storeID,
            name = " Caneca ",
            costPrice = BigDecimal("10"),
            salePrice = BigDecimal("25"),
            imageFileName = "caneca.jpg",
            variants = listOf(
                ProductVariantInput(name = "Branca", initialQuantity = 2),
                ProductVariantInput(name = "Verde", initialQuantity = 0),
            ),
            store = store,
        )

        val savedProduct = store.product(product.id)!!
        val variants = ProductVariantService.variants(savedProduct, store)
        assertEquals("Caneca", savedProduct.name)
        assertEquals("caneca.jpg", savedProduct.imageFileName)
        assertEquals(2, variants.size)
        assertTrue(variants.all { it.storeID == storeID && it.productID == savedProduct.id })
        assertEquals(listOf(2, 0), variants.map { it.quantity })
        assertEquals(1, store.movements(storeID).size)
    }

    @Test
    fun cadastroSemVariacoesCriaVariacaoInterna() = withStore { store ->
        val storeID = UUID.randomUUID()
        val product = ProductFormService.apply(
            storeID = storeID,
            name = "Produto",
            costPrice = BigDecimal.ZERO,
            salePrice = BigDecimal.ZERO,
            imageFileName = null,
            variants = emptyList(),
            usesVariants = false,
            store = store,
        )
        val variant = ProductVariantService.variants(product, store).first()

        assertTrue(variant.isDefault)
        assertEquals(ProductVariant.INTERNAL_DEFAULT_NAME, variant.name)
        assertEquals(0, variant.quantity)
        assertEquals(0, store.movements(storeID).size)
        assertTrue(store.variant(variant.id)!!.isDefault)
    }

    @Test
    fun cadastroComVariacoesExigePeloMenosUmaVariacao() = withStore { store ->
        val storeID = UUID.randomUUID()

        expectError(ProductFormError.MissingVariation) {
            ProductFormService.apply(
                storeID = storeID,
                name = "Produto",
                costPrice = BigDecimal.ZERO,
                salePrice = BigDecimal.ZERO,
                imageFileName = null,
                variants = emptyList(),
                store = store,
            )
        }
        assertEquals(0, store.products(storeID).size)
        assertEquals(0, store.variants(storeID).size)
        assertEquals(0, store.movements(storeID).size)
    }

    @Test
    fun ativarEDesativarVariacaoPreservaIdentidadeSaldoEHistorico() = withStore { store ->
        val storeID = UUID.randomUUID()
        val product = ProductFormService.apply(
            storeID = storeID,
            name = "Produto",
            costPrice = BigDecimal.ZERO,
            salePrice = BigDecimal.ZERO,
            imageFileName = null,
            variants = emptyList(),
            usesVariants = false,
            store = store,
        )
        val variant = ProductVariantService.variants(product, store).first()
        StockService.registerEntry(3, variant, product, store = store)

        ProductFormService.apply(
            product = product,
            storeID = product.storeID,
            name = product.name,
            costPrice = BigDecimal.ZERO,
            salePrice = BigDecimal.ZERO,
            imageFileName = null,
            variants = listOf(ProductVariantInput(existingID = variant.id, name = "Preta", initialQuantity = 3)),
            usesVariants = true,
            store = store,
        )
        val activated = store.variant(variant.id)!!
        assertFalse(activated.isDefault)
        assertEquals("Preta", activated.name)
        assertEquals(3, activated.quantity)

        ProductFormService.apply(
            product = product,
            storeID = product.storeID,
            name = product.name,
            costPrice = BigDecimal.ZERO,
            salePrice = BigDecimal.ZERO,
            imageFileName = null,
            variants = emptyList(),
            usesVariants = false,
            store = store,
        )
        val stored = ProductVariantService.variants(product, store)
        val deactivated = store.variant(variant.id)!!
        assertEquals(listOf(variant.id), stored.map { it.id })
        assertTrue(deactivated.isDefault)
        assertEquals(ProductVariant.INTERNAL_DEFAULT_NAME, deactivated.name)
        assertEquals(3, deactivated.quantity)
        assertEquals(1, store.movements(storeID).size)
    }

    @Test
    fun variasVariacoesNaoPodemSerOcultadas() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        val first = ProductVariantService.create(product, "Preta", store = store)
        val second = ProductVariantService.create(product, "Branca", store = store)

        expectError(ProductFormError.CannotDisableMultipleVariations) {
            ProductFormService.apply(
                product = product,
                storeID = product.storeID,
                name = product.name,
                costPrice = BigDecimal.ZERO,
                salePrice = BigDecimal.ZERO,
                imageFileName = null,
                variants = emptyList(),
                usesVariants = false,
                store = store,
            )
        }
        val savedFirst = store.variant(first.id)!!
        val savedSecond = store.variant(second.id)!!
        assertTrue(!savedFirst.isDefault && !savedSecond.isDefault)
        assertEquals(setOf("Preta", "Branca"), setOf(savedFirst.name, savedSecond.name))
    }

    @Test
    fun variacoesInvalidasNaoCriamProdutoParcial() = withStore { store ->
        val cases = listOf(
            listOf(ProductVariantInput(name = "Azul"), ProductVariantInput(name = " azul ")) to
                ProductVariantError.DuplicateName,
            listOf(ProductVariantInput(name = "Válida"), ProductVariantInput(name = "  ")) to
                ProductVariantError.EmptyName,
            listOf(ProductVariantInput(name = "Azul", initialQuantity = -1)) to
                ProductVariantError.NegativeQuantity,
            listOf(ProductVariantInput(existingID = UUID.randomUUID(), name = "Azul")) to
                ProductVariantError.ProductMismatch,
        )
        for ((inputs, error) in cases) {
            val storeID = UUID.randomUUID()
            expectError(error) {
                ProductFormService.apply(
                    storeID = storeID,
                    name = "Produto",
                    costPrice = BigDecimal.ZERO,
                    salePrice = BigDecimal.ZERO,
                    imageFileName = null,
                    variants = inputs,
                    store = store,
                )
            }
            assertEquals(0, store.products(storeID).size)
            assertEquals(0, store.variants(storeID).size)
            assertEquals(0, store.movements(storeID).size)
        }
    }

    @Test
    fun edicaoPreservaSaldoMesmoComQuantidadeAntigaNoRascunho() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        val variant = ProductVariantService.create(product, "Azul", initialQuantity = 2, store = store)
        val input = ProductVariantInput(existingID = variant.id, name = "Azul claro", initialQuantity = 2)
        StockService.registerEntry(3, variant, product, store = store)

        ProductFormService.apply(
            product = product,
            storeID = product.storeID,
            name = "Renomeado",
            costPrice = BigDecimal("1"),
            salePrice = BigDecimal("3"),
            imageFileName = null,
            variants = listOf(input),
            store = store,
        )

        val savedProduct = store.product(product.id)!!
        val savedVariant = store.variant(variant.id)!!
        assertEquals("Renomeado", savedProduct.name)
        assertEquals("Azul claro", savedVariant.name)
        assertEquals(5, savedVariant.quantity)
        assertEquals(2, store.movements(product.storeID).size)
    }

    @Test
    fun nomesPodemSerTrocadosSemFalsaDuplicata() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        val first = ProductVariantService.create(product, "Azul", initialQuantity = 2, store = store)
        val second = ProductVariantService.create(product, "Verde", initialQuantity = 3, store = store)

        ProductFormService.apply(
            product = product,
            storeID = product.storeID,
            name = product.name,
            costPrice = BigDecimal.ZERO,
            salePrice = BigDecimal.ZERO,
            imageFileName = null,
            variants = listOf(
                ProductVariantInput(existingID = first.id, name = "Verde"),
                ProductVariantInput(existingID = second.id, name = "Azul"),
                ProductVariantInput(name = "Branca", initialQuantity = 1),
            ),
            store = store,
        )

        val savedFirst = store.variant(first.id)!!
        val savedSecond = store.variant(second.id)!!
        assertEquals("Verde", savedFirst.name)
        assertEquals(2, savedFirst.quantity)
        assertEquals("Azul", savedSecond.name)
        assertEquals(3, savedSecond.quantity)
        assertEquals(3, store.movements(product.storeID).size)
    }

    @Test
    fun variacoesOmitidasSaoPreservadasEParticipamDaValidacao() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        val variant = ProductVariantService.create(product, "Única", initialQuantity = 2, store = store)

        expectError(ProductVariantError.DuplicateName) {
            ProductFormService.apply(
                product = product,
                storeID = product.storeID,
                name = "Alterado",
                costPrice = BigDecimal.ZERO,
                salePrice = BigDecimal.ZERO,
                imageFileName = null,
                variants = listOf(ProductVariantInput(name = "unica")),
                store = store,
            )
        }
        assertEquals("Produto", store.product(product.id)!!.name)

        ProductFormService.apply(
            product = product,
            storeID = product.storeID,
            name = product.name,
            costPrice = BigDecimal.ZERO,
            salePrice = BigDecimal.ZERO,
            imageFileName = null,
            variants = emptyList(),
            store = store,
        )
        assertEquals(2, store.variant(variant.id)!!.quantity)
        assertEquals(1, ProductVariantService.variants(product, store).size)
    }

    @Test
    fun produtoInvalidoNaoAlteraVariacoes() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        val variant = ProductVariantService.create(product, "Azul", store = store)

        expectError(ProductError.NegativeCostPrice) {
            ProductFormService.apply(
                product = product,
                storeID = product.storeID,
                name = "Alterado",
                costPrice = BigDecimal("-1"),
                salePrice = BigDecimal("1"),
                imageFileName = null,
                variants = listOf(ProductVariantInput(existingID = variant.id, name = "Verde")),
                store = store,
            )
        }
        assertEquals("Produto", store.product(product.id)!!.name)
        assertEquals("Azul", store.variant(variant.id)!!.name)
    }

    @Test
    fun naoPermiteMoverProdutoOuVariacaoEntreLojas() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        val other = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        val variant = ProductVariantService.create(other, "Azul", store = store)

        expectError(ProductVariantError.ProductMismatch) {
            ProductFormService.apply(
                product = product,
                storeID = other.storeID,
                name = "Novo",
                costPrice = BigDecimal.ZERO,
                salePrice = BigDecimal.ZERO,
                imageFileName = null,
                variants = emptyList(),
                store = store,
            )
        }
        expectError(ProductVariantError.ProductMismatch) {
            ProductFormService.apply(
                product = product,
                storeID = product.storeID,
                name = "Novo",
                costPrice = BigDecimal.ZERO,
                salePrice = BigDecimal.ZERO,
                imageFileName = null,
                variants = listOf(ProductVariantInput(existingID = variant.id, name = "Verde")),
                store = store,
            )
        }
        assertEquals("Produto", store.product(product.id)!!.name)
        assertEquals("Azul", store.variant(variant.id)!!.name)
    }

    @Test
    fun rollbackDesfazTodoOCadastroSemApagarDadosAnteriores() = withStore { store ->
        val storeID = UUID.randomUUID()
        ProductService.create(storeID = storeID, name = "Anterior", store = store)
        val failingStore = FailingOnMovementStore(store)

        try {
            ProductFormService.apply(
                storeID = storeID,
                name = "Rascunho",
                costPrice = BigDecimal.ZERO,
                salePrice = BigDecimal.ZERO,
                imageFileName = null,
                variants = listOf(ProductVariantInput(name = "Azul", initialQuantity = 3)),
                store = failingStore,
            )
            fail("Esperava falha ao gravar movimentacao")
        } catch (_: IllegalStateException) {
            // Esperado.
        }

        assertEquals(listOf("Anterior"), store.products(storeID).map { it.name })
        assertEquals(0, store.variants(storeID).size)
        assertEquals(0, store.movements(storeID).size)
    }

    private class FailingOnMovementStore(
        private val inner: InMemoryTinyStockStore,
    ) : TinyStockStore by inner {
        override suspend fun saveMovement(movement: StockMovement) {
            throw IllegalStateException("Falha simulada na gravacao")
        }
    }
}

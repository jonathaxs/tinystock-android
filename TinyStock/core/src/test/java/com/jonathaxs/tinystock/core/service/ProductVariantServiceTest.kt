package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Porte de `ProductVariantTests.swift`. */
class ProductVariantServiceTest {

    @Test
    fun criacaoHerdaProdutoELoja() = withStore { store ->
        val product = Product(storeID = UUID.randomUUID(), name = "Produto principal")
        store.saveProduct(product)
        val date = Instant.ofEpochSecond(1_800_000_000)

        val variant = ProductVariantService.create(
            product = product,
            name = "  Preta  ",
            initialQuantity = 2,
            date = date,
            store = store,
        )

        val saved = store.variant(variant.id)!!
        assertEquals(product.storeID, saved.storeID)
        assertEquals(product.id, saved.productID)
        assertEquals("Preta", saved.name)
        assertEquals(2, saved.quantity)
        assertEquals(date, saved.createdAt)
        assertEquals(date, saved.updatedAt)
        assertTrue(saved.belongsTo(product))
    }

    @Test
    fun consultaIsolaProdutoELoja() = withStore { store ->
        val first = Product(storeID = UUID.randomUUID(), name = "Caneca")
        val second = Product(storeID = UUID.randomUUID(), name = "Caneca")
        store.saveProduct(first)
        store.saveProduct(second)

        ProductVariantService.create(product = first, name = "Branca", initialQuantity = 2, store = store)
        ProductVariantService.create(product = first, name = "Verde", initialQuantity = 1, store = store)
        ProductVariantService.create(product = second, name = "Branca", initialQuantity = 9, store = store)

        val firstVariants = ProductVariantService.variants(first, store)

        assertEquals(listOf("Branca", "Verde"), firstVariants.map { it.name })
        assertTrue(firstVariants.all { it.storeID == first.storeID })
        assertTrue(firstVariants.all { it.productID == first.id })
    }

    @Test
    fun nomeDuplicadoNoMesmoProdutoEhRecusado() = withStore { store ->
        val product = Product(storeID = UUID.randomUUID(), name = "Produto principal")
        store.saveProduct(product)

        ProductVariantService.create(product = product, name = "Vermelha", store = store)

        expectError(ProductVariantError.DuplicateName) {
            ProductVariantService.create(product = product, name = "vermelha", store = store)
        }
    }

    @Test
    fun produtosDiferentesPodemRepetirNomeDaVariacao() = withStore { store ->
        val storeID = UUID.randomUUID()
        val first = Product(storeID = storeID, name = "Caneca")
        val second = Product(storeID = storeID, name = "Camiseta")
        store.saveProduct(first)
        store.saveProduct(second)

        ProductVariantService.create(product = first, name = "Branca", store = store)
        ProductVariantService.create(product = second, name = "Branca", store = store)

        assertEquals(2, store.variants(storeID).size)
    }

    @Test
    fun nomeVazioEQuantidadeNegativaSaoRecusados() = withStore { store ->
        val product = Product(storeID = UUID.randomUUID(), name = "Produto principal")
        store.saveProduct(product)

        expectError(ProductVariantError.EmptyName) {
            ProductVariantService.create(product = product, name = "   ", store = store)
        }

        expectError(ProductVariantError.NegativeQuantity) {
            ProductVariantService.create(
                product = product,
                name = "Edição limitada",
                initialQuantity = -1,
                store = store,
            )
        }
    }

    @Test
    fun renomearPreservaSaldoECriacao() = withStore { store ->
        val product = Product(storeID = UUID.randomUUID(), name = "Caneca")
        store.saveProduct(product)
        val createdAt = Instant.ofEpochSecond(1_700_000_000)
        val updatedAt = Instant.ofEpochSecond(1_800_000_000)
        val variant = ProductVariantService.create(
            product = product,
            name = "Branca",
            initialQuantity = 3,
            date = createdAt,
            store = store,
        )

        ProductVariantService.rename(
            variant = variant,
            name = "Verde",
            product = product,
            date = updatedAt,
            store = store,
        )

        val saved = store.variant(variant.id)!!
        assertEquals("Verde", saved.name)
        assertEquals(3, saved.quantity)
        assertEquals(createdAt, saved.createdAt)
        assertEquals(updatedAt, saved.updatedAt)
    }

    @Test
    fun produtoErradoNaoPodeRenomearVariacao() = withStore { store ->
        val first = Product(storeID = UUID.randomUUID(), name = "Caneca")
        val second = Product(storeID = UUID.randomUUID(), name = "Camiseta")
        store.saveProduct(first)
        store.saveProduct(second)
        val variant = ProductVariantService.create(product = first, name = "Branca", store = store)

        expectError(ProductVariantError.ProductMismatch) {
            ProductVariantService.rename(
                variant = variant,
                name = "Verde",
                product = second,
                store = store,
            )
        }
    }

    @Test
    fun totalSomaSomenteAsVariacoesDoProduto() = withStore { store ->
        val product = Product(storeID = UUID.randomUUID(), name = "Produto principal")
        val other = Product(storeID = UUID.randomUUID(), name = "Caneca")
        store.saveProduct(product)
        store.saveProduct(other)

        ProductVariantService.create(product = product, name = "Preta", initialQuantity = 2, store = store)
        ProductVariantService.create(product = product, name = "Vermelha", initialQuantity = 1, store = store)
        ProductVariantService.create(product = other, name = "Branca", initialQuantity = 20, store = store)

        assertEquals(3, ProductVariantService.totalQuantity(product, store))
    }

    // O iOS tem "todoErroTemMensagemLocalizada"; no Android o texto fica no :app e o teste vai para la.
}

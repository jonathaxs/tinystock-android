package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.support.assertDecimal
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import com.jonathaxs.tinystock.core.util.FoldedNameOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Porte de `ProductServiceTests.swift`. */
class ProductServiceTest {

    @Test
    fun criacaoPreservaSomenteOsDadosCanonicos() = withStore { store ->
        val storeID = UUID.randomUUID()
        val date = Instant.ofEpochSecond(1_800_000_000)

        val product = ProductService.create(
            storeID = storeID,
            name = "  Café Especial  ",
            costPrice = BigDecimal("40"),
            salePrice = BigDecimal("100"),
            imageFileName = "cafe.jpg",
            date = date,
            store = store,
        )

        val saved = store.product(product.id)!!
        assertEquals(storeID, saved.storeID)
        assertEquals("Café Especial", saved.name)
        assertDecimal("40", saved.costPrice)
        assertDecimal("100", saved.salePrice)
        assertEquals("cafe.jpg", saved.imageFileName)
        assertEquals(date, saved.createdAt)
        assertEquals(date, saved.updatedAt)
        assertTrue(saved.category.isEmpty())
        assertEquals(0, saved.quantity)
        assertEquals(0, saved.minimumStock)
    }

    @Test
    fun ordemManualEhPreservadaENovoProdutoEntraNoFim() = withStore { store ->
        val storeID = UUID.randomUUID()
        val bolsa = ProductService.create(storeID = storeID, name = "Bolsa", store = store)
        val tapete = ProductService.create(storeID = storeID, name = "Tapete", store = store)

        ProductService.setDisplayOrder(listOf(tapete, bolsa), store)
        val touca = ProductService.create(storeID = storeID, name = "Touca", store = store)

        val ordered = store.products(storeID).sortedWith(
            compareBy<Product> { it.sortOrder }.thenBy(FoldedNameOrder) { it.name },
        )
        assertEquals(listOf("Tapete", "Bolsa", "Touca"), ordered.map { it.name })
        assertEquals(2, store.product(touca.id)!!.sortOrder)
    }

    @Test
    fun nomeVazioEhRecusado() = withStore { store ->
        expectError(ProductError.EmptyName) {
            ProductService.create(storeID = UUID.randomUUID(), name = "   ", store = store)
        }
    }

    @Test
    fun nomeDuplicadoNaMesmaLojaEhRecusado() = withStore { store ->
        val storeID = UUID.randomUUID()
        ProductService.create(storeID = storeID, name = "Café Especial", store = store)

        expectError(ProductError.DuplicateName) {
            ProductService.create(storeID = storeID, name = "cafe especial", store = store)
        }
    }

    @Test
    fun lojasDiferentesPodemUsarOMesmoNome() = withStore { store ->
        val firstStore = UUID.randomUUID()
        val secondStore = UUID.randomUUID()

        ProductService.create(storeID = firstStore, name = "Caneca", store = store)
        ProductService.create(storeID = secondStore, name = "Caneca", store = store)

        assertEquals(1, store.products(firstStore).size)
        assertEquals(1, store.products(secondStore).size)
    }

    @Test
    fun valoresNegativosSaoRecusados() = withStore { store ->
        val storeID = UUID.randomUUID()

        expectError(ProductError.NegativeCostPrice) {
            ProductService.create(
                storeID = storeID,
                name = "Caneca",
                costPrice = BigDecimal("-1"),
                store = store,
            )
        }

        expectError(ProductError.NegativeSalePrice) {
            ProductService.create(
                storeID = storeID,
                name = "Caneca",
                salePrice = BigDecimal("-1"),
                store = store,
            )
        }
    }

    @Test
    fun edicaoPreservaLojaECriacao() = withStore { store ->
        val storeID = UUID.randomUUID()
        val createdAt = Instant.ofEpochSecond(1_700_000_000)
        val updatedAt = Instant.ofEpochSecond(1_800_000_000)
        val product = Product(
            storeID = storeID,
            name = "Caneca",
            costPrice = BigDecimal("10"),
            salePrice = BigDecimal("20"),
            createdAt = createdAt,
            updatedAt = createdAt,
        )
        store.saveProduct(product)

        ProductService.update(
            product = product,
            name = "Caneca personalizada",
            costPrice = BigDecimal("12"),
            salePrice = BigDecimal("30"),
            imageFileName = null,
            date = updatedAt,
            store = store,
        )

        val saved = store.product(product.id)!!
        assertEquals(storeID, saved.storeID)
        assertEquals("Caneca personalizada", saved.name)
        assertDecimal("12", saved.costPrice)
        assertDecimal("30", saved.salePrice)
        assertNull(saved.imageFileName)
        assertEquals(createdAt, saved.createdAt)
        assertEquals(updatedAt, saved.updatedAt)
    }

    // O iOS tem "todoErroTemMensagemLocalizada"; no Android o texto fica no :app e o teste vai para la.
}

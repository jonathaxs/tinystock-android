package com.jonathaxs.tinystock.core.model

import com.jonathaxs.tinystock.core.support.assertDecimal
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/** Porte de `ProductTests.swift`. */
class ProductTest {

    @Test
    fun lowStockLigaQuandoQuantidadeAtingeMinimo() {
        // Estoque igual ou abaixo do minimo deve acender o alerta.
        val produto = Product(name = "Produto A", quantity = 3, minimumStock = 5)
        assertTrue(produto.isLowStock)
    }

    @Test
    fun lowStockDesligaQuandoMinimoZero() {
        // Minimo zero significa "sem alerta", mesmo com estoque zerado.
        val produto = Product(name = "Produto B", quantity = 0, minimumStock = 0)
        assertFalse(produto.isLowStock)
    }

    @Test
    fun lucroUnitarioEhPrecoMenosCusto() {
        val produto = Product(name = "Produto C", costPrice = BigDecimal("8"), salePrice = BigDecimal("25"))
        assertDecimal("17", produto.unitProfit)
    }

    @Test
    fun lowStockDesligaQuandoEstoqueEstaAcimaDoMinimo() {
        val produto = Product(name = "Produto D", quantity = 10, minimumStock = 3)
        assertFalse(produto.isLowStock)
    }

    @Test
    fun lowStockLigaComEstoqueZeradoEMinimoDefinido() {
        // Estoque acabou de vez, que e justamente quando o alerta mais importa.
        val produto = Product(name = "Produto C", quantity = 0, minimumStock = 2)
        assertTrue(produto.isLowStock)
    }

    @Test
    fun buscaVaziaDevolveTodosOsProdutos() {
        val produto = Product(name = "Luminária Clássica", category = "Decoração")
        assertTrue(produto.matches(""))
        assertTrue(produto.matches("   "))
    }

    @Test
    fun buscaEncontraPorPedacoDoNome() {
        val produto = Product(name = "Luminária Clássica", category = "Decoração")
        assertTrue(produto.matches("classica"))
    }

    @Test
    fun buscaIgnoraAcentoEMaiuscula() {
        // A busca deve aceitar o texto sem acentos e com outra capitalizacao.
        val produto = Product(name = "Luminária Clássica", category = "Decoração")
        assertTrue(produto.matches("DECORACAO"))
    }

    @Test
    fun buscaEncontraPelaCategoria() {
        val produto = Product(name = "Caderno", category = "Acessórios")
        assertTrue(produto.matches("acessorios"))
    }

    @Test
    fun buscaNaoEncontraOQueNaoExiste() {
        val produto = Product(name = "Luminária Clássica", category = "Decoração")
        assertFalse(produto.matches("caneca"))
    }

    @Test
    fun lucroPotencialMultiplicaOLucroPelaQuantidade() {
        val produto = Product(
            name = "Produto A",
            quantity = 4,
            costPrice = BigDecimal("10"),
            salePrice = BigDecimal("25"),
        )
        assertDecimal("60", produto.potentialProfit)
    }

    @Test
    fun lucroPotencialEhZeroComEstoqueVazio() {
        val produto = Product(
            name = "Produto B",
            quantity = 0,
            costPrice = BigDecimal("30"),
            salePrice = BigDecimal("90"),
        )
        assertDecimal("0", produto.potentialProfit)
    }

    @Test
    fun produtoSalvoPodeSerLidoDeVolta() = withStore { store ->
        store.saveProduct(
            Product(
                name = "Luminária Clássica",
                category = "Decoração",
                quantity = 12,
                costPrice = BigDecimal("20"),
                salePrice = BigDecimal("45"),
            ),
        )

        val salvos = store.products(StoreScope.UNASSIGNED_STORE_ID)

        assertEquals(1, salvos.size)
        assertEquals("Luminária Clássica", salvos.first().name)
        assertEquals("Decoração", salvos.first().category)
        assertEquals(12, salvos.first().quantity)
        assertDecimal("25", salvos.first().unitProfit)
    }

    @Test
    fun edicaoAlteraOsCamposEPreservaACriacao() = withStore { store ->
        val criadoEm = Instant.ofEpochSecond(1_700_000_000)
        val atualizadoEm = Instant.ofEpochSecond(1_800_000_000)
        val produto = Product(
            name = "Organizador",
            quantity = 2,
            salePrice = BigDecimal("25"),
            createdAt = criadoEm,
            updatedAt = criadoEm,
        )
        store.saveProduct(produto)

        store.saveProduct(
            produto.copy(
                name = "Organizador V2",
                quantity = 10,
                salePrice = BigDecimal("30"),
                updatedAt = atualizadoEm,
            ),
        )

        val salvos = store.products(StoreScope.UNASSIGNED_STORE_ID)

        assertEquals(1, salvos.size)
        assertEquals("Organizador V2", salvos.first().name)
        assertEquals(10, salvos.first().quantity)
        assertEquals(criadoEm, salvos.first().createdAt)
        assertNotEquals(criadoEm, salvos.first().updatedAt)
    }

    @Test
    fun exclusaoTiraOProdutoDoEstoque() = withStore { store ->
        val removedProduct = Product(name = "Produto removido", quantity = 12)
        val remainingProduct = Product(name = "Produto mantido", quantity = 8)
        store.saveProduct(removedProduct)
        store.saveProduct(remainingProduct)

        store.deleteProduct(removedProduct.id)

        val restantes = store.products(StoreScope.UNASSIGNED_STORE_ID)

        assertEquals(1, restantes.size)
        assertEquals("Produto mantido", restantes.first().name)
    }
}
